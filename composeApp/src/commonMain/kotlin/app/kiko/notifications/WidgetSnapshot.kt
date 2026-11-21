@file:OptIn(ExperimentalTime::class)

package app.kiko.notifications

import app.kiko.chats.kindLabel
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Ce que les widgets et les activités en direct affichent, écrit par l'app à chaque mise à jour
 * de la liste des discussions. Un seul petit JSON, lu par :
 *  - les widgets Glance d'Android (androidApp, préférences « kiko_widgets ») ;
 *  - les widgets SwiftUI de l'iPhone (UserDefaults du groupe d'apps « group.app.kiko »).
 * Les heures sont en millisecondes depuis 1970 ; les noms de champs sont repris tels quels en Swift.
 */
@Serializable
data class WidgetSnapshot(
    val updatedAt: Long,
    val streak: WidgetStreak? = null,
    val partners: List<WidgetPartner> = emptyList(),
    val nextCall: WidgetCall? = null,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        const val Key = "snapshot"
        fun decode(text: String?): WidgetSnapshot? = text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** La série à deux la plus longue (maquettes 27 et 30). */
@Serializable
data class WidgetStreak(val conversationId: String, val partnerName: String, val days: Int, val waitingForMe: Boolean)

/** Un partenaire : son heure locale et son ciel se calculent dans le widget, à partir du fuseau. */
@Serializable
data class WidgetPartner(
    val conversationId: String,
    val name: String,
    val timeZone: String,
    val lastSeen: Long? = null,
    val unread: Int = 0,
    val lastMessage: String? = null,
    val lastMessageMine: Boolean = false,
    val streak: Int = 0,
)

/** Le prochain appel accepté (maquettes 25, 28, 31 et 32). */
@Serializable
data class WidgetCall(
    val callId: String,
    val conversationId: String,
    val partnerName: String,
    val partnerTimeZone: String,
    val startsAt: Long,
    val minutes: Int,
)

/** Le JSON à partir des discussions acceptées et des appels à venir. */
fun widgetSnapshot(conversations: List<Conversation>, calls: List<UpcomingCall>, now: Instant): WidgetSnapshot {
    val accepted = conversations.filter { it.status == ConversationStatus.Accepted }
    val best = accepted.filter { it.streak > 0 }.maxByOrNull { it.streak }
    val next = calls.filter { it.startsAt.toEpochMilliseconds() + it.minutes * 60_000L > now.toEpochMilliseconds() }
        .minByOrNull { it.startsAt }
    return WidgetSnapshot(
        updatedAt = now.toEpochMilliseconds(),
        streak = best?.let { WidgetStreak(it.id, it.partnerName, it.streak, waitingForMe = it.lastMessage != null && !it.lastMessageMine) },
        partners = accepted.sortedByDescending { it.lastActivity }.take(6).map {
            WidgetPartner(
                conversationId = it.id,
                name = it.partnerName,
                timeZone = it.partnerTimeZone,
                lastSeen = it.partnerLastSeen?.toEpochMilliseconds(),
                unread = it.unread,
                lastMessage = (kindLabel(it.lastMessageKind) ?: it.lastMessage)?.take(140),
                lastMessageMine = it.lastMessageMine,
                streak = it.streak,
            )
        },
        nextCall = next?.let {
            WidgetCall(it.callId, it.conversationId, it.partnerName, it.partnerTimeZone, it.startsAt.toEpochMilliseconds(), it.minutes)
        },
    )
}

/** Écrit le JSON là où les widgets le lisent, puis leur demande de se redessiner. */
expect object WidgetBridge {
    fun publish(snapshot: WidgetSnapshot)

    /** À la déconnexion : les widgets n'affichent plus rien de personnel. */
    fun clear()
}
