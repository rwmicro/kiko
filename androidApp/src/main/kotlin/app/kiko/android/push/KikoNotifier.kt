package app.kiko.android.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.kiko.android.MainActivity
import app.kiko.android.R
import app.kiko.notifications.WidgetBridge
import java.time.Instant
import kotlin.math.abs

/**
 * Construit les notifications Android à partir des données FCM (voir supabase/functions/_shared/payloads.ts).
 *
 * - Messages : MessagingStyle, avec la réponse rapide et « Marquer comme lu » (maquette 26),
 *   regroupés sous Kiko, et rangés dans la section Conversations d'Android 11+.
 * - Corrections : l'ancien texte barré, le nouveau en gras, « Merci » et « Répondre » (maquette 23).
 * - Appel qui approche et série en danger : mises à jour en direct d'Android 16 (maquette 36),
 *   avec la pastille de la barre d'état ; une notification permanente avant Android 16.
 */
object KikoNotifier {
    const val ChannelMessages = "messages"
    const val ChannelCorrections = "corrections"
    const val ChannelCalls = "calls"
    const val ChannelReminders = "reminders"
    const val ChannelLive = "live"
    private const val Group = "app.kiko.chats"
    private const val SummaryId = 1
    private const val ReminderId = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(ChannelMessages, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Quand un partenaire t'écrit."
            },
            NotificationChannel(ChannelCorrections, "Corrections", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Quand un partenaire corrige ton message."
            },
            NotificationChannel(ChannelCalls, "Appels", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Propositions d'appel et appels acceptés."
            },
            NotificationChannel(ChannelLive, "En direct", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Appel qui approche, série qui va se perdre."
                setSound(null, null)
            },
            NotificationChannel(ChannelReminders, "Rappels", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Séries, anniversaires d'amitié. Un par jour au plus, jamais la nuit."
            },
        ).forEach(manager::createNotificationChannel)
    }

    fun show(context: Context, data: Map<String, String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        when (data["type"]) {
            "message" -> message(context, data)
            "correction" -> correction(context, data)
            "request", "call", "call_accepted" -> simple(context, data, if (data["type"] == "request") ChannelMessages else ChannelCalls, chatId(data))
            "call_soon" -> liveCall(context, data)
            "streak_risk" -> liveStreak(context, data)
            "milestone" -> milestone(context, data)
            "reengage", "anniversary" -> simple(context, data, ChannelReminders, ReminderId)
        }
        updateWidgets(context, data)
    }

    // ------------------------------------------------------------------ messages

    private fun message(context: Context, data: Map<String, String>) {
        val conv = data["conversation_id"] ?: return
        val name = data["sender_name"] ?: "Kiko"
        val id = chatId(data)
        val sender = person(name, data["sender_id"] ?: name)
        val style = activeStyle(context, id) ?: NotificationCompat.MessagingStyle(me())
        style.addMessage(NotificationCompat.MessagingStyle.Message(data["preview"] ?: data["body"].orEmpty(), System.currentTimeMillis(), sender))
        pushShortcut(context, conv, name, sender)
        val builder = base(context, ChannelMessages, data)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(conv)
            .setGroup(Group)
            .addAction(replyAction(context, conv, name, id))
            .addAction(action(context, ActionReceiver.MarkRead, "Marquer comme lu", conv, id))
        post(context, id, builder.build())
        summary(context)
    }

    /** Après une réponse depuis la notification, on l'ajoute au fil (Android l'exige pour arrêter l'attente). */
    fun replied(context: Context, conversationId: String, text: String, notificationId: Int) {
        val style = activeStyle(context, notificationId) ?: return
        style.addMessage(NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), null as Person?))
        val builder = NotificationCompat.Builder(context, ChannelMessages)
            .setSmallIcon(R.drawable.ic_stat_kiko)
            .setStyle(style)
            .setShortcutId(conversationId)
            .setGroup(Group)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openChat(context, conversationId, null, "message", notificationId))
        post(context, notificationId, builder.build())
    }

    fun replyFailed(context: Context, conversationId: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(context, ChannelMessages)
            .setSmallIcon(R.drawable.ic_stat_kiko)
            .setContentTitle("La réponse n'est pas partie")
            .setContentText("Ouvre la discussion pour réessayer.")
            .setAutoCancel(true)
            .setContentIntent(openChat(context, conversationId, null, "message", notificationId))
        post(context, notificationId, builder.build())
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun correction(context: Context, data: Map<String, String>) {
        val conv = data["conversation_id"] ?: return
        val name = data["sender_name"] ?: "Kiko"
        val id = chatId(data) + 1
        val text = SpannableStringBuilder().apply {
            data["original"]?.let {
                append(it, StrikethroughSpan(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("\n")
            }
            append(data["corrected"].orEmpty(), StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            data["note"]?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
        }
        val builder = base(context, ChannelCorrections, data)
            .setContentText(data["corrected"])
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setLargeIcon(initials(name))
            .setGroup(Group)
            .addAction(action(context, ActionReceiver.Thank, "Merci 🙏", conv, id))
            .addAction(replyAction(context, conv, name, id))
        post(context, id, builder.build())
        summary(context)
    }

    private fun simple(context: Context, data: Map<String, String>, channel: String, id: Int) {
        val builder = base(context, channel, data)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"]))
        (data["sender_name"] ?: data["partner_name"])?.let { builder.setLargeIcon(initials(it)) }
        if (channel == ChannelReminders) builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
        post(context, id, builder.build())
    }

    private fun milestone(context: Context, data: Map<String, String>) {
        val conv = data["conversation_id"] ?: return
        val days = data["streak"] ?: ""
        val id = chatId(data) + 2
        val builder = base(context, ChannelReminders, data)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"]))
            .setLargeIcon(flame(days))
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "Envoyer un bravo",
                    ActionReceiver.pending(context, ActionReceiver.Bravo, conv, id, extra = days),
                ).build(),
            )
        post(context, id, builder.build())
    }

    // ------------------------------------------------------------------ en direct

    private fun liveCall(context: Context, data: Map<String, String>) {
        val conv = data["conversation_id"] ?: return
        val at = data["at"]?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return
        val end = at + (data["minutes"]?.toIntOrNull() ?: 15) * 60_000L
        val id = (data["call_id"] ?: conv).hashCode()
        val open = openChat(context, conv, data["outbox_id"], "call_soon", id)
        val title = "Appel avec ${data["partner_name"] ?: "ton partenaire"}"
        val text = data["body"].orEmpty()
        val timeout = (end - System.currentTimeMillis()).coerceAtLeast(60_000L)
        val notification = if (Build.VERSION.SDK_INT >= 36) {
            Notification.Builder(context, ChannelLive)
                .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_stat_kiko))
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setWhen(at)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShortCriticalText("Appel")
                .setRequestPromotedOngoing(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .setTimeoutAfter(timeout)
                .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_kiko), "Rejoindre", open).build())
                .build()
        } else {
            NotificationCompat.Builder(context, ChannelLive)
                .setSmallIcon(R.drawable.ic_stat_kiko)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setWhen(at)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setTimeoutAfter(timeout)
                .addAction(0, "Rejoindre", open)
                .build()
        }
        post(context, id, notification)
    }

    private fun liveStreak(context: Context, data: Map<String, String>) {
        val conv = data["conversation_id"] ?: return
        val deadline = data["deadline"]?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        if (deadline == null || deadline <= System.currentTimeMillis()) {
            simple(context, data, ChannelReminders, ReminderId)
            return
        }
        val id = chatId(data) + 3
        val partner = data["partner_name"] ?: "ton partenaire"
        val open = openChat(context, conv, data["outbox_id"], "streak_risk", id)
        val reply = replyAction(context, conv, partner, id)
        val timeout = deadline - System.currentTimeMillis()
        val dayMs = 24 * 3600 * 1000L
        val elapsed = (100 - timeout * 100 / dayMs).toInt().coerceIn(0, 100)
        val notification = if (Build.VERSION.SDK_INT >= 36) {
            Notification.Builder(context, ChannelLive)
                .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_stat_kiko))
                .setContentTitle(data["title"] ?: "Sauve votre série")
                .setContentText(data["body"])
                .setContentIntent(open)
                .setOngoing(true)
                .setWhen(deadline)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShortCriticalText("${data["streak"] ?: ""} j")
                .setRequestPromotedOngoing(true)
                .setStyle(
                    Notification.ProgressStyle()
                        .addProgressSegment(Notification.ProgressStyle.Segment(100).setColor(0xFFF6B93B.toInt()))
                        .setProgress(elapsed)
                        .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_stat_flame)),
                )
                .setTimeoutAfter(timeout)
                .addAction(
                    Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_kiko), "Répondre à $partner", ActionReceiver.pending(context, ActionReceiver.Reply, conv, id, mutable = true))
                        .addRemoteInput(android.app.RemoteInput.Builder(ActionReceiver.KeyReply).setLabel("Répondre à $partner…").build())
                        .build(),
                )
                .build()
        } else {
            NotificationCompat.Builder(context, ChannelLive)
                .setSmallIcon(R.drawable.ic_stat_kiko)
                .setContentTitle(data["title"] ?: "Sauve votre série")
                .setContentText(data["body"])
                .setContentIntent(open)
                .setOngoing(true)
                .setWhen(deadline)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setProgress(100, elapsed, false)
                .setTimeoutAfter(timeout)
                .addAction(reply)
                .build()
        }
        post(context, id, notification)
    }

    // ------------------------------------------------------------------ outils

    private fun base(context: Context, channel: String, data: Map<String, String>): NotificationCompat.Builder {
        val conv = data["conversation_id"]
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_kiko)
            .setColor(0xFFDD3F1A.toInt())
            .setContentTitle(data["title"])
            .setContentText(data["body"])
            .setAutoCancel(true)
            .setSilent(data["quiet"] == "true")
            .setPriority(if (data["quiet"] == "true") NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
        if (conv != null) builder.setContentIntent(openChat(context, conv, data["outbox_id"], data["type"], chatId(data) + (data["type"]?.hashCode() ?: 0)))
        return builder
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun summary(context: Context) {
        val builder = NotificationCompat.Builder(context, ChannelMessages)
            .setSmallIcon(R.drawable.ic_stat_kiko)
            .setColor(0xFFDD3F1A.toInt())
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Kiko"))
            .setGroup(Group)
            .setGroupSummary(true)
            .setSilent(true)
            .setAutoCancel(true)
        post(context, SummaryId, builder.build())
    }

    fun chatId(data: Map<String, String>): Int = chatId(data["conversation_id"] ?: data["type"].orEmpty())

    fun chatId(conversationId: String): Int = (conversationId.hashCode() and 0x0FFFFFF0) + 16

    private fun openChat(context: Context, conv: String, outbox: String?, type: String?, request: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            request,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.ExtraConversation, conv)
                .putExtra(MainActivity.ExtraOutbox, outbox)
                .putExtra(MainActivity.ExtraType, type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun replyAction(context: Context, conv: String, name: String, id: Int): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, "Répondre", ActionReceiver.pending(context, ActionReceiver.Reply, conv, id, mutable = true))
            .addRemoteInput(RemoteInput.Builder(ActionReceiver.KeyReply).setLabel("Répondre à $name…").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()

    private fun action(context: Context, kind: String, label: String, conv: String, id: Int): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, label, ActionReceiver.pending(context, kind, conv, id))
            .setSemanticAction(if (kind == ActionReceiver.MarkRead) NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ else NotificationCompat.Action.SEMANTIC_ACTION_NONE)
            .setShowsUserInterface(false)
            .build()

    private fun activeStyle(context: Context, id: Int): NotificationCompat.MessagingStyle? {
        val manager = context.getSystemService(NotificationManager::class.java)
        val current = manager.activeNotifications.firstOrNull { it.id == id }?.notification ?: return null
        return NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(current)
    }

    private fun me(): Person = Person.Builder().setName("Moi").setKey("me").build()

    private fun person(name: String, key: String): Person =
        Person.Builder().setName(name).setKey(key).setIcon(IconCompat.createWithBitmap(initials(name))).build()

    /** Une discussion devient un raccourci : Android 11+ la range dans « Conversations ». */
    private fun pushShortcut(context: Context, conv: String, name: String, person: Person) {
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                ShortcutInfoCompat.Builder(context, conv)
                    .setShortLabel(name)
                    .setLongLived(true)
                    .setPerson(person)
                    .setIcon(IconCompat.createWithBitmap(initials(name)))
                    .setIntent(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(MainActivity.ExtraConversation, conv),
                    )
                    .build(),
            )
        }
    }

    private val tints = intArrayOf(0xFFF59B4B.toInt(), 0xFF0E9594.toInt(), 0xFFDD3F1A.toInt(), 0xFFC98A12.toInt(), 0xFF8C5A9E.toInt(), 0xFF3F7CAC.toInt())

    /** L'avatar sans photo de l'app : l'initiale sur une teinte tirée du nom. */
    fun initials(name: String, size: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tints[abs(name.hashCode()) % tints.size] }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = size * 0.45f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(name.trim().take(1).uppercase(), size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return bitmap
    }

    /** L'image des étapes de série : le chiffre sur un rond soleil. */
    private fun flame(days: String, size: Int = 160): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6B93B.toInt() }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = 0xFF2A1F1A.toInt()
        paint.textSize = size * (if (days.length > 2) 0.34f else 0.44f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(days, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return bitmap
    }

    /** Le widget de la discussion montre le message reçu même si l'app n'est pas ouverte. */
    private fun updateWidgets(context: Context, data: Map<String, String>) {
        val conv = data["conversation_id"] ?: return
        if (data["type"] !in setOf("message", "correction", "call", "call_accepted")) return
        val snapshot = WidgetBridge.read(context) ?: return
        val partners = snapshot.partners.map {
            if (it.conversationId == conv) it.copy(unread = it.unread + 1, lastMessage = data["preview"] ?: it.lastMessage, lastMessageMine = false) else it
        }
        val streak = snapshot.streak?.let { if (it.conversationId == conv) it.copy(waitingForMe = true) else it }
        WidgetBridge.write(context, snapshot.copy(partners = partners.sortedByDescending { it.conversationId == conv }, streak = streak))
    }
}
