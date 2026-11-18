@file:OptIn(ExperimentalTime::class)

package app.kiko.chats

import app.kiko.data.ChatMessage
import app.kiko.data.Conversation
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private fun two(n: Int) = n.toString().padStart(2, '0')

/** Heure d'un message : « 22:41 ». */
fun clockTime(instant: Instant, zone: TimeZone): String {
    val t = instant.toLocalDateTime(zone)
    return "${two(t.hour)}:${two(t.minute)}"
}

private val ShortDays = mapOf(
    DayOfWeek.MONDAY to "Lun.", DayOfWeek.TUESDAY to "Mar.", DayOfWeek.WEDNESDAY to "Mer.",
    DayOfWeek.THURSDAY to "Jeu.", DayOfWeek.FRIDAY to "Ven.", DayOfWeek.SATURDAY to "Sam.", DayOfWeek.SUNDAY to "Dim.",
)

/** Date à droite d'une discussion : l'heure aujourd'hui, « Hier », le jour de la semaine, puis la date. */
fun listTime(instant: Instant, now: Instant, zone: TimeZone): String {
    val day = instant.toLocalDateTime(zone).date
    val today = now.toLocalDateTime(zone).date
    return when {
        day == today -> clockTime(instant, zone)
        day == today.minus(DatePeriod(days = 1)) -> "Hier"
        day > today.minus(DatePeriod(days = 7)) -> ShortDays.getValue(day.dayOfWeek)
        else -> "${two(day.day)}/${two(day.month.ordinal + 1)}"
    }
}

private val MonthNames = listOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
)

/** Étiquette entre deux jours de messages : « Aujourd'hui », « Hier », « 14 novembre ». */
fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Aujourd'hui"
    today.minus(DatePeriod(days = 1)) -> "Hier"
    else -> "${day.day} ${MonthNames[day.month.ordinal]}" + if (day.year != today.year) " ${day.year}" else ""
}

/** Les messages groupés par jour, dans l'ordre. */
fun groupByDay(messages: List<ChatMessage>, zone: TimeZone): List<Pair<LocalDate, List<ChatMessage>>> =
    messages.groupBy { it.createdAt.toLocalDateTime(zone).date }.toList()

/** Aperçu sous le prénom dans la liste. */
fun Conversation.preview(): String = when {
    isIncomingRequest -> "Veut discuter avec toi"
    lastMessage == null -> "Nouvelle discussion"
    isOutgoingRequest -> "Demande envoyée · $lastMessage"
    lastMessageMine -> "Toi : $lastMessage"
    else -> lastMessage
}

/** Filtres en haut de la liste. */
enum class ChatFilter(val label: String) { All("Toutes"), Unread("Non lues"), Requests("Demandes") }

fun List<Conversation>.filtered(filter: ChatFilter, query: String): List<Conversation> {
    val q = query.trim()
    return filter { c ->
        val byFilter = when (filter) {
            ChatFilter.All -> true
            ChatFilter.Unread -> c.unread > 0
            ChatFilter.Requests -> c.isIncomingRequest
        }
        byFilter && (q.isEmpty() || c.partnerName.contains(q, ignoreCase = true) || (c.lastMessage?.contains(q, ignoreCase = true) ?: false))
    }
}
