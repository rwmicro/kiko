@file:OptIn(ExperimentalTime::class)

package app.kiko.calls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Le son de l'appel : LiveKit, en Kotlin sur Android (androidApp/.../calls/LiveKitMedia.kt),
 * en Swift sur iPhone (iosApp/iosApp/KikoCalls.swift). Un seul appel à la fois.
 */
interface CallMedia {
    /** Entre dans la salle et ouvre le micro. Les nouvelles arrivent par [listener]. */
    fun connect(url: String, token: String, listener: CallMediaListener)
    fun setMuted(muted: Boolean)
    fun setSpeaker(on: Boolean)
    fun disconnect()
}

interface CallMediaListener {
    /** Dans la salle, micro ouvert. [partnerHere] : l'autre y est déjà. */
    fun onConnected(partnerHere: Boolean)
    fun onPartnerJoined()
    fun onPartnerLeft()
    fun onReconnecting()
    fun onReconnected()
    /** Connexion perdue ou refusée : l'appel s'arrête. */
    fun onFailed(message: String)
}

/** Pourquoi un appel s'arrête, pour l'écran du système (CallKit, Telecom). */
enum class CallEndReason { Local, Remote, Declined, Missed, Failed }

/**
 * Le téléphone lui-même : CallKit sur iPhone, Telecom et la notification d'appel sur Android.
 * C'est ce qui fait sonner l'appareil, l'affiche à l'écran verrouillé, et le relie aux écouteurs
 * Bluetooth ou à la voiture. Chaque méthode peut être appelée deux fois sans dommage.
 */
interface CallSystem {
    fun reportIncoming(callId: String, conversationId: String, callerName: String)
    fun reportOutgoing(callId: String, conversationId: String, partnerName: String)
    /** L'autre a décroché (appel sortant), ou on a décroché (appel entrant) : l'appel est en cours. */
    fun reportConnected(callId: String)
    fun reportEnded(callId: String, reason: CallEndReason)
    fun reportMuted(callId: String, muted: Boolean)
}

enum class CallPhase {
    Idle,
    /** On appelle : ça sonne chez l'autre. */
    Calling,
    /** On nous appelle. */
    Incoming,
    /** Décroché, on entre dans la salle. */
    Connecting,
    Active,
    Reconnecting,
    /** Fini : l'écran montre pourquoi quelques secondes, puis disparaît. */
    Ended,
}

data class CallState(
    val phase: CallPhase = CallPhase.Idle,
    val callId: String? = null,
    val conversationId: String? = null,
    val partnerName: String = "",
    val outgoing: Boolean = false,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val startedAt: Instant? = null,
    val message: String? = null,
) {
    val busy: Boolean get() = phase != CallPhase.Idle && phase != CallPhase.Ended
}

/**
 * Le chef d'orchestre d'un appel, partagé par l'interface et par le code natif (push VoIP, CallKit,
 * notification Android) : un appel peut arriver app fermée, et se décrocher sans ouvrir l'écran.
 */
