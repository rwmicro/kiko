package app.kiko.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import kotlin.coroutines.resume

/** iOS ne montre la question qu'une fois ; ensuite la réponse revient tout de suite. */
@Composable
actual fun rememberMicrophoneAccess(): suspend () -> Boolean = remember {
    suspend {
        suspendCancellableCoroutine { cont ->
            AVAudioSession.sharedInstance().requestRecordPermission { granted -> if (cont.isActive) cont.resume(granted) }
        }
    }
}
