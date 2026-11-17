package app.kiko.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Couleurs des maquettes (design/prototype-lezo.html) : papier chaud, encre brune, orange vif. */
object KikoColors {
    val Paper = Color(0xFFFFF9EC)
    val Tint = Color(0xFFF6EEDD)
    val Hair = Color(0xFFE9DFCB)
    val Ink = Color(0xFF2A1F1A)
    val Mid = Color(0xFF6E6259)
    val Primary = Color(0xFFDD3F1A)
    val PrimaryDark = Color(0xFFA82C0E)
    val PrimaryLight = Color(0xFFFFE4D6)

    val NightBg = Color(0xFF17120F)
    val NightCard = Color(0xFF221A15)
    val NightInk = Color(0xFFF6EEE2)
    val NightMid = Color(0xFFB8AA9B)
    val NightLine = Color(0xFF3A2E26)
    val NightPrimary = Color(0xFFFF9B7A)
}

private val LightColors = lightColorScheme(
    primary = KikoColors.Primary,
    onPrimary = Color.White,
    primaryContainer = KikoColors.PrimaryLight,
    onPrimaryContainer = KikoColors.PrimaryDark,
    secondaryContainer = KikoColors.PrimaryLight,
    onSecondaryContainer = KikoColors.PrimaryDark,
    background = KikoColors.Paper,
    onBackground = KikoColors.Ink,
    surface = KikoColors.Paper,
    onSurface = KikoColors.Ink,
    surfaceVariant = KikoColors.Tint,
    onSurfaceVariant = KikoColors.Mid,
    outline = KikoColors.Hair,
    outlineVariant = KikoColors.Hair,
)

private val DarkColors = darkColorScheme(
    primary = KikoColors.NightPrimary,
    onPrimary = KikoColors.NightBg,
    primaryContainer = KikoColors.NightLine,
    onPrimaryContainer = KikoColors.NightPrimary,
    secondaryContainer = KikoColors.NightLine,
    onSecondaryContainer = KikoColors.NightPrimary,
    background = KikoColors.NightBg,
    onBackground = KikoColors.NightInk,
    surface = KikoColors.NightBg,
    onSurface = KikoColors.NightInk,
    surfaceVariant = KikoColors.NightCard,
    onSurfaceVariant = KikoColors.NightMid,
    outline = KikoColors.NightLine,
    outlineVariant = KikoColors.NightLine,
)

@Composable
fun KikoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
