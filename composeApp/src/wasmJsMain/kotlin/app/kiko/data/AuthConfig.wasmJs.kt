package app.kiko.data

import io.github.jan.supabase.auth.AuthConfig

/** Aperçu web : le retour se fait sur l'adresse de la page (kiko://login ne sert que sur mobile). */
internal actual fun AuthConfig.platformAuthDefaults() = Unit
