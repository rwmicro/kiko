package app.kiko.data

import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.ExternalAuthAction

/** Connexion Apple dans un onglet personnalisé (Custom Tabs) plutôt que dans le navigateur à part. */
internal actual fun AuthConfig.platformAuthDefaults() {
    defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
}
