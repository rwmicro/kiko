package app.kiko.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoDepth
import app.kiko.ui.theme.KikoType

/** Variantes de bouton des maquettes : `.btn`, `.btn.w`, `.btn.s` et `.btn.y`. */
enum class KikoButtonStyle { Primary, White, Soft, Sun }

enum class KikoButtonSize { Large, Small }

private data class Look(val face: Color, val edge: Color, val text: Color, val border: Color?)

/**
 * Bouton en relief, texte en capitales. Large : 54 dp (action principale d'un écran).
 * Small : 42 dp (actions dans une carte ou une ligne).
 */
@Composable
fun KikoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KikoButtonStyle = KikoButtonStyle.Primary,
    size: KikoButtonSize = KikoButtonSize.Large,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Kiko.colors
    val large = size == KikoButtonSize.Large
    val shape = RoundedCornerShape(if (large) 18.dp else 14.dp)
    val depth = if (large) KikoDepth.large else KikoDepth.small
    val look = when {
        !enabled -> Look(c.hair, c.hair, c.soft, null)
        style == KikoButtonStyle.Primary -> Look(c.primary, c.primaryEdge, c.onPrimary, null)
        style == KikoButtonStyle.White -> Look(c.surface, c.hair, c.primary, c.hair)
        style == KikoButtonStyle.Soft -> Look(c.primaryLight, c.primaryLightEdge, c.primaryDark, null)
        else -> Look(c.sun, c.sunEdge, Color(0xFF2A1F1A), null)
    }
    Row(
        modifier = modifier
            .pressableRelief(shape, look.face, look.edge, depth, enabled, onClick, look.border)
            .height(if (large) 54.dp else 42.dp)
            .padding(horizontal = if (large) 20.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = look.text, modifier = Modifier.size(if (large) 20.dp else 17.dp))
        Text(
            text.uppercase(),
            style = if (large) KikoType.button else KikoType.buttonSmall,
            color = look.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Lien d'action sans fond (`.btn.link`), par exemple « J'ai déjà un compte ». */
@Composable
fun KikoLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Kiko.colors.primary) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KikoType.captionStrong, color = color)
    }
}

/** Bouton carré à pictogramme (`.sbtn`), par exemple le filtre à côté de la recherche. */
@Composable
fun KikoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KikoButtonStyle = KikoButtonStyle.White,
) {
    val c = Kiko.colors
    val (face, edge, tint) = when (style) {
        KikoButtonStyle.Primary -> Triple(c.primary, c.primaryEdge, c.onPrimary)
        KikoButtonStyle.White -> Triple(c.surface, c.hair, c.ink)
        KikoButtonStyle.Soft -> Triple(c.primaryLight, c.primaryLightEdge, c.primaryDark)
        KikoButtonStyle.Sun -> Triple(c.sun, c.sunEdge, Color(0xFF2A1F1A))
    }
    Box(
        modifier = modifier
            .pressableRelief(
                RoundedCornerShape(16.dp), face, edge, KikoDepth.small, true, onClick,
                borderColor = if (style == KikoButtonStyle.White) c.hair else null,
            )
            .size(width = 48.dp, height = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Bouton flottant (`.fab`), par exemple « Nouvelle publication » sur le fil. */
@Composable
fun KikoFab(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Box(
        modifier = modifier
            .pressableRelief(RoundedCornerShape(20.dp), c.sun, c.sunEdge, KikoDepth.large, true, onClick)
            .size(width = 62.dp, height = 58.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color(0xFF2A1F1A), modifier = Modifier.size(24.dp))
    }
}

/** Atténue un contenu désactivé de la même façon partout. */
internal fun Modifier.disabledAlpha(enabled: Boolean): Modifier = if (enabled) this else alpha(0.45f)
