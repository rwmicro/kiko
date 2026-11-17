package app.kiko.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typographie des maquettes, ramenée à taille réelle (les maquettes sont dessinées à 72 %).
 *
 * La maquette utilise la police « Allo » (titres arrondis, graisse 800 à 900). En attendant
 * de l'embarquer dans les ressources, on garde la police système avec les mêmes graisses.
 */
object KikoType {
    val family: FontFamily = FontFamily.Default

    /** Grand titre d'accueil et de récompense (h4 22px). */
    val display = TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em)

    /** Titre d'écran (h4 20px). */
    val title = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em)

    /** Titre de carte ou d'état vide (h4 18px, 15px). */
    val titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em)

    /** Nom dans une liste, titre de ligne. */
    val name = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)

    val body = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)

    val bodyStrong = body.copy(fontWeight = FontWeight.Bold)

    /** Texte secondaire : méta d'une ligne, explication (10.5px). */
    val caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp)

    val captionStrong = caption.copy(fontWeight = FontWeight.Bold)

    /** Très petit texte : heure, compteur (9.5px). */
    val micro = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 16.sp)

    /** Étiquette de section en capitales (.lbl). */
    val label = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.06.em)

    /** Texte des boutons, en capitales (.btn). */
    val button = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.06.em)

    val buttonSmall = button.copy(fontSize = 13.5.sp, lineHeight = 17.sp)

    /** Onglets du haut, en capitales (.tabsx). */
    val tab = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.05.em)
}
