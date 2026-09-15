package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.util.DAY_MS
import com.serkantkn.zunelauncher.util.DayLabel
import com.serkantkn.zunelauncher.util.RepeatKind
import com.serkantkn.zunelauncher.util.agendaDays
import com.serkantkn.zunelauncher.util.dayLabelOf
import com.serkantkn.zunelauncher.util.daysBetween
import com.serkantkn.zunelauncher.util.daysWithEvents
import com.serkantkn.zunelauncher.util.eventsOn
import com.serkantkn.zunelauncher.util.happensOn
import com.serkantkn.zunelauncher.util.isSameDay
import com.serkantkn.zunelauncher.util.matches
import com.serkantkn.zunelauncher.util.monthGrid
import com.serkantkn.zunelauncher.util.repeatKindOf
import com.serkantkn.zunelauncher.util.resolveWeekStart
import com.serkantkn.zunelauncher.util.shiftMonth
import com.serkantkn.zunelauncher.util.startOfDay
import com.serkantkn.zunelauncher.util.startOfWeek
import com.serkantkn.zunelauncher.util.weekDays
import com.serkantkn.zunelauncher.util.weekOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * The Calendar hub's date arithmetic.
 *
 * Calendars are where off-by-one lives: a grid that starts on the wrong weekday, a day that is
 * twenty-three hours long twice a year, an event that finishes at midnight and lands on the wrong
 * day. None of that is visible by looking at the screen for a minute, so it is tested here.
 */
class CalendarTest {

    // ── Days ────────────────────────────────────────────────────────────────

    @Test
    fun `a day begins at midnight and ends before the next one`() {
        val noon = at(2026, Calendar.JUNE, 10, 12, 30)
        val midnight = startOfDay(noon)
        assertTrue(midnight <= noon)
        assertTrue(isSameDay(midnight, noon))
        assertFalse(isSameDay(midnight, noon + DAY_MS))
    }

    @Test
    fun `days are counted a day at a time, not by dividing`() {
        val first = at(2026, Calendar.MARCH, 28, 12, 0)
        val third = at(2026, Calendar.MARCH, 31, 12, 0)
        // The clocks go forward in between, so one of these days is 23 hours long.
        assertEquals(3, daysBetween(first, third))
        assertEquals(-3, daysBetween(third, first))
        assertEquals(0, daysBetween(first, first + 60_000L))
    }

    @Test
    fun `a heading knows today from tomorrow`() {
        val now = at(2026, Calendar.JUNE, 10, 9, 0)
        assertEquals(DayLabel.TODAY, dayLabelOf(now, now))
        assertEquals(DayLabel.TOMORROW, dayLabelOf(now + DAY_MS, now))
        assertEquals(DayLabel.YESTERDAY, dayLabelOf(now - DAY_MS, now))
        assertEquals(DayLabel.OTHER, dayLabelOf(now + 5 * DAY_MS, now))
    }

    // ── Weeks ───────────────────────────────────────────────────────────────

    @Test
    fun `the week starts where the reader's language says it does`() {
        assertEquals(Calendar.MONDAY, resolveWeekStart(0, Locale("tr", "TR")))
        assertEquals(Calendar.SUNDAY, resolveWeekStart(0, Locale.US))
        // A deliberate choice beats the language.
        assertEquals(Calendar.SUNDAY, resolveWeekStart(Calendar.SUNDAY, Locale("tr", "TR")))
    }

