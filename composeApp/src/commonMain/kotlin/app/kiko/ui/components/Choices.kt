package app.kiko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoDepth

// Puces d'information (non cliquables)

enum class KikoPillStyle { Apricot, Primary }

/** Pastille arrondie (`.pill`), par exemple le compteur de série « 12 jours ». */
@Composable
fun KikoPill(text: String, modifier: Modifier = Modifier, style: KikoPillStyle = KikoPillStyle.Apricot, icon: ImageVector? = null) {
    val c = Kiko.colors
    val (bg, fg) = when (style) {
        KikoPillStyle.Apricot -> c.apricotLight to c.apricotDark
        KikoPillStyle.Primary -> c.primaryLight to c.primaryDark
    }
    Row(
        modifier.height(36.dp).background(bg, CircleShape).padding(horizontal = 12.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
        Text(text, style = Kiko.type.captionStrong, color = fg)
    }
}

enum class KikoTagStyle { Neutral, Ok, Sun }

/** Petite étiquette (`.tg`) sous un nom : intérêt commun, langue, « Répond vite ». */
@Composable
fun KikoTag(text: String, modifier: Modifier = Modifier, style: KikoTagStyle = KikoTagStyle.Neutral) {
    val c = Kiko.colors
    val (bg, fg) = when (style) {
        KikoTagStyle.Neutral -> c.tint to c.mid
        KikoTagStyle.Ok -> c.okLight to c.ok
        KikoTagStyle.Sun -> c.sunLight to c.sunInk
    }
    Box(modifier.height(26.dp).background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Kiko.type.micro, color = fg, maxLines = 1)
    }
}

