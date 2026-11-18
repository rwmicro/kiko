@file:OptIn(ExperimentalTime::class, ExperimentalResourceApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.kiko.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kiko.data.ChatBackground
import app.kiko.data.ChatMessage
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import app.kiko.data.MessageKind
import app.kiko.data.call
import app.kiko.data.correction
import app.kiko.data.voice
import app.kiko.media.audioType
import app.kiko.media.prepareImage
import app.kiko.media.rememberVoicePlayer
import app.kiko.media.rememberVoiceRecorder
import app.kiko.partners.localClock
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoCallCard
import app.kiko.ui.components.KikoCard
import app.kiko.ui.components.KikoChatHeader
import app.kiko.ui.components.KikoChatInput
import app.kiko.ui.components.KikoConversationRow
import app.kiko.ui.components.KikoCorrectionCard
import app.kiko.ui.components.KikoCountBadge
import app.kiko.ui.components.KikoDayLabel
import app.kiko.ui.components.KikoDivider
import app.kiko.ui.components.KikoEmptyState
import app.kiko.ui.components.KikoFilterChips
import app.kiko.ui.components.KikoIcons
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoMascotNote
import app.kiko.ui.components.KikoMessageBubble
import app.kiko.ui.components.KikoPhotoBubble
import app.kiko.ui.components.KikoRecordingBar
import app.kiko.ui.components.KikoSearchField
import app.kiko.ui.components.KikoToast
import app.kiko.ui.components.KikoTypingIndicator
import app.kiko.ui.components.KikoVoiceBubble
import app.kiko.ui.theme.Kiko
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
            store.notice?.let {
                KikoToast(it, Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 110.dp).safeDrawingPadding())
            }
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
                            streak = if (conv.status == ConversationStatus.Accepted) conv.streak else 0,
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
    val partnerZone = remember(conv.partnerTimeZone) { runCatching { TimeZone.of(conv.partnerTimeZone) }.getOrNull() }
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Clock.System.now()
        }
    }
    val online = conv.partnerLastSeen?.let { now - it < 5.minutes } ?: false
    val theirClock = localClock(conv.partnerTimeZone, now)
    val period = SkyPeriod.at(partnerZone?.let { now.toLocalDateTime(it).hour } ?: 12)
    val accepted = conv.status == ConversationStatus.Accepted
    var draft by remember(conv.id) { mutableStateOf("") }
    var confirmBlock by remember(conv.id) { mutableStateOf(false) }
    var sheet by remember(conv.id) { mutableStateOf<ChatSheet?>(null) }
    var localError by remember(conv.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val groups = groupByDay(store.messages, zone)
    val today = now.toLocalDateTime(zone).date
    val readUpTo = store.partnerReadUpTo
    val scope = rememberCoroutineScope()

    // Vocaux : un seul enregistrement et une seule lecture à la fois.
    val recorder = rememberVoiceRecorder()
    val player = rememberVoicePlayer()
    var recordingSince by remember(conv.id) { mutableStateOf<Instant?>(null) }
    var playing by remember(conv.id) { mutableStateOf<String?>(null) }
    var wantToPlay by remember(conv.id) { mutableStateOf<ChatMessage?>(null) }

    fun play(m: ChatMessage) {
        val path = m.mediaPath ?: return
        val bytes = store.media[path]
        if (bytes == null) {
            wantToPlay = m
            store.loadMedia(path)
            return
        }
        wantToPlay = null
        playing = m.id
        player.play(bytes, audioType(path)) { if (playing == m.id) playing = null }
    }

    // Le vocal demandé arrive : on le lit.
    val waiting = wantToPlay
    LaunchedEffect(waiting?.mediaPath?.let { store.media[it] != null }) {
        if (waiting != null && waiting.mediaPath?.let { store.media[it] } != null) play(waiting)
    }

    fun stopRecording(send: Boolean) {
        val since = recordingSince ?: return
        recordingSince = null
        if (!send) {
            recorder.cancel()
            return
        }
        scope.launch {
            val clip = recorder.stop()
            val length = Clock.System.now() - since
            when {
                clip == null || length < 700.milliseconds -> localError = "Vocal trop court : garde l'enregistrement un peu plus longtemps."
                else -> store.sendVoice(clip, length.inWholeMilliseconds)
            }
        }
    }

    val pickPhoto = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) {
            scope.launch {
                val photo = prepareImage(file)
                if (photo == null) localError = "Cette photo n'a pas pu être envoyée : choisis une image JPEG, PNG ou WebP de moins de 10 Mo." else store.sendPhoto(photo)
            }
        }
    }

    // Rester en bas de la conversation quand un message arrive, ou quand l'autre écrit.
    val itemCount = groups.sumOf { it.second.size + 1 } + 1 + if (store.partnerTyping) 1 else 0
    LaunchedEffect(store.messages.size, store.partnerTyping) {
        if (store.messages.isNotEmpty()) listState.animateScrollToItem(itemCount - 1)
    }

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize()) {
            KikoChatHeader(
                name = conv.partnerName,
                status = listOfNotNull(
                    if (online) "En ligne" else null,
                    theirClock?.let { "${it.time} chez ${conv.partnerName}" },
                    if (conv.streak > 0) "série de ${conv.streak} j" else null,
                ).joinToString(" · ").ifEmpty { " " },
                verified = conv.partnerVerified,
                online = online,
                onBack = store::close,
                onCall = if (accepted) ({ sheet = ChatSheet.Call }) else null,
                onMore = { sheet = ChatSheet.More },
                sky = period.brush,
                onDark = period.onDark,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ChatBackdrop(if (accepted) conv.background else ChatBackground.None, period, Modifier.matchParentSize())
                LazyColumn(
                    Modifier.fillMaxSize(),
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
                        items(messages, key = { it.id }) { m ->
                            MessageItem(
                                store = store,
                                conv = conv,
                                m = m,
                                zone = zone,
                                partnerZone = partnerZone ?: zone,
                                now = now,
                                readUpTo = readUpTo,
                                playing = playing == m.id,
                                onPlay = { if (playing == m.id) { player.stop(); playing = null } else play(m) },
                                onCorrect = { if (accepted) sheet = ChatSheet.Correct(m) },
                            )
                        }
                    }
                    if (store.partnerTyping) {
                        item(key = "typing") { KikoTypingIndicator(conv.partnerName, Modifier.padding(top = 4.dp)) }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.paper)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                (localError ?: store.chatError)?.let {
                    Text(
                        it,
                        style = Kiko.type.captionStrong,
                        color = c.error,
                        modifier = Modifier.fillMaxWidth().clickable { localError = null }.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
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
                        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            KikoLinkButton(
                                if (confirmBlock) "Confirmer : bloquer" else "Bloquer",
                                { if (confirmBlock) store.block() else confirmBlock = true },
                                color = c.error,
                            )
                            KikoLinkButton("Signaler", { sheet = ChatSheet.Report }, color = c.error)
                        }
                    }
                    conv.isOutgoingRequest -> KikoMascotNote(
                        "Ta demande attend la réponse de ${conv.partnerName}. Tu pourras écrire dès qu'elle sera acceptée.",
                        Modifier.padding(16.dp),
                    )
                    recordingSince != null -> {
                        var elapsed by remember { mutableStateOf("0:00") }
                        LaunchedEffect(recordingSince) {
                            while (true) {
                                val since = recordingSince ?: break
                                val length = Clock.System.now() - since
                                elapsed = voiceDuration(length.inWholeMilliseconds)
                                // Deux minutes au plus : le vocal part tout seul.
                                if (length >= 2.minutes) {
                                    stopRecording(send = true)
                                    break
                                }
                                delay(250)
                            }
                        }
                        KikoRecordingBar(elapsed, onCancel = { stopRecording(send = false) }, onSend = { stopRecording(send = true) })
                    }
                    else -> KikoChatInput(
                        value = draft,
                        onValueChange = {
                            draft = it.take(4000)
                            store.onDraftChanged(draft)
                        },
                        onSend = {
                            store.send(draft)
                            draft = ""
                        },
                        placeholder = "Écris…",
                        onVoice = {
                            localError = null
                            player.stop()
                            playing = null
                            scope.launch {
                                if (recorder.start()) {
                                    recordingSince = Clock.System.now()
                                } else {
                                    localError = "Kiko n'a pas accès au micro. Autorise-le dans les réglages pour envoyer un vocal."
                                }
                            }
                        },
                        onAttach = {
                            localError = null
                            pickPhoto.launch()
                        },
                    )
                }
            }
        }

        sheet?.let { open ->
            SheetOverlay(onDismiss = { sheet = null }) {
                when (open) {
                    ChatSheet.More -> MoreSheet(
                        name = conv.partnerName,
                        background = if (accepted) conv.background else null,
                        onBackground = { sheet = ChatSheet.Background },
                        onReport = { sheet = ChatSheet.Report },
                        onBlock = {
                            sheet = null
                            store.block()
                        },
                    )
                    ChatSheet.Background -> BackgroundSheet(
                        name = conv.partnerName,
                        current = conv.background,
                        period = period,
                        busy = store.busy,
                        onApply = {
                            store.setBackground(it)
                            sheet = null
                        },
                        onCancel = { sheet = null },
                    )
                    ChatSheet.Report -> ReportSheet(
                        name = conv.partnerName,
                        busy = store.busy,
                        onSend = { reason, details, block ->
                            store.report(reason, details, block)
                            sheet = null
                        },
                        onCancel = { sheet = null },
                    )
                    ChatSheet.Call -> CallSheet(
                        name = conv.partnerName,
                        now = now,
                        mine = zone,
                        theirs = partnerZone ?: zone,
                        onSend = {
                            store.proposeCall(it)
                            sheet = null
                        },
                        onCancel = { sheet = null },
                    )
                    is ChatSheet.Correct -> CorrectSheet(
                        message = open.message,
                        name = conv.partnerName,
                        onSend = { corrected, note, tags ->
                            store.sendCorrection(open.message, corrected, note, tags)
                            sheet = null
                        },
                        onCancel = { sheet = null },
                    )
                }
            }
        }
    }
}

