package app.kiko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kiko.ui.theme.Kiko

/**
 * Drapeau d'une langue ou d'un pays, dessiné (les émojis drapeaux ne s'affichent
 * pas partout). Codes connus : fr, id, es, it, de, jp, mx. Sinon, le code en capitales.
 */
@Composable
fun KikoFlag(code: String, modifier: Modifier = Modifier, width: Dp = 22.dp) {
    val height = width * 0.7f
    val shape = RoundedCornerShape(3.dp)
    val drawn = code.lowercase() in setOf("fr", "id", "es", "it", "de", "jp", "mx")
    if (!drawn) {
        Box(modifier.size(width, height).background(Kiko.colors.tint, shape), contentAlignment = Alignment.Center) {
            Text(code.uppercase().take(2), style = Kiko.type.micro.copy(fontSize = Kiko.type.micro.fontSize * 0.7f), color = Kiko.colors.mid)
        }
        return
    }
    Canvas(modifier.size(width, height).clip(shape)) {
        val w = size.width
        val h = size.height
        fun vertical(colors: List<Color>) = colors.forEachIndexed { i, col ->
            drawRect(col, Offset(w * i / colors.size, 0f), Size(w / colors.size + 0.5f, h))
        }
        fun horizontal(vararg parts: Pair<Color, Float>) {
            var y = 0f
            parts.forEach { (col, share) -> drawRect(col, Offset(0f, y), Size(w, h * share + 0.5f)); y += h * share }
        }
        when (code.lowercase()) {
            "fr" -> vertical(listOf(Color(0xFF0055A4), Color.White, Color(0xFFEF4135)))
            "it" -> vertical(listOf(Color(0xFF009246), Color.White, Color(0xFFCE2B37)))
            "mx" -> vertical(listOf(Color(0xFF006847), Color.White, Color(0xFFCE1126)))
            "id" -> horizontal(Color(0xFFCE1126) to 0.5f, Color.White to 0.5f)
            "es" -> horizontal(Color(0xFFAA151B) to 0.25f, Color(0xFFF1BF00) to 0.5f, Color(0xFFAA151B) to 0.25f)
            "de" -> horizontal(Color.Black to 1f / 3, Color(0xFFDD0000) to 1f / 3, Color(0xFFFFCE00) to 1f / 3)
            "jp" -> {
                drawRect(Color.White)
                drawCircle(Color(0xFFBC002D), radius = h * 0.3f)
            }
        }
        drawRect(Color.Black.copy(alpha = 0.08f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
    }
}

/**
 * Ligne de choix dans une liste (liste des langues) : drapeau, nom, précision à droite,
 * pastille radio. Le nom passe en primaire quand la ligne est choisie.
 */
@Composable
fun KikoSelectRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    flag: String? = null,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .disabledAlpha(enabled),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (flag != null) KikoFlag(flag, width = 26.dp)
            Text(text, style = Kiko.type.bodyStrong.copy(fontSize = Kiko.type.name.fontSize * 0.93f), color = if (selected) c.primary else c.ink, modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, style = Kiko.type.caption, color = c.soft)
            Box(
                Modifier.size(22.dp).then(
                    if (selected) Modifier.background(c.primary, CircleShape).padding(6.dp).background(c.surface, CircleShape)
                    else Modifier.background(c.soft, CircleShape).padding(2.dp).background(c.paper, CircleShape),
                ),
            )
        }
        KikoDivider()
    }
}

enum class KikoStepState { Done, Current, Todo }

/** Une étape d'une liste de progression. */
data class KikoStep(val title: String, val state: KikoStepState, val detail: String? = null)

/**
 * Liste d'étapes numérotées (`.vst`, « Tes premiers pas ») : faite en vert avec une coche,
 * en cours en soleil, à venir en gris. [trailing] place un bouton au bout d'une étape.
 */
@Composable
fun KikoStepList(
    steps: List<KikoStep>,
    modifier: Modifier = Modifier,
    trailing: @Composable (index: Int) -> Unit = {},
) {
    val c = Kiko.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val (bg, fg) = when (step.state) {
                    KikoStepState.Done -> c.ok to Color.White
                    KikoStepState.Current -> c.sun to Color(0xFF2A1F1A)
                    KikoStepState.Todo -> c.hair to c.mid
                }
                Box(Modifier.size(28.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                    if (step.state == KikoStepState.Done) {
                        Icon(KikoIcons.Check, contentDescription = "Fait", tint = fg, modifier = Modifier.size(16.dp))
                    } else {
                        Text("${i + 1}", style = Kiko.type.micro.copy(fontWeight = FontWeight.ExtraBold), color = fg)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        step.title,
                        style = Kiko.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = if (step.state == KikoStepState.Todo) c.soft else c.ink,
                    )
                    if (step.detail != null) Text(step.detail, style = Kiko.type.micro, color = c.mid)
                }
                trailing(i)
            }
        }
    }
}

/** Remarque de la mascotte sans bulle (`.icount`, aides sous un formulaire). */
@Composable
fun KikoMascotNote(text: String, modifier: Modifier = Modifier, mood: KikoMascotMood = KikoMascotMood.Smile) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KikoMascot(size = 48.dp, mood = mood)
        Text(text, style = Kiko.type.caption, color = Kiko.colors.mid, modifier = Modifier.weight(1f))
    }
}

/** Mention compacte d'une correction dans le fil (`.fixc`) : « Lucía t'a corrigé · 1 correction · Voir ». */
@Composable
fun KikoCorrectionChip(author: String, count: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Row(
            Modifier
                .relief(RoundedCornerShape(17.dp), c.surface, c.hair, KikoDepth4, borderColor = c.hair, borderWidth = 2.dp)
                .selectable(selected = false, role = Role.Button, onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            KikoAvatar(author, size = 20.dp)
            Text("$author t'a corrigé", style = Kiko.type.caption.copy(fontWeight = FontWeight.Bold), color = c.ink)
            Text(
                "$count correction${if (count > 1) "s" else ""} · Voir",
                style = Kiko.type.caption.copy(fontWeight = FontWeight.ExtraBold),
                color = c.primaryDark,
            )
        }
    }
}

private val KikoDepth4 = 4.dp

enum class KikoMascotMood { Smile, Yay }

/** L'axolotl des maquettes. */
@Composable
fun KikoMascot(modifier: Modifier = Modifier, size: Dp = 46.dp, mood: KikoMascotMood = KikoMascotMood.Smile) {
    val vector = if (mood == KikoMascotMood.Yay) MascotVectors.Yay else MascotVectors.Smile
    Image(rememberVectorPainter(vector), contentDescription = null, modifier = modifier.size(size))
}
