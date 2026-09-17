package com.serkantkn.zunelauncher

import android.view.Surface
import com.serkantkn.zunelauncher.util.CameraGeometry
import com.serkantkn.zunelauncher.util.PanoramaPlanner
import com.serkantkn.zunelauncher.util.averageFrames
import com.serkantkn.zunelauncher.util.brighten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** Sweeping a panorama, and stacking a night shot. */
class CameraTest {

    // A 1000px frame across a 1 radian lens: a radian of turn slides the scene 1000px.
    private fun planner(
        stripWidth: Int = 50,
        maxWidth: Int = 1000
    ) = PanoramaPlanner(frameWidth = 1000, stripWidth = stripWidth, horizontalFov = 1f, maxWidth = maxWidth)

    // ── Where the strips land ─────────────────────────────────────────────────────────────────

    @Test
    fun theFirstReadingStartsThePictureRatherThanMovingIt() {
        val plan = planner()

        assertEquals(0, plan.onYaw(0f))
        assertEquals(0, plan.nextStripAt)
    }

    @Test
    fun aStripIsLaidDownEveryTimeTheSceneHasMovedItsWidth() {
        val plan = planner(stripWidth = 50)
        plan.onYaw(0f)

        // 50px of scene is 0.05 radians at this geometry.
        assertNull("not yet a strip's worth", plan.onYaw(0.02f))
        assertEquals(50, plan.onYaw(0.06f))
        assertEquals(100, plan.onYaw(0.11f))
    }

    @Test
    fun theSweepPicksItsDirectionFromTheFirstRealMovement() {
        val plan = planner()
        plan.onYaw(0f)

        plan.onYaw(-0.06f)

        assertEquals("turning left is a sweep too", -1, plan.direction)
        assertEquals(50, plan.nextStripAt)
    }

    @Test
    fun aWobbleDoesNotDecideTheDirection() {
        val plan = planner()
        plan.onYaw(0f)

        plan.onYaw(0.001f)   // one pixel of movement

        assertEquals(0, plan.direction)
    }

    @Test
    fun turningBackDoesNotUnwindThePicture() {
        val plan = planner()
        plan.onYaw(0f)
        plan.onYaw(0.06f)

        assertNull(plan.onYaw(0.0f))
        assertNull(plan.onYaw(0.03f))
        assertEquals("only new ground adds a strip", 50, plan.nextStripAt)
    }

    @Test
    fun theSweepStopsWhenThePictureIsAsWideAsItCanBe() {
        val plan = planner(stripWidth = 50, maxWidth = 200)
        plan.onYaw(0f)

        var yaw = 0f
        repeat(10) {
            yaw += 0.06f
            plan.onYaw(yaw)
        }

        assertTrue(plan.isFull)
        assertEquals(200, plan.canvasWidth())
        assertEquals(1f, plan.progress, 0.01f)
    }

    @Test
    fun theStripIsCutFromTheMiddleOfTheFrame() {
        assertEquals(475, planner(stripWidth = 50).sourceLeft())
    }

    // ── The geometry behind it ────────────────────────────────────────────────────────────────

    @Test
    fun aWiderLensMovesTheSceneLessForTheSameTurn() {
        val narrow = PanoramaPlanner.pixelsPerRadian(1000, 1f)
        val wide = PanoramaPlanner.pixelsPerRadian(1000, 2f)

        assertTrue(wide < narrow)
        assertEquals(1000f, narrow, 0.01f)
    }

    @Test
    fun aLensThatWillNotSayHowWideItSeesGetsATypicalPhoneLens() {
        val guessed = PanoramaPlanner.pixelsPerRadian(1000, 0f)

        assertEquals(1000f / PanoramaPlanner.FALLBACK_FOV_RADIANS, guessed, 0.01f)
        assertEquals(guessed, PanoramaPlanner.pixelsPerRadian(1000, Float.NaN), 0.01f)
    }

    @Test
    fun crossingNorthIsASmallTurnNotAWholeCircle() {
        val justPastNorth = (PI - 0.1).toFloat()
        val justBeforeNorth = (-PI + 0.1).toFloat()

        assertEquals(0.2f, PanoramaPlanner.shortestAngle(justPastNorth, justBeforeNorth), 0.001f)
        assertEquals(-0.2f, PanoramaPlanner.shortestAngle(justBeforeNorth, justPastNorth), 0.001f)
    }

    @Test
    fun aSweepAcrossNorthKeepsGoingRatherThanTurningRound() {
        val plan = planner()
        plan.onYaw((PI - 0.02).toFloat())

        val landed = plan.onYaw((-PI + 0.04).toFloat())

        assertEquals(1, plan.direction)
        assertEquals(50, landed)
    }

    // ── Night: frames added together ──────────────────────────────────────────────────────────

    private fun grey(value: Int, size: Int = 4) =
        IntArray(size) { (0xFF shl 24) or (value shl 16) or (value shl 8) or value }

