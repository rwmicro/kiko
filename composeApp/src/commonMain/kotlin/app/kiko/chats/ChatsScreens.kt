@file:OptIn(ExperimentalTime::class)

package app.kiko.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kiko.data.ChatMessage
import app.kiko.data.Conversation
import app.kiko.partners.localClock
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoCard
import app.kiko.ui.components.KikoChatHeader
import app.kiko.ui.components.KikoChatInput
import app.kiko.ui.components.KikoConversationRow
import app.kiko.ui.components.KikoCountBadge
import app.kiko.ui.components.KikoDayLabel
import app.kiko.ui.components.KikoDivider
import app.kiko.ui.components.KikoEmptyState
import app.kiko.ui.components.KikoFilterChips
import app.kiko.ui.components.KikoIcons
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoMascotNote
import app.kiko.ui.components.KikoMessageBubble
import app.kiko.ui.components.KikoSearchField
import app.kiko.ui.theme.Kiko
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/** Onglet Discussions : la liste, ou la conversation ouverte. */
@Composable
fun ChatsTab(
    store: ChatStore,
    contentPadding: PaddingValues,
    onFindPartners: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        val open = store.open
        if (store.openId != null && open != null) {
            ChatScreen(store, open)
        } else {
            ChatList(store, contentPadding, onFindPartners)
            bottomBar()
        }
    }
}

@Composable
private fun ChatList(store: ChatStore, contentPadding: PaddingValues, onFindPartners: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ChatFilter.All) }
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = Clock.System.now()
    val all = store.conversations
    val requests = all.count { it.isIncomingRequest }
    val unread = all.count { it.unread > 0 }

    fun labelOf(f: ChatFilter) = when (f) {
        ChatFilter.All -> f.label
        ChatFilter.Unread -> if (unread > 0) "${f.label} $unread" else f.label
        ChatFilter.Requests -> if (requests > 0) "${f.label} $requests" else f.label
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        Text("Discussions", style = Kiko.type.title, color = Kiko.colors.ink, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
        KikoSearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Rechercher")
        KikoFilterChips(
            options = ChatFilter.entries.map(::labelOf),
            selected = labelOf(filter),
            onSelect = { label -> filter = ChatFilter.entries.first { labelOf(it) == label } },
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        when {
            store.loading && all.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text("Chargement des discussions…", style = Kiko.type.body, color = Kiko.colors.mid)
            }
            store.error != null && all.isEmpty() -> KikoEmptyState(
                title = "Impossible de charger les discussions",
                message = store.error.orEmpty(),
                action = "Réessayer",
                onAction = store::reload,
            )
            all.isEmpty() -> KikoEmptyState(
                title = "Pas encore de discussion",
                message = "Salue un partenaire : ta demande et sa réponse arriveront ici.",
                action = "Trouver un partenaire",
                onAction = onFindPartners,
            )
            else -> {
                val shown = all.filtered(filter, query)
                LazyColumn(Modifier.weight(1f), contentPadding = contentPadding) {
                    if (filter == ChatFilter.All && requests > 0 && query.isBlank()) {
                        item(key = "requests") { RequestsRow(requests) { filter = ChatFilter.Requests } }
                    }
                    items(shown, key = { it.id }) { conv ->
                        KikoConversationRow(
                            name = conv.partnerName,
                            preview = conv.preview(),
                            time = listTime(conv.lastActivity, now, zone),
                            unread = conv.unread,
                            verified = conv.partnerVerified,
                            online = conv.partnerLastSeen?.let { now - it < 5.minutes } ?: false,
                            onClick = { store.openConversation(conv.id) },
                        )
                    }
                    if (shown.isEmpty()) {
                        item { KikoEmptyState(title = "Rien ici", message = "Aucune discussion ne correspond.") }
                    }
                }
            }
        }
    }
}

/** Ligne « Demandes » en tête de liste (maquette 14) : les saluts reçus en attente. */
@Composable
private fun RequestsRow(count: Int, onClick: () -> Unit) {
    val c = Kiko.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(KikoIcons.Wave, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp).padding(start = 2.dp))
            Text("Demandes", style = Kiko.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            KikoCountBadge(count, color = c.primary)
        }
        KikoDivider()
    }
}

