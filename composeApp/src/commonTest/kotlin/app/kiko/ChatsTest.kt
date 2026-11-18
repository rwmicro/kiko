@file:OptIn(ExperimentalTime::class)

package app.kiko

import app.kiko.chats.ChatFilter
import app.kiko.chats.dayLabel
import app.kiko.chats.filtered
import app.kiko.chats.listTime
import app.kiko.chats.preview
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import app.kiko.data.chatMessage
import app.kiko.data.parseTimestamp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ChatsTest {
    private val now = Instant.parse("2026-11-18T15:00:00Z") // un mercredi

    private fun conv(
        name: String,
        status: ConversationStatus = ConversationStatus.Accepted,
        byMe: Boolean = true,
        last: String? = "Halo",
        mine: Boolean = false,
        unread: Int = 0,
    ) = Conversation(
        id = name, status = status, requestedByMe = byMe, partnerId = name, partnerName = name,
        partnerVerified = false, partnerLastSeen = null, partnerTimeZone = "UTC", partnerNativeLanguage = "id",
        lastMessage = last, lastMessageMine = mine, lastActivity = now, unread = unread,
    )

    @Test
    fun listTimeShowsHourThenYesterdayThenWeekdayThenDate() {
        val utc = TimeZone.UTC
        assertEquals("13:05", listTime(Instant.parse("2026-11-18T13:05:00Z"), now, utc))
        assertEquals("Hier", listTime(now - 1.days, now, utc))
        assertEquals("Lun.", listTime(now - 2.days, now, utc))
        assertEquals("04/11", listTime(now - 14.days, now, utc))
    }

    @Test
    fun dayLabels() {
        val today = LocalDate(2026, 11, 18)
        assertEquals("Aujourd'hui", dayLabel(today, today))
        assertEquals("Hier", dayLabel(LocalDate(2026, 11, 17), today))
        assertEquals("14 novembre", dayLabel(LocalDate(2026, 11, 14), today))
        assertEquals("2 janvier 2025", dayLabel(LocalDate(2025, 1, 2), today))
    }

    @Test
    fun previewsDependOnWhoWroteAndTheRequest() {
        assertEquals("Veut discuter avec toi", conv("Sari", ConversationStatus.Requested, byMe = false).preview())
        assertEquals("Demande envoyée · Halo", conv("Budi", ConversationStatus.Requested, byMe = true, mine = true).preview())
        assertEquals("Toi : Halo", conv("Ayu", mine = true).preview())
        assertEquals("Halo", conv("Ayu").preview())
    }

    @Test
    fun filters() {
        val list = listOf(
            conv("Ayu", unread = 2),
            conv("Sari", ConversationStatus.Requested, byMe = false, unread = 1),
            conv("Budi", ConversationStatus.Requested, byMe = true),
        )
        assertEquals(listOf("Ayu", "Sari"), list.filtered(ChatFilter.Unread, "").map { it.partnerName })
        assertEquals(listOf("Sari"), list.filtered(ChatFilter.Requests, "").map { it.partnerName })
        assertEquals(listOf("Budi"), list.filtered(ChatFilter.All, "bud").map { it.partnerName })
    }

    @Test
    fun postgresTimestampsAreParsed() {
        val expected = Instant.parse("2026-11-18T15:00:00Z")
        assertEquals(expected, parseTimestamp("2026-11-18T15:00:00+00:00"))
        assertEquals(expected, parseTimestamp("2026-11-18 15:00:00+00"))
        assertEquals(expected + 0.hours, parseTimestamp("2026-11-18T15:00:00"))
        assertEquals(Instant.parse("2026-11-18T15:00:00.123456Z"), parseTimestamp("2026-11-18T15:00:00.123456+00:00"))
        assertNull(parseTimestamp("pas une date"))
    }

    @Test
    fun chatErrorsBecomeReadableSentences() {
        assertEquals("Cette demande a déjà reçu une réponse.", chatMessage(Exception("P0001: no pending request")))
    }
}
