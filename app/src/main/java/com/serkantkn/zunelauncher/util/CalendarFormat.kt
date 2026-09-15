package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.CalendarEvent
import java.util.Calendar
import java.util.Locale

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

const val DAY_MS = 24 * 60 * 60 * 1000L
private const val HOUR_MS = 60 * 60 * 1000L

/** A guard against a malformed event that claims to run for a thousand years. */
private const val MAX_EVENT_DAYS = 400
