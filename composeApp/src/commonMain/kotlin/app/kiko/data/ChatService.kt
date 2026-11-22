@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package app.kiko.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
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

/** Type d'un message, comme l'énumération message_kind de la base. */
enum class MessageKind(val wire: String) {
    Text("text"),
    Voice("voice"),
    Photo("photo"),
    Correction("correction"),
    CallProposal("call_proposal"),
    ;

    companion object {
        fun of(wire: String?): MessageKind = entries.firstOrNull { it.wire == wire } ?: Text
    }
}

/** Fond d'une discussion (maquette 17), choisi par l'un et vu par les deux. */
enum class ChatBackground(val wire: String, val label: String) {
    Sky("sky", "Son ciel"),
    Beach("beach", "Plage"),
    Forest("forest", "Forêt"),
    Lagoon("lagoon", "Lagon"),
    Sakura("sakura", "Sakura"),
    None("none", "Aucun"),
    ;

    companion object {
        fun of(wire: String?): ChatBackground = entries.firstOrNull { it.wire == wire } ?: Sky
    }
}

/** Motifs de signalement, dans l'ordre où l'écran les propose. */
enum class ReportReason(val wire: String, val label: String) {
    Harassment("harassment", "Harcèlement ou insultes"),
    Inappropriate("inappropriate", "Contenu sexuel ou déplacé"),
    Spam("spam", "Spam, arnaque ou pub"),
    Fake("fake", "Faux profil"),
    Minor("minor", "Semble avoir moins de 18 ans"),
    Other("other", "Autre chose"),
}

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
    val lastMessageKind: MessageKind = MessageKind.Text,
    /** Jours d'affilée où chacun a écrit au moins une fois. */
    val streak: Int = 0,
    val background: ChatBackground = ChatBackground.Sky,
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
    /** Le texte ; pour une correction ou une proposition d'appel, le détail en JSON (voir ChatPayloads). */
    val body: String,
    val createdAt: Instant,
    val kind: MessageKind = MessageKind.Text,
    /** Photo ou vocal : chemin du fichier dans le stockage, « <conversation>/<fichier> ». */
    val mediaPath: String? = null,
    /** Le message corrigé, ou la proposition d'appel acceptée. */
    val replyToId: String? = null,
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

    /** L'identifiant du message est créé par l'app : un envoi rejoué ne crée pas de doublon. */
    suspend fun send(message: ChatMessage): ChatMessage

    /** Range une photo ou un vocal au chemin [path] ; un envoi rejoué ne le duplique pas. */
    suspend fun uploadMedia(path: String, bytes: ByteArray, contentType: String)

    /** Le contenu d'une photo ou d'un vocal. */
    suspend fun media(path: String): ByteArray
    suspend fun respond(conversationId: String, accept: Boolean)
    suspend fun block(partnerId: String)
    suspend fun report(partnerId: String, conversationId: String?, reason: ReportReason, details: String?)
    suspend fun setBackground(conversationId: String, background: ChatBackground)
    suspend fun markRead(conversationId: String)

    /** Jusqu'où l'autre a lu, seulement une fois la demande acceptée. */
    suspend fun partnerReadUpTo(conversationId: String): Instant?

    /** Les nouveaux messages de toutes mes conversations, en temps réel. */
    fun newMessages(): Flow<ChatMessage>

    /** « écrit… » : l'identifiant de qui écrit dans cette conversation, à chaque signal. */
    fun typing(conversationId: String): Flow<String>

    /** Prévient l'autre que j'écris. Sans effet si [typing] n'écoute pas cette conversation. */
    suspend fun sendTyping(conversationId: String)
}

/** Chemin d'un fichier de discussion : un dossier par conversation, le nom suit le message. */
fun mediaPath(conversationId: String, messageId: String, extension: String) = "$conversationId/$messageId.$extension"

fun newMessageId(): String = Uuid.random().toString()

class SupabaseChatService(private val client: SupabaseClient) : ChatService {

    override val myId: String? get() = client.auth.currentUserOrNull()?.id

    override suspend fun conversations(): List<Conversation> = guard {
        client.postgrest.rpc("my_conversations").decodeList<ConversationRow>().map { it.toConversation() }
    }

