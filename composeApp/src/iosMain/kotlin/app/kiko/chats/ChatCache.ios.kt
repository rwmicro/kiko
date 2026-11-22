package app.kiko.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.kiko.db.KikoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Dans le dossier privé de l'app, protégé par le chiffrement de l'appareil (classe par défaut d'iOS). */
private val shared: ChatCache by lazy {
    SqlChatCache(NativeSqliteDriver(KikoDatabase.Schema, CHAT_DB_NAME), Dispatchers.IO)
}

@Composable
actual fun rememberChatCache(): ChatCache = remember { shared }
