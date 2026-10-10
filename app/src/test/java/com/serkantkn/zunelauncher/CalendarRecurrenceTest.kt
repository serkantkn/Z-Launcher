package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.util.DAY_MS
import com.serkantkn.zunelauncher.util.allDayLocalEnd
import com.serkantkn.zunelauncher.util.allDayLocalStart
import com.serkantkn.zunelauncher.util.allDayUtcEnd
import com.serkantkn.zunelauncher.util.allDayUtcStart
import com.serkantkn.zunelauncher.util.expandOccurrences
import com.serkantkn.zunelauncher.util.happensOn
import com.serkantkn.zunelauncher.util.nextReminderOccurrence
import com.serkantkn.zunelauncher.util.occurrenceId
import com.serkantkn.zunelauncher.util.occurrenceStarts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * All-day events across zones, and the launcher's own repeats.
 *
 * The phone keeps an all-day event as a UTC midnight; the launcher showed it as a local moment
 * and put a birthday on the evening before. And a weekly event kept by the launcher itself was
 * never expanded, so it happened once. Both are arithmetic, and both are pinned here.
 */
class CalendarRecurrenceTest {

    private val istanbul: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")
    private val losAngeles: TimeZone = TimeZone.getTimeZone("America/Los_Angeles")
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun localMidnight(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun utcMidnight(year: Int, month: Int, day: Int): Long = localMidnight(utc, year, month, day)

    // ── All-day events ──────────────────────────────────────────────────────

    @Test
    fun `an all-day event is written as midnight UTC on its date, east of Greenwich`() {
        val day = localMidnight(istanbul, 2026, Calendar.MARCH, 14)
        assertEquals(utcMidnight(2026, Calendar.MARCH, 14), allDayUtcStart(day, istanbul))
        assertEquals(utcMidnight(2026, Calendar.MARCH, 15), allDayUtcEnd(day, day + DAY_MS, istanbul))
    }

    @Test
    fun `an all-day event is written as midnight UTC on its date, west of Greenwich`() {
        val day = localMidnight(losAngeles, 2026, Calendar.MARCH, 14)
        assertEquals(utcMidnight(2026, Calendar.MARCH, 14), allDayUtcStart(day, losAngeles))
        assertEquals(utcMidnight(2026, Calendar.MARCH, 15), allDayUtcEnd(day, day + DAY_MS, losAngeles))
    }

    @Test
    fun `a moment in the middle of the day still names that day`() {
        val afternoon = localMidnight(istanbul, 2026, Calendar.MARCH, 14, 15, 30)
        assertEquals(utcMidnight(2026, Calendar.MARCH, 14), allDayUtcStart(afternoon, istanbul))
    }

    @Test
    fun `a one-day event ends the day after it starts, never on the same midnight`() {
        val day = localMidnight(istanbul, 2026, Calendar.MARCH, 14)
        assertEquals(utcMidnight(2026, Calendar.MARCH, 15), allDayUtcEnd(day, day, istanbul))
        assertEquals(utcMidnight(2026, Calendar.MARCH, 15), allDayUtcEnd(day, day + 60_000L, istanbul))
    }

    @Test
    fun `a three-day event ends on the fourth midnight`() {
        val day = localMidnight(istanbul, 2026, Calendar.MARCH, 14)
        assertEquals(utcMidnight(2026, Calendar.MARCH, 17), allDayUtcEnd(day, day + 3 * DAY_MS, istanbul))
    }

    @Test
    fun `a UTC midnight reads back as local midnight on the same date`() {
        val stored = utcMidnight(2026, Calendar.MARCH, 14)
        assertEquals(localMidnight(istanbul, 2026, Calendar.MARCH, 14), allDayLocalStart(stored, istanbul))
        assertEquals(localMidnight(losAngeles, 2026, Calendar.MARCH, 14), allDayLocalStart(stored, losAngeles))
        assertEquals(localMidnight(istanbul, 2026, Calendar.MARCH, 15), allDayLocalEnd(stored + DAY_MS, istanbul))
    }

    @Test
    fun `the conversion goes there and back without moving a day`() {
        listOf(istanbul, losAngeles, TimeZone.getTimeZone("Asia/Kolkata"), TimeZone.getTimeZone("Pacific/Auckland")).forEach { zone ->
            val day = localMidnight(zone, 2026, Calendar.OCTOBER, 31)
            assertEquals(zone.id, day, allDayLocalStart(allDayUtcStart(day, zone), zone))
        }
    }

    @Test
    fun `read back as local midnights, an all-day event is on its own day only`() {
        val zone = TimeZone.getDefault()
        val day = localMidnight(zone, 2026, Calendar.MARCH, 14)
        val stored = utcMidnight(2026, Calendar.MARCH, 14)
        val event = CalendarEvent(
            title = "bayram",
            startMillis = allDayLocalStart(stored, zone),
            endMillis = allDayLocalEnd(stored + DAY_MS, zone),
            isAllDay = true
        )
        assertTrue(event.happensOn(day))
        assertTrue(!event.happensOn(day - DAY_MS))
        assertTrue(!event.happensOn(day + DAY_MS))
    }

    // ── Repeats ─────────────────────────────────────────────────────────────

    private fun weekly(start: Long) = CalendarEvent(
        id = "series",
        title = "toplantı",
        startMillis = start,
        endMillis = start + 60 * 60_000L,
        recurrence = "FREQ=WEEKLY",
        reminderMinutes = 10
    )

    @Test
    fun `a weekly event appears once a week over the window`() {
        val zone = TimeZone.getDefault()
        val start = localMidnight(zone, 2026, Calendar.MARCH, 2, 10, 0)
        val starts = occurrenceStarts(weekly(start), start, start + 28 * DAY_MS, zone)
        assertEquals(4, starts.size)
        starts.forEachIndexed { index, moment ->
            val calendar = Calendar.getInstance(zone).apply { timeInMillis = moment }
            assertEquals(10, calendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
            assertEquals(2 + 7 * index, calendar.get(Calendar.DAY_OF_MONTH))
        }
    }

    @Test
    fun `a daily event keeps its hour across the clock change`() {
        val zone = TimeZone.getTimeZone("Europe/Berlin")
        val start = localMidnight(zone, 2026, Calendar.MARCH, 27, 9, 0)
        val starts = occurrenceStarts(
            CalendarEvent(id = "d", title = "sabah", startMillis = start, recurrence = "FREQ=DAILY"),
            start,
            localMidnight(zone, 2026, Calendar.APRIL, 1),
            zone
        )
        // 27th to 31st: five mornings, the clock change on the 29th notwithstanding.
        assertEquals(5, starts.size)
        starts.forEach { moment ->
            assertEquals(9, Calendar.getInstance(zone).apply { timeInMillis = moment }.get(Calendar.HOUR_OF_DAY))
        }
    }

    @Test
    fun `a monthly event on the 31st lands on the last day of shorter months, not the 30th forever`() {
        val zone = TimeZone.getDefault()
        val start = localMidnight(zone, 2026, Calendar.JANUARY, 31, 12, 0)
        val starts = occurrenceStarts(
            CalendarEvent(id = "m", title = "fatura", startMillis = start, recurrence = "FREQ=MONTHLY"),
            start,
            localMidnight(zone, 2026, Calendar.JUNE, 1),
            zone
        )
        val days = starts.map { Calendar.getInstance(zone).apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH) }
        assertEquals(listOf(31, 28, 31, 30, 31), days)
    }

    @Test
    fun `a yearly event comes round once a year`() {
        val zone = TimeZone.getDefault()
        val start = localMidnight(zone, 2024, Calendar.MAY, 5)
        val starts = occurrenceStarts(
            CalendarEvent(id = "y", title = "doğum günü", startMillis = start, endMillis = start + DAY_MS, isAllDay = true, recurrence = "FREQ=YEARLY"),
            localMidnight(zone, 2026, Calendar.JANUARY, 1),
            localMidnight(zone, 2028, Calendar.JANUARY, 1),
            zone
        )
        assertEquals(listOf(2026, 2027), starts.map { Calendar.getInstance(zone).apply { timeInMillis = it }.get(Calendar.YEAR) })
    }

    @Test
    fun `only the occurrences that overlap the window are returned`() {
        val zone = TimeZone.getDefault()
        val start = localMidnight(zone, 2026, Calendar.MARCH, 2, 10, 0)
        val from = start + 10 * DAY_MS
        val starts = occurrenceStarts(weekly(start), from, from + 14 * DAY_MS, zone)
        assertEquals(2, starts.size)
        assertTrue(starts.all { it >= from })
    }

    @Test
    fun `an occurrence taken out of the series leaves a gap`() {
        val zone = TimeZone.getDefault()
        val start = localMidnight(zone, 2026, Calendar.MARCH, 2, 10, 0)
        val second = start + 7 * DAY_MS
        val series = weekly(start).copy(excludedStarts = setOf(second))
        val starts = occurrenceStarts(series, start, start + 28 * DAY_MS, zone)
        assertEquals(3, starts.size)
        assertTrue(second !in starts)
    }

    @Test
    fun `an event that happens once is itself, in the window or not at all`() {
        val once = CalendarEvent(id = "o", title = "bir kez", startMillis = 1_000_000L)
        assertEquals(listOf(once), expandOccurrences(once, 0L, 2_000_000L))
        assertEquals(listOf(1_000_000L), occurrenceStarts(once, 0L, 2_000_000L))
        assertTrue(occurrenceStarts(once, 5_000_000L, 6_000_000L).isEmpty())
    }

    @Test
    fun `expanded occurrences know their series and keep its length`() {
        val zone = TimeZone.getDefault()
        val start = localMidnight(zone, 2026, Calendar.MARCH, 2, 10, 0)
        val occurrences = expandOccurrences(weekly(start), start, start + 14 * DAY_MS, zone)
        assertEquals(2, occurrences.size)
        occurrences.forEach {
            assertEquals("series", it.seriesId)
            assertEquals(it.startMillis, it.instanceStartMillis)
            assertEquals(60 * 60_000L, it.durationMillis)
            assertEquals(occurrenceId("series", it.startMillis), it.id)
            assertTrue(it.isOccurrence)
            assertTrue(it.isRepeating)
        }
    }

    @Test
    fun `the reminder is booked for the next occurrence whose warning is still to come`() {
        val zone = TimeZone.getDefault()
        val start = localMidnight(zone, 2026, Calendar.MARCH, 2, 10, 0)
        // Five minutes before the second week: too late to warn ten minutes ahead of it.
        val now = start + 7 * DAY_MS - 5 * 60_000L
        val next = nextReminderOccurrence(weekly(start), now, zone)!!
        assertEquals(start + 14 * DAY_MS, next.startMillis)
    }

    @Test
    fun `a reminder of an event that happens once is that event, or nothing once it has passed`() {
        val once = CalendarEvent(id = "o", title = "bir kez", startMillis = 10_000_000L, reminderMinutes = 10)
        assertEquals(once, nextReminderOccurrence(once, 1_000_000L))
        assertNull(nextReminderOccurrence(once, 10_000_000L))
        assertNull(nextReminderOccurrence(once.copy(reminderMinutes = null), 0L))
    }
}
