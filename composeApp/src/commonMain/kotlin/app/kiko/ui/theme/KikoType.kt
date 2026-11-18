package app.kiko.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kiko.resources.Res
import app.kiko.resources.allo_round_black
import app.kiko.resources.allo_round_extrabold
import app.kiko.resources.allo_text_bold
import app.kiko.resources.allo_text_extrabold
import app.kiko.resources.allo_text_medium
import app.kiko.resources.allo_text_semibold
import org.jetbrains.compose.resources.Font

/**
 * Typographie des maquettes, à taille réelle : les maquettes dessinent un téléphone de
 * 280 px de large, soit un facteur de 390 / 280 ≈ 1,39 vers les dp et sp.
 *
 * Police « Allo » (licence SIL OFL), embarquée en instances fixes tirées de la police
 * variable des maquettes : [text] pour le texte (axe ROND 50) et [round] pour les titres
 * (ROND 100, le `font-variation-settings: "ROND" 100` des `h4`).
 */
@Immutable
class KikoTypography(text: FontFamily, round: FontFamily) {
    /** Grand titre d'accueil et de récompense (`.wel h4`, 22 px). */
    val display = TextStyle(fontFamily = round, fontWeight = FontWeight.Black, fontSize = 30.5.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em)

    /** Titre d'écran et de feuille (`.app h4`, 20 px). */
    val title = TextStyle(fontFamily = round, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 31.sp, letterSpacing = (-0.02).em)

    /** Titre d'état vide ou de carte (`.empty h4`, 18 px ; `.rt b`, 17 px). */
    val titleSmall = TextStyle(fontFamily = round, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em)

    /** Nom dans une liste (`.lrow .n`, 13 px). */
    val name = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp)

    /** Texte courant (11,5 px). */
    val body = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)

    val bodyStrong = body.copy(fontWeight = FontWeight.Bold)

    /** Texte secondaire (10,5 px). */
    val caption = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, lineHeight = 19.sp)

    val captionStrong = caption.copy(fontWeight = FontWeight.Bold)

    /** Petit texte : heure, compteur (9,5 px). */
    val micro = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)

    /** Étiquette de section en capitales (`.lbl`, 9,5 px). */
    val label = TextStyle(fontFamily = text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.06.em)

    /** Boutons, en capitales (`.btn`, 12 px). */
    val button = TextStyle(fontFamily = text, fontWeight = FontWeight.ExtraBold, fontSize = 16.5.sp, lineHeight = 20.sp, letterSpacing = 0.06.em)

    /** Petits boutons (`.btn.sm`, 10 px). */
    val buttonSmall = button.copy(fontSize = 14.sp, lineHeight = 17.sp)

    /** Onglets du haut, en capitales (`.tabsx`, 10,5 px). */
    val tab = TextStyle(fontFamily = text, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, lineHeight = 18.sp, letterSpacing = 0.05.em)
}

val LocalKikoTypography = staticCompositionLocalOf { KikoTypography(FontFamily.Default, FontFamily.Default) }

@Composable
internal fun rememberKikoTypography(): KikoTypography {
    val text = FontFamily(
        Font(Res.font.allo_text_medium, FontWeight.Medium),
        Font(Res.font.allo_text_semibold, FontWeight.SemiBold),
        Font(Res.font.allo_text_bold, FontWeight.Bold),
        Font(Res.font.allo_text_extrabold, FontWeight.ExtraBold),
    )
    val round = FontFamily(
        Font(Res.font.allo_round_extrabold, FontWeight.ExtraBold),
        Font(Res.font.allo_round_black, FontWeight.Black),
    )
    return remember(text, round) { KikoTypography(text, round) }
}
