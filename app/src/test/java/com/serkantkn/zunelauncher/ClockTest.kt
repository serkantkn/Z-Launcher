package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.datastore.ClockDataStore
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.util.cityNameFromZoneId
import com.serkantkn.zunelauncher.util.fastestLapIndex
import com.serkantkn.zunelauncher.util.formatCountdown
import com.serkantkn.zunelauncher.util.formatOffset
import com.serkantkn.zunelauncher.util.formatStopwatch
import com.serkantkn.zunelauncher.util.lapSplits
import com.serkantkn.zunelauncher.util.selectableZoneIds
import com.serkantkn.zunelauncher.util.slowestLapIndex
import com.serkantkn.zunelauncher.util.untilParts
import com.serkantkn.zunelauncher.util.zoneDayOffset
import com.serkantkn.zunelauncher.util.zoneOffsetMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The Clock hub's arithmetic.
 *
 * All of it is a pure function of numbers and zone ids, which is the point of keeping it out of
 * the screens: a countdown that rounds the wrong way, or a stopwatch that loses an hour, is not
 * something worth discovering by watching a phone for an hour.
 */
class ClockTest {

    // ── Reading a stopwatch ─────────────────────────────────────────────────

    @Test
    fun `a stopwatch reads in minutes until there is an hour to show`() {
        assertEquals("00:00.00", formatStopwatch(0L))
        assertEquals("00:05.23", formatStopwatch(5_230L))
        assertEquals("12:34.56", formatStopwatch(12 * 60_000L + 34_560L))
    }

    @Test
    fun `an hour on the stopwatch grows a field rather than rolling over`() {
        // The old code only ever printed minutes, so an hour and a second read as 00:01.
        assertEquals("1:00:01.00", formatStopwatch(3_601_000L))
        assertEquals("2:03:04.05", formatStopwatch(2 * 3_600_000L + 3 * 60_000L + 4_050L))
    }

    @Test
    fun `a negative reading is not a reading`() {
        assertEquals("00:00.00", formatStopwatch(-5_000L))
    }

    // ── Reading a countdown ─────────────────────────────────────────────────

    @Test
    fun `a countdown rounds up, so it only shows zero when it is over`() {
        assertEquals("00:01", formatCountdown(1L))
        assertEquals("00:01", formatCountdown(999L))
        assertEquals("00:01", formatCountdown(1_000L))
        assertEquals("00:00", formatCountdown(0L))
    }

    @Test
    fun `a countdown grows an hour field only when there is an hour left`() {
        assertEquals("05:00", formatCountdown(5 * 60_000L))
        assertEquals("1:00:00", formatCountdown(3_600_000L))
    }

    // ── Laps ────────────────────────────────────────────────────────────────

    @Test
    fun `a lap is the distance from the one before it`() {
        // Newest first: 30s, 20s, 8s on the watch.
        val laps = listOf(30_000L, 20_000L, 8_000L)
        assertEquals(listOf(10_000L, 12_000L, 8_000L), lapSplits(laps))
    }

    @Test
    fun `the fastest and slowest laps are found, but only once there are two`() {
        val splits = lapSplits(listOf(30_000L, 20_000L, 8_000L))
        assertEquals(2, fastestLapIndex(splits)) // the 8s first lap
        assertEquals(1, slowestLapIndex(splits)) // the 12s middle lap
        assertEquals(null, fastestLapIndex(lapSplits(listOf(5_000L))))
        assertEquals(null, slowestLapIndex(emptyList()))
    }

    // ── How long until ──────────────────────────────────────────────────────

    @Test
    fun `the wait is rounded up, never down`() {
        // 90 seconds away is "2 minutes": a number checked against a clock must not run short.
        assertEquals(2, untilParts(0L, 90_000L).minutes)
        assertEquals(1, untilParts(0L, 1L).minutes)
        assertTrue(untilParts(0L, 0L).isNow)
    }

    @Test
    fun `a long wait is split into days, hours and minutes`() {
        val parts = untilParts(0L, (2 * 1440 + 3 * 60 + 4) * 60_000L)
        assertEquals(2, parts.days)
        assertEquals(3, parts.hours)
        assertEquals(4, parts.minutes)
    }

    @Test
    fun `a moment already past is no wait at all`() {
        assertTrue(untilParts(1_000_000L, 500_000L).isNow)
    }

    // ── Somebody else's clock ───────────────────────────────────────────────

    @Test
    fun `an offset keeps its half hours`() {
        // The old hub divided by whole hours, so India's half hour disappeared.
        val utc = TimeZone.getTimeZone("UTC")
        val minutes = zoneOffsetMinutes(SUMMER_NOON, "Asia/Kolkata", utc)
        assertEquals(330, minutes)
        assertEquals("+5:30", formatOffset(minutes))
    }

