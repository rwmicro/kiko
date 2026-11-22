package app.kiko.chats

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.kiko.db.KikoDatabase
import kotlinx.coroutines.Dispatchers

private val lock = Any()
private var shared: ChatCache? = null

/** Dans le dossier privé de l'app ; exclu des sauvegardes (allowBackup="false"). */
private fun chatCache(context: Context): ChatCache = synchronized(lock) {
    shared ?: SqlChatCache(
        AndroidSqliteDriver(KikoDatabase.Schema, context.applicationContext, CHAT_DB_NAME),
        Dispatchers.IO,
    ).also { shared = it }
}

@Composable
actual fun rememberChatCache(): ChatCache {
    val context = LocalContext.current.applicationContext
    return remember(context) { chatCache(context) }
}
