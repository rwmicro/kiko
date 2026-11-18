package app.kiko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoDepth

/** En-tête de discussion (`.chead`) : retour, avatar, prénom, statut, bouton d'appel. */
@Composable
fun KikoChatHeader(
    name: String,
    status: String,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    online: Boolean = false,
    onBack: () -> Unit = {},
    onCall: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(32.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(KikoIcons.Back, contentDescription = "Retour", tint = c.ink, modifier = Modifier.size(22.dp))
            }
            KikoAvatar(name, size = 40.dp)
            Column(Modifier.weight(1f)) {
                NameLine(name, verified)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (online) KikoOnlineDot()
                    Text(status, style = Kiko.type.micro, color = c.mid)
                }
            }
            if (onCall != null) {
                Box(
                    Modifier.size(36.dp).clickable(role = Role.Button, onClick = onCall),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KikoIcons.Phone, contentDescription = "Proposer un appel", tint = c.ink, modifier = Modifier.size(22.dp))
                }
            }
            if (onMore != null) {
                Box(Modifier.size(36.dp).clickable(role = Role.Button, onClick = onMore), contentAlignment = Alignment.Center) {
                    Icon(KikoIcons.More, contentDescription = "Plus", tint = c.mid, modifier = Modifier.size(22.dp))
                }
            }
        }
        KikoDivider()
    }
}

/**
 * Bulle de message. Reçue (`.mi`) : blanche bordée, coin bas gauche pincé.
 * Envoyée (`.mo`) : primaire avec tranche, coin bas droit pincé, double coche si lue.
 */
@Composable
fun KikoMessageBubble(
    text: String,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    time: String? = null,
    read: Boolean = false,
) {
    val c = Kiko.colors
    val shape = if (outgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 5.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 5.dp)
    }
    Box(modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        val face = if (outgoing) {
            Modifier.relief(shape, c.primary, c.primaryEdge, KikoDepth.small)
        } else {
            Modifier.background(c.surface, shape).border(KikoDepth.border, c.hair, shape)
        }
        Column(
            Modifier.widthIn(max = 290.dp).then(face).padding(start = 12.5.dp, end = 11.dp, top = 7.dp, bottom = 7.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text,
                style = if (outgoing) Kiko.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold) else Kiko.type.body,
                color = if (outgoing) c.onPrimary else c.ink,
            )
            if (time != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(time, style = Kiko.type.micro, color = if (outgoing) c.onPrimary.copy(alpha = 0.78f) else c.soft)
                    if (outgoing && read) DoubleCheck(c.onPrimary.copy(alpha = 0.9f))
                }
            }
        }
    }
}

@Composable
private fun DoubleCheck(color: Color) {
    Box(Modifier.size(width = 19.dp, height = 14.dp)) {
        Icon(KikoIcons.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Icon(KikoIcons.Check, contentDescription = "Lu", tint = color, modifier = Modifier.size(14.dp).offset(x = 5.dp))
    }
}

/** Séparateur de jour dans le fil (`.day`). */
@Composable
fun KikoDayLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = Kiko.type.micro,
            color = Kiko.colors.mid,
            modifier = Modifier.background(Kiko.colors.surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** « Ayu écrit… » (`.typing`) : petit avatar et trois points. */
@Composable
fun KikoTypingIndicator(name: String, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Row(
        modifier.background(c.surface.copy(alpha = 0.92f), RoundedCornerShape(14.dp)).padding(start = 4.dp, end = 11.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        KikoAvatar(name, size = 21.dp)
        Text("$name écrit", style = Kiko.type.micro, color = c.mid)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.size(5.dp).background(c.soft, CircleShape)) }
        }
    }
}

/**
 * Carte de correction (`.fix`) : qui corrige, mention « Important », phrase fautive barrée,
 * phrase corrigée avec le passage changé surligné en vert, explication et actions.
 * [highlight] est le passage de [corrected] à surligner ; s'il est absent, rien n'est surligné.
 */
@Composable
fun KikoCorrectionCard(
    author: String,
    original: String,
    corrected: String,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    explanation: String? = null,
    important: Boolean = false,
    onThanks: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
) {
    val c = Kiko.colors
    val shape = RoundedCornerShape(17.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .fillMaxWidth(0.8f)
                .relief(shape, c.surface, c.hair, KikoDepth.large, borderColor = c.hair)
                .padding(horizontal = 12.5.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                KikoAvatar(author, size = 18.dp)
                Text("$author t'a corrigé", style = Kiko.type.micro.copy(fontWeight = FontWeight.Bold), color = c.mid)
            }
            if (important) {
                Text(
                    "Important",
                    style = Kiko.type.micro.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.background(c.error, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
            Text(
                original,
                style = Kiko.type.caption.copy(fontSize = 15.sp, textDecoration = TextDecoration.LineThrough),
                color = c.error,
            )
            Text(
                buildAnnotatedString {
                    val start = highlight?.let { corrected.indexOf(it) } ?: -1
                    if (highlight == null || start < 0) {
                        append(corrected)
                    } else {
                        append(corrected.substring(0, start))
                        withStyle(SpanStyle(background = c.okLight, color = c.ok)) { append(highlight) }
                        append(corrected.substring(start + highlight.length))
                    }
                },
                style = Kiko.type.captionStrong.copy(fontSize = 15.sp),
                color = c.ink,
            )
            if (explanation != null) Text(explanation, style = Kiko.type.micro.copy(fontWeight = FontWeight.Medium), color = c.mid)
            if (onThanks != null || onSave != null) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (onThanks != null) ActionText("Merci", onThanks)
                    if (onSave != null) ActionText("Ajouter au carnet", onSave)
                }
            }
        }
    }
}

@Composable
private fun ActionText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = Kiko.type.captionStrong,
        color = Kiko.colors.primaryDark,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
    )
}

/**
 * Demande de discussion (`.req`) : qui veut écrire, son premier message,
 * et les choix Accepter / Refuser. Bloquer et signaler restent dans le menu de l'appelant.
 */
@Composable
fun KikoChatRequestCard(
    name: String,
    message: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
) {
    KikoCard(modifier, color = Kiko.colors.paper) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KikoAvatar(name, size = 40.dp)
            Column(Modifier.weight(1f)) {
                NameLine(name, verified)
                Text("veut discuter avec toi", style = Kiko.type.micro, color = Kiko.colors.mid)
            }
        }
        Text(message, style = Kiko.type.caption, color = Kiko.colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KikoButton("Refuser", onDecline, Modifier.weight(1f), style = KikoButtonStyle.White, size = KikoButtonSize.Small)
            KikoButton("Accepter", onAccept, Modifier.weight(1f), size = KikoButtonSize.Small)
        }
    }
}

/** Barre de saisie du chat (`.comp`) : champ arrondi et bouton d'envoi. */
@Composable
fun KikoChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Écris un message…",
    onVoice: (() -> Unit)? = null,
) {
    val c = Kiko.colors
    Row(
        modifier.fillMaxWidth().background(c.paper).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Kiko.type.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(c.surface, CircleShape)
                        .border(2.dp, c.hair, CircleShape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = Kiko.type.body, color = c.soft)
                    inner()
                }
            },
        )
        val sending = value.isNotBlank() || onVoice == null
        Box(
            Modifier
                .size(46.dp)
                .background(c.primary, CircleShape)
                .clickable(role = Role.Button, onClick = if (sending) onSend else onVoice ?: onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (sending) KikoIcons.Send else KikoIcons.Mic,
                contentDescription = if (sending) "Envoyer" else "Message vocal",
                tint = c.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
