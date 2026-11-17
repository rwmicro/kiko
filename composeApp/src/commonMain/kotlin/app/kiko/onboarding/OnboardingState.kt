package app.kiko.onboarding

import app.kiko.CefrLevel
import app.kiko.Languages
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil

enum class CorrectionPreference(val label: String, val detail: String) {
    All("Corrige-moi tout", "Chaque erreur, même de style"),
    MajorOnly("Seulement les grosses erreurs", "Les détails de style sont repliés"),
    None("Ne me corrige pas", "Tu discutes, sans corrections"),
}

/** Centres d'intérêt proposés à l'inscription (au moins 3 à choisir). */
val InterestChoices: List<String> = listOf(
    "Cinéma", "Cuisine", "Voyage", "Musique", "Foot", "Lecture",
    "Jeux vidéo", "Randonnée", "Photo", "Art", "Tech", "Yoga",
    "Animaux", "Théâtre", "Écologie", "Podcasts", "Mode", "Badminton",
)

const val MinInterests = 3
const val MinimumAge = 18

/** Étapes du parcours, dans l'ordre des maquettes. */
enum class OnboardingStep(val number: Int?) {
    Account(1), Selfie(null), NativeLanguage(2), TargetLanguage(3), Level(4), City(5), Interests(6), Done(null);

    companion object {
        const val COUNTED_STEPS = 6
    }
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Account,
    val firstName: String = "",
    val birthDay: String = "",
    val birthMonth: String = "",
    val birthYear: String = "",
    val email: String = "",
    val acceptedTerms: Boolean = false,
    val nativeLanguage: String? = null,
    val targetLanguage: String? = null,
    val level: CefrLevel = CefrLevel.A2,
    val correction: CorrectionPreference = CorrectionPreference.MajorOnly,
    val city: String = "",
    val showCity: Boolean = true,
    val interests: Set<String> = emptySet(),
)

/** Date de naissance saisie, ou null si elle est incomplète ou n'existe pas (31 février…). */
fun OnboardingState.birthDate(): LocalDate? {
    val d = birthDay.toIntOrNull() ?: return null
    val m = birthMonth.toIntOrNull() ?: return null
    val y = birthYear.toIntOrNull() ?: return null
    if (birthYear.length != 4) return null
    return runCatching { LocalDate(y, m, d) }.getOrNull()
}

fun ageOn(birth: LocalDate, today: LocalDate): Int = birth.yearsUntil(today)

private val EmailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

/** Message à afficher sous le formulaire de compte, ou null s'il est valide. */
fun OnboardingState.accountProblem(today: LocalDate): String? {
    if (firstName.isBlank()) return "Indique ton prénom."
    val birth = birthDate() ?: return "Indique une date de naissance valide."
    if (ageOn(birth, today) < MinimumAge) return "L'app est réservée aux 18 ans et plus."
    if (!EmailPattern.matches(email.trim())) return "Indique une adresse e-mail valide."
    if (!acceptedTerms) return "Accepte les conditions pour continuer."
    return null
}

fun OnboardingState.canContinue(today: LocalDate): Boolean = when (step) {
    OnboardingStep.Account -> accountProblem(today) == null
    OnboardingStep.Selfie -> true
    OnboardingStep.NativeLanguage -> nativeLanguage != null
    OnboardingStep.TargetLanguage -> targetLanguage != null && targetLanguage != nativeLanguage
    OnboardingStep.Level -> true
    OnboardingStep.City -> city.isNotBlank()
    OnboardingStep.Interests -> interests.size >= MinInterests
    OnboardingStep.Done -> false
}

fun OnboardingState.next(): OnboardingState {
    val all = OnboardingStep.entries
    val nextStep = all.getOrNull(step.ordinal + 1) ?: return this
    // En bêta, une seule paire : la langue à apprendre est l'autre langue de la paire.
    val withTarget = if (nextStep == OnboardingStep.TargetLanguage && targetLanguage == null) {
        copy(targetLanguage = Languages.beta.firstOrNull { it.code != nativeLanguage }?.code)
    } else this
    return withTarget.copy(step = nextStep)
}

fun OnboardingState.back(): OnboardingState {
    val previous = OnboardingStep.entries.getOrNull(step.ordinal - 1) ?: return this
    return copy(step = previous)
}

fun OnboardingState.toggleInterest(interest: String): OnboardingState =
    copy(interests = if (interest in interests) interests - interest else interests + interest)
