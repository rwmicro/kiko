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

    /** Logo Apple (à teinter : noir ou blanc selon le bouton). */
    val AppleLogo: ImageVector by lazy {
        ImageVector.Builder("logo-apple", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701"), fill = SolidColor(Color.Black))
            .build()
    }

    /** Logo Google en quatre couleurs (ne pas teinter : `iconTint = Color.Unspecified`). */
    val GoogleLogo: ImageVector by lazy {
        ImageVector.Builder("logo-google", 24.dp, 24.dp, 48f, 48f)
            .addPath(addPathNodes("M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"), fill = SolidColor(Color(0xFFEA4335)))
            .addPath(addPathNodes("M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"), fill = SolidColor(Color(0xFF4285F4)))
            .addPath(addPathNodes("M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"), fill = SolidColor(Color(0xFFFBBC05)))
            .addPath(addPathNodes("M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"), fill = SolidColor(Color(0xFF34A853)))
            .build()
    }
}
