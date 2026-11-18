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
import androidx.compose.runtime.rememberCoroutineScope
import app.kiko.data.AccountException
import app.kiko.data.AccountService
import app.kiko.data.SignUpOutcome
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import app.kiko.ui.components.KikoButtonSize
import app.kiko.ui.components.KikoLinkButton
import app.kiko.ui.components.KikoMascot
import app.kiko.ui.components.KikoMascotNote
import app.kiko.ui.components.KikoSearchField
import app.kiko.ui.components.KikoSelectRow
import app.kiko.ui.components.KikoStep
import app.kiko.ui.components.KikoStepList
import app.kiko.ui.components.KikoStepState
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
import kotlinx.datetime.LocalDate

/**
 * Parcours d'inscription : un écran par étape, état tenu par l'appelant.
 * Les étapes de compte et la fin du parcours parlent au serveur via [account].
 */
@Composable
fun OnboardingFlow(
    state: OnboardingState,
    today: LocalDate,
    account: AccountService,
    onChange: (OnboardingState) -> Unit,
    onExit: () -> Unit,
    onSignIn: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var serverError by remember(state.step) { mutableStateOf<String?>(null) }
    var notice by remember(state.step) { mutableStateOf<String?>(null) }
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        serverError = null
        notice = null
        scope.launch {
            try {
                block()
            } catch (e: AccountException) {
                serverError = e.message
            } finally {
                busy = false
            }
        }
    }
    val back = { onChange(state.back()) }
    val next = { onChange(state.next()) }

    when (state.step) {
        OnboardingStep.Account -> StepScaffold(
            state, today, onBack = onExit, busy = busy,
            title = "Crée ton compte",
            subtitle = "Ton prénom et ton âge seront visibles, jamais ta date de naissance.",
            primaryLabel = "Créer mon compte",
            onPrimary = {
                run {
                    val birth = state.birthDate() ?: return@run
                    val outcome = account.signUp(state.email, state.password, state.firstName, birth)
                    onChange(
                        if (outcome == SignUpOutcome.NeedsCode) state.copy(step = OnboardingStep.EmailCode, code = "")
                        else state.copy(step = OnboardingStep.Selfie, accountCreated = true, password = ""),
                    )
                }
            },
            secondaryLabel = "J'ai déjà un compte",
            onSecondary = onSignIn,
            problem = serverError ?: state.accountProblem(today).takeIf { state.hasStartedAccount() },
        ) { AccountForm(state, onChange) }

        OnboardingStep.EmailCode -> StepScaffold(
            state, today, onBack = back, busy = busy,
            title = "Entre le code reçu",
            subtitle = "On vient de l'envoyer à ${state.email.trim()}. Pense à regarder dans les spams.",
            primaryLabel = "Valider",
            onPrimary = {
                run {
                    account.confirmCode(state.email, state.code)
                    val user = account.restore()
                    onChange(if (user != null) state.resumeFor(user) else state.copy(step = OnboardingStep.Selfie, accountCreated = true, password = "", code = ""))
                }
            },
            secondaryLabel = "Renvoyer le code",
            onSecondary = { run { account.resendCode(state.email); notice = "Nouveau code envoyé." } },
            headerLabel = "Confirmation",
            problem = serverError,
            notice = notice,
        ) {
            KikoTextField(
                value = state.code,
                onValueChange = { onChange(state.copy(code = it.filter(Char::isDigit).take(8))) },
                label = "Code à 6 chiffres",
                placeholder = "123456",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            KikoMascotNote("Le code expire au bout d'une heure. Tu peux revenir en arrière pour corriger ton e-mail.")
        }

        OnboardingStep.Selfie -> StepScaffold(
            state, today, onBack = back, busy = busy,
            title = "Montre que c'est bien toi",
            subtitle = "Un selfie avec un geste demandé au hasard. Il sert seulement à la vérification et n'est jamais affiché.",
            primaryLabel = "Prendre le selfie",
            onPrimary = next,
            secondaryLabel = "Plus tard",
            onSecondary = next,
            headerLabel = "Sécurité",
        ) { SelfieExplainer() }

        OnboardingStep.NativeLanguage -> StepScaffold(
            state, today, onBack = back, busy = busy, onPrimary = next,
            title = "Quelle est ta langue natale ?",
            subtitle = "Celle que tu parles depuis toujours. C'est elle que tu feras découvrir.",
        ) {
            LanguageChoices(selected = state.nativeLanguage, disabled = null) {
                onChange(state.copy(nativeLanguage = it, targetLanguage = null))
            }
        }

        OnboardingStep.TargetLanguage -> StepScaffold(
            state, today, onBack = back, busy = busy, onPrimary = next,
            title = "Quelle langue veux-tu parler ?",
            subtitle = "Pendant la bêta, l'app relie les francophones et les indonésiens.",
        ) {
            LanguageChoices(selected = state.targetLanguage, disabled = state.nativeLanguage) {
                onChange(state.copy(targetLanguage = it))
            }
        }

        OnboardingStep.Level -> StepScaffold(
            state, today, onBack = back, busy = busy, onPrimary = next,
            title = "Ton niveau en ${languageName(state.targetLanguage).lowercase()}",
            subtitle = "Tes partenaires le verront, pour adapter leur façon de parler.",
        ) { LevelForm(state, onChange) }

        OnboardingStep.City -> StepScaffold(
            state, today, onBack = back, busy = busy, onPrimary = next,
            title = "Où vis-tu ?",
            subtitle = "Tes partenaires verront l'heure qu'il est chez toi.",
        ) { CityForm(state, onChange) }

        OnboardingStep.Interests -> StepScaffold(
            state, today, onBack = back, busy = busy,
            title = "Qu'est-ce qui te passionne ?",
            subtitle = "Choisis au moins $MinInterests sujets. Ils servent à te proposer des partenaires et des idées pour briser la glace.",
            primaryLabel = "Terminer",
            onPrimary = {
                run {
                    val profile = state.toNewProfile(TimeZone.currentSystemDefault().id)
                        ?: throw AccountException("Il manque une réponse plus haut dans le parcours.")
                    account.saveProfile(profile)
                    onChange(state.next())
                }
            },
            problem = serverError,
        ) { InterestsForm(state, onChange) }

        OnboardingStep.Done -> HomeScreen(
            firstName = state.firstName,
            onSignOut = { run { account.signOut(); onExit() } },
        )
    }
}