    @Test
    fun stackedFramesAverageOutTheNoise() {
        val stacked = averageFrames(listOf(grey(100), grey(140), grey(120), grey(120)))

        assertNotNull(stacked)
        assertEquals(120, (stacked!![0] shr 16) and 0xFF)
        assertEquals(120, (stacked[0] shr 8) and 0xFF)
        assertEquals(120, stacked[0] and 0xFF)
    }

    @Test
    fun oneFrameIsAlreadyItsOwnAverage() {
        val single = grey(77)

        assertEquals(single.toList(), averageFrames(listOf(single))?.toList())
    }

    @Test
    fun framesOfDifferentSizesCannotBeStacked() {
        assertNull(averageFrames(listOf(grey(10, size = 4), grey(10, size = 6))))
        assertNull(averageFrames(emptyList()))
    }

    @Test
    fun aStackedShotStaysFullyOpaque() {
        val stacked = averageFrames(listOf(grey(10), grey(20)))!!

        assertEquals(0xFF, (stacked[0] ushr 24) and 0xFF)
    }

    // ── Night: lifting the dark ───────────────────────────────────────────────────────────────

    @Test
    fun brighteningLiftsTheDarkAndLeavesTheLightAlone() {
        val lifted = brighten(grey(60) + grey(250), 1.6f)

        val dark = (lifted[0] shr 16) and 0xFF
        val bright = (lifted[4] shr 16) and 0xFF
        assertTrue("the dark comes up", dark > 60)
        assertTrue("nothing is pushed past white", bright <= 255)
        assertTrue("the bright end is not crushed", bright >= 250)
    }

    @Test
    fun blackStaysBlackAndNoGainChangesNothing() {
        assertEquals(0, (brighten(grey(0), 1.6f)[0] shr 16) and 0xFF)
        assertEquals(grey(60).toList(), brighten(grey(60), 1f).toList())
    }

    // ── Pinching the viewfinder ───────────────────────────────────────────────────────────────

    @Test
    fun aPinchMovesTheZoomByAProportionRatherThanAnAmount() {
        assertEquals(2f, CameraGeometry.zoomAfterPinch(1f, 2f, min = 1f, max = 10f), 0.001f)
        assertEquals(8f, CameraGeometry.zoomAfterPinch(4f, 2f, min = 1f, max = 10f), 0.001f)
    }

    @Test
    fun aPinchCannotTakeTheLensPastWhatItCanDo() {
        assertEquals(10f, CameraGeometry.zoomAfterPinch(8f, 4f, min = 1f, max = 10f), 0.001f)
        assertEquals(1f, CameraGeometry.zoomAfterPinch(1.2f, 0.1f, min = 1f, max = 10f), 0.001f)
    }

    @Test
    fun anImpossibleFactorLeavesTheZoomWhereItWas() {
        assertEquals(3f, CameraGeometry.zoomAfterPinch(3f, Float.NaN, min = 1f, max = 10f), 0.001f)
        assertEquals(3f, CameraGeometry.zoomAfterPinch(3f, 0f, min = 1f, max = 10f), 0.001f)
        assertEquals("a lens with no range has one setting", 1f,
            CameraGeometry.zoomAfterPinch(1f, 2f, min = 1f, max = 1f), 0.001f)
    }

    @Test
    fun aLensThatDoesNotZoomIsNotAskedTo() {
        assertTrue(CameraGeometry.canZoom(min = 1f, max = 10f))
        assertFalse(CameraGeometry.canZoom(min = 1f, max = 1f))
        assertFalse("a hair of range is not a zoom", CameraGeometry.canZoom(min = 1f, max = 1.005f))
    }

    // ── Which way up the phone is held ────────────────────────────────────────────────────────

    @Test
    fun eachQuarterTurnIsRoundedToTheNearestWayUp() {
        assertEquals(Surface.ROTATION_0, CameraGeometry.surfaceRotationFor(0))
        assertEquals(Surface.ROTATION_0, CameraGeometry.surfaceRotationFor(44))
        assertEquals(Surface.ROTATION_270, CameraGeometry.surfaceRotationFor(90))
        assertEquals(Surface.ROTATION_180, CameraGeometry.surfaceRotationFor(180))
        assertEquals(Surface.ROTATION_90, CameraGeometry.surfaceRotationFor(270))
        assertEquals(Surface.ROTATION_0, CameraGeometry.surfaceRotationFor(350))
    }

    @Test
    fun aPhoneLyingFlatIsTakenAsUpright() {
        // The sensor says -1 when it cannot tell, which is most of the time on a table.
        assertEquals(Surface.ROTATION_0, CameraGeometry.surfaceRotationFor(-1))
    }

    @Test
    fun aReadingPastAFullCircleStillMeansSomething() {
        assertEquals(Surface.ROTATION_270, CameraGeometry.surfaceRotationFor(450))
    }
}
