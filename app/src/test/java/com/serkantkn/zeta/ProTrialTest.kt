package com.serkantkn.zeta

import com.serkantkn.zeta.data.billing.ProTrial
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the Pro trial began, and what is left of it, from what was kept of it. */
class ProTrialTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = ProTrial.EPOCH + 60 * day + 15 * 60 * 60 * 1000L

    @Test
    fun nothingKeptMeansNotTaken() {
        assertEquals(0L, ProTrial.earliestStart(listOf(0L, 0L), now))
    }

    @Test
    fun clearingTheDataLeavesTheDayInTheComponents() {
        // Taken eleven days ago at 15:00; the data cleared, only the components' day is left.
        val taken = now - 11 * day
        val fromMarks = ProTrial.startOfMarked(ProTrial.markedDay(taken))
        val start = ProTrial.earliestStart(listOf(0L, fromMarks), now)
        assertEquals(0L, ProTrial.remainingMillis(start, start, now))
    }

    @Test
    fun theDayKeptDoesNotCutTheExactMomentShort() {
        val taken = now - 2 * day
        val fromMarks = ProTrial.startOfMarked(ProTrial.markedDay(taken))
        assertEquals(taken, ProTrial.earliestStart(listOf(taken, fromMarks), now))
    }

    @Test
    fun anEarlierDayKeptWins() {
        // Installed again: the prefs are new, Block Store remembers the trial from a month ago.
        val kept = now - 30 * day
        assertEquals(kept, ProTrial.earliestStart(listOf(now - day, kept), now))
    }

    @Test
    fun aMomentFromTheFutureIsNotBelieved() {
        assertEquals(0L, ProTrial.earliestStart(listOf(now + 5 * day), now))
    }

    @Test
    fun theComponentsKeepTheDay() {
        val taken = now - 3 * day
        val back = ProTrial.startOfMarked(ProTrial.markedDay(taken))
        assert(back <= taken && taken - back < day)
        assertEquals(0, ProTrial.markedDay(0L))
        assertEquals(0L, ProTrial.startOfMarked(0))
    }

    @Test
    fun turningTheClockBackGivesNoDaysBack() {
        val start = now
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
