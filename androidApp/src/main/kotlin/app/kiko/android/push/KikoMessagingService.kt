package app.kiko.android.push

import app.kiko.notifications.AppVisibility
import app.kiko.notifications.PushRegistry
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Reçoit les messages FCM (données seulement) et en fait des notifications. */
class KikoMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushRegistry.onToken(token, "android", "alert", null, false)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // La discussion est ouverte à l'écran : le message s'y affiche déjà.
        val conv = data["conversation_id"]
        if (AppVisibility.foreground && conv != null && conv == AppVisibility.openConversation &&
            data["type"] in setOf("message", "correction", "call", "call_accepted")
        ) {
            return
        }
        KikoNotifier.show(applicationContext, data)
    }
}
