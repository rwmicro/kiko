@file:OptIn(ExperimentalTime::class)

package app.kiko.data

import app.kiko.chats.callSlots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/** Discussions d'exemple pour la démo sans compte : Ayu écrit, puis répond quand on lui écrit. */
class DemoChatService : ChatService {
    override val myId: String = "moi"
    private val now = Clock.System.now()
    private val incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 8)
    private val typingSignals = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    private val replies = CoroutineScope(SupervisorJob())
    private val files = mutableMapOf<String, ByteArray>()

    private val conversations = mutableListOf(
        demo("ayu", "Ayu", ConversationStatus.Accepted, requestedByMe = true, verified = true, ago = 3.minutes, streak = 6),
        demo("sari", "Sari", ConversationStatus.Requested, requestedByMe = false, verified = true, ago = 40.minutes),
        demo("putri", "Putri", ConversationStatus.Accepted, requestedByMe = false, ago = 1.days, streak = 2),
        demo("budi", "Budi", ConversationStatus.Requested, requestedByMe = true, ago = 3.days),
    )

    private val corrected = msg("ayu", myId, "Saya suka film itu sangat.", 24.hours)
    private val messages = mutableMapOf(
        "ayu" to mutableListOf(
            msg("ayu", myId, "Halo Ayu! Film apa yang terakhir membuatmu menangis?", 26.hours),
            msg("ayu", "ayu", "Halo Martin ! « Coco », tu connais ?", 25.hours),
            corrected,
            msg(
                "ayu", "ayu",
                CorrectionPayload(
                    original = corrected.body,
                    corrected = "Saya sangat suka film itu.",
                    note = "« Sangat » se place avant ce qu'il renforce.",
                    tags = listOf(CorrectionPayload.TagGrammar),
                ).encode(),
                24.hours - 2.minutes,
                kind = MessageKind.Correction,
                replyTo = corrected.id,
            ),
            msg(
                "ayu", "ayu",
                CallPayload(at = callSlots(now, TimeZone.currentSystemDefault(), Jakarta).getOrElse(1) { now + 1.days }.toString()).encode(),
                23.hours,
                kind = MessageKind.CallProposal,
            ),
            msg("ayu", "ayu", "Ton indonésien est déjà bien !", 5.minutes),
            msg("ayu", "ayu", "Tu fais quoi ce soir ?", 3.minutes),
        ),
        "sari" to mutableListOf(
            msg("sari", "sari", "Bonjour Martin ! J'ai vu que tu aimes l'art. Tu connais des musées à Lyon ?", 40.minutes),
        ),
        "putri" to mutableListOf(
            msg("putri", "putri", "Terima kasih banyak !", 25.hours),
            msg("putri", myId, "Sama-sama, à demain !", 1.days),
        ),
        "budi" to mutableListOf(
            msg("budi", myId, "Halo Budi! Kamu dukung tim mana?", 3.days),
        ),
    )
    private val readByMe = mutableMapOf("ayu" to 5, "sari" to 0, "putri" to 2, "budi" to 1)

    private fun demo(
        id: String,
        name: String,
        status: ConversationStatus,
        requestedByMe: Boolean,
        verified: Boolean = false,
        ago: Duration,
        streak: Int = 0,
    ) = Conversation(
        id = id, status = status, requestedByMe = requestedByMe, partnerId = id, partnerName = name,
        partnerVerified = verified, partnerLastSeen = if (id == "ayu") now else now - 2.hours,
        partnerTimeZone = Jakarta.id, partnerNativeLanguage = "id",
        lastMessage = null, lastMessageMine = false, lastActivity = now - ago, unread = 0, streak = streak,
    )

    private fun msg(conv: String, sender: String, text: String, ago: Duration, kind: MessageKind = MessageKind.Text, replyTo: String? = null) =
        ChatMessage(newMessageId(), conv, sender, text, now - ago, kind, replyToId = replyTo)

    override suspend fun conversations(): List<Conversation> {
        delay(250)
        return conversations.map { c ->
            val list = messages[c.id].orEmpty()
            val last = list.lastOrNull()
            c.copy(
                lastMessage = last?.body,
                lastMessageKind = last?.kind ?: MessageKind.Text,
                lastMessageMine = last?.senderId == myId,
                lastActivity = last?.createdAt ?: c.lastActivity,
                unread = list.drop(readByMe[c.id] ?: 0).count { it.senderId != myId },
            )
        }.sortedByDescending { it.lastActivity }
    }

    override suspend fun messages(conversationId: String): List<ChatMessage> {
        delay(200)
        return messages[conversationId].orEmpty().toList()
    }

    override suspend fun send(message: ChatMessage): ChatMessage {
        delay(300)
        val sent = message.copy(senderId = myId, body = message.body.trim(), createdAt = Clock.System.now(), pending = false, failed = false)
        val list = messages.getOrPut(message.conversationId) { mutableListOf() }
        if (list.none { it.id == sent.id }) list.add(sent)
        readByMe[message.conversationId] = list.size
        if (message.conversationId == "ayu") replyAsAyu(sent)
        return sent
    }

    /** Ayu « écrit… » puis répond, comme en vrai. */
    private fun replyAsAyu(to: ChatMessage) {
        val text = when (to.kind) {
            MessageKind.Photo -> "Oh, jolie photo !"
            MessageKind.Voice -> "Haha, ton accent est mignon. Bagus sekali !"
            MessageKind.Correction -> "Ah merci, je retiens !"
            MessageKind.CallProposal -> if (to.call()?.acceptedAt != null) "Super, à bientôt !" else "D'accord pour cette heure-là !"
            MessageKind.Text -> "Haha, d'accord ! On s'appelle demain ?"
        }
        replies.launch {
            delay(900)
            typingSignals.emit("ayu" to "ayu")
            delay(2_000)
            typingSignals.emit("ayu" to "ayu")
            delay(1_000)
            val reply = ChatMessage(newMessageId(), "ayu", "ayu", text, Clock.System.now())
            messages.getValue("ayu").add(reply)
            incoming.emit(reply)
        }
    }

    override suspend fun uploadMedia(path: String, bytes: ByteArray, contentType: String) {
        delay(400)
        files[path] = bytes
    }

    override suspend fun media(path: String): ByteArray =
        files[path] ?: throw AccountException("Ce fichier n'existe pas dans la démo.")

    override suspend fun respond(conversationId: String, accept: Boolean) {
        delay(300)
        val i = conversations.indexOfFirst { it.id == conversationId }
        if (i < 0) return
        if (accept) conversations[i] = conversations[i].copy(status = ConversationStatus.Accepted) else conversations.removeAt(i)
    }

    override suspend fun block(partnerId: String) {
        delay(300)
        conversations.removeAll { it.partnerId == partnerId }
    }

    override suspend fun report(partnerId: String, conversationId: String?, reason: ReportReason, details: String?) {
        delay(300)
    }

    override suspend fun setBackground(conversationId: String, background: ChatBackground) {
        delay(200)
        val i = conversations.indexOfFirst { it.id == conversationId }
        if (i >= 0) conversations[i] = conversations[i].copy(background = background)
    }

    override suspend fun markRead(conversationId: String) {
        readByMe[conversationId] = messages[conversationId]?.size ?: 0
    }

    override suspend fun partnerReadUpTo(conversationId: String) =
        if (conversations.firstOrNull { it.id == conversationId }?.status == ConversationStatus.Accepted) {
            messages[conversationId]?.lastOrNull { it.senderId == myId }?.createdAt
        } else {
            null
        }

    override fun newMessages(): Flow<ChatMessage> = incoming

    override fun typing(conversationId: String): Flow<String> =
        typingSignals.filter { it.first == conversationId }.map { it.second }

    override suspend fun sendTyping(conversationId: String) = Unit

    private companion object {
        val Jakarta = TimeZone.of("Asia/Jakarta")
    }
}
