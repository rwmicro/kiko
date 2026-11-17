package app.kiko

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kiko.data.SupabaseConfig
import app.kiko.onboarding.OnboardingFlow
import app.kiko.onboarding.OnboardingState
import app.kiko.ui.KikoTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun App() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var onboarding by remember { mutableStateOf<OnboardingState?>(null) }

    KikoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val current = onboarding
            if (current == null) {
                WelcomeScreen(
                    supabaseReady = SupabaseConfig.Default.isConfigured,
                    onStart = { onboarding = OnboardingState() },
                )
            } else {
                OnboardingFlow(
                    state = current,
                    today = today,
                    onChange = { onboarding = it },
                    onExit = { onboarding = null },
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(supabaseReady: Boolean, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bonjour · Halo", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Parle avec de vraies personnes, dans leur langue.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "Tu apprends sa langue, elle apprend la tienne. Et c'est toi qui choisis qui peut t'écrire.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Text(
            Languages.beta.joinToString("  ·  ") { it.nativeName },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(52.dp)) {
            Text("Commencer")
        }
        TextButton(onClick = onStart) { Text("J'ai déjà un compte") }
        Text(
            text = "${platformName()} · Supabase ${if (supabaseReady) "configuré" else "non configuré"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
