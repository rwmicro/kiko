package app.kiko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoType

/** Onglets du haut en capitales (`.tabsx`), soulignés en primaire, défilants. */
@Composable
fun KikoTopTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            tabs.forEach { tab ->
                val on = tab == selected
                Column(
                    Modifier.width(IntrinsicSize.Max).selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tab.uppercase(), style = KikoType.tab, color = if (on) c.primary else c.soft, modifier = Modifier.padding(vertical = 9.dp))
                    Box(Modifier.matchTextWidthUnderline(on, c.primary))
                }
            }
        }
        KikoDivider()
    }
}

private fun Modifier.matchTextWidthUnderline(on: Boolean, color: Color): Modifier =
    if (on) fillMaxWidth().height(3.dp).background(color, RoundedCornerShape(2.dp)) else height(3.dp)

/** Un onglet de la barre du bas. */
data class KikoTab(val label: String, val icon: ImageVector, val badge: Int = 0)

/** Les cinq onglets de l'app, dans l'ordre des maquettes. */
val KikoMainTabs = listOf(
    KikoTab("Partenaires", KikoIcons.Partners),
    KikoTab("Discussions", KikoIcons.Chat),
    KikoTab("Fil", KikoIcons.Feed),
    KikoTab("Défis", KikoIcons.Trophy),
    KikoTab("Moi", KikoIcons.Me),
)

/**
 * Barre d'onglets flottante (`.app .tabbar`, version « verre ») : pilule posée au-dessus
 * du contenu, onglet actif sur fond légèrement teinté, compteur abricot.
 */
@Composable
fun KikoBottomBar(tabs: List<KikoTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .shadow(14.dp, shape, ambientColor = Color(0x553C1E0A), spotColor = Color(0x553C1E0A))
            .background(c.glass, shape)
            .border(1.dp, c.hair, shape)
            .height(64.dp)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .background(if (on) c.ink.copy(alpha = 0.07f) else Color.Transparent, RoundedCornerShape(27.dp))
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(tab.icon, contentDescription = null, tint = if (on) c.primary else c.soft, modifier = Modifier.size(23.dp))
                    Text(
                        tab.label,
                        style = KikoType.micro.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                        color = if (on) c.primary else c.soft,
                        maxLines = 1,
                    )
                }
                if (tab.badge > 0) KikoCountBadge(tab.badge, Modifier.align(Alignment.TopCenter).offset(x = 13.dp, y = 2.dp))
            }
        }
    }
}

/** Progression d'un parcours en segments (`.prog`) : un segment par étape. */
@Composable
fun KikoStepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            Box(Modifier.weight(1f).height(12.dp).background(if (i < current) c.primary else c.hair, CircleShape))
        }
    }
}

/**
 * En-tête d'étape : bouton retour et progression. [current] à null masque la progression
 * (étapes facultatives comme le selfie).
 */
@Composable
fun KikoStepHeader(current: Int?, total: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.CenterStart) {
            Icon(KikoIcons.Back, contentDescription = "Retour", tint = c.mid, modifier = Modifier.size(26.dp))
        }
        if (current != null) KikoStepProgress(current, total, Modifier.weight(1f))
    }
}

/** Barre de titre d'onglet : titre à gauche, actions à droite. */
@Composable
fun KikoTopBar(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = KikoType.title, color = Kiko.colors.ink, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Semaine de série (`.week`) : jours faits en soleil, aujourd'hui en primaire cerclé. */
@Composable
fun KikoWeekStreak(
    doneDays: Int,
    modifier: Modifier = Modifier,
    today: Int = doneDays,
    labels: List<String> = listOf("L", "M", "M", "J", "V", "S", "D"),
) {
    val c = Kiko.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val (bg, fg) = when {
                i == today -> c.primary to c.onPrimary
                i < doneDays -> c.sun to Color(0xFF2A1F1A)
                else -> c.hair to c.mid
            }
            Box(
                Modifier
                    .then(if (i == today) Modifier.border(4.dp, c.primaryLight, CircleShape).padding(4.dp) else Modifier.padding(4.dp))
                    .size(34.dp)
                    .background(bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = KikoType.micro.copy(fontWeight = FontWeight.Bold), color = fg)
            }
        }
    }
}
