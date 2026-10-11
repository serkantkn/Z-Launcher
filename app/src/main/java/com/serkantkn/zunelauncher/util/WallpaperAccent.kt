package com.serkantkn.zunelauncher.util

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The accent colour taken from the wallpaper, for the "automatic" choice of theme colour.
 *
 * It used to be the picture's dominant colour, and a wallpaper is mostly shadow: dark leaves
 * gave a green so close to black (#182018) that every tile, link and switch drawn in it
 * vanished, and the choice looked as if it did nothing. An accent is the opposite of the
 * background — Windows Phone's were all mid-light and strongly coloured — so this picks the most
 * colourful colour the picture really has, and then brings it to an accent's lightness and
 * strength, keeping its hue.
 */
object WallpaperAccent {

    /** One colour found in the picture, and how much of the picture it covers. */
    data class Candidate(val rgb: Int, val population: Int)

    /** Windows Phone's steel, for a wallpaper with no colour in it at all. */
    const val STEEL = 0xFF647687.toInt()

    /**
     * The accent for the wallpaper the launcher is drawing: the custom picture when there is
     * one, otherwise the phone's wallpaper. Null when neither can be read.
     */
    suspend fun fromWallpaper(context: Context, customPath: String?): Int? = withContext(Dispatchers.Default) {
        val bitmap = WallpaperImages.sample(context, customPath)
        val candidates = if (bitmap != null) {
            runCatching {
                Palette.from(bitmap).maximumColorCount(24).generate().swatches
                    .map { Candidate(it.rgb, it.population) }
            }.getOrNull().orEmpty()
        } else {
            systemColors(context)
        }
        if (candidates.isEmpty()) null else pick(candidates)
    }

    /** What Android says the wallpaper's colours are, when the picture itself cannot be read. */
    private fun systemColors(context: Context): List<Candidate> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return emptyList()
        val colors = runCatching {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return emptyList()
        return listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)
            .mapIndexed { i, c -> Candidate(c.toArgb(), 3 - i) }
    }

    /**
     * The accent from what the picture holds: the colour with the most colour in it, weighed a
     * little by how much of the picture it covers, at an accent's lightness and saturation.
     */
    fun pick(candidates: List<Candidate>): Int {
        val total = candidates.sumOf { it.population }.coerceAtLeast(1).toFloat()
        val best = candidates.maxByOrNull { c ->
            val hsl = toHsl(c.rgb)
            chroma(hsl) * sqrt(c.population / total)
        } ?: return STEEL
        val hsl = toHsl(best.rgb)
        if (chroma(hsl) < GREY_CHROMA) return STEEL
        return fromHsl(
            hsl[0],
            hsl[1].coerceIn(MIN_SATURATION, MAX_SATURATION),
            hsl[2].coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS)
        )
    }

    // ── Colour arithmetic, kept free of android.graphics so it can be tested on its own ──

    private fun chroma(hsl: FloatArray): Float = hsl[1] * (1f - abs(2f * hsl[2] - 1f))

    internal fun toHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val d = max - min
        if (d == 0f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, s.coerceIn(0f, 1f), l)
    }

    internal fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** Below this there is no colour to speak of: greys, black, white. */
    private const val GREY_CHROMA = 0.06f
    private const val MIN_SATURATION = 0.50f
    private const val MAX_SATURATION = 0.85f
    private const val MIN_LIGHTNESS = 0.36f
    private const val MAX_LIGHTNESS = 0.50f
}
