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
import app.kiko.data.isTemporaryFailure
import app.kiko.data.mediaPath
import app.kiko.data.newMessageId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Une photo ou un vocal choisi sur l'appareil, prêt à partir. */
class MediaFile(val bytes: ByteArray, val contentType: String, val extension: String)

/**
 * État de l'onglet Discussions, partagé par la liste et le chat ouvert.
 * Il vit tant que l'écran principal vit, pour que le compteur de non-lus de l'onglet
 * reste à jour pendant qu'on est ailleurs dans l'app.
 *
 * Hors ligne : la liste et les derniers messages viennent de [cache], et ce que j'écris attend
 * dans sa file. La file part dans l'ordre dès que le serveur répond (voir [flushOutbox]) ;
 * l'identifiant de chaque message est créé par l'app, donc un envoi rejoué ne fait pas de doublon.
 */
class ChatStore(
    private val service: ChatService,
    private val scope: CoroutineScope,
    private val cache: ChatCache = MemoryChatCache(),
) {
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

    /** Le serveur ne répond pas : on montre ce que l'appareil a gardé, la file attend. */
    var offline by mutableStateOf(false)
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
    private val outboxWake = Channel<Unit>(Channel.CONFLATED)
    private var typingJob: Job? = null
    private var typingReset: Job? = null
    private var lastTypingSent = Instant.DISTANT_PAST

    val myId: String? get() = service.myId
    val open: Conversation? get() = conversations.firstOrNull { it.id == openId }
    val unreadTotal: Int get() = conversations.sumOf { it.unread }

    /**
     * Montre la liste gardée sur l'appareil, la recharge, écoute les nouveaux messages et
     * vide la file d'envoi. Se reconnecte si le temps réel tombe.
     */
    fun start() {
        scope.launch {
            myId?.let { me -> local(Unit) { open(me) } }
            val saved = local(emptyList()) { conversations() }
            if (saved.isNotEmpty() && conversations.isEmpty()) {
                conversations = saved
                loading = false
            }
            launch { runOutbox() }
            refresh()
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
            val list = service.conversations()
            conversations = list
            error = null
            offline = false
            local(Unit) { saveConversations(list) }
            wakeOutbox()
        } catch (e: AccountException) {
            if (e.isTemporaryFailure()) offline = true
            // Avec une liste gardée sur l'appareil, on la montre plutôt qu'une erreur.
            if (conversations.isEmpty()) error = e.message
        } finally {
            loading = false
        }
    }

    /** Retour au premier plan, réseau revenu… : la file retente tout de suite. */
    fun wakeOutbox() {
        outboxWake.trySend(Unit)
    }

    /** Déconnexion ou suppression du compte : l'appareil oublie les discussions. */
    suspend fun forgetDevice() {
        local(Unit) { clear() }
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
            val saved = local(emptyList()) { messages(id) }
            if (openId != id) return@launch
            if (saved.isNotEmpty()) {
                messages = saved
                loadingMessages = false
                restoreQueuedMedia(saved)
            }
            try {
                val server = service.messages(id)
                local(Unit) { saveServerMessages(id, server) }
                if (openId != id) return@launch
                messages = mergeMessages(server, messages)
                offline = false
            } catch (e: AccountException) {
                if (e.isTemporaryFailure()) offline = true
                if (openId == id && (saved.isEmpty() || !e.isTemporaryFailure())) chatError = e.message
            } finally {
                if (openId == id) loadingMessages = false
            }
            if (openId != id) return@launch
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
        post(kind, body, attached = Attached(id, path, file))
    }

    /** La photo ou le vocal d'un message : son identifiant fixe le chemin du fichier. */
    private class Attached(val id: String, val path: String, val file: MediaFile)

    private fun post(kind: MessageKind, body: String, replyTo: String? = null, attached: Attached? = null) {
        val conv = openId ?: return
        val id = attached?.id ?: newMessageId()
        val draft = ChatMessage(id, conv, myId, body, Clock.System.now(), kind, attached?.path, replyTo, pending = true)
        messages = messages + draft
        scope.launch { queue(draft, attached?.file) }
    }

    /** Range le message dans la file de l'appareil, puis réveille l'envoi. */
    private suspend fun queue(message: ChatMessage, file: MediaFile?) {
        try {
            cache.enqueue(message, file)
            wakeOutbox()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            setState(message.id, pending = false, failed = true)
            chatError = "Ce message n'a pas pu être gardé sur l'appareil. Touche-le pour réessayer."
        }
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

    /** Mes photos et vocaux pas encore partis : ils sont sur l'appareil, pas encore sur le serveur. */
    private suspend fun restoreQueuedMedia(list: List<ChatMessage>) {
        list.filter { (it.pending || it.failed) && it.mediaPath != null }.forEach { m ->
            val path = m.mediaPath ?: return@forEach
            if (media.containsKey(path)) return@forEach
            local(null) { queuedMedia(m.id) }?.let { media[path] = it.bytes }
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

    /** Un message refusé, touché : il retourne dans la file. */
    fun retry(message: ChatMessage) {
        setState(message.id, pending = true, failed = false)
        chatError = null
        scope.launch {
            val file = local(null) { queuedMedia(message.id) } ?: pendingUploads[message.id]
            queue(message.copy(pending = true, failed = false), file)
        }
    }

    /** Tourne tant que l'écran principal vit : vide la file, attend, réessaie de plus en plus loin. */
    private suspend fun runOutbox() {
        var attempt = 0
        while (true) {
            if (flushOutbox()) {
                attempt = 0
                outboxWake.receive()
            } else {
                offline = true
                withTimeoutOrNull(outboxRetryDelay(attempt++)) { outboxWake.receive() }
            }
        }
    }

    /**
     * Envoie la file dans l'ordre où les messages ont été écrits. S'arrête au premier échec
     * de réseau (faux : la suite attend son tour) ; un message refusé par le serveur est
     * marqué « non envoyé » et n'empêche pas les suivants de partir.
     */
    internal suspend fun flushOutbox(): Boolean {
        for (m in local(emptyList()) { queued() }) {
            try {
                deliver(m)
            } catch (e: AccountException) {
                if (e.isTemporaryFailure()) return false
                local(Unit) { markFailed(m.id) }
                setState(m.id, pending = false, failed = true)
                if (m.conversationId == openId) chatError = e.message
            }
        }
        return true
    }

    /** Range la photo ou le vocal s'il y en a un, puis envoie le message. */
    private suspend fun deliver(m: ChatMessage) {
        val path = m.mediaPath
        val file = if (path != null) local(null) { queuedMedia(m.id) } ?: pendingUploads[m.id] else null
        if (path != null && file != null) service.uploadMedia(path, file.bytes, file.contentType)
        val sent = service.send(m)
        local(Unit) { saveSent(sent) }
        pendingUploads.remove(m.id)
        offline = false
        messages = messages.map { if (it.id == m.id) sent.copy(createdAt = it.createdAt) else it }
    }

    private fun setState(id: String, pending: Boolean, failed: Boolean) {
        messages = messages.map { if (it.id == id) it.copy(pending = pending, failed = failed) else it }
    }

    /** Le cache est un confort : s'il échoue (disque plein…), le chat continue sans lui. */
    private suspend fun <T> local(fallback: T, block: suspend ChatCache.() -> T): T = try {
        cache.block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
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
                local(Unit) { saveConversations(conversations) }
                openId = null
            }
        }
    }

    fun block() {
        val conv = open ?: return
        act {
            service.block(conv.partnerId)
            conversations = conversations.filterNot { it.id == conv.id }
            local(Unit) { saveConversations(conversations) }
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
                local(Unit) { saveConversations(conversations) }
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
        local(Unit) { saveSent(message) }
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

/** Ce qui vient du serveur, plus mes messages pas encore partis (ou refusés), dans l'ordre. */
internal fun mergeMessages(server: List<ChatMessage>, current: List<ChatMessage>): List<ChatMessage> {
    val known = server.map { it.id }.toSet()
    val waiting = current.filter { (it.pending || it.failed) && it.id !in known }
    return (server + waiting).sortedBy { it.createdAt }
}

/** 2 s, 5 s, 10 s, 30 s, puis une fois par minute. */
internal fun outboxRetryDelay(attempt: Int): Duration =
    listOf(2, 5, 10, 30, 60)[attempt.coerceIn(0, 4)].seconds
