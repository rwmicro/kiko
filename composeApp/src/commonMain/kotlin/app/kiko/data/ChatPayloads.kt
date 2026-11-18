@file:OptIn(ExperimentalTime::class)

package app.kiko.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Le détail des messages qui ne sont pas du simple texte, rangé en JSON dans `body`.
 * Un message mal formé s'affiche comme du texte : rien ne casse si le format évolue.
 */
private val payloadJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Ce que l'autre a corrigé (maquette 18). [tags] : « grammaire », « orthographe », « naturel ». */
@Serializable
data class CorrectionPayload(
    val original: String,
    val corrected: String,
    val note: String? = null,
    val tags: List<String> = emptyList(),
) {
    /** Une erreur de grammaire ou d'orthographe passe en premier ; « plus naturel » est un détail de style. */
    val important: Boolean get() = tags.any { it != TagNatural }

    companion object {
        const val TagGrammar = "grammaire"
        const val TagSpelling = "orthographe"
        const val TagNatural = "naturel"
    }
}

/** Une proposition d'appel, ou son acceptation (alors [acceptedAt] est rempli et le message répond à la proposition). */
@Serializable
data class CallPayload(
    val at: String? = null,
    val minutes: Int = 15,
    @SerialName("accepted_at") val acceptedAt: String? = null,
) {
    val time: Instant? get() = (acceptedAt ?: at)?.let(::parseTimestamp)
}

/** La durée d'un vocal, pour l'afficher avant de le télécharger. */
@Serializable
data class VoicePayload(@SerialName("duration_ms") val durationMs: Long = 0)

fun ChatMessage.correction(): CorrectionPayload? =
    if (kind == MessageKind.Correction) decode(CorrectionPayload.serializer(), body) else null

fun ChatMessage.call(): CallPayload? =
    if (kind == MessageKind.CallProposal) decode(CallPayload.serializer(), body)?.takeIf { it.time != null } else null

fun ChatMessage.voice(): VoicePayload =
    (if (kind == MessageKind.Voice) decode(VoicePayload.serializer(), body) else null) ?: VoicePayload()

fun CorrectionPayload.encode(): String = payloadJson.encodeToString(CorrectionPayload.serializer(), this)

fun CallPayload.encode(): String = payloadJson.encodeToString(CallPayload.serializer(), this)

fun VoicePayload.encode(): String = payloadJson.encodeToString(VoicePayload.serializer(), this)

private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, body: String): T? =
    runCatching { payloadJson.decodeFromString(serializer, body) }.getOrNull()
