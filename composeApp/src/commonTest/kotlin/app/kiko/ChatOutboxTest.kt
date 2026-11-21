@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package app.kiko

import app.kiko.chats.ChatStore
import app.kiko.chats.MediaFile
import app.kiko.chats.MemoryChatCache
import app.kiko.chats.mergeMessages
import app.kiko.chats.outboxRetryDelay
import app.kiko.data.AccountException
import app.kiko.data.ChatBackground
import app.kiko.data.ChatMessage
import app.kiko.data.ChatService
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import app.kiko.data.MessageKind
import app.kiko.data.ReportReason
import app.kiko.data.isTemporaryFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Serveur de test : coupé ou non, il peut aussi refuser certains messages. */
private class FakeChatService : ChatService {
    override val myId = "moi"
    var online = true
    val refused = mutableSetOf<String>()
    val sent = mutableListOf<ChatMessage>()
    val uploads = mutableListOf<String>()
    val server = mutableListOf<ChatMessage>()

    private fun network() {
        if (!online) throw AccountException("Connexion impossible.", RuntimeException("Unable to resolve host"))
    }

    override suspend fun conversations(): List<Conversation> {
        network()
        return listOf(
            Conversation(
                id = "ayu", status = ConversationStatus.Accepted, requestedByMe = true, partnerId = "ayu",
                partnerName = "Ayu", partnerVerified = true, partnerLastSeen = null, partnerTimeZone = "Asia/Jakarta",
                partnerNativeLanguage = "id", lastMessage = null, lastMessageMine = false,
                lastActivity = Instant.parse("2026-11-21T10:00:00Z"), unread = 0,
            ),
        )
    }

    override suspend fun messages(conversationId: String): List<ChatMessage> {
        network()
        return server.filter { it.conversationId == conversationId }
    }

    override suspend fun send(message: ChatMessage): ChatMessage {
        network()
        // Refus du serveur (règles RLS) : pas de cause réseau, c'est définitif.
        if (message.body in refused) throw AccountException("Ce message n'a pas pu partir.")
        if (sent.none { it.id == message.id }) sent += message
        return message.copy(pending = false, failed = false)
    }

    override suspend fun uploadMedia(path: String, bytes: ByteArray, contentType: String) {
        network()
        uploads += path
    }

    override suspend fun media(path: String) = ByteArray(0)
    override suspend fun respond(conversationId: String, accept: Boolean) = Unit
    override suspend fun block(partnerId: String) = Unit
    override suspend fun report(partnerId: String, conversationId: String?, reason: ReportReason, details: String?) = Unit
    override suspend fun setBackground(conversationId: String, background: ChatBackground) = Unit
    override suspend fun markRead(conversationId: String) = Unit
    override suspend fun partnerReadUpTo(conversationId: String): Instant? = null
    override fun newMessages(): Flow<ChatMessage> = emptyFlow()
    // « écrit… » reste ouvert sans rien dire, comme un canal calme.
    override fun typing(conversationId: String): Flow<String> = flow { awaitCancellation() }
    override suspend fun sendTyping(conversationId: String) = Unit
}

class ChatOutboxTest {
    private val service = FakeChatService()
    private val cache = MemoryChatCache()

    private suspend fun TestScope.openStore(): ChatStore {
        cache.open("moi")
        val store = ChatStore(service, backgroundScope, cache)
        store.refresh()
        store.openConversation("ayu")
        runCurrent()
        return store
    }

    @Test
    fun messagesWrittenOfflineLeaveInOrderOnceTheNetworkIsBack() = runTest {
        val store = openStore()
        service.online = false
        store.send("Halo")
        store.send("Apa kabar ?")
        runCurrent()
        assertFalse(store.flushOutbox())
        assertTrue(service.sent.isEmpty())
        assertTrue(store.messages.all { it.pending && !it.failed })
        assertEquals(2, cache.queued().size)

        service.online = true
        assertTrue(store.flushOutbox())
        assertEquals(listOf("Halo", "Apa kabar ?"), service.sent.map { it.body })
        assertTrue(store.messages.none { it.pending || it.failed })
        assertTrue(cache.queued().isEmpty())
        assertFalse(store.offline)
    }

