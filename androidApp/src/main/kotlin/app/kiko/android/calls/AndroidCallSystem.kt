package app.kiko.android.calls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallsManager
import app.kiko.calls.CallEndReason
import app.kiko.calls.CallSystem
import app.kiko.calls.Calls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * L'équivalent Android de CallKit :
 * - la notification d'appel (CallStyle), plein écran quand le téléphone est verrouillé ;
 * - Telecom (androidx.core.telecom) : Android sait qu'un appel est en cours, le relie au casque
 *   Bluetooth, à la montre ou à la voiture, et le met en pause si un appel téléphonique arrive ;
 * - un service au premier plan, pour garder le micro quand l'app passe en arrière-plan.
 *
 * Rien n'est inscrit dans le journal d'appels du téléphone.
 */
class AndroidCallSystem(context: Context) : CallSystem {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val names = mutableMapOf<String, String>()
    private val conversations = mutableMapOf<String, String>()
    private val sessions = mutableMapOf<String, TelecomSession>()

    private val telecom: CallsManager? by lazy {
        if (!allowed()) return@lazy null
        runCatching { CallsManager(app).also { it.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE) } }.getOrNull()
    }

    override fun reportIncoming(callId: String, conversationId: String, callerName: String) {
        scope.launch {
            names[callId] = callerName
            conversations[callId] = conversationId
            CallNotifications.showIncoming(app, callId, callerName)
            addToTelecom(callId, callerName, incoming = true)
        }
    }

    override fun reportOutgoing(callId: String, conversationId: String, partnerName: String) {
        scope.launch {
            names[callId] = partnerName
            conversations[callId] = conversationId
            OngoingCallService.start(app, callId, partnerName, connected = false)
            addToTelecom(callId, partnerName, incoming = false)
        }
    }

    override fun reportConnected(callId: String) {
        scope.launch {
            CallNotifications.cancelIncoming(app)
            OngoingCallService.start(app, callId, names[callId].orEmpty(), connected = true)
            sessions[callId]?.commands?.trySend(Command.Active)
        }
    }

    override fun reportEnded(callId: String, reason: CallEndReason) {
        scope.launch {
            CallNotifications.cancelIncoming(app)
            OngoingCallService.stop(app)
            if (reason == CallEndReason.Missed) {
                CallNotifications.showMissed(app, names[callId].orEmpty(), conversations[callId])
            }
            sessions.remove(callId)?.end(
                when (reason) {
                    CallEndReason.Local -> DisconnectCause.LOCAL
                    CallEndReason.Remote -> DisconnectCause.REMOTE
                    CallEndReason.Declined -> DisconnectCause.REJECTED
                    CallEndReason.Missed -> DisconnectCause.MISSED
                    CallEndReason.Failed -> DisconnectCause.ERROR
                },
            )
            names.remove(callId)
            conversations.remove(callId)
        }
    }

    // Le micro est coupé par LiveKit ; Telecom n'a rien à en savoir.
    override fun reportMuted(callId: String, muted: Boolean) = Unit

    private fun allowed(): Boolean =
        app.checkSelfPermission(Manifest.permission.MANAGE_OWN_CALLS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Vérifiée par allowed() avant de créer CallsManager.
    private fun addToTelecom(callId: String, name: String, incoming: Boolean) {
        val manager = telecom ?: return
        if (sessions.containsKey(callId)) return
        val session = TelecomSession()
        sessions[callId] = session
        session.job = scope.launch {
            try {
                manager.addCall(
                    CallAttributesCompat(
                        displayName = name,
                        address = Uri.parse("kiko:call/$callId"),
                        direction = if (incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
                        callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
                        callCapabilities = 0,
                    ),
                    onAnswer = { Calls.answer(callId) },
                    onDisconnect = { Calls.endFromSystem(callId) },
                    onSetActive = {},
                    // Pas de mise en attente : un appel téléphonique qui arrive met fin à l'appel Kiko.
                    onSetInactive = { Calls.endFromSystem(callId) },
                ) {
                    launch {
                        for (command in session.commands) {
                            when (command) {
                                Command.Active -> if (incoming) answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL) else setActive()
                                is Command.End -> {
                                    disconnect(DisconnectCause(command.cause))
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Telecom refuse (vieux téléphone, autre appel en cours) : la notification et le service suffisent.
                sessions.remove(callId)
            }
        }
    }

    private sealed interface Command {
        data object Active : Command
        data class End(val cause: Int) : Command
    }

    private inner class TelecomSession {
        val commands = Channel<Command>(Channel.UNLIMITED)
        var job: Job? = null

        fun end(cause: Int) {
            commands.trySend(Command.End(cause))
            val running = job
            scope.launch {
                delay(3_000)
                running?.cancel()
            }
        }
    }
}
