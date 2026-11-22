package app.kiko.notifications

import androidx.compose.runtime.Composable

/**
 * L'aperçu web ne reçoit pas de notifications : on simule seulement l'autorisation,
 * pour que l'écran de demande et les réglages se voient dans la démo.
 */
actual object PushPlatform {
    actual val name: String = "web"

    actual suspend fun permission(): PushPermission = when (LocalStore.get(Storage)) {
        "granted" -> PushPermission.Granted
        "denied" -> PushPermission.Denied
        else -> PushPermission.NotAsked
    }

    actual fun openSystemSettings() = Unit

    internal const val Storage = "web_push_preview"
}

@Composable
actual fun rememberPushPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = {
    LocalStore.set(PushPlatform.Storage, "granted")
    onResult(true)
}

actual object WidgetBridge {
    actual fun publish(snapshot: WidgetSnapshot) {
        LocalStore.set(WidgetSnapshot.Slot, snapshot.encode())
    }

    actual fun clear() {
        LocalStore.set(WidgetSnapshot.Slot, null)
    }
}

actual object LocalStore {
    actual fun get(key: String): String? = storageGet(key)

    actual fun set(key: String, value: String?) {
        if (value == null) storageRemove(key) else storageSet(key, value)
    }
}

private fun storageGet(key: String): String? =
    js("(() => { try { return window.localStorage.getItem(key); } catch (e) { return null; } })()")

private fun storageSet(key: String, value: String): Unit =
    js("(() => { try { window.localStorage.setItem(key, value); } catch (e) {} })()")

private fun storageRemove(key: String): Unit =
    js("(() => { try { window.localStorage.removeItem(key); } catch (e) {} })()")
