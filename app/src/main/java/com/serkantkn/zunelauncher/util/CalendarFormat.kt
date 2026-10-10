package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.CalendarEvent
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The Calendar hub's date arithmetic, kept out of the screens so it can be tested.
 *
 * A month grid, which day a week starts on, whether two moments are the same day, which events
 * fall on a given day: all of it is a pure function of numbers, and all of it is the kind of thing
 * that is wrong by exactly one day until somebody writes a test for it.
 */

// ── Days ────────────────────────────────────────────────────────────────────

/** Midnight at the start of the day a moment falls in. */
fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** The last millisecond of that day. */
fun endOfDay(millis: Long): Long = startOfDay(millis) + DAY_MS - 1

/** Whether two moments land on the same calendar day. */
fun isSameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

/** Whole days from one day to another: today to tomorrow is 1, whatever the clocks did. */
fun daysBetween(fromMillis: Long, toMillis: Long): Int {
    val from = Calendar.getInstance().apply { timeInMillis = startOfDay(fromMillis) }
    val to = Calendar.getInstance().apply { timeInMillis = startOfDay(toMillis) }
    var days = 0
    // Counting a day at a time rather than dividing: a day is not always 24 hours long.
    while (from.timeInMillis < to.timeInMillis) {
        from.add(Calendar.DAY_OF_YEAR, 1)
        days++
    }
    while (from.timeInMillis > to.timeInMillis) {
        from.add(Calendar.DAY_OF_YEAR, -1)
        days--
    }
    return days
}

/** How a day heading should read, relative to today. */
enum class DayLabel { YESTERDAY, TODAY, TOMORROW, OTHER }

fun dayLabelOf(dayMillis: Long, nowMillis: Long): DayLabel = when (daysBetween(nowMillis, dayMillis)) {
    -1 -> DayLabel.YESTERDAY
    0 -> DayLabel.TODAY
    1 -> DayLabel.TOMORROW
    else -> DayLabel.OTHER
}

// ── Weeks ───────────────────────────────────────────────────────────────────

/**
 * Which day the week starts on: [stored] is 0 for "whatever the phone's language says", else one
 * of Calendar's own day constants.
 */
fun resolveWeekStart(stored: Int, locale: Locale = Locale.getDefault()): Int =
    if (stored in Calendar.SUNDAY..Calendar.SATURDAY) stored else Calendar.getInstance(locale).firstDayOfWeek

/** The seven weekdays in the order that week runs. */
fun weekOrder(weekStart: Int): List<Int> = (0..6).map { ((weekStart - 1 + it) % 7) + 1 }

/** Midnight on the first day of the week a moment falls in. */
fun startOfWeek(millis: Long, weekStart: Int): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = startOfDay(millis) }
    var guard = 0
    while (calendar.get(Calendar.DAY_OF_WEEK) != weekStart && guard++ < 7) {
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    return calendar.timeInMillis
}

/** The seven days of that week, as midnights. */
fun weekDays(millis: Long, weekStart: Int): List<Long> {
    val first = Calendar.getInstance().apply { timeInMillis = startOfWeek(millis, weekStart) }
    return (0..6).map {
        val day = first.timeInMillis
        first.add(Calendar.DAY_OF_YEAR, 1)
        day
    }
}

// ── Months ──────────────────────────────────────────────────────────────────

/**
 * The squares of a month grid: leading blanks so the first falls under its own weekday, then one
 * entry per day as a midnight.
 *
 * The old grid worked the leading blanks out with `(dayOfWeek + 5) % 7`, which hard-codes a week
 * that starts on Monday — correct in Turkey, one square out in every country whose week starts on
 * Sunday.
 */
