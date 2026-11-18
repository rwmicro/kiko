package app.kiko.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kiko.data.Partner
import app.kiko.data.PartnerService
import app.kiko.onboarding.HomeScreen
import app.kiko.partners.PartnersTab
import app.kiko.ui.components.KikoBottomBar
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoEmptyState
import app.kiko.ui.components.KikoMainTabs
import app.kiko.ui.components.KikoScreenTitle

/** Index des onglets, dans l'ordre de [KikoMainTabs]. */
private const val TabPartners = 0
private const val TabChats = 1
private const val TabFeed = 2
private const val TabChallenges = 3
private const val TabMe = 4

/** Hauteur réservée sous le contenu pour la barre d'onglets flottante. */
private val BarSpace = 100.dp

/** L'app une fois connecté : cinq onglets, en commençant par le Fil et ses premiers pas. */
@Composable
fun MainScreen(firstName: String, partners: PartnerService, onSignOut: () -> Unit, startOnPartners: Boolean = false) {
    var tab by remember { mutableIntStateOf(if (startOnPartners) TabPartners else TabFeed) }
    var openedPartner by remember { mutableStateOf<Partner?>(null) }

    LaunchedEffect(Unit) { partners.markActive() }

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            TabPartners -> PartnersTab(
                service = partners,
                opened = openedPartner,
                onOpen = { openedPartner = it },
                contentPadding = PaddingValues(bottom = BarSpace),
                bottomBar = { Box(Modifier.fillMaxSize()) { Bar(tab) { tab = it; openedPartner = null } } },
            )
            TabChats -> Placeholder(
                title = "Pas encore de discussion",
                message = "Tes saluts envoyés et les demandes que tu reçois arriveront ici. Le chat arrive à la prochaine étape du prototype.",
                action = "Trouver un partenaire",
                onAction = { tab = TabPartners },
            )
            TabFeed -> HomeScreen(firstName = firstName, onGreet = { tab = TabPartners }, bottomPadding = BarSpace)
            TabChallenges -> Placeholder(
                title = "Les défis arrivent bientôt",
                message = "La série à deux et les quêtes se débloqueront avec tes premiers partenaires.",
            )
            TabMe -> MeTab(firstName, onSignOut)
        }

        if (tab != TabPartners) Bar(tab) { tab = it; openedPartner = null }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Bar(selected: Int, onSelect: (Int) -> Unit) {
    KikoBottomBar(
        tabs = KikoMainTabs,
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
    )
}

@Composable
private fun Placeholder(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = BarSpace), contentAlignment = Alignment.Center) {
        KikoEmptyState(title = title, message = message, action = action, onAction = onAction)
    }
}

@Composable
private fun MeTab(firstName: String, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = BarSpace),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        KikoScreenTitle(firstName.trim(), subtitle = "Ton niveau, ton carnet et tes réglages arriveront ici.")
        KikoButton("Me déconnecter", onSignOut, Modifier.fillMaxWidth(), style = KikoButtonStyle.White)
    }
}
