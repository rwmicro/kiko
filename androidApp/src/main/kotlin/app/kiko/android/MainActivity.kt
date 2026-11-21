package app.kiko.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.kiko.App
import app.kiko.auth.handleAuthCallback

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
        setContent { App() }
    }

    // singleTop : le retour du navigateur arrive ici quand l'app est au premier plan, l'écran en cours est gardé.
    // (Pas de singleTask, qui exposerait au détournement de tâche « StrandHogg ».)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }
}
