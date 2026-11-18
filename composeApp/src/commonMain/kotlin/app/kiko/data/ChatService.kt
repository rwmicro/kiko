@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package app.kiko.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class ConversationStatus { Requested, Accepted, Declined }

/** Une ligne de l'onglet Discussions. */
data class Conversation(
    val id: String,
    val status: ConversationStatus,
    /** Vrai si c'est moi qui ai salué en premier. */
    val requestedByMe: Boolean,
    val partnerId: String,
    val partnerName: String,
    val partnerVerified: Boolean,
    val partnerLastSeen: Instant?,
    val partnerTimeZone: String,
    val partnerNativeLanguage: String?,
    val lastMessage: String?,
    val lastMessageMine: Boolean,
    val lastActivity: Instant,
    val unread: Int,
) {
    /** Une demande que j'ai reçue et pas encore acceptée. */
    val isIncomingRequest: Boolean get() = status == ConversationStatus.Requested && !requestedByMe

    /** Une demande que j'ai envoyée et qui attend sa réponse. */
    val isOutgoingRequest: Boolean get() = status == ConversationStatus.Requested && requestedByMe
}

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String?,
    val body: String,
    val createdAt: Instant,
    /** Envoyé par l'app mais pas encore confirmé par le serveur. */
    val pending: Boolean = false,
    /** L'envoi a échoué : on peut réessayer. */
    val failed: Boolean = false,
)

/** Discussions et messages, côté serveur. */
interface ChatService {
    /** Identifiant de la personne connectée. */
    val myId: String?
    suspend fun conversations(): List<Conversation>
    suspend fun messages(conversationId: String): List<ChatMessage>

    /** [id] est créé par l'app : un envoi rejoué ne crée pas de doublon. */
    suspend fun send(conversationId: String, id: String, text: String): ChatMessage
    suspend fun respond(conversationId: String, accept: Boolean)
    suspend fun block(partnerId: String)
    suspend fun markRead(conversationId: String)

    /** Jusqu'où l'autre a lu, seulement une fois la demande acceptée. */
    suspend fun partnerReadUpTo(conversationId: String): Instant?

    /** Les nouveaux messages de toutes mes conversations, en temps réel. */
    fun newMessages(): Flow<ChatMessage>
}

fun newMessageId(): String = Uuid.random().toString()

class SupabaseChatService(private val client: SupabaseClient) : ChatService {

    override val myId: String? get() = client.auth.currentUserOrNull()?.id

    override suspend fun conversations(): List<Conversation> = guard {
        client.postgrest.rpc("my_conversations").decodeList<ConversationRow>().map { it.toConversation() }
    }

