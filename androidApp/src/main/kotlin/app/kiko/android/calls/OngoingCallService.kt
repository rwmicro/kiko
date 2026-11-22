package app.kiko.android.calls

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Garde l'appel vivant quand on quitte l'app : Android laisse le micro à un service au premier plan,
 * déclaré comme appel téléphonique. Sa notification permet de revenir à l'appel ou de raccrocher.
 */
class OngoingCallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getStringExtra(ExtraCall)
        if (callId == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = CallNotifications.ongoing(
            this,
            callId,
            intent.getStringExtra(ExtraName).orEmpty(),
            intent.getBooleanExtra(ExtraConnected, false),
        )
        val types = if (Build.VERSION.SDK_INT >= 30) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, CallNotifications.OngoingId, notification, types)
        } catch (e: Exception) {
            // Démarré depuis l'arrière-plan (décroché depuis la voiture, par exemple) : l'appel continue tant que l'app vit.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val ExtraCall = "app.kiko.call_id"
        private const val ExtraName = "app.kiko.partner_name"
        private const val ExtraConnected = "app.kiko.connected"

        fun start(context: Context, callId: String, partnerName: String, connected: Boolean) {
            val intent = Intent(context, OngoingCallService::class.java)
                .putExtra(ExtraCall, callId)
                .putExtra(ExtraName, partnerName)
                .putExtra(ExtraConnected, connected)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OngoingCallService::class.java))
        }
    }
}
