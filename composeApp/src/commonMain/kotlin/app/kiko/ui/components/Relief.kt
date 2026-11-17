package app.kiko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le relief des maquettes : une tranche pleine sous l'élément (`box-shadow: 0 4px 0`),
 * qui s'enfonce quand on appuie. La place de la tranche est réservée sous l'élément,
 * donc la hauteur occupée vaut la hauteur de la face plus [depth].
 */
internal fun Modifier.relief(
    shape: Shape,
    face: Color,
    edge: Color,
    depth: Dp,
    pressed: Boolean = false,
    borderColor: Color? = null,
    borderWidth: Dp = 2.dp,
): Modifier = this
    .padding(bottom = depth)
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(top = depth.toPx()) { drawOutline(outline, edge) }
    }
    .graphicsLayer { translationY = if (pressed) depth.toPx() else 0f }
    .clip(shape)
    .background(face)
    .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier)

/** Élément en relief cliquable : renvoie le modificateur à poser sur la face. */
@Composable
internal fun Modifier.pressableRelief(
    shape: Shape,
    face: Color,
    edge: Color,
    depth: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    borderColor: Color? = null,
    borderWidth: Dp = 2.dp,
    role: Role = Role.Button,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .relief(shape, face, edge, depth, pressed && enabled, borderColor, borderWidth)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
