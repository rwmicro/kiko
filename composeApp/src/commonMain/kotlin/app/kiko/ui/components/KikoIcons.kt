package app.kiko.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Pictogrammes au trait (24 × 24, trait 2), dans l'esprit des maquettes.
 * Définis ici pour ne pas dépendre d'une bibliothèque d'icônes.
 */
object KikoIcons {
    val Back = icon("back", "M15 18 L9 12 L15 6")
    val Chevron = icon("chevron", "M9 18 L15 12 L9 6")
    val Check = icon("check", "M20 6 L9 17 L4 12", width = 3f)
    val Close = icon("close", "M18 6 L6 18 M6 6 L18 18")
    val Plus = icon("plus", "M12 5 V19 M5 12 H19")
    val Search = icon("search", "M18 11 a7 7 0 1 1 -14 0 a7 7 0 1 1 14 0 Z M21 21 L16.7 16.7")
    val Filter = icon("filter", "M4 6 H20 M7 12 H17 M10 18 H14")
    val Partners = icon(
        "partners",
        "M16 21 v-2 a4 4 0 0 0 -4 -4 H6 a4 4 0 0 0 -4 4 v2 M13 7 a4 4 0 1 1 -8 0 a4 4 0 1 1 8 0 Z " +
            "M22 21 v-2 a4 4 0 0 0 -3 -3.87 M16 3.13 a4 4 0 0 1 0 7.75",
    )
    val Chat = icon("chat", "M21 15 a2 2 0 0 1 -2 2 H7 l-4 4 V5 a2 2 0 0 1 2 -2 h14 a2 2 0 0 1 2 2 Z")
    val Feed = icon(
        "feed",
        "M22 12 a10 10 0 1 1 -20 0 a10 10 0 1 1 20 0 Z M2 12 H22 " +
            "M12 2 a15 15 0 0 1 4 10 a15 15 0 0 1 -4 10 a15 15 0 0 1 -4 -10 a15 15 0 0 1 4 -10 Z",
    )
    val Trophy = icon(
        "trophy",
        "M6 9 H4.5 a2.5 2.5 0 0 1 0 -5 H6 M18 9 h1.5 a2.5 2.5 0 0 0 0 -5 H18 M4 22 H20 " +
            "M10 14.7 V17 c0 0.6 -0.5 1 -1 1.2 C7.8 18.8 7 20.2 7 22 M14 14.7 V17 c0 0.6 0.5 1 1 1.2 C16.2 18.8 17 20.2 17 22 " +
            "M18 2 H6 v7 a6 6 0 0 0 12 0 V2 Z",
    )
    val Me = icon("me", "M19 21 v-2 a4 4 0 0 0 -4 -4 H9 a4 4 0 0 0 -4 4 v2 M16 7 a4 4 0 1 1 -8 0 a4 4 0 1 1 8 0 Z")
    val Video = icon("video", "M23 7 L16 12 L23 17 Z M3 5 H14 a2 2 0 0 1 2 2 V17 a2 2 0 0 1 -2 2 H3 a2 2 0 0 1 -2 -2 V7 a2 2 0 0 1 2 -2 Z")
    val Mic = icon("mic", "M12 2 a3 3 0 0 0 -3 3 v7 a3 3 0 0 0 6 0 V5 a3 3 0 0 0 -3 -3 Z M19 10 v2 a7 7 0 0 1 -14 0 v-2 M12 19 v3")
    val Send = icon("send", "M22 2 L11 13 M22 2 L15 22 L11 13 L2 9 Z")
    val Flame = icon(
        "flame",
        "M8.5 14.5 A2.5 2.5 0 0 0 11 12 c0 -1.4 -0.5 -2 -1 -3 c-1.1 -2.1 -0.2 -4 2 -6 c0.5 2.5 2 4.9 4 6.5 " +
            "c2 1.6 3 3.5 3 5.5 a7 7 0 1 1 -14 0 c0 -1.2 0.4 -2.3 1 -3 a2.5 2.5 0 0 0 2.5 2.5 Z",
    )
    val Notebook = icon("notebook", "M4 19.5 A2.5 2.5 0 0 1 6.5 17 H20 M6.5 2 H20 V22 H6.5 A2.5 2.5 0 0 1 4 19.5 V4.5 A2.5 2.5 0 0 1 6.5 2 Z")
    val Bell = icon("bell", "M18 8 a6 6 0 0 0 -12 0 c0 7 -3 9 -3 9 H21 s-3 -2 -3 -9 M13.7 21 a2 2 0 0 1 -3.4 0")
    val Wave = icon(
        "wave",
        "M18 11 V6 a2 2 0 0 0 -4 0 v5 M14 10 V4 a2 2 0 0 0 -4 0 v6 M10 10.5 V6 a2 2 0 0 0 -4 0 v8 " +
            "M18 8 a2 2 0 1 1 4 0 v6 a8 8 0 0 1 -8 8 h-2 c-2.8 0 -4.5 -0.9 -6 -2.4 L2.4 16 a2 2 0 0 1 2.8 -2.8 L7 15",
    )
    val Settings = icon(
        "settings",
        "M15 12 a3 3 0 1 1 -6 0 a3 3 0 1 1 6 0 Z " +
            "M12 2 v3 M12 19 v3 M4.9 4.9 l2.1 2.1 M17 17 l2.1 2.1 M2 12 h3 M19 12 h3 M4.9 19.1 L7 17 M17 7 l2.1 -2.1",
    )

    private fun icon(name: String, path: String, width: Float = 2f): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
