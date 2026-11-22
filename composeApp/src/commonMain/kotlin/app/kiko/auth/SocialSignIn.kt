package app.kiko.auth

import androidx.compose.runtime.Composable
import app.kiko.data.AccountService
import app.kiko.data.SignedInUser
import app.kiko.data.SocialProvider

/**
 * Connexion avec Apple ou Google, par la fenêtre native quand la plateforme en a une :
 * - iPhone : Apple natif (Face ID), Google dans une fenêtre de connexion intégrée ;
 * - Android : Google natif (Gestionnaire d'identifiants), Apple dans un onglet du navigateur ;
 * - aperçu web : les deux par redirection.
 *
 * On ne demande au fournisseur que l'e-mail : ni nom, ni photo. Le prénom et la date de naissance
 * sont demandés dans l'app, à l'étape « Faisons connaissance ».
 */
interface SocialSignIn {
    /**
     * Le compte connecté ; null si la personne a fermé la fenêtre, ou si la connexion continue
     * dans le navigateur (elle revient alors par kiko://login, voir AccountService.signedInFromBrowser).
     * Lève AccountException avec un message à montrer tel quel.
     */
    suspend fun signIn(provider: SocialProvider): SignedInUser?
}

@Composable
expect fun rememberSocialSignIn(account: AccountService): SocialSignIn
