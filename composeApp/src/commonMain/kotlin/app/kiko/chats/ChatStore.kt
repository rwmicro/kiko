@file:OptIn(ExperimentalTime::class)

package app.kiko.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kiko.data.AccountException
import app.kiko.data.CallPayload
import app.kiko.data.ChatBackground
import app.kiko.data.ChatMessage
import app.kiko.data.ChatService
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import app.kiko.data.CorrectionPayload
import app.kiko.data.MessageKind
import app.kiko.data.ReportReason
import app.kiko.data.VoicePayload
import app.kiko.data.call
import app.kiko.data.encode
import app.kiko.data.mediaPath
import app.kiko.data.newMessageId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Une photo ou un vocal choisi sur l'appareil, prêt à partir. */
class MediaFile(val bytes: ByteArray, val contentType: String, val extension: String)

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

    /** « Ayu écrit… » dans la conversation ouverte. */
    var partnerTyping by mutableStateOf(false)
        private set

    /** Message bref après une action (signalement envoyé…), sur la liste. */
    var notice by mutableStateOf<String?>(null)
        private set

    /** Photos et vocaux déjà chargés, par chemin. */
    val media = mutableStateMapOf<String, ByteArray>()

    /** Chemins dont le chargement a échoué : un toucher relance. */
    val mediaErrors = mutableStateListOf<String>()

    /** Corrections déjà remerciées pendant cette session. */
    val thanked = mutableStateListOf<String>()

    private val mediaLoading = mutableSetOf<String>()
    private val pendingUploads = mutableMapOf<String, MediaFile>()
    private var typingJob: Job? = null
    private var typingReset: Job? = null
    private var lastTypingSent = Instant.DISTANT_PAST

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
        listenTyping()
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
        stopTyping()
        scope.launch { refresh() }
    }

    fun message(id: String?): ChatMessage? = id?.let { wanted -> messages.firstOrNull { it.id == wanted } }

    /** Affiche le message tout de suite, puis le confirme ou le marque en échec. */
    fun send(text: String) {
        val body = text.trim()
        if (body.isNotEmpty()) post(MessageKind.Text, body)
    }

    /** Corrige un message de l'autre (maquette 18). */
    fun sendCorrection(target: ChatMessage, corrected: String, note: String?, tags: List<String>) {
        val payload = CorrectionPayload(target.body, corrected.trim(), note?.trim()?.ifEmpty { null }, tags.distinct())
        post(MessageKind.Correction, payload.encode(), replyTo = target.id)
    }

    fun thank(correction: ChatMessage) {
        if (correction.id in thanked) return
        thanked += correction.id
        send("Merci pour la correction !")
    }

    /** Propose un appel à une heure donnée (maquette 19). */
    fun proposeCall(at: Instant, minutes: Int = 15) {
        post(MessageKind.CallProposal, CallPayload(at = at.toString(), minutes = minutes).encode())
    }

    fun acceptCall(proposal: ChatMessage) {
        val call = proposal.call() ?: return
        if (acceptance(proposal) != null) return
        post(MessageKind.CallProposal, CallPayload(acceptedAt = call.time.toString(), minutes = call.minutes).encode(), replyTo = proposal.id)
    }

    /** La réponse « d'accord » à une proposition d'appel, s'il y en a une. */
    fun acceptance(proposal: ChatMessage): ChatMessage? =
        messages.firstOrNull { it.replyToId == proposal.id && it.call()?.acceptedAt != null }

    fun sendPhoto(file: MediaFile) = postMedia(MessageKind.Photo, file, body = "")

    fun sendVoice(file: MediaFile, durationMs: Long) =
        postMedia(MessageKind.Voice, file, body = VoicePayload(durationMs).encode())

    private fun postMedia(kind: MessageKind, file: MediaFile, body: String) {
        val conv = openId ?: return
        val id = newMessageId()
        val path = mediaPath(conv, id, file.extension)
        media[path] = file.bytes
        pendingUploads[id] = file
        post(kind, body, mediaPath = path, id = id)
    }

    private fun post(kind: MessageKind, body: String, replyTo: String? = null, mediaPath: String? = null, id: String = newMessageId()) {
        val conv = openId ?: return
        val draft = ChatMessage(id, conv, myId, body, Clock.System.now(), kind, mediaPath, replyTo, pending = true)
        messages = messages + draft
        deliver(draft)
    }

    /** Charge une photo ou un vocal, une seule fois. */
    fun loadMedia(path: String) {
        if (media.containsKey(path) || !mediaLoading.add(path)) return
        mediaErrors.remove(path)
        scope.launch {
            try {
                media[path] = service.media(path)
            } catch (_: AccountException) {
                mediaErrors += path
            } finally {
                mediaLoading.remove(path)
            }
        }
    }

    /** Le texte du champ a changé : on prévient l'autre, au plus une fois toutes les 2,5 s. */
    fun onDraftChanged(text: String) {
        val conv = openId ?: return
        val now = Clock.System.now()
        if (text.isBlank() || now - lastTypingSent < 2_500.milliseconds) return
        lastTypingSent = now
        scope.launch { service.sendTyping(conv) }
    }

    private fun listenTyping() {
        stopTyping()
        val conv = open ?: return
        if (conv.status != ConversationStatus.Accepted) return
        typingJob = scope.launch {
            while (true) {
                try {
                    service.typing(conv.id).collect { sender -> if (sender != myId) showTyping() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Canal coupé : on se reconnecte plus tard.
                }
                delay(5_000)
            }
        }
    }

    private fun showTyping() {
        partnerTyping = true
        typingReset?.cancel()
        typingReset = scope.launch {
            delay(4_000)
            partnerTyping = false
        }
    }

    private fun stopTyping() {
        typingJob?.cancel()
        typingReset?.cancel()
        partnerTyping = false
    }

    fun retry(message: ChatMessage) {
        messages = messages.map { if (it.id == message.id) it.copy(failed = false, pending = true) else it }
        deliver(message)
    }

    private fun deliver(message: ChatMessage) {
        scope.launch {
            try {
                pendingUploads[message.id]?.let { file ->
                    service.uploadMedia(message.mediaPath ?: return@let, file.bytes, file.contentType)
                    pendingUploads.remove(message.id)
                }
                val sent = service.send(message)
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
                listenTyping()
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
            stopTyping()
        }
    }

    /** Signale la personne de la conversation ouverte ; bloque aussi si [alsoBlock]. */
    fun report(reason: ReportReason, details: String?, alsoBlock: Boolean) {
        val conv = open ?: return
        act {
            service.report(conv.partnerId, conv.id, reason, details)
            if (alsoBlock) {
                service.block(conv.partnerId)
                conversations = conversations.filterNot { it.id == conv.id }
                openId = null
                stopTyping()
            }
            showNotice(
                if (alsoBlock) {
                    "Merci, on regarde ça. ${conv.partnerName} ne peut plus t'écrire."
                } else {
                    "Merci, on regarde ça."
                },
            )
        }
    }

    fun setBackground(background: ChatBackground) {
        val conv = open ?: return
        act {
            service.setBackground(conv.id, background)
            conversations = conversations.map { if (it.id == conv.id) it.copy(background = background) else it }
        }
    }

    private fun showNotice(text: String) {
        notice = text
        scope.launch {
            delay(4_000)
            if (notice == text) notice = null
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
            if (message.senderId != myId) partnerTyping = false
            if (message.senderId != myId) service.markRead(message.conversationId)
            partnerReadUpTo = service.partnerReadUpTo(message.conversationId)
        }
        refresh()
        if (message.conversationId == openId) {
            conversations = conversations.map { if (it.id == openId) it.copy(unread = 0) else it }
        }
    }
}
