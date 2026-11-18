@file:OptIn(ExperimentalTime::class)

package app.kiko

import app.kiko.data.Partner
import app.kiko.data.partnerMessage
import app.kiko.partners.PartnerFilter
import app.kiko.partners.clockAt
import app.kiko.partners.filtered
import app.kiko.partners.icebreakersFor
import app.kiko.partners.isOnline
import app.kiko.partners.localClock
import app.kiko.partners.metaLine
import app.kiko.partners.textIn
import app.kiko.ui.components.KikoTimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class PartnersTest {
    private val now = Instant.parse("2026-11-18T15:00:00Z")

    private fun partner(
        name: String,
        city: String? = "Bandung",
        common: List<String> = emptyList(),
        verified: Boolean = false,
        lastSeen: Instant? = null,
    ) = Partner(
        id = name, firstName = name, age = 25, city = city, countryCode = null, timeZone = "UTC",
        verified = verified, lastSeen = lastSeen, nativeLanguage = "id", learningLanguage = "fr",
        learningLevel = "B1", correction = "all", interests = common + "Foot", commonInterests = common,
    )

    @Test
    fun clockShowsTimeAndMomentOfDay() {
        assertEquals("06:05", clockAt(6, 5).time)
        assertEquals(KikoTimeOfDay.Dawn, clockAt(6, 5).moment)
        assertEquals(KikoTimeOfDay.Day, clockAt(12, 0).moment)
        assertEquals(KikoTimeOfDay.Dusk, clockAt(19, 30).moment)
        assertEquals(KikoTimeOfDay.Night, clockAt(23, 59).moment)
        assertEquals(KikoTimeOfDay.Night, clockAt(2, 0).moment)
        assertEquals("15:00", localClock("UTC", now)?.time)
        assertEquals(null, localClock("Pas/UnFuseau", now))
    }

    @Test
    fun metaLineHidesMissingCity() {
        assertEquals("Bandung · français B1", partner("Ayu").metaLine())
        assertEquals("français B1", partner("Rina", city = null).metaLine())
    }

    @Test
    fun onlineMeansActiveInTheLastFiveMinutes() {
        assertTrue(partner("Ayu", lastSeen = now - 2.minutes).isOnline(now))
        assertFalse(partner("Ayu", lastSeen = now - 10.minutes).isOnline(now))
        assertFalse(partner("Ayu").isOnline(now))
    }

    @Test
    fun filtersAndSearch() {
        val list = listOf(
            partner("Ayu", common = listOf("Cinéma"), lastSeen = now),
            partner("Budi", city = "Jakarta", verified = true),
            partner("Rina", city = null),
        )
        assertEquals(listOf("Ayu"), list.filtered(PartnerFilter.Common, "", now).map { it.firstName })
        assertEquals(listOf("Ayu"), list.filtered(PartnerFilter.Online, "", now).map { it.firstName })
        assertEquals(listOf("Budi"), list.filtered(PartnerFilter.Verified, "", now).map { it.firstName })
        assertEquals(listOf("Budi"), list.filtered(PartnerFilter.All, "jakar", now).map { it.firstName })
        assertEquals(listOf("Ayu"), list.filtered(PartnerFilter.All, "ciné", now).map { it.firstName })
        assertEquals(3, list.filtered(PartnerFilter.All, "  ", now).size)
    }

    @Test
    fun icebreakersStartWithCommonInterests() {
        val ideas = icebreakersFor(listOf("Cuisine", "Inconnu"))
        assertEquals("Cuisine", ideas.first().topic)
        assertTrue(ideas.drop(1).all { it.topic == null })
        assertTrue(ideas.all { it.text.keys == setOf("fr", "id") })
        assertEquals(ideas.first().textIn("fr"), ideas.first().textIn("es"))
    }

    @Test
    fun greetingErrorsBecomeReadableSentences() {
        assertEquals(
            "Tu as déjà salué cette personne. Retrouve-la dans Discussions.",
            partnerMessage(Exception("P0001: already greeted")),
        )
        assertEquals(
            "Le serveur n'est pas encore à jour : il manque la migration Partenaires.",
            partnerMessage(Exception("Could not find the function public.partner_suggestions")),
        )
    }
}