    override suspend fun messages(conversationId: String): List<ChatMessage> = guard {
        client.from("messages")
            .select {
                filter { eq("conversation_id", conversationId) }
                order("created_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList<MessageRow>()
            .mapNotNull { it.toMessage() }
            .reversed()
    }

    override suspend fun send(conversationId: String, id: String, text: String): ChatMessage = guard {
        val body = text.trim()
        if (body.isEmpty()) throw AccountException("Écris un message avant d'envoyer.")
        val sender = myId ?: throw AccountException("Ta session a expiré. Reconnecte-toi.")
        // Pas de created_at : c'est le serveur qui date le message.
        client.from("messages").upsert(NewMessageRow(id, conversationId, sender, body)) { ignoreDuplicates = true }
        ChatMessage(id, conversationId, sender, body, kotlin.time.Clock.System.now())
    }

    override suspend fun respond(conversationId: String, accept: Boolean): Unit = guard {
        client.postgrest.rpc(
            "respond_to_request",
            buildJsonObject {
                put("conv", conversationId)
                put("accept", accept)
            },
        )
    }

    override suspend fun block(partnerId: String): Unit = guard {
        val me = myId ?: throw AccountException("Ta session a expiré. Reconnecte-toi.")
        client.from("blocks").upsert(BlockRow(me, partnerId)) { ignoreDuplicates = true }
    }

    override suspend fun markRead(conversationId: String) {
        try {
            client.postgrest.rpc("mark_read", buildJsonObject { put("conv", conversationId) })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Le marqueur de lecture est un confort : un échec ne bloque rien.
        }
    }

    override suspend fun partnerReadUpTo(conversationId: String): Instant? = try {
        val raw = client.postgrest.rpc("partner_read_up_to", buildJsonObject { put("conv", conversationId) })
            .data.trim().trim('"')
        if (raw.isEmpty() || raw == "null") null else parseTimestamp(raw)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override fun newMessages(): Flow<ChatMessage> = flow {
        // Un seul canal pour toutes les conversations : les règles RLS ne laissent passer
        // que les messages des conversations dont je suis membre.
        val channel = client.channel("messages-${Uuid.random()}")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "messages" }
        channel.subscribe()
        try {
            inserts.collect { change ->
                val message = runCatching { change.decodeRecord<MessageRow>().toMessage() }.getOrNull()
                if (message != null) emit(message)
            }
        } finally {
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: AccountException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AccountException(chatMessage(e), e)
    }
}

/** Erreurs des discussions, en phrases claires. */
internal fun chatMessage(e: Throwable): String {
    val raw = (e.message ?: "").lowercase()
    return when {
        "no pending request" in raw -> "Cette demande a déjà reçu une réponse."
        "row-level security" in raw && "messages" in raw ->
            "Ce message n'a pas pu partir : la demande n'est pas encore acceptée, ou la conversation est bloquée."
        "could not find the function" in raw || "pgrst202" in raw ->
            "Le serveur n'est pas encore à jour : il manque la migration Discussions."
        else -> friendlyMessage(e)
    }
}

/** Lit un horodatage Postgres, avec ou sans « T », et avec un décalage court (+00). */
fun parseTimestamp(raw: String): Instant? {
    var s = raw.trim().replace(' ', 'T')
    if (Regex("[+-]\\d{2}$").containsMatchIn(s)) s += ":00"
    if (!s.endsWith("Z") && !Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(s)) s += "Z"
    return runCatching { Instant.parse(s) }.getOrNull()
}

@Serializable
private data class ConversationRow(
    val id: String,
    val status: String,
    @SerialName("requested_by_me") val requestedByMe: Boolean,
    @SerialName("partner_id") val partnerId: String,
    @SerialName("partner_first_name") val partnerFirstName: String,
    @SerialName("partner_verified") val partnerVerified: Boolean = false,
    @SerialName("partner_last_seen") val partnerLastSeen: String? = null,
    @SerialName("partner_time_zone") val partnerTimeZone: String = "UTC",
    @SerialName("partner_native_language") val partnerNativeLanguage: String? = null,
    @SerialName("last_message_body") val lastMessageBody: String? = null,
    @SerialName("last_message_mine") val lastMessageMine: Boolean? = null,
    @SerialName("last_activity") val lastActivity: String,
    val unread: Int = 0,
) {
    fun toConversation() = Conversation(
        id = id,
        status = when (status) {
            "accepted" -> ConversationStatus.Accepted
            "declined" -> ConversationStatus.Declined
            else -> ConversationStatus.Requested
        },
        requestedByMe = requestedByMe,
        partnerId = partnerId,
        partnerName = partnerFirstName,
        partnerVerified = partnerVerified,
        partnerLastSeen = partnerLastSeen?.let(::parseTimestamp),
        partnerTimeZone = partnerTimeZone,
        partnerNativeLanguage = partnerNativeLanguage,
        lastMessage = lastMessageBody,
        lastMessageMine = lastMessageMine ?: false,
        lastActivity = parseTimestamp(lastActivity) ?: Instant.fromEpochSeconds(0),
        unread = unread,
    )
}

@Serializable
private data class MessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String? = null,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toMessage(): ChatMessage? {
        val text = body?.takeIf { it.isNotBlank() } ?: return null
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            body = text,
            createdAt = createdAt?.let(::parseTimestamp) ?: kotlin.time.Clock.System.now(),
        )
    }
}

@Serializable
private data class NewMessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
)

@Serializable
private data class BlockRow(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
)