    @Test
    fun `the seven days run from whichever one starts the week`() {
        assertEquals(
            listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY),
            weekOrder(Calendar.MONDAY)
        )
        assertEquals(Calendar.SUNDAY, weekOrder(Calendar.SUNDAY).first())
        assertEquals(7, weekOrder(Calendar.SUNDAY).toSet().size)
    }

    @Test
    fun `a week is seven days beginning on its own first day`() {
        // Wednesday 10 June 2026.
        val wednesday = at(2026, Calendar.JUNE, 10, 15, 0)
        val mondayWeek = weekDays(wednesday, Calendar.MONDAY)
        assertEquals(7, mondayWeek.size)
        assertEquals(startOfWeek(wednesday, Calendar.MONDAY), mondayWeek.first())
        assertEquals(
            Calendar.MONDAY,
            Calendar.getInstance().apply { timeInMillis = mondayWeek.first() }.get(Calendar.DAY_OF_WEEK)
        )
        assertEquals(
            Calendar.SUNDAY,
            Calendar.getInstance().apply { timeInMillis = mondayWeek.last() }.get(Calendar.DAY_OF_WEEK)
        )
    }

    // ── The month grid ──────────────────────────────────────────────────────

    @Test
    fun `the grid leaves room for the days before the first`() {
        // 1 June 2026 is a Monday.
        val june = at(2026, Calendar.JUNE, 1, 0, 0)
        val mondayFirst = monthGrid(june, Calendar.MONDAY)
        assertEquals(0, mondayFirst.takeWhile { it == null }.size)
        assertEquals(30, mondayFirst.count { it != null })

        // The same month on a week that starts on Sunday needs one blank before it. The old grid
        // hard-coded a Monday start, so this square was the bug.
        val sundayFirst = monthGrid(june, Calendar.SUNDAY)
        assertEquals(1, sundayFirst.takeWhile { it == null }.size)
        assertEquals(30, sundayFirst.count { it != null })
    }

    @Test
    fun `every square of the grid is its own day`() {
        val grid = monthGrid(at(2026, Calendar.FEBRUARY, 1, 0, 0), Calendar.MONDAY)
        val days = grid.filterNotNull()
        assertEquals(28, days.size)
        assertEquals(days, days.distinct())
        assertEquals(1, Calendar.getInstance().apply { timeInMillis = days.first() }.get(Calendar.DAY_OF_MONTH))
        assertEquals(28, Calendar.getInstance().apply { timeInMillis = days.last() }.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `moving by months keeps the first of the month`() {
        val january = at(2026, Calendar.JANUARY, 31, 12, 0)
        val next = shiftMonth(january, 1)
        val calendar = Calendar.getInstance().apply { timeInMillis = next }
        // Shifting from the 31st must not spill into March.
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH))
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
    }

    // ── Which day an event belongs to ───────────────────────────────────────

    @Test
    fun `an event belongs to every day it runs through`() {
        val friday = startOfDay(at(2026, Calendar.JUNE, 12, 0, 0))
        val event = event(
            start = friday + 22 * HOUR,
            end = friday + DAY_MS + 2 * HOUR
        )
        assertTrue(event.happensOn(friday))
        assertTrue(event.happensOn(friday + DAY_MS))
        assertFalse(event.happensOn(friday - DAY_MS))
        assertFalse(event.happensOn(friday + 2 * DAY_MS))
    }

    @Test
    fun `an event that merely ends at midnight stays on the day it ran`() {
        val day = startOfDay(at(2026, Calendar.JUNE, 12, 0, 0))
        val event = event(start = day + 20 * HOUR, end = day + DAY_MS)
        assertTrue(event.happensOn(day))
        assertFalse(event.happensOn(day + DAY_MS))
    }

    @Test
    fun `a day's events lead with the all-day ones and then run in order`() {
        val day = startOfDay(at(2026, Calendar.JUNE, 12, 0, 0))
        val morning = event(start = day + 9 * HOUR, end = day + 10 * HOUR, title = "morning")
        val evening = event(start = day + 18 * HOUR, end = day + 19 * HOUR, title = "evening")
        val allDay = event(start = day, end = day + DAY_MS, title = "birthday").copy(isAllDay = true)
        val ordered = eventsOn(listOf(evening, morning, allDay), day)
        assertEquals(listOf("birthday", "morning", "evening"), ordered.map { it.title })
    }

    @Test
    fun `the month marks every day something touches`() {
        val day = startOfDay(at(2026, Calendar.JUNE, 12, 0, 0))
        val marked = daysWithEvents(listOf(event(start = day + 22 * HOUR, end = day + DAY_MS + 2 * HOUR)))
        assertEquals(setOf(day, startOfDay(day + DAY_MS)), marked)
    }

    // ── The agenda ──────────────────────────────────────────────────────────

    @Test
    fun `the agenda drops what has already finished`() {
        val now = at(2026, Calendar.JUNE, 12, 12, 0)
        val finished = event(start = now - 3 * HOUR, end = now - 2 * HOUR, title = "gone")
        val running = event(start = now - HOUR, end = now + HOUR, title = "running")
        val later = event(start = now + 5 * HOUR, end = now + 6 * HOUR, title = "later")

        val upcoming = agendaDays(listOf(finished, running, later), now, includePast = false)
            .flatMap { it.second }
            .map { it.title }
        assertEquals(listOf("running", "later"), upcoming)

        val all = agendaDays(listOf(finished, running, later), now, includePast = true)
            .flatMap { it.second }
        assertEquals(3, all.size)
    }

    @Test
    fun `the agenda is grouped by day, soonest day first`() {
        val now = at(2026, Calendar.JUNE, 12, 8, 0)
        val today = event(start = now + HOUR, end = now + 2 * HOUR, title = "today")
        val tomorrow = event(start = now + DAY_MS, end = now + DAY_MS + HOUR, title = "tomorrow")
        val days = agendaDays(listOf(tomorrow, today), now, includePast = false)
        assertEquals(2, days.size)
        assertEquals("today", days.first().second.single().title)
        assertEquals("tomorrow", days.last().second.single().title)
    }

    // ── Searching ───────────────────────────────────────────────────────────

    @Test
    fun `searching folds Turkish, which ignore-case does not`() {
        val exam = event(start = 0L, end = HOUR, title = "Sınav")
        assertTrue(exam.matches("sinav"))
        assertTrue(exam.matches("SINAV"))
        assertTrue(exam.matches("sınav"))
        assertFalse(exam.matches("kargo"))
    }

    @Test
    fun `searching looks at where it is and what it is about, not only its name`() {
        val meeting = event(start = 0L, end = HOUR, title = "Toplantı")
            .copy(location = "Kadıköy", description = "bütçe")
        assertTrue(meeting.matches("kadikoy"))
        assertTrue(meeting.matches("bütçe"))
        assertTrue(meeting.matches(""))
    }

    // ── Repeats ─────────────────────────────────────────────────────────────

    @Test
    fun `a repeat rule is recognised whatever else it carries`() {
        assertEquals(RepeatKind.ONCE, repeatKindOf(null))
        assertEquals(RepeatKind.ONCE, repeatKindOf(""))
        assertEquals(RepeatKind.WEEKLY, repeatKindOf("FREQ=WEEKLY;BYDAY=MO,WE"))
        assertEquals(RepeatKind.YEARLY, repeatKindOf("freq=yearly"))
        assertEquals(RepeatKind.OTHER, repeatKindOf("FREQ=HOURLY"))
    }

    // ── The event itself ────────────────────────────────────────────────────

    @Test
    fun `an event reads its hour off its start rather than storing it twice`() {
        val start = at(2026, Calendar.JUNE, 12, 14, 35)
        val event = event(start = start, end = start + HOUR)
        assertEquals(14, event.hour)
        assertEquals(35, event.minute)
        assertEquals("14:35", event.formattedTime)
        assertEquals(start, event.timestamp)
    }

    @Test
    fun `a duration is never negative, however the event was written`() {
        val start = at(2026, Calendar.JUNE, 12, 14, 0)
        assertEquals(0L, event(start = start, end = start - HOUR).durationMillis)
        assertEquals(HOUR, event(start = start, end = start + HOUR).durationMillis)
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private fun event(start: Long, end: Long, title: String = "event") =
        CalendarEvent(title = title, startMillis = start, endMillis = end)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
