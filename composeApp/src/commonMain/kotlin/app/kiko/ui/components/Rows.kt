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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoDepth

/** Filet entre deux lignes de liste (2 px dans les maquettes). */
@Composable
fun KikoDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 2.5.dp, color = Kiko.colors.hair)
}

/** Une étiquette sous un nom de partenaire. */
data class KikoTagData(val text: String, val style: KikoTagStyle = KikoTagStyle.Neutral)

/**
 * Ligne de partenaire (`.lrow`) : avatar avec présence, prénom et âge, badge vérifié,
 * heure locale à droite (pastille colorée selon le moment de la journée), ligne de méta
 * précédée du drapeau, étiquettes facultatives, et bouton « saluer » à droite.
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
    flag: String? = null,
    localTime: String? = null,
    timeOfDay: KikoTimeOfDay = KikoTimeOfDay.Night,
    onClick: () -> Unit = {},
    onWave: (() -> Unit)? = null,
) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KikoAvatar(name, size = 64.dp, online = online)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { NameLine(name + (age?.let { ", $it" } ?: ""), verified) }
                    if (localTime != null) {
                        Box(Modifier.size(8.dp).background(timeOfDay.color, CircleShape))
                        Text(" $localTime", style = Kiko.type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.soft)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (flag != null) KikoFlag(flag)
                    Text(meta, style = Kiko.type.caption, color = c.mid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { tags.take(3).forEach { KikoTag(it.text, style = it.style) } }
                }
            }
            if (onWave != null) {
                Box(
                    Modifier
                        .pressableRelief(RoundedCornerShape(18.dp), c.sun, c.sunEdge, KikoDepth.large, true, onWave)
                        .size(width = 50.dp, height = 45.dp),
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
    streak: Int = 0,
    onClick: () -> Unit = {},
) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            KikoAvatar(name, size = 64.dp, online = online)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NameLine(name, verified)
                    if (streak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(KikoIcons.Flame, contentDescription = null, tint = c.apricot, modifier = Modifier.size(15.dp))
                            Text("$streak", style = Kiko.type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.apricotDark)
                        }
                    }
                }
                Text(
                    preview,
                    style = if (unread > 0) Kiko.type.captionStrong else Kiko.type.caption,
                    color = if (unread > 0) c.ink else c.mid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(time, style = Kiko.type.micro, color = if (unread > 0) c.primary else c.soft)
                if (unread > 0) KikoCountBadge(unread, color = c.primary)
            }
        }
        KikoDivider()
    }
}

/**
 * Compteur rond : abricot dans la barre d'onglets (`.tabbar em`),
 * primaire sur une discussion non lue (`.w2 .bd`).
 */
@Composable
fun KikoCountBadge(count: Int, modifier: Modifier = Modifier, color: Color = Kiko.colors.apricot) {
    Box(
        modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).background(color, CircleShape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = Kiko.type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = androidx.compose.ui.graphics.Color.White)
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
            Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = if (destructive) c.error else c.mid, modifier = Modifier.size(22.dp))
            Text(label, style = Kiko.type.bodyStrong, color = tint, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = Kiko.type.caption, color = c.mid)
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
                Text(name, style = Kiko.type.bodyStrong, color = c.ink)
                Text(detail, style = Kiko.type.micro, color = c.mid)
            }
            Icon(KikoIcons.Flame, contentDescription = null, tint = c.apricot, modifier = Modifier.size(19.dp))
            Text(days.toString(), style = Kiko.type.bodyStrong, color = c.ink)
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
            Text(language, style = Kiko.type.bodyStrong, color = c.ink)
            KikoLevelBadge(level)
            Box(Modifier.weight(1f))
            if (nextStep != null) Text(nextStep, style = Kiko.type.micro, color = c.mid)
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
            Text(title, style = Kiko.type.captionStrong, color = c.ink)
            Text(message, style = Kiko.type.caption, color = c.mid)
        }
        Text(time, style = Kiko.type.micro, color = c.soft)
    }
}

@Composable
internal fun NameLine(text: String, verified: Boolean, color: Color = Kiko.colors.ink) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text, style = Kiko.type.name, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Text(meta, style = Kiko.type.micro, color = c.mid)
                }
            }
            Text(text, style = Kiko.type.body, color = c.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Traduire", style = Kiko.type.captionStrong, color = c.primaryDark, modifier = Modifier.clickable(onClick = onTranslate))
                Text("Écouter", style = Kiko.type.captionStrong, color = c.primaryDark, modifier = Modifier.clickable(onClick = onListen))
                Box(Modifier.weight(1f))
                if (corrections > 0) KikoTag("$corrections correction${if (corrections > 1) "s" else ""}", style = KikoTagStyle.Ok)
            }
        }
        KikoDivider()
    }
}

/** Moment de la journée chez le partenaire : la couleur de la pastille à côté de son heure. */
enum class KikoTimeOfDay(val color: Color) {
    Dawn(Color(0xFFFFA36B)),
    Day(Color(0xFF5DB5E8)),
    Dusk(Color(0xFFF0764F)),
    Night(Color(0xFF3A3F86)),
}
