package app.kiko.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.FlowType
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
        /**
         * Projet Supabase de l'environnement visé par ce build (test par défaut, production en CI de prod).
         * Les valeurs viennent de gradle.properties via la tâche generateBuildEnv.
         * Clé publiable, faite pour être embarquée.
         */
        val Default = SupabaseConfig(
            url = BuildEnv.SUPABASE_URL,
            anonKey = BuildEnv.SUPABASE_PUBLISHABLE_KEY,
        )
    }
}

/**
 * Lien de retour après une connexion Apple ou Google faite dans le navigateur : kiko://login.
 * À ajouter dans Supabase > Authentication > URL Configuration > Redirect URLs.
 */
const val AuthCallbackScheme = "kiko"
const val AuthCallbackHost = "login"
const val AuthCallbackUrl = "$AuthCallbackScheme://$AuthCallbackHost"

/** Réglages d'authentification propres à la plateforme (onglet personnalisé sur Android…). */
internal expect fun AuthConfig.platformAuthDefaults()

object SupabaseProvider {
    private var shared: SupabaseClient? = null

    /**
     * Le client de l'app, un seul par processus : l'interface et le retour d'une connexion
     * faite dans le navigateur (lien kiko://login reçu par l'activité Android) partagent la même session.
     */
    fun shared(): SupabaseClient = shared ?: create(SupabaseConfig.Default).also { shared = it }

    fun create(config: SupabaseConfig): SupabaseClient {
        require(config.isConfigured) { "Supabase n'est pas configuré : renseigne l'URL et la clé anon." }
        return createSupabaseClient(supabaseUrl = config.url, supabaseKey = config.anonKey) {
            install(Auth) {
                // PKCE : le navigateur ne renvoie qu'un code à usage unique, jamais les jetons eux-mêmes.
                flowType = FlowType.PKCE
                scheme = AuthCallbackScheme
                host = AuthCallbackHost
                platformAuthDefaults()
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
