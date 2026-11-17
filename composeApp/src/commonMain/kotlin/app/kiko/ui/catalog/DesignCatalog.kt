package app.kiko.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kiko.ui.components.KikoAvatar
import app.kiko.ui.components.KikoAvatarPair
import app.kiko.ui.components.KikoAvatarShape
import app.kiko.ui.components.KikoBottomBar
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonSize
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoCard
import app.kiko.ui.components.KikoChatHeader
import app.kiko.ui.components.KikoChatInput
import app.kiko.ui.components.KikoChatRequestCard
import app.kiko.ui.components.KikoCheckRow
import app.kiko.ui.components.KikoChip
import app.kiko.ui.components.KikoConversationRow
import app.kiko.ui.components.KikoCorrectionCard
import app.kiko.ui.components.KikoDayLabel
import app.kiko.ui.components.KikoEmptyState
import app.kiko.ui.components.KikoFab
import app.kiko.ui.components.KikoFilterChips
import app.kiko.ui.components.KikoIconButton
import app.kiko.ui.components.KikoIcons
import app.kiko.ui.components.KikoLanguageProgress
import app.kiko.ui.components.KikoLevelBadge
import app.kiko.ui.components.KikoLevelPicker
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoMainTabs
import app.kiko.ui.components.KikoMenuRow
import app.kiko.ui.components.KikoMessageBubble
import app.kiko.ui.components.KikoNativeBadge
import app.kiko.ui.components.KikoNotificationRow
import app.kiko.ui.components.KikoOption
import app.kiko.ui.components.KikoPartnerRow
import app.kiko.ui.components.KikoPill
import app.kiko.ui.components.KikoPillStyle
import app.kiko.ui.components.KikoPostCard
import app.kiko.ui.components.KikoQuestionOfDay
import app.kiko.ui.components.KikoRadioOption
import app.kiko.ui.components.KikoSearchField
import app.kiko.ui.components.KikoSectionLabel
import app.kiko.ui.components.KikoSegmentedControl
import app.kiko.ui.components.KikoSheet
import app.kiko.ui.components.KikoSlotOption
import app.kiko.ui.components.KikoStepHeader
import app.kiko.ui.components.KikoStreakRow
import app.kiko.ui.components.KikoTag
import app.kiko.ui.components.KikoTagData
import app.kiko.ui.components.KikoTagStyle
import app.kiko.ui.components.KikoTextField
import app.kiko.ui.components.KikoTip
import app.kiko.ui.components.KikoToast
import app.kiko.ui.components.KikoToggleRow
import app.kiko.ui.components.KikoTopTabs
import app.kiko.ui.components.KikoTypingIndicator
import app.kiko.ui.components.KikoVerifiedBadge
import app.kiko.ui.components.KikoWeekStreak
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoTheme
import app.kiko.ui.theme.KikoType

/**
 * Catalogue du design system : chaque composant avec des données d'exemple,
 * en clair ou en sombre. Sert de référence visuelle, pas d'écran de l'app.
 */
@Composable
fun DesignCatalog(onClose: () -> Unit, startDark: Boolean = false) {
    var dark by remember { mutableStateOf(startDark) }
    KikoTheme(darkTheme = dark) {
        val c = Kiko.colors
        Column(Modifier.fillMaxSize().background(c.paper).safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KikoLinkButton("← Retour", onClose)
                Spacer(Modifier.weight(1f))
                KikoToggleRow("Sombre", checked = dark, onCheckedChange = { dark = it }, modifier = Modifier.width(120.dp))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Design system", style = KikoType.display, color = c.ink)
                    Text("Les composants des maquettes, prêts à être appelés depuis les écrans.", style = KikoType.body, color = c.mid)
                }
                ColorsSection()
                TypeSection()
                ButtonsSection()
                FieldsSection()
                ChoicesSection()
                BadgesSection()
                ListsSection()
                ChatSection()
                ProgressSection()
                NavigationSection()
                FeedbackSection()
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KikoSectionLabel(title)
        content()
    }
}

@Composable
private fun ColorsSection() = Section("Couleurs") {
    val c = Kiko.colors
    val swatches = listOf(
        "paper" to c.paper, "tint" to c.tint, "hair" to c.hair, "surface" to c.surface,
        "ink" to c.ink, "mid" to c.mid, "soft" to c.soft, "primary" to c.primary,
        "primaryLight" to c.primaryLight, "apricot" to c.apricot, "sun" to c.sun, "sunLight" to c.sunLight,
        "ok" to c.ok, "okLight" to c.okLight, "error" to c.error, "errorLight" to c.errorLight,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches.forEach { (name, color) -> Swatch(name, color) }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(44.dp).background(color, RoundedCornerShape(12.dp)).border(1.dp, Kiko.colors.hair, RoundedCornerShape(12.dp)))
        Text(name, style = KikoType.micro, color = Kiko.colors.mid)
    }
}

