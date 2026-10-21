package com.serkantkn.zeta.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.serkantkn.zeta.util.ZuneLog

/** One video in the phone's media store. */
data class LocalVideo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val dateAddedSec: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val folder: String
) {
    /** The name as a title: no extension, which says nothing to anybody watching it. */
    val title: String get() = name.substringBeforeLast('.', name)
}

/** The videos on the phone, newest first, as the media store knows them. */
object VideoLibrary {
    private const val TAG = "VideoLibrary"

    fun query(context: Context): List<LocalVideo> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        val selection = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
        }.joinToString(" AND ").ifEmpty { null }
        val videos = mutableListOf<LocalVideo>()
        try {
            context.contentResolver.query(
                collection, projection, selection, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    videos += LocalVideo(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cursor.getString(nameCol).orEmpty(),
                        durationMs = cursor.getLong(durationCol),
                        dateAddedSec = cursor.getLong(addedCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        mimeType = cursor.getString(mimeCol).orEmpty(),
                        folder = cursor.getString(folderCol).orEmpty()
                    )
                }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the videos could not be read", e)
        }
        return videos
    }
}
