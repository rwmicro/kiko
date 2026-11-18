@file:OptIn(ExperimentalTime::class)

package app.kiko.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Une personne proposée sur l'écran Partenaires. */
data class Partner(
    val id: String,
    val firstName: String,
    val age: Int,
    /** Null quand la personne a choisi de ne montrer que son pays. */
    val city: String?,
    val countryCode: String?,
    val timeZone: String,
    val verified: Boolean,
    val lastSeen: Instant?,
    /** Sa langue natale, celle que j'apprends. */
    val nativeLanguage: String,
    /** La langue qu'elle apprend, ma langue natale, et son niveau. */
    val learningLanguage: String,
    val learningLevel: String?,
    /** all, major_only ou none : comment la personne veut être corrigée. */
    val correction: String?,
    val interests: List<String>,
    val commonInterests: List<String>,
)

/** Partenaires, côté serveur. */
interface PartnerService {
    suspend fun suggestions(): List<Partner>

    /** Envoie un premier message : ouvre la demande de discussion. */
    suspend fun greet(partnerId: String, message: String)

    /** Note que la personne connectée est active, pour la pastille « en ligne ». */
    suspend fun markActive()
}

class SupabasePartnerService(private val client: SupabaseClient) : PartnerService {

    override suspend fun suggestions(): List<Partner> = guard {
        client.postgrest.rpc("partner_suggestions", buildJsonObject { put("max_results", 50) })
            .decodeList<PartnerRow>()
            .map { it.toPartner() }
    }

    override suspend fun greet(partnerId: String, message: String): Unit = guard {
        client.postgrest.rpc(
            "send_greeting",
            buildJsonObject {
                put("partner", partnerId)
                put("greeting", message.trim())
            },
        )
    }

    override suspend fun markActive() {
        if (client.auth.currentUserOrNull() == null) return
        // L'heure est posée par le serveur (fonction mark_active), pas par l'horloge du téléphone.
        try {
            client.postgrest.rpc("mark_active")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // La présence est un bonus : un échec ne doit rien bloquer.
        }
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: AccountException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AccountException(partnerMessage(e), e)
    }
}

/** Erreurs des fonctions Partenaires, en phrases claires. */
internal fun partnerMessage(e: Throwable): String {
    val raw = (e.message ?: "").lowercase()
    return when {
        "already greeted" in raw -> "Tu as déjà salué cette personne. Retrouve-la dans Discussions."
        "blocked" in raw -> "Tu ne peux pas écrire à cette personne."
        "too many greetings" in raw -> "Tu as envoyé beaucoup de saluts aujourd'hui. Réessaie demain."
        "empty greeting" in raw -> "Écris un petit message avant d'envoyer."
        "invalid partner" in raw -> "Ce profil n'est plus disponible."
        "could not find the function" in raw || "pgrst202" in raw ->
            "Le serveur n'est pas encore à jour : il manque la migration Partenaires."
        else -> friendlyMessage(e)
    }
}

@Serializable
private data class PartnerRow(
    val id: String,
    @SerialName("first_name") val firstName: String,
    val age: Int,
    val city: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("time_zone") val timeZone: String = "UTC",
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("native_language") val nativeLanguage: String,
    @SerialName("learning_language") val learningLanguage: String,
    @SerialName("learning_level") val learningLevel: String? = null,
    @SerialName("correction_preference") val correctionPreference: String? = null,
    val interests: List<String> = emptyList(),
    @SerialName("common_interests") val commonInterests: List<String> = emptyList(),
) {
    fun toPartner() = Partner(
        id = id,
        firstName = firstName,
        age = age,
        city = city?.takeIf { it.isNotBlank() },
        countryCode = countryCode?.trim()?.takeIf { it.isNotEmpty() },
        timeZone = timeZone,
        verified = isVerified,
        lastSeen = lastSeenAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        nativeLanguage = nativeLanguage,
        learningLanguage = learningLanguage,
        learningLevel = learningLevel,
        correction = correctionPreference,
        interests = interests,
        commonInterests = commonInterests,
    )
}
