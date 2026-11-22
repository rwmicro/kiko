package app.kiko.chats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import app.kiko.data.ChatBackground
import app.kiko.resources.Res
import app.kiko.resources.bg_beach
import app.kiko.resources.bg_forest
import app.kiko.resources.bg_lagoon
import app.kiko.resources.bg_night
import app.kiko.resources.bg_sakura
import app.kiko.resources.bg_sky_dawn
import app.kiko.resources.bg_sky_day
import app.kiko.resources.bg_sky_dusk
import app.kiko.ui.theme.Kiko
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource

/**
 * Le ciel chez l'autre (signature des maquettes) : il colore l'en-tête du chat et sert de fond
 * par défaut. Aube 5 h à 8 h, jour 8 h à 18 h, crépuscule 18 h à 21 h, nuit 21 h à 5 h.
 */
enum class SkyPeriod(val top: Color, val bottom: Color, val onDark: Boolean, internal val image: DrawableResource) {
    Dawn(Color(0xFFF59A6B), Color(0xFFFBD29C), false, Res.drawable.bg_sky_dawn),
    Day(Color(0xFF6DBDEE), Color(0xFFB4DFF7), false, Res.drawable.bg_sky_day),
    Dusk(Color(0xFFE2683F), Color(0xFFF3A66A), false, Res.drawable.bg_sky_dusk),
    Night(Color(0xFF1C2052), Color(0xFF353A84), true, Res.drawable.bg_night),
    ;

    val brush: Brush get() = Brush.verticalGradient(listOf(top, bottom))

    companion object {
        fun at(hour: Int): SkyPeriod = when (hour) {
            in 5..7 -> Dawn
            in 8..17 -> Day
            in 18..20 -> Dusk
            else -> Night
        }
    }
}

/** L'image d'un fond, 72 × 108 pixels ; « Son ciel » suit l'heure chez l'autre. */
internal fun ChatBackground.image(period: SkyPeriod): DrawableResource? = when (this) {
    ChatBackground.Sky -> period.image
    ChatBackground.Beach -> Res.drawable.bg_beach
    ChatBackground.Forest -> Res.drawable.bg_forest
    ChatBackground.Lagoon -> Res.drawable.bg_lagoon
    ChatBackground.Sakura -> Res.drawable.bg_sakura
    ChatBackground.None -> null
}

/** Le fond de la discussion, en pixels nets, calé en bas pour garder l'horizon. */
@Composable
fun ChatBackdrop(background: ChatBackground, period: SkyPeriod, modifier: Modifier = Modifier) {
    val res = background.image(period)
    if (res == null) {
        Box(modifier.background(Kiko.colors.paper))
    } else {
        Image(
            imageResource(res),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            filterQuality = FilterQuality.None,
        )
    }
}
