@file:OptIn(ExperimentalTime::class)

package app.kiko.notifications

import app.kiko.data.AccountException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Ce que la personne veut recevoir (réglages, onglet Moi). */
data class NotificationPrefs(val messages: Boolean = true, val reminders: Boolean = true)

/** Un appel accepté, à venir ou en cours : widgets et activités en direct. */
data class UpcomingCall(
    val callId: String,
    val conversationId: String,
    val partnerName: String,
    val partnerTimeZone: String,
    val startsAt: Instant,
    val minutes: Int,
)

/** Le côté serveur des notifications (voir supabase/migrations/20261121000000_notifications.sql). */
interface NotificationService {
    /** Enregistre le jeton d'un téléphone, ou celui d'une activité en direct de l'iPhone. */
    suspend fun registerDevice(token: String, platform: String, kind: String = "alert", activity: String? = null, sandbox: Boolean = false)
    suspend fun unregisterDevice(token: String)
    suspend fun prefs(): NotificationPrefs
    suspend fun setPrefs(prefs: NotificationPrefs)
    suspend fun upcomingCalls(): List<UpcomingCall>
}

class SupabaseNotificationService(private val client: SupabaseClient) : NotificationService {
    override suspend fun registerDevice(token: String, platform: String, kind: String, activity: String?, sandbox: Boolean) = guard {
        client.postgrest.rpc(
            "register_push_device",
            buildJsonObject {
                put("p_token", token)
                put("p_platform", platform)
                put("p_kind", kind)
                put("p_activity", activity)
                put("p_sandbox", sandbox)
            },
        )
        Unit
    }

    override suspend fun unregisterDevice(token: String) = guard {
        client.postgrest.rpc("unregister_push_device", buildJsonObject { put("p_token", token) })
        Unit
    }

    override suspend fun prefs(): NotificationPrefs = guard {
        client.postgrest.rpc("my_notification_prefs").decodeList<PrefsRow>().firstOrNull()
            ?.let { NotificationPrefs(it.messages, it.reminders) } ?: NotificationPrefs()
    }

    override suspend fun setPrefs(prefs: NotificationPrefs) = guard {
        client.postgrest.rpc(
            "set_notification_prefs",
            buildJsonObject {
                put("p_messages", prefs.messages)
                put("p_reminders", prefs.reminders)
            },
        )
        Unit
    }

    override suspend fun upcomingCalls(): List<UpcomingCall> = guard {
        client.postgrest.rpc("my_upcoming_calls").decodeList<CallRow>().map {
            UpcomingCall(it.callId, it.conversationId, it.partnerName, it.partnerTimeZone ?: "UTC", Instant.parse(it.startsAt), it.minutes)
        }
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AccountException("Les notifications n'ont pas pu être réglées. Vérifie ta connexion.", e)
    }
}

@Serializable
private data class PrefsRow(val messages: Boolean = true, val reminders: Boolean = true)

@Serializable
private data class CallRow(
    @SerialName("call_id") val callId: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("partner_first_name") val partnerName: String,
    @SerialName("partner_time_zone") val partnerTimeZone: String? = null,
    @SerialName("starts_at") val startsAt: String,
    val minutes: Int = 15,
)

/** La démo sans compte : Ayu propose un appel dans 40 minutes, les réglages restent en mémoire. */
class DemoNotificationService : NotificationService {
    private var current = NotificationPrefs()
    private val call = UpcomingCall("demo-call", "ayu", "Ayu", "Asia/Jakarta", Clock.System.now() + 40.minutes, 15)

    override suspend fun registerDevice(token: String, platform: String, kind: String, activity: String?, sandbox: Boolean) = Unit
    override suspend fun unregisterDevice(token: String) = Unit
    override suspend fun prefs(): NotificationPrefs = current

    override suspend fun setPrefs(prefs: NotificationPrefs) {
        delay(200)
        current = prefs
    }

    override suspend fun upcomingCalls(): List<UpcomingCall> = listOf(call)
}
