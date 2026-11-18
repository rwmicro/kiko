package app.kiko.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Pictogrammes des maquettes, repris tels quels des symboles SVG de
 * design/prototype-lezo.html (trait 1,9 sur une grille de 24). Teintés par `Icon`.
 * Fichier généré par tools/design/svg2kt.py.
 */
object KikoIcons {
    val Back: ImageVector by lazy {
        ImageVector.Builder("i-back", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 15 5 l -7 7 7 7"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Partners: ImageVector by lazy {
        ImageVector.Builder("i-people", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 5.5 8 A 3.5 3.5 0 1 0 12.5 8 A 3.5 3.5 0 1 0 5.5 8 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 2.5 20 c 0 -4 3 -6 6.5 -6 s 6.5 2 6.5 6"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 16 5 a 3.3 3.3 0 0 1 0 6.4 M 18.5 14.5 c 2 .8 3 2.6 3 5.5"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Chat: ImageVector by lazy {
        ImageVector.Builder("i-chat", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 4 5.5 A 2.5 2.5 0 0 1 6.5 3 h 11 A 2.5 2.5 0 0 1 20 5.5 v 8 a 2.5 2.5 0 0 1 -2.5 2.5 H 11 l -5 4.5 V 16 A 2.5 2.5 0 0 1 4 13.5 z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Feed: ImageVector by lazy {
        ImageVector.Builder("i-feed", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 3.5 12 A 8.5 8.5 0 1 0 20.5 12 A 8.5 8.5 0 1 0 3.5 12 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 3.5 12 h 17 M 12 3.5 c 3 3 3 14 0 17 M 12 3.5 c -3 3 -3 14 0 17"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Trophy: ImageVector by lazy {
        ImageVector.Builder("i-cup", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 7 3.5 h 10 V 10 a 5 5 0 0 1 -10 0 z M 7 6 H 3.5 c 0 3 1.5 4.5 4 5 M 17 6 h 3.5 c 0 3 -1.5 4.5 -4 5 M 12 15 v 4 M 8 20.5 h 8"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Me: ImageVector by lazy {
        ImageVector.Builder("i-me", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 8 8 A 4 4 0 1 0 16 8 A 4 4 0 1 0 8 8 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 4 20.5 c 0 -4.5 3.5 -7 8 -7 s 8 2.5 8 7"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder("i-search", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 4.5 11 A 6.5 6.5 0 1 0 17.5 11 A 6.5 6.5 0 1 0 4.5 11 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 16 16 l 4.5 4.5"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Filter: ImageVector by lazy {
        ImageVector.Builder("i-filter", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 4 7 h 10 M 18 7 h 2 M 4 17 h 2 M 10 17 h 10"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 13.8 7 A 2.2 2.2 0 1 0 18.2 7 A 2.2 2.2 0 1 0 13.8 7 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 5.8 17 A 2.2 2.2 0 1 0 10.2 17 A 2.2 2.2 0 1 0 5.8 17 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Flame: ImageVector by lazy {
        ImageVector.Builder("i-flame", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 12 3 c 1 4 6 6 6 11 a 6 6 0 0 1 -12 0 c 0 -3 2 -5 3 -6 0 2 1 3 2 3 0 -3 0 -5 1 -8 z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Wave: ImageVector by lazy {
        ImageVector.Builder("i-hand", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 8 13 V 6.5 a 1.5 1.5 0 0 1 3 0 V 11 m 0 -5.5 V 5 a 1.5 1.5 0 0 1 3 0 v 6 m 0 -4.5 a 1.5 1.5 0 0 1 3 0 V 14 a 6 6 0 0 1 -6 6 h -1 a 5 5 0 0 1 -4 -2 l -2.5 -4 a 1.5 1.5 0 0 1 2.5 -1.5 z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Send: ImageVector by lazy {
        ImageVector.Builder("i-send", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 4 12 l 16 -8 -6 16 -3 -6.5 z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Mic: ImageVector by lazy {
        ImageVector.Builder("i-mic", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 12 3 H 12 A 3 3 0 0 1 15 6 V 11 A 3 3 0 0 1 12 14 H 12 A 3 3 0 0 1 9 11 V 6 A 3 3 0 0 1 12 3 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 5.5 11.5 a 6.5 6.5 0 0 0 13 0 M 12 18 v 3"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Phone: ImageVector by lazy {
        ImageVector.Builder("i-phone", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 6 3.5 h 3 l 1.5 4.5 -2 1.5 a 11 11 0 0 0 6 6 l 1.5 -2 4.5 1.5 v 3 a 2 2 0 0 1 -2 2 A 15.5 15.5 0 0 1 4 5.5 a 2 2 0 0 1 2 -2 z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Notebook: ImageVector by lazy {
        ImageVector.Builder("i-book", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 7.5 3.5 H 16.5 A 3 3 0 0 1 19.5 6.5 V 17.5 A 3 3 0 0 1 16.5 20.5 H 7.5 A 3 3 0 0 1 4.5 17.5 V 6.5 A 3 3 0 0 1 7.5 3.5 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 9 8.5 h 6 M 9 12.5 h 6"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Bell: ImageVector by lazy {
        ImageVector.Builder("i-bell", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 6 16 V 11 a 6 6 0 0 1 12 0 v 5 l 1.5 2 h -15 z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 10 20.5 a 2 2 0 0 0 4 0"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder("i-gear", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 9 12 A 3 3 0 1 0 15 12 A 3 3 0 1 0 9 12 Z"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes("M 12 3 v 2.5 M 12 18.5 V 21 M 3 12 h 2.5 M 18.5 12 H 21 M 5.6 5.6 l 1.8 1.8 M 16.600 16.600 l 1.800 1.800 M 5.600 18.400 l 1.800 -1.800 M 16.600 7.400 l 1.800 -1.800"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder("i-x", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 6 6 l 12 12 M 18 6 L 6 18"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val More: ImageVector by lazy {
        ImageVector.Builder("i-more", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 5 12 h .01 M 12 12 h .01 M 19 12 h .01"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder("i-check", 12.dp, 12.dp, 12f, 12f)
            .addPath(addPathNodes("M 2.5 6.5 l 2.5 2.5 4.5 -5.5"), fill = null, fillAlpha = 1f, stroke = SolidColor(Color.Black), strokeAlpha = 1f, strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    /** Absent des maquettes : le retour, retourné. */
    val Chevron: ImageVector by lazy {
        ImageVector.Builder("chevron", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 9 5 l 7 7 -7 7"), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }

    /** Absent des maquettes : une croix droite. */
    val Plus: ImageVector by lazy {
        ImageVector.Builder("plus", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M 12 5 V 19 M 5 12 H 19"), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }
}
