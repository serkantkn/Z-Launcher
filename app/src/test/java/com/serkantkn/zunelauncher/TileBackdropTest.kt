package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.ui.components.blurRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileBackdropTest {

    @Test
    fun theBlurGrowsWithTheSlider() {
        val radii = (10..100 step 10).map { blurRadius(it) }
        assertEquals(radii.sorted(), radii)
        assertTrue("the low end only softens", blurRadius(10) <= 1)
        assertEquals(16, blurRadius(100))
    }

    @Test
    fun anythingOutsideTheScaleIsHeldToIt() {
        assertEquals(blurRadius(100), blurRadius(250))
        assertEquals(1, blurRadius(-5))
    }
}