@Composable
private fun TypeSection() = Section("Typographie") {
    val c = Kiko.colors
    Text("Parle avec le monde", style = KikoType.display, color = c.ink)
    Text("Titre d'écran", style = KikoType.title, color = c.ink)
    Text("Titre de carte", style = KikoType.titleSmall, color = c.ink)
    Text("Ayu, 24", style = KikoType.name, color = c.ink)
    Text("Texte courant : tu apprends sa langue, elle apprend la tienne.", style = KikoType.body, color = c.ink)
    Text("Légende : Bandung · 14:32 chez elle", style = KikoType.caption, color = c.mid)
    Text("ÉTIQUETTE DE SECTION", style = KikoType.label, color = c.soft)
}

@Composable
private fun ButtonsSection() = Section("Boutons") {
    KikoButton("Continuer", {}, Modifier.fillMaxWidth())
    KikoButton("J'ai déjà un compte", {}, Modifier.fillMaxWidth(), style = KikoButtonStyle.White)
    KikoButton("Voir le profil", {}, Modifier.fillMaxWidth(), style = KikoButtonStyle.Soft)
    KikoButton("Relever le défi", {}, Modifier.fillMaxWidth(), style = KikoButtonStyle.Sun, icon = KikoIcons.Flame)
    KikoButton("Désactivé", {}, Modifier.fillMaxWidth(), enabled = false)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        KikoButton("Accepter", {}, size = KikoButtonSize.Small)
        KikoButton("Refuser", {}, size = KikoButtonSize.Small, style = KikoButtonStyle.White)
        KikoIconButton(KikoIcons.Filter, "Filtres", {})
        KikoFab(KikoIcons.Plus, "Publier", {})
    }
    KikoLinkButton("Lien d'action", {})
}

@Composable
private fun FieldsSection() = Section("Champs") {
    var name by remember { mutableStateOf("") }
    var mail by remember { mutableStateOf("ayu@") }
    var search by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(true) }
    var notif by remember { mutableStateOf(true) }
    KikoTextField(name, { name = it }, label = "Prénom", placeholder = "Ton prénom", helper = "Visible sur ton profil")
    KikoTextField(mail, { mail = it }, label = "E-mail", error = "Cette adresse n'est pas complète")
    KikoSearchField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Rechercher une langue")
    KikoCheckRow(terms, { terms = it }, "J'accepte les conditions d'utilisation")
    KikoToggleRow("Notifications", notif, { notif = it }, detail = "Nouveaux messages et corrections")
}

@Composable
private fun ChoicesSection() = Section("Choix") {
    var interests by remember { mutableStateOf(setOf("Cuisine", "Voyage")) }
    var filter by remember { mutableStateOf("En ligne") }
    var lang by remember { mutableStateOf("id") }
    var level by remember { mutableStateOf("A2") }
    var pref by remember { mutableStateOf(1) }
    var seg by remember { mutableStateOf("Texte") }
    var slot by remember { mutableStateOf(0) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Cuisine", "Voyage", "Musique", "Cinéma", "Sport").forEach { i ->
            KikoChip(i, i in interests, { interests = if (i in interests) interests - i else interests + i })
        }
    }
    KikoFilterChips(listOf("En ligne", "Vérifiés", "Près de chez moi", "Même niveau"), filter, { filter = it })
    KikoOption("Indonésien", lang == "id", { lang = "id" }, Modifier.fillMaxWidth(), detail = "Bahasa Indonesia", leading = "ID")
    KikoOption("Français", lang == "fr", { lang = "fr" }, Modifier.fillMaxWidth(), detail = "Ta langue natale", leading = "FR", enabled = false)
    KikoLevelPicker(listOf("A1", "A2", "B1", "B2", "C1"), level, { level = it })
    listOf("Toutes mes erreurs", "Seulement les importantes", "Aucune").forEachIndexed { i, label ->
        KikoRadioOption(label, pref == i, { pref = i })
    }
    KikoSegmentedControl(listOf("Texte", "Photo", "Vocal"), seg, { seg = it })
    KikoSlotOption("Jeu. 19:00", "13:00 à Bandung", slot == 0, { slot = 0 })
    KikoSlotOption("Sam. 10:30", "16:30 à Bandung", slot == 1, { slot = 1 })
}

@Composable
private fun BadgesSection() = Section("Avatars et badges") {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        KikoAvatar("Ayu", size = 60.dp, online = true)
        KikoAvatar("Budi", size = 60.dp, shape = KikoAvatarShape.Rounded)
        KikoAvatar("Sari", size = 60.dp, ring = true)
        KikoAvatarPair("Martin", "Ayu")
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KikoPill("12 jours", icon = KikoIcons.Flame)
        KikoPill("Nouveau", style = KikoPillStyle.Primary)
        KikoTag("Cuisine")
        KikoTag("3 points communs", style = KikoTagStyle.Ok)
        KikoTag("Répond vite", style = KikoTagStyle.Sun)
        KikoLevelBadge("B1")
        KikoNativeBadge()
        KikoVerifiedBadge()
    }
}