/** Un message du fil, selon son type. Un envoi raté se relance d'un toucher. */
@Composable
private fun MessageItem(
    store: ChatStore,
    conv: Conversation,
    m: ChatMessage,
    zone: TimeZone,
    partnerZone: TimeZone,
    now: Instant,
    readUpTo: Instant?,
    playing: Boolean,
    onPlay: () -> Unit,
    onCorrect: () -> Unit,
) {
    val mine = m.senderId == store.myId
    val time = when {
        m.failed -> "Non envoyé · toucher pour réessayer"
        m.pending -> "Envoi…"
        else -> clockTime(m.createdAt, zone)
    }
    val read = mine && !m.pending && readUpTo != null && m.createdAt <= readUpTo
    val retry = if (m.failed) Modifier.clickable(role = Role.Button) { store.retry(m) } else Modifier

    when (m.kind) {
        MessageKind.Photo -> {
            val path = m.mediaPath.orEmpty()
            val bytes = store.media[path]
            LaunchedEffect(path) { if (path.isNotEmpty()) store.loadMedia(path) }
            val image = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
            val failed = path in store.mediaErrors
            KikoPhotoBubble(
                image = image,
                outgoing = mine,
                time = time,
                status = when {
                    failed -> "Toucher pour réessayer"
                    bytes != null && image == null -> "Photo illisible"
                    else -> "Chargement…"
                },
                onClick = when {
                    m.failed -> ({ store.retry(m) })
                    failed -> ({ store.loadMedia(path) })
                    else -> null
                },
            )
        }
        MessageKind.Voice -> KikoVoiceBubble(
            duration = voiceDuration(m.voice().durationMs),
            outgoing = mine,
            playing = playing,
            onToggle = onPlay,
            time = time,
            read = read,
            modifier = retry,
        )
        MessageKind.Correction -> {
            val fix = m.correction()
            if (fix == null) {
                TextBubble(m, mine, time, read, retry)
            } else {
                val thanked = m.id in store.thanked
                KikoCorrectionCard(
                    author = if (mine) "Toi" else conv.partnerName,
                    title = if (mine) "Tu as corrigé ${conv.partnerName}" else "${conv.partnerName} t'a corrigé",
                    original = fix.original,
                    corrected = fix.corrected,
                    changes = changedRanges(fix.original, fix.corrected),
                    explanation = fix.note,
                    important = fix.important,
                    alignEnd = !mine,
                    footer = when {
                        m.failed || m.pending -> time
                        thanked -> "Merci envoyé"
                        else -> null
                    },
                    onThanks = if (!mine && !thanked) ({ store.thank(m) }) else null,
                    modifier = retry,
                )
            }
        }
        MessageKind.CallProposal -> {
            val call = m.call()
            val at = call?.time
            if (call == null || at == null) {
                TextBubble(m, mine, time, read, retry)
            } else {
                val acceptance = store.acceptance(m)
                val slotText = "${slotTitle(at, now, zone)} · ${clockTime(at, partnerZone)} chez ${conv.partnerName}"
                when {
                    call.acceptedAt != null -> KikoCallCard(
                        title = "Appel prévu",
                        detail = slotText,
                        outgoing = mine,
                        status = if (mine) "Tu as dit oui" else "${conv.partnerName} a dit oui",
                        confirmed = true,
                        modifier = retry,
                    )
                    else -> KikoCallCard(
                        title = if (mine) "Tu proposes un appel de ${call.minutes} min" else "${conv.partnerName} propose un appel de ${call.minutes} min",
                        detail = slotText,
                        outgoing = mine,
                        status = when {
                            m.failed || m.pending -> time
                            acceptance != null -> "C'est noté pour les deux"
                            at < now -> "Créneau passé"
                            mine -> "En attente de la réponse de ${conv.partnerName}"
                            else -> null
                        },
                        action = if (!mine && acceptance == null && at >= now && !m.pending) "Ça me va" else null,
                        onAction = { store.acceptCall(m) },
                        confirmed = acceptance != null,
                        modifier = retry,
                    )
                }
            }
        }
        MessageKind.Text -> TextBubble(
            m, mine, time, read,
            if (m.failed) {
                retry
            } else if (!mine && conv.status == ConversationStatus.Accepted) {
                Modifier.combinedClickable(onClickLabel = null, onLongClickLabel = "Corriger", onLongClick = onCorrect, onClick = {})
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun TextBubble(m: ChatMessage, mine: Boolean, time: String, read: Boolean, modifier: Modifier) {
    KikoMessageBubble(text = m.body, outgoing = mine, time = time, read = read, modifier = modifier)
}
