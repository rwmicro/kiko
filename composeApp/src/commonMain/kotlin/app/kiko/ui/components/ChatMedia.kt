package app.kiko.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoDepth

private fun bubbleShape(outgoing: Boolean) = if (outgoing) {
    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 5.dp, bottomStart = 18.dp)
} else {
    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 5.dp)
}

/**
 * Photo dans le fil (maquette 16) : l'image dans un cadre blanc, l'heure dessous.
 * Sans [image], un emplacement indique le chargement ou l'échec ([status]).
 */
@Composable
fun KikoPhotoBubble(
    image: ImageBitmap?,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    time: String? = null,
    status: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Kiko.colors
    val shape = bubbleShape(outgoing)
    Box(modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .width(230.dp)
                .background(c.surface, shape)
                .border(KikoDepth.border, c.hair, shape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            val inner = RoundedCornerShape(14.dp)
            if (image != null) {
                val ratio = (image.width.toFloat() / image.height.coerceAtLeast(1)).coerceIn(0.6f, 1.8f)
                Image(
                    image,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(inner),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(170.dp).background(c.tint, inner),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(KikoIcons.Photo, contentDescription = null, tint = c.soft, modifier = Modifier.size(30.dp))
                        if (status != null) Text(status, style = Kiko.type.micro, color = c.mid)
                    }
                }
            }
            if (time != null) {
                Text(time, style = Kiko.type.micro, color = c.soft, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

/** Message vocal : lecture, barres de son, durée et heure. */
@Composable
fun KikoVoiceBubble(
    duration: String,
    outgoing: Boolean,
    playing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    time: String? = null,
    read: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Kiko.colors
    val shape = bubbleShape(outgoing)
    val ink = if (outgoing) c.onPrimary else c.ink
    Box(modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        val face = if (outgoing) {
            Modifier.relief(shape, c.primary, c.primaryEdge, KikoDepth.small)
        } else {
            Modifier.background(c.surface, shape).border(KikoDepth.border, c.hair, shape)
        }
        Row(
            Modifier.width(230.dp).then(face).padding(start = 8.dp, end = 11.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(if (outgoing) Color.White.copy(alpha = 0.22f) else c.primaryLight, CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) KikoIcons.Pause else KikoIcons.Play,
                    contentDescription = if (playing) "Pause" else "Écouter",
                    tint = if (outgoing) c.onPrimary else c.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Des barres fixes : une forme d'onde reconnaissable, sans analyser le son.
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(6, 12, 18, 10, 14, 22, 16, 8, 12, 20, 14, 9, 16, 11, 7, 13, 18, 10).forEach { h ->
                    Box(Modifier.width(3.dp).height(h.dp).background(ink.copy(alpha = if (playing) 0.95f else 0.55f), CircleShape))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(duration, style = Kiko.type.micro.copy(fontWeight = FontWeight.Bold), color = ink)
                if (time != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(time, style = Kiko.type.micro, color = if (outgoing) c.onPrimary.copy(alpha = 0.78f) else c.soft)
                        if (outgoing && read) DoubleCheck(c.onPrimary.copy(alpha = 0.9f))
                    }
                }
            }
        }
    }
}

/**
 * Proposition d'appel dans le fil (maquette 19, côté chat) : l'heure chez moi et chez l'autre,
 * puis l'action ou l'état (« Accepter », « Appel prévu »).
 */
@Composable
fun KikoCallCard(
    title: String,
    detail: String,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    status: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    confirmed: Boolean = false,
) {
    val c = Kiko.colors
    val shape = RoundedCornerShape(17.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(0.8f)
                .relief(shape, if (confirmed) c.okLight else c.sunLight, c.hair, KikoDepth.large, borderColor = if (confirmed) c.ok else c.sunEdge)
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(KikoIcons.Phone, contentDescription = null, tint = if (confirmed) c.ok else c.sunInk, modifier = Modifier.size(18.dp))
                Text(title, style = Kiko.type.captionStrong, color = c.ink, modifier = Modifier.weight(1f))
            }
            Text(detail, style = Kiko.type.micro, color = c.mid)
            if (action != null) {
                KikoButton(action, onAction, Modifier.padding(top = 4.dp).fillMaxWidth(), size = KikoButtonSize.Small)
            } else if (status != null) {
                Text(status, style = Kiko.type.micro.copy(fontWeight = FontWeight.Bold), color = if (confirmed) c.ok else c.mid)
            }
        }
    }
}

/** Barre d'enregistrement d'un vocal : point rouge, durée, jeter ou envoyer. */
@Composable
fun KikoRecordingBar(elapsed: String, onCancel: () -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Row(
        modifier.fillMaxWidth().background(c.paper).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(40.dp).clickable(role = Role.Button, onClick = onCancel), contentAlignment = Alignment.Center) {
            Icon(KikoIcons.Trash, contentDescription = "Jeter le vocal", tint = c.error, modifier = Modifier.size(22.dp))
        }
        Row(
            Modifier.weight(1f).heightIn(min = 46.dp).background(c.surface, CircleShape).border(2.dp, c.hair, CircleShape).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(10.dp).background(c.error, CircleShape))
            Text("Enregistrement · $elapsed", style = Kiko.type.bodyStrong, color = c.ink)
        }
        Box(
            Modifier.size(46.dp).background(c.primary, CircleShape).clickable(role = Role.Button, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KikoIcons.Send, contentDescription = "Envoyer le vocal", tint = c.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Mot touchable de « Corriger au toucher » (maquette 18). Un mot changé montre l'ancien barré
 * et le nouveau en vert ; le mot ouvert est bordé de la couleur primaire.
 */
@Composable
fun KikoWordChip(word: String, onClick: () -> Unit, modifier: Modifier = Modifier, replacement: String? = null, selected: Boolean = false) {
    val c = Kiko.colors
    val shape = RoundedCornerShape(11.dp)
    val changed = replacement != null && replacement != word
    Row(
        modifier
            .heightIn(min = 36.dp)
            .background(if (selected) c.primaryLight else if (changed) c.okLight else c.surface, shape)
            .border(if (selected) 2.dp else KikoDepth.border, if (selected) c.primary else c.hair, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            word,
            style = Kiko.type.captionStrong.copy(textDecoration = if (changed) TextDecoration.LineThrough else null),
            color = if (changed) c.ok.copy(alpha = 0.7f) else if (selected) c.primaryDark else c.ink,
        )
        if (changed) Text(replacement.ifEmpty { "∅" }, style = Kiko.type.captionStrong, color = c.ok)
    }
}
