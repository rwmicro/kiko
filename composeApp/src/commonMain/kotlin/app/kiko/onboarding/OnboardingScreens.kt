package app.kiko.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kiko.CefrLevel
import app.kiko.Languages
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoCard
import app.kiko.ui.components.KikoCheckRow
import app.kiko.ui.components.KikoChip
import app.kiko.ui.components.KikoLevelPicker
import app.kiko.ui.components.KikoOption
import app.kiko.ui.components.KikoQuestionOfDay
import app.kiko.ui.components.KikoRadioOption
import app.kiko.ui.components.KikoScreenTitle
import app.kiko.ui.components.KikoSectionLabel
import app.kiko.ui.components.KikoStepHeader
import app.kiko.ui.components.KikoTag
import app.kiko.ui.components.KikoTagStyle
import app.kiko.ui.components.KikoTextField
import app.kiko.ui.components.KikoTip
import app.kiko.ui.theme.Kiko
import app.kiko.ui.theme.KikoType
import kotlinx.datetime.LocalDate

/** Parcours d'inscription : un écran par étape, état tenu par l'appelant. */
@Composable
fun OnboardingFlow(
    state: OnboardingState,
    today: LocalDate,
    onChange: (OnboardingState) -> Unit,
    onExit: () -> Unit,
) {
    when (state.step) {
        OnboardingStep.Account -> StepScaffold(
            state, today, onChange, onBack = onExit,
            title = "Crée ton compte",
            subtitle = "Ton prénom et ton âge seront visibles, jamais ta date de naissance.",
            primaryLabel = "Créer mon compte",
            problem = state.accountProblem(today).takeIf { state.hasStartedAccount() },
        ) { AccountForm(state, onChange) }

        OnboardingStep.Selfie -> StepScaffold(
            state, today, onChange, onBack = { onChange(state.back()) },
            title = "Montre que c'est bien toi",
            subtitle = "Un selfie avec un geste demandé au hasard. Il sert seulement à la vérification et n'est jamais affiché.",
            primaryLabel = "Plus tard",
        ) { SelfieExplainer() }

        OnboardingStep.NativeLanguage -> StepScaffold(
            state, today, onChange, onBack = { onChange(state.back()) },
            title = "Quelle est ta langue natale ?",
            subtitle = "Celle que tu parles depuis toujours. C'est elle que tu feras découvrir.",
        ) {
            LanguageChoices(selected = state.nativeLanguage, disabled = null) {
                onChange(state.copy(nativeLanguage = it, targetLanguage = null))
            }
        }

        OnboardingStep.TargetLanguage -> StepScaffold(
            state, today, onChange, onBack = { onChange(state.back()) },
            title = "Quelle langue veux-tu parler ?",
            subtitle = "Pendant la bêta, l'app relie les francophones et les indonésiens.",
        ) {
            LanguageChoices(selected = state.targetLanguage, disabled = state.nativeLanguage) {
                onChange(state.copy(targetLanguage = it))
            }
        }

        OnboardingStep.Level -> StepScaffold(
            state, today, onChange, onBack = { onChange(state.back()) },
            title = "Ton niveau en ${languageName(state.targetLanguage).lowercase()}",
            subtitle = "Tes partenaires le verront, pour adapter leur façon de parler.",
        ) { LevelForm(state, onChange) }

        OnboardingStep.City -> StepScaffold(
            state, today, onChange, onBack = { onChange(state.back()) },
            title = "Où vis-tu ?",
            subtitle = "Tes partenaires verront l'heure qu'il est chez toi.",
        ) { CityForm(state, onChange) }

        OnboardingStep.Interests -> StepScaffold(
            state, today, onChange, onBack = { onChange(state.back()) },
            title = "Qu'est-ce qui te passionne ?",
            subtitle = "Choisis au moins $MinInterests sujets. Ils servent à te proposer des partenaires et des idées pour briser la glace.",
            primaryLabel = "Terminer",
        ) { InterestsForm(state, onChange) }

        OnboardingStep.Done -> WelcomeHome(state, onRestart = { onChange(OnboardingState()) })
    }
}

