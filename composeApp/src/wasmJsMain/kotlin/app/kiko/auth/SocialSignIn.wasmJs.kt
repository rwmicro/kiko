package app.kiko.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.kiko.data.AccountService
import app.kiko.data.SignedInUser
import app.kiko.data.SocialProvider

/** Aperçu web : la page part chez Apple ou Google, puis revient ; la session est reprise au chargement. */
@Composable
actual fun rememberSocialSignIn(account: AccountService): SocialSignIn = remember(account) {
    object : SocialSignIn {
        override suspend fun signIn(provider: SocialProvider): SignedInUser? {
            account.startBrowserSignIn(provider)
            return null
        }
    }
}
