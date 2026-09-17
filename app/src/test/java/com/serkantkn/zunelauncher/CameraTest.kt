package com.serkantkn.zunelauncher

import android.view.Surface
import com.serkantkn.zunelauncher.util.CameraGeometry
import com.serkantkn.zunelauncher.util.FrameAlign
import com.serkantkn.zunelauncher.util.PanoramaSheet
import com.serkantkn.zunelauncher.util.averageFrames
import com.serkantkn.zunelauncher.util.brighten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** Sweeping a panorama, stacking a night shot, and the arithmetic of the viewfinder. */
class CameraTest {

    // ── A made-up scene to sweep across ───────────────────────────────────────────────────────
    //
    // The texture is built as one line of values across and another down, so that a frame's
    // column averages carry the first and its row averages carry the second — which is exactly
    // what the real thing reads off a photograph.

    private fun texture(length: Int, seed: Int): IntArray {
        var state = seed
        var value = 128
        return IntArray(length) {
            state = state * 1103515245 + 12345
            value = (value + ((state shr 20) and 0x3F) - 32).coerceIn(20, 235)
            value
        }
    }

    private val across = texture(1200, seed = 7)
    private val down = texture(1200, seed = 31)

    /** A frame of the scene, taken [offsetX] across and [offsetY] down from the origin. */
    private fun frame(
        width: Int = 160,
        height: Int = 120,
        offsetX: Int = 0,
        offsetY: Int = 0,
        gain: Float = 1f,
        lift: Int = 0
    ) = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val grey = (((across[x + offsetX] + down[y + offsetY]) / 2) * gain + lift)
            .toInt().coerceIn(0, 255)
        (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
    }

    private fun profile(pixels: IntArray, width: Int = 160, height: Int = 120) =
        FrameAlign.profileOf(pixels, width, height)!!

    // ── Reading the movement out of the pictures ──────────────────────────────────────────────

    @Test
    fun theSidewaysMovementIsReadOffTheFrames() {
        val previous = profile(frame(offsetX = 40))
        val next = profile(frame(offsetX = 40 + 17))

        val shift = FrameAlign.shiftBetween(previous, next, maxDx = 80, maxDy = 10)

        assertNotNull(shift)
        assertEquals(17, shift!!.dx)
    }

    @Test
    fun turningTheOtherWayComesBackNegative() {
        val previous = profile(frame(offsetX = 40))
        val next = profile(frame(offsetX = 40 - 12))

        assertEquals(-12, FrameAlign.shiftBetween(previous, next, maxDx = 80, maxDy = 10)?.dx)
    }

    @Test
    fun theUpAndDownWanderIsReadTheSameWay() {
        val previous = profile(frame(offsetX = 40, offsetY = 30))
        val next = profile(frame(offsetX = 46, offsetY = 30 + 5))

        val shift = FrameAlign.shiftBetween(previous, next, maxDx = 80, maxDy = 20)

        assertNotNull(shift)
        assertEquals(6, shift!!.dx)
        assertEquals(5, shift.dy)
    }

    @Test
    fun aFrameThatStayedStillSaysSo() {
        val previous = profile(frame(offsetX = 40))
        val next = profile(frame(offsetX = 40))

        assertEquals(0, FrameAlign.shiftBetween(previous, next, maxDx = 80, maxDy = 10)?.dx)
    }

    @Test
    fun aCloudOverTheSunDoesNotMoveTheScene() {
        val previous = profile(frame(offsetX = 40))
        // The same view, a third darker and lifted off black: a different exposure, not a pan.
        val next = profile(frame(offsetX = 40 + 9, gain = 0.66f, lift = 18))

        assertEquals(9, FrameAlign.shiftBetween(previous, next, maxDx = 80, maxDy = 10)?.dx)
    }

    @Test
    fun aBlankWallWillNotSayWhereItWent() {
        val flat = IntArray(160 * 120) { (0xFF shl 24) or 0x808080 }
        val reading = FrameAlign.profileOf(flat, 160, 120)!!

        assertFalse(reading.columnsReadable)
        assertNull(FrameAlign.shiftBetween(reading, reading, maxDx = 80, maxDy = 10))
    }

    @Test
    fun aMatchThatIsNoBetterThanAnyOtherIsNotAMatch() {
        val previous = profile(frame(offsetX = 40))
        val next = profile(frame(offsetX = 40 + 11))

        val (offset, confidence) = FrameAlign.bestOffset(previous.columns, next.columns, 80)!!

        assertEquals(11, offset)
        assertTrue("a real match stands well clear of the rest", confidence > FrameAlign.MIN_CONFIDENCE)
    }