private fun OnboardingState.hasStartedAccount(): Boolean =
    firstName.isNotEmpty() || birthYear.isNotEmpty() || email.isNotEmpty() || acceptedTerms

private fun languageName(code: String?): String = code?.let { Languages.byCode(it)?.nameFr } ?: "langue"

@Composable
private fun StepScaffold(
    state: OnboardingState,
    today: LocalDate,
    onBack: () -> Unit,
    busy: Boolean,
    title: String,
    subtitle: String,
    onPrimary: () -> Unit,
    primaryLabel: String = "Continuer",
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    headerLabel: String? = null,
    problem: String? = null,
    notice: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        KikoStepHeader(current = state.step.number, total = OnboardingStep.COUNTED_STEPS, onBack = onBack, label = headerLabel)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KikoScreenTitle(title, subtitle = subtitle)
            content()
        }
        if (problem != null) {
            Text(problem, style = Kiko.type.captionStrong, color = Kiko.colors.error, modifier = Modifier.padding(bottom = 10.dp))
        } else if (notice != null) {
            Text(notice, style = Kiko.type.captionStrong, color = Kiko.colors.ok, modifier = Modifier.padding(bottom = 10.dp))
        }
        KikoButton(
            if (busy) "Un instant…" else primaryLabel,
            onClick = onPrimary,
            enabled = state.canContinue(today) && !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (secondaryLabel != null) {
            KikoLinkButton(secondaryLabel, { if (!busy) onSecondary() }, Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        }
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
        Text("Date de naissance", style = Kiko.type.captionStrong, color = Kiko.colors.mid)
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
    KikoTextField(
        value = state.password,
        onValueChange = { onChange(state.copy(password = it.take(72))) },
        label = "Mot de passe",
        placeholder = "8 caractères, lettres et chiffres",
        password = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
    KikoStepList(
        listOf(
            KikoStep("Photo de profil ajoutée", KikoStepState.Done),
            KikoStep("Selfie avec le geste : main levée", KikoStepState.Current),
            KikoStep("Badge « Vérifié » sur ton profil", KikoStepState.Todo),
        ),
    )
    KikoTip("La caméra n'est disponible que dans l'app sur téléphone. Dans cet aperçu, passe cette étape.")
}

@Composable
private fun LanguageChoices(selected: String?, disabled: String?, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    KikoSearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Langue ou dialecte")
    val shown = Languages.beta.filter {
        query.isBlank() || it.nameFr.contains(query.trim(), ignoreCase = true) || it.nativeName.contains(query.trim(), ignoreCase = true)
    }
    KikoSectionLabel(if (disabled == null) "Pendant la bêta" else "Disponibles pendant la bêta", Modifier.padding(top = 4.dp))
    Column {
        shown.forEach { language ->
            val isDisabled = language.code == disabled
            KikoSelectRow(
                text = language.nameFr,
                detail = if (isDisabled) "Ta langue natale" else language.nativeName,
                flag = language.code,
                selected = language.code == selected,
                enabled = !isDisabled,
                onClick = { onSelect(language.code) },
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
    Text(levelDescription(state.level), style = Kiko.type.body, color = Kiko.colors.mid)
    KikoSectionLabel("Quand je fais une erreur", Modifier.padding(top = 8.dp))
    CorrectionPreference.entries.forEach { pref ->
        KikoRadioOption(
            text = pref.label,
            detail = pref.detail,
            selected = state.correction == pref,
            onClick = { onChange(state.copy(correction = pref)) },
        )
    }
    KikoMascotNote("Tes partenaires verront ce choix, et les corrections que tu reçois le suivront. Tu le changes quand tu veux dans Réglages.")
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
    KikoButton("Utiliser ma position", onClick = {}, modifier = Modifier.fillMaxWidth(), style = KikoButtonStyle.Soft)
    KikoTextField(
        value = state.city,
        onValueChange = { onChange(state.copy(city = it.take(60))) },
        label = "Ville",
        placeholder = "Lyon, Bandung…",
        modifier = Modifier.fillMaxWidth(),
    )
    KikoSectionLabel("Ce que les autres voient", Modifier.padding(top = 8.dp))
    KikoRadioOption(
        text = "Ma ville" + state.city.trim().let { if (it.isEmpty()) "" else " : $it" },
        selected = state.showCity,
        onClick = { onChange(state.copy(showCity = true)) },
    )
    KikoRadioOption(
        text = "Seulement mon pays",
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
    KikoMascotNote(
        if (n < MinInterests) "$n choisi${if (n > 1) "s" else ""}, encore ${MinInterests - n}. Lézo s'en sert pour te proposer des partenaires et des sujets pour briser la glace."
        else "$n choisis. Lézo s'en sert pour te proposer des partenaires et des sujets pour briser la glace.",
    )
}

/** Accueil après l'inscription ou la connexion, en attendant les écrans Partenaires et Discussions. */
@Composable
fun HomeScreen(firstName: String, onSignOut: (() -> Unit)? = null, onGreet: () -> Unit = {}, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val c = Kiko.colors
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        KikoScreenTitle("Bienvenue, ${firstName.trim()}", subtitle = "Ton profil est prêt. Il ne reste qu'à dire bonjour.")
        KikoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KikoMascot(size = 52.dp)
                Column {
                    Text("Tes premiers pas", style = Kiko.type.name.copy(fontWeight = FontWeight.ExtraBold), color = c.ink)
                    Text("1 sur 3 · environ 5 minutes", style = Kiko.type.micro, color = c.mid)
                }
            }
            KikoStepList(
                listOf(
                    KikoStep("Créer ton profil", KikoStepState.Done),
                    KikoStep("Saluer un partenaire", KikoStepState.Current),
                    KikoStep("Répondre à la question du jour", KikoStepState.Todo),
                ),
                trailing = { i -> if (i == 1) KikoButton("Saluer", onGreet, size = KikoButtonSize.Small, style = KikoButtonStyle.Sun) },
            )
        }
        KikoQuestionOfDay("Quel plat de ton pays ferais-tu goûter à ton partenaire ?", onAnswer = {})
        KikoTip("Va dans Partenaires pour trouver quelqu'un à saluer. Le chat arrive à la prochaine étape du prototype.")
        if (onSignOut != null) KikoLinkButton("Me déconnecter", onSignOut, Modifier.align(Alignment.CenterHorizontally), color = c.mid)
    }
}
