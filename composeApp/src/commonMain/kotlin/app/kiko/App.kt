package app.kiko

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.kiko.data.SupabaseConfig
import app.kiko.onboarding.OnboardingFlow
import app.kiko.onboarding.OnboardingState
import app.kiko.ui.catalog.DesignCatalog
import app.kiko.ui.components.KikoAvatarPair
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoTag
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoTheme
import app.kiko.ui.theme.KikoType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun App() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var onboarding by remember { mutableStateOf<OnboardingState?>(null) }
    var showCatalog by remember { mutableStateOf(false) }

    KikoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val current = onboarding
            when {
                showCatalog -> DesignCatalog(onClose = { showCatalog = false }, startDark = Kiko.colors.isDark)
                current == null -> WelcomeScreen(
                    supabaseReady = SupabaseConfig.Default.isConfigured,
                    onStart = { onboarding = OnboardingState() },
                    onCatalog = { showCatalog = true },
                )
                else -> OnboardingFlow(
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
private fun WelcomeScreen(supabaseReady: Boolean, onStart: () -> Unit, onCatalog: () -> Unit) {
    val c = Kiko.colors
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            KikoAvatarPair("Martin", "Ayu", size = 72.dp)
            Text(
                buildAnnotatedString {
                    append("Parle avec de vraies personnes, ")
                    withStyle(SpanStyle(color = c.primary)) { append("dans leur langue.") }
                },
                style = KikoType.display,
                color = c.ink,
            )
            Text(
                "Tu apprends sa langue, elle apprend la tienne. Et c'est toi qui choisis qui peut t'écrire.",
                style = KikoType.body,
                color = c.mid,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Languages.beta.forEach { KikoTag(it.nativeName) }
            }
        }
        Column(
            Modifier.fillMaxWidth().widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KikoButton("Commencer", onStart, Modifier.fillMaxWidth())
            KikoButton("J'ai déjà un compte", onStart, Modifier.fillMaxWidth(), style = KikoButtonStyle.White)
            KikoLinkButton("Voir le design system", onCatalog)
            Text(
                text = "${platformName()} · Supabase ${if (supabaseReady) "configuré" else "non configuré"}",
                style = KikoType.micro,
                color = c.soft,
                textAlign = TextAlign.Center,
            )
        }
    }
}
