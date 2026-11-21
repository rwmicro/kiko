package app.kiko.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.NotificationManagerCompat

/**
 * Le contexte de l'application, donné au démarrage par androidApp (KikoApplication), et
 * le crochet qui redessine les widgets Glance (eux aussi dans androidApp).
 */
@SuppressLint("StaticFieldLeak") // C'est le contexte de l'application, pas celui d'une activité.
object KikoAndroid {
    lateinit var context: Context
    var onWidgetsChanged: ((Context) -> Unit)? = null
}

actual object PushPlatform {
    actual val name: String = "android"

    actual suspend fun permission(): PushPermission {
        val context = KikoAndroid.context
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < 33) return if (enabled) PushPermission.Granted else PushPermission.Denied
        return when {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && enabled ->
                PushPermission.Granted
            LocalStore.get(AskedKey) != null -> PushPermission.Denied
            else -> PushPermission.NotAsked
        }
    }

    actual fun openSystemSettings() {
        val context = KikoAndroid.context
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal const val AskedKey = "push_permission_asked"
}

@Composable
actual fun rememberPushPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        LocalStore.set(PushPlatform.AskedKey, "1")
        callback.value(granted)
    }
    return {
        if (Build.VERSION.SDK_INT >= 33) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            callback.value(NotificationManagerCompat.from(KikoAndroid.context).areNotificationsEnabled())
        }
    }
}

actual object WidgetBridge {
    const val Prefs = "kiko_widgets"

    actual fun publish(snapshot: WidgetSnapshot) {
        val context = KikoAndroid.context
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putString(WidgetSnapshot.Key, snapshot.encode()).apply()
        KikoAndroid.onWidgetsChanged?.invoke(context)
    }

    actual fun clear() {
        val context = KikoAndroid.context
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().clear().apply()
        KikoAndroid.onWidgetsChanged?.invoke(context)
    }

    /** Lu par les widgets et par le service FCM (qui met à jour le dernier message reçu). */
    fun read(context: Context): WidgetSnapshot? =
        WidgetSnapshot.decode(context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(WidgetSnapshot.Key, null))

    fun write(context: Context, snapshot: WidgetSnapshot) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putString(WidgetSnapshot.Key, snapshot.encode()).apply()
        KikoAndroid.onWidgetsChanged?.invoke(context)
    }
}

actual object LocalStore {
    private val prefs get() = KikoAndroid.context.getSharedPreferences("kiko", Context.MODE_PRIVATE)

    actual fun get(key: String): String? = prefs.getString(key, null)

    actual fun set(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
