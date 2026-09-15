package com.serkantkn.zunelauncher.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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

    /** The folder the camera's own shots live in. */
    const val ALBUM = "Z Launcher"

    fun fileName(prefix: String, extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$stamp.$extension"
    }

    /** Writes a JPEG into Pictures/[ALBUM] and returns where it landed. */
    fun saveJpeg(context: Context, bitmap: Bitmap, name: String, quality: Int = 95): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, name, quality)
        } else {
            saveViaPublicFolder(context, bitmap, name, quality)
        }
    } catch (e: Exception) {
        ZuneLog.e(TAG, "could not save $name", e)
        null
    }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, name: String, quality: Int): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            ?: return null
        // Pending until it is fully written, so nothing reads half a picture.
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** Before Android 10 there is no relative path: the file is written and then announced. */
    private fun saveViaPublicFolder(context: Context, bitmap: Bitmap, name: String, quality: Int): Uri? {
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM
        )
        if (!folder.exists() && !folder.mkdirs()) return null
        val file = File(folder, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }
}
