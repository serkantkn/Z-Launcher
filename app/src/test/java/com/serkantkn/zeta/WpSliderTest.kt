package com.serkantkn.zeta

import com.serkantkn.zeta.ui.components.sliderFraction
import com.serkantkn.zeta.ui.components.sliderValueAt
import com.serkantkn.zeta.ui.components.snapToSteps
import com.serkantkn.zeta.ui.components.sliderStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WpSliderTest {

    @Test
    fun theThumbsMiddleFollowsTheFinger() {
        // 210 across with a 10-wide thumb: the middle travels from 5 to 205.
        assertEquals(0f, sliderValueAt(5f, 210f, 10f, 0f..1f, 0), 0.0001f)
        assertEquals(0.5f, sliderValueAt(105f, 210f, 10f, 0f..1f, 0), 0.0001f)
        assertEquals(1f, sliderValueAt(205f, 210f, 10f, 0f..1f, 0), 0.0001f)
    }

    @Test
    fun aFingerPastTheEndsHoldsAtThem() {
        assertEquals(0f, sliderValueAt(-40f, 210f, 10f, 0f..1f, 0), 0.0001f)
        assertEquals(360f, sliderValueAt(900f, 210f, 10f, 0f..360f, 0), 0.0001f)
    }

    @Test
    fun theValueIsInTheSlidersOwnRange() {
        assertEquals(180f, sliderValueAt(105f, 210f, 10f, 0f..360f, 0), 0.001f)
        assertEquals(0.25f, sliderFraction(90f, 0f..360f), 0.0001f)
        assertEquals(1f, sliderFraction(500f, 0f..360f), 0.0001f)
    }

    @Test
    fun withStepsTheValueLandsOnAStop() {
        // Three steps between 0 and 1: stops at 0, .25, .5, .75 and 1.
        assertEquals(0.25f, snapToSteps(0.3f, 0f..1f, 3), 0.0001f)
        assertEquals(0.75f, snapToSteps(0.7f, 0f..1f, 3), 0.0001f)
        assertEquals(1f, snapToSteps(0.95f, 0f..1f, 3), 0.0001f)
        assertEquals(0.3f, snapToSteps(0.3f, 0f..1f, 0), 0.0001f)
    }

    @Test
    fun anArrowMovesAStopOrATwentiethAndStopsAtTheEnds() {
        // Three steps: a press goes to the next stop.
        assertEquals(0.5f, sliderStep(0.25f, 0f..1f, 3, 1), 0.0001f)
        assertEquals(0f, sliderStep(0.25f, 0f..1f, 3, -1), 0.0001f)
        // No steps: a twentieth of the way.
        assertEquals(55f, sliderStep(50f, 0f..100f, 0, 1), 0.0001f)
        // Never past the ends.
        assertEquals(100f, sliderStep(98f, 0f..100f, 0, 1), 0.0001f)
        assertEquals(0f, sliderStep(0f, 0f..1f, 3, -1), 0.0001f)
    }

    @Test
    fun theSettingsUseWindowsPhonesSlider() {
        val roots = listOf(
            File("src/main/java/com/serkantkn/zeta/ui/screens/settings"),
            File("src/main/java/com/serkantkn/zeta/ui/screens/home/TileLookPage.kt")
        )
        val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        assertTrue("no settings sources found", files.size > 5)
        val offenders = files.filter { "import androidx.compose.material3.Slider" in it.readText() }.map { it.name }
        assertTrue("Material sliders in the settings: $offenders", offenders.isEmpty())
    }
}
