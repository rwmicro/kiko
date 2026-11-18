@file:OptIn(ExperimentalTime::class)

package app.kiko.partners

import app.kiko.Languages
import app.kiko.data.Partner
import app.kiko.ui.components.KikoTimeOfDay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** L'heure qu'il est chez quelqu'un, et le moment de la journée qui colore sa pastille. */
data class LocalClock(val time: String, val moment: KikoTimeOfDay) {
    /** « c'est le soir », pour la fiche du partenaire. */
    val momentLabel: String
        get() = when (moment) {
            KikoTimeOfDay.Dawn -> "c'est le matin"
            KikoTimeOfDay.Day -> "c'est la journée"
            KikoTimeOfDay.Dusk -> "c'est le soir"
            KikoTimeOfDay.Night -> "c'est la nuit"
        }
}

/** Null si le fuseau est inconnu de l'appareil. */
fun localClock(timeZone: String, now: Instant): LocalClock? {
    val zone = runCatching { TimeZone.of(timeZone) }.getOrNull() ?: return null
    val local = now.toLocalDateTime(zone)
    return clockAt(local.hour, local.minute)
}

fun clockAt(hour: Int, minute: Int): LocalClock {
    val moment = when (hour) {
        in 5..7 -> KikoTimeOfDay.Dawn
        in 8..17 -> KikoTimeOfDay.Day
        in 18..21 -> KikoTimeOfDay.Dusk
        else -> KikoTimeOfDay.Night
    }
    return LocalClock("${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}", moment)
}

/** En ligne : actif dans les cinq dernières minutes. */
fun Partner.isOnline(now: Instant): Boolean = lastSeen?.let { now - it < 5.minutes } ?: false

/** « Bandung · français B1 » sous le prénom. */
fun Partner.metaLine(): String {
    val learning = Languages.byCode(learningLanguage)?.nameFr?.lowercase() ?: learningLanguage
    return listOfNotNull(city, listOfNotNull(learning, learningLevel).joinToString(" ")).joinToString(" · ")
}

/** Filtres en haut de la liste. */
enum class PartnerFilter(val label: String) {
    All("Tous"),
    Common("Intérêts communs"),
    Online("En ligne"),
    Verified("Vérifiés"),
}

fun List<Partner>.filtered(filter: PartnerFilter, query: String, now: Instant): List<Partner> {
    val q = query.trim()
    return filter { p ->
        val matchesFilter = when (filter) {
            PartnerFilter.All -> true
            PartnerFilter.Common -> p.commonInterests.isNotEmpty()
            PartnerFilter.Online -> p.isOnline(now)
            PartnerFilter.Verified -> p.verified
        }
        val matchesQuery = q.isEmpty() ||
            p.firstName.contains(q, ignoreCase = true) ||
            (p.city?.contains(q, ignoreCase = true) ?: false) ||
            p.interests.any { it.contains(q, ignoreCase = true) }
        matchesFilter && matchesQuery
    }
}
