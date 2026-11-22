package app.kiko.android.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kiko.android.MainActivity
import app.kiko.calls.Calls
import app.kiko.calls.SupabaseCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** « Refuser » sur la sonnerie, « Raccrocher » sur la notification d'appel en cours. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(MainActivity.ExtraCall) ?: return
        CallNotifications.cancelIncoming(context)
        val state = Calls.controller.state.value
        if (state.callId == callId && state.busy) {
            Calls.hangUp()
            return
        }
        // L'app a été relancée entre-temps : on prévient quand même le serveur.
        OngoingCallService.stop(context)
        val result = goAsync()
        scope.launch {
            try {
                runCatching { SupabaseCallService().end(callId) }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val Decline = "app.kiko.call.DECLINE"
        const val HangUp = "app.kiko.call.HANG_UP"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
