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

/** Relief sous les éléments cliquables : le « 0 4px 0 » des maquettes, ×1,39. */
object KikoDepth {
    val large = 5.5.dp
    val small = 4.dp

    /** Filet des éléments en relief (2 px dans les maquettes). */
    val border = 2.75.dp

    /** Filet fin (1,5 px dans les maquettes). */
    val hairline = 2.dp
}

/** Point d'accès aux jetons : `Kiko.colors.primary`, `Kiko.type.title`. */
object Kiko {
    val colors: KikoPalette
        @Composable @ReadOnlyComposable get() = LocalKikoPalette.current

    val type: KikoTypography
        @Composable @ReadOnlyComposable get() = LocalKikoTypography.current
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
    val t = rememberKikoTypography()
    val typography = Typography(
        displaySmall = t.display,
        headlineMedium = t.title,
        titleLarge = t.titleSmall,
        titleMedium = t.name,
        bodyLarge = t.body,
        bodyMedium = t.caption,
        bodySmall = t.micro,
        labelLarge = t.captionStrong,
    )
    CompositionLocalProvider(LocalKikoPalette provides p, LocalKikoTypography provides t) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
