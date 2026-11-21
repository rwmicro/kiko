@file:OptIn(ExperimentalTime::class)

package app.kiko

import app.kiko.chats.CACHED_MESSAGES_PER_CONVERSATION
import app.kiko.chats.ChatCache
import app.kiko.chats.MediaFile
import app.kiko.data.ChatMessage
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import app.kiko.data.MessageKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Ce que tout cache des discussions doit garantir ; rejoué sur la mémoire et sur SQLite. */
abstract class ChatCacheContract {
    abstract fun newCache(): ChatCache

    private val t0 = Instant.parse("2026-11-21T10:00:00Z")

    private fun conv(id: String, minutesAgo: Int = 0) = Conversation(
        id = id, status = ConversationStatus.Accepted, requestedByMe = true, partnerId = "p-$id", partnerName = id,
        partnerVerified = true, partnerLastSeen = t0, partnerTimeZone = "Asia/Jakarta", partnerNativeLanguage = "id",
        lastMessage = "Halo", lastMessageMine = false, lastActivity = t0 - minutesAgo.minutes, unread = 2, streak = 3,
    )

    private fun msg(id: String, conv: String = "ayu", minute: Int = 0, pending: Boolean = false) =
        ChatMessage(id, conv, "moi", "texte $id", t0 + minute.minutes, MessageKind.Text, pending = pending)

    @Test
    fun conversationsComeBackWithoutPresence() = runTest {
        val cache = newCache().apply { open("moi") }
        cache.saveConversations(listOf(conv("sari", 10), conv("ayu", 1)))
        val back = cache.conversations()
        assertEquals(listOf("ayu", "sari"), back.map { it.id })
        assertEquals(conv("ayu", 1).copy(partnerLastSeen = null), back.first())
    }

    @Test
    fun anotherAccountStartsFromNothing() = runTest {
        val cache = newCache().apply { open("moi") }
        cache.saveConversations(listOf(conv("ayu")))
        cache.enqueue(msg("a"))
        cache.open("quelqu-un-d-autre")
        assertTrue(cache.conversations().isEmpty())
        assertTrue(cache.queued().isEmpty())
    }

    @Test
    fun reopeningWithTheSameAccountKeepsEverything() = runTest {
        val cache = newCache().apply { open("moi") }
        cache.saveConversations(listOf(conv("ayu")))
        cache.open("moi")
        assertEquals(1, cache.conversations().size)
    }

    @Test
    fun queueKeepsWritingOrderAcrossConversations() = runTest {
        val cache = newCache().apply { open("moi") }
        cache.enqueue(msg("b", conv = "sari", minute = 2))
        cache.enqueue(msg("a", conv = "ayu", minute = 1))
        cache.enqueue(msg("c", conv = "ayu", minute = 3))
        assertEquals(listOf("a", "b", "c"), cache.queued().map { it.id })
        assertTrue(cache.queued().all { it.pending })
    }

    @Test
    fun sentMessageLeavesTheQueueWithItsFileAndKeepsItsTime() = runTest {
        val cache = newCache().apply { open("moi") }
        val file = MediaFile(byteArrayOf(1, 2, 3), "image/jpeg", "jpg")
        cache.enqueue(msg("a", minute = 1).copy(kind = MessageKind.Photo, mediaPath = "ayu/a.jpg"), file)
        assertContentEquals(byteArrayOf(1, 2, 3), cache.queuedMedia("a")?.bytes)
        // Le serveur renvoie le message avec son heure à lui : l'écran garde celle où je l'ai écrit.
        cache.saveSent(msg("a", minute = 9).copy(kind = MessageKind.Photo, mediaPath = "ayu/a.jpg"))
        assertTrue(cache.queued().isEmpty())
        assertNull(cache.queuedMedia("a"))
        val kept = cache.messages("ayu").single()
        assertEquals(t0 + 1.minutes, kept.createdAt)
        assertTrue(!kept.pending && !kept.failed)
    }

    @Test
    fun refusedMessageStaysVisibleUntilRequeued() = runTest {
        val cache = newCache().apply { open("moi") }
        cache.enqueue(msg("a"))
        cache.markFailed("a")
        assertTrue(cache.queued().isEmpty())
        assertTrue(cache.messages("ayu").single().failed)
        cache.requeue("a")
        assertEquals(listOf("a"), cache.queued().map { it.id })
    }

    @Test
    fun serverListReplacesSentMessagesButNotTheQueue() = runTest {
        val cache = newCache().apply { open("moi") }
        cache.saveSent(msg("ancien", minute = 0))
        cache.enqueue(msg("en-attente", minute = 5))
        cache.saveServerMessages("ayu", listOf(msg("s1", minute = 1), msg("s2", minute = 2)))
        assertEquals(listOf("s1", "s2", "en-attente"), cache.messages("ayu").map { it.id })
    }

    @Test
    fun keepsOnlyTheLatestSentMessages() = runTest {
        val cache = newCache().apply { open("moi") }
        val many = (0 until CACHED_MESSAGES_PER_CONVERSATION + 20).map { msg("m$it", minute = it) }
        cache.saveServerMessages("ayu", many)
        cache.enqueue(msg("en-attente", minute = 10_000))
        val kept = cache.messages("ayu")
        assertEquals(CACHED_MESSAGES_PER_CONVERSATION + 1, kept.size)
        assertEquals("m20", kept.first().id)
    }

    @Test
    fun vanishedConversationTakesItsMessagesAndQueueAlong() = runTest {
        val cache = newCache().apply { open("moi") }
        cache.saveConversations(listOf(conv("ayu"), conv("budi")))
        cache.saveSent(msg("a", conv = "budi"))
        cache.enqueue(msg("b", conv = "budi"), MediaFile(byteArrayOf(9), "audio/mp4", "m4a"))
        cache.enqueue(msg("c", conv = "ayu"))
        cache.saveConversations(listOf(conv("ayu")))
        assertTrue(cache.messages("budi").isEmpty())
        assertNull(cache.queuedMedia("b"))
        assertEquals(listOf("c"), cache.queued().map { it.id })
    }

    @Test
    fun clearForgetsEverything() = runTest {
        val cache = newCache().apply { open("moi") }
        cache.saveConversations(listOf(conv("ayu")))
        cache.enqueue(msg("a"), MediaFile(byteArrayOf(1), "image/jpeg", "jpg"))
        cache.clear()
        assertTrue(cache.conversations().isEmpty())
        assertTrue(cache.messages("ayu").isEmpty())
        assertNull(cache.queuedMedia("a"))
    }
}

class MemoryChatCacheTest : ChatCacheContract() {
    override fun newCache(): ChatCache = app.kiko.chats.MemoryChatCache()
}
