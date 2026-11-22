package app.kiko.android.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.kiko.notifications.NotificationActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Les boutons des notifications : répondre, marquer comme lu, remercier, féliciter. Sans ouvrir l'app. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conv = intent.getStringExtra(ExtraConversation) ?: return
        val id = intent.getIntExtra(ExtraNotification, 0)
        val result = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    Reply -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KeyReply)?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            if (NotificationActions.reply(conv, text)) {
                                KikoNotifier.replied(context, conv, text, id)
                            } else {
                                KikoNotifier.replyFailed(context, conv, id)
                            }
                        }
                    }
                    MarkRead -> {
                        NotificationActions.markRead(conv)
                        KikoNotifier.cancel(context, id)
                    }
                    Thank -> {
                        NotificationActions.thank(conv)
                        KikoNotifier.cancel(context, id)
                    }
                    Bravo -> {
                        val days = intent.getStringExtra(ExtraValue).orEmpty()
                        NotificationActions.reply(conv, "🎉 Bravo pour nos $days jours !")
                        KikoNotifier.cancel(context, id)
                    }
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val Reply = "app.kiko.action.REPLY"
        const val MarkRead = "app.kiko.action.MARK_READ"
        const val Thank = "app.kiko.action.THANK"
        const val Bravo = "app.kiko.action.BRAVO"
        const val KeyReply = "reply"
        private const val ExtraConversation = "conversation_id"
        private const val ExtraNotification = "notification_id"
        private const val ExtraValue = "value"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Un PendingIntent par action et par notification ; « mutable » pour la réponse rapide (RemoteInput). */
        fun pending(context: Context, action: String, conv: String, notificationId: Int, mutable: Boolean = false, extra: String? = null): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                (action + notificationId).hashCode(),
                Intent(context, ActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(ExtraConversation, conv)
                    .putExtra(ExtraNotification, notificationId)
                    .putExtra(ExtraValue, extra),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE),
            )
    }
}
