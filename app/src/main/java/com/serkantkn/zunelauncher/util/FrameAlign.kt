package com.serkantkn.zunelauncher.util

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * How far the scene moved between one frame and the next, measured from the pictures themselves.
 *
 * A panorama swept by the rotation sensor alone cannot hold together: the sensor says how far the
 * phone turned, not how far the scene moved, and the two part company the moment a hand shakes,
 * a lens is wider than its specification sheet says, or a gyroscope drifts. Asking the pictures
 * instead costs a millisecond or two and answers the question that is actually being asked.
 *
 * The measurement is made on two lines of numbers rather than on the whole frame: the average
 * brightness of every column, and of every row. A sideways move slides the column line along
 * itself, a vertical one slides the row line, and finding how far is a search over a few hundred
 * offsets rather than over a few hundred thousand pixels.
 */
object FrameAlign {

    /**
     * A frame reduced to what is needed to line it up with its neighbour.
     *
     * Both lines are shifted to a mean of zero and scaled to a standard deviation of one, so a
     * cloud passing over the sun changes nothing about where the match is found.
     */
    class Profile(
        val columns: FloatArray,
        val rows: FloatArray,
        /** False when the frame is too flat along that axis for a match to mean anything. */
        val columnsReadable: Boolean,
        val rowsReadable: Boolean
    )

    /** Where the scene went: [dx] across, [dy] down, in pixels of the frame. */
    data class Shift(val dx: Int, val dy: Int, val confidence: Float)

    /**
     * Reads the two lines off a frame.
     *
     * The edges are left out of both: a wide lens bends what is near the border, and the bend
     * changes as the phone turns, which is exactly the sort of movement this must not mistake for
     * a pan.
     */
    fun profileOf(pixels: IntArray, width: Int, height: Int): Profile? {
        if (width <= 8 || height <= 8 || pixels.size < width * height) return null

        val insetY = (height * EDGE_INSET).toInt()
        val insetX = (width * EDGE_INSET).toInt()

        val columns = FloatArray(width)
        var sampledRows = 0
        var y = insetY
        while (y < height - insetY) {
            val base = y * width
            for (x in 0 until width) columns[x] += luma(pixels[base + x]).toFloat()
            sampledRows++
            y += COLUMN_ROW_STEP
        }

        val rows = FloatArray(height)
        var sampledColumns = 0
        var x = insetX
        while (x < width - insetX) {
            for (row in 0 until height) rows[row] += luma(pixels[row * width + x]).toFloat()
            sampledColumns++
            x += ROW_COLUMN_STEP
        }

        if (sampledRows == 0 || sampledColumns == 0) return null
        for (i in columns.indices) columns[i] /= sampledRows
        for (i in rows.indices) rows[i] /= sampledColumns

        return Profile(
            columns = columns,
            rows = rows,
            columnsReadable = normalise(columns),
            rowsReadable = normalise(rows)
        )
    }

    /**
     * How far the scene moved between [previous] and [next], or null when the picture will not say.
     *
     * A positive [Shift.dx] means the scene slid left across the frame, which is what happens when
     * the phone is turned to the right.
     */
    fun shiftBetween(previous: Profile, next: Profile, maxDx: Int, maxDy: Int): Shift? {
        if (!previous.columnsReadable || !next.columnsReadable) return null
        if (previous.columns.size != next.columns.size) return null

        val across = bestOffset(previous.columns, next.columns, maxDx) ?: return null
        if (across.second < MIN_CONFIDENCE) return null

        val down = if (previous.rowsReadable && next.rowsReadable &&
            previous.rows.size == next.rows.size
        ) {
            bestOffset(previous.rows, next.rows, maxDy)?.takeIf { it.second >= MIN_CONFIDENCE }
        } else {
            null
        }

        return Shift(dx = across.first, dy = down?.first ?: 0, confidence = across.second)
    }

    /**
     * The offset that lays [next] over [previous] most closely, and how much better that offset is
     * than the run of them.
     *
     * The confidence is what separates a match from a coincidence. A blank wall matches itself
     * equally well at every offset, and the number that comes back says so.
     */
    internal fun bestOffset(previous: FloatArray, next: FloatArray, maxShift: Int): Pair<Int, Float>? {
        val n = next.size
        if (n == 0 || maxShift <= 0) return null
        val minOverlap = (n * MIN_OVERLAP).toInt().coerceAtLeast(8)

        var best = Float.MAX_VALUE
        var bestShift = Int.MIN_VALUE
        var total = 0f
        var counted = 0

        for (shift in -maxShift..maxShift) {
            val start = maxOf(0, -shift)
            val end = minOf(n, n - shift)
            if (end - start < minOverlap) continue

            var sum = 0f
            for (i in start until end) sum += abs(next[i] - previous[i + shift])
            val score = sum / (end - start)

            total += score
            counted++
            if (score < best) {
                best = score
                bestShift = shift
            }
        }

        if (bestShift == Int.MIN_VALUE || counted == 0) return null
        val average = total / counted
        if (average <= 0f) return null
        val confidence = ((average - best) / average).coerceIn(0f, 1f)
        return bestShift to confidence
    }

    /** Centres a line on zero and scales it; false when there was nothing there to scale. */
    private fun normalise(values: FloatArray): Boolean {
        if (values.isEmpty()) return false
        var sum = 0f
        for (v in values) sum += v
        val mean = sum / values.size
        var variance = 0f
        for (v in values) {
            val d = v - mean
            variance += d * d
        }
        val deviation = sqrt(variance / values.size)
        if (deviation < MIN_CONTRAST) {
            for (i in values.indices) values[i] = 0f
            return false
        }
        for (i in values.indices) values[i] = (values[i] - mean) / deviation
        return true
    }

    private fun luma(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (77 * r + 150 * g + 29 * b) shr 8
    }

    /** How much of each edge is left out of the reading, as a fraction of the frame. */
    private const val EDGE_INSET = 0.12f

    /** Only every third row is read for the column line, and every fourth column for the row line. */
    private const val COLUMN_ROW_STEP = 3
    private const val ROW_COLUMN_STEP = 4

    /** Below this much variation, on a 0..255 scale, a frame has nothing to line up by. */
    const val MIN_CONTRAST = 1.2f

    /** How much better than the average a match must be before it is believed. */
    const val MIN_CONFIDENCE = 0.10f

    /** How much of the two lines must overlap before an offset is even considered. */
    const val MIN_OVERLAP = 0.55f
}
