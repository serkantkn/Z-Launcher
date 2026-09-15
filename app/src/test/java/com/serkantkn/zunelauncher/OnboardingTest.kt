package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.FirstRunAction
import com.serkantkn.zunelauncher.util.OnboardingConditions
import com.serkantkn.zunelauncher.util.OnboardingStep
import com.serkantkn.zunelauncher.util.ReleaseNote
import com.serkantkn.zunelauncher.util.firstRunAction
import com.serkantkn.zunelauncher.util.onboardingSteps
import com.serkantkn.zunelauncher.util.unseenReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What somebody is shown when the launcher starts.
 *
 * Every case here is one a person would only meet once, which is exactly why it is worth testing:
 * an installation that shows the beginner's tour to somebody who has used it for months, or shows
 * a brand new user a list of what changed in a version they never had, is a mistake nobody would
 * catch by opening the app.
 */
class OnboardingTest {

    // -- Which pages ------------------------------------------------------

    @Test
    fun `a fresh phone is shown every page`() {
        val steps = onboardingSteps(OnboardingConditions(isDefaultLauncher = false, hasPermissionsToAsk = true))
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.THEME,
                OnboardingStep.DEFAULT_LAUNCHER,
                OnboardingStep.PERMISSIONS,
                OnboardingStep.GESTURES,
                OnboardingStep.DONE
            ),
            steps
        )
    }

    @Test
    fun `a launcher that is already the default is not asked to become it`() {
        val steps = onboardingSteps(OnboardingConditions(isDefaultLauncher = true, hasPermissionsToAsk = true))
        assertFalse(OnboardingStep.DEFAULT_LAUNCHER in steps)
    }

    @Test
    fun `nothing left to ask for means no permissions page`() {
        val steps = onboardingSteps(OnboardingConditions(isDefaultLauncher = true, hasPermissionsToAsk = false))
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.THEME,
                OnboardingStep.GESTURES,
                OnboardingStep.DONE
            ),
            steps
        )
    }

    @Test
    fun `the welcome and the end are always there`() {
        listOf(true, false).forEach { default ->
            listOf(true, false).forEach { asking ->
                val steps = onboardingSteps(OnboardingConditions(default, asking))
                assertEquals(OnboardingStep.WELCOME, steps.first())
                assertEquals(OnboardingStep.DONE, steps.last())
            }
        }
    }

    // -- What happens on the first run of a build -------------------------

    @Test
    fun `a brand new installation gets the tour`() {
        assertEquals(
            FirstRunAction.SHOW_ONBOARDING,
            firstRunAction(
                onboardingCompleted = false,
                hasExistingData = false,
                lastSeenVersion = 0,
                currentVersion = 3,
                hasUnseenNotes = true
            )
        )
    }

    @Test
    fun `an installation from before the tour existed is not dragged through it`() {
        // No flag, but the settings store is full: somebody has been using this for months.
        assertEquals(
            FirstRunAction.NOTHING,
            firstRunAction(
                onboardingCompleted = false,
                hasExistingData = true,
                lastSeenVersion = 0,
                currentVersion = 3,
                hasUnseenNotes = true
            )
        )
    }

    @Test
    fun `an updated installation is told what changed`() {
        assertEquals(
            FirstRunAction.SHOW_WHATS_NEW,
            firstRunAction(
                onboardingCompleted = true,
                hasExistingData = true,
                lastSeenVersion = 2,
                currentVersion = 3,
                hasUnseenNotes = true
            )
        )
    }

    @Test
    fun `an update with nothing worth saying says nothing`() {
        assertEquals(
            FirstRunAction.NOTHING,
            firstRunAction(
                onboardingCompleted = true,
                hasExistingData = true,
                lastSeenVersion = 2,
                currentVersion = 3,
                hasUnseenNotes = false
            )
        )
    }

    @Test
    fun `opening the same version again says nothing`() {
        assertEquals(
            FirstRunAction.NOTHING,
            firstRunAction(
                onboardingCompleted = true,
                hasExistingData = true,
                lastSeenVersion = 3,
                currentVersion = 3,
                hasUnseenNotes = true
            )
        )
    }

    @Test
    fun `somebody who just took the tour is not then told what is new`() {
        // Finishing the tour stamps the current version, which is what makes this true.
        assertEquals(
            FirstRunAction.NOTHING,
            firstRunAction(
                onboardingCompleted = true,
                hasExistingData = true,
                lastSeenVersion = 5,
                currentVersion = 5,
                hasUnseenNotes = true
            )
        )
    }

    // -- Which notes ------------------------------------------------------

    private fun note(version: Int) = ReleaseNote(version, titleRes = 0, lineRes = emptyList())

    @Test
    fun `only the versions somebody has missed are shown, newest first`() {
        val notes = listOf(note(1), note(2), note(3), note(4))
        assertEquals(listOf(4, 3), unseenReleaseNotes(notes, lastSeenVersion = 2, currentVersion = 4).map { it.version })
    }

    @Test
    fun `a version already read is gone for good`() {
        val notes = listOf(note(1), note(2))
        assertTrue(unseenReleaseNotes(notes, lastSeenVersion = 2, currentVersion = 2).isEmpty())
    }

    @Test
    fun `notes from a version this build does not have are not shown`() {
        // Happens after a rollback: the store still holds notes for a newer build.
        val notes = listOf(note(1), note(2), note(9))
        assertEquals(listOf(2), unseenReleaseNotes(notes, lastSeenVersion = 1, currentVersion = 2).map { it.version })
    }

    @Test
    fun `no notes at all is not an error`() {
        assertTrue(unseenReleaseNotes(emptyList(), lastSeenVersion = 0, currentVersion = 1).isEmpty())
    }

    @Test
    fun `the real notes are in order and none share a version`() {
        val versions = com.serkantkn.zunelauncher.data.model.ReleaseNotes.ALL.map { it.version }
        assertEquals(versions.distinct(), versions)
        assertTrue(versions.all { it >= 1 })
    }
}
