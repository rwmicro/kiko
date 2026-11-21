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
import app.kiko.data.ChatService
import app.kiko.chats.ChatStore
import app.kiko.chats.ChatsTab
import app.kiko.ui.components.KikoTab
import androidx.compose.runtime.rememberCoroutineScope
import app.kiko.onboarding.HomeScreen
import app.kiko.partners.PartnersTab
import app.kiko.ui.components.KikoBottomBar
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoEmptyState
import app.kiko.ui.components.KikoMainTabs
import app.kiko.ui.components.KikoScreenTitle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import app.kiko.notifications.AppVisibility
import app.kiko.notifications.DeepLinks
import app.kiko.notifications.NotificationController
import app.kiko.notifications.NotificationPrimer
import app.kiko.notifications.NotificationService
import app.kiko.notifications.NotificationSettings
import app.kiko.notifications.PushRegistry
import app.kiko.notifications.UpcomingCall
import app.kiko.calls.CallOverlay
import app.kiko.notifications.WidgetBridge
import app.kiko.notifications.rememberPushPermissionRequest
import app.kiko.notifications.widgetSnapshot
import app.kiko.chats.SheetOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Index des onglets, dans l'ordre de [KikoMainTabs]. */
private const val TabPartners = 0
private const val TabChats = 1
private const val TabFeed = 2
private const val TabChallenges = 3
private const val TabMe = 4

/** Hauteur réservée sous le contenu pour la barre d'onglets flottante. */
private val BarSpace = 100.dp

/** L'app une fois connecté : cinq onglets, en commençant par le Fil et ses premiers pas. */
@OptIn(ExperimentalTime::class)
@Composable
fun MainScreen(
    firstName: String,
    partners: PartnerService,
    chats: ChatService,
    notifications: NotificationService,
    onSignOut: () -> Unit,
    /** Supprime le compte côté serveur ; une erreur est montrée telle quelle dans la feuille. */
    onDeleteAccount: suspend () -> Unit,
    startOnPartners: Boolean = false,
) {
    var tab by remember { mutableIntStateOf(if (startOnPartners) TabPartners else TabFeed) }
    var openedPartner by remember { mutableStateOf<Partner?>(null) }
    val scope = rememberCoroutineScope()
    val chatStore = remember(chats) { ChatStore(chats, scope) }
    val alerts = remember(notifications) { NotificationController(notifications, scope) }
    val askSystem = rememberPushPermissionRequest(alerts::onSystemAnswer)
    var deleting by remember { mutableStateOf(false) }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { partners.markActive() }
    LaunchedEffect(chatStore) { chatStore.start() }
    LaunchedEffect(notifications) {
        PushRegistry.attach(notifications)
        alerts.load()
    }

    // Les widgets et les activités en direct suivent la liste des discussions.
    LaunchedEffect(chatStore, notifications) {
        var calls = emptyList<UpcomingCall>()
        snapshotFlow { chatStore.conversations }.collect { list ->
            if (chatStore.loading) return@collect
            calls = runCatching { notifications.upcomingCalls() }.getOrDefault(calls)
            WidgetBridge.publish(widgetSnapshot(list, calls, Clock.System.now()))
        }
    }

    // Les notifications de la discussion ouverte se taisent.
    LaunchedEffect(chatStore) {
        snapshotFlow { chatStore.openId }.collect { AppVisibility.openConversation = it }
    }

    // Une notification touchée ouvre sa discussion.
    val tap by DeepLinks.taps.collectAsState()
    LaunchedEffect(tap) {
        val t = tap ?: return@LaunchedEffect
        DeepLinks.consume()
        val conv = t.conversationId ?: return@LaunchedEffect
        tab = TabChats
        openedPartner = null
        if (chatStore.conversations.none { it.id == conv }) chatStore.refresh()
        if (chatStore.conversations.any { it.id == conv }) chatStore.openConversation(conv)
    }

    // Le compteur de l'onglet Discussions : messages non lus, demandes comprises.
    val tabs = KikoMainTabs.mapIndexed { i, t -> if (i == TabChats) t.copy(badge = chatStore.unreadTotal) else t }
    val selectTab: (Int) -> Unit = { tab = it; openedPartner = null }

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            TabPartners -> PartnersTab(
                service = partners,
                opened = openedPartner,
                onOpen = { openedPartner = it },
                contentPadding = PaddingValues(bottom = BarSpace),
                onGreeted = chatStore::reload,
                bottomBar = { Box(Modifier.fillMaxSize()) { Bar(tabs, tab, selectTab) } },
            )
            TabChats -> ChatsTab(
                store = chatStore,
                contentPadding = PaddingValues(bottom = BarSpace),
                onFindPartners = { tab = TabPartners },
                bottomBar = { Box(Modifier.fillMaxSize()) { Bar(tabs, tab, selectTab) } },
            )
            TabFeed -> HomeScreen(firstName = firstName, onGreet = { tab = TabPartners }, bottomPadding = BarSpace)
            TabChallenges -> Placeholder(
                title = "Les défis arrivent bientôt",
                message = "La série à deux et les quêtes se débloqueront avec tes premiers partenaires.",
            )
            TabMe -> MeTab(
                firstName,
                alerts,
                onEnableAlerts = askSystem,
                onSignOut = {
                    scope.launch {
                        runCatching { PushRegistry.signOut() }
                        WidgetBridge.clear()
                        onSignOut()
                    }
                },
                onDeleteAccount = { deleteError = null; deleting = true },
            )
        }

        if (tab != TabPartners && tab != TabChats) Bar(tabs, tab, selectTab)

        if (deleting) {
            SheetOverlay(onDismiss = { if (!deleteBusy) deleting = false }) {
                DeleteAccountSheet(
                    busy = deleteBusy,
                    error = deleteError,
                    onConfirm = {
                        scope.launch {
                            deleteBusy = true
                            try {
                                onDeleteAccount()
                                PushRegistry.detach()
                                WidgetBridge.clear()
                                deleting = false
                                onSignOut()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                deleteError = e.message ?: "La suppression n'a pas abouti. Réessaie."
                            } finally {
                                deleteBusy = false
                            }
                        }
                    },
                    onCancel = { deleting = false },
                )
            }
        }

        // Maquette 17 : la demande vient après le premier échange, avec le prénom de quelqu'un.
        val partner = chatStore.conversations.firstOrNull { it.status == app.kiko.data.ConversationStatus.Accepted }
        if (partner != null && chatStore.openId == null && alerts.shouldPrime(hasPartner = true)) {
            NotificationPrimer(
                partnerName = partner.partnerName,
                onEnable = askSystem,
                onLater = alerts::remindLater,
            )
        }

        // Un appel audio, entrant ou sortant, passe par-dessus tout le reste.
        CallOverlay()
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Bar(tabs: List<KikoTab>, selected: Int, onSelect: (Int) -> Unit) {
    KikoBottomBar(
        tabs = tabs,
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
private fun MeTab(
    firstName: String,
    alerts: NotificationController,
    onEnableAlerts: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    LaunchedEffect(alerts) { alerts.refreshPermission() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = BarSpace),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        KikoScreenTitle(firstName.trim(), subtitle = "Ton niveau et ton carnet arriveront ici.")
        NotificationSettings(alerts, onEnable = onEnableAlerts)
        KikoButton("Me déconnecter", onSignOut, Modifier.fillMaxWidth(), style = KikoButtonStyle.White)
        KikoButton("Supprimer mon compte", onDeleteAccount, Modifier.fillMaxWidth(), style = KikoButtonStyle.Soft)
    }
}
