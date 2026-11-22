package app.kiko.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.kiko.chats.MediaFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.compressImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val context = LocalContext.current.applicationContext
    val recorder = remember(context) { AndroidVoiceRecorder(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), recorder::onPermission)
    recorder.askPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

@Composable
actual fun rememberVoicePlayer(): VoicePlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { AndroidVoicePlayer(context) }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}

actual suspend fun prepareImage(file: PlatformFile): MediaFile? = runCatching {
    val bytes = FileKit.compressImage(file, quality = 82, maxWidth = 1600, maxHeight = 1600)
    MediaFile(bytes, "image/jpeg", "jpg").takeIf { bytes.isNotEmpty() && bytes.size <= MaxMediaBytes }
}.getOrNull()

private class AndroidVoiceRecorder(private val context: Context) : VoiceRecorder {
    var askPermission: () -> Unit = {}
    private var permission: CompletableDeferred<Boolean>? = null
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun onPermission(granted: Boolean) {
        permission?.complete(granted)
    }

    override suspend fun start(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val answer = CompletableDeferred<Boolean>()
            permission = answer
            askPermission()
            if (!answer.await()) return false
        }
        cancel()
        return try {
            val out = File.createTempFile("vocal", ".m4a", context.cacheDir)
            file = out
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            recorder = r
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            true
        } catch (_: Exception) {
            cancel()
            false
        }
    }

    override suspend fun stop(): MediaFile? {
        val r = recorder ?: return null
        val out = file
        recorder = null
        file = null
        // stop() échoue si l'enregistrement est trop court pour contenir un son.
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || out == null) {
            out?.delete()
            return null
        }
        val bytes = withContext(Dispatchers.IO) { out.readBytes().also { out.delete() } }
        return MediaFile(bytes, "audio/mp4", "m4a").takeIf { bytes.isNotEmpty() }
    }

    override fun cancel() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        file?.delete()
        file = null
    }
}

private class AndroidVoicePlayer(private val context: Context) : VoicePlayer {
    private var player: MediaPlayer? = null
    private var file: File? = null

    override fun play(bytes: ByteArray, contentType: String, onEnd: () -> Unit) {
        stop()
        val extension = if (contentType.contains("webm")) ".webm" else ".m4a"
        val out = File.createTempFile("ecoute", extension, context.cacheDir)
        val p = MediaPlayer()
        try {
            out.writeBytes(bytes)
            p.setDataSource(out.absolutePath)
            p.setOnCompletionListener {
                stop()
                onEnd()
            }
            p.prepare()
            p.start()
            player = p
            file = out
        } catch (_: Exception) {
            p.release()
            out.delete()
            onEnd()
        }
    }

    override fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        file?.delete()
        file = null
    }
}
