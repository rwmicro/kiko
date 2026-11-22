@file:OptIn(ExperimentalTime::class)

package app.kiko.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kiko.ui.components.KikoAvatar
import app.kiko.ui.components.KikoIcons
import app.kiko.ui.theme.Kiko
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * L'écran d'appel, par-dessus toute l'app tant qu'un appel est en cours. Sur iPhone, CallKit montre
 * aussi le sien (écran verrouillé, îlot dynamique) ; sur Android, la notification d'appel.
 */
@Composable
fun CallOverlay(controller: CallController = Calls.controller) {
    val state by controller.state.collectAsState()
    if (state.phase == CallPhase.Idle) return
    CallScreen(
        state = state,
        onAnswer = { state.callId?.let(controller::answer) },
        onHangUp = { if (state.phase == CallPhase.Ended) controller.dismiss() else controller.hangUp() },
        onMute = controller::toggleMute,
        onSpeaker = controller::toggleSpeaker,
    )
}

@Composable
fun CallScreen(
    state: CallState,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
) {
    val c = Kiko.colors
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(state.phase) {
        while (state.phase == CallPhase.Active || state.phase == CallPhase.Reconnecting) {
            now = Clock.System.now()
            delay(1000)
        }
    }
    val status = when (state.phase) {
        CallPhase.Calling -> "Ça sonne chez ${state.partnerName}…"
        CallPhase.Incoming -> "t'appelle sur Kiko"
        CallPhase.Connecting -> "Connexion…"
        CallPhase.Active -> state.startedAt?.let { elapsed((now - it).inWholeSeconds) } ?: "En ligne"
        CallPhase.Reconnecting -> "Connexion perdue, on réessaie…"
        CallPhase.Ended -> state.message ?: "Appel terminé"
        CallPhase.Idle -> ""
    }
    val sky = Brush.verticalGradient(listOf(c.primaryDark, c.primary))
    Box(
        Modifier
            .fillMaxSize()
            .background(sky)
            // L'écran d'appel arrête les touchers destinés à l'app en dessous.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            KikoAvatar(state.partnerName, size = 132.dp, ring = state.phase == CallPhase.Active)
            Spacer(Modifier.height(22.dp))
            Text(state.partnerName, style = Kiko.type.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                status,
                style = Kiko.type.body,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = "${state.partnerName} : $status" },
            )
            Spacer(Modifier.weight(1f))
            when (state.phase) {
                CallPhase.Incoming -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(KikoIcons.Close, "Refuser", c.error, onHangUp)
                    RoundButton(KikoIcons.Phone, "Décrocher", c.ok, onAnswer)
                }
                CallPhase.Ended -> RoundButton(KikoIcons.Check, "Fermer", Color.White.copy(alpha = 0.22f), onHangUp)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundButton(
                            KikoIcons.Mic,
                            if (state.muted) "Micro coupé" else "Micro",
                            if (state.muted) Color.White else Color.White.copy(alpha = 0.22f),
                            onMute,
                            tint = if (state.muted) c.primaryDark else Color.White,
                        )
                        RoundButton(
                            KikoIcons.Wave,
                            "Haut-parleur",
                            if (state.speaker) Color.White else Color.White.copy(alpha = 0.22f),
                            onSpeaker,
                            tint = if (state.speaker) c.primaryDark else Color.White,
                        )
                    }
                    RoundButton(KikoIcons.Phone, if (state.phase == CallPhase.Calling) "Annuler" else "Raccrocher", c.error, onHangUp)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, face: Color, onClick: () -> Unit, tint: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(face)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        }
        Text(label, style = Kiko.type.micro, color = Color.White)
    }
}

internal fun elapsed(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
}
