@file:OptIn(ExperimentalTime::class)

package app.kiko.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kiko.data.AccountException
import app.kiko.data.ChatMessage
import app.kiko.data.ChatService
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import app.kiko.data.newMessageId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * État de l'onglet Discussions, partagé par la liste et le chat ouvert.
 * Il vit tant que l'écran principal vit, pour que le compteur de non-lus de l'onglet
 * reste à jour pendant qu'on est ailleurs dans l'app.
 */
class ChatStore(private val service: ChatService, private val scope: CoroutineScope) {
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** La conversation ouverte, ou null sur la liste. */
    var openId by mutableStateOf<String?>(null)
        private set
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var loadingMessages by mutableStateOf(false)
        private set

    /** Jusqu'où l'autre a lu : mes messages jusqu'à cette date ont la double coche. */
    var partnerReadUpTo by mutableStateOf<Instant?>(null)
        private set

    /** Erreur d'une action dans le chat (envoi, réponse à une demande). */
    var chatError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    val myId: String? get() = service.myId
    val open: Conversation? get() = conversations.firstOrNull { it.id == openId }
    val unreadTotal: Int get() = conversations.sumOf { it.unread }

    /** Charge la liste puis écoute les nouveaux messages. Se reconnecte si le temps réel tombe. */
    fun start() {
        scope.launch { refresh() }
        scope.launch {
            while (true) {
                try {
                    service.newMessages().collect { onIncoming(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Réseau coupé : on réessaie un peu plus tard.
                }
                delay(5_000)
                refresh()
            }
        }
    }

    suspend fun refresh() {
        try {
            conversations = service.conversations()
            error = null
        } catch (e: AccountException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    fun reload() {
        loading = true
        scope.launch { refresh() }
    }

    fun openConversation(id: String) {
        openId = id
        messages = emptyList()
        partnerReadUpTo = null
        chatError = null
        loadingMessages = true
        scope.launch {
            try {
                messages = service.messages(id)
            } catch (e: AccountException) {
                chatError = e.message
            } finally {
                loadingMessages = false
            }
            service.markRead(id)
            conversations = conversations.map { if (it.id == id) it.copy(unread = 0) else it }
            partnerReadUpTo = service.partnerReadUpTo(id)
        }
    }

    fun close() {
        openId = null
        messages = emptyList()
        scope.launch { refresh() }
    }

    /** Affiche le message tout de suite, puis le confirme ou le marque en échec. */
    fun send(text: String) {
        val conv = openId ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        val draft = ChatMessage(newMessageId(), conv, myId, body, Clock.System.now(), pending = true)
        messages = messages + draft
        deliver(draft)
    }

    fun retry(message: ChatMessage) {
        messages = messages.map { if (it.id == message.id) it.copy(failed = false, pending = true) else it }
        deliver(message)
    }

    private fun deliver(message: ChatMessage) {
        scope.launch {
            try {
                val sent = service.send(message.conversationId, message.id, message.body)
                messages = messages.map { if (it.id == message.id) sent.copy(createdAt = it.createdAt) else it }
                chatError = null
            } catch (e: AccountException) {
                messages = messages.map { if (it.id == message.id) it.copy(pending = false, failed = true) else it }
                chatError = e.message
            }
        }
    }

    fun respond(accept: Boolean) {
        val conv = open ?: return
        act {
            service.respond(conv.id, accept)
            if (accept) {
                conversations = conversations.map { if (it.id == conv.id) it.copy(status = ConversationStatus.Accepted) else it }
            } else {
                conversations = conversations.filterNot { it.id == conv.id }
                openId = null
            }
        }
    }

    fun block() {
        val conv = open ?: return
        act {
            service.block(conv.partnerId)
            conversations = conversations.filterNot { it.id == conv.id }
            openId = null
        }
    }

    private fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        chatError = null
        scope.launch {
            try {
                block()
            } catch (e: AccountException) {
                chatError = e.message
            } finally {
                busy = false
            }
        }
    }

    private suspend fun onIncoming(message: ChatMessage) {
        if (message.conversationId == openId) {
            if (messages.none { it.id == message.id }) messages = messages + message
            if (message.senderId != myId) service.markRead(message.conversationId)
            partnerReadUpTo = service.partnerReadUpTo(message.conversationId)
        }
        refresh()
        if (message.conversationId == openId) {
            conversations = conversations.map { if (it.id == openId) it.copy(unread = 0) else it }
        }
    }
}
