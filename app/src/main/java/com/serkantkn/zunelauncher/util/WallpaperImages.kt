package com.serkantkn.zunelauncher.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The one copy of the wallpaper the launcher draws, and the blurred copy that goes under a hub.
 *
 * Both used to be decoded inside [com.serkantkn.zunelauncher.ui.components.ZuneBackground], which
 * meant once per instance: the Start screen had one, the hub in front of it had another, and the
 * settings preview a third — three full-size bitmaps of the same picture. A wallpaper is usually
 * wider than the screen so it can slide, so that was upwards of sixty megabytes of a phone's
 * memory spent drawing the same image three times.
 *
 * Here it is read once, scaled to the screen it is going to be drawn on, and handed to everybody
 * who asks. [version] rises when the system says the wallpaper changed, which is what makes the
 * cached copy stale.
 */
object WallpaperImages {

    data class Wallpapers(val full: Bitmap?, val blurred: Bitmap?)

    private val mutex = Mutex()
    private var cachedKey: String? = null
    private var cached: Wallpapers = Wallpapers(null, null)

    /**
     * The wallpaper, decoded at most once per [customPath] and [version].
     *
     * [blurred] says whether the blurred copy is wanted as well. It is asked for separately
     * because most people never turn that background on, and blurring a picture nobody is going
     * to see is a second bitmap and a tenth of a second of somebody's first frame.
     */
    suspend fun load(
        context: Context,
        customPath: String?,
        version: Int,
        blurred: Boolean
    ): Wallpapers = mutex.withLock {
        val key = "${customPath.orEmpty()}#$version"
        val hit = key == cachedKey && cached.full?.isRecycled == false
        if (hit && (!blurred || cached.blurred != null)) return@withLock cached

        val metrics = context.resources.displayMetrics
        val wallpapers = withContext(Dispatchers.IO) {
            val full = if (hit) cached.full
            else decode(context, customPath, metrics.widthPixels, metrics.heightPixels)
            Wallpapers(full, if (blurred) full?.let { blurredCopy(it) } else cached.blurred)
        }
        if (wallpapers.full != null) {
            cachedKey = key
            cached = wallpapers
        }
        wallpapers
    }

    /**
     * Forgets the decoded wallpaper. The next background that asks reads it again.
     *
     * Nothing is recycled here on purpose: a background that is still on screen is holding the
     * same bitmap, and recycling underneath it would draw a hole.
     */
    fun trim() {
        cachedKey = null
        cached = Wallpapers(null, null)
    }

    /**
     * A blurred copy, small on purpose: it is only ever drawn stretched across the screen under a
     * layer of tint, so a sixth of the width carries every bit of detail that survives the blur.
     */
    private fun blurredCopy(source: Bitmap): Bitmap = runCatching {
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width / 6).coerceAtLeast(1),
            (source.height / 6).coerceAtLeast(1),
            true
        )
        SimpleBlur.blur(scaled, radius = 3, iterations = 2)
    }.getOrElse {
        ZuneLog.w("WallpaperImages", "the wallpaper could not be blurred; using it as it is", it)
        source
    }

    /** The picture itself, never decoded larger than the screen it will be drawn on. */
    private fun decode(context: Context, customPath: String?, width: Int, height: Int): Bitmap? {
        val custom = customPath?.let { File(it) }?.takeIf { it.exists() }
        if (custom != null) {
            decodeSampled({ BitmapFactory.decodeFile(custom.absolutePath, it) }, width, height)
                ?.let { return it }
        }
        val manager = WallpaperManager.getInstance(context)
        return systemWallpaper(manager, width, height)
    }

    /**
     * Decodes twice: once for the size alone, once for the pixels, so a wallpaper that is much
     * larger than the screen — most of them are, to leave room to slide — never arrives whole.
     */
    private fun decodeSampled(
        decoder: (BitmapFactory.Options) -> Bitmap?,
        width: Int,
        height: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decoder(bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, width, height)
        }
        return runCatching { decoder(options) }.getOrNull()
    }

    private fun systemWallpaper(manager: WallpaperManager, width: Int, height: Int): Bitmap? {
        try {
            val sized = decodeSampled(
                { options ->
                    manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { descriptor ->
                        BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                    }
                },
                width,
                height
            )
            if (sized != null) return sized
        } catch (e: Exception) {
            ZuneLog.w("WallpaperImages", "getWallpaperFile failed; trying the drawable", e)
        }

        val drawable = runCatching { manager.drawable }.getOrNull()
            ?: runCatching { manager.peekDrawable() }.getOrNull()
            ?: return null

        val drawableWidth = drawable.intrinsicWidth.takeIf { it > 1 }
            ?: manager.desiredMinimumWidth.takeIf { it > 1 } ?: width
        val drawableHeight = drawable.intrinsicHeight.takeIf { it > 1 }
            ?: manager.desiredMinimumHeight.takeIf { it > 1 } ?: height
        val sample = sampleSizeFor(drawableWidth, drawableHeight, width, height)

        return runCatching {
            Bitmap.createBitmap(
                (drawableWidth / sample).coerceAtLeast(1),
                (drawableHeight / sample).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            ).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }.getOrNull()
    }

    /** The largest power of two that still leaves the picture covering the screen. */
    internal fun sampleSizeFor(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= width && sourceHeight / (sample * 2) >= height) {
            sample *= 2
        }
        return sample
    }
}
