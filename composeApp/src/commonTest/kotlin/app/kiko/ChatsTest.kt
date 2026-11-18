@file:OptIn(ExperimentalTime::class)

package app.kiko

import app.kiko.chats.ChatFilter
import app.kiko.chats.SkyPeriod
import app.kiko.chats.callSlots
import app.kiko.chats.changedRanges
import app.kiko.chats.dayLabel
import app.kiko.chats.filtered
import app.kiko.chats.listTime
import app.kiko.chats.preview
import app.kiko.chats.replaceWord
import app.kiko.chats.slotTitle
import app.kiko.chats.voiceDuration
import app.kiko.data.CallPayload
import app.kiko.data.ChatMessage
import app.kiko.data.Conversation
import app.kiko.data.ConversationStatus
import app.kiko.data.CorrectionPayload
import app.kiko.data.MessageKind
import app.kiko.data.call
import app.kiko.data.chatMessage
import app.kiko.data.correction
import app.kiko.data.encode
import app.kiko.data.mediaPath
import app.kiko.data.parseTimestamp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        kind: MessageKind = MessageKind.Text,
    ) = Conversation(
        id = name, status = status, requestedByMe = byMe, partnerId = name, partnerName = name,
        partnerVerified = false, partnerLastSeen = null, partnerTimeZone = "UTC", partnerNativeLanguage = "id",
        lastMessage = last, lastMessageMine = mine, lastActivity = now, unread = unread, lastMessageKind = kind,
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

    @Test
    fun mediaAndCorrectionsHaveAReadablePreview() {
        assertEquals("Toi : Photo", conv("Ayu", last = null, mine = true, kind = MessageKind.Photo).preview())
        assertEquals("Message vocal", conv("Ayu", last = null, kind = MessageKind.Voice).preview())
        assertEquals("0:07", voiceDuration(7_400))
        assertEquals("2:00", voiceDuration(120_000))
    }

    @Test
    fun correctionsHighlightOnlyTheChangedWords() {
        val original = "Hier je suis allé au cinéma et j'ai regardé un film génial"
        val corrected = "Hier je suis allée au cinéma et j'ai vu un film génial"
        val changed = changedRanges(original, corrected).map { corrected.substring(it) }
        assertEquals(listOf("allée", "vu"), changed)
        val moved = "Saya sangat suka film itu."
        assertEquals(listOf("sangat"), changedRanges("Saya suka film itu sangat.", moved).map { moved.substring(it) })
        // Deux mots changés côte à côte forment un seul passage.
        val pair = "il est très bien"
        assertEquals(listOf("est très"), changedRanges("il a trop bien", pair).map { pair.substring(it) })
        assertEquals("Pero estoy cansado hoy.", replaceWord("Pero soy cansado hoy.", 1, "estoy"))
        assertEquals("Pero soy cansado hoy.", replaceWord("Pero soy cansado hoy.", 9, "x"))
    }

    @Test
    fun payloadsRoundTripAndBadOnesFallBackToText() {
        val fix = CorrectionPayload("soy cansado", "estoy cansado", "État passager", listOf(CorrectionPayload.TagGrammar))
        val m = ChatMessage("1", "c", "a", fix.encode(), now, MessageKind.Correction, replyToId = "0")
        assertEquals(fix, m.correction())
        assertTrue(fix.important)
        assertEquals(false, CorrectionPayload("a", "b", tags = listOf(CorrectionPayload.TagNatural)).important)
        assertNull(m.copy(body = "pas du json").correction())
        assertNull(m.copy(kind = MessageKind.Text).correction())
        val call = ChatMessage("2", "c", "a", CallPayload(at = now.toString()).encode(), now, MessageKind.CallProposal)
        assertEquals(now, call.call()?.time)
        assertNull(call.copy(body = "{}").call())
        assertEquals("conv/msg.m4a", mediaPath("conv", "msg", "m4a"))
    }

    @Test
    fun callSlotsFallWhenBothAreAwake() {
        val paris = TimeZone.of("Europe/Paris")
        val jakarta = TimeZone.of("Asia/Jakarta")
        val slots = callSlots(now, paris, jakarta)
        assertEquals(3, slots.size)
        slots.forEach {
            assertTrue(it.toLocalDateTime(paris).hour in 8..21, "réveillé à Paris")
            assertTrue(it.toLocalDateTime(jakarta).hour in 8..21, "réveillé à Jakarta")
            assertTrue(it > now)
        }
        // Mercredi 15 h UTC = 16 h à Paris = 22 h à Jakarta : rien ce soir, demain midi chez moi (18 h chez elle).
        assertEquals("Demain · 12:00", slotTitle(slots.first(), now, paris))
    }

    @Test
    fun skyFollowsTheHour() {
        assertEquals(SkyPeriod.Night, SkyPeriod.at(23))
        assertEquals(SkyPeriod.Night, SkyPeriod.at(4))
        assertEquals(SkyPeriod.Dawn, SkyPeriod.at(6))
        assertEquals(SkyPeriod.Day, SkyPeriod.at(12))
        assertEquals(SkyPeriod.Dusk, SkyPeriod.at(19))
    }
}
