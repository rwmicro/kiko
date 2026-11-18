package app.kiko.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kiko.data.AccountException
import app.kiko.data.AccountService
import app.kiko.data.EmailNotConfirmedException
import app.kiko.data.SignedInUser
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoScreenTitle
import app.kiko.ui.components.KikoStepHeader
import app.kiko.ui.components.KikoTextField
import app.kiko.ui.theme.Kiko
import kotlinx.coroutines.launch

/** Connexion « J'ai déjà un compte » : e-mail et mot de passe. */
@Composable
fun SignInScreen(
    account: AccountService,
    onBack: () -> Unit,
    onSignedIn: (SignedInUser) -> Unit,
    onCreateAccount: () -> Unit,
    onNeedsCode: (email: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = email.contains('@') && password.isNotEmpty() && !busy

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        KikoStepHeader(current = null, total = 0, onBack = onBack, label = "Connexion")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KikoScreenTitle("Content de te revoir", subtitle = "Connecte-toi avec l'e-mail et le mot de passe de ton compte.")
            KikoTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = "E-mail",
                placeholder = "toi@exemple.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            KikoTextField(
                value = password,
                onValueChange = { password = it.take(72) },
                label = "Mot de passe",
                password = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Text(it, style = Kiko.type.captionStrong, color = Kiko.colors.error, modifier = Modifier.padding(bottom = 10.dp))
        }
        KikoButton(
            if (busy) "Un instant…" else "Se connecter",
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        onSignedIn(account.signIn(email, password))
                    } catch (e: EmailNotConfirmedException) {
                        runCatching { account.resendCode(email) }
                        onNeedsCode(email)
                    } catch (e: AccountException) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        )
        KikoLinkButton("Créer un compte", { if (!busy) onCreateAccount() }, Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
    }
}
