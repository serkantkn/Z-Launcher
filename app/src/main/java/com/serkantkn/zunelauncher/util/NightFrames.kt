package com.serkantkn.zunelauncher.util

import kotlin.math.pow
import kotlin.math.roundToInt

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
