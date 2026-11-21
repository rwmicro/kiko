@file:OptIn(ExperimentalTime::class)

package app.kiko.chats

import app.cash.sqldelight.db.SqlDriver
import app.kiko.data.ChatBackground
import app.kiko.data.ChatMessage
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import app.kiko.data.MessageKind
import app.kiko.db.KikoDatabase
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import app.kiko.db.Conversation as ConversationRow
import app.kiko.db.Message as MessageRow

/** Messages envoyés gardés par conversation sur l'appareil : autant que l'écran en charge. */
const val CACHED_MESSAGES_PER_CONVERSATION = 200

/**
 * Ce que l'appareil garde des discussions : la liste, les derniers messages, et la file des
 * messages écrits hors ligne. Le serveur reste la référence : le cache se recale sur lui à
 * chaque chargement réussi.
 */
interface ChatCache {
    /** Ouvre le cache pour [owner] ; s'il appartenait à un autre compte, il est vidé d'abord. */
    suspend fun open(owner: String)

    suspend fun conversations(): List<Conversation>

    /**
     * Remplace la liste par celle du serveur. Une conversation qui n'y est plus (refusée,
     * bloquée, supprimée) part avec ses messages, y compris ceux qui attendaient de partir.
     */
    suspend fun saveConversations(list: List<Conversation>)

    /** Les messages d'une conversation, envoyés ou non, du plus ancien au plus récent. */
    suspend fun messages(conversationId: String): List<ChatMessage>

    /** Remplace les messages envoyés d'une conversation par ceux du serveur ; la file reste. */
    suspend fun saveServerMessages(conversationId: String, list: List<ChatMessage>)

    /** Un message confirmé par le serveur (reçu en temps réel, ou le mien enfin parti). */
    suspend fun saveSent(message: ChatMessage)

    /** Met un message dans la file d'envoi, avec sa photo ou son vocal s'il en a un. */
    suspend fun enqueue(message: ChatMessage, file: MediaFile? = null)

    /** Les messages qui attendent de partir, toutes conversations, dans l'ordre où ils ont été écrits. */
    suspend fun queued(): List<ChatMessage>

    suspend fun queuedMedia(messageId: String): MediaFile?

    /** Le serveur a refusé ce message : il reste affiché, un toucher le remet dans la file. */
    suspend fun markFailed(messageId: String)

    suspend fun requeue(messageId: String)

    /** Déconnexion ou suppression du compte : plus rien sur l'appareil. */
    suspend fun clear()
}

private const val SENT = 0L
private const val QUEUED = 1L
private const val FAILED = 2L

/** Cache sur SQLite (Android, iOS). [io] : où faire les lectures et écritures. */
class SqlChatCache(driver: SqlDriver, private val io: CoroutineContext = EmptyCoroutineContext) : ChatCache {
    private val db = KikoDatabase(driver)
    private val q = db.chatQueries

    private suspend fun <T> onDisk(block: () -> T): T = withContext(io) { block() }

    override suspend fun open(owner: String): Unit = onDisk {
        db.transaction {
            val current = q.owner().executeAsOneOrNull()
            if (current != owner) {
                wipe()
                q.setOwner(owner)
            }
        }
    }

    override suspend fun conversations(): List<Conversation> = onDisk {
        q.conversations().executeAsList().map { it.toConversation() }
    }

    override suspend fun saveConversations(list: List<Conversation>): Unit = onDisk {
        db.transaction {
            val keep = list.map { it.id }.toSet()
            q.conversationIds().executeAsList().filterNot { it in keep }.forEach { gone ->
                q.deleteMediaIn(gone)
                q.deleteMessagesIn(gone)
                q.deleteConversation(gone)
            }
            list.forEach { q.upsertConversation(it.toRow()) }
        }
    }

    override suspend fun messages(conversationId: String): List<ChatMessage> = onDisk {
        q.messages(conversationId).executeAsList().map { it.toMessage() }
    }

    override suspend fun saveServerMessages(conversationId: String, list: List<ChatMessage>): Unit = onDisk {
        db.transaction {
            q.deleteSentIn(conversationId)
            list.forEach { q.upsertMessage(it.toRow(SENT)) }
            q.trimSent(conversationId, CACHED_MESSAGES_PER_CONVERSATION.toLong())
        }
    }

    override suspend fun saveSent(message: ChatMessage): Unit = onDisk {
        db.transaction {
            // Un message à moi confirmé garde l'heure où je l'ai écrit, comme à l'écran.
            val createdAt = q.createdAt(message.id).executeAsOneOrNull()
            q.upsertMessage(message.toRow(SENT).let { if (createdAt != null) it.copy(created_at = createdAt) else it })
            q.deleteMedia(message.id)
            q.trimSent(message.conversationId, CACHED_MESSAGES_PER_CONVERSATION.toLong())
        }
    }

    override suspend fun enqueue(message: ChatMessage, file: MediaFile?): Unit = onDisk {
        db.transaction {
            q.upsertMessage(message.toRow(QUEUED))
            if (file != null) {
                q.insertMedia(app.kiko.db.Outbox_media(message.id, file.bytes, file.contentType, file.extension))
            }
        }
    }

    override suspend fun queued(): List<ChatMessage> = onDisk {
        q.queued().executeAsList().map { it.toMessage() }
    }

    override suspend fun queuedMedia(messageId: String): MediaFile? = onDisk {
        q.media(messageId).executeAsOneOrNull()?.let { MediaFile(it.bytes, it.content_type, it.extension) }
    }

    override suspend fun markFailed(messageId: String): Unit = onDisk { q.setState(FAILED, messageId) }

