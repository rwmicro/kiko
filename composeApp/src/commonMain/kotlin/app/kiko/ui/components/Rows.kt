package app.kiko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoDepth
import app.kiko.ui.theme.KikoType

/** Filet de 2 dp entre deux lignes de liste. */
@Composable
fun KikoDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 2.dp, color = Kiko.colors.hair)
}

/** Une étiquette sous un nom de partenaire. */
data class KikoTagData(val text: String, val style: KikoTagStyle = KikoTagStyle.Neutral)

/**
 * Ligne de partenaire (`.lrow`) : avatar avec présence, prénom et âge, badge vérifié,
 * ligne de méta (ville, heure locale, niveau), étiquettes, et bouton « saluer » à droite.
 */
@Composable
fun KikoPartnerRow(
    name: String,
    meta: String,
    modifier: Modifier = Modifier,
    age: Int? = null,
    verified: Boolean = false,
    online: Boolean = false,
    tags: List<KikoTagData> = emptyList(),
    onClick: () -> Unit = {},
    onWave: (() -> Unit)? = null,
) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KikoAvatar(name, size = 60.dp, online = online)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NameLine(name + (age?.let { ", $it" } ?: ""), verified)
                Text(meta, style = KikoType.caption, color = c.mid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { tags.take(3).forEach { KikoTag(it.text, style = it.style) } }
                }
            }
            if (onWave != null) {
                Box(
                    Modifier
                        .pressableRelief(RoundedCornerShape(16.dp), c.sun, c.sunEdge, KikoDepth.large, true, onWave)
                        .size(width = 48.dp, height = 42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KikoIcons.Wave, contentDescription = "Saluer", tint = androidx.compose.ui.graphics.Color(0xFF2A1F1A), modifier = Modifier.size(20.dp))
                }
            }
        }
        KikoDivider()
    }
}

/**
 * Ligne de discussion (`.crow`) : avatar, prénom, dernier message, heure,
 * et compteur de non-lus sur fond abricot.
 */
@Composable
fun KikoConversationRow(
    name: String,
    preview: String,
    time: String,
    modifier: Modifier = Modifier,
    unread: Int = 0,
    verified: Boolean = false,
    online: Boolean = false,
    onClick: () -> Unit = {},
) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KikoAvatar(name, size = 60.dp, online = online)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NameLine(name, verified)
                Text(
                    preview,
                    style = if (unread > 0) KikoType.captionStrong else KikoType.caption,
                    color = if (unread > 0) c.ink else c.mid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(time, style = KikoType.micro, color = if (unread > 0) c.primary else c.soft)
                if (unread > 0) KikoCountBadge(unread)
            }
        }
        KikoDivider()
    }
}

/** Compteur rond sur fond abricot (`.tabbar em`) : non-lus, notifications. */
@Composable
fun KikoCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).background(Kiko.colors.apricot, CircleShape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = KikoType.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = androidx.compose.ui.graphics.Color.White)
    }
}

/** Ligne de menu (`.mrow`) : pictogramme, intitulé, valeur, chevron. */
@Composable
fun KikoMenuRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit = {},
) {
    val c = Kiko.colors
    val tint = if (destructive) c.error else c.ink
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = if (destructive) c.error else c.mid, modifier = Modifier.size(22.dp))
            Text(label, style = KikoType.bodyStrong, color = tint, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = KikoType.caption, color = c.mid)
            if (!destructive) Icon(KikoIcons.Chevron, contentDescription = null, tint = c.soft, modifier = Modifier.size(18.dp))
        }
        KikoDivider()
    }
}

/** Ligne de série partagée (`.strk`) : partenaire, précision, flamme et nombre de jours. */
@Composable
fun KikoStreakRow(name: String, detail: String, days: Int, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KikoAvatar(name, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = KikoType.bodyStrong, color = c.ink)
                Text(detail, style = KikoType.micro, color = c.mid)
            }
            Icon(KikoIcons.Flame, contentDescription = null, tint = c.apricot, modifier = Modifier.size(19.dp))
            Text(days.toString(), style = KikoType.bodyStrong, color = c.ink)
        }
        KikoDivider()
    }
}

/** Langue et progression (`.mlb`) : nom, niveau, palier suivant, barre. */
@Composable
fun KikoLanguageProgress(
    language: String,
    level: String,
    progress: Float,
    modifier: Modifier = Modifier,
    nextStep: String? = null,
) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(language, style = KikoType.bodyStrong, color = c.ink)
            KikoLevelBadge(level)
            Box(Modifier.weight(1f))
            if (nextStep != null) Text(nextStep, style = KikoType.micro, color = c.mid)
        }
        KikoProgressBar(progress)
    }
}

/** Barre de progression pleine (`.mlb .bar`). */
@Composable
fun KikoProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Box(modifier.fillMaxWidth().height(10.dp).background(c.hair, CircleShape)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(10.dp).background(c.primary, CircleShape))
    }
}

/** Notification (`.nt`) : pictogramme, titre, texte, heure. */
@Composable
fun KikoNotificationRow(
    title: String,
    message: String,
    time: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = KikoIcons.Bell,
    onClick: () -> Unit = {},
) {
    val c = Kiko.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(38.dp).background(c.primaryLight, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.primaryDark, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = KikoType.captionStrong, color = c.ink)
            Text(message, style = KikoType.caption, color = c.mid)
        }
        Text(time, style = KikoType.micro, color = c.soft)
    }
}

@Composable
internal fun NameLine(text: String, verified: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text, style = KikoType.name, color = Kiko.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (verified) KikoVerifiedBadge()
    }
}

/**
 * Publication du fil public (`.post`) : auteur, méta, texte, puis les actions
 * « Traduire » et « Écouter » et le nombre de corrections reçues.
 */
@Composable
fun KikoPostCard(
    author: String,
    meta: String,
    text: String,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    corrections: Int = 0,
    onTranslate: () -> Unit = {},
    onListen: () -> Unit = {},
) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KikoAvatar(author, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    NameLine(author, verified)
                    Text(meta, style = KikoType.micro, color = c.mid)
                }
            }
            Text(text, style = KikoType.body, color = c.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Traduire", style = KikoType.captionStrong, color = c.primaryDark, modifier = Modifier.clickable(onClick = onTranslate))
                Text("Écouter", style = KikoType.captionStrong, color = c.primaryDark, modifier = Modifier.clickable(onClick = onListen))
                Box(Modifier.weight(1f))
                if (corrections > 0) KikoTag("$corrections correction${if (corrections > 1) "s" else ""}", style = KikoTagStyle.Ok)
            }
        }
        KikoDivider()
    }
}
