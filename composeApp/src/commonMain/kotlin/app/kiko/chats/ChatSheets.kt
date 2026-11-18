@file:OptIn(ExperimentalTime::class)

package app.kiko.chats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.kiko.data.ChatBackground
import app.kiko.data.ChatMessage
import app.kiko.data.CorrectionPayload
import app.kiko.data.ReportReason
import app.kiko.ui.components.KikoAvatar
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoCheckRow
import app.kiko.ui.components.KikoChip
import app.kiko.ui.components.KikoIcons
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoMascotNote
import app.kiko.ui.components.KikoMenuRow
import app.kiko.ui.components.KikoRadioOption
import app.kiko.ui.components.KikoSheet
import app.kiko.ui.components.KikoSlotOption
import app.kiko.ui.components.KikoTextField
import app.kiko.ui.components.KikoWordChip
import app.kiko.ui.theme.Kiko
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.imageResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Les feuilles du chat, une à la fois. */
internal sealed interface ChatSheet {
    data object More : ChatSheet
    data object Background : ChatSheet
    data object Report : ChatSheet
    data object Call : ChatSheet
    data class Correct(val message: ChatMessage) : ChatSheet
}

/** Voile et feuille du bas ; toucher le voile ferme. */
@Composable
internal fun BoxScope.SheetOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        content()
        Spacer(Modifier.fillMaxWidth().background(Kiko.colors.paper).windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
}

private val SheetModifier = Modifier.heightIn(max = 640.dp)

/** Menu « … » de l'en-tête : fond, signaler, bloquer. */
@Composable
internal fun MoreSheet(
    name: String,
    background: ChatBackground?,
    onBackground: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    var confirmBlock by remember { mutableStateOf(false) }
    KikoSheet(name) {
        Column {
            if (background != null) {
                KikoMenuRow("Fond de la discussion", icon = KikoIcons.Palette, value = background.label, onClick = onBackground)
            }
            KikoMenuRow("Signaler $name", icon = KikoIcons.Flag, destructive = true, onClick = onReport)
            KikoMenuRow(
                if (confirmBlock) "Confirmer : bloquer $name" else "Bloquer $name",
                icon = KikoIcons.Block,
                destructive = true,
                onClick = { if (confirmBlock) onBlock() else confirmBlock = true },
            )
        }
    }
}

/** Changer le fond (maquette 17) : choisi ici, vu des deux côtés. */
@Composable
internal fun BackgroundSheet(
    name: String,
    current: ChatBackground,
    period: SkyPeriod,
    busy: Boolean,
    onApply: (ChatBackground) -> Unit,
    onCancel: () -> Unit,
) {
    var choice by remember(current) { mutableStateOf(current) }
    val c = Kiko.colors
    KikoSheet("Fond de la discussion", SheetModifier.verticalScroll(rememberScrollState())) {
        ChatBackground.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { bg ->
                    val selected = bg == choice
                    Column(
                        Modifier.weight(1f).clickable(role = Role.RadioButton) { choice = bg },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(shape)
                                .background(c.paper)
                                .border(if (selected) 3.dp else 2.dp, if (selected) c.primary else c.hair, shape),
                        ) {
                            bg.image(period)?.let {
                                Image(
                                    imageResource(it),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().padding(if (selected) 3.dp else 2.dp).clip(RoundedCornerShape(11.dp)),
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.BottomCenter,
                                    filterQuality = FilterQuality.None,
                                )
                            }
                            if (bg == ChatBackground.Sky) {
                                Text(
                                    "Auto",
                                    style = Kiko.type.micro,
                                    color = c.ink,
                                    modifier = Modifier.padding(6.dp).background(c.surface, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp),
                                )
                            }
                        }
                        Text(bg.label, style = Kiko.type.micro, color = if (selected) c.primaryDark else c.mid)
                    }
                }
            }
        }
        KikoMascotNote("$name verra le même fond que toi.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KikoButton("Annuler", onCancel, Modifier.weight(1f), style = KikoButtonStyle.White)
            KikoButton("Appliquer", { onApply(choice) }, Modifier.weight(1f), enabled = !busy)
        }
    }
}

/** Signaler (maquette 15) : un motif, un mot facultatif, et bloquer dans la foulée. */
@Composable
internal fun ReportSheet(name: String, busy: Boolean, onSend: (ReportReason, String, Boolean) -> Unit, onCancel: () -> Unit) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var details by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(true) }
    KikoSheet("Signaler $name", SheetModifier.verticalScroll(rememberScrollState())) {
        Text(
            "Ton signalement reste anonyme. L'équipe Kiko le lit et peut suspendre le compte.",
            style = Kiko.type.caption,
            color = Kiko.colors.mid,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportReason.entries.forEach { r ->
                KikoRadioOption(r.label, selected = reason == r, onClick = { reason = r })
            }
        }
        KikoTextField(
            details,
            { details = it.take(1000) },
            placeholder = "Ce qui s'est passé (facultatif)",
            singleLine = false,
        )
        KikoCheckRow(alsoBlock, { alsoBlock = it }, "Bloquer aussi $name : vous ne pourrez plus vous écrire.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KikoButton("Annuler", onCancel, Modifier.weight(1f), style = KikoButtonStyle.White)
            KikoButton(
                "Signaler",
                { reason?.let { onSend(it, details, alsoBlock) } },
                Modifier.weight(1f),
                enabled = reason != null && !busy,
            )
        }
    }
}

