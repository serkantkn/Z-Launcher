package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.DisplaySound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplaySoundTest {

    @Test
    fun theUsualTimeoutsAreOffered() {
        assertEquals(DisplaySound.TIMEOUTS, DisplaySound.timeoutChoices(30_000))
    }

    @Test
    fun aTimeoutOfThePhonesOwnIsOfferedAmongThem() {
        val choices = DisplaySound.timeoutChoices(45_000)
        assertTrue(45_000 in choices)
        assertEquals(choices.sorted(), choices)
        assertEquals(DisplaySound.TIMEOUTS.size + 1, choices.size)
    }
}
