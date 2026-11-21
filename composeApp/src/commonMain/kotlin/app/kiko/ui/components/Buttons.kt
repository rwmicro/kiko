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
import androidx.compose.ui.unit.sp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoDepth

/** Variantes de bouton des maquettes : `.btn`, `.btn.w`, `.btn.s`, `.btn.y`, et le bouton Apple (encre). */
enum class KikoButtonStyle { Primary, White, Soft, Sun, Ink }

enum class KikoButtonSize { Large, Small }

private data class Look(val face: Color, val edge: Color, val text: Color, val border: Color?)

/**
 * Bouton en relief, texte en capitales. Large : 58 dp (action principale d'un écran).
 * Small : 43 dp (actions dans une carte ou une ligne).
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
    /** Color.Unspecified garde les couleurs de l'icône (logo Google). Par défaut : couleur du texte. */
    iconTint: Color? = null,
    /** Texte tel quel, sans capitales (boutons « Continuer avec Apple », dont la forme est imposée). */
    keepCase: Boolean = false,
) {
    val c = Kiko.colors
    val large = size == KikoButtonSize.Large
    val shape = RoundedCornerShape(if (large) 21.dp else 14.dp)
    val depth = if (large) KikoDepth.large else KikoDepth.small
    val look = when {
        !enabled -> Look(c.hair, c.hair, c.soft, null)
        style == KikoButtonStyle.Primary -> Look(c.primary, c.primaryEdge, c.onPrimary, null)
        style == KikoButtonStyle.White -> Look(c.surface, c.hair, c.primary, c.hair)
        style == KikoButtonStyle.Soft -> Look(c.primaryLight, c.primaryLightEdge, c.primaryDark, null)
        style == KikoButtonStyle.Ink -> Look(c.ink, Color.Black, c.paper, null)
        else -> Look(c.sun, c.sunEdge, Color(0xFF2A1F1A), null)
    }
    Row(
        modifier = modifier
            .pressableRelief(shape, look.face, look.edge, depth, enabled, onClick, look.border)
            .height(if (large) 58.dp else 43.dp)
            .padding(horizontal = if (large) 20.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = iconTint ?: look.text, modifier = Modifier.size(if (large) 20.dp else 17.dp))
        Text(
            if (keepCase) text else text.uppercase(),
            style = if (large) Kiko.type.button else Kiko.type.buttonSmall,
            color = look.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Lien d'action sans fond, en capitales (`.btn.link`), par exemple « J'ai déjà un compte ». */
@Composable
fun KikoLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Kiko.colors.primary) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = Kiko.type.buttonSmall.copy(fontSize = 15.sp), color = color)
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
        KikoButtonStyle.Ink -> Triple(c.ink, Color.Black, c.paper)
    }
    Box(
        modifier = modifier
            .pressableRelief(
                RoundedCornerShape(18.dp), face, edge, KikoDepth.large, true, onClick,
                borderColor = if (style == KikoButtonStyle.White) c.hair else null,
            )
            .size(width = 47.dp, height = 43.dp),
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
            .pressableRelief(RoundedCornerShape(22.dp), c.sun, c.sunEdge, KikoDepth.large, true, onClick)
            .size(width = 64.dp, height = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color(0xFF2A1F1A), modifier = Modifier.size(24.dp))
    }
}

/** Atténue un contenu désactivé de la même façon partout. */
internal fun Modifier.disabledAlpha(enabled: Boolean): Modifier = if (enabled) this else alpha(0.45f)
