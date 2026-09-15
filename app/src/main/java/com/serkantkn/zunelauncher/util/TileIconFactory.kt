package com.serkantkn.zunelauncher.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.model.TileIcon
import com.serkantkn.zunelauncher.data.model.TileIconStyle
import com.serkantkn.zunelauncher.data.model.WpGlyphs
import com.serkantkn.zunelauncher.data.repository.AppRepository
import com.serkantkn.zunelauncher.data.repository.IconPackRepository
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.Locale

/** What a tile should draw where an app's icon goes. */
sealed interface TileIconFace {

    /** A single shape, drawn in the tile's own ink: the Windows Phone look. */
    data class Glyph(val bitmap: ImageBitmap) : TileIconFace

    /** A picture drawn as it is — the app's own icon, or one the user chose. */
    data class Picture(val bitmap: ImageBitmap) : TileIconFace

    /** Nothing worth silhouetting, so the app's initial stands in for it. */
    data class Letter(val text: String) : TileIconFace

    data object None : TileIconFace
}

/**
 * Turns an app into the face its tile shows.
 *
 * In Windows Phone style a tile carries one white shape and nothing else, so the app's artwork has
 * to be reduced to a silhouette. Android already has the right picture for that when the icon is
 * adaptive: its monochrome layer is the glyph the app's own designer drew for themed icons. Where
 * there is none, the foreground layer's shape is used; and where even that is a solid block of
 * colour — an old square icon, where a silhouette would just be a white square — the app's first
 * letter is drawn instead, which is what Windows Phone did for tiles it had no glyph for.
 */
class TileIconFactory(
    private val context: Context,
    private val appRepository: AppRepository,
    private val iconPacks: IconPackRepository
) {

    private val cache = LruCache<String, TileIconFace>(220)

    /** Icon-pack drawables as the picker shows them: untouched, so the pack looks like itself. */
    private val previews = LruCache<String, ImageBitmap>(300)

    fun face(
        app: AppInfo,
        style: TileIconStyle,
        packPackage: String?,
        override: TileIcon
    ): TileIconFace {
        val key = "${app.packageName}|${style.name}|${packPackage.orEmpty()}|${override.store()}"
        cache.get(key)?.let { return it }
        val face = build(app, style, packPackage, override)
        cache.put(key, face)
        return face
    }

    /** One icon-pack drawable, drawn as the pack drew it, for picking by eye. */
    fun previewOfPack(packPackage: String, name: String): ImageBitmap? {
        val key = "$packPackage/$name"
        previews.get(key)?.let { return it }
        val drawable = iconPacks.drawableByName(packPackage, name) ?: return null
        val bitmap = render(drawable, PREVIEW_PX)?.asImageBitmap() ?: return null
        previews.put(key, bitmap)
        return bitmap
    }

    /** Drops everything drawn so far; call when the style, the pack or an override changes. */
    fun invalidate() {
        cache.evictAll()
        previews.evictAll()
    }

    private fun build(
        app: AppInfo,
        style: TileIconStyle,
        packPackage: String?,
        override: TileIcon
    ): TileIconFace {
        when (override) {
            // The user picked this exact picture, so it is shown exactly as it is.
            is TileIcon.Picture -> loadFile(override.path)?.let { return TileIconFace.Picture(it) }

            is TileIcon.Glyph -> glyphDrawable(override.name)?.let { drawable ->
                silhouette(drawable)?.let { return TileIconFace.Glyph(it) }
            }

            is TileIcon.Pack -> iconPacks.drawableByName(override.pack, override.drawable)
                ?.let { return dress(it, style, app) }

            TileIcon.Default -> Unit
        }

        if (!packPackage.isNullOrBlank()) {
            iconPacks.iconFor(packPackage, app.packageName, app.activityName)
                ?.let { return dress(it, style, app) }
        }

        val icon = appRepository.getAppIcon(app.packageName) ?: return TileIconFace.None
        return dress(icon, style, app)
    }

    private fun dress(drawable: Drawable, style: TileIconStyle, app: AppInfo): TileIconFace {
        if (style == TileIconStyle.ORIGINAL) {
            return render(drawable, ICON_PX)?.let { TileIconFace.Picture(it.asImageBitmap()) }
                ?: TileIconFace.None
        }
        silhouette(drawable)?.let { return TileIconFace.Glyph(it) }
        return TileIconFace.Letter(app.label.trim().take(1).uppercase(Locale.getDefault()).ifEmpty { "?" })
    }

    /**
     * The shape of an icon, ready to be tinted: transparent everywhere the icon is, opaque where
     * its glyph is. Null when the icon has no shape to speak of — a photo, or a full-bleed square.
     */
    private fun silhouette(drawable: Drawable): ImageBitmap? {
        val (layer, zoom) = glyphLayerOf(drawable)
        val rendered = render(layer, ICON_PX, zoom) ?: return null
        // Many icons carry a faint plate or drop shadow behind the glyph. Left alone it reads as a
        // grey square on the tile, so everything below the floor is dropped and everything above
        // the ceiling is made solid, with a ramp between the two to keep the edges smooth.
        val alpha = curveAlpha(alphaChannel(rendered), ALPHA_FLOOR, ALPHA_CEILING)
        if (alphaCoverage(alpha, ALPHA_THRESHOLD) > SOLID_BLOCK_COVERAGE) return null
        val bounds = alphaBounds(alpha, rendered.width, rendered.height, ALPHA_THRESHOLD)
            ?: return null
        return cropCentred(whiteWith(alpha, rendered.width, rendered.height), bounds).asImageBitmap()
    }

    /**
     * Which part of an icon is its glyph, and how much of the drawable's own box that part fills.
     *
     * An adaptive icon is drawn on a 108-unit canvas of which only the middle 72 are ever seen, so
     * its layers are rendered [ADAPTIVE_ZOOM] larger and cropped back to that safe zone.
     */
    private fun glyphLayerOf(drawable: Drawable): Pair<Drawable, Float> {
        if (drawable !is AdaptiveIconDrawable) return drawable to 1f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawable.monochrome?.let { return it to ADAPTIVE_ZOOM }
        }
        return (drawable.foreground ?: drawable) to ADAPTIVE_ZOOM
    }

    private fun render(drawable: Drawable, size: Int, zoom: Float = 1f): Bitmap? {
        if (size <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val span = (size * zoom).roundToInt()
            val offset = (size - span) / 2
            drawable.setBounds(offset, offset, offset + span, offset + span)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            ZuneLog.w(TAG, "could not draw an icon", e)
            null
        }
    }

    private fun glyphDrawable(name: String): Drawable? {
        @Suppress("DISCOURAGED_API_USAGE")
        val id = context.resources.getIdentifier(
            WpGlyphs.resourceName(name),
            "drawable",
            context.packageName
        )
        return if (id == 0) null else ContextCompat.getDrawable(context, id)
    }

    private fun loadFile(path: String): ImageBitmap? = try {
        val file = File(path)
        if (!file.exists()) null else BitmapFactory.decodeFile(path)?.asImageBitmap()
    } catch (e: Exception) {
        ZuneLog.w(TAG, "custom tile icon $path could not be read", e)
        null
    }

    companion object {
        private const val TAG = "TileIconFactory"

        /** Big enough for the largest tile, small enough to keep a couple of hundred cached. */
        const val ICON_PX = 192

        /** The picker never shows an icon larger than this. */
        const val PREVIEW_PX = 96

        /** 108 / 72: an adaptive icon's whole canvas against the part that is ever seen. */
        const val ADAPTIVE_ZOOM = 1.5f

        /** Below this an icon's pixel counts as see-through. */
        const val ALPHA_THRESHOLD = 40

        /** A plate this faint behind a glyph is not part of it. */
        const val ALPHA_FLOOR = 96

        /** From here up a pixel is simply part of the glyph. */
        const val ALPHA_CEILING = 190

        /** An icon this solid has no silhouette worth drawing — it would be a white square. */
        const val SOLID_BLOCK_COVERAGE = 0.92f

        /** How much of the tile's icon box the trimmed glyph fills. */
        const val GLYPH_FILL = 0.94f
    }
}

