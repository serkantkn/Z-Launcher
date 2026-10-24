package com.serkantkn.zeta

import com.serkantkn.zeta.data.billing.ProTrial
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the Pro trial began, and what is left of it, from what was kept of it. */
class ProTrialTest {

    private val day = 24L * 60 * 60 * 1000
    private val after = ProTrial.INTRODUCED_AT + 60 * day

    @Test
    fun aNewInstallBeginsOnItsInstallDay() {
        val installed = after - 2 * day
        assertEquals(installed, ProTrial.earliestStart(stored = emptyList(), installedAt = installed, updatedAt = installed, now = after))
    }

    @Test
    fun clearingTheDataDoesNotStartItAgain() {
        // Nothing kept in the app any more, but the install day is still the install day.
        val installed = after - 40 * day
        val start = ProTrial.earliestStart(stored = emptyList(), installedAt = installed, updatedAt = installed, now = after)
        assertEquals(0L, ProTrial.remainingMillis(start, start, after))
    }

    @Test
    fun aReinstallFindsTheDayKeptOutsideTheApp() {
        // Installed again yesterday; Block Store remembers the trial from a month ago.
        val kept = after - 30 * day
        val start = ProTrial.earliestStart(stored = listOf(kept), installedAt = after - day, updatedAt = after - day, now = after)
        assertEquals(kept, start)
    }

    @Test
    fun anInstallFromBeforeTheTrialHasItsTenDaysFromTheUpdateThatBroughtIt() {
        val updated = after - 3 * day
        val start = ProTrial.earliestStart(stored = emptyList(), installedAt = ProTrial.INTRODUCED_AT - 90 * day, updatedAt = updated, now = after)
        assertEquals(updated, start)
        assertEquals(ProTrial.LENGTH_MILLIS - 3 * day, ProTrial.remainingMillis(start, start, after))
    }

    @Test
    fun anOldInstallClearedOfItsDataDoesNotStartItAgain() {
        val updated = after - 20 * day
        val start = ProTrial.earliestStart(stored = emptyList(), installedAt = ProTrial.INTRODUCED_AT - 90 * day, updatedAt = updated, now = after)
        assertEquals(0L, ProTrial.remainingMillis(start, start, after))
    }

    @Test
    fun aDayFromTheFutureIsNotBelieved() {
        assertEquals(after, ProTrial.earliestStart(stored = listOf(after + 5 * day), installedAt = 0L, updatedAt = 0L, now = after))
    }

    @Test
    fun turningTheClockBackGivesNoDaysBack() {
        val start = after
        val seen = start + 9 * day
        // The clock set a week back: still one day left, as it was when last seen.
        assertEquals(day, ProTrial.remainingMillis(start, seen, seen - 7 * day))
    }

    @Test
    fun partOfADayCountsAsADay() {
        assertEquals(10, ProTrial.daysLeft(ProTrial.LENGTH_MILLIS))
        assertEquals(1, ProTrial.daysLeft(1L))
        assertEquals(0, ProTrial.daysLeft(0L))
    }
}