    // ── Where each piece of the sweep goes ────────────────────────────────────────────────────

    private fun sheet() = PanoramaSheet(
        frameWidth = 100,
        frameHeight = 60,
        maxWidth = 400,
        verticalMargin = 6
    )

    @Test
    fun aSweepToTheRightOpensAtTheLeftEdgeWithHalfAFrame() {
        val plan = sheet()

        val opening = plan.start(5)!!

        assertEquals(1, plan.direction)
        assertEquals(0, opening.left)
        assertEquals(0, opening.sourceLeft)
        assertEquals(50, opening.sourceRight)
        assertEquals(6, opening.top)
    }

    @Test
    fun aSweepToTheLeftOpensAtTheRightEdgeInstead() {
        val plan = sheet()

        val opening = plan.start(-5)!!

        assertEquals(-1, plan.direction)
        assertEquals(350, opening.left)
        assertEquals(50, opening.sourceLeft)
        assertEquals(100, opening.sourceRight)
        assertEquals(400, plan.filledLeft + plan.filledWidth)
    }

    @Test
    fun exactlyAsMuchIsLaidDownAsTheSceneMoved() {
        val plan = sheet()
        plan.start(5)

        val paste = plan.advance(10, 0)!!

        assertEquals("it starts where the last piece ended", 50, paste.left)
        assertEquals(10, paste.width)
        assertEquals(60, plan.frontier)
    }

    @Test
    fun theWholeSweepIsOnePieceWithNoGapAndNoOverlap() {
        val plan = sheet()
        plan.start(5)
        var edge = 50

        listOf(10, 7, 22, 4, 31).forEach { step ->
            val paste = plan.advance(step, 0)!!
            assertEquals("piece starts where the last one ended", edge, paste.left)
            edge += paste.width
        }

        assertEquals(edge, plan.frontier)
    }

    @Test
    fun turningBackLaysNothingDownAndGoingOnAgainPicksUpWhereItStopped() {
        val plan = sheet()
        plan.start(5)
        plan.advance(10, 0)

        assertNull("the sheet already holds that ground", plan.advance(-4, 0))
        val paste = plan.advance(9, 0)!!

        assertEquals("only the newly uncovered part", 5, paste.width)
        assertEquals(60, paste.left)
    }

    @Test
    fun theSweepDriftsUpwardsAndThePictureIsTheBandTheyAllShare() {
        val plan = sheet()
        plan.start(5)

        plan.advance(10, 3)
        plan.advance(10, 2)

        assertEquals(11, plan.frameTop)
        assertEquals("the lowest top any frame had", 11, plan.filledTop)
        assertEquals("down to the highest bottom", 55, plan.filledHeight)
    }

    @Test
    fun theWanderCannotLeaveTheSheet() {
        val plan = sheet()
        plan.start(5)

        repeat(20) { plan.advance(10, 5) }

        assertEquals(plan.canvasHeight - plan.frameHeight, plan.frameTop)
    }

    @Test
    fun theSheetFillsUpAndNothingIsLaidPastItsEdge() {
        val plan = sheet()
        plan.start(5)

        var lastEdge = 50
        repeat(20) {
            plan.advance(50, 0)?.let { lastEdge = it.left + it.width }
        }

        assertTrue(plan.isFull)
        assertEquals(400, plan.frontier)
        assertEquals("nothing was drawn past the end", 400, lastEdge)
        assertEquals(400, plan.filledWidth)
    }

    @Test
    fun aSweepThatNeverMovedNeverStarts() {
        val plan = sheet()

        assertNull(plan.start(0))
        assertNull("and nothing can be laid on a sheet that was never opened", plan.advance(10, 0))
    }

    // ── The turn itself ───────────────────────────────────────────────────────────────────────

    @Test
    fun crossingNorthIsASmallTurnNotAWholeCircle() {
        val justPastNorth = (PI - 0.1).toFloat()
        val justBeforeNorth = (-PI + 0.1).toFloat()

        assertEquals(0.2f, PanoramaSheet.shortestAngle(justPastNorth, justBeforeNorth), 0.001f)
        assertEquals(-0.2f, PanoramaSheet.shortestAngle(justBeforeNorth, justPastNorth), 0.001f)
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
        assertEquals(
            "a lens with no range has one setting", 1f,
            CameraGeometry.zoomAfterPinch(1f, 2f, min = 1f, max = 1f), 0.001f
        )
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
