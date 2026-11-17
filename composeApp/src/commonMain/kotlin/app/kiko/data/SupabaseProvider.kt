package app.kiko.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Configuration du projet Supabase. La clé « anon » est publique par nature :
 * la sécurité repose sur les politiques Row Level Security (voir supabase/migrations).
 */
data class SupabaseConfig(val url: String, val anonKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()

    companion object {
        /** À remplir quand le projet Supabase existe (voir README). */
        val Default = SupabaseConfig(url = "", anonKey = "")
    }
}

object SupabaseProvider {
    fun create(config: SupabaseConfig): SupabaseClient {
        require(config.isConfigured) { "Supabase n'est pas configuré : renseigne l'URL et la clé anon." }
        return createSupabaseClient(supabaseUrl = config.url, supabaseKey = config.anonKey) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
