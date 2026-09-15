package com.serkantkn.zunelauncher.util

import android.graphics.Bitmap

/**
 * A box blur, done the cheap way.
 *
 * A box blur is separable: blurring every row and then every column gives the same picture as
 * averaging each pixel's whole square, and a running sum means each pass costs the same whatever
 * the radius is. The straightforward version read (2r+1)² pixels per pixel per pass, which on a
 * phone that needs the help most was most of a second of the launcher's first frame.
 */
object SimpleBlur {

    fun blur(image: Bitmap, radius: Int, iterations: Int = 2): Bitmap {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0 || radius <= 0 || iterations <= 0) return image

        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        val scratch = IntArray(width * height)

        repeat(iterations) {
            boxPass(pixels, scratch, width, height, radius)   // rows
            boxPass(scratch, pixels, height, width, radius)   // columns (the scratch is transposed)
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Blurs each row of [source] and writes the result transposed into [destination], so running
     * the same pass twice covers both directions. [width] × [height] describe [source].
     */
    private fun boxPass(
        source: IntArray,
        destination: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        for (y in 0 until height) {
            val row = y * width
            var red = 0
            var green = 0
            var blue = 0
            var count = 0

            // The window for x = 0, then slid one pixel at a time.
            for (x in 0..minOf(radius, width - 1)) {
                val color = source[row + x]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
                count++
            }

            for (x in 0 until width) {
                destination[x * height + y] =
                    (0xFF shl 24) or
                        ((red / count) shl 16) or
                        ((green / count) shl 8) or
                        (blue / count)

                val leaving = x - radius
                if (leaving >= 0) {
                    val color = source[row + leaving]
                    red -= (color shr 16) and 0xFF
                    green -= (color shr 8) and 0xFF
                    blue -= color and 0xFF
                    count--
                }
                val arriving = x + radius + 1
                if (arriving < width) {
                    val color = source[row + arriving]
                    red += (color shr 16) and 0xFF
                    green += (color shr 8) and 0xFF
                    blue += color and 0xFF
                    count++
                }
            }
        }
    }
}
