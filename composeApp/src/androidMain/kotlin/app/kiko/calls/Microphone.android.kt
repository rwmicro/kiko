package app.kiko.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred

@Composable
actual fun rememberMicrophoneAccess(): suspend () -> Boolean {
    val context = LocalContext.current.applicationContext
    val pending = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.complete(granted)
    }
    return remember(context) {
        suspend {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                val answer = CompletableDeferred<Boolean>()
                pending[0] = answer
                launcher.launch(Manifest.permission.RECORD_AUDIO)
                answer.await()
            }
        }
    }
}
