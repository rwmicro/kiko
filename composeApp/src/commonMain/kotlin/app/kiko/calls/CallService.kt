package app.kiko.calls

import app.kiko.data.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Où en est un appel côté serveur (colonne status de la table calls). */
enum class CallStatus(val wire: String) {
    Ringing("ringing"), Active("active"), Declined("declined"), Missed("missed"), Cancelled("cancelled"), Ended("ended");

    val isOver: Boolean get() = this != Ringing && this != Active

    companion object {
        fun of(wire: String?): CallStatus? = entries.firstOrNull { it.wire == wire }
    }
}

/** Un appel créé (ou décroché, si l'autre appelait au même moment). */
data class StartedCall(val id: String, val status: CallStatus)

/** De quoi entrer dans la salle LiveKit de l'appel. */
data class CallAccess(val url: String, val token: String)

/** Un refus que la personne peut comprendre, en français, prêt à afficher. */
class CallException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Le côté serveur des appels (voir supabase/migrations/20261122000000_calls.sql et la fonction livekit-token). */
interface CallService {
    suspend fun start(conversationId: String, partnerName: String): StartedCall
    suspend fun answer(callId: String): CallStatus
    suspend fun end(callId: String): CallStatus
    suspend fun status(callId: String): CallStatus?
    suspend fun access(callId: String): CallAccess
}

class SupabaseCallService(private val client: SupabaseClient = SupabaseProvider.shared()) : CallService {
    override suspend fun start(conversationId: String, partnerName: String): StartedCall = guard(partnerName) {
        val row = client.postgrest.rpc("start_call", buildJsonObject { put("p_conversation", conversationId) })
            .decodeList<StartRow>().first()
        StartedCall(row.callId, CallStatus.of(row.status) ?: CallStatus.Ringing)
    }

    override suspend fun answer(callId: String): CallStatus = guard {
        val raw = client.postgrest.rpc("answer_call", buildJsonObject { put("p_call", callId) }).data
        CallStatus.of(scalar(raw)) ?: CallStatus.Ended
    }

    override suspend fun end(callId: String): CallStatus = guard {
        val raw = client.postgrest.rpc("end_call", buildJsonObject { put("p_call", callId) }).data
        CallStatus.of(scalar(raw)) ?: CallStatus.Ended
    }

    override suspend fun status(callId: String): CallStatus? = guard {
        client.postgrest.rpc("call_state", buildJsonObject { put("p_call", callId) })
            .decodeList<StateRow>().firstOrNull()?.let { CallStatus.of(it.status) }
    }

    override suspend fun access(callId: String): CallAccess = guard {
        val response = client.functions.invoke("livekit-token", buildJsonObject { put("call_id", callId) })
        val body = json.decodeFromString(TokenRow.serializer(), response.bodyAsText())
        CallAccess(body.url ?: throw CallException("Les appels ne sont pas encore configurés."), body.token)
    }

    private fun scalar(raw: String): String? =
        runCatching { json.parseToJsonElement(raw).jsonPrimitive.content }.getOrNull() ?: raw.trim('"')

    private inline fun <T> guard(partnerName: String = "", block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: CallException) {
        throw e
    } catch (e: Exception) {
        throw CallException(explain(e.message.orEmpty(), partnerName), e)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Les refus du serveur, en mots simples. */
internal fun explain(raw: String, partnerName: String): String {
    val who = partnerName.ifBlank { "Ton partenaire" }
    return when {
        "partner_asleep" in raw -> "Il fait nuit chez $who. Propose plutôt un appel à une heure où vous êtes réveillés tous les deux."
        "too_many_calls" in raw -> "Tu as déjà appelé souvent cette dernière heure. Réessaie un peu plus tard."
        "call_not_allowed" in raw -> "Tu ne peux pas appeler dans cette discussion."
        "call_unavailable" in raw || "call_not_found" in raw -> "Cet appel est terminé."
        "LiveKit" in raw || "503" in raw -> "Les appels ne sont pas encore configurés."
        else -> "L'appel n'a pas pu se faire. Vérifie ta connexion."
    }
}

@Serializable
private data class StartRow(@SerialName("call_id") val callId: String, val status: String)

@Serializable
private data class StateRow(val status: String)

@Serializable
private data class TokenRow(val url: String? = null, val token: String)
