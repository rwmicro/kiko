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
import androidx.compose.runtime.LaunchedEffect
import app.kiko.data.AccountException
import app.kiko.data.AccountService
import app.kiko.data.SignedInUser
import app.kiko.data.SupabaseAccountService
import app.kiko.data.SupabaseProvider
import app.kiko.main.MainScreen
import app.kiko.data.PartnerService
import app.kiko.data.DemoPartnerService
import app.kiko.data.DemoChatService
import app.kiko.data.ChatService
import app.kiko.data.SupabaseChatService
import app.kiko.data.SupabasePartnerService
import app.kiko.notifications.DemoNotificationService
import app.kiko.notifications.NotificationService
import app.kiko.notifications.SupabaseNotificationService
import app.kiko.onboarding.OnboardingStep
import app.kiko.onboarding.SignInScreen
import app.kiko.onboarding.resumeFor
import app.kiko.ui.components.KikoMascot
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Écran affiché par l'app. */
private sealed interface Screen {
    data object Loading : Screen
    data object Welcome : Screen
    data object SignIn : Screen
    data object Catalog : Screen
    /** L'app avec des partenaires d'exemple, sans compte ni serveur. */
    data object Demo : Screen
    data class Onboarding(val state: OnboardingState) : Screen
    data class Home(val firstName: String) : Screen
}

@OptIn(ExperimentalTime::class)
@Composable
fun App() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val client = remember { runCatching { SupabaseProvider.shared() }.getOrNull() }
    val account: AccountService? = remember(client) { client?.let(::SupabaseAccountService) }
    val partners: PartnerService? = remember(client) { client?.let(::SupabasePartnerService) }
    val chats: ChatService? = remember(client) { client?.let(::SupabaseChatService) }
    val notifications: NotificationService? = remember(client) { client?.let(::SupabaseNotificationService) }
    var screen by remember { mutableStateOf<Screen>(if (account == null) Screen.Welcome else Screen.Loading) }
    val scope = rememberCoroutineScope()
    var beforeCatalog by remember { mutableStateOf<Screen>(Screen.Welcome) }

    // Une personne déjà connectée retrouve son accueil, ou reprend l'inscription là où elle l'a laissée.
    LaunchedEffect(account) {
        if (account == null) return@LaunchedEffect
        val user = runCatching { account.restore() }.getOrNull()
        screen = screenFor(user)
    }

    KikoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val current = screen) {
                Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KikoMascot(size = 96.dp)
                }
                Screen.Catalog -> DesignCatalog(onClose = { screen = beforeCatalog }, startDark = Kiko.colors.isDark)
                Screen.Welcome -> WelcomeScreen(
                    supabaseReady = account != null,
                    onStart = { screen = Screen.Onboarding(OnboardingState()) },
                    onSignIn = { screen = Screen.SignIn },
                    onCatalog = { beforeCatalog = screen; screen = Screen.Catalog },
                    onDemo = { screen = Screen.Demo },
                )
                Screen.SignIn -> if (account != null) {
                    SignInScreen(
                        account = account,
                        onBack = { screen = Screen.Welcome },
                        onSignedIn = { screen = screenFor(it) },
                        onCreateAccount = { screen = Screen.Onboarding(OnboardingState()) },
                        onNeedsCode = { email -> screen = Screen.Onboarding(OnboardingState(step = OnboardingStep.EmailCode, email = email)) },
                    )
                }
                is Screen.Onboarding -> if (account != null) {
                    OnboardingFlow(
                        state = current.state,
                        today = today,
                        account = account,
                        onChange = { screen = if (it.step == OnboardingStep.Done) Screen.Home(it.firstName.trim()) else Screen.Onboarding(it) },
                        onExit = { screen = Screen.Welcome },
                        onSignIn = { screen = Screen.SignIn },
                    )
                }
                Screen.Demo -> MainScreen(
                    firstName = "Martin",
                    partners = remember { DemoPartnerService() },
                    chats = remember { DemoChatService() },
                    notifications = remember { DemoNotificationService() },
                    onSignOut = { screen = Screen.Welcome },
                    onDeleteAccount = {},
                    startOnPartners = true,
                )
                is Screen.Home -> if (partners != null && chats != null && notifications != null) {
                    MainScreen(
                        firstName = current.firstName,
                        partners = partners,
                        chats = chats,
                        notifications = notifications,
                        onSignOut = {
                            screen = Screen.Welcome
                            account?.let { a -> scope.launch { runCatching { a.signOut() } } }
                        },
                        onDeleteAccount = {
                            account?.deleteAccount() ?: throw AccountException("Connexion au serveur impossible.")
                        },
                    )
                }
            }
        }
    }
}

private fun screenFor(user: SignedInUser?): Screen = when {
    user == null -> Screen.Welcome
    user.firstName != null -> Screen.Home(user.firstName)
    else -> Screen.Onboarding(OnboardingState().resumeFor(user))
}

@Composable
private fun WelcomeScreen(supabaseReady: Boolean, onStart: () -> Unit, onSignIn: () -> Unit, onCatalog: () -> Unit, onDemo: () -> Unit) {
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
                style = Kiko.type.display,
                color = c.ink,
            )
            Text(
                "Tu apprends sa langue, elle apprend la tienne. Et c'est toi qui choisis qui peut t'écrire.",
                style = Kiko.type.body,
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
            KikoButton("Commencer", onStart, Modifier.fillMaxWidth(), enabled = supabaseReady)
            KikoLinkButton("J'ai déjà un compte", onSignIn)
            KikoLinkButton("Voir la démo sans compte", onDemo, color = c.mid)
            KikoLinkButton("Voir le design system", onCatalog, color = c.mid)
            Text(
                text = "${platformName()} · Supabase ${if (supabaseReady) "configuré" else "non configuré"}",
                style = Kiko.type.micro,
                color = c.soft,
                textAlign = TextAlign.Center,
            )
        }
    }
}
