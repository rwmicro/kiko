package app.kiko.data

import io.github.jan.supabase.auth.AuthConfig

/** Rien de propre à iOS : Apple est natif, Google passe par une fenêtre de connexion intégrée. */
internal actual fun AuthConfig.platformAuthDefaults() = Unit