/** Proposer un appel (maquette 19) : des créneaux où vous êtes réveillés tous les deux, dans les deux fuseaux. */
@Composable
internal fun CallSheet(
    name: String,
    now: Instant,
    mine: TimeZone,
    theirs: TimeZone,
    onSend: (Instant) -> Unit,
    onCancel: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    val all = remember(now.epochSeconds / 60) { callSlots(now, mine, theirs, count = 9) }
    val slots = all.drop(page * 3).take(3).ifEmpty { all.take(3) }
    var choice by remember(page) { mutableStateOf(slots.firstOrNull()) }
    KikoSheet("Proposer un appel", SheetModifier.verticalScroll(rememberScrollState()), counter = "15 min") {
        KikoMascotNote("Voici des moments où vous êtes réveillés tous les deux. $name choisira de dire oui.")
        if (slots.isEmpty()) {
            Text("Aucun créneau commun cette semaine.", style = Kiko.type.caption, color = Kiko.colors.mid)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slots.forEach { at ->
                KikoSlotOption(
                    title = slotTitle(at, now, mine),
                    detail = "${clockTime(at, theirs)} chez $name",
                    selected = at == choice,
                    onClick = { choice = at },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KikoButton(
                "Autre heure",
                { page = if ((page + 1) * 3 < all.size) page + 1 else 0 },
                Modifier.weight(1f),
                style = KikoButtonStyle.White,
                enabled = all.size > 3,
            )
            KikoButton("Envoyer", { choice?.let(onSend) }, Modifier.weight(1f), enabled = choice != null)
        }
        KikoLinkButton("Annuler", onCancel, Modifier.align(Alignment.CenterHorizontally), color = Kiko.colors.mid)
    }
}

private val CorrectionTags = listOf(
    CorrectionPayload.TagGrammar to "Grammaire",
    CorrectionPayload.TagSpelling to "Orthographe",
    CorrectionPayload.TagNatural to "Plus naturel",
)

/**
 * Corriger au toucher (maquette 18) : on touche un mot pour le remplacer, ou on réécrit la phrase.
 * Pas de points à gagner : juste aider l'autre.
 */
@Composable
internal fun CorrectSheet(message: ChatMessage, name: String, onSend: (String, String, List<String>) -> Unit, onCancel: () -> Unit) {
    val c = Kiko.colors
    val original = message.body
    val tokens = remember(original) { words(original) }
    val replacements = remember(message.id) { mutableStateMapOf<Int, String>() }
    var open by remember(message.id) { mutableStateOf<Int?>(null) }
    var rewrite by remember(message.id) { mutableStateOf<String?>(null) }
    var note by remember(message.id) { mutableStateOf("") }
    val tags = remember(message.id) { mutableStateMapOf<String, Boolean>() }

    val corrected = rewrite ?: tokens.indices.reversed().fold(original) { text, i ->
        val r = replacements[i]
        if (r == null || r == tokens[i].first) text else replaceWord(text, i, r)
    }.replace(Regex(" {2,}"), " ").trim()
    val count = if (rewrite != null) changedRanges(original, corrected).size else replacements.count { (i, r) -> r != tokens.getOrNull(i)?.first }

    KikoSheet("Corriger le message", SheetModifier.verticalScroll(rememberScrollState())) {
        Text(
            if (rewrite == null) "Touche un mot pour le changer." else "Réécris la phrase comme tu la dirais.",
            style = Kiko.type.caption,
            color = c.mid,
        )
        Column(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(17.dp)).border(2.dp, c.hair, RoundedCornerShape(17.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                KikoAvatar(name, size = 20.dp)
                Text("$name a écrit", style = Kiko.type.micro, color = c.mid)
            }
            if (rewrite == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tokens.forEachIndexed { i, (word, _) ->
                        KikoWordChip(word, onClick = { open = if (open == i) null else i }, replacement = replacements[i], selected = open == i)
                    }
                }
            } else {
                Text(original, style = Kiko.type.caption.copy(textDecoration = TextDecoration.LineThrough), color = c.error)
            }
        }

        val index = open
        if (rewrite == null && index != null && index in tokens.indices) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tokens[index].first, style = Kiko.type.bodyStrong.copy(textDecoration = TextDecoration.LineThrough), color = c.error)
                KikoTextField(
                    replacements[index] ?: tokens[index].first,
                    { replacements[index] = it.take(80) },
                    Modifier.weight(1f),
                    placeholder = "Rien (supprimer le mot)",
                )
            }
        }
        if (rewrite != null) {
            KikoTextField(rewrite.orEmpty(), { rewrite = it.take(4000) }, singleLine = false)
        }
        KikoLinkButton(
            if (rewrite == null) "Réécrire toute la phrase" else "Revenir aux mots",
            {
                rewrite = if (rewrite == null) corrected else null
                open = null
            },
            color = c.primaryDark,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CorrectionTags.forEach { (tag, label) ->
                KikoChip(label, selected = tags[tag] == true, onClick = { tags[tag] = tags[tag] != true })
            }
        }
        KikoTextField(note, { note = it.take(280) }, placeholder = "Un petit mot d'explication (facultatif)", singleLine = false)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KikoButton("Annuler", onCancel, Modifier.weight(1f), style = KikoButtonStyle.White)
            KikoButton(
                when (count) {
                    0 -> "Envoyer"
                    1 -> "Envoyer 1 correction"
                    else -> "Envoyer $count corrections"
                },
                { onSend(corrected, note, tags.filterValues { it }.keys.toList()) },
                Modifier.weight(1.6f),
                enabled = count > 0 && corrected.isNotBlank() && corrected != original,
            )
        }
    }
}
