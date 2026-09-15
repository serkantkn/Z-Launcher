package com.serkantkn.zunelauncher.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The arithmetic behind the Clock hub, kept away from the screen so it can be tested.
 *
 * Everything here is a pure function of numbers and time-zone ids: no Android, no formatting of
 * words. Whatever needs a translated word (»2 saat sonra«) is handed back as parts and written out
 * by the screen, which is the only place that knows the user's language.
 */

// ── Reading a duration ──────────────────────────────────────────────────────

/**
 * A stopwatch reading: `mm:ss.cc`, growing an hour field only once there is an hour to show.
 *
 * Hundredths rather than milliseconds because that is what a person can read off a running
 * counter — and what every stopwatch since the mechanical ones has shown.
 */
fun formatStopwatch(millis: Long): String {
    val ms = millis.coerceAtLeast(0L)
    val hours = ms / 3_600_000L
    val minutes = (ms / 60_000L) % 60
    val seconds = (ms / 1_000L) % 60
    val hundredths = (ms % 1_000L) / 10
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
    } else {
        String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }
}

/**
 * A countdown reading: `mm:ss`, with an hour field only when there is an hour left.
 *
 * Rounded up, not down: a timer showing 00:00 while it is still running reads as finished when it
 * is not. The last second is shown as 00:01 for its whole length and 00:00 means over.
 */
fun formatCountdown(millis: Long): String {
    val ms = millis.coerceAtLeast(0L)
    val totalSeconds = (ms + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

// ── Laps ────────────────────────────────────────────────────────────────────

/**
 * How long each lap itself took.
 *
 * Laps are kept newest first, each holding the reading on the watch when it was taken — so a lap's
 * own length is the distance to the one before it, and the oldest lap's length is its reading.
 */
fun lapSplits(lapsNewestFirst: List<Long>): List<Long> {
    if (lapsNewestFirst.isEmpty()) return emptyList()
    return lapsNewestFirst.mapIndexed { index, reading ->
        val previous = lapsNewestFirst.getOrNull(index + 1) ?: 0L
        (reading - previous).coerceAtLeast(0L)
    }
}

/** Index of the shortest lap, or null while there is nothing to compare it against. */
fun fastestLapIndex(splits: List<Long>): Int? =
    if (splits.size < 2) null else splits.indices.minByOrNull { splits[it] }

/** Index of the longest lap, or null while there is nothing to compare it against. */
fun slowestLapIndex(splits: List<Long>): Int? =
    if (splits.size < 2) null else splits.indices.maxByOrNull { splits[it] }

// ── How long until something ────────────────────────────────────────────────

/** How far off a moment is, split into the units a person would say it in. */
data class UntilParts(val days: Int, val hours: Int, val minutes: Int) {
    val isNow: Boolean get() = days == 0 && hours == 0 && minutes == 0
}

/**
 * The distance to a moment, rounded up to the next whole minute.
 *
 * Rounded up so an alarm 90 seconds away reads "2 dakika" rather than "1 dakika" — the number a
 * person checks against the clock should never be shorter than the wait actually is.
 */
fun untilParts(fromMillis: Long, toMillis: Long): UntilParts {
    val delta = (toMillis - fromMillis).coerceAtLeast(0L)
    val totalMinutes = ((delta + 59_999L) / 60_000L).toInt()
    return UntilParts(
        days = totalMinutes / 1440,
        hours = (totalMinutes % 1440) / 60,
        minutes = totalMinutes % 60
    )
}

// ── Somewhere else's clock ──────────────────────────────────────────────────

/**
 * How far ahead a zone is of the phone's own, in minutes.
 *
 * Minutes, not hours: half the subcontinent runs on a half hour and Kathmandu on a quarter, and an
 * hours-only figure quietly rounds those away.
 */
fun zoneOffsetMinutes(nowMillis: Long, zoneId: String, localZone: TimeZone = TimeZone.getDefault()): Int {
    val there = TimeZone.getTimeZone(zoneId).getOffset(nowMillis)
    val here = localZone.getOffset(nowMillis)
    return (there - here) / 60_000
}

/** That difference written the way a clock face shows it: `+2`, `-7:30`, `+5:45`. */
fun formatOffset(minutes: Int): String {
    val sign = if (minutes < 0) "-" else "+"
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val rest = absolute % 60
    return if (rest == 0) "$sign$hours" else String.format(Locale.ROOT, "%s%d:%02d", sign, hours, rest)
}

/** Whether a zone is on yesterday's date (-1), today's (0) or tomorrow's (+1) relative to here. */
fun zoneDayOffset(nowMillis: Long, zoneId: String, localZone: TimeZone = TimeZone.getDefault()): Int {
    val here = Calendar.getInstance(localZone).apply { timeInMillis = nowMillis }
    val there = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = nowMillis }
    val hereDay = here.get(Calendar.YEAR) * 1000 + here.get(Calendar.DAY_OF_YEAR)
    val thereDay = there.get(Calendar.YEAR) * 1000 + there.get(Calendar.DAY_OF_YEAR)
    return when {
        thereDay > hereDay -> 1
        thereDay < hereDay -> -1
        else -> 0
    }
}

/**
 * Whether it is dark where that clock is.
 *
 * A flat 07:00–19:00 rule rather than real sunrise, which would need a latitude the launcher does
 * not have and an internet call it should not make to say whether a city is asleep.
 */
fun isNightInZone(nowMillis: Long, zoneId: String): Boolean {
    val hour = Calendar.getInstance(TimeZone.getTimeZone(zoneId))
        .apply { timeInMillis = nowMillis }
        .get(Calendar.HOUR_OF_DAY)
    return hour < DAY_STARTS_HOUR || hour >= DAY_ENDS_HOUR
}

/** The hour of the day in a zone, for drawing a day/night band. */
fun hourInZone(nowMillis: Long, zoneId: String): Int =
    Calendar.getInstance(TimeZone.getTimeZone(zoneId))
        .apply { timeInMillis = nowMillis }
        .get(Calendar.HOUR_OF_DAY)

/**
 * A readable city out of a zone id: `America/New_York` → `New York`.
 *
 * The fallback for the several hundred zones nobody translated by hand. Zones that name a region
 * rather than a city (`Europe/Istanbul` is a city, `America/Argentina/Buenos_Aires` is nested) keep
 * their last segment, which is the city in every case the database has.
 */
fun cityNameFromZoneId(zoneId: String): String =
    zoneId.substringAfterLast('/').replace('_', ' ')

/** The continent half of a zone id, for grouping: `America/New_York` → `America`. */
fun regionFromZoneId(zoneId: String): String =
    zoneId.substringBefore('/', missingDelimiterValue = "")

/**
 * The zones worth offering.
 *
 * Android ships every id the tz database has, including a few hundred that are either historical
 * aliases (`US/Eastern`), fixed offsets (`Etc/GMT+3`) or single-letter military zones. None of
 * those name a place a person would look for, so they are kept out of the picker.
 */
fun selectableZoneIds(all: Array<String>): List<String> = all
    .filter { it.contains('/') }
    .filterNot { it.startsWith("Etc/") || it.startsWith("SystemV/") }
    .filterNot { it.startsWith("US/") || it.startsWith("Canada/") || it.startsWith("Mexico/") }
    .filterNot { it.startsWith("Brazil/") || it.startsWith("Chile/") || it.startsWith("Australia/ACT") }
    .distinct()
    .sorted()

private const val DAY_STARTS_HOUR = 7
private const val DAY_ENDS_HOUR = 19
