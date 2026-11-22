package app.kiko

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.kiko.chats.ChatCache
import app.kiko.chats.SqlChatCache
import app.kiko.db.KikoDatabase

/** Les mêmes garanties, sur les vraies requêtes SQLite du cache (Chat.sq). */
class SqlChatCacheTest : ChatCacheContract() {
    override fun newCache(): ChatCache {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KikoDatabase.Schema.create(driver)
        return SqlChatCache(driver)
    }
}
