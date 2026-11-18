package app.kiko.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette des maquettes (design/prototype-lezo.html).
 *
 * Les noms suivent les variables CSS de la maquette : paper, tint, hair, ink, mid, soft,
 * pri (primary), apr (apricot), sun, ok et err. Chaque couleur « Edge » est la tranche
 * pleine dessinée sous un élément en relief (le `box-shadow: 0 4px 0` des maquettes).
 */
@Immutable
data class KikoPalette(
    val isDark: Boolean,
    /** Fond des écrans. */
    val paper: Color,
    /** Fond teinté : fil de discussion, champs secondaires. */
    val tint: Color,
    /** Filets, bordures et tranches neutres. */
    val hair: Color,
    /** Fond des cartes, boutons blancs et champs. */
    val surface: Color,
    val ink: Color,
    val mid: Color,
    val soft: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Tranche sous les boutons primaires. */
    val primaryEdge: Color,
    /** Texte sur fond [primaryLight]. */
    val primaryDark: Color,
    val primaryLight: Color,
    /** Bordure et tranche des encarts sur fond [primaryLight]. */
    val primaryLightEdge: Color,
    val apricot: Color,
    val apricotDark: Color,
    val apricotLight: Color,
    val sun: Color,
    val sunEdge: Color,
    val sunLight: Color,
    /** Texte sur fond [sunLight]. */
    val sunInk: Color,
    val ok: Color,
    val okLight: Color,
    /** Texte surligné en vert dans les corrections. */
    val okInk: Color,
    val error: Color,
    val errorLight: Color,
    /** Fond de la barre d'onglets flottante. */
    val glass: Color,
)

val LightPalette = KikoPalette(
    isDark = false,
    paper = Color(0xFFFFF9EC),
    tint = Color(0xFFF6EEDD),
    hair = Color(0xFFE9DFCB),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF2A1F1A),
    mid = Color(0xFF6E6259),
    soft = Color(0xFFA59A8F),
    primary = Color(0xFFDD3F1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryEdge = Color(0xFFA82C0E),
    primaryDark = Color(0xFFA82C0E),
    primaryLight = Color(0xFFFFE4D6),
    primaryLightEdge = Color(0xFFFFCDB8),
    apricot = Color(0xFFF59B4B),
    apricotDark = Color(0xFFB9661A),
    apricotLight = Color(0xFFFFF0DF),
    sun = Color(0xFFF6B93B),
    sunEdge = Color(0xFFC98A12),
    sunLight = Color(0xFFFFEFC2),
    sunInk = Color(0xFF7A5A00),
    ok = Color(0xFF0E9594),
    okLight = Color(0xFFD9F1EF),
    okInk = Color(0xFF0A5E5D),
    error = Color(0xFFC81E5A),
    errorLight = Color(0xFFFBE1EA),
    glass = Color(0xEBFFFAF0),
)

val DarkPalette = KikoPalette(
    isDark = true,
    paper = Color(0xFF17120F),
    tint = Color(0xFF221A15),
    hair = Color(0xFF3A2E26),
    surface = Color(0xFF221A15),
    ink = Color(0xFFF6EEE2),
    mid = Color(0xFFB8AA9B),
    soft = Color(0xFF85776A),
    primary = Color(0xFFF2542D),
    onPrimary = Color(0xFFFFFFFF),
    primaryEdge = Color(0xFF8E2A12),
    primaryDark = Color(0xFFFFA98F),
    primaryLight = Color(0xFF4A1F12),
    primaryLightEdge = Color(0xFF6B2C18),
    apricot = Color(0xFFF59B4B),
    apricotDark = Color(0xFFF7B679),
    apricotLight = Color(0xFF3A2614),
    sun = Color(0xFFF6B93B),
    sunEdge = Color(0xFFA87410),
    sunLight = Color(0xFF3A3015),
    sunInk = Color(0xFFF6D27A),
    ok = Color(0xFF2BB8B5),
    okLight = Color(0xFF0F3534),
    okInk = Color(0xFF8FE0DD),
    error = Color(0xFFF0628F),
    errorLight = Color(0xFF3D1424),
    glass = Color(0xEB221A15),
)

val LocalKikoPalette = staticCompositionLocalOf { LightPalette }

/** Teintes de fond des avatars sans photo, choisies dans la palette chaude. */
internal val AvatarTints = listOf(
    Color(0xFFF59B4B),
    Color(0xFF0E9594),
    Color(0xFFDD3F1A),
    Color(0xFFC98A12),
    Color(0xFF8C5A9E),
    Color(0xFF3F7CAC),
)
