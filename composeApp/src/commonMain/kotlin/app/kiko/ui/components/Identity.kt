package app.kiko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiko.ui.theme.AvatarTints
import app.kiko.ui.theme.Kiko
import kotlin.math.abs

enum class KikoAvatarShape { Circle, Rounded }

/**
 * Avatar. Sans photo, il affiche l'initiale sur une teinte stable tirée du nom.
 * [online] ajoute la pastille verte en bas à droite (`.lrow .ph em`).
 * [ring] ajoute l'anneau vert des partenaires en ligne (`.online .ph`).
 * Pour une vraie photo, passer le contenu dans [image].
 */
@Composable
fun KikoAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: KikoAvatarShape = KikoAvatarShape.Circle,
    online: Boolean = false,
    ring: Boolean = false,
    image: (@Composable () -> Unit)? = null,
) {
    val c = Kiko.colors
    val clip = if (shape == KikoAvatarShape.Circle) CircleShape else RoundedCornerShape(size * 0.31f)
    val tint = AvatarTints[abs(name.hashCode()) % AvatarTints.size]
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .then(if (ring) Modifier.border(2.dp, c.ok, clip).padding(4.dp) else Modifier)
                .clip(clip)
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                image()
            } else {
                Text(
                    name.trim().take(1).uppercase(),
                    style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.4f).sp),
                    color = Color.White,
                )
            }
        }
        if (online) {
            val dot = (size.value * 0.27f).coerceIn(10f, 18f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(dot)
                    .background(c.paper, CircleShape)
                    .padding(dot * 0.18f)
                    .background(c.ok, CircleShape),
            )
        }
    }
}

/** Deux avatars qui se chevauchent (`.pair .avs`) : un binôme, une série à deux. */
@Composable
fun KikoAvatarPair(first: String, second: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = Kiko.colors
    Row(modifier) {
        KikoAvatar(first, Modifier.border(3.dp, c.paper, CircleShape), size = size)
        KikoAvatar(second, Modifier.offset(x = -(size * 0.24f)).border(3.dp, c.paper, CircleShape), size = size)
    }
}

/** Badge « Vérifié » après un prénom (`.ver`). */
@Composable
fun KikoVerifiedBadge(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val c = Kiko.colors
    Box(modifier.size(size).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
        Icon(KikoIcons.Check, contentDescription = "Vérifié", tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/** Pastille « en ligne » dans un texte (`.dot`). */
@Composable
fun KikoOnlineDot(modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).background(Kiko.colors.ok, CircleShape))
}

/**
 * Mascotte provisoire : une bouille ronde. À remplacer par l'illustration
 * définitive quand elle sera exportée des maquettes.
 */
@Composable
fun KikoMascot(modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val c = Kiko.colors
    val body = c.apricot
    val cheek = c.primary.copy(alpha = 0.35f)
    val eye = Color(0xFF2A1F1A)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(body, radius = s / 2f)
        drawCircle(Color.White.copy(alpha = 0.25f), radius = s * 0.18f, center = Offset(s * 0.32f, s * 0.28f))
        drawCircle(eye, radius = s * 0.06f, center = Offset(s * 0.36f, s * 0.46f))
        drawCircle(eye, radius = s * 0.06f, center = Offset(s * 0.64f, s * 0.46f))
        drawCircle(cheek, radius = s * 0.07f, center = Offset(s * 0.24f, s * 0.6f))
        drawCircle(cheek, radius = s * 0.07f, center = Offset(s * 0.76f, s * 0.6f))
        drawArc(
            color = eye,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(s * 0.4f, s * 0.48f),
            size = Size(s * 0.2f, s * 0.16f),
            style = Stroke(width = s * 0.045f, cap = StrokeCap.Round),
        )
    }
}