    override suspend fun requeue(messageId: String): Unit = onDisk { q.setState(QUEUED, messageId) }

    override suspend fun clear(): Unit = onDisk { db.transaction { wipe() } }

    private fun wipe() {
        q.clearMedia()
        q.clearMessages()
        q.clearConversations()
        q.clearOwner()
    }
}

/** Cache qui ne survit pas à l'app : aperçu web, mode démo et tests. */
class MemoryChatCache : ChatCache {
    private var owner: String? = null
    private var conversations = emptyList<Conversation>()
    private val messages = mutableMapOf<String, ChatMessage>()
    private val files = mutableMapOf<String, MediaFile>()

    override suspend fun open(owner: String) {
        if (this.owner != owner) clear()
        this.owner = owner
    }

    override suspend fun conversations() = conversations.sortedByDescending { it.lastActivity }

    override suspend fun saveConversations(list: List<Conversation>) {
        val keep = list.map { it.id }.toSet()
        messages.values.filter { it.conversationId !in keep }.forEach {
            messages.remove(it.id)
            files.remove(it.id)
        }
        // Comme sur SQLite : la présence de l'autre n'est pas gardée.
        conversations = list.map { it.copy(partnerLastSeen = null) }
    }

    override suspend fun messages(conversationId: String) =
        messages.values.filter { it.conversationId == conversationId }.sortedWith(writingOrder)

    override suspend fun saveServerMessages(conversationId: String, list: List<ChatMessage>) {
        messages.values.filter { it.conversationId == conversationId && it.isSent }.forEach { messages.remove(it.id) }
        list.forEach { messages[it.id] = it.copy(pending = false, failed = false) }
        trim(conversationId)
    }

    override suspend fun saveSent(message: ChatMessage) {
        val createdAt = messages[message.id]?.createdAt ?: message.createdAt
        messages[message.id] = message.copy(createdAt = createdAt, pending = false, failed = false)
        files.remove(message.id)
        trim(message.conversationId)
    }

    override suspend fun enqueue(message: ChatMessage, file: MediaFile?) {
        messages[message.id] = message.copy(pending = true, failed = false)
        if (file != null) files[message.id] = file
    }

    override suspend fun queued() = messages.values.filter { it.pending }.sortedWith(writingOrder)

    override suspend fun queuedMedia(messageId: String) = files[messageId]

    override suspend fun markFailed(messageId: String) {
        messages[messageId]?.let { messages[messageId] = it.copy(pending = false, failed = true) }
    }

    override suspend fun requeue(messageId: String) {
        messages[messageId]?.let { messages[messageId] = it.copy(pending = true, failed = false) }
    }

    override suspend fun clear() {
        owner = null
        conversations = emptyList()
        messages.clear()
        files.clear()
    }

    private fun trim(conversationId: String) {
        messages.values.filter { it.conversationId == conversationId && it.isSent }
            .sortedByDescending { it.createdAt }
            .drop(CACHED_MESSAGES_PER_CONVERSATION)
            .forEach { messages.remove(it.id) }
    }
}

private val ChatMessage.isSent get() = !pending && !failed

private val writingOrder = compareBy<ChatMessage>({ it.createdAt }, { it.id })

private fun Conversation.toRow() = ConversationRow(
    id = id,
    status = when (status) {
        ConversationStatus.Requested -> "requested"
        ConversationStatus.Accepted -> "accepted"
        ConversationStatus.Declined -> "declined"
    },
    requested_by_me = if (requestedByMe) 1 else 0,
    partner_id = partnerId,
    partner_name = partnerName,
    partner_verified = if (partnerVerified) 1 else 0,
    partner_time_zone = partnerTimeZone,
    partner_native_language = partnerNativeLanguage,
    last_message = lastMessage,
    last_message_kind = lastMessageKind.wire,
    last_message_mine = if (lastMessageMine) 1 else 0,
    last_activity = lastActivity.toEpochMilliseconds(),
    unread = unread.toLong(),
    streak = streak.toLong(),
    background = background.wire,
)

private fun ConversationRow.toConversation() = Conversation(
    id = id,
    status = when (status) {
        "accepted" -> ConversationStatus.Accepted
        "declined" -> ConversationStatus.Declined
        else -> ConversationStatus.Requested
    },
    requestedByMe = requested_by_me != 0L,
    partnerId = partner_id,
    partnerName = partner_name,
    partnerVerified = partner_verified != 0L,
    // Pas gardé sur l'appareil : « vu il y a… » revient avec le réseau.
    partnerLastSeen = null,
    partnerTimeZone = partner_time_zone,
    partnerNativeLanguage = partner_native_language,
    lastMessage = last_message,
    lastMessageMine = last_message_mine != 0L,
    lastActivity = Instant.fromEpochMilliseconds(last_activity),
    unread = unread.toInt(),
    lastMessageKind = MessageKind.of(last_message_kind),
    streak = streak.toInt(),
    background = ChatBackground.of(background),
)

private fun ChatMessage.toRow(state: Long) = MessageRow(
    id = id,
    conversation_id = conversationId,
    sender_id = senderId,
    kind = kind.wire,
    body = body,
    media_path = mediaPath,
    reply_to_id = replyToId,
    created_at = createdAt.toEpochMilliseconds(),
    state = state,
)

private fun MessageRow.toMessage() = ChatMessage(
    id = id,
    conversationId = conversation_id,
    senderId = sender_id,
    body = body,
    createdAt = Instant.fromEpochMilliseconds(created_at),
    kind = MessageKind.of(kind),
    mediaPath = media_path,
    replyToId = reply_to_id,
    pending = state == QUEUED,
    failed = state == FAILED,
)
