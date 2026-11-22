package app.kiko.android.calls

import android.content.Context
import app.kiko.calls.CallMedia
import app.kiko.calls.CallMediaListener
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Le son de l'appel sur Android, avec le SDK LiveKit. Une salle par appel, micro seulement.
 * L'audio passe par l'écouteur au départ (comme un appel téléphonique), ou par le casque
 * Bluetooth ou filaire s'il y en a un.
 */
class LiveKitMedia(context: Context) : CallMedia {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private var job: Job? = null

    override fun connect(url: String, token: String, listener: CallMediaListener) {
        scope.launch {
            close()
            val room = LiveKit.create(app)
            room.audioSwitchHandler?.preferredDeviceList = listOf(
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.Earpiece::class.java,
                AudioDevice.Speakerphone::class.java,
            )
            this@LiveKitMedia.room = room
            var closing = false
            job = launch {
                launch {
                    room.events.events.collect { event ->
                        when (event) {
                            is RoomEvent.ParticipantConnected -> listener.onPartnerJoined()
                            is RoomEvent.ParticipantDisconnected -> listener.onPartnerLeft()
                            is RoomEvent.Reconnecting -> listener.onReconnecting()
                            is RoomEvent.Reconnected -> listener.onReconnected()
                            is RoomEvent.Disconnected -> if (!closing) listener.onFailed(event.error?.message ?: event.reason.name)
                            else -> Unit
                        }
                    }
                }
                try {
                    room.connect(url, token)
                    room.localParticipant.setMicrophoneEnabled(true)
                    listener.onConnected(room.remoteParticipants.isNotEmpty())
                } catch (e: CancellationException) {
                    closing = true
                    throw e
                } catch (e: Exception) {
                    listener.onFailed(e.message ?: "LiveKit")
                }
            }
        }
    }

    override fun setMuted(muted: Boolean) {
        scope.launch { runCatching { room?.localParticipant?.setMicrophoneEnabled(!muted) } }
    }

    override fun setSpeaker(on: Boolean) {
        scope.launch {
            val handler = room?.audioSwitchHandler ?: return@launch
            val target = handler.availableAudioDevices.firstOrNull {
                if (on) it is AudioDevice.Speakerphone else it !is AudioDevice.Speakerphone
            }
            handler.selectDevice(target)
        }
    }

    override fun disconnect() {
        scope.launch { close() }
    }

    private fun close() {
        job?.cancel()
        job = null
        room?.let {
            it.disconnect()
            it.release()
        }
        room = null
    }
}
