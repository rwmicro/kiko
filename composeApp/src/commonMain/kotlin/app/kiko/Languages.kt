package app.kiko

/** Niveaux du Cadre européen commun de référence, utilisés pour les langues apprises. */
enum class CefrLevel { A1, A2, B1, B2, C1, C2 }

/** Langue proposée dans l'app, identifiée par son code ISO 639-1. */
data class Language(val code: String, val nameFr: String, val nativeName: String)

object Languages {
    val French = Language(code = "fr", nameFr = "Français", nativeName = "Français")
    val Indonesian = Language(code = "id", nameFr = "Indonésien", nativeName = "Bahasa Indonesia")

    /** Langues ouvertes pendant la bêta : français ↔ indonésien. */
    val beta: List<Language> = listOf(French, Indonesian)

    fun byCode(code: String): Language? = beta.firstOrNull { it.code == code }
}

/**
 * Deux personnes forment un échange possible si chacune apprend une langue natale de l'autre.
 * C'est le premier filtre strict de l'algorithme de mise en relation.
 */
fun isReciprocalExchange(
    myNative: Set<String>,
    myLearning: Set<String>,
    theirNative: Set<String>,
    theirLearning: Set<String>,
): Boolean = myLearning.any { it in theirNative } && theirLearning.any { it in myNative }