// ════════════════════════════════════════════════════════════
// SHAPE MEASUREMENT (pure, unit-tested)
// ════════════════════════════════════════════════════════════

/** How much of an icon is opaque, 0..1. */
fun alphaCoverage(alpha: IntArray, threshold: Int): Float {
    if (alpha.isEmpty()) return 0f
    var solid = 0
    alpha.forEach { if (it > threshold) solid++ }
    return solid.toFloat() / alpha.size
}

/** The square, centred on the icon, that just contains everything opaque; null when empty. */
fun alphaBounds(alpha: IntArray, width: Int, height: Int, threshold: Int): IntArray? {
    if (width <= 0 || height <= 0 || alpha.size < width * height) return null
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if (alpha[row + x] <= threshold) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    if (right < left || bottom < top) return null
    // A square around the glyph, so nothing is stretched when it is scaled back up.
    val side = max(right - left + 1, bottom - top + 1)
    val centreX = (left + right + 1) / 2
    val centreY = (top + bottom + 1) / 2
    return intArrayOf(centreX - side / 2, centreY - side / 2, side, side)
}

/**
 * Drops what is faint, keeps what is solid, and ramps between the two. A glyph's antialiased edge
 * survives; the pale plate an icon is often drawn on does not.
 */
fun curveAlpha(alpha: IntArray, floor: Int, ceiling: Int): IntArray {
    val span = (ceiling - floor).coerceAtLeast(1)
    return IntArray(alpha.size) { index ->
        val value = alpha[index]
        when {
            value <= floor -> 0
            value >= ceiling -> 255
            else -> ((value - floor) * 255) / span
        }
    }
}

private fun alphaChannel(bitmap: Bitmap): IntArray {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return IntArray(pixels.size) { (pixels[it] ushr 24) and 0xFF }
}

/** The same shape, painted white, ready for the tile to tint it. */
private fun whiteWith(alpha: IntArray, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(alpha.size) { (alpha[it] shl 24) or 0x00FFFFFF }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

/**
 * Redraws [source] with the glyph at [bounds] centred and filling the frame, so icons of wildly
 * different margins all read the same size on a tile.
 */
private fun cropCentred(source: Bitmap, bounds: IntArray): Bitmap {
    val size = source.width
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val span = (size * TileIconFactory.GLYPH_FILL).roundToInt().coerceAtLeast(1)
    val offset = (size - span) / 2
    val drawable = BitmapDrawable(null, source).apply {
        // Scaling the glyph's own square up to the frame is what evens the sizes out.
        val scale = span.toFloat() / bounds[2].toFloat()
        val left = offset - (bounds[0] * scale).roundToInt()
        val top = offset - (bounds[1] * scale).roundToInt()
        setBounds(left, top, left + (size * scale).roundToInt(), top + (size * scale).roundToInt())
    }
    drawable.draw(canvas)
    return out
}
