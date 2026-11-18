@file:OptIn(ExperimentalTime::class)

package app.kiko.data

import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/** Partenaires d'exemple, pour la démo sans compte et les captures d'écran. */
class DemoPartnerService : PartnerService {
    private val now = Clock.System.now()
    private val partners = mutableListOf(
        demo("Ayu", 26, "Bandung", "B1", listOf("Cinéma", "Cuisine"), verified = true, online = true),
        demo("Budi", 24, "Jakarta", "B1", listOf("Foot"), online = true),
        demo("Sari", 31, "Yogyakarta", "A1", listOf("Art"), verified = true),
        demo("Rizky", 28, "Denpasar", "B2", emptyList(), online = true),
        demo("Dewi", 23, null, "A2", listOf("Voyage"), tz = "Asia/Makassar", verified = true),
        demo("Putri", 27, "Medan", "B1", listOf("Musique"), online = true),
        demo("Andi", 34, "Makassar", "A2", emptyList(), tz = "Asia/Makassar"),
    )

    private fun demo(
        name: String, age: Int, city: String?, level: String, common: List<String>,
        tz: String = "Asia/Jakarta", verified: Boolean = false, online: Boolean = false,
    ) = Partner(
        id = name.lowercase(), firstName = name, age = age, city = city, countryCode = "ID", timeZone = tz,
        verified = verified, lastSeen = if (online) now else now - 90.minutes,
        nativeLanguage = "id", learningLanguage = "fr", learningLevel = level, correction = if (name.length % 2 == 0) "all" else "major_only",
        interests = common + listOf("Photo", "Randonnée").filterNot { it in common },
        commonInterests = common,
    )

    override suspend fun suggestions(): List<Partner> {
        delay(300)
        return partners.toList()
    }

    override suspend fun greet(partnerId: String, message: String) {
        delay(400)
        partners.removeAll { it.id == partnerId }
    }

    override suspend fun markActive() = Unit
}
