package app.kiko.android.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.kiko.android.MainActivity
import app.kiko.android.R
import app.kiko.notifications.WidgetBridge
import app.kiko.notifications.WidgetPartner
import app.kiko.notifications.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Les widgets Android des maquettes 30 et 31, avec les couleurs du design system (encarts orange
 * clair, soleil et menthe, arrondi de 28) plutôt que celles du fond d'écran.
 * Ils lisent le JSON écrit par l'app (WidgetBridge) ; l'heure chez l'autre et le compte à rebours
 * sont des TextClock et Chronometer du système, justes à la minute sans réveiller l'app.
 */
object KikoWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refreshAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { StreakWidget().updateAll(app) }
            runCatching { ConversationWidget().updateAll(app) }
            runCatching { PartnersWidget().updateAll(app) }
            runCatching { NextCallWidget().updateAll(app) }
        }
    }
}

private object W {
    val ink = Color(0xFF2A1F1A)
    val mid = Color(0xFF6E6259)
    val surface = Color(0xFFFFFFFF)
    val hair = Color(0xFFE9DFCB)
    val primary = Color(0xFFDD3F1A)
    val primaryLight = Color(0xFFFFE4D6)
    val sunLight = Color(0xFFFFEFC2)
    val sunInk = Color(0xFF7A5A00)
    val okLight = Color(0xFFD9F1EF)
    val okInk = Color(0xFF0A5E5D)
    val paper = Color(0xFFFFF9EC)
    val tints = listOf(Color(0xFFF59B4B), Color(0xFF0E9594), Color(0xFFDD3F1A), Color(0xFFC98A12), Color(0xFF8C5A9E), Color(0xFF3F7CAC))
    val radius = 28.dp
}

private fun text(color: Color, size: Float, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private fun openApp(context: Context, conversationId: String? = null): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("kiko://chat/${conversationId ?: "home"}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (conversationId != null) putExtra(MainActivity.ExtraConversation, conversationId) },
    )

private fun cityOf(tz: String): String = if (tz == "UTC") "UTC" else tz.substringAfterLast('/').replace('_', ' ')

private fun online(p: WidgetPartner): Boolean =
    p.lastSeen != null && System.currentTimeMillis() - p.lastSeen!! < 5 * 60_000L

@Composable
private fun Avatar(name: String, size: Dp) {
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).background(W.tints[abs(name.hashCode()) % W.tints.size]),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase(), style = text(Color.White, size.value * 0.42f, bold = true))
    }
}

/** L'heure chez l'autre, tenue à jour par le système. */
@Composable
private fun Clock(context: Context, timeZone: String) {
    val views = RemoteViews(context.packageName, R.layout.widget_clock)
    views.setString(R.id.clock, "setTimeZone", timeZone)
    AndroidRemoteViews(views)
}

/** Compte à rebours jusqu'à [at] (millisecondes depuis 1970). */
@Composable
private fun Countdown(context: Context, at: Long) {
    val views = RemoteViews(context.packageName, R.layout.widget_countdown)
    views.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + (at - System.currentTimeMillis()), null, true)
    views.setChronometerCountDown(R.id.countdown, true)
    AndroidRemoteViews(views)
}

@Composable
private fun SignedOut(context: Context, background: Color) {
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(W.radius).background(background).padding(16.dp).clickable(openApp(context)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Ouvre Kiko pour voir tes partenaires ici.", style = text(W.mid, 13f))
    }
}

// ---------------------------------------------------------------------------- série (2 × 2)

class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetBridge.read(context)
        provideContent { StreakContent(context, snapshot) }
    }
}