    @Test
    fun theQueueSurvivesARestartOfTheApp() = runTest {
        val first = openStore()
        service.online = false
        first.send("Écrit dans le métro")
        runCurrent()

        // L'app est relancée : un nouvel état, le même cache sur l'appareil.
        service.online = true
        val second = ChatStore(service, backgroundScope, cache)
        assertTrue(second.flushOutbox())
        assertEquals(listOf("Écrit dans le métro"), service.sent.map { it.body })
    }

    @Test
    fun aRefusedMessageDoesNotHoldBackTheOthers() = runTest {
        val store = openStore()
        service.refused += "refusé"
        store.send("refusé")
        store.send("accepté")
        runCurrent()
        assertTrue(store.flushOutbox())
        assertEquals(listOf("accepté"), service.sent.map { it.body })
        assertTrue(store.messages.first { it.body == "refusé" }.failed)
        assertTrue(cache.queued().isEmpty())

        // Touché, il repart.
        service.refused.clear()
        store.retry(store.messages.first { it.body == "refusé" })
        runCurrent()
        assertTrue(store.flushOutbox())
        assertEquals(listOf("accepté", "refusé"), service.sent.map { it.body })
    }

    @Test
    fun aPhotoWrittenOfflineIsUploadedBeforeItsMessage() = runTest {
        val store = openStore()
        service.online = false
        store.sendPhoto(MediaFile(byteArrayOf(1, 2), "image/jpeg", "jpg"))
        runCurrent()
        assertFalse(store.flushOutbox())
        service.online = true
        assertTrue(store.flushOutbox())
        val photo = service.sent.single()
        assertEquals(MessageKind.Photo, photo.kind)
        assertEquals(listOf(photo.mediaPath), service.uploads)
        assertEquals(null, cache.queuedMedia(photo.id))
    }

    @Test
    fun theChatOpensFromTheDeviceWhenOffline() = runTest {
        val online = openStore()
        online.send("Halo")
        runCurrent()
        online.flushOutbox()
        service.server += service.sent

        service.online = false
        val offline = ChatStore(service, backgroundScope, cache)
        offline.refresh()
        offline.openConversation("ayu")
        runCurrent()
        assertEquals(listOf("ayu"), cache.conversations().map { it.id })
        assertEquals(listOf("Halo"), offline.messages.map { it.body })
        assertTrue(offline.offline)
        assertEquals(null, offline.chatError)
    }

    @Test
    fun mergeKeepsWhatHasNotLeftYet() {
        val t = Instant.parse("2026-11-21T10:00:00Z")
        val server = listOf(ChatMessage("s", "ayu", "ayu", "Halo", t))
        val current = listOf(
            ChatMessage("s", "ayu", "ayu", "Halo", t),
            ChatMessage("p", "ayu", "moi", "en route", t + 5.seconds, pending = true),
            ChatMessage("f", "ayu", "moi", "refusé", t + 1.seconds, failed = true),
            ChatMessage("vieux", "ayu", "moi", "déjà parti", t - 5.seconds),
        )
        assertEquals(listOf("s", "f", "p"), mergeMessages(server, current).map { it.id })
    }

    @Test
    fun temporaryFailuresAreTheNetworkNotOurRefusals() {
        assertTrue(AccountException("Connexion impossible.", RuntimeException("timeout")).isTemporaryFailure())
        assertFalse(AccountException("Écris un message avant d'envoyer.").isTemporaryFailure())
    }

    @Test
    fun retriesSpreadOutThenStayAtOneMinute() {
        assertEquals(2.seconds, outboxRetryDelay(0))
        assertEquals(60.seconds, outboxRetryDelay(4))
        assertEquals(60.seconds, outboxRetryDelay(50))
    }
}
