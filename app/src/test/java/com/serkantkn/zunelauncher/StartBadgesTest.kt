package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.StartBadges
import org.junit.Assert.assertEquals
import org.junit.Test

/** The count a messaging or phone tile shows follows the app that holds the role. */
class StartBadgesTest {

    private val self = "com.serkantkn.zunelauncher"
    private val counts = mapOf(
        "com.google.android.apps.messaging" to 3,
        "org.other.sms" to 7,
        "com.google.android.dialer" to 2
    )

    @Test
    fun `the role holder's notifications count, not Google's by name`() {
        assertEquals(7, StartBadges.count(0, counts, "org.other.sms", self))
        assertEquals(3, StartBadges.count(0, counts, "com.google.android.apps.messaging", self))
    }

    @Test
    fun `the larger of the launcher's own count and the app's wins`() {
        assertEquals(5, StartBadges.count(5, counts, "com.google.android.dialer", self))
        assertEquals(2, StartBadges.count(1, counts, "com.google.android.dialer", self))
    }

    @Test
    fun `the launcher holding the role keeps its own count`() {
        assertEquals(4, StartBadges.count(4, counts + (self to 9), self, self))
        assertEquals(0, StartBadges.count(0, counts + (self to 9), self, self))
    }

    @Test
    fun `an unknown role holder falls back to the launcher's count`() {
        assertEquals(2, StartBadges.count(2, counts, null, self))
        assertEquals(0, StartBadges.count(0, counts, "com.nobody.sms", self))
    }
}