@Composable
private fun StreakContent(context: Context, snapshot: WidgetSnapshot?) {
    val streak = snapshot?.streak
    if (snapshot == null) return SignedOut(context, W.sunLight)
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(W.radius).background(W.sunLight).padding(14.dp)
            .clickable(openApp(context, streak?.conversationId)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (streak == null) {
            Text("🔥", style = text(W.sunInk, 26f))
            Text("Écris à un partenaire pour lancer une série", style = text(W.sunInk, 12f, bold = true))
        } else {
            Text("🔥", style = text(W.sunInk, 24f))
            Text("${streak.days}", style = text(W.sunInk, 44f, bold = true))
            Text("série avec ${streak.partnerName}", style = text(W.ink, 12f, bold = true))
            if (streak.waitingForMe) Text("${streak.partnerName} attend ton message", style = text(W.mid, 11f))
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

// ---------------------------------------------------------------------------- discussion (4 × 2)

class ConversationWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetBridge.read(context)
        provideContent { ConversationContent(context, snapshot) }
    }
}

@Composable
private fun ConversationContent(context: Context, snapshot: WidgetSnapshot?) {
    val p = snapshot?.partners?.firstOrNull()
    if (snapshot == null) return SignedOut(context, W.primaryLight)
    if (p == null) {
        Box(
            GlanceModifier.fillMaxSize().cornerRadius(W.radius).background(W.primaryLight).padding(16.dp).clickable(openApp(context)),
            contentAlignment = Alignment.Center,
        ) { Text("Salue quelqu'un pour commencer une discussion.", style = text(W.ink, 14f, bold = true)) }
        return
    }
    Column(GlanceModifier.fillMaxSize().cornerRadius(W.radius).background(W.primaryLight).padding(14.dp).clickable(openApp(context, p.conversationId))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 40.dp)
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(p.name, style = text(W.ink, 16f, bold = true))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (online(p)) "en ligne · " else "", style = text(W.mid, 12f))
                    Clock(context, p.timeZone)
                    Text(" à ${cityOf(p.timeZone)}", style = text(W.mid, 12f))
                }
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        Box(GlanceModifier.fillMaxWidth().cornerRadius(18.dp).background(W.surface).padding(horizontal = 12.dp, vertical = 9.dp)) {
            val last = p.lastMessage
            Text(
                when {
                    last == null -> "Dis bonjour à ${p.name} !"
                    p.lastMessageMine -> "Toi : $last"
                    else -> last
                },
                style = text(W.ink, 14f),
                maxLines = 2,
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(
            GlanceModifier.fillMaxWidth().cornerRadius(22.dp).background(W.surface).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Répondre…", style = text(W.mid, 14f), modifier = GlanceModifier.defaultWeight())
            Box(GlanceModifier.size(30.dp).cornerRadius(15.dp).background(W.primary), contentAlignment = Alignment.Center) {
                Text("➤", style = text(Color.White, 13f, bold = true))
            }
        }
    }
}

class ConversationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ConversationWidget()
}

// ---------------------------------------------------------------------------- raccourcis (4 × 1)

class PartnersWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetBridge.read(context)
        provideContent { PartnersContent(context, snapshot) }
    }
}

@Composable
private fun PartnersContent(context: Context, snapshot: WidgetSnapshot?) {
    if (snapshot == null) return SignedOut(context, W.paper)
    val list = snapshot.partners.take(4)
    Row(
        GlanceModifier.fillMaxSize().cornerRadius(W.radius).background(W.paper).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (list.isEmpty()) {
            Text("Tes partenaires apparaîtront ici.", style = text(W.mid, 13f), modifier = GlanceModifier.clickable(openApp(context)))
        }
        list.forEach { p ->
            Column(
                GlanceModifier.defaultWeight().clickable(openApp(context, p.conversationId)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(p.name, 44.dp)
                Text(p.name, style = text(W.ink, 12f, bold = p.unread > 0), maxLines = 1)
            }
        }
    }
}

class PartnersWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PartnersWidget()
}

// ---------------------------------------------------------------------------- prochain appel (4 × 1)

class NextCallWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetBridge.read(context)
        provideContent { NextCallContent(context, snapshot) }
    }
}

@Composable
private fun NextCallContent(context: Context, snapshot: WidgetSnapshot?) {
    if (snapshot == null) return SignedOut(context, W.okLight)
    val call = snapshot.nextCall
    Row(
        GlanceModifier.fillMaxSize().cornerRadius(W.radius).background(W.okLight).padding(14.dp)
            .clickable(openApp(context, call?.conversationId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            if (call == null) {
                Text("Aucun appel prévu", style = text(W.okInk, 15f, bold = true))
                Text("Propose un appel dans une discussion.", style = text(W.mid, 12f))
            } else {
                Text("Appel avec ${call.partnerName}", style = text(W.okInk, 15f, bold = true))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (call.startsAt > System.currentTimeMillis()) {
                        Text("dans ", style = text(W.mid, 12f))
                        Countdown(context, call.startsAt)
                    } else {
                        Text("maintenant", style = text(W.mid, 12f, bold = true))
                    }
                    Text(" · ", style = text(W.mid, 12f))
                    Clock(context, call.partnerTimeZone)
                    Text(" à ${cityOf(call.partnerTimeZone)}", style = text(W.mid, 12f))
                }
            }
        }
        if (call != null) {
            Box(
                GlanceModifier.cornerRadius(16.dp).background(W.primary).padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text("REJOINDRE", style = text(Color.White, 12f, bold = true)) }
        }
    }
}

class NextCallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextCallWidget()
}
