package app.kiko.onboarding

import app.kiko.CefrLevel
import app.kiko.Languages
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil

enum class CorrectionPreference(val label: String, val detail: String, val sqlValue: String) {
    All("Corrige-moi tout", "Chaque erreur, même de style", "all"),
    MajorOnly("Seulement les grosses erreurs", "Les détails de style sont repliés", "major_only"),
    None("Ne me corrige pas", "Tu discutes, sans corrections", "none"),
}

/** Centres d'intérêt proposés à l'inscription (au moins 3 à choisir). */
val InterestChoices: List<String> = listOf(
    "Cinéma", "Cuisine", "Voyage", "Musique", "Foot", "Lecture",
    "Jeux vidéo", "Randonnée", "Photo", "Art", "Tech", "Yoga",
    "Animaux", "Théâtre", "Écologie", "Podcasts", "Mode", "Badminton",
)

const val MinInterests = 3
const val MinimumAge = 18
const val MinPasswordLength = 8

/**
 * Étapes du parcours, dans l'ordre des maquettes.
 * Identity ne sert qu'aux comptes Apple et Google : ils arrivent sans prénom ni date de naissance.
 */
enum class OnboardingStep(val number: Int?) {
    Account(1), EmailCode(null), Identity(1), Selfie(null), NativeLanguage(2), TargetLanguage(3), Level(4), City(5), Interests(6), Done(null);

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
    val password: String = "",
    val acceptedTerms: Boolean = false,
    /** Code de confirmation reçu par e-mail. */
    val code: String = "",
    /** Le compte existe et la session est ouverte : on ne revient plus aux étapes de compte. */
    val accountCreated: Boolean = false,
    /**
     * Le compte n'a gardé ni prénom ni date de naissance (compte Apple ou Google) :
     * on les demande à l'étape Identity.
     */
    val asksIdentity: Boolean = false,
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
    passwordProblem(password)?.let { return it }
    if (!acceptedTerms) return "Accepte les conditions pour continuer."
    return null
}

/** Étape Identity (comptes Apple et Google) : prénom, âge et conditions, sans e-mail ni mot de passe. */
fun OnboardingState.identityProblem(today: LocalDate): String? {
    if (firstName.isBlank()) return "Indique ton prénom."
    val birth = birthDate() ?: return "Indique une date de naissance valide."
    if (ageOn(birth, today) < MinimumAge) return "L'app est réservée aux 18 ans et plus."
    if (!acceptedTerms) return "Accepte les conditions pour continuer."
    return null
}

/** Message sur le mot de passe, ou null s'il convient : 8 caractères, des lettres et des chiffres. */
fun passwordProblem(password: String): String? = when {
    password.length < MinPasswordLength -> "Choisis un mot de passe d'au moins $MinPasswordLength caractères."
    password.none { it.isLetter() } || password.none { it.isDigit() } -> "Mets au moins une lettre et un chiffre dans ton mot de passe."
    else -> null
}

/** Le code reçu par e-mail : 6 à 8 chiffres selon le réglage du projet Supabase. */
fun OnboardingState.codeIsComplete(): Boolean = code.length in 6..8 && code.all { it.isDigit() }

fun OnboardingState.canContinue(today: LocalDate): Boolean = when (step) {
    OnboardingStep.Account -> accountProblem(today) == null
    OnboardingStep.EmailCode -> codeIsComplete()
    OnboardingStep.Identity -> identityProblem(today) == null
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
    val following = all.getOrNull(step.ordinal + 1) ?: return this
    // Les comptes par e-mail ont donné prénom et date de naissance dès le formulaire de compte.
    val nextStep = if (following == OnboardingStep.Identity && !asksIdentity) OnboardingStep.Selfie else following
    // En bêta, une seule paire : la langue à apprendre est l'autre langue de la paire.
    val withTarget = if (nextStep == OnboardingStep.TargetLanguage && targetLanguage == null) {
        copy(targetLanguage = Languages.beta.firstOrNull { it.code != nativeLanguage }?.code)
    } else this
    return withTarget.copy(step = nextStep)
}

fun OnboardingState.back(): OnboardingState {
    val before = OnboardingStep.entries.getOrNull(step.ordinal - 1) ?: return this
    val previous = if (before == OnboardingStep.Identity && !asksIdentity) OnboardingStep.EmailCode else before
    if (accountCreated && previous.ordinal <= OnboardingStep.EmailCode.ordinal) return this
    // Depuis le code, on revient au formulaire (pour corriger l'e-mail), sans repasser par le code.
    return copy(step = previous, code = "")
}

/** Ce que l'inscription enregistre, ou null s'il manque encore une réponse. */
fun OnboardingState.toNewProfile(timeZone: String): app.kiko.data.NewProfile? {
    val birth = birthDate() ?: return null
    val native = nativeLanguage ?: return null
    val target = targetLanguage ?: return null
    return app.kiko.data.NewProfile(
        firstName = firstName.trim(),
        birthDate = birth,
        city = city.trim(),
        showCity = showCity,
        timeZone = timeZone,
        correction = correction.sqlValue,
        nativeLanguage = native,
        targetLanguage = target,
        level = level.name,
        interests = interests.sorted(),
    )
}

fun OnboardingState.toggleInterest(interest: String): OnboardingState =
    copy(interests = if (interest in interests) interests - interest else interests + interest)

/**
 * Reprend un parcours interrompu après la création du compte : prénom et date de naissance
 * viennent de ce que le compte a gardé à l'inscription, la suite repart du selfie.
 * Un compte Apple ou Google n'a rien gardé : il passe d'abord par l'étape Identity.
 */
fun OnboardingState.resumeFor(user: app.kiko.data.SignedInUser): OnboardingState {
    val parts = user.pendingBirthDate?.split("-").orEmpty()
    val (y, m, d) = if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else Triple(birthYear, birthMonth, birthDay)
    val knowsIdentity = (firstName.isNotBlank() || user.pendingFirstName != null) && (parts.size == 3 || birthDate() != null)
    return copy(
        step = if (knowsIdentity) OnboardingStep.Selfie else OnboardingStep.Identity,
        asksIdentity = asksIdentity || !knowsIdentity,
        accountCreated = true,
        password = "",
        code = "",
        firstName = firstName.ifBlank { user.pendingFirstName.orEmpty() },
        birthYear = birthYear.ifBlank { y },
        birthMonth = birthMonth.ifBlank { m },
        birthDay = birthDay.ifBlank { d },
    )
}
