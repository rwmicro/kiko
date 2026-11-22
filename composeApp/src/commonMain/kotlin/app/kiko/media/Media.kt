package app.kiko.media

import androidx.compose.runtime.Composable
import app.kiko.chats.MediaFile
import io.github.vinceglb.filekit.PlatformFile

/** Enregistre un message vocal au micro. */
interface VoiceRecorder {
    /** Demande le micro si besoin, puis enregistre. Faux si l'accès est refusé ou impossible. */
    suspend fun start(): Boolean

    /** Arrête et rend l'enregistrement, ou null s'il n'a rien donné. */
    suspend fun stop(): MediaFile?

    /** Arrête et jette l'enregistrement en cours. */
    fun cancel()
}

/** Lit un message vocal ; un seul à la fois. */
interface VoicePlayer {
    /** [onEnd] est appelé à la fin de la lecture, ou si elle échoue. */
    fun play(bytes: ByteArray, contentType: String, onEnd: () -> Unit)
    fun stop()
}

@Composable
expect fun rememberVoiceRecorder(): VoiceRecorder

@Composable
expect fun rememberVoicePlayer(): VoicePlayer

/**
 * Prépare une photo choisie pour l'envoi : réduite (1600 px au plus) et compressée en JPEG
 * quand la plateforme sait le faire. Null si le fichier n'est pas une image utilisable.
 */
expect suspend fun prepareImage(file: PlatformFile): MediaFile?

/** Type d'un fichier audio d'après son extension, pour le relire. */
fun audioType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "m4a", "mp4", "aac" -> "audio/mp4"
    "webm" -> "audio/webm"
    "ogg" -> "audio/ogg"
    "mp3" -> "audio/mpeg"
    else -> "audio/mp4"
}

/** Une photo ne dépasse pas la limite du stockage (10 Mo). */
const val MaxMediaBytes = 10 * 1024 * 1024
