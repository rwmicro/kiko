@file:OptIn(ExperimentalWasmJsInterop::class)

package app.kiko.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import app.kiko.chats.MediaFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.await
import kotlin.js.Promise

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val recorder = remember { WebVoiceRecorder() }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

@Composable
actual fun rememberVoicePlayer(): VoicePlayer {
    val player = remember { WebVoicePlayer() }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}

/** Le navigateur ne compresse pas : on envoie l'image telle quelle si elle est assez légère. */
actual suspend fun prepareImage(file: PlatformFile): MediaFile? {
    val type = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> return null
    }
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
    if (bytes.isEmpty() || bytes.size > MaxMediaBytes) return null
    return MediaFile(bytes, type, if (type == "image/jpeg") "jpg" else type.substringAfter('/'))
}

private class WebVoiceRecorder : VoiceRecorder {
    override suspend fun start(): Boolean = runCatching { jsStartRecording().await<JsBoolean>().toBoolean() }.getOrDefault(false)

    override suspend fun stop(): MediaFile? {
        val clip = runCatching { jsStopRecording().await<JsAny?>() }.getOrNull() ?: return null
        val type = clipType(clip)
        val bytes = toByteArray(clipBytes(clip))
        if (bytes.isEmpty()) return null
        val extension = if (type.contains("mp4")) "m4a" else if (type.contains("ogg")) "ogg" else "webm"
        return MediaFile(bytes, type.substringBefore(';'), extension)
    }

    override fun cancel() = jsCancelRecording()
}

private class WebVoicePlayer : VoicePlayer {
    private var audio: JsAny? = null

    override fun play(bytes: ByteArray, contentType: String, onEnd: () -> Unit) {
        stop()
        audio = jsPlay(toUint8Array(bytes), contentType) {
            audio = null
            onEnd()
        }
    }

    override fun stop() {
        audio?.let(::jsPause)
        audio = null
    }
}

private fun toByteArray(array: JsAny): ByteArray = ByteArray(jsLength(array)) { jsByteAt(array, it) }

private fun toUint8Array(bytes: ByteArray): JsAny {
    val array = jsNewBytes(bytes.size)
    bytes.forEachIndexed { i, b -> jsSetByte(array, i, b) }
    return array
}

// Le micro du navigateur, via MediaRecorder. On préfère l'AAC (.m4a), que les téléphones relisent.
private fun jsStartRecording(): Promise<JsBoolean> = js(
    """(async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const type = ['audio/mp4;codecs=mp4a.40.2', 'audio/mp4', 'audio/webm;codecs=opus', 'audio/webm']
                .find(t => window.MediaRecorder && MediaRecorder.isTypeSupported(t)) || '';
            const rec = new MediaRecorder(stream, type ? { mimeType: type } : {});
            const chunks = [];
            rec.ondataavailable = e => { if (e.data && e.data.size) chunks.push(e.data); };
            rec.start();
            globalThis.__kikoVoice = { rec, stream, chunks };
            return true;
        } catch (e) {
            return false;
        }
    })()""",
)

private fun jsStopRecording(): Promise<JsAny?> = js(
    """new Promise(resolve => {
        const k = globalThis.__kikoVoice;
        globalThis.__kikoVoice = null;
        if (!k) { resolve(null); return; }
        k.rec.onstop = async () => {
            k.stream.getTracks().forEach(t => t.stop());
            const type = k.rec.mimeType || 'audio/webm';
            const blob = new Blob(k.chunks, { type });
            resolve({ type, bytes: new Int8Array(await blob.arrayBuffer()) });
        };
        k.rec.stop();
    })""",
)

private fun jsCancelRecording(): Unit = js(
    """{
        const k = globalThis.__kikoVoice;
        globalThis.__kikoVoice = null;
        if (k) { try { k.rec.stop(); } catch (e) {} k.stream.getTracks().forEach(t => t.stop()); }
    }""",
)

private fun clipType(clip: JsAny): String = js("clip.type")

private fun clipBytes(clip: JsAny): JsAny = js("clip.bytes")

private fun jsLength(array: JsAny): Int = js("array.length")

private fun jsByteAt(array: JsAny, i: Int): Byte = js("array[i]")

private fun jsNewBytes(size: Int): JsAny = js("new Int8Array(size)")

private fun jsSetByte(array: JsAny, i: Int, value: Byte): Unit = js("array[i] = value")

private fun jsPlay(bytes: JsAny, type: String, onEnd: () -> Unit): JsAny = js(
    """(() => {
        const url = URL.createObjectURL(new Blob([bytes], { type }));
        const audio = new Audio(url);
        const done = () => { URL.revokeObjectURL(url); onEnd(); };
        audio.onended = done;
        audio.onerror = done;
        audio.play().catch(done);
        return audio;
    })()""",
)

private fun jsPause(audio: JsAny): Unit = js("audio.pause()")
