package app.kiko.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.kiko.App
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
    }

    override fun onPause() {
        AppVisibility.foreground = false
        super.onPause()
    }

    /** Une notification ou un widget touché : la discussion à ouvrir. */
    private fun handle(intent: Intent?) {
        val conversation = intent?.getStringExtra(ExtraConversation) ?: return
        DeepLinks.open(conversation, intent.getStringExtra(ExtraOutbox), intent.getStringExtra(ExtraType))
    }

    companion object {
        const val ExtraConversation = "app.kiko.conversation_id"
        const val ExtraOutbox = "app.kiko.outbox_id"
        const val ExtraType = "app.kiko.type"
    }
}
