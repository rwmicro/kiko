package app.kiko.android

import android.app.Application
import android.util.Log
import app.kiko.android.push.KikoNotifier
import app.kiko.android.widgets.KikoWidgets
import app.kiko.notifications.KikoAndroid
import app.kiko.notifications.PushRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

class KikoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KikoAndroid.context = this
        KikoAndroid.onWidgetsChanged = KikoWidgets::refreshAll
        KikoNotifier.createChannels(this)
        startFirebase()
    }

    /**
     * Firebase est lancé à la main, avec les identifiants de firebase.properties (pas de plugin
     * google-services) : sans eux, l'app tourne normalement, sans notifications.
     */
    private fun startFirebase() {
        if (BuildConfig.FIREBASE_APP_ID.isBlank() || BuildConfig.FIREBASE_PROJECT_ID.isBlank()) return
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(
                    this,
                    FirebaseOptions.Builder()
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build(),
                )
            }
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                PushRegistry.onToken(token, "android", "alert", null, false)
            }
        }.onFailure { Log.w("Kiko", "Firebase indisponible : ${it.message}") }
    }
}
