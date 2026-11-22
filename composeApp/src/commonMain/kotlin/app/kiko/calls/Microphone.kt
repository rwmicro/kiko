package app.kiko.calls

import androidx.compose.runtime.Composable

/**
 * Demande le micro avant d'appeler (une seule fois : ensuite le système se souvient).
 * Rend vrai si l'app peut s'en servir.
 */
@Composable
expect fun rememberMicrophoneAccess(): suspend () -> Boolean
