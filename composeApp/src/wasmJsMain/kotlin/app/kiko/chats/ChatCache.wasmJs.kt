package app.kiko.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** L'aperçu web n'écrit rien dans le navigateur : le cache vit le temps de l'onglet. */
private val shared: ChatCache by lazy { MemoryChatCache() }

@Composable
actual fun rememberChatCache(): ChatCache = remember { shared }
