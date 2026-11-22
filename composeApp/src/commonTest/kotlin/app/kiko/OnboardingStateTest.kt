package app.kiko

import app.kiko.onboarding.OnboardingState
import app.kiko.onboarding.OnboardingStep
import app.kiko.onboarding.accountProblem
import app.kiko.onboarding.birthDate
import app.kiko.onboarding.canContinue
import app.kiko.onboarding.next
import app.kiko.onboarding.toggleInterest
import app.kiko.onboarding.back
import app.kiko.onboarding.codeIsComplete
import app.kiko.onboarding.passwordProblem
import app.kiko.onboarding.toNewProfile
import app.kiko.onboarding.resumeFor
import app.kiko.onboarding.identityProblem
import app.kiko.data.SignedInUser
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingStateTest {
    private val today = LocalDate(2026, 11, 17)
    private val valid = OnboardingState(
        firstName = "Martin", birthDay = "14", birthMonth = "03", birthYear = "1999",
        email = "toi@exemple.fr", password = "kiko2026", acceptedTerms = true,
    )

    @Test
    fun validAccountHasNoProblem() {
        assertNull(valid.accountProblem(today))
    }

    @Test
    fun underEighteenIsRefused() {
        val minor = valid.copy(birthYear = "2009", birthMonth = "11", birthDay = "18")
        assertEquals("L'app est réservée aux 18 ans et plus.", minor.accountProblem(today))
        val justAdult = valid.copy(birthYear = "2008", birthMonth = "11", birthDay = "17")
        assertNull(justAdult.accountProblem(today))
    }

    @Test
    fun impossibleDateIsRejected() {
        assertNull(valid.copy(birthDay = "31", birthMonth = "02").birthDate())
    }

    @Test
    fun targetLanguageDefaultsToTheOtherBetaLanguage() {
        val state = OnboardingState(step = OnboardingStep.NativeLanguage, nativeLanguage = "id").next()
        assertEquals(OnboardingStep.TargetLanguage, state.step)
        assertEquals("fr", state.targetLanguage)
    }

    @Test
    fun interestsNeedAtLeastThree() {
        var state = OnboardingState(step = OnboardingStep.Interests)
        state = state.toggleInterest("Cuisine").toggleInterest("Voyage")
        assertFalse(state.canContinue(today))
        state = state.toggleInterest("Musique")
        assertTrue(state.canContinue(today))
    }

    @Test
    fun passwordNeedsEightCharactersWithLettersAndDigits() {
        assertTrue(passwordProblem("court1") != null)
        assertTrue(passwordProblem("seulementdeslettres") != null)
        assertTrue(passwordProblem("12345678") != null)
        assertNull(passwordProblem("kiko2026"))
        assertEquals(
            "Choisis un mot de passe d'au moins 8 caractères.",
            valid.copy(password = "abc1").accountProblem(today),
        )
    }

    @Test
    fun accountStepIsFollowedByTheEmailCode() {
        assertEquals(OnboardingStep.EmailCode, valid.next().step)
        val code = valid.next()
        assertFalse(code.copy(code = "12345").codeIsComplete())
        assertFalse(code.copy(code = "12a456").codeIsComplete())
        assertTrue(code.copy(code = "123456").codeIsComplete())
    }

    @Test
    fun onceTheAccountExistsBackStopsAtTheSelfie() {
        val selfie = valid.copy(step = OnboardingStep.Selfie, accountCreated = true)
        assertEquals(OnboardingStep.Selfie, selfie.back().step)
        val notYet = valid.copy(step = OnboardingStep.EmailCode, code = "123")
        assertEquals(OnboardingStep.Account, notYet.back().step)
        assertEquals("", notYet.back().code)
    }

    @Test
    fun finishedSignUpBecomesAProfile() {
        val done = valid.copy(nativeLanguage = "fr", targetLanguage = "id", city = " Lyon ", interests = setOf("Voyage", "Cuisine", "Art"))
        val profile = done.toNewProfile("Europe/Paris")!!
        assertEquals("Lyon", profile.city)
        assertEquals("major_only", profile.correction)
        assertEquals("A2", profile.level)
        assertEquals(listOf("Art", "Cuisine", "Voyage"), profile.interests)
        assertEquals("1999-03-14", profile.birthDate.toString())
        assertNull(valid.toNewProfile("UTC"))
    }

    @Test
    fun supabaseErrorsBecomeReadableSentences() {
        assertEquals(
            "Un compte existe déjà avec cet e-mail. Connecte-toi plutôt.",
            app.kiko.data.friendlyMessage(Exception("User already registered")),
        )
        assertEquals(
            "Code incorrect ou expiré. Vérifie-le, ou demande un nouveau code.",
            app.kiko.data.friendlyMessage(Exception("Token has expired or is invalid")),
        )
    }

    @Test
    fun interruptedSignUpResumesAtSelfieWithSavedDetails() {
        val user = SignedInUser(id = "u1", firstName = null, pendingFirstName = "Ayu", pendingBirthDate = "2001-07-09")
        val resumed = OnboardingState().resumeFor(user)
        assertEquals(OnboardingStep.Selfie, resumed.step)
        assertTrue(resumed.accountCreated)
        assertEquals("Ayu", resumed.firstName)
        assertEquals(LocalDate(2001, 7, 9), resumed.birthDate())
        assertEquals(OnboardingStep.Selfie, resumed.back().step)
    }

    @Test
    fun appleOrGoogleAccountAsksForNameAndBirthDateFirst() {
        val user = SignedInUser(id = "u2", firstName = null, pendingFirstName = null, pendingBirthDate = null)
        val identity = OnboardingState().resumeFor(user)
        assertEquals(OnboardingStep.Identity, identity.step)
        assertTrue(identity.accountCreated)
        assertTrue(identity.asksIdentity)
        assertFalse(identity.canContinue(today))
        // Pas de retour vers le formulaire e-mail : le compte existe déjà.
        assertEquals(OnboardingStep.Identity, identity.back().step)

        val minor = identity.copy(
            firstName = "Ayu", birthDay = "18", birthMonth = "11", birthYear = "2009", acceptedTerms = true,
        )
        assertEquals("L'app est réservée aux 18 ans et plus.", minor.identityProblem(today))
        val noTerms = minor.copy(birthYear = "2001", acceptedTerms = false)
        assertEquals("Accepte les conditions pour continuer.", noTerms.identityProblem(today))

        val filled = noTerms.copy(acceptedTerms = true)
        assertNull(filled.identityProblem(today))
        val selfie = filled.next()
        assertEquals(OnboardingStep.Selfie, selfie.step)
        assertEquals(OnboardingStep.Identity, selfie.back().step)
    }

    @Test
    fun emailAccountsNeverSeeTheIdentityStep() {
        val code = valid.copy(step = OnboardingStep.EmailCode)
        assertEquals(OnboardingStep.Selfie, code.next().step)
        val selfie = valid.copy(step = OnboardingStep.Selfie, accountCreated = true)
        assertEquals(OnboardingStep.Selfie, selfie.back().step)
    }

    @Test
    fun socialSignInErrorsAreReadable() {
        assertEquals(
            "Cette méthode de connexion n'est pas encore activée. Utilise ton e-mail pour l'instant.",
            app.kiko.data.friendlyMessage(Exception("Unsupported provider: provider is not enabled")),
        )
        assertEquals(
            "La connexion n'a pas pu être vérifiée. Réessaie, ou utilise ton e-mail.",
            app.kiko.data.friendlyMessage(
                Exception("Passed nonce and nonce in id_token should either both exist or not."),
            ),
        )
    }
}