private fun OnboardingState.hasStartedAccount(): Boolean =
    firstName.isNotEmpty() || birthYear.isNotEmpty() || email.isNotEmpty() || acceptedTerms

private fun languageName(code: String?): String = code?.let { Languages.byCode(it)?.nameFr } ?: "langue"

private fun withArticle(code: String?): String = when (code) {
    "fr" -> "le français"
    "id" -> "l'indonésien"
    else -> "ta langue"
}

@Composable
private fun StepScaffold(
    state: OnboardingState,
    today: LocalDate,
    onChange: (OnboardingState) -> Unit,
    onBack: () -> Unit,
    title: String,
    subtitle: String,
    primaryLabel: String = "Continuer",
    problem: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        KikoStepHeader(current = state.step.number, total = OnboardingStep.COUNTED_STEPS, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KikoScreenTitle(title, subtitle = subtitle)
            content()
        }
        if (problem != null) {
            Text(problem, style = KikoType.captionStrong, color = Kiko.colors.error, modifier = Modifier.padding(bottom = 10.dp))
        }
        KikoButton(
            primaryLabel,
            onClick = { onChange(state.next()) },
            enabled = state.canContinue(today),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AccountForm(state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    KikoTextField(
        value = state.firstName,
        onValueChange = { onChange(state.copy(firstName = it.take(40))) },
        label = "Prénom",
        placeholder = "Ton prénom",
        modifier = Modifier.fillMaxWidth(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Date de naissance", style = KikoType.captionStrong, color = Kiko.colors.mid)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DigitsField(state.birthDay, "JJ", 2, Modifier.weight(1f)) { onChange(state.copy(birthDay = it)) }
            DigitsField(state.birthMonth, "MM", 2, Modifier.weight(1f)) { onChange(state.copy(birthMonth = it)) }
            DigitsField(state.birthYear, "AAAA", 4, Modifier.weight(1.6f)) { onChange(state.copy(birthYear = it)) }
        }
    }
    KikoTextField(
        value = state.email,
        onValueChange = { onChange(state.copy(email = it.trim())) },
        label = "E-mail",
        placeholder = "toi@exemple.com",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    KikoCheckRow(
        checked = state.acceptedTerms,
        onCheckedChange = { onChange(state.copy(acceptedTerms = it)) },
        text = "J'accepte les conditions d'utilisation et la politique de confidentialité",
    )
}

@Composable
private fun DigitsField(value: String, placeholder: String, max: Int, modifier: Modifier, onValue: (String) -> Unit) {
    KikoTextField(
        value = value,
        onValueChange = { onValue(it.filter(Char::isDigit).take(max)) },
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SelfieExplainer() {
    KikoCard(Modifier.fillMaxWidth()) {
        KikoTag("Geste demandé : main levée", style = KikoTagStyle.Sun)
        Text("1. Photo de profil ajoutée", style = KikoType.body, color = Kiko.colors.ink)
        Text("2. Selfie avec le geste", style = KikoType.body, color = Kiko.colors.ink)
        Text("3. Badge « Vérifié » sur ton profil", style = KikoType.body, color = Kiko.colors.ink)
    }
    KikoTip("La caméra n'est disponible que dans l'app sur téléphone. Dans cet aperçu, passe cette étape.")
}

@Composable
private fun LanguageChoices(selected: String?, disabled: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Languages.beta.forEach { language ->
            val isDisabled = language.code == disabled
            KikoOption(
                text = language.nameFr,
                detail = if (isDisabled) "Ta langue natale" else language.nativeName,
                leading = language.code.uppercase(),
                selected = language.code == selected,
                enabled = !isDisabled,
                onClick = { onSelect(language.code) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LevelForm(state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    val levels = listOf(CefrLevel.A1, CefrLevel.A2, CefrLevel.B1, CefrLevel.B2, CefrLevel.C1)
    KikoLevelPicker(
        levels = levels.map { it.name },
        selected = state.level.name,
        onSelect = { name -> onChange(state.copy(level = CefrLevel.valueOf(name))) },
    )
    Text(levelDescription(state.level), style = KikoType.body, color = Kiko.colors.mid)
    KikoSectionLabel("Quand je fais une erreur", Modifier.padding(top = 8.dp))
    CorrectionPreference.entries.forEach { pref ->
        KikoRadioOption(
            text = pref.label,
            detail = pref.detail,
            selected = state.correction == pref,
            onClick = { onChange(state.copy(correction = pref)) },
        )
    }
}

private fun levelDescription(level: CefrLevel): String = when (level) {
    CefrLevel.A1 -> "A1 : je connais quelques mots et phrases toutes faites."
    CefrLevel.A2 -> "A2 : je me débrouille dans les situations simples."
    CefrLevel.B1 -> "B1 : je tiens une conversation sur des sujets familiers."
    CefrLevel.B2 -> "B2 : je discute avec aisance de la plupart des sujets."
    CefrLevel.C1, CefrLevel.C2 -> "C1 : je m'exprime couramment, avec des nuances."
}

@Composable
private fun CityForm(state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    KikoTextField(
        value = state.city,
        onValueChange = { onChange(state.copy(city = it.take(60))) },
        label = "Ville",
        placeholder = "Lyon, Bandung…",
        modifier = Modifier.fillMaxWidth(),
    )
    KikoSectionLabel("Ce que les autres voient", Modifier.padding(top = 8.dp))
    KikoRadioOption(
        text = "Ma ville",
        detail = state.city.ifBlank { "Ta ville" },
        selected = state.showCity,
        onClick = { onChange(state.copy(showCity = true)) },
    )
    KikoRadioOption(
        text = "Seulement mon pays",
        detail = "Plus discret",
        selected = !state.showCity,
        onClick = { onChange(state.copy(showCity = false)) },
    )
}

@Composable
private fun InterestsForm(state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InterestChoices.forEach { interest ->
            KikoChip(
                text = interest,
                selected = interest in state.interests,
                onClick = { onChange(state.toggleInterest(interest)) },
            )
        }
    }
    val n = state.interests.size
    Text(
        if (n < MinInterests) "$n choisi${if (n > 1) "s" else ""}, encore ${MinInterests - n}" else "$n choisis, c'est parfait",
        style = KikoType.captionStrong,
        color = if (n < MinInterests) Kiko.colors.mid else Kiko.colors.ok,
    )
}

@Composable
private fun WelcomeHome(state: OnboardingState, onRestart: () -> Unit) {
    val c = Kiko.colors
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        KikoScreenTitle(
            "Bienvenue, ${state.firstName.trim()}",
            subtitle = "Tu apprends ${withArticle(state.targetLanguage)} (niveau ${state.level.name}) et tu fais découvrir ${withArticle(state.nativeLanguage)}.",
        )
        KikoCard(Modifier.fillMaxWidth()) {
            Text("Tes premiers pas", style = KikoType.titleSmall, color = c.ink)
            Text("1. Vérifier ton profil (selfie, sur téléphone)", style = KikoType.body, color = c.ink)
            Text("2. Saluer un partenaire", style = KikoType.body, color = c.ink)
            Text("3. Répondre à la question du jour", style = KikoType.body, color = c.ink)
        }
        KikoQuestionOfDay("Quel plat de ton pays ferais-tu goûter à ton partenaire ?", onAnswer = {})
        KikoSectionLabel("Tes centres d'intérêt")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.interests.sorted().forEach { KikoTag(it) }
        }
        KikoTip("La suite (partenaires, discussions) arrive avec les prochaines étapes du prototype.")
        KikoButton("Recommencer l'inscription", onRestart, Modifier.fillMaxWidth(), style = KikoButtonStyle.White)
    }
}
