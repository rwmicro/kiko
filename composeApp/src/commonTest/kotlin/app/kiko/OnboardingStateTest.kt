package app.kiko

import app.kiko.onboarding.OnboardingState
import app.kiko.onboarding.OnboardingStep
import app.kiko.onboarding.accountProblem
import app.kiko.onboarding.birthDate
import app.kiko.onboarding.canContinue
import app.kiko.onboarding.next
import app.kiko.onboarding.toggleInterest
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
        email = "toi@exemple.fr", acceptedTerms = true,
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
}
