package app.kiko.chats

import androidx.compose.runtime.Composable

/**
 * Le cache des discussions de l'appareil : une base SQLite sur Android et iOS, la mémoire sur le web.
 * Une seule instance par lancement de l'app.
 */
@Composable
expect fun rememberChatCache(): ChatCache

/** Nom du fichier de la base sur l'appareil. */
internal const val CHAT_DB_NAME = "kiko-chat.db"
