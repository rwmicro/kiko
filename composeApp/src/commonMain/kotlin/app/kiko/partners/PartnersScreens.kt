@file:OptIn(ExperimentalTime::class)

package app.kiko.partners

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kiko.Languages
import app.kiko.data.AccountException
import app.kiko.data.Partner
import app.kiko.data.PartnerService
import app.kiko.ui.components.KikoAvatar
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonSize
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoCard
import app.kiko.ui.components.KikoEmptyState
import app.kiko.ui.components.KikoFilterChips
import app.kiko.ui.components.KikoFlag
import app.kiko.ui.components.KikoIconButton
import app.kiko.ui.components.KikoIcons
import app.kiko.ui.components.KikoLevelBadge
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoMascotNote
import app.kiko.ui.components.KikoNativeBadge
import app.kiko.ui.components.KikoPartnerRow
import app.kiko.ui.components.KikoSearchField
import app.kiko.ui.components.KikoSectionLabel
import app.kiko.ui.components.KikoSheet
import app.kiko.ui.components.KikoTag
import app.kiko.ui.components.KikoTagData
import app.kiko.ui.components.KikoTagStyle
import app.kiko.ui.components.KikoTextField
import app.kiko.ui.components.KikoToast
import app.kiko.ui.components.KikoVerifiedBadge
import app.kiko.ui.theme.Kiko
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private sealed interface PartnersLoad {
    data object Loading : PartnersLoad
    data class Failed(val message: String) : PartnersLoad
    data class Ready(val partners: List<Partner>) : PartnersLoad
}

/**
 * Onglet Partenaires : la liste, la fiche d'un partenaire et la feuille « Saluer ».
 * [opened] est tenu par l'appelant pour qu'il cache la barre d'onglets sur la fiche.
 */
@Composable
fun PartnersTab(
    service: PartnerService,
    opened: Partner?,
    onOpen: (Partner?) -> Unit,
    contentPadding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf<PartnersLoad>(PartnersLoad.Loading) }
    var greeting by remember { mutableStateOf<Partner?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        load = PartnersLoad.Loading
        load = try {
            PartnersLoad.Ready(service.suggestions())
        } catch (e: AccountException) {
            PartnersLoad.Failed(e.message ?: "Erreur inconnue")
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (opened != null) {
            PartnerProfile(opened, onBack = { onOpen(null) }, onGreet = { greeting = opened })
        } else {
            PartnerList(
                load = load,
                contentPadding = contentPadding,
                onRetry = { reload++ },
                onOpen = { onOpen(it) },
                onGreet = { greeting = it },
            )
        }

        toast?.let {
            KikoToast(it, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 12.dp))
        }

        greeting?.let { partner ->
            GreetSheet(
                partner = partner,
                onDismiss = { greeting = null },
                onSend = { text ->
                    service.greet(partner.id, text)
                    greeting = null
                    onOpen(null)
                    toast = "Salut envoyé à ${partner.firstName}. Tu le retrouves dans Discussions."
                    val current = load
                    if (current is PartnersLoad.Ready) load = PartnersLoad.Ready(current.partners.filterNot { it.id == partner.id })
                },
                scopeLaunch = { block -> scope.launch { block() } },
            )
        }
    }
}

