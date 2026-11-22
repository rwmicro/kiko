package app.kiko.notifications

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** Où en est l'autorisation d'afficher des notifications sur ce téléphone. */
enum class PushPermission { NotAsked, Granted, Denied, Unsupported }

/** Ce que chaque plateforme sait faire : Android, iPhone, ou rien (aperçu web). */
expect object PushPlatform {
    /** « android », « ios » ou « web » : la colonne platform de push_devices. */
    val name: String
    suspend fun permission(): PushPermission

    /** Ouvre les réglages de notification de l'app, quand la personne a refusé. */
    fun openSystemSettings()
}

/** Montre la fenêtre du système ; [onResult] reçoit la réponse. */
@Composable
expect fun rememberPushPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

/** Un jeton reçu du système (FCM, APNs, ou ActivityKit). */
data class DeviceToken(val token: String, val platform: String, val kind: String, val activity: String?, val sandbox: Boolean)

/**
 * Le pont entre le système et le serveur. Les jetons arrivent du code natif (service FCM,
 * AppDelegate) à n'importe quel moment, parfois avant la connexion : on les garde, puis on les
 * enregistre dès qu'une personne est connectée.
 */
object PushRegistry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val tokens = linkedMapOf<String, DeviceToken>()
    private var service: NotificationService? = null

    /** Appelée depuis Kotlin (Android) ou Swift (iPhone). */
    fun onToken(token: String, platform: String, kind: String, activity: String?, sandbox: Boolean) {
        val device = DeviceToken(token, platform, kind, activity, sandbox)
        scope.launch {
            val target = lock.withLock {
                tokens[token] = device
                service
            }
            target?.let { register(it, device) }
        }
    }

    /** Un jeton d'activité terminée ne sert plus. */
    fun forget(token: String) {
        scope.launch { lock.withLock { tokens.remove(token) } }
    }

    fun attach(target: NotificationService) {
        scope.launch {
            val all = lock.withLock {
                service = target
                tokens.values.toList()
            }
            all.forEach { register(target, it) }
        }
    }

    /** À la déconnexion : ce téléphone ne reçoit plus rien pour ce compte. */
    suspend fun signOut() {
        val (target, all) = lock.withLock { (service to tokens.values.toList()).also { service = null } }
        target ?: return
        all.forEach { runCatching { target.unregisterDevice(it.token) } }
    }

    /** Compte supprimé : le serveur a déjà effacé les appareils, on cesse seulement de les envoyer. */
    suspend fun detach() {
        lock.withLock { service = null }
    }

    private suspend fun register(target: NotificationService, device: DeviceToken) {
        runCatching { target.registerDevice(device.token, device.platform, device.kind, device.activity, device.sandbox) }
    }
}

/** Une notification touchée : la discussion à ouvrir. Rien n'est mesuré ni renvoyé au serveur. */
data class NotificationTap(val conversationId: String?, val outboxId: Long?, val type: String?)

object DeepLinks {
    private val pending = MutableStateFlow<NotificationTap?>(null)
    val taps: StateFlow<NotificationTap?> = pending

    /** Appelée depuis Kotlin (Android) ou Swift (iPhone) quand une notification est touchée. */
    fun open(conversationId: String?, outboxId: String?, type: String?) {
        pending.value = NotificationTap(conversationId?.ifBlank { null }, outboxId?.toLongOrNull(), type)
    }

    fun consume() {
        pending.value = null
    }
}

/** L'app est-elle à l'écran ? Les notifications de message se taisent alors (la liste suffit). */
object AppVisibility {
    @Volatile var foreground: Boolean = false
    @Volatile var openConversation: String? = null
}
