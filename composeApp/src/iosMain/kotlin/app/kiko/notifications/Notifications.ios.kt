package app.kiko.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Groupe d'apps partagé avec l'extension des widgets et celle des notifications. */
const val AppGroup = "group.app.kiko"

/**
 * Implémenté en Swift (iosApp/iosApp/KikoBridge.swift) : redessine les widgets et lance
 * ou met à jour l'activité en direct du prochain appel. WidgetKit et ActivityKit n'existent
 * qu'en Swift, d'où ce pont.
 */
interface NativeWidgets {
    fun snapshotChanged(json: String)
}

object IosHooks {
    var widgets: NativeWidgets? = null
}

actual object PushPlatform {
    actual val name: String = "ios"

    actual suspend fun permission(): PushPermission = suspendCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            cont.resume(
                when (status) {
                    UNAuthorizationStatusNotDetermined -> PushPermission.NotAsked
                    UNAuthorizationStatusDenied -> PushPermission.Denied
                    null -> PushPermission.NotAsked
                    else -> PushPermission.Granted
                },
            )
        }
    }

    actual fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }
}

@Composable
actual fun rememberPushPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
        ) { granted, _ ->
            dispatch_async(dispatch_get_main_queue()) {
                if (granted) UIApplication.sharedApplication.registerForRemoteNotifications()
                callback.value(granted)
            }
        }
    }
}

actual object WidgetBridge {
    private val shared: NSUserDefaults get() = NSUserDefaults(suiteName = AppGroup)

    actual fun publish(snapshot: WidgetSnapshot) {
        val json = snapshot.encode()
        shared.setObject(json, forKey = WidgetSnapshot.Key)
        IosHooks.widgets?.snapshotChanged(json)
    }

    actual fun clear() {
        shared.removeObjectForKey(WidgetSnapshot.Key)
        IosHooks.widgets?.snapshotChanged("")
    }
}

actual object LocalStore {
    actual fun get(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)

    actual fun set(key: String, value: String?) {
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
        }
    }
}
