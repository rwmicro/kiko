package app.kiko.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.kiko.App
import app.kiko.calls.Calls
import app.kiko.notifications.AppVisibility
import app.kiko.notifications.DeepLinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        AppVisibility.foreground = true
        // Par-dessus l'écran verrouillé seulement le temps d'un appel.
        if (!Calls.controller.state.value.busy) showOverLockScreen(false)
    }

    override fun onPause() {
        AppVisibility.foreground = false
        super.onPause()
    }

    /** Une notification ou un widget touché : la discussion à ouvrir, ou l'appel qui sonne. */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ActionAnswerCall, ActionShowCall -> {
                val call = intent.getStringExtra(ExtraCall) ?: return
                showOverLockScreen(true)
                if (intent.action == ActionAnswerCall) Calls.answer(call)
                return
            }
        }
        val conversation = intent?.getStringExtra(ExtraConversation) ?: return
        DeepLinks.open(conversation, intent.getStringExtra(ExtraOutbox), intent.getStringExtra(ExtraType))
    }

    private fun showOverLockScreen(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        }
    }

    companion object {
        const val ActionAnswerCall = "app.kiko.call.ANSWER"
        const val ActionShowCall = "app.kiko.call.SHOW"
        const val ExtraCall = "app.kiko.call_id"
        const val ExtraConversation = "app.kiko.conversation_id"
        const val ExtraOutbox = "app.kiko.outbox_id"
        const val ExtraType = "app.kiko.type"
    }
}
