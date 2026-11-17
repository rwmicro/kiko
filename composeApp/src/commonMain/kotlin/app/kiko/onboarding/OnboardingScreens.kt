package app.kiko.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kiko.CefrLevel
import app.kiko.Languages
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
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("← Retour") }
            Spacer(Modifier.weight(1f))
            state.step.number?.let { n ->
                Text(
                    "$n / ${OnboardingStep.COUNTED_STEPS}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.step.number?.let { n ->
            LinearProgressIndicator(
                progress = { n.toFloat() / OnboardingStep.COUNTED_STEPS },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
        if (problem != null) {
            Text(
                problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Button(
            onClick = { onChange(state.next()) },
            enabled = state.canContinue(today),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(primaryLabel) }
    }
}

@Composable
private fun AccountForm(state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    OutlinedTextField(
        value = state.firstName,
        onValueChange = { onChange(state.copy(firstName = it.take(40))) },
        label = { Text("Prénom") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Date de naissance", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DigitsField(state.birthDay, "JJ", 2, Modifier.weight(1f)) { onChange(state.copy(birthDay = it)) }
        DigitsField(state.birthMonth, "MM", 2, Modifier.weight(1f)) { onChange(state.copy(birthMonth = it)) }
        DigitsField(state.birthYear, "AAAA", 4, Modifier.weight(1.6f)) { onChange(state.copy(birthYear = it)) }
    }
    OutlinedTextField(
        value = state.email,
        onValueChange = { onChange(state.copy(email = it.trim())) },
        label = { Text("E-mail") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = state.acceptedTerms, onCheckedChange = { onChange(state.copy(acceptedTerms = it)) })
        Text(
            "J'accepte les conditions d'utilisation et la politique de confidentialité",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DigitsField(value: String, label: String, max: Int, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter(Char::isDigit).take(max)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SelfieExplainer() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✋  Geste demandé : main levée", style = MaterialTheme.typography.titleMedium)
            Text("1. Photo de profil ajoutée", style = MaterialTheme.typography.bodyMedium)
            Text("2. Selfie avec le geste", style = MaterialTheme.typography.bodyMedium)
            Text("3. Badge « Vérifié » sur ton profil", style = MaterialTheme.typography.bodyMedium)
            Text(
                "La caméra n'est disponible que dans l'app sur téléphone. Dans cet aperçu, passe cette étape.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageChoices(selected: String?, disabled: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Languages.beta.forEach { language ->
            val isDisabled = language.code == disabled
            ChoiceCard(
                title = language.nameFr,
                detail = if (isDisabled) "Ta langue natale" else language.nativeName,
                selected = language.code == selected,
                enabled = !isDisabled,
                onClick = { onSelect(language.code) },
            )
        }
    }
}

@Composable
private fun ChoiceCard(title: String, detail: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelForm(state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(CefrLevel.A1, CefrLevel.A2, CefrLevel.B1, CefrLevel.B2, CefrLevel.C1).forEach { level ->
            FilterChip(
                selected = state.level == level,
                onClick = { onChange(state.copy(level = level)) },
                label = { Text(level.name) },
            )
        }
    }
    Text(levelDescription(state.level), style = MaterialTheme.typography.bodyMedium)
    Text("Quand je fais une erreur", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    CorrectionPreference.entries.forEach { pref ->
        ChoiceCard(
            title = pref.label,
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
    OutlinedTextField(
        value = state.city,
        onValueChange = { onChange(state.copy(city = it.take(60))) },
        label = { Text("Ville") },
        placeholder = { Text("Lyon, Bandung…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Ce que les autres voient", style = MaterialTheme.typography.titleMedium)
    ChoiceCard(
        title = "Ma ville",
        detail = state.city.ifBlank { "Ta ville" },
        selected = state.showCity,
        onClick = { onChange(state.copy(showCity = true)) },
    )
    ChoiceCard(
        title = "Seulement mon pays",
        detail = "Plus discret",
        selected = !state.showCity,
        onClick = { onChange(state.copy(showCity = false)) },
    )
}

@Composable
private fun InterestsForm(state: OnboardingState, onChange: (OnboardingState) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InterestChoices.forEach { interest ->
            FilterChip(
                selected = interest in state.interests,
                onClick = { onChange(state.toggleInterest(interest)) },
                label = { Text(interest) },
            )
        }
    }
    Text(
        "${state.interests.size} choisi${if (state.interests.size > 1) "s" else ""}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WelcomeHome(state: OnboardingState, onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bienvenue, ${state.firstName.trim()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Tu apprends ${withArticle(state.targetLanguage)} (niveau ${state.level.name}) et tu fais découvrir ${withArticle(state.nativeLanguage)}.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tes premiers pas", style = MaterialTheme.typography.titleMedium)
                Text("1. Vérifier ton profil (selfie, sur téléphone)", style = MaterialTheme.typography.bodyMedium)
                Text("2. Saluer un partenaire", style = MaterialTheme.typography.bodyMedium)
                Text("3. Répondre à la question du jour", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Centres d'intérêt : ${state.interests.sorted().joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.widthIn(max = 480.dp)) {
            Text(
                "La suite (partenaires, discussions) arrive avec les prochaines étapes du prototype.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
        OutlinedButton(onClick = onRestart) { Text("Recommencer l'inscription") }
    }
}