@Composable
private fun ListsSection() = Section("Listes") {
    Column {
        KikoPartnerRow(
            "Ayu", "Bandung · 14:32 chez elle · apprend le français A2", age = 24, verified = true, online = true,
            tags = listOf(KikoTagData("Cuisine", KikoTagStyle.Ok), KikoTagData("Répond vite", KikoTagStyle.Sun)),
            onWave = {},
        )
        KikoPartnerRow("Budi", "Yogyakarta · 14:32 chez lui · B1", age = 29, tags = listOf(KikoTagData("Musique")), onWave = {})
        KikoConversationRow("Sari", "Bonjour ! Kamu tinggal di mana ?", "12:04", unread = 2, online = true, verified = true)
        KikoConversationRow("Dewi", "Merci pour la correction", "Hier")
        KikoStreakRow("Ayu", "Série à deux", days = 9)
        KikoMenuRow("Langues", icon = KikoIcons.Feed, value = "FR · ID")
        KikoMenuRow("Réglages", icon = KikoIcons.Settings)
        KikoMenuRow("Supprimer mon compte", destructive = true)
    }
    KikoPostCard("Budi", "Fil public · il y a 2 h", "Aujourd'hui j'ai mangé un croissant pour la première fois, c'était très délicieux !", corrections = 2)
    KikoNotificationRow("Ayu t'a corrigé", "« Je suis allé » et pas « J'ai allé »", "2 min")
}

@Composable
private fun ChatSection() = Section("Discussion") {
    var draft by remember { mutableStateOf("") }
    KikoChatHeader("Ayu", "En ligne · 14:32 chez elle", verified = true, online = true, onCall = {})
    Column(
        Modifier.fillMaxWidth().background(Kiko.colors.tint, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KikoDayLabel("Aujourd'hui")
        KikoMessageBubble("Halo Martin ! Kamu sudah makan?", outgoing = false, time = "12:01")
        KikoMessageBubble("Oui, j'ai allé au marché ce matin", outgoing = true, time = "12:03", read = true)
        KikoCorrectionCard(
            author = "Ayu",
            original = "J'ai allé au marché",
            corrected = "Je suis allé au marché",
            highlight = "Je suis allé",
            explanation = "Aller se conjugue avec être au passé composé.",
            important = true,
            onThanks = {},
            onSave = {},
        )
        KikoTypingIndicator("Ayu")
    }
    KikoChatInput(draft, { draft = it }, onSend = { draft = "" }, onVoice = {})
    KikoChatRequestCard("Dewi", "Halo ! Je cherche quelqu'un pour pratiquer le français le soir.", onAccept = {}, onDecline = {}, verified = true)
}

@Composable
private fun ProgressSection() = Section("Progression et défis") {
    KikoStepHeader(current = 3, total = 6, onBack = {})
    KikoWeekStreak(doneDays = 4)
    KikoLanguageProgress("Indonésien", "A2", 0.62f, nextStep = "B1 dans 38 échanges")
    KikoQuestionOfDay("Quel plat de ton pays ferais-tu goûter à ton partenaire ?", onAnswer = {})
    KikoCard {
        Text("Carte", style = KikoType.titleSmall, color = Kiko.colors.ink)
        Text("Conteneur blanc en relief, pour une mission, une demande ou un encart.", style = KikoType.caption, color = Kiko.colors.mid)
    }
}

@Composable
private fun NavigationSection() = Section("Navigation") {
    var tab by remember { mutableStateOf("Toutes") }
    var main by remember { mutableStateOf(1) }
    KikoTopTabs(listOf("Toutes", "Non lues", "Corrections", "Séries", "Demandes"), tab, { tab = it })
    KikoBottomBar(
        KikoMainTabs.mapIndexed { i, t -> if (i == 1) t.copy(badge = 3) else t },
        selected = main,
        onSelect = { main = it },
    )
}

@Composable
private fun FeedbackSection() = Section("Retours") {
    KikoTip("Commence par une question sur sa ville, ça marche presque toujours.")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { KikoToast("Ajouté au carnet") }
    KikoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        KikoEmptyState("Ton carnet est vide", "Les corrections que tu gardes arriveront ici.", action = "Trouver un partenaire")
    }
    KikoSheet("Signaler Budi") {
        KikoRadioOption("Faux profil", true, {})
        KikoRadioOption("Propos déplacés", false, {})
        KikoButton("Envoyer le signalement", {}, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.size(8.dp))
}
