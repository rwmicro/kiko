@file:OptIn(ExperimentalTime::class)

package app.kiko.notifications

import app.kiko.data.ChatMessage
import app.kiko.data.SupabaseChatService
import app.kiko.data.SupabaseProvider
import app.kiko.data.newMessageId
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Les boutons des notifications, sans ouvrir l'app (maquettes 23 et 26) : répondre, marquer comme
 * lu, remercier pour une correction. Appelées par le récepteur Android ou l'AppDelegate iOS.
 * Rendent faux si l'envoi n'a pas pu se faire (pas de session, pas de réseau).
 */
object NotificationActions {
    private val chats by lazy { SupabaseChatService(SupabaseProvider.shared()) }

    suspend fun reply(conversationId: String, text: String): Boolean = act {
        val body = text.trim()
        if (body.isEmpty()) return@act
        chats.send(ChatMessage(newMessageId(), conversationId, chats.myId, body, Clock.System.now()))
        chats.markRead(conversationId)
    }

    suspend fun markRead(conversationId: String): Boolean = act { chats.markRead(conversationId) }

    suspend fun thank(conversationId: String): Boolean = act {
        chats.send(ChatMessage(newMessageId(), conversationId, chats.myId, "Merci pour la correction !", Clock.System.now()))
    }

    private suspend fun act(block: suspend () -> Unit): Boolean = runCatching {
        val auth = SupabaseProvider.shared().auth
        val status = withTimeoutOrNull(10_000) { auth.sessionStatus.first { it !is SessionStatus.Initializing } }
        if (status !is SessionStatus.Authenticated) return false
        block()
        true
    }.getOrDefault(false)
}
