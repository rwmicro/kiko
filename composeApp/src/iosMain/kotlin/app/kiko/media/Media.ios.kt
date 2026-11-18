@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.kiko.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import app.kiko.chats.MediaFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.compressImage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val recorder = remember { IosVoiceRecorder() }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

@Composable
actual fun rememberVoicePlayer(): VoicePlayer {
    val player = remember { IosVoicePlayer() }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}

actual suspend fun prepareImage(file: PlatformFile): MediaFile? = runCatching {
    val bytes = FileKit.compressImage(file, quality = 82, maxWidth = 1600, maxHeight = 1600)
    MediaFile(bytes, "image/jpeg", "jpg").takeIf { bytes.isNotEmpty() && bytes.size <= MaxMediaBytes }
}.getOrNull()

private class IosVoiceRecorder : VoiceRecorder {
    private var recorder: AVAudioRecorder? = null
    private var url: NSURL? = null

    override suspend fun start(): Boolean {
        val session = AVAudioSession.sharedInstance()
        val granted = suspendCancellableCoroutine { cont ->
            session.requestRecordPermission { ok -> if (cont.isActive) cont.resume(ok) }
        }
        if (!granted) return false
        cancel()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, null)
        val out = NSURL.fileURLWithPath(NSTemporaryDirectory() + "vocal-" + NSUUID().UUIDString + ".m4a")
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
            AVSampleRateKey to NSNumber(double = 44_100.0),
            AVNumberOfChannelsKey to NSNumber(int = 1),
            AVEncoderBitRateKey to NSNumber(int = 64_000),
        )
        val r = AVAudioRecorder(uRL = out, settings = settings, error = null)
        if (!r.record()) return false
        recorder = r
        url = out
        return true
    }

    override suspend fun stop(): MediaFile? {
        val r = recorder ?: return null
        val out = url
        recorder = null
        url = null
        r.stop()
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        if (out == null) return null
        val data = NSData.dataWithContentsOfURL(out)
        NSFileManager.defaultManager.removeItemAtURL(out, null)
        val bytes = data?.toByteArray() ?: return null
        return MediaFile(bytes, "audio/mp4", "m4a").takeIf { bytes.isNotEmpty() }
    }

    override fun cancel() {
        recorder?.let {
            it.stop()
            it.deleteRecording()
        }
        recorder = null
        url = null
    }
}

private class IosVoicePlayer : VoicePlayer {
    private var player: AVAudioPlayer? = null
    private var delegate: Ending? = null

    override fun play(bytes: ByteArray, contentType: String, onEnd: () -> Unit) {
        stop()
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        val p = AVAudioPlayer(data = bytes.toNSData(), error = null)
        val ending = Ending {
            stop()
            onEnd()
        }
        p.delegate = ending
        if (!p.play()) {
            onEnd()
            return
        }
        player = p
        delegate = ending
    }

    override fun stop() {
        player?.stop()
        player = null
        delegate = null
    }
}

private class Ending(private val onEnd: () -> Unit) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) = onEnd()
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
