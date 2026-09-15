package com.serkantkn.zunelauncher.util

/**
 * The arithmetic behind the photo editor's sliders and filters.
 *
 * All of it is colour-matrix multiplication: four numbers on a screen become one 4×5 matrix, which
 * is applied once to draw the preview and once more to write the file. Keeping it here, away from
 * Android's own `ColorMatrix` and from Compose's, means the same matrix feeds both and the maths
 * can be tested without a phone.
 *
 * The matrix is the usual row-major 4×5: each output channel is a weighted sum of the four input
 * channels plus an offset, and the offset is in 0..255 the way both platforms expect.
 */

/** Where the four sliders stand. Every one is -1..1, with zero meaning "as it was". */
data class PhotoAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

/** The canned looks. [NONE] is the picture as it was taken. */
enum class PhotoFilter {
    NONE,
    MONO,
    SEPIA,
    VIVID,
    COOL,
    FADED
}

/** The matrix that changes nothing. */
fun identityMatrix(): FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)

/**
 * [second] applied after [first], as one matrix.
 *
 * Order matters: brightening and then flattening is not the same picture as flattening and then
 * brightening, and the editor applies its sliders in a fixed order so the same numbers always mean
 * the same result.
 */
fun concatMatrix(second: FloatArray, first: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (column in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) {
                sum += second[row * 5 + k] * first[k * 5 + column]
            }
            out[row * 5 + column] = sum
        }
        // The offset column carries the first matrix's offsets through the second, plus its own.
        var offset = second[row * 5 + 4]
        for (k in 0 until 4) {
            offset += second[row * 5 + k] * first[k * 5 + 4]
        }
        out[row * 5 + 4] = offset
    }
    return out
}

/** Lighter or darker, by up to a full range either way. */
fun brightnessMatrix(amount: Float): FloatArray {
    val shift = amount.coerceIn(-1f, 1f) * 100f
    return identityMatrix().also {
        it[4] = shift
        it[9] = shift
        it[14] = shift
    }
}

/** More or less separation between light and dark, pivoting around mid-grey. */
fun contrastMatrix(amount: Float): FloatArray {
    // 1 means "twice as contrasty", -1 means "flat". The curve either side of zero is not the
    // same: halving contrast is a bigger visual change than doubling it.
    val scale = if (amount >= 0f) 1f + amount.coerceAtMost(1f) else 1f + amount.coerceAtLeast(-1f) * 0.8f
    val shift = (1f - scale) * 128f
    return identityMatrix().also {
        it[0] = scale; it[4] = shift
        it[6] = scale; it[9] = shift
        it[12] = scale; it[14] = shift
    }
}

/** From grey at -1, through the picture as taken at 0, to twice as colourful at 1. */
fun saturationMatrix(amount: Float): FloatArray {
    val value = (1f + amount.coerceIn(-1f, 1f)).coerceAtLeast(0f)
    // The weights are the usual luminance ones: the eye is far more sensitive to green than blue.
    val lumaR = 0.213f
    val lumaG = 0.715f
    val lumaB = 0.072f
    val inverse = 1f - value
    return floatArrayOf(
        lumaR * inverse + value, lumaG * inverse, lumaB * inverse, 0f, 0f,
        lumaR * inverse, lumaG * inverse + value, lumaB * inverse, 0f, 0f,
        lumaR * inverse, lumaG * inverse, lumaB * inverse + value, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/** Towards tungsten at 1 and towards daylight at -1: red up and blue down, or the reverse. */
fun warmthMatrix(amount: Float): FloatArray {
    val value = amount.coerceIn(-1f, 1f)
    return identityMatrix().also {
        it[0] = 1f + value * 0.2f
        it[12] = 1f - value * 0.2f
    }
}

/** The matrix behind one of the canned looks. */
fun filterMatrix(filter: PhotoFilter): FloatArray = when (filter) {
    PhotoFilter.NONE -> identityMatrix()
    PhotoFilter.MONO -> saturationMatrix(-1f)
    PhotoFilter.SEPIA -> concatMatrix(
        floatArrayOf(
            1.07f, 0f, 0f, 0f, 12f,
            0f, 0.94f, 0f, 0f, 6f,
            0f, 0f, 0.74f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ),
        saturationMatrix(-1f)
    )
    PhotoFilter.VIVID -> concatMatrix(contrastMatrix(0.25f), saturationMatrix(0.45f))
    PhotoFilter.COOL -> concatMatrix(warmthMatrix(-0.55f), saturationMatrix(0.1f))
    // A faded look is flat and lifted: the blacks never reach black.
    PhotoFilter.FADED -> concatMatrix(
        identityMatrix().also {
            it[4] = 18f; it[9] = 16f; it[14] = 14f
        },
        concatMatrix(contrastMatrix(-0.35f), saturationMatrix(-0.25f))
    )
}

/** Everything the editor is doing to the colours, as one matrix. */
fun photoMatrix(adjust: PhotoAdjust, filter: PhotoFilter): FloatArray {
    var result = filterMatrix(filter)
    if (adjust.saturation != 0f) result = concatMatrix(saturationMatrix(adjust.saturation), result)
    if (adjust.warmth != 0f) result = concatMatrix(warmthMatrix(adjust.warmth), result)
    if (adjust.contrast != 0f) result = concatMatrix(contrastMatrix(adjust.contrast), result)
    if (adjust.brightness != 0f) result = concatMatrix(brightnessMatrix(adjust.brightness), result)
    return result
}

/**
 * What "auto-fix" should set the sliders to, given how the picture's brightness is spread.
 *
 * The idea is the one every auto-levels button has used for thirty years: if the darkest parts of
 * a picture are not dark and the lightest are not light, the picture is using less of the range
 * than it could, so stretch it — and if the whole thing sits low or high, move it back to the
 * middle. A picture that already fills the range is left alone, which is why the numbers come back
 * at zero rather than at some default.
 *
 * [darkest], [lightest] and [average] are luminance in 0..255.
 */
fun autoFixFor(darkest: Int, lightest: Int, average: Int): PhotoAdjust {
    val low = darkest.coerceIn(0, 255)
    val high = lightest.coerceIn(0, 255)
    if (high - low < 8) return PhotoAdjust()

    // How much of the range is unused. A picture spanning half the range wants +1 contrast.
    val span = (high - low).toFloat() / 255f
    val contrast = ((1f / span) - 1f).coerceIn(0f, 1f)

    // Where the picture sits once stretched, against where the middle is.
    val stretchedAverage = ((average - low).toFloat() / (high - low).toFloat()) * 255f
    val brightness = ((128f - stretchedAverage) / 255f).coerceIn(-0.4f, 0.4f)

    return PhotoAdjust(
        brightness = round2(brightness),
        contrast = round2(contrast),
        saturation = 0f,
        warmth = 0f
    )
}

/** Slider values are shown to one decimal; keeping them there stops 0.30000001 from appearing. */
private fun round2(value: Float): Float = Math.round(value * 100f) / 100f
