package app.kiko.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/** Espacements, sur une grille de 4 dp. */
object KikoSpace {
    val xxs = 4.dp
    val xs = 6.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Marge latérale des écrans. */
    val screen = 20.dp
}

/** Arrondis. */
object KikoRadius {
    val xs = RoundedCornerShape(8.dp)
    val s = RoundedCornerShape(12.dp)
    val m = RoundedCornerShape(16.dp)
    val l = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)
}

/** Épaisseur du relief sous les éléments cliquables (le « 0 4px 0 » des maquettes). */
object KikoDepth {
    val large = 4.dp
    val small = 3.dp
    val border = 2.dp
}

/** Point d'accès aux jetons : `Kiko.colors.primary`, `Kiko.type.title`. */
object Kiko {
    val colors: KikoPalette
        @Composable @ReadOnlyComposable get() = LocalKikoPalette.current

    val type: KikoType get() = KikoType
}

@Composable
fun KikoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary,
            primaryContainer = p.primaryLight, onPrimaryContainer = p.primaryDark,
            secondaryContainer = p.primaryLight, onSecondaryContainer = p.primaryDark,
            background = p.paper, onBackground = p.ink,
            surface = p.paper, onSurface = p.ink,
            surfaceVariant = p.tint, onSurfaceVariant = p.mid,
            outline = p.hair, outlineVariant = p.hair,
            error = p.error, errorContainer = p.errorLight,
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary,
            primaryContainer = p.primaryLight, onPrimaryContainer = p.primaryDark,
            secondaryContainer = p.primaryLight, onSecondaryContainer = p.primaryDark,
            background = p.paper, onBackground = p.ink,
            surface = p.paper, onSurface = p.ink,
            surfaceVariant = p.tint, onSurfaceVariant = p.mid,
            outline = p.hair, outlineVariant = p.hair,
            error = p.error, errorContainer = p.errorLight,
        )
    }
    val typography = Typography(
        displaySmall = KikoType.display,
        headlineMedium = KikoType.title,
        titleLarge = KikoType.titleSmall,
        titleMedium = KikoType.name,
        bodyLarge = KikoType.body,
        bodyMedium = KikoType.caption,
        bodySmall = KikoType.micro,
        labelLarge = KikoType.captionStrong,
    )
    CompositionLocalProvider(LocalKikoPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
