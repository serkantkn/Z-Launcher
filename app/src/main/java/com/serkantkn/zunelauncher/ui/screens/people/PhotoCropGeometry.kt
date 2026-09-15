package com.serkantkn.zunelauncher.ui.screens.people

import kotlin.math.max
import kotlin.math.roundToInt

/** The piece of the original picture that the square window is showing. */
internal data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Works out which part of the picture the user has framed.
 *
 * Cropping by photographing the composable seemed simpler and produced an empty image inside a
 * dialog, so this goes the other way: the same sums the screen used to lay the picture out, run
 * backwards, to find the rectangle of the original that the square window covers. It is exact, it
 * does not depend on anything having been drawn, and it can be checked without a screen.
 *
 * [windowPx] is the side of the square window, [scale] and [offsetX]/[offsetY] are the pinch and
 * drag applied to a picture that was first scaled to cover that window.
 */
internal fun cropRectFor(
    sourceWidth: Int,
    sourceHeight: Int,
    windowPx: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): CropRect {
    if (sourceWidth <= 0 || sourceHeight <= 0 || windowPx <= 0) {
        return CropRect(0, 0, max(sourceWidth, 1), max(sourceHeight, 1))
    }

    // What "cover the square" scaled the picture by before the user touched it.
    val cover = max(windowPx.toFloat() / sourceWidth, windowPx.toFloat() / sourceHeight)
    val total = cover * scale
    val half = windowPx / 2f

    fun sourceAt(screenFromCentre: Float, offset: Float, sourceSize: Int): Float =
        (screenFromCentre - offset) / total + sourceSize / 2f

    val left = sourceAt(-half, offsetX, sourceWidth)
    val right = sourceAt(half, offsetX, sourceWidth)
    val top = sourceAt(-half, offsetY, sourceHeight)
    val bottom = sourceAt(half, offsetY, sourceHeight)

    // Dragging past the edge asks for pixels that are not there; the window stops at the picture.
    val x0 = left.roundToInt().coerceIn(0, sourceWidth - 1)
    val y0 = top.roundToInt().coerceIn(0, sourceHeight - 1)
    val x1 = right.roundToInt().coerceIn(x0 + 1, sourceWidth)
    val y1 = bottom.roundToInt().coerceIn(y0 + 1, sourceHeight)

    return CropRect(x0, y0, x1 - x0, y1 - y0)
}
