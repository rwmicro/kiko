package app.kiko

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kiko.data.SupabaseConfig

@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            WelcomeScreen(supabaseReady = SupabaseConfig.Default.isConfigured)
        }
    }
}

@Composable
private fun WelcomeScreen(supabaseReady: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bonjour · Halo", style = MaterialTheme.typography.displaySmall)
        Text(
            "Parle avec de vraies personnes, dans leur langue.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            Languages.beta.joinToString("  ↔  ") { it.nativeName },
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = {}) { Text("Commencer") }
        Text(
            text = "${platformName()} · Supabase ${if (supabaseReady) "configuré" else "non configuré"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
