package app.kiko.android.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import app.kiko.android.MainActivity
import app.kiko.android.R
import app.kiko.android.push.KikoNotifier

/** Les notifications d'un appel : la sonnerie (plein écran si verrouillé), l'appel en cours, l'appel manqué. */
object CallNotifications {
    const val ChannelIncoming = "incoming_calls"
    const val ChannelOngoing = "ongoing_calls"
    const val IncomingId = 40
    const val OngoingId = 41
    private const val MissedId = 42

    fun createChannels(context: Context) {
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ChannelIncoming, "Appels entrants", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Quand un partenaire t'appelle sur Kiko."
                setSound(
                    ringtone,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ChannelOngoing, "Appel en cours", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Reste affichée pendant un appel, pour revenir à l'appel ou raccrocher."
                setSound(null, null)
            },
        )
    }

    fun showIncoming(context: Context, callId: String, callerName: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val caller = Person.Builder().setName(callerName).setImportant(true).build()
        val notification = NotificationCompat.Builder(context, ChannelIncoming)
            .setSmallIcon(R.drawable.ic_stat_kiko)
            .setContentTitle(callerName)
            .setContentText("t'appelle sur Kiko")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(45_000)
            .setContentIntent(activity(context, MainActivity.ActionShowCall, callId, 1))
            .setFullScreenIntent(activity(context, MainActivity.ActionShowCall, callId, 2), true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    broadcast(context, CallActionReceiver.Decline, callId),
                    activity(context, MainActivity.ActionAnswerCall, callId, 3),
                ),
            )
            .build()
        // La sonnerie se répète jusqu'à ce qu'on réponde.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        runCatching { NotificationManagerCompat.from(context).notify(IncomingId, notification) }
    }

    fun ongoing(context: Context, callId: String, partnerName: String, connected: Boolean): Notification {
        val partner = Person.Builder().setName(partnerName.ifBlank { "Kiko" }).setImportant(true).build()
        return NotificationCompat.Builder(context, ChannelOngoing)
            .setSmallIcon(R.drawable.ic_stat_kiko)
            .setContentTitle(partnerName.ifBlank { "Appel Kiko" })
            .setContentText(if (connected) "Appel en cours" else "Ça sonne…")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(connected)
            .setContentIntent(activity(context, MainActivity.ActionShowCall, callId, 4))
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(partner, broadcast(context, CallActionReceiver.HangUp, callId)))
            .build()
    }

    fun showMissed(context: Context, callerName: String, conversationId: String?) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() || callerName.isBlank()) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.ExtraConversation, conversationId)
            .putExtra(MainActivity.ExtraType, "call_missed")
        val notification = NotificationCompat.Builder(context, KikoNotifier.ChannelCalls)
            .setSmallIcon(R.drawable.ic_stat_kiko)
            .setContentTitle("Appel manqué")
            .setContentText("$callerName a essayé de t'appeler.")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 5, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(MissedId, notification) }
    }

    fun cancelIncoming(context: Context) {
        NotificationManagerCompat.from(context).cancel(IncomingId)
    }

    private fun activity(context: Context, action: String, callId: String, request: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.ExtraCall, callId)
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun broadcast(context: Context, action: String, callId: String): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(MainActivity.ExtraCall, callId)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
