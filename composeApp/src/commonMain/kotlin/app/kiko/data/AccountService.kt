package app.kiko.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Ce que l'inscription enregistre à la fin du parcours. */
data class NewProfile(
    val firstName: String,
    val birthDate: LocalDate,
    val city: String,
    val showCity: Boolean,
    val timeZone: String,
    /** Valeur de l'enum SQL `correction_preference` : all, major_only ou none. */
    val correction: String,
    val nativeLanguage: String,
    val targetLanguage: String,
    /** Niveau CECR dans la langue apprise : A1 à C2. */
    val level: String,
    val interests: List<String>,
)

/** Le compte connecté : son identifiant, et son prénom s'il a déjà un profil. */
data class SignedInUser(
    val id: String,
    val firstName: String?,
    /** Prénom et date de naissance gardés à l'inscription, pour reprendre un parcours interrompu. */
    val pendingFirstName: String?,
    val pendingBirthDate: String?,
)

enum class SignUpOutcome { NeedsCode, SignedIn }

/** Erreur à montrer telle quelle à la personne. */
open class AccountException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Le compte existe mais l'e-mail n'a pas été confirmé : il faut passer par le code. */
class EmailNotConfirmedException(cause: Throwable? = null) :
    AccountException("Ton e-mail n'est pas encore confirmé. Entre le code reçu par e-mail.", cause)

/** Compte et profil, côté serveur. */
interface AccountService {
    /** Attend que la session enregistrée soit chargée ; null si personne n'est connecté. */
    suspend fun restore(): SignedInUser?
    suspend fun signUp(email: String, password: String, firstName: String, birthDate: LocalDate): SignUpOutcome
    suspend fun confirmCode(email: String, code: String)
    suspend fun resendCode(email: String)
    suspend fun signIn(email: String, password: String): SignedInUser
    suspend fun saveProfile(profile: NewProfile)
    suspend fun signOut()
}

class SupabaseAccountService(private val client: SupabaseClient) : AccountService {

    override suspend fun restore(): SignedInUser? = guard {
        val status = client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        if (status is SessionStatus.Authenticated) currentUser() else null
    }

    override suspend fun signUp(email: String, password: String, firstName: String, birthDate: LocalDate): SignUpOutcome = guard {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("first_name", firstName.trim())
                put("birth_date", birthDate.toString())
            }
        }
        if (client.auth.currentSessionOrNull() != null) SignUpOutcome.SignedIn else SignUpOutcome.NeedsCode
    }

    override suspend fun confirmCode(email: String, code: String) = guard {
        client.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email.trim(), token = code.trim())
    }

    override suspend fun resendCode(email: String) = guard {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    override suspend fun signIn(email: String, password: String): SignedInUser = guard {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        currentUser() ?: throw AccountException("La connexion n'a pas abouti. Réessaie.")
    }

    override suspend fun saveProfile(profile: NewProfile) = guard {
        val id = client.auth.currentUserOrNull()?.id ?: throw AccountException("Ta session a expiré. Reconnecte-toi.")
        // Insertion simple : la mise à jour du profil n'est permise que sur certaines colonnes.
        // Si le profil existe déjà (deuxième essai après une coupure), on garde l'existant.
        try {
            client.from("profiles").insert(
                ProfileRow(
                    id = id,
                    firstName = profile.firstName.trim(),
                    birthDate = profile.birthDate.toString(),
                    city = profile.city.trim(),
                    showCity = profile.showCity,
                    timeZone = profile.timeZone,
                    correctionPreference = profile.correction,
                    interests = profile.interests,
                ),
            )
        } catch (e: Exception) {
            val raw = e.message.orEmpty()
            if ("23505" !in raw && "duplicate key" !in raw) throw e
        }
        client.from("user_languages").upsert(
            listOf(
                LanguageRow(id, profile.nativeLanguage, isNative = true, level = null),
                LanguageRow(id, profile.targetLanguage, isNative = false, level = profile.level),
            ),
        )
    }

    override suspend fun signOut() = guard { client.auth.signOut() }

    private suspend fun currentUser(): SignedInUser? {
        val user = client.auth.currentUserOrNull() ?: return null
        val profile = client.from("profiles")
            .select(Columns.list("first_name")) { filter { eq("id", user.id) } }
            .decodeSingleOrNull<ProfileName>()
        val meta = user.userMetadata
        return SignedInUser(
            id = user.id,
            firstName = profile?.firstName,
            pendingFirstName = meta?.get("first_name")?.jsonPrimitive?.content,
            pendingBirthDate = meta?.get("birth_date")?.jsonPrimitive?.content,
        )
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: AccountException) {
        throw e
    } catch (e: Exception) {
        val raw = e.message.orEmpty().lowercase()
        if ("email not confirmed" in raw || "email_not_confirmed" in raw) throw EmailNotConfirmedException(e)
        throw AccountException(friendlyMessage(e), e)
    }
}

/** Traduit les erreurs Supabase les plus courantes en phrases claires. */
internal fun friendlyMessage(e: Throwable): String {
    val raw = (e.message ?: "").lowercase()
    return when {
        "already registered" in raw || "already been registered" in raw || "user_already_exists" in raw ->
            "Un compte existe déjà avec cet e-mail. Connecte-toi plutôt."
        "invalid login credentials" in raw || "invalid_credentials" in raw ->
            "E-mail ou mot de passe incorrect."
        "email not confirmed" in raw || "email_not_confirmed" in raw ->
            "Ton e-mail n'est pas encore confirmé. Entre le code reçu par e-mail."
        "token has expired" in raw || "otp_expired" in raw || "invalid otp" in raw || "token" in raw && "invalid" in raw ->
            "Code incorrect ou expiré. Vérifie-le, ou demande un nouveau code."
        "password" in raw && ("weak" in raw || "at least" in raw || "should be" in raw) ->
            "Mot de passe trop faible : 8 caractères minimum, avec des lettres et des chiffres."
        "rate limit" in raw || "429" in raw || "over_email_send_rate_limit" in raw ->
            "Trop d'e-mails envoyés pour l'instant. Réessaie dans quelques minutes."
        "permission denied" in raw || "42501" in raw ->
            "Le serveur a refusé l'enregistrement (droits manquants)."
        "failed to fetch" in raw || "unable to resolve" in raw || "timeout" in raw || "connect" in raw ->
            "Connexion au serveur impossible. Vérifie ta connexion internet."
        else -> "Une erreur est survenue : ${e.message ?: e::class.simpleName}"
    }
}

@Serializable
private data class ProfileRow(
    val id: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("birth_date") val birthDate: String,
    val city: String,
    @SerialName("show_city") val showCity: Boolean,
    @SerialName("time_zone") val timeZone: String,
    @SerialName("correction_preference") val correctionPreference: String,
    val interests: List<String>,
)

@Serializable
private data class ProfileName(@SerialName("first_name") val firstName: String)

@Serializable
private data class LanguageRow(
    @SerialName("user_id") val userId: String,
    @SerialName("language_code") val languageCode: String,
    @SerialName("is_native") val isNative: Boolean,
    val level: String?,
)
