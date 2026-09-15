package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.GRID_COLUMN_CHOICES
import com.serkantkn.zunelauncher.util.PhotoAdjust
import com.serkantkn.zunelauncher.util.PhotoFilter
import com.serkantkn.zunelauncher.util.autoFixFor
import com.serkantkn.zunelauncher.util.brightnessMatrix
import com.serkantkn.zunelauncher.util.concatMatrix
import com.serkantkn.zunelauncher.util.contrastMatrix
import com.serkantkn.zunelauncher.util.defaultGridColumns
import com.serkantkn.zunelauncher.util.filterMatrix
import com.serkantkn.zunelauncher.util.formatDuration
import com.serkantkn.zunelauncher.util.identityMatrix
import com.serkantkn.zunelauncher.util.photoMatrix
import com.serkantkn.zunelauncher.util.saturationMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gallery's arithmetic and the photo editor's colour maths.
 *
 * Neither touches Android, which is the point: a video's running time and what a slider does to a
 * pixel are both things that should be right before anything is drawn.
 */
class PicturesTest {

    // ── How long a video runs ───────────────────────────────────────────────

    @Test
    fun `a short clip is minutes and seconds`() {
        assertEquals("0:07", formatDuration(6_500L))
        assertEquals("1:23", formatDuration(83_000L))
    }

    @Test
    fun `an hour long recording grows an hours field`() {
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    @Test
    fun `a part second still reads as one second, never as zero`() {
        // A clip that exists should never be labelled "0:00"; rounding up is the honest answer.
        assertEquals("0:01", formatDuration(300L))
    }

    @Test
    fun `a broken duration does not produce a negative time`() {
        assertEquals("0:00", formatDuration(-5_000L))
    }

    // ── How dense the grid starts ───────────────────────────────────────────

    @Test
    fun `a handful of pictures is shown large and a library is shown small`() {
        assertEquals(2, defaultGridColumns(3))
        assertEquals(3, defaultGridColumns(40))
        assertEquals(4, defaultGridColumns(900))
    }

    @Test
    fun `every offered column count is a sensible one`() {
        assertEquals(listOf(2, 3, 4, 5), GRID_COLUMN_CHOICES)
    }

    // ── The editor's colour matrices ────────────────────────────────────────

    @Test
    fun `doing nothing to a picture leaves the identity matrix`() {
        assertArrayAlmostEquals(identityMatrix(), photoMatrix(PhotoAdjust(), PhotoFilter.NONE))
    }

    @Test
    fun `brightness moves every channel by the same offset`() {
        val matrix = brightnessMatrix(0.5f)
        assertEquals(50f, matrix[4], 0.001f)
        assertEquals(50f, matrix[9], 0.001f)
        assertEquals(50f, matrix[14], 0.001f)
        // Alpha is left alone, or the picture fades instead of brightening.
        assertEquals(1f, matrix[18], 0.001f)
        assertEquals(0f, matrix[19], 0.001f)
    }

    @Test
    fun `contrast pivots around mid grey`() {
        val matrix = contrastMatrix(1f)
        // Doubling contrast must leave 128 where it is: 128 * 2 - 128 = 128.
        assertEquals(128f, 128f * matrix[0] + matrix[4], 0.001f)
    }

    @Test
    fun `full desaturation gives all three channels the same luminance weights`() {
        val matrix = saturationMatrix(-1f)
        for (channel in 0 until 3) {
            assertEquals(0.213f, matrix[channel * 5], 0.001f)
            assertEquals(0.715f, matrix[channel * 5 + 1], 0.001f)
            assertEquals(0.072f, matrix[channel * 5 + 2], 0.001f)
        }
    }

    @Test
    fun `concatenation carries the first matrix's offset through the second`() {
        // Brighten by 50, then double: the offset must be doubled too, not kept at 50.
        val combined = concatMatrix(contrastMatrix(1f), brightnessMatrix(0.5f))
        val red = 100f
        val throughCombined = red * combined[0] + combined[4]
        val stepByStep = ((red + 50f) * 2f) - 128f
        assertEquals(stepByStep, throughCombined, 0.001f)
    }

    @Test
    fun `the plain filter changes nothing`() {
        assertArrayAlmostEquals(identityMatrix(), filterMatrix(PhotoFilter.NONE))
    }

    @Test
    fun `mono leaves no colour behind`() {
        val matrix = filterMatrix(PhotoFilter.MONO)
        // A strongly red pixel and a strongly blue one of the same luminance come out alike.
        val red = 200f * matrix[0] + 0f * matrix[1] + 0f * matrix[2] + matrix[4]
        val redAsBlue = 200f * matrix[10] + 0f * matrix[11] + 0f * matrix[12] + matrix[14]
        assertEquals(red, redAsBlue, 0.001f)
    }

    @Test
    fun `every filter keeps alpha untouched`() {
        PhotoFilter.entries.forEach { filter ->
            val matrix = filterMatrix(filter)
            assertEquals("alpha row of $filter", 1f, matrix[18], 0.001f)
            assertEquals("alpha offset of $filter", 0f, matrix[19], 0.001f)
        }
    }

    // ── Auto fix ────────────────────────────────────────────────────────────

    @Test
    fun `a picture already using the whole range is left alone`() {
        val fix = autoFixFor(darkest = 0, lightest = 255, average = 128)
        assertEquals(0f, fix.contrast, 0.02f)
        assertEquals(0f, fix.brightness, 0.02f)
    }

    @Test
    fun `a flat picture is stretched`() {
        // Everything between 80 and 160: half the range unused, so contrast goes up.
        val fix = autoFixFor(darkest = 80, lightest = 160, average = 120)
        assertTrue("expected contrast to rise, was ${fix.contrast}", fix.contrast > 0.5f)
    }

    @Test
    fun `a dark picture is lifted and a bright one is pulled down`() {
        val dark = autoFixFor(darkest = 0, lightest = 200, average = 40)
        assertTrue("expected a lift, was ${dark.brightness}", dark.brightness > 0f)

        val bright = autoFixFor(darkest = 40, lightest = 255, average = 230)
        assertTrue("expected a drop, was ${bright.brightness}", bright.brightness < 0f)
    }

    @Test
    fun `a picture with no range at all is not divided by zero`() {
        val fix = autoFixFor(darkest = 128, lightest = 128, average = 128)
        assertEquals(PhotoAdjust(), fix)
    }

    @Test
    fun `auto fix never asks for more than the sliders can give`() {
        val fix = autoFixFor(darkest = 250, lightest = 255, average = 252)
        assertTrue(fix.contrast in 0f..1f)
        assertTrue(fix.brightness in -1f..1f)
    }

    private fun assertArrayAlmostEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals("at $index", expected[index], actual[index], 0.001f)
        }
    }
}