    override suspend fun messages(conversationId: String): List<ChatMessage> = guard {
        client.from("messages")
            .select(Columns.list("id", "conversation_id", "sender_id", "kind", "body", "media_path", "reply_to_id", "created_at")) {
                filter { eq("conversation_id", conversationId) }
                order("created_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList<MessageRow>()
            .mapNotNull { it.toMessage() }
            .reversed()
    }

    override suspend fun send(message: ChatMessage): ChatMessage = guard {
        val body = message.body.trim()
        val media = message.kind == MessageKind.Photo || message.kind == MessageKind.Voice
        if (body.isEmpty() && !media) throw AccountException("Écris un message avant d'envoyer.")
        if (media && message.mediaPath == null) throw AccountException("Le fichier manque.")
        val sender = myId ?: throw AccountException("Ta session a expiré. Reconnecte-toi.")
        // Pas de created_at : c'est le serveur qui date le message.
        val row = NewMessageRow(
            id = message.id,
            conversationId = message.conversationId,
            senderId = sender,
            kind = message.kind.wire,
            body = body.ifEmpty { null },
            mediaPath = message.mediaPath,
            replyToId = message.replyToId,
        )
        client.from("messages").upsert(row) { ignoreDuplicates = true }
        message.copy(senderId = sender, body = body, pending = false, failed = false)
    }

    override suspend fun uploadMedia(path: String, bytes: ByteArray, contentType: String): Unit = guard {
        try {
            client.storage.from(MEDIA_BUCKET).upload(path, bytes) { this.contentType = ContentType.parse(contentType) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un envoi rejoué retrouve le fichier déjà rangé : c'est un succès.
            val raw = (e.message ?: "").lowercase()
            if ("already exists" !in raw && "duplicate" !in raw) throw e
        }
    }

    override suspend fun media(path: String): ByteArray = guard {
        client.storage.from(MEDIA_BUCKET).downloadAuthenticated(path)
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

    override suspend fun report(partnerId: String, conversationId: String?, reason: ReportReason, details: String?): Unit = guard {
        client.postgrest.rpc(
            "report_user",
            buildJsonObject {
                put("target", partnerId)
                put("reason", reason.wire)
                put("details", details?.trim()?.take(1000))
                put("conv", conversationId)
            },
        )
    }

    override suspend fun setBackground(conversationId: String, background: ChatBackground): Unit = guard {
        client.postgrest.rpc(
            "set_conversation_background",
            buildJsonObject {
                put("conv", conversationId)
                put("bg", background.wire)
            },
        )
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

    /** Canaux « écrit… » ouverts, par conversation : [sendTyping] passe par le même canal. */
    private val typingChannels = mutableMapOf<String, RealtimeChannel>()

    override fun typing(conversationId: String): Flow<String> = flow {
        // Canal privé : les règles de realtime.messages le réservent aux deux membres.
        val channel = client.channel("chat:$conversationId") { isPrivate = true }
        val signals = channel.broadcastFlow<TypingSignal>(TYPING_EVENT)
        channel.subscribe()
        typingChannels[conversationId] = channel
        try {
            signals.collect { emit(it.userId) }
        } finally {
            typingChannels.remove(conversationId)
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    override suspend fun sendTyping(conversationId: String) {
        val me = myId ?: return
        val channel = typingChannels[conversationId] ?: return
        try {
            channel.broadcast(TYPING_EVENT, TypingSignal(me))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // « écrit… » est un confort : un signal perdu ne gêne personne.
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

private const val MEDIA_BUCKET = "chat-media"
private const val TYPING_EVENT = "typing"

@Serializable
private data class TypingSignal(@SerialName("user_id") val userId: String)

/** Erreurs des discussions, en phrases claires. */
internal fun chatMessage(e: Throwable): String {
    val raw = (e.message ?: "").lowercase()
    return when {
        "no pending request" in raw -> "Cette demande a déjà reçu une réponse."
        "row-level security" in raw && "messages" in raw ->
            "Ce message n'a pas pu partir : la demande n'est pas encore acceptée, ou la conversation est bloquée."
        "could not find the function" in raw || "pgrst202" in raw ->
            "Le serveur n'est pas encore à jour : il manque une migration des Discussions."
        "too many reports" in raw -> "Tu as déjà envoyé beaucoup de signalements aujourd'hui. Réessaie demain."
        "invalid report" in raw -> "Ce signalement n'a pas pu être envoyé."
        "not allowed" in raw -> "Le fond se choisit une fois la demande acceptée."
        "payload too large" in raw || "maximum allowed size" in raw || "413" in raw -> "Ce fichier est trop lourd (10 Mo au plus)."
        "bucket not found" in raw -> "Le serveur n'est pas encore à jour : il manque le stockage des photos et vocaux."
        else -> friendlyMessage(e)
    }
}

/**
 * Vrai si l'échec ne dit rien du message lui-même : pas de réseau, délai dépassé, serveur
 * momentanément indisponible. Le message reste dans la file et repartira tout seul.
 * Faux si le serveur l'a refusé (règles RLS, fichier trop lourd…) : réessayer ne changerait rien.
 */
fun Throwable.isTemporaryFailure(): Boolean {
    var e: Throwable? = this
    var fromOutside = false
    while (e != null) {
        if (e is RestException) return e.statusCode >= 500 || e.statusCode == 408 || e.statusCode == 429
        // Nos propres refus (message vide, session expirée) n'ont pas de cause : ils sont définitifs.
        if (e !is AccountException) fromOutside = true
        e = e.cause
    }
    return fromOutside
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
    @SerialName("last_message_kind") val lastMessageKind: String? = null,
    @SerialName("last_message_mine") val lastMessageMine: Boolean? = null,
    @SerialName("last_activity") val lastActivity: String,
    val unread: Int = 0,
    val streak: Int = 0,
    val background: String? = null,
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
        lastMessageKind = MessageKind.of(lastMessageKind),
        streak = streak,
        background = ChatBackground.of(background),
    )
}

@Serializable
private data class MessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String? = null,
    val kind: String? = null,
    val body: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toMessage(): ChatMessage? {
        val type = MessageKind.of(kind)
        val text = body?.takeIf { it.isNotBlank() }
        val hasMedia = type == MessageKind.Photo || type == MessageKind.Voice
        if (if (hasMedia) mediaPath == null else text == null) return null
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            body = text.orEmpty(),
            createdAt = createdAt?.let(::parseTimestamp) ?: kotlin.time.Clock.System.now(),
            kind = type,
            mediaPath = mediaPath,
            replyToId = replyToId,
        )
    }
}

@Serializable
private data class NewMessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String,
    val body: String?,
    @SerialName("media_path") val mediaPath: String?,
    @SerialName("reply_to_id") val replyToId: String?,
)

@Serializable
private data class BlockRow(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
)
