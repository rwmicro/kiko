package app.kiko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoDepth

/**
 * Carte blanche en relief (`.task`, `.req`, `.fix`) : filet de 2 dp et tranche neutre.
 * Avec [onClick], toute la carte s'enfonce quand on appuie.
 */
@Composable
fun KikoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    color: Color = Kiko.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Kiko.colors
    val shape = RoundedCornerShape(22.dp)
    val face = if (onClick != null) {
        modifier.pressableRelief(shape, color, c.hair, KikoDepth.large, true, onClick, borderColor = c.hair)
    } else {
        modifier.relief(shape, color, c.hair, KikoDepth.large, borderColor = c.hair)
    }
    Column(face.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** Étiquette de section en capitales (`.lbl`), par exemple « TES LANGUES ». */
@Composable
fun KikoSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Kiko.type.label, color = Kiko.colors.soft, modifier = modifier)
}

/** En-tête d'écran : grand titre et sous-titre facultatif. */
@Composable
fun KikoScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Kiko.type.title, color = Kiko.colors.ink)
        if (subtitle != null) Text(subtitle, style = Kiko.type.body, color = Kiko.colors.mid)
    }
}

/** Conseil de la mascotte dans une bulle (`.tip`). */
@Composable
fun KikoTip(text: String, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KikoMascot(size = 64.dp)
        Box(
            Modifier
                .background(c.tint, RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp, bottomEnd = 17.dp, bottomStart = 4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, style = Kiko.type.captionStrong, color = c.ink)
        }
    }
}

/** Encart « Question du jour » (`.qday`) sur fond primaire clair, avec son bouton. */
@Composable
fun KikoQuestionOfDay(
    question: String,
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier,
    kicker: String = "Question du jour",
    action: String = "Répondre",
) {
    val c = Kiko.colors
    Row(
        modifier
            .fillMaxWidth()
            .relief(RoundedCornerShape(22.dp), c.primaryLight, c.primaryLightEdge, KikoDepth.large, borderColor = c.primaryLightEdge)
            .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KikoMascot(size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(kicker, style = Kiko.type.micro, color = c.primaryDark)
            Text(question, style = Kiko.type.captionStrong.copy(fontSize = Kiko.type.body.fontSize), color = c.ink)
        }
        KikoButton(action, onAnswer, size = KikoButtonSize.Small)
    }
}

/** État vide (`.empty`) : mascotte, titre, texte et action facultative. */
@Composable
fun KikoEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = Kiko.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KikoMascot(size = 164.dp)
        Text(title, style = Kiko.type.titleSmall, color = c.ink, textAlign = TextAlign.Center)
        Text(message, style = Kiko.type.body, color = c.mid, textAlign = TextAlign.Center)
        if (action != null) KikoButton(action, onAction, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

/** Message bref en bas d'écran (`.toast`), par exemple « Ajouté au carnet ». */
@Composable
fun KikoToast(message: String, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Box(modifier.background(c.ink, RoundedCornerShape(17.dp)).padding(horizontal = 17.dp, vertical = 10.dp)) {
        Text(message, style = Kiko.type.captionStrong, color = c.paper)
    }
}

/** Feuille du bas (`.sheet`) : poignée, titre, contenu. La gestion modale reste à l'appelant. */
@Composable
fun KikoSheet(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Kiko.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.paper, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .border(1.dp, c.hair, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).padding(vertical = 2.dp).size(width = 44.dp, height = 5.dp).background(c.hair, CircleShape))
        Text(title, style = Kiko.type.title, color = c.ink)
        content()
    }
}
