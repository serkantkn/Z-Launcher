package com.serkantkn.zunelauncher.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Putting a picture the launcher made into the phone's own gallery, so it turns up everywhere
 * pictures turn up rather than only inside this app.
 */
object MediaSaver {

    private const val TAG = "MediaSaver"

    /** The folder everything the launcher makes lives in: camera shots and edited pictures alike. */
    const val ALBUM = "Z Launcher"

    /**
     * A name with the time in it, to the millisecond. Before Android 10 the name is the file,
     * and two shots in one second — a burst, a panorama's frames — used to land on the same one.
     */
    fun fileName(prefix: String, extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "${prefix}_$stamp.$extension"
    }

    /** Writes a JPEG into Pictures/[ALBUM] and returns where it landed. */
    fun saveJpeg(context: Context, bitmap: Bitmap, name: String, quality: Int = 95): Uri? =
        saveImage(context, bitmap, name, png = false, quality = quality, exifFrom = null)

    /**
     * Writes a picture into Pictures/[ALBUM] and returns where it landed, or null — and nothing
     * half-written left behind — when it could not.
     *
     * [png] keeps a PNG a PNG: a screenshot run through the editor should not come out with JPEG
     * blocks in its text. [exifFrom] names the picture this one was made from, whose date, camera
     * and place are carried over so the edit sorts beside the original rather than at the top of
     * the roll as a picture taken just now.
     */
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        name: String,
        png: Boolean,
        quality: Int = 95,
        exifFrom: Uri? = null
    ): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, name, png, quality, exifFrom)
        } else {
            saveViaPublicFolder(context, bitmap, name, png, quality, exifFrom)
        }
    } catch (e: Throwable) {
        // OutOfMemoryError included: compressing a large bitmap allocates, and a failure to save
        // is a failure to save whatever its kind.
        ZuneLog.e(TAG, "could not save $name", e)
        null
    }

    private fun format(png: Boolean) = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

    private fun mime(png: Boolean) = if (png) "image/png" else "image/jpeg"

    private fun saveViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        name: String,
        png: Boolean,
        quality: Int,
        exifFrom: Uri?
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime(png))
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            val written = resolver.openOutputStream(uri)?.use { bitmap.compress(format(png), quality, it) } ?: false
            if (!written) error("the picture would not compress")
            if (!png && exifFrom != null) {
                resolver.openFileDescriptor(uri, "rw")?.use { copyExif(context, exifFrom, ExifInterface(it.fileDescriptor)) }
            }
            // Pending until it is fully written, so nothing reads half a picture.
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Throwable) {
            // A pending row that never clears is invisible to every gallery and never cleaned
            // up; it goes with the failure rather than being left to pile up.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /** Before Android 10 there is no relative path: the file is written and then announced. */
    private fun saveViaPublicFolder(
        context: Context,
        bitmap: Bitmap,
        name: String,
        png: Boolean,
        quality: Int,
        exifFrom: Uri?
    ): Uri? {
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM
        )
        if (!folder.exists() && !folder.mkdirs()) return null
        val file = File(folder, name)
        try {
            FileOutputStream(file).use { bitmap.compress(format(png), quality, it) }
            if (!png && exifFrom != null) copyExif(context, exifFrom, ExifInterface(file.absolutePath))
        } catch (e: Throwable) {
            runCatching { file.delete() }
            throw e
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime(png))
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    /**
     * Carries the source's date, camera and place over to [target]. The orientation is set
     * upright on purpose: the pixels being saved were already turned the way the tag said, and
     * copying the tag would turn them once more.
     */
    private fun copyExif(context: Context, source: Uri, target: ExifInterface) {
        val original = runCatching {
            context.contentResolver.openInputStream(source)?.use { ExifInterface(it) }
        }.getOrNull() ?: return
        COPIED_TAGS.forEach { tag ->
            original.getAttribute(tag)?.let { target.setAttribute(tag, it) }
        }
        target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        runCatching { target.saveAttributes() }
            .onFailure { ZuneLog.w(TAG, "exif could not be written", it) }
    }

    private val COPIED_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_TIMESTAMP
    )
}