/** Niveau CECR sur fond primaire (`.cef`), par exemple « B1 ». */
@Composable
fun KikoLevelBadge(level: String, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Box(modifier.background(c.primary, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(level, style = Kiko.type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = c.onPrimary)
    }
}

/** Mention « Natif » à côté d'une langue (`.nat`). */
@Composable
fun KikoNativeBadge(modifier: Modifier = Modifier, text: String = "Natif") {
    val c = Kiko.colors
    Box(modifier.background(c.okLight, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text.uppercase(), style = Kiko.type.label.copy(fontSize = Kiko.type.micro.fontSize), color = c.ok)
    }
}

// Choix (cliquables)

/** Puce sélectionnable en relief (`.ints`), pour les centres d'intérêt. */
@Composable
fun KikoChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: String? = null) {
    val c = Kiko.colors
    Row(
        modifier
            .pressableRelief(
                RoundedCornerShape(21.dp),
                face = if (selected) c.primaryLight else c.surface,
                edge = if (selected) c.primary else c.hair,
                depth = KikoDepth.small,
                enabled = true,
                onClick = onClick,
                borderColor = if (selected) c.primary else c.hair,
                role = Role.Checkbox,
            )
            .height(40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Text(leading, style = Kiko.type.captionStrong)
        Text(text, style = Kiko.type.captionStrong, color = if (selected) c.primaryDark else c.ink)
    }
}

/**
 * Rangée de filtres défilante (`.wa .wc`) : filtres sur fond teinté, le filtre actif en
 * primaire clair. Les filtres de [dropdowns] ouvrent un choix : fond blanc bordé et flèche.
 */
@Composable
fun KikoFilterChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    dropdowns: Set<String> = emptySet(),
) {
    val c = Kiko.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            val dropdown = option in dropdowns
            val shape = RoundedCornerShape(17.dp)
            Row(
                Modifier
                    .height(34.dp)
                    .background(if (on) c.primaryLight else if (dropdown) c.surface else c.tint, shape)
                    .then(if (dropdown && !on) Modifier.border(KikoDepth.hairline, c.hair, shape) else Modifier)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val color = if (on) c.primaryDark else if (dropdown) c.ink else c.mid
                Text(option, style = Kiko.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = color)
                if (dropdown) Text("▾", style = Kiko.type.micro, color = color.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Option en relief (`.opt`) : une langue, une préférence, un choix de liste.
 * Avec [detail], l'option prend deux lignes et s'aligne à gauche.
 */
@Composable
fun KikoOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: String? = null,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val c = Kiko.colors
    Row(
        modifier
            .pressableRelief(
                RoundedCornerShape(19.5.dp),
                face = if (selected) c.primaryLight else c.surface,
                edge = if (selected) c.primary else c.hair,
                depth = KikoDepth.large,
                enabled = enabled,
                onClick = onClick,
                borderColor = if (selected) c.primary else c.hair,
                role = Role.RadioButton,
            )
            .disabledAlpha(enabled)
            .heightIn(min = if (large) 46.dp else 50.dp)
            .padding(horizontal = if (large) 4.dp else 16.dp, vertical = if (detail != null) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, if (detail == null) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        if (leading != null) Text(leading, style = Kiko.type.bodyStrong, color = if (selected) c.primaryDark else c.mid)
        if (detail == null) {
            Text(
                text,
                style = if (large) Kiko.type.bodyStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 16.5.sp) else Kiko.type.captionStrong,
                color = if (selected) c.primaryDark else c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column(Modifier.weight(1f)) {
                Text(text, style = Kiko.type.bodyStrong, color = if (selected) c.primaryDark else c.ink)
                Text(detail, style = Kiko.type.caption, color = c.mid)
            }
            if (selected) Icon(KikoIcons.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Option à pastille radio (`.rad .opt`), alignée à gauche. */
@Composable
fun KikoRadioOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    val c = Kiko.colors
    Row(
        modifier
            .pressableRelief(
                RoundedCornerShape(19.5.dp),
                face = if (selected) c.primaryLight else c.surface,
                edge = if (selected) c.primary else c.hair,
                depth = KikoDepth.large,
                enabled = true,
                onClick = onClick,
                borderColor = if (selected) c.primary else c.hair,
                role = Role.RadioButton,
            )
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioDot(selected)
        Column(Modifier.weight(1f)) {
            Text(text, style = Kiko.type.captionStrong, color = if (selected) c.primaryDark else c.ink)
            if (detail != null) Text(detail, style = Kiko.type.micro, color = c.mid)
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val c = Kiko.colors
    Box(
        Modifier
            .size(size)
            .border(if (selected) size * 0.31f else 2.dp, if (selected) c.primary else c.soft, CircleShape),
    )
}

/** Sélecteur de niveau (`.seg`) : une rangée de cases égales, par exemple A1 à C1. */
@Composable
fun KikoLevelPicker(levels: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        levels.forEach { level ->
            KikoOption(level, selected = level == selected, onClick = { onSelect(level) }, modifier = Modifier.weight(1f), large = true)
        }
    }
}

/** Contrôle segmenté sur fond teinté (`.seg3`), par exemple Texte / Photo / Vocal. */
@Composable
fun KikoSegmentedControl(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Row(
        modifier.fillMaxWidth().background(c.tint, RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .then(if (on) Modifier.background(c.surface, RoundedCornerShape(12.dp)).border(1.dp, c.hair, RoundedCornerShape(12.dp)) else Modifier)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(option, style = Kiko.type.captionStrong, color = if (on) c.ink else c.mid)
            }
        }
    }
}

/** Créneau proposé pour un appel (`.slots`) : jour et heure, précision, pastille radio. */
@Composable
fun KikoSlotOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(if (selected) c.primaryLight else c.surface, RoundedCornerShape(17.dp))
            .border(KikoDepth.hairline, if (selected) c.primary else c.hair, RoundedCornerShape(17.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = Kiko.type.bodyStrong, color = c.ink)
        Text(detail, style = Kiko.type.caption, color = c.mid, modifier = Modifier.weight(1f))
        RadioDot(selected, 22.dp)
    }
}
