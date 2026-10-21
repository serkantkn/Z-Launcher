package com.serkantkn.zeta

import com.serkantkn.zeta.data.datastore.ClockDataStore
import com.serkantkn.zeta.data.model.Alarm
import com.serkantkn.zeta.util.TimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * What an alarm does between being set and being answered.
 *
 * The audit found a snooze that overwrote the next morning's booking, a reminder re-booked in the
 * past, and a one-shot alarm that came back the day after the phone was off. Each of those is a
 * rule about numbers, and each is pinned here.
 */
class AlarmTest {

    private fun at(hour: Int, minute: Int, dayOfWeek: Int = Calendar.WEDNESDAY): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 11, hour, minute, 0) // a Wednesday
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != dayOfWeek) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    // ── Booking ─────────────────────────────────────────────────────────────

    @Test
    fun `a snooze is booked under a different code from the alarm itself`() {
        repeat(50) {
            val id = UUID.randomUUID().toString()
            assertNotEquals(Alarm.requestCode(id), Alarm.snoozeRequestCode(id))
        }
    }

    @Test
    fun `the codes are stable, so a booking can be cancelled by its id alone`() {
        val id = "alarm-1"
        assertEquals(Alarm.requestCode(id), Alarm.requestCode("alarm-1"))
        assertEquals(Alarm.snoozeRequestCode(id), Alarm.snoozeRequestCode("alarm-1"))
    }

    @Test
    fun `a pending snooze is what is due, whatever the alarm's own time`() {
        val now = at(7, 0)
        val alarm = Alarm(hour = 7, minute = 0, daysOfWeek = Alarm.EVERY_DAY, snoozedUntilMillis = now + 600_000L)
        assertEquals(now + 600_000L, alarm.dueAt(now))
        assertTrue(alarm.isSnoozedAt(now))
    }

    @Test
    fun `the alarm's own next day is unaffected by a snooze`() {
        val now = at(7, 0)
        val snoozed = Alarm(hour = 7, minute = 0, daysOfWeek = Alarm.EVERY_DAY, snoozedUntilMillis = now + 600_000L)
        val own = snoozed.copy(snoozedUntilMillis = null)
        val tomorrow = Calendar.getInstance().apply { timeInMillis = own.dueAt(now)!! }
        assertEquals(Calendar.THURSDAY, tomorrow.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, tomorrow.get(Calendar.HOUR_OF_DAY))
    }

    // ── Reminders already past ──────────────────────────────────────────────

    @Test
    fun `a reminder whose moment has passed is not booked again in the past`() {
        val now = at(12, 0)
        val reminder = Alarm(hour = 9, minute = 0, noteId = "note", exactTimeMillis = now - 3_600_000L)
        assertNull(reminder.dueAt(now))
        // The older rule still says the exact time, for the tile that wants to show it.
        assertEquals(now - 3_600_000L, reminder.nextTriggerMillis(now))
    }

    @Test
    fun `a reminder still to come is booked for its moment`() {
        val now = at(12, 0)
        val reminder = Alarm(hour = 9, minute = 0, noteId = "note", exactTimeMillis = now + 3_600_000L)
        assertEquals(now + 3_600_000L, reminder.dueAt(now))
    }

    // ── Missed while the phone was off ──────────────────────────────────────

    @Test
    fun `a one-shot alarm booked for a moment that has passed was missed`() {
        val now = at(12, 0)
        val alarm = Alarm(hour = 7, minute = 0, bookedForMillis = at(7, 0))
        assertTrue(alarm.isMissedAt(now))
    }

    @Test
    fun `a one-shot alarm booked for later is simply waiting`() {
        val now = at(6, 0)
        val alarm = Alarm(hour = 7, minute = 0, bookedForMillis = at(7, 0))
        assertFalse(alarm.isMissedAt(now))
    }

    @Test
    fun `a repeating alarm is never missed, it moves on to its next day`() {
        val now = at(12, 0)
        val alarm = Alarm(hour = 7, minute = 0, daysOfWeek = Alarm.WEEKDAYS, bookedForMillis = at(7, 0))
        assertFalse(alarm.isMissedAt(now))
    }

    @Test
    fun `a reminder whose moment went by is missed`() {
        val now = at(12, 0)
        val reminder = Alarm(hour = 9, minute = 0, noteId = "note", exactTimeMillis = now - 1L)
        assertTrue(reminder.isMissedAt(now))
    }

    @Test
    fun `a snooze that came and went while the phone was off is missed`() {
        val now = at(12, 0)
        val alarm = Alarm(hour = 7, minute = 0, snoozedUntilMillis = now - 60_000L, bookedForMillis = at(7, 0))
        assertTrue(alarm.isMissedAt(now))
    }

    @Test
    fun `a snooze still pending is not missed`() {
        val now = at(7, 5)
        val alarm = Alarm(hour = 7, minute = 0, snoozedUntilMillis = now + 300_000L, bookedForMillis = at(7, 0))
        assertFalse(alarm.isMissedAt(now))
    }

    @Test
    fun `a switched-off alarm cannot be missed`() {
        val now = at(12, 0)
        val alarm = Alarm(hour = 7, minute = 0, isEnabled = false, bookedForMillis = at(7, 0))
        assertFalse(alarm.isMissedAt(now))
    }

    // ── The stopwatch on the uptime clock ───────────────────────────────────

    @Test
    fun `a running stopwatch counts on the uptime clock`() {
        val wall = 1_700_000_000_000L
        val uptime = 50_000L
        val started = ClockDataStore.StopwatchState(elapsedMillis = 1_000L).started(wall, uptime)
        assertEquals(6_000L, started.readingAt(wall + 5_000L, uptime + 5_000L))
        // A second's drift between the two clocks is noise, not a set clock.
        assertEquals(6_000L, started.readingAt(wall + 5_800L, uptime + 5_000L))
    }

    @Test
    fun `when the wall clock is set, the watch falls back to what it had counted`() {
        val wall = 1_700_000_000_000L
        val uptime = 50_000L
        val started = ClockDataStore.StopwatchState(elapsedMillis = 1_000L).started(wall, uptime)
        // The wall clock jumps an hour while the uptime clock moved five seconds: the base no
        // longer means anything, so the reading is what was counted for certain.
        assertFalse(started.sameClock(wall + 3_600_000L + 5_000L, uptime + 5_000L))
        assertEquals(1_000L, started.readingAt(wall + 3_600_000L + 5_000L, uptime + 5_000L))
    }

    @Test
    fun `after a reboot the watch falls back to what it had counted`() {
        val wall = 1_700_000_000_000L
        val started = ClockDataStore.StopwatchState(elapsedMillis = 4_000L).started(wall, 50_000L)
        // A new boot: uptime is small again, the boot epoch is a different number.
        assertFalse(started.sameClock(wall + 600_000L, 3_000L))
        assertEquals(4_000L, started.readingAt(wall + 600_000L, 3_000L))
    }

    @Test
    fun `stopping freezes the uptime reading`() {
        val wall = 1_700_000_000_000L
        val started = ClockDataStore.StopwatchState().started(wall, 10_000L)
        val stopped = started.stopped(wall + 2_500L, 12_500L)
        assertFalse(stopped.isRunning)
        assertEquals(2_500L, stopped.readingAt(wall + 99_000L, 99_000L))
    }

    // ── One clock setting, every reading ────────────────────────────────────

    @Test
    fun `a clock reading follows the 12 or 24 hour setting`() {
        assertEquals("07:05", TimeText.clock(7, 5, Locale.US, use24h = true))
        assertEquals("7:05 AM", TimeText.clock(7, 5, Locale.US, use24h = false))
        assertEquals("12:30 AM", TimeText.clock(0, 30, Locale.US, use24h = false))
        assertEquals("1:15 PM", TimeText.clock(13, 15, Locale.US, use24h = false))
    }

    @Test
    fun `the stored pattern says which clock it is`() {
        assertTrue(TimeText.is24hPattern("HH:mm"))
        assertFalse(TimeText.is24hPattern("hh:mm a"))
    }

    @Test
    fun `a 12-hour dial maps back onto 24 hours`() {
        assertEquals(12, TimeText.hour12Of(0))
        assertEquals(1, TimeText.hour12Of(13))
        assertEquals(0, TimeText.hour24Of(12, pm = false))
        assertEquals(12, TimeText.hour24Of(12, pm = true))
        assertEquals(13, TimeText.hour24Of(1, pm = true))
        assertTrue(TimeText.isPm(12))
        assertFalse(TimeText.isPm(11))
    }
}