@Composable
private fun PartnerList(
    load: PartnersLoad,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onOpen: (Partner) -> Unit,
    onGreet: (Partner) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PartnerFilter.All) }
    val now = Clock.System.now()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        Text("Partenaires", style = Kiko.type.title, color = Kiko.colors.ink, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
        KikoSearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Rechercher un prénom, une ville, un intérêt")
        KikoFilterChips(
            options = PartnerFilter.entries.map { it.label },
            selected = filter.label,
            onSelect = { label -> filter = PartnerFilter.entries.first { it.label == label } },
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        when (load) {
            PartnersLoad.Loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text("Lézo cherche tes partenaires…", style = Kiko.type.body, color = Kiko.colors.mid)
            }
            is PartnersLoad.Failed -> KikoEmptyState(
                title = "Impossible de charger les partenaires",
                message = load.message,
                action = "Réessayer",
                onAction = onRetry,
            )
            is PartnersLoad.Ready -> {
                val shown = load.partners.filtered(filter, query, now)
                if (shown.isEmpty()) {
                    if (load.partners.isEmpty()) {
                        KikoEmptyState(
                            title = "Personne pour l'instant",
                            message = "Les natifs de la langue que tu apprends, qui apprennent la tienne, apparaîtront ici. La bêta vient d'ouvrir : invite un ami de l'autre langue !",
                            action = "Actualiser",
                            onAction = onRetry,
                        )
                    } else {
                        KikoEmptyState(title = "Aucun résultat", message = "Essaie une autre recherche ou un autre filtre.")
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = contentPadding) {
                        items(shown, key = { it.id }) { partner ->
                            val clock = localClock(partner.timeZone, now)
                            KikoPartnerRow(
                                name = partner.firstName,
                                age = partner.age,
                                meta = partner.metaLine(),
                                verified = partner.verified,
                                online = partner.isOnline(now),
                                flag = partner.nativeLanguage,
                                localTime = clock?.time,
                                timeOfDay = clock?.moment ?: app.kiko.ui.components.KikoTimeOfDay.Day,
                                tags = partner.commonInterests.take(2).map { KikoTagData(it, KikoTagStyle.Ok) },
                                onClick = { onOpen(partner) },
                                onWave = { onGreet(partner) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fiche d'un partenaire : photo, langues, intérêts, et le bouton Saluer. */
@Composable
private fun PartnerProfile(partner: Partner, onBack: () -> Unit, onGreet: () -> Unit) {
    val c = Kiko.colors
    val now = Clock.System.now()
    val clock = localClock(partner.timeZone, now)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(c.primaryLight).safeDrawingPadding().height(96.dp).padding(12.dp)) {
            KikoIconButton(KikoIcons.Back, "Retour", onBack)
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().offset(y = (-40).dp).padding(bottom = 0.dp), verticalAlignment = Alignment.Bottom) {
                KikoAvatar(partner.firstName, size = 96.dp, online = partner.isOnline(now), ring = true)
                Spacer(Modifier.weight(1f))
                KikoButton("Saluer", onGreet, style = KikoButtonStyle.Sun, size = KikoButtonSize.Small, icon = KikoIcons.Wave)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${partner.firstName}, ${partner.age}", style = Kiko.type.titleSmall, color = c.ink)
                    if (partner.verified) KikoVerifiedBadge()
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KikoFlag(partner.nativeLanguage)
                    val place = listOfNotNull(
                        partner.city,
                        clock?.let { "${it.time} chez ${partner.firstName}, ${it.momentLabel}" },
                        if (partner.isOnline(now)) "en ligne" else null,
                    ).joinToString(" · ")
                    Text(place, style = Kiko.type.caption, color = c.mid)
                }
            }

            KikoSectionLabel("Langues")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguagePill(partner.nativeLanguage) { KikoNativeBadge() }
                LanguagePill(partner.learningLanguage) { partner.learningLevel?.let { KikoLevelBadge(it) } }
            }

            if (partner.interests.isNotEmpty()) {
                KikoSectionLabel("Centres d'intérêt")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    partner.interests.forEach { interest ->
                        KikoTag(
                            if (interest in partner.commonInterests) "$interest · en commun" else interest,
                            style = if (interest in partner.commonInterests) KikoTagStyle.Ok else KikoTagStyle.Neutral,
                        )
                    }
                }
            }

            KikoMascotNote(
                "Pas d'appel tant que vous n'êtes pas partenaires. Commence par un salut : ${partner.firstName} choisira d'accepter ou non.",
                Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun LanguagePill(code: String, badge: @Composable () -> Unit) {
    val c = Kiko.colors
    Row(
        Modifier.background(c.surface, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KikoFlag(code)
        Text(Languages.byCode(code)?.nameFr ?: code.uppercase(), style = Kiko.type.captionStrong, color = c.ink)
        badge()
    }
}

/** Feuille « Saluer » : Lézo propose une idée dans la langue du partenaire, ou on écrit soi-même. */
@Composable
private fun GreetSheet(
    partner: Partner,
    onDismiss: () -> Unit,
    onSend: suspend (String) -> Unit,
    scopeLaunch: (suspend () -> Unit) -> Unit,
) {
    val ideas = remember(partner.id) { icebreakersFor(partner.commonInterests) }
    var index by remember(partner.id) { mutableIntStateOf(0) }
    var custom by remember(partner.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val idea = ideas[index % ideas.size]
    val message = custom ?: idea.textIn(partner.nativeLanguage)

    fun send() {
        if (busy || message.isBlank()) return
        busy = true
        error = null
        scopeLaunch {
            try {
                onSend(message)
            } catch (e: AccountException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!busy) onDismiss() },
        )
        KikoSheet(
            "Saluer ${partner.firstName}",
            Modifier.align(Alignment.BottomCenter).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            if (custom == null) {
                val topic = idea.topic
                KikoMascotNote(
                    if (topic != null) "Vous aimez tous les deux : ${topic.lowercase()}. Une idée pour commencer :"
                    else "Une idée pour commencer :",
                )
                KikoCard(Modifier.fillMaxWidth()) {
                    if (topic != null) KikoTag("$topic · en commun", style = KikoTagStyle.Ok)
                    Text(
                        idea.textIn(partner.nativeLanguage),
                        style = Kiko.type.name.copy(fontWeight = FontWeight.ExtraBold),
                        color = Kiko.colors.ink,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (partner.learningLanguage != partner.nativeLanguage) {
                        Text(idea.textIn(partner.learningLanguage), style = Kiko.type.caption, color = Kiko.colors.mid, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KikoButton("Autre idée", { index++ }, Modifier.weight(1f), style = KikoButtonStyle.White, enabled = !busy)
                    KikoButton(if (busy) "Envoi…" else "Envoyer", ::send, Modifier.weight(1f), enabled = !busy)
                }
                KikoLinkButton("Écrire mon propre message", { custom = idea.textIn(partner.nativeLanguage) }, Modifier.align(Alignment.CenterHorizontally))
            } else {
                val language = Languages.byCode(partner.nativeLanguage)?.nameFr?.lowercase() ?: "sa langue"
                KikoTextField(
                    value = custom.orEmpty(),
                    onValueChange = { custom = it.take(500) },
                    label = "Ton message, en $language si tu peux",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                KikoButton(if (busy) "Envoi…" else "Envoyer", ::send, Modifier.fillMaxWidth(), enabled = !busy && message.isNotBlank())
                KikoLinkButton("Revenir aux idées de Lézo", { custom = null }, Modifier.align(Alignment.CenterHorizontally))
            }
            error?.let { Text(it, style = Kiko.type.captionStrong, color = Kiko.colors.error) }
        }
    }
}
