package app.kiko.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kiko.ui.components.KikoAvatar
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonSize
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoCard
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoMascot
import app.kiko.ui.components.KikoSectionLabel
import app.kiko.ui.components.KikoToggleRow
import app.kiko.ui.theme.Kiko
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * L'état des notifications pour l'écran principal : l'autorisation du système, les préférences
 * du serveur, et l'écran de demande (maquette 17) à montrer ou non.
 */
@OptIn(ExperimentalTime::class)
class NotificationController(private val service: NotificationService, private val scope: CoroutineScope) {
    var permission by mutableStateOf(PushPermission.Unsupported)
        private set
    var prefs by mutableStateOf(NotificationPrefs())
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var later by mutableStateOf(laterUntil())

    suspend fun load() {
        permission = PushPlatform.permission()
        runCatching { service.prefs() }.onSuccess { prefs = it }
    }

    suspend fun refreshPermission() {
        permission = PushPlatform.permission()
    }

    /** On demande après le premier vrai échange, jamais au lancement, et pas plus d'une fois tous les 3 jours. */
    fun shouldPrime(hasPartner: Boolean): Boolean =
        hasPartner && permission == PushPermission.NotAsked && Clock.System.now().toEpochMilliseconds() >= later

    fun remindLater() {
        later = Clock.System.now().toEpochMilliseconds() + LaterDelayMs
        LocalStore.set(LaterKey, later.toString())
    }

    fun onSystemAnswer(granted: Boolean) {
        permission = if (granted) PushPermission.Granted else PushPermission.Denied
        if (!granted) remindLater()
    }

    fun update(next: NotificationPrefs) {
        val before = prefs
        prefs = next
        saving = true
        error = null
        scope.launch {
            runCatching { service.setPrefs(next) }.onFailure {
                prefs = before
                error = it.message
            }
            saving = false
        }
    }

    private companion object {
        const val LaterKey = "push_primer_later_until"
        const val LaterDelayMs = 3L * 24 * 3600 * 1000
        fun laterUntil(): Long = LocalStore.get(LaterKey)?.toLongOrNull() ?: 0L
    }
}

/**
 * Maquette 17 : avant la fenêtre du système, un exemple réel et deux promesses
 * (pas la nuit, un rappel par jour au plus).
 */
@Composable
fun NotificationPrimer(partnerName: String, onEnable: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Box(modifier.fillMaxSize().background(c.paper).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 440.dp).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.6f))
            KikoMascot(size = 132.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Ne rate pas la réponse de $partnerName",
                style = Kiko.type.title,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Kiko te prévient quand quelqu'un te répond ou te corrige. Jamais la nuit chez toi, jamais plus d'un rappel par jour.",
                style = Kiko.type.body,
                color = c.mid,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewNotification(partnerName, partnerName, "Halo Martin! Besok kita telepon?", "maintenant")
                PreviewNotification(partnerName, "$partnerName a corrigé ton message", "« Saya sangat suka film itu » est plus naturel.", "2 min")
            }
            Spacer(Modifier.weight(1f))
            KikoButton("Activer les notifications", onEnable, Modifier.fillMaxWidth())
            KikoLinkButton("Plus tard", onLater)
        }
    }
}

/** Une fausse notification de l'écran verrouillé, pour montrer à quoi s'attendre. */
@Composable
private fun PreviewNotification(name: String, title: String, text: String, time: String) {
    val c = Kiko.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().background(c.surface, shape).border(2.dp, c.hair, shape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box {
            KikoAvatar(name, size = 40.dp)
            Box(
                Modifier.align(Alignment.BottomEnd).size(16.dp).background(c.surface, RoundedCornerShape(5.dp)).padding(2.dp)
                    .background(c.primary, RoundedCornerShape(4.dp)),
            )
        }
        Column(Modifier.weight(1f)) {
            Row {
                Text(title, style = Kiko.type.captionStrong, color = c.ink, modifier = Modifier.weight(1f))
                Text(time, style = Kiko.type.micro, color = c.soft)
            }
            Text(text, style = Kiko.type.caption, color = c.ink)
        }
    }
}

/** Les réglages de l'onglet Moi : l'autorisation du système, puis ce qu'on veut recevoir. */
@Composable
fun NotificationSettings(controller: NotificationController, onEnable: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KikoSectionLabel("Notifications")
        KikoCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (controller.permission) {
                    PushPermission.NotAsked -> {
                        Text("Les notifications sont éteintes sur ce téléphone.", style = Kiko.type.caption, color = c.mid)
                        KikoButton("Activer", onEnable, Modifier.padding(vertical = 6.dp).fillMaxWidth(), size = KikoButtonSize.Small)
                    }
                    PushPermission.Denied -> {
                        Text("Tu as refusé les notifications. Tu peux les rallumer dans les réglages du téléphone.", style = Kiko.type.caption, color = c.mid)
                        KikoButton(
                            "Ouvrir les réglages",
                            PushPlatform::openSystemSettings,
                            Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                            size = KikoButtonSize.Small,
                            style = KikoButtonStyle.White,
                        )
                    }
                    PushPermission.Unsupported ->
                        Text("Cet aperçu web ne reçoit pas de notifications. Elles arrivent sur Android et iPhone.", style = Kiko.type.caption, color = c.mid)
                    PushPermission.Granted -> Unit
                }
                val prefs = controller.prefs
                KikoToggleRow(
                    "Messages et corrections",
                    prefs.messages,
                    { controller.update(prefs.copy(messages = it)) },
                    detail = "Quand quelqu'un t'écrit, te corrige ou te propose un appel. Sans son entre 22 h et 8 h.",
                )
                KikoToggleRow(
                    "Rappels et séries",
                    prefs.reminders,
                    { controller.update(prefs.copy(reminders = it)) },
                    detail = "Une série en danger, un anniversaire d'amitié. Un par jour au plus, jamais la nuit.",
                )
                controller.error?.let { Text(it, style = Kiko.type.micro, color = c.error) }
            }
        }
    }
}