/** Une conversation : demande à accepter, demande en attente, ou chat ouvert. */
@Composable
private fun ChatScreen(store: ChatStore, conv: Conversation) {
    val c = Kiko.colors
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = Clock.System.now()
    val online = conv.partnerLastSeen?.let { now - it < 5.minutes } ?: false
    val theirClock = localClock(conv.partnerTimeZone, now)
    var draft by remember(conv.id) { mutableStateOf("") }
    var confirmBlock by remember(conv.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val groups = groupByDay(store.messages, zone)
    val today = now.toLocalDateTime(zone).date
    val readUpTo = store.partnerReadUpTo

    // Rester en bas de la conversation quand un message arrive.
    val itemCount = groups.sumOf { it.second.size + 1 } + 1
    LaunchedEffect(store.messages.size) {
        if (store.messages.isNotEmpty()) listState.animateScrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        KikoChatHeader(
            name = conv.partnerName,
            status = listOfNotNull(
                if (online) "En ligne" else null,
                theirClock?.let { "${it.time} chez ${conv.partnerName}" },
            ).joinToString(" · ").ifEmpty { " " },
            verified = conv.partnerVerified,
            online = online,
            onBack = store::close,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "intro") {
                if (conv.isIncomingRequest) {
                    KikoCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("${conv.partnerName} veut discuter avec toi", style = Kiko.type.bodyStrong, color = c.ink)
                        Text(
                            if (conv.partnerVerified) "Profil vérifié par selfie. Vous n'avez pas encore échangé." else "Vous n'avez pas encore échangé.",
                            style = Kiko.type.micro,
                            color = c.mid,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            groups.forEach { (day, messages) ->
                item(key = "day-$day") { KikoDayLabel(dayLabel(day, today), Modifier.padding(vertical = 6.dp)) }
                items(messages, key = { it.id }) { m -> Bubble(store, m, zone, readUpTo) }
            }
        }

        store.chatError?.let {
            Text(it, style = Kiko.type.captionStrong, color = c.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        when {
            conv.isIncomingRequest -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KikoMascotNote("Tant que tu n'as pas accepté, ${conv.partnerName} ne voit pas si tu as lu ses messages.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KikoButton("Refuser", { store.respond(false) }, Modifier.weight(1f), style = KikoButtonStyle.White, enabled = !store.busy)
                    KikoButton("Accepter", { store.respond(true) }, Modifier.weight(1f), enabled = !store.busy)
                }
                KikoLinkButton(
                    if (confirmBlock) "Confirmer : bloquer ${conv.partnerName}" else "Bloquer",
                    { if (confirmBlock) store.block() else confirmBlock = true },
                    Modifier.align(Alignment.CenterHorizontally),
                    color = c.error,
                )
            }
            conv.isOutgoingRequest -> KikoMascotNote(
                "Ta demande attend la réponse de ${conv.partnerName}. Tu pourras écrire dès qu'elle sera acceptée.",
                Modifier.padding(16.dp),
            )
            else -> KikoChatInput(
                value = draft,
                onValueChange = { draft = it.take(4000) },
                onSend = {
                    store.send(draft)
                    draft = ""
                },
                placeholder = "Écris…",
            )
        }
    }
}

@Composable
private fun Bubble(store: ChatStore, m: ChatMessage, zone: TimeZone, readUpTo: kotlin.time.Instant?) {
    val mine = m.senderId == store.myId
    val time = when {
        m.failed -> "Non envoyé · toucher pour réessayer"
        m.pending -> "Envoi…"
        else -> clockTime(m.createdAt, zone)
    }
    KikoMessageBubble(
        text = m.body,
        outgoing = mine,
        time = time,
        read = mine && !m.pending && readUpTo != null && m.createdAt <= readUpTo,
        modifier = if (m.failed) Modifier.clickable(role = Role.Button) { store.retry(m) } else Modifier,
    )
}
