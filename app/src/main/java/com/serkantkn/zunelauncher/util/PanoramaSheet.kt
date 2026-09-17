package com.serkantkn.zunelauncher.util

import kotlin.math.PI

/**
 * The sheet a panorama is laid out on, and the bookkeeping of where the next piece of it goes.
 *
 * The sheet is in scene coordinates: a column of it holds one direction, whatever frame happened
 * to see it. Each frame is placed at the point the measured movement puts it, and only the part
 * of it that is new — the gap between what has already been laid down and the middle of where the
 * frame now sits — is copied across. Taking from the middle rather than the edge keeps the lens's
 * own bending out of the finished picture.
 *
 * Nothing here knows about cameras, sensors or bitmaps: it is where things go, not what they are.
 */
class PanoramaSheet(
    val frameWidth: Int,
    val frameHeight: Int,
    val maxWidth: Int,
    /** Headroom above and below, so a sweep that wanders up or down has somewhere to wander. */
    val verticalMargin: Int
) {
    /** What to copy out of this frame, and where to put it. */
    data class Paste(val sourceLeft: Int, val sourceRight: Int, val left: Int, val top: Int) {
        val width: Int get() = sourceRight - sourceLeft
    }

    val canvasHeight: Int = frameHeight + verticalMargin * 2

    /** Which way the sweep is going, fixed by the movement that started it. */
    var direction: Int = 0
        private set

    /** Where the frame now sits on the sheet. */
    var frameLeft: Int = 0
        private set
    var frameTop: Int = verticalMargin
        private set

    /** How far along the sheet has been laid down; the edge new work is added at. */
    var frontier: Int = 0
        private set

    /** True once the sheet has been filled to its far edge. */
    var isFull: Boolean = false
        private set

    private var started = false
    private var recorded = false
    private var topUsed = 0
    private var bottomUsed = 0

    /**
     * Opens the sheet with the frame in hand, going whichever way [dx] says.
     *
     * A sweep to the right fills the sheet from its left edge and a sweep to the left fills it
     * from its right, so that the finished picture reads the way the world does either way.
     */
    fun start(dx: Int): Paste? {
        if (started || dx == 0) return null
        started = true
        direction = if (dx > 0) 1 else -1
        val half = frameWidth / 2
        frameTop = verticalMargin
        return if (direction > 0) {
            frameLeft = 0
            frontier = half
            record(Paste(sourceLeft = 0, sourceRight = half, left = 0, top = frameTop))
        } else {
            frameLeft = maxWidth - frameWidth
            frontier = frameLeft + half
            record(Paste(sourceLeft = half, sourceRight = frameWidth, left = frontier, top = frameTop))
        }
    }

    /**
     * Moves the frame by what was measured and says what of it is new, or null when it has not
     * moved far enough to be worth anything yet.
     *
     * Turning back the way the sweep came moves the frame without laying anything down: the sheet
     * already holds that ground, and it holds it from when the sweep was going forwards.
     */
    fun advance(dx: Int, dy: Int): Paste? {
        if (!started || isFull) return null
        frameLeft += dx
        frameTop = (frameTop + dy).coerceIn(0, canvasHeight - frameHeight)

        val half = frameWidth / 2
        var target = frameLeft + half

        return if (direction > 0) {
            if (target <= frontier) return null
            val sourceLeft = (frontier - frameLeft).coerceAtLeast(0)
            var sourceRight = half
            if (target > maxWidth) {
                sourceRight -= target - maxWidth
                target = maxWidth
                isFull = true
            }
            frontier = target
            if (sourceRight <= sourceLeft) return null
            record(Paste(sourceLeft, sourceRight, frameLeft + sourceLeft, frameTop))
        } else {
            if (target >= frontier) return null
            var sourceLeft = half
            val sourceRight = (frontier - frameLeft).coerceAtMost(frameWidth)
            if (target < 0) {
                sourceLeft += -target
                target = 0
                isFull = true
            }
            frontier = target
            if (sourceRight <= sourceLeft) return null
            record(Paste(sourceLeft, sourceRight, frameLeft + sourceLeft, frameTop))
        }
    }

    private fun record(paste: Paste): Paste {
        if (!recorded) {
            recorded = true
            topUsed = paste.top
            bottomUsed = paste.top + frameHeight
        } else {
            topUsed = maxOf(topUsed, paste.top)
            bottomUsed = minOf(bottomUsed, paste.top + frameHeight)
        }
        return paste
    }

    /** The left edge of the finished picture on the sheet. */
    val filledLeft: Int get() = if (direction >= 0) 0 else frontier

    /** How wide the finished picture is. */
    val filledWidth: Int get() = if (direction >= 0) frontier else maxWidth - frontier

    /**
     * The rows every frame covered.
     *
     * A sweep that drifts upwards leaves the bottom of the early frames and the top of the late
     * ones sticking out with nothing beside them; the picture is the band they all share.
     */
    val filledTop: Int get() = topUsed
    val filledHeight: Int get() = (bottomUsed - topUsed).coerceAtLeast(0)

    companion object {
        /** Wide enough for a half turn on a decent lens, small enough to hold in memory. */
        const val MAX_CANVAS_WIDTH = 4096

        /** A finished panorama is half a turn; the progress bar fills as the sweep reaches it. */
        const val TARGET_SWEEP_RADIANS = 3.1415927f

        /** Headroom for vertical wander, as a fraction of the frame's height. */
        const val VERTICAL_MARGIN_FRACTION = 0.08f

        /** Narrower than this and it is a photograph that went wrong, not a panorama. */
        const val MIN_PANORAMA_WIDTH = 480

        /** The signed turn from [from] to [to], never the long way round. */
        fun shortestAngle(from: Float, to: Float): Float {
            var delta = to - from
            while (delta > PI) delta -= (2 * PI).toFloat()
            while (delta < -PI) delta += (2 * PI).toFloat()
            return delta
        }
    }
}