class CallController(
    private val service: () -> CallService,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val current = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = current

    /** Posés au démarrage par le code natif. Sans eux (aperçu web), on ne peut pas appeler. */
    var media: CallMedia? = null
    var system: CallSystem? = null

    private var watcher: Job? = null
    private var ringTimer: Job? = null
    private var closer: Job? = null

    val supported: Boolean get() = media != null

    /** Appeler un partenaire depuis sa discussion. */
    fun call(conversationId: String, partnerName: String) {
        if (current.value.busy) return
        val media = media ?: return finish(null, CallEndReason.Failed, "Les appels ne marchent que dans l'app iPhone ou Android.")
        closer?.cancel()
        current.value = CallState(CallPhase.Calling, null, conversationId, partnerName, outgoing = true)
        scope.launch {
            val started = try {
                service().start(conversationId, partnerName)
            } catch (e: Exception) {
                return@launch finish(null, CallEndReason.Failed, e.message ?: "L'appel n'a pas pu se faire.")
            }
            if (current.value.phase != CallPhase.Calling || current.value.callId != null) {
                // Raccroché pendant la mise en relation : l'appel ne doit pas sonner chez l'autre.
                runCatching { service().end(started.id) }
                return@launch
            }
            current.update { it.copy(callId = started.id) }
            system?.reportOutgoing(started.id, conversationId, partnerName)
            if (started.status == CallStatus.Active) {
                // L'autre nous appelait au même moment : son appel est décroché.
                current.update { it.copy(phase = CallPhase.Connecting, outgoing = false) }
            } else {
                watch(started.id)
                ringTimer = scope.launch {
                    delay(RingTimeout)
                    if (current.value.callId == started.id && current.value.phase == CallPhase.Calling) {
                        hangUp(missed = true)
                    }
                }
            }
            join(started.id, media)
        }
    }

    /** Une sonnerie arrive (push VoIP sur iPhone, FCM sur Android). */
    fun onIncoming(callId: String, conversationId: String, callerName: String) {
        val now = current.value
        if (now.callId == callId) return
        if (now.busy) {
            // Déjà en ligne : l'autre entend que ce n'est pas le moment.
            scope.launch { runCatching { service().end(callId) } }
            system?.reportEnded(callId, CallEndReason.Declined)
            return
        }
        closer?.cancel()
        current.value = CallState(CallPhase.Incoming, callId, conversationId, callerName, outgoing = false)
        system?.reportIncoming(callId, conversationId, callerName)
        ringTimer = scope.launch {
            delay(RingTimeout)
            if (current.value.callId == callId && current.value.phase == CallPhase.Incoming) {
                finish(callId, CallEndReason.Missed, "Appel manqué de $callerName")
            }
        }
    }

    /** L'appelant a raccroché avant qu'on décroche (push call_cancel). */
    fun onCancelled(callId: String) {
        val now = current.value
        if (now.callId != callId || now.phase != CallPhase.Incoming) {
            system?.reportEnded(callId, CallEndReason.Missed)
            return
        }
        finish(callId, CallEndReason.Missed, "Appel manqué de ${now.partnerName}")
    }

    /** Décrocher : depuis l'écran de l'app, CallKit, ou la notification Android. */
    fun answer(callId: String) {
        val now = current.value
        if (now.callId != callId || now.phase != CallPhase.Incoming) return
        val media = media ?: return
        ringTimer?.cancel()
        current.update { it.copy(phase = CallPhase.Connecting) }
        scope.launch {
            val status = try {
                service().answer(callId)
            } catch (e: Exception) {
                return@launch finish(callId, CallEndReason.Failed, e.message ?: "L'appel n'a pas pu se faire.")
            }
            if (status != CallStatus.Active) {
                return@launch finish(callId, CallEndReason.Remote, "${now.partnerName} a raccroché.")
            }
            join(callId, media)
        }
    }

    /** Raccrocher, refuser ou annuler, depuis l'app. */
    fun hangUp(missed: Boolean = false) {
        val now = current.value
        if (!now.busy) return
        val id = now.callId
        if (id != null) scope.launch { runCatching { service().end(id) } }
        val message = when {
            missed -> "${now.partnerName} n'a pas répondu."
            now.phase == CallPhase.Incoming -> null
            now.startedAt != null -> "Appel terminé · ${duration(now.startedAt)}"
            else -> null
        }
        finish(id, if (now.phase == CallPhase.Incoming) CallEndReason.Declined else CallEndReason.Local, message, tellSystem = true)
    }

    /** Le système a raccroché (bouton de CallKit, écouteurs, voiture) : même chose, sans le lui redire. */
    fun endFromSystem(callId: String) {
        val now = current.value
        if (now.callId != callId || !now.busy) return
        scope.launch { runCatching { service().end(callId) } }
        finish(callId, CallEndReason.Local, now.startedAt?.let { "Appel terminé · ${duration(it)}" }, tellSystem = false)
    }

    fun toggleMute() = setMuted(!current.value.muted)

    fun setMuted(muted: Boolean) {
        val now = current.value
        if (!now.busy || now.muted == muted) return
        current.update { it.copy(muted = muted) }
        media?.setMuted(muted)
        now.callId?.let { system?.reportMuted(it, muted) }
    }

    fun toggleSpeaker() {
        val on = !current.value.speaker
        current.update { it.copy(speaker = on) }
        media?.setSpeaker(on)
    }

    /** Fermer tout de suite l'écran « appel terminé ». */
    fun dismiss() {
        if (current.value.phase == CallPhase.Ended) current.value = CallState()
    }

    private suspend fun join(callId: String, media: CallMedia) {
        val access = try {
            service().access(callId)
        } catch (e: Exception) {
            runCatching { service().end(callId) }
            return finish(callId, CallEndReason.Failed, e.message ?: "L'appel n'a pas pu se faire.")
        }
        if (current.value.callId != callId || !current.value.busy) return
        media.connect(access.url, access.token, Listener(callId))
    }

    /** L'appelant regarde si l'autre décroche ou refuse ; LiveKit dit ensuite quand il arrive. */
    private fun watch(callId: String) {
        watcher?.cancel()
        watcher = scope.launch {
            while (current.value.callId == callId && current.value.phase == CallPhase.Calling) {
                delay(PollEvery)
                val status = runCatching { service().status(callId) }.getOrNull() ?: continue
                if (current.value.callId != callId || current.value.phase != CallPhase.Calling) break
                when (status) {
                    CallStatus.Declined -> finish(callId, CallEndReason.Declined, "${current.value.partnerName} ne peut pas répondre pour l'instant.")
                    CallStatus.Missed -> finish(callId, CallEndReason.Missed, "${current.value.partnerName} n'a pas répondu.")
                    CallStatus.Cancelled, CallStatus.Ended -> finish(callId, CallEndReason.Remote, null)
                    CallStatus.Active, CallStatus.Ringing -> Unit
                }
            }
        }
    }

    private inner class Listener(private val callId: String) : CallMediaListener {
        private fun mine() = current.value.callId == callId && current.value.busy

        override fun onConnected(partnerHere: Boolean) {
            if (!mine()) return
            if (current.value.muted) media?.setMuted(true)
            if (partnerHere || current.value.phase == CallPhase.Connecting) live()
        }

        override fun onPartnerJoined() {
            if (mine()) live()
        }

        override fun onPartnerLeft() {
            if (!mine() || current.value.phase == CallPhase.Calling) return
            val started = current.value.startedAt
            scope.launch { runCatching { service().end(callId) } }
            finish(callId, CallEndReason.Remote, started?.let { "Appel terminé · ${duration(it)}" })
        }

        override fun onReconnecting() {
            if (mine() && current.value.phase == CallPhase.Active) current.update { it.copy(phase = CallPhase.Reconnecting) }
        }

        override fun onReconnected() {
            if (mine() && current.value.phase == CallPhase.Reconnecting) current.update { it.copy(phase = CallPhase.Active) }
        }

        override fun onFailed(message: String) {
            if (!mine()) return
            scope.launch { runCatching { service().end(callId) } }
            finish(callId, CallEndReason.Failed, "La connexion a été perdue.")
        }

        private fun live() {
            if (current.value.phase == CallPhase.Active) return
            ringTimer?.cancel()
            watcher?.cancel()
            current.update { it.copy(phase = CallPhase.Active, startedAt = it.startedAt ?: clock.now()) }
            system?.reportConnected(callId)
        }
    }

    private fun finish(callId: String?, reason: CallEndReason, message: String?, tellSystem: Boolean = true) {
        ringTimer?.cancel()
        watcher?.cancel()
        val wasBusy = current.value.busy
        if (wasBusy) media?.disconnect()
        if (tellSystem && callId != null) system?.reportEnded(callId, reason)
        current.update {
            it.copy(phase = CallPhase.Ended, message = message, muted = false, speaker = false)
        }
        closer?.cancel()
        closer = scope.launch {
            delay(if (message == null) 1.seconds else 3.seconds)
            if (current.value.phase == CallPhase.Ended) current.value = CallState()
        }
    }

    private fun duration(since: Instant): String {
        val total = (clock.now() - since).inWholeSeconds.coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    companion object {
        /** Comme côté serveur (call_ring_seconds) : au-delà, l'appel est manqué. */
        val RingTimeout = 45.seconds
        val PollEvery = 2.seconds
    }
}

/** L'appel en cours de l'app, partagé par l'interface et le code natif. */
object Calls {
    val controller: CallController by lazy {
        CallController({ service }, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    private val service: CallService by lazy { SupabaseCallService() }

    /** Appelée par le code natif au démarrage de l'app. */
    fun install(media: CallMedia?, system: CallSystem?) {
        controller.media = media
        controller.system = system
    }

    // Raccourcis pour Swift et pour les récepteurs Android.
    fun onIncoming(callId: String, conversationId: String, callerName: String) = controller.onIncoming(callId, conversationId, callerName)
    fun onCancelled(callId: String) = controller.onCancelled(callId)
    fun answer(callId: String) = controller.answer(callId)
    fun endFromSystem(callId: String) = controller.endFromSystem(callId)
    fun setMuted(muted: Boolean) = controller.setMuted(muted)
    fun hangUp() = controller.hangUp()
}