fun monthGrid(monthMillis: Long, weekStart: Int): List<Long?> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = startOfDay(monthMillis)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val lead = ((calendar.get(Calendar.DAY_OF_WEEK) - weekStart) + 7) % 7
    val days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return buildList {
        repeat(lead) { add(null) }
        repeat(days) {
            add(calendar.timeInMillis)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

/** The same month, moved by whole months, keeping the first of the month. */
fun shiftMonth(monthMillis: Long, by: Int): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(monthMillis)
    set(Calendar.DAY_OF_MONTH, 1)
    add(Calendar.MONTH, by)
}.timeInMillis

// ── Which events fall where ─────────────────────────────────────────────────

/**
 * Whether an event is happening on a given day.
 *
 * It is an overlap, not an equality: a meeting that runs from Friday evening into Saturday belongs
 * on both days, and an all-day event covers its whole span.
 */
fun CalendarEvent.happensOn(dayStartMillis: Long): Boolean {
    val dayEnd = dayStartMillis + DAY_MS
    // An event that merely ends at midnight belongs to the day before, not to the one starting.
    return startMillis < dayEnd && maxOf(endMillis, startMillis + 1) > dayStartMillis
}

/** The events of a day, earliest first, with all-day ones leading. */
fun eventsOn(events: List<CalendarEvent>, dayStartMillis: Long): List<CalendarEvent> =
    events.filter { it.happensOn(dayStartMillis) }
        .sortedWith(compareBy({ !it.isAllDay }, { it.startMillis }))

/** Every day that has something on it, as midnights — what the month grid marks. */
fun daysWithEvents(events: List<CalendarEvent>): Set<Long> = buildSet {
    events.forEach { event ->
        var day = startOfDay(event.startMillis)
        val last = startOfDay(maxOf(event.endMillis - 1, event.startMillis))
        var guard = 0
        while (day <= last && guard++ < MAX_EVENT_DAYS) {
            add(day)
            day = startOfDay(day + DAY_MS + HOUR_MS)
        }
    }
}

/**
 * The agenda: upcoming events grouped by day, soonest first.
 *
 * [includePast] decides whether the day that has already half happened keeps its finished events —
 * the old agenda claimed to show what was upcoming and in fact listed everything ever entered,
 * oldest first, so the first thing on the page was the least useful thing on it.
 */
fun agendaDays(
    events: List<CalendarEvent>,
    nowMillis: Long,
    includePast: Boolean
): List<Pair<Long, List<CalendarEvent>>> {
    val visible = if (includePast) events else events.filter { it.endMillis > nowMillis }
    return visible
        .groupBy { startOfDay(it.startMillis) }
        .toSortedMap()
        .map { (day, dayEvents) ->
            day to dayEvents.sortedWith(compareBy({ !it.isAllDay }, { it.startMillis }))
        }
}

// ── Searching ───────────────────────────────────────────────────────────────

/** Whether an event answers to a typed query, folded the way the app list folds Turkish. */
fun CalendarEvent.matches(query: String): Boolean {
    val needle = foldForSearch(query.trim())
    if (needle.isEmpty()) return true
    return foldForSearch("$title $description $location").contains(needle)
}

// ── All-day events, which the phone keeps in UTC ────────────────────────────

/**
 * The phone's calendar stores an all-day event as midnight *UTC* on its date, with the end at
 * midnight UTC on the day after, and `eventTimezone` set to UTC. Written as local midnight, an
 * all-day event lands on the wrong day everywhere east or west of Greenwich; read as a local
 * moment, it shows up the evening before. These four do the conversion in both directions.
 */

/** Midnight UTC on the calendar date that [localMillis] falls on in [zone]. */
fun allDayUtcStart(localMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val local = Calendar.getInstance(zone).apply { timeInMillis = localMillis }
    return Calendar.getInstance(UTC).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/**
 * Midnight UTC on the day after the last day of an event that runs from [localStartMillis] to
 * [localEndMillis] (exclusive): a one-day event ends a day after it starts, never on the same
 * midnight. An end that is missing or does not reach past the start day still gives one day.
 */
fun allDayUtcEnd(localStartMillis: Long, localEndMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val first = allDayUtcStart(localStartMillis, zone)
    val lastDay = maxOf(localEndMillis - 1, localStartMillis)
    val last = allDayUtcStart(lastDay, zone)
    return maxOf(last, first) + DAY_MS
}

/** Local midnight on the date that a UTC-midnight [utcMillis] names. */
fun allDayLocalStart(utcMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val utc = Calendar.getInstance(UTC).apply { timeInMillis = utcMillis }
    return Calendar.getInstance(zone).apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Local midnight on the (exclusive) end date a UTC-midnight [utcEndMillis] names. */
fun allDayLocalEnd(utcEndMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    allDayLocalStart(utcEndMillis, zone)

// ── Repeats ─────────────────────────────────────────────────────────────────

/** How a repeat rule should read, without this file knowing any language. */
enum class RepeatKind { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY, OTHER }

fun repeatKindOf(rrule: String?): RepeatKind {
    if (rrule.isNullOrBlank()) return RepeatKind.ONCE
    val rule = rrule.uppercase(Locale.ROOT)
    return when {
        rule.contains("FREQ=DAILY") -> RepeatKind.DAILY
        rule.contains("FREQ=WEEKLY") -> RepeatKind.WEEKLY
        rule.contains("FREQ=MONTHLY") -> RepeatKind.MONTHLY
        rule.contains("FREQ=YEARLY") -> RepeatKind.YEARLY
        else -> RepeatKind.OTHER
    }
}

/**
 * The starts of a repeating event that overlap [fromMillis, toMillis), oldest first.
 *
 * The phone's provider works its own events' repeats out; the launcher's own events nobody else
 * knows about were never expanded at all, so a weekly reminder showed once and then vanished.
 * Each occurrence is counted from the first one — the n-th month after the 31st is the last day
 * of that month, not the 30th forever after — in the phone's own zone, so a daily event stays at
 * nine in the morning across a clock change.
 *
 * A rule this file cannot read ([RepeatKind.OTHER]) is treated as happening once.
 */
fun occurrenceStarts(
    event: CalendarEvent,
    fromMillis: Long,
    toMillis: Long,
    zone: TimeZone = TimeZone.getDefault(),
    limit: Int = MAX_OCCURRENCES
): List<Long> {
    val kind = repeatKindOf(event.recurrence)
    val duration = maxOf(event.durationMillis, 1L)
    val field = when (kind) {
        RepeatKind.DAILY -> Calendar.DAY_OF_YEAR
        RepeatKind.WEEKLY -> Calendar.WEEK_OF_YEAR
        RepeatKind.MONTHLY -> Calendar.MONTH
        RepeatKind.YEARLY -> Calendar.YEAR
        RepeatKind.ONCE, RepeatKind.OTHER -> {
            val once = event.startMillis
            return if (once < toMillis && once + duration > fromMillis) listOf(once) else emptyList()
        }
    }
    val starts = ArrayList<Long>()
    val calendar = Calendar.getInstance(zone)
    var index = 0
    while (index < MAX_STEPS) {
        calendar.timeInMillis = event.startMillis
        calendar.add(field, index)
        val start = calendar.timeInMillis
        if (start >= toMillis) break
        if (start + duration > fromMillis && start !in event.excludedStarts) {
            starts += start
            if (starts.size >= limit) break
        }
        index++
    }
    return starts
}

/** The same event, once per occurrence in the range, each knowing which series it came from. */
fun expandOccurrences(
    event: CalendarEvent,
    fromMillis: Long,
    toMillis: Long,
    zone: TimeZone = TimeZone.getDefault()
): List<CalendarEvent> {
    if (!event.isRepeating) return listOf(event)
    val duration = event.durationMillis
    return occurrenceStarts(event, fromMillis, toMillis, zone).map { start ->
        event.copy(
            id = occurrenceId(event.id, start),
            startMillis = start,
            endMillis = start + duration,
            seriesId = event.id,
            instanceStartMillis = start
        )
    }
}

/** How one occurrence of a series is named: the series' id and the moment. */
fun occurrenceId(seriesId: String, startMillis: Long): String = "$seriesId#$startMillis"

/**
 * The first occurrence whose warning is still to come: the one to book a reminder for. Null
 * when there is no reminder, or the series has run out of the window it is looked at over.
 */
fun nextReminderOccurrence(
    event: CalendarEvent,
    nowMillis: Long,
    zone: TimeZone = TimeZone.getDefault()
): CalendarEvent? {
    val minutes = event.reminderMinutes ?: return null
    val lead = minutes * 60_000L
    if (!event.isRepeating) return event.takeIf { it.startMillis - lead > nowMillis }
    val horizon = nowMillis + REMINDER_HORIZON_MS
    return occurrenceStarts(event, nowMillis, horizon, zone)
        .firstOrNull { it - lead > nowMillis }
        ?.let { start ->
            event.copy(
                id = occurrenceId(event.id, start),
                startMillis = start,
                endMillis = start + event.durationMillis,
                seriesId = event.id,
                instanceStartMillis = start
            )
        }
}

const val DAY_MS = 24 * 60 * 60 * 1000L
private const val HOUR_MS = 60 * 60 * 1000L
private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

/** A guard against a malformed event that claims to run for a thousand years. */
private const val MAX_EVENT_DAYS = 400

/** How many occurrences one series may put into a list, and how far it is stepped to find them. */
private const val MAX_OCCURRENCES = 400
private const val MAX_STEPS = 5_000

/** How far ahead the next reminder of a series is looked for: a yearly event two years out. */
private const val REMINDER_HORIZON_MS = 2 * 366 * DAY_MS
