package app.kiko.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kiko.auth.rememberSocialSignIn
import app.kiko.data.AccountException
import app.kiko.data.AccountService
import app.kiko.data.SignedInUser
import app.kiko.data.SocialProvider
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoIcons
import app.kiko.ui.theme.Kiko
import kotlinx.coroutines.launch

/**
 * « Continuer avec Apple » et « Continuer avec Google », puis un séparateur avant le formulaire e-mail.
 * Apple est en premier et à égalité avec Google (règle 4.8 de l'App Store).
 *
 * [onBusy] signale le début et la fin d'une tentative ; [onError] reçoit un message à montrer tel quel.
 */
@Composable
fun SocialButtons(
    account: AccountService,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onSignedIn: (SignedInUser) -> Unit,
    onError: (String?) -> Unit,
    dividerLabel: String = "ou avec ton e-mail",
) {
    val social = rememberSocialSignIn(account)
    val scope = rememberCoroutineScope()
    fun start(provider: SocialProvider) {
        if (busy) return
        onBusy(true)
        onError(null)
        scope.launch {
            try {
                social.signIn(provider)?.let(onSignedIn)
            } catch (e: AccountException) {
                onError(e.message)
            } finally {
                onBusy(false)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KikoButton(
            "Continuer avec Apple",
            onClick = { start(SocialProvider.Apple) },
            style = KikoButtonStyle.Ink,
            icon = KikoIcons.AppleLogo,
            keepCase = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        KikoButton(
            "Continuer avec Google",
            onClick = { start(SocialProvider.Google) },
            style = KikoButtonStyle.White,
            icon = KikoIcons.GoogleLogo,
            iconTint = Color.Unspecified,
            keepCase = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(Kiko.colors.hair))
            Text(dividerLabel, style = Kiko.type.micro, color = Kiko.colors.mid)
            Box(Modifier.weight(1f).height(1.dp).background(Kiko.colors.hair))
        }
    }
}