    @Test
    fun `an offset behind is written with a minus`() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("-4", formatOffset(zoneOffsetMinutes(SUMMER_NOON, "America/New_York", utc)))
    }

    @Test
    fun `being in the same place is no offset at all`() {
        val istanbul = TimeZone.getTimeZone("Europe/Istanbul")
        assertEquals(0, zoneOffsetMinutes(SUMMER_NOON, "Europe/Istanbul", istanbul))
        assertEquals("+0", formatOffset(0))
    }

    @Test
    fun `a zone far enough east is already on tomorrow`() {
        // 23:00 UTC is 08:00 the next day in Tokyo. Reykjavik keeps UTC all year, so it is the
        // one European clock still on the same date — London in June is an hour ahead and is not.
        val lateUtc = utcMillis(2026, Calendar.JUNE, 1, 23, 0)
        assertEquals(1, zoneDayOffset(lateUtc, "Asia/Tokyo", TimeZone.getTimeZone("UTC")))
        assertEquals(0, zoneDayOffset(lateUtc, "Atlantic/Reykjavik", TimeZone.getTimeZone("UTC")))
        assertEquals(1, zoneDayOffset(lateUtc, "Europe/London", TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun `a zone far enough west is still on yesterday`() {
        // 01:00 UTC is 21:00 the previous day in New York.
        val earlyUtc = utcMillis(2026, Calendar.JUNE, 2, 1, 0)
        assertEquals(-1, zoneDayOffset(earlyUtc, "America/New_York", TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun `a city comes out of its zone id readably`() {
        assertEquals("New York", cityNameFromZoneId("America/New_York"))
        assertEquals("Istanbul", cityNameFromZoneId("Europe/Istanbul"))
        assertEquals("Buenos Aires", cityNameFromZoneId("America/Argentina/Buenos_Aires"))
    }

    @Test
    fun `the picker offers places, not fixed offsets and old aliases`() {
        val offered = selectableZoneIds(
            arrayOf("Europe/Istanbul", "Etc/GMT+3", "US/Eastern", "UTC", "America/New_York")
        )
        assertEquals(listOf("America/New_York", "Europe/Istanbul"), offered)
    }

    // ── When an alarm is next due ───────────────────────────────────────────

    @Test
    fun `a one-shot alarm whose time has passed today is due tomorrow`() {
        val alarm = Alarm(hour = 7, minute = 0)
        val now = localMillis(2026, Calendar.JUNE, 1, 9, 0) // Monday morning, already past 07:00
        val next = Calendar.getInstance().apply { timeInMillis = alarm.nextTriggerMillis(now) }
        assertEquals(7, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(2, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `a weekday alarm skips the weekend`() {
        val alarm = Alarm(hour = 7, minute = 0, daysOfWeek = Alarm.WEEKDAYS)
        // Saturday morning: the next weekday 07:00 is Monday.
        val saturday = localMillis(2026, Calendar.JUNE, 6, 9, 0)
        val next = Calendar.getInstance().apply { timeInMillis = alarm.nextTriggerMillis(saturday) }
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `a snooze wins over whenever the alarm would otherwise be due`() {
        val now = localMillis(2026, Calendar.JUNE, 1, 9, 0)
        val alarm = Alarm(hour = 7, minute = 0, snoozedUntilMillis = now + 600_000L)
        assertEquals(now + 600_000L, alarm.nextTriggerMillis(now))
    }

    @Test
    fun `a snooze that has already come and gone is ignored`() {
        val now = localMillis(2026, Calendar.JUNE, 1, 9, 0)
        val alarm = Alarm(hour = 7, minute = 0, snoozedUntilMillis = now - 60_000L)
        assertTrue(alarm.nextTriggerMillis(now) > now)
    }

    @Test
    fun `the week is offered in the reader's own order`() {
        assertEquals(Calendar.MONDAY, Alarm.weekOrder(Locale("tr", "TR")).first())
        assertEquals(Calendar.SUNDAY, Alarm.weekOrder(Locale.US).first())
        assertEquals(7, Alarm.weekOrder(Locale.US).size)
        assertEquals(7, Alarm.weekOrder(Locale("tr", "TR")).toSet().size)
    }

    @Test
    fun `an alarm knows whether it repeats`() {
        assertFalse(Alarm(hour = 7, minute = 0).isRepeating)
        assertTrue(Alarm(hour = 7, minute = 0, daysOfWeek = Alarm.WEEKEND).isRepeating)
    }

    // ── Counters that are moments, not loops ────────────────────────────────

    @Test
    fun `a running stopwatch reads from when it would have started`() {
        val state = ClockDataStore.StopwatchState(isRunning = true, baseMillis = 1_000_000L)
        assertEquals(5_000L, state.readingAt(1_005_000L))
        // This is what makes it survive being killed: the reading is not stored, it is derived.
        assertEquals(60_000L, state.readingAt(1_060_000L))
    }

    @Test
    fun `a stopped stopwatch holds its reading whatever the time says`() {
        val state = ClockDataStore.StopwatchState(isRunning = false, elapsedMillis = 7_500L)
        assertEquals(7_500L, state.readingAt(1_005_000L))
        assertEquals(7_500L, state.readingAt(9_999_999L))
    }

    @Test
    fun `a running timer reads from when it runs out`() {
        val state = ClockDataStore.TimerState(isRunning = true, endsAtMillis = 2_000_000L)
        assertEquals(60_000L, state.remainingAt(1_940_000L))
        // Past its end it is over, not negative.
        assertEquals(0L, state.remainingAt(2_500_000L))
    }

    @Test
    fun `a paused timer keeps what was left of it`() {
        val state = ClockDataStore.TimerState(isRunning = false, remainingMillis = 42_000L)
        assertEquals(42_000L, state.remainingAt(9_999_999L))
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    /** A midsummer noon, so every zone is on the daylight-saving side its offsets are named for. */
    private val SUMMER_NOON = utcMillis(2026, Calendar.JUNE, 1, 12, 0)
}
