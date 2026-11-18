@file:OptIn(ExperimentalTime::class)

package app.kiko.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/** Discussions d'exemple pour la démo sans compte : Ayu répond quand on lui écrit. */
class DemoChatService : ChatService {
    override val myId: String = "moi"
    private val now = Clock.System.now()
    private val incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 8)
    private val replies = CoroutineScope(SupervisorJob())

    private val conversations = mutableListOf(
        demo("ayu", "Ayu", ConversationStatus.Accepted, requestedByMe = true, verified = true, ago = 3.minutes),
        demo("sari", "Sari", ConversationStatus.Requested, requestedByMe = false, verified = true, ago = 40.minutes),
        demo("putri", "Putri", ConversationStatus.Accepted, requestedByMe = false, ago = 1.days),
        demo("budi", "Budi", ConversationStatus.Requested, requestedByMe = true, ago = 3.days),
    )
    private val messages = mutableMapOf(
        "ayu" to mutableListOf(
            msg("ayu", myId, "Halo Ayu! Film apa yang terakhir membuatmu menangis?", 26.hours),
            msg("ayu", "ayu", "Halo Martin ! « Coco », tu connais ?", 25.hours),
            msg("ayu", myId, "Oui ! Aku suka sekali film itu.", 24.hours),
            msg("ayu", "ayu", "Ton indonésien est déjà bien 😄", 5.minutes),
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
    private val readByMe = mutableMapOf("ayu" to 2, "sari" to 0, "putri" to 2, "budi" to 1)

    private fun demo(id: String, name: String, status: ConversationStatus, requestedByMe: Boolean, verified: Boolean = false, ago: kotlin.time.Duration) =
        Conversation(
            id = id, status = status, requestedByMe = requestedByMe, partnerId = id, partnerName = name,
            partnerVerified = verified, partnerLastSeen = if (id == "ayu") now else now - 2.hours,
            partnerTimeZone = "Asia/Jakarta", partnerNativeLanguage = "id",
            lastMessage = null, lastMessageMine = false, lastActivity = now - ago, unread = 0,
        )

    private fun msg(conv: String, sender: String, text: String, ago: kotlin.time.Duration) =
        ChatMessage(newMessageId(), conv, sender, text, now - ago)

    override suspend fun conversations(): List<Conversation> {
        delay(250)
        return conversations.map { c ->
            val list = messages[c.id].orEmpty()
            val last = list.lastOrNull()
            c.copy(
                lastMessage = last?.body,
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

    override suspend fun send(conversationId: String, id: String, text: String): ChatMessage {
        delay(300)
        val sent = ChatMessage(id, conversationId, myId, text.trim(), Clock.System.now())
        messages.getOrPut(conversationId) { mutableListOf() }.add(sent)
        readByMe[conversationId] = messages.getValue(conversationId).size
        if (conversationId == "ayu") {
            // Ayu répond quelques secondes plus tard, comme en vrai.
            replies.launch {
                delay(2_500)
                val reply = ChatMessage(newMessageId(), conversationId, "ayu", "Haha, d'accord ! On s'appelle demain ?", Clock.System.now())
                messages.getValue(conversationId).add(reply)
                incoming.emit(reply)
            }
        }
        return sent
    }

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
}
