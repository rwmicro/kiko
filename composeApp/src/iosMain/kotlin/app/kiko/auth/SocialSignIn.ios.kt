@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalUnsignedTypes::class)

package app.kiko.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.kiko.data.AccountException
import app.kiko.data.AccountService
import app.kiko.data.AUTH_CALLBACK_SCHEME
import app.kiko.data.SignedInUser
import app.kiko.data.SocialProvider
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberSocialSignIn(account: AccountService): SocialSignIn = remember(account) { IosSocialSignIn(account) }

private class IosSocialSignIn(private val account: AccountService) : SocialSignIn {
    // AuthenticationServices ne garde que des références faibles : on les tient le temps de la connexion.
    private var appleController: ASAuthorizationController? = null
    private var appleDelegate: AppleDelegate? = null
    private var webSession: ASWebAuthenticationSession? = null
    private val webAnchor = WebAnchor()

    override suspend fun signIn(provider: SocialProvider): SignedInUser? = when (provider) {
        SocialProvider.Apple -> apple()
        SocialProvider.Google -> inAppBrowser(provider)
    }

    /** Fenêtre « Se connecter avec Apple » : seul l'e-mail est demandé, pas le nom. */
    private suspend fun apple(): SignedInUser? {
        val rawNonce = randomNonce()
        val idToken = suspendCancellableCoroutine<String?> { cont ->
            val request = ASAuthorizationAppleIDProvider().createRequest().apply {
                requestedScopes = listOf(ASAuthorizationScopeEmail)
                nonce = sha256Hex(rawNonce)
            }
            val delegate = AppleDelegate { result ->
                appleController = null
                appleDelegate = null
                if (cont.isActive) cont.resumeWith(result)
            }
            val controller = ASAuthorizationController(listOf(request))
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            appleDelegate = delegate
            appleController = controller
            controller.performRequests()
        } ?: return null
        return account.signInWithIdToken(SocialProvider.Apple, idToken, rawNonce)
    }

    /** Page de connexion du fournisseur dans une fenêtre intégrée, qui se ferme sur kiko://login?code=… */
    private suspend fun inAppBrowser(provider: SocialProvider): SignedInUser? {
        val url = NSURL(string = account.browserSignInUrl(provider))
        val callback = suspendCancellableCoroutine<NSURL?> { cont ->
            val onDone = { callbackUrl: NSURL?, error: NSError? ->
                webSession = null
                if (cont.isActive) {
                    when {
                        callbackUrl != null -> cont.resume(callbackUrl)
                        // ASWebAuthenticationSessionErrorCodeCanceledLogin (1) : la personne a fermé la fenêtre.
                        error == null || error.code == 1L -> cont.resume(null)
                        else -> cont.resumeWithException(AccountException(failed(provider)))
                    }
                }
            }
            val session = ASWebAuthenticationSession(url, AUTH_CALLBACK_SCHEME, onDone)
            session.presentationContextProvider = webAnchor
            webSession = session
            cont.invokeOnCancellation { session.cancel() }
            if (!session.start()) {
                webSession = null
                cont.resumeWithException(AccountException("Impossible d'ouvrir la connexion avec ${provider.label}."))
            }
        } ?: return null
        val params = NSURLComponents(callback, false).queryItems.orEmpty()
            .mapNotNull { it as? NSURLQueryItem }
            .associate { it.name to it.value }
        params["error_description"]?.let { throw AccountException("${provider.label} a refusé la connexion : $it") }
        val code = params["code"] ?: throw AccountException(failed(provider))
        return account.completeBrowserSignIn(code)
    }
}

private fun failed(provider: SocialProvider) = "La connexion avec ${provider.label} n'a pas abouti. Réessaie."

private class AppleDelegate(
    private val onResult: (Result<String?>) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val token = credential?.identityToken
            ?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
        onResult(
            if (token != null) Result.success(token)
            else Result.failure(AccountException("Apple n'a pas renvoyé d'identifiant. Réessaie.")),
        )
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        // ASAuthorizationErrorCanceled (1001) : la personne a fermé la fenêtre.
        onResult(
            if (didCompleteWithError.code == 1001L) Result.success(null)
            else Result.failure(AccountException("La connexion avec Apple n'a pas abouti. Réessaie.")),
        )
    }

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController,
    ): ASPresentationAnchor = keyWindow()
}

private class WebAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor = keyWindow()
}

private fun keyWindow(): UIWindow? = UIApplication.sharedApplication.connectedScenes
    .mapNotNull { it as? UIWindowScene }
    .flatMap { scene -> scene.windows.mapNotNull { it as? UIWindow } }
    .let { windows -> windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() }

/** Nonce à usage unique : Apple l'inscrit (haché) dans le jeton, Supabase vérifie qu'il correspond. */
private fun randomNonce(): String = NSUUID().UUIDString + NSUUID().UUIDString

private fun sha256Hex(value: String): String {
    val data = value.encodeToByteArray()
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { input ->
        digest.usePinned { output ->
            CC_SHA256(input.addressOf(0), data.size.convert(), output.addressOf(0))
        }
    }
    return digest.joinToString("") { it.toString(16).padStart(2, '0') }
}
