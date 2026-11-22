package app.kiko.calls

import androidx.compose.runtime.Composable

/** L'aperçu web ne passe pas d'appels. */
@Composable
actual fun rememberMicrophoneAccess(): suspend () -> Boolean = { false }
