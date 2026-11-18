@file:OptIn(ExperimentalTime::class)

package app.kiko.chats

import app.kiko.data.ChatMessage
import app.kiko.data.Conversation
import app.kiko.data.MessageKind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes
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

/** Ce qu'on montre d'un message qui n'est pas du texte. */
fun kindLabel(kind: MessageKind): String? = when (kind) {
    MessageKind.Text -> null
    MessageKind.Photo -> "Photo"
    MessageKind.Voice -> "Message vocal"
    MessageKind.Correction -> "Correction"
    MessageKind.CallProposal -> "Appel"
}

/** Aperçu sous le prénom dans la liste. */
fun Conversation.preview(): String {
    val last = kindLabel(lastMessageKind) ?: lastMessage
    return when {
        isIncomingRequest -> "Veut discuter avec toi"
        last == null -> "Nouvelle discussion"
        isOutgoingRequest -> "Demande envoyée · $last"
        lastMessageMine -> "Toi : $last"
        else -> last
    }
}

/** Durée d'un vocal : « 0:07 ». */
fun voiceDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${two((s % 60).toInt())}"
}

/** Les mots d'une phrase, avec leur place dans le texte. */
fun words(text: String): List<Pair<String, IntRange>> =
    Regex("\\S+").findAll(text).map { it.value to it.range }.toList()

/**
 * Les passages de [corrected] qui changent par rapport à [original], mot à mot
 * (plus longue sous-suite commune). Des mots changés côte à côte forment un seul passage.
 */
fun changedRanges(original: String, corrected: String): List<IntRange> {
    // La ponctuation collée au mot ne compte pas : « itu » et « itu. » sont le même mot.
    fun bare(w: String) = w.trim { !it.isLetterOrDigit() }.ifEmpty { w }
    val a = words(original).map { bare(it.first) }
    val b = words(corrected).map { bare(it.first) to it.second }
    val n = a.size
    val m = b.size
    val lcs = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
        lcs[i][j] = if (a[i] == b[j].first) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
    }
    val kept = BooleanArray(m)
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            a[i] == b[j].first -> { kept[j] = true; i++; j++ }
            lcs[i + 1][j] >= lcs[i][j + 1] -> i++
            else -> j++
        }
    }
    val ranges = mutableListOf<IntRange>()
    for (k in 0 until m) {
        if (kept[k]) continue
        val r = b[k].second
        val prev = ranges.lastOrNull()
        if (prev != null && k > 0 && !kept[k - 1]) ranges[ranges.lastIndex] = prev.first..r.last else ranges += r
    }
    return ranges
}

/** Remplace le mot n° [index] de [text] par [replacement], sans toucher au reste. */
fun replaceWord(text: String, index: Int, replacement: String): String {
    val w = words(text).getOrNull(index) ?: return text
    return text.substring(0, w.second.first) + replacement + text.substring(w.second.last + 1)
}

/**
 * Créneaux d'appel proposés (maquette 19) : un par jour, à une heure de veille pour les deux
 * (8 h à 22 h de chaque côté), en commençant par les soirées.
 */
fun callSlots(now: Instant, mine: TimeZone, theirs: TimeZone, count: Int = 3): List<Instant> {
    val preferred = listOf(20, 21, 19, 18, 12, 13, 11, 10, 9, 8, 14, 15, 16, 17)
    val today = now.toLocalDateTime(mine).date
    val slots = mutableListOf<Instant>()
    for (d in 0 until 14) {
        val day = today.plus(DatePeriod(days = d))
        val pick = preferred.asSequence()
            .map { LocalDateTime(day, LocalTime(it, 0)).toInstant(mine) }
            .firstOrNull { at -> at > now + 30.minutes && at.toLocalDateTime(theirs).hour in 8..21 }
        if (pick != null) slots += pick
        if (slots.size == count) break
    }
    return slots
}

private val LongDays = mapOf(
    DayOfWeek.MONDAY to "Lundi", DayOfWeek.TUESDAY to "Mardi", DayOfWeek.WEDNESDAY to "Mercredi",
    DayOfWeek.THURSDAY to "Jeudi", DayOfWeek.FRIDAY to "Vendredi", DayOfWeek.SATURDAY to "Samedi", DayOfWeek.SUNDAY to "Dimanche",
)

/** « Ce soir · 20:00 », « Demain · 11:00 », « Jeudi · 21:00 », « 2 décembre · 9:00 ». */
fun slotTitle(at: Instant, now: Instant, zone: TimeZone): String {
    val t = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    val day = when {
        t.date == today && t.hour >= 18 -> "Ce soir"
        t.date == today -> "Aujourd'hui"
        t.date == today.plus(DatePeriod(days = 1)) -> "Demain"
        t.date < today.plus(DatePeriod(days = 7)) -> LongDays.getValue(t.date.dayOfWeek)
        else -> "${t.date.day} ${MonthNames[t.date.month.ordinal]}"
    }
    return "$day · ${clockTime(at, zone)}"
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
