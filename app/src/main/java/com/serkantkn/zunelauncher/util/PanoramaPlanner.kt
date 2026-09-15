package com.serkantkn.zunelauncher.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turning a slow sweep of the phone into one wide picture.
 *
 * Android has no panorama API, so the sweep is measured rather than guessed at: the rotation
 * sensor says how far the phone has turned, and the lens's field of view says how many pixels a
 * turn of that size moves the scene across the frame. Every time the scene has moved by one strip
 * width, the strip under the middle of the frame is kept and laid down next to the last one.
 *
 * Reading the movement off the sensor rather than out of the picture is what makes this possible
 * without a feature matcher: it costs nothing per frame and it does not care what is in shot.
 */
class PanoramaPlanner(
    private val frameWidth: Int,
    /** How wide a slice of each frame is kept. */
    val stripWidth: Int,
    horizontalFov: Float,
    /** How wide the finished picture is allowed to get. */
    val maxWidth: Int = MAX_CANVAS_WIDTH
) {
    private val pixelsPerRadian = pixelsPerRadian(frameWidth, horizontalFov)

    /** How far the sweep has come, in pixels of the finished picture. */
    var travelled = 0f
        private set

    /** Where the next strip will be laid down. */
    var nextStripAt = 0
        private set

    /** Which way the sweep is going; fixed by the first real movement. */
    var direction = 0
        private set

    private var lastYaw: Float? = null

    val isFull: Boolean get() = nextStripAt + stripWidth >= maxWidth

    /** How much of a full sweep has been covered, 0..1. */
    val progress: Float get() = (nextStripAt.toFloat() / maxWidth).coerceIn(0f, 1f)

    /**
     * Takes a yaw reading and says where — if anywhere — a strip should be laid down now.
     * Returns null while the phone has not yet moved a strip's worth.
     */
    fun onYaw(yaw: Float): Int? {
        val previous = lastYaw
        lastYaw = yaw
        if (previous == null) return 0.also { nextStripAt = 0 }

        val delta = shortestAngle(previous, yaw)
        if (direction == 0) {
            if (abs(delta) * pixelsPerRadian < DIRECTION_DEADZONE_PX) return null
            direction = if (delta > 0) 1 else -1
        }
        // Turning back the way it came does not un-take a strip; it just does not add one.
        val forward = delta * direction
        if (forward <= 0f) return null

        travelled += forward * pixelsPerRadian
        if (travelled < stripWidth) return null

        travelled -= stripWidth
        nextStripAt += stripWidth
        return if (nextStripAt + stripWidth > maxWidth) null else nextStripAt
    }

    /** The width of the picture as it stands, including the strip that started it. */
    fun canvasWidth(): Int = (nextStripAt + stripWidth).coerceAtMost(maxWidth)

    /** Where in a frame the kept strip is cut from: its middle, which is its least distorted part. */
    fun sourceLeft(): Int = ((frameWidth - stripWidth) / 2).coerceAtLeast(0)

    companion object {
        /** Wide enough for a half-turn at a sensible strip size, small enough to hold in memory. */
        const val MAX_CANVAS_WIDTH = 4096

        /** A finished panorama is half a turn; the progress bar fills as the sweep reaches it. */
        const val TARGET_SWEEP_RADIANS = 3.1415927f

        /** How far the phone has to move before the sweep commits to a direction. */
        const val DIRECTION_DEADZONE_PX = 12f

        /** A strip this wide keeps the seams frequent enough that the join does not show. */
        const val DEFAULT_STRIP_WIDTH = 48

        /** What a phone camera sees across, when the lens will not say. */
        const val FALLBACK_FOV_RADIANS = 1.15f   // about 66 degrees

        /** How many pixels the scene slides across the frame per radian of turn. */
        fun pixelsPerRadian(frameWidth: Int, horizontalFov: Float): Float {
            val fov = if (horizontalFov.isFinite() && horizontalFov > 0.1f) horizontalFov else FALLBACK_FOV_RADIANS
            return frameWidth / fov
        }

        /** The signed turn from [from] to [to], never the long way round. */
        fun shortestAngle(from: Float, to: Float): Float {
            var delta = to - from
            while (delta > PI) delta -= (2 * PI).toFloat()
            while (delta < -PI) delta += (2 * PI).toFloat()
            return delta
        }
    }
}

/**
 * Averages frames pixel by pixel.
 *
 * This is what the night mode does when the phone has no night extension of its own: the noise in
 * a dark frame is different every time and the picture is not, so adding several together and
 * dividing leaves the picture and loses much of the noise.
 */
fun averageFrames(frames: List<IntArray>): IntArray? {
    if (frames.isEmpty()) return null
    val size = frames.first().size
    if (frames.any { it.size != size }) return null
    if (frames.size == 1) return frames.first().copyOf()

    val red = IntArray(size)
    val green = IntArray(size)
    val blue = IntArray(size)
    frames.forEach { frame ->
        for (i in 0 until size) {
            val pixel = frame[i]
            red[i] += (pixel shr 16) and 0xFF
            green[i] += (pixel shr 8) and 0xFF
            blue[i] += pixel and 0xFF
        }
    }
    val count = frames.size
    return IntArray(size) { i ->
        (0xFF shl 24) or
            ((red[i] / count) shl 16) or
            ((green[i] / count) shl 8) or
            (blue[i] / count)
    }
}

/**
 * Lifts a dark frame without blowing out what is already bright.
 *
 * The curve is applied to how far a pixel is from white rather than to the pixel itself: that
 * distance is shrunk by [gain], so the shadows climb steeply, the highlights barely move, black
 * stays black and nothing can be pushed past white.
 */
fun brighten(pixels: IntArray, gain: Float): IntArray {
    if (gain <= 1f) return pixels
    val curve = IntArray(256) { value ->
        val headroom = 1f - value / 255f
        (255f * (1f - headroom.toDouble().pow(gain.toDouble()).toFloat())).roundToInt().coerceIn(0, 255)
    }
    return IntArray(pixels.size) { i ->
        val pixel = pixels[i]
        (pixel and 0xFF000000.toInt()) or
            (curve[(pixel shr 16) and 0xFF] shl 16) or
            (curve[(pixel shr 8) and 0xFF] shl 8) or
            curve[pixel and 0xFF]
    }
}
