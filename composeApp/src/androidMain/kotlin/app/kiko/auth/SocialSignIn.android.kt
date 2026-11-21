package app.kiko.auth

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.kiko.data.AccountException
import app.kiko.data.AccountService
import app.kiko.data.BuildEnv
import app.kiko.data.SignedInUser
import app.kiko.data.SocialProvider
import app.kiko.data.SupabaseProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.github.jan.supabase.auth.handleDeeplinks
import java.security.MessageDigest
import java.security.SecureRandom

@Composable
actual fun rememberSocialSignIn(account: AccountService): SocialSignIn {
    val context = LocalContext.current
    return remember(account, context) { AndroidSocialSignIn(context, account) }
}

private class AndroidSocialSignIn(private val context: Context, private val account: AccountService) : SocialSignIn {

    override suspend fun signIn(provider: SocialProvider): SignedInUser? = when {
        provider == SocialProvider.Google && BuildEnv.GOOGLE_WEB_CLIENT_ID.isNotBlank() -> google()
        else -> {
            // Apple n'a pas de fenêtre native sur Android : onglet du navigateur, retour par kiko://login.
            account.startBrowserSignIn(provider)
            null
        }
    }

    private suspend fun google(): SignedInUser? {
        val rawNonce = randomNonce()
        val option = GetSignInWithGoogleOption.Builder(BuildEnv.GOOGLE_WEB_CLIENT_ID)
            .setNonce(sha256Hex(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            return null
        } catch (e: NoCredentialException) {
            throw AccountException("Aucun compte Google sur ce téléphone. Ajoute-en un dans les réglages, ou utilise ton e-mail.", e)
        } catch (e: GetCredentialException) {
            throw AccountException("La connexion avec Google n'a pas abouti. Réessaie.", e)
        }
        val credential = response.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AccountException("Google n'a pas renvoyé d'identifiant. Réessaie.")
        }
        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw AccountException("Google n'a pas renvoyé d'identifiant valide. Réessaie.", e)
        }
        return account.signInWithIdToken(SocialProvider.Google, idToken, rawNonce)
    }
}

/**
 * À appeler par l'activité (onCreate et onNewIntent) : termine une connexion Apple ou Google
 * faite dans le navigateur, quand celui-ci renvoie vers kiko://login?code=…
 */
fun handleAuthCallback(intent: Intent?) {
    if (intent?.data?.scheme != app.kiko.data.AuthCallbackScheme) return
    runCatching { SupabaseProvider.shared() }.getOrNull()?.handleDeeplinks(intent)
}

/** Nonce à usage unique : Google l'inscrit (haché) dans le jeton, Supabase vérifie qu'il correspond. */
private fun randomNonce(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
