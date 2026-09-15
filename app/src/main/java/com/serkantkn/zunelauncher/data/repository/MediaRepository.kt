package com.serkantkn.zunelauncher.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.serkantkn.zunelauncher.data.model.MediaAlbum
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * What the phone has taken and been sent: photos and videos, read out of the media store.
 *
 * Two things changed here. It reads the video collection as well as the image one, because the
 * launcher's own camera records video and the gallery was pretending otherwise. And it no longer
 * asks the filesystem whether each row's file is really there — that was a disk lookup per photo,
 * thousands of them on a full phone before anything appeared, resting on a `DATA` column that
 * Android stopped guaranteeing years ago. The store's own "this is not pending and not in the
 * bin" flags say the same thing without leaving the query.
 */
class MediaRepository(private val context: Context) {

    /** Everything, newest first. */
    suspend fun getAllImages(): List<MediaImage> = withContext(Dispatchers.IO) {
        val items = buildList {
            addAll(query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false))
            addAll(query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true))
        }
        items.sortedByDescending { it.dateAdded }
    }

    private fun query(collection: Uri, isVideo: Boolean): List<MediaImage> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.BUCKET_ID)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        val where = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
            add("${MediaStore.MediaColumns.SIZE} > 0")
        }.joinToString(" AND ")

        return runCatching {
            context.contentResolver.query(
                collection,
                projection,
                where,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val bucketIdColumn = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1
                val durationColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val relativePath = if (pathColumn != -1) cursor.getString(pathColumn).orEmpty() else ""
                        val storedBucket = if (bucketNameColumn != -1) cursor.getString(bucketNameColumn) else null
                        add(
                            MediaImage(
                                id = id,
                                uri = ContentUris.withAppendedId(collection, id),
                                dateAdded = cursor.getLong(dateColumn),
                                bucketId = if (bucketIdColumn != -1) cursor.getLong(bucketIdColumn) else 0L,
                                // A store row without a folder name still came from somewhere:
                                // the path says where, which beats calling every one of them
                                // "unknown" and heaping them into a single nameless album.
                                bucketName = storedBucket?.takeIf { it.isNotBlank() }
                                    ?: folderFromPath(relativePath),
                                displayName = if (nameColumn != -1) cursor.getString(nameColumn).orEmpty() else "",
                                relativePath = relativePath,
                                isVideo = isVideo,
                                durationMillis = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L,
                                width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0,
                                height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0,
                                sizeBytes = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L,
                                mimeType = if (mimeColumn != -1) cursor.getString(mimeColumn).orEmpty() else ""
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrElse {
            ZuneLog.e(TAG, "the media store would not be read", it)
            throw it
        }
    }

    /** The last folder of a relative path: "DCIM/Camera/" → "Camera". */
    private fun folderFromPath(relativePath: String): String =
        relativePath.trim('/').substringAfterLast('/', "").ifBlank { UNSORTED }

    /**
     * Whether a picture came from the camera rather than from a download or a chat.
     *
     * Every phone spells its own camera folder differently, so this asks the same question several
     * ways rather than trusting one of them.
     */
    fun isCameraRoll(image: MediaImage): Boolean {
        val bucket = image.bucketName.lowercase(Locale.ROOT)
        val path = image.relativePath.lowercase(Locale.ROOT)
        return bucket == "camera" || bucket == "kamera" || bucket == "dcim" ||
            path.contains("dcim/camera") || path.contains("dcim/100media") ||
            path.contains("dcim/100andro") || path.startsWith("dcim/")
    }

    /**
     * The camera roll.
     *
     * It used to hand back the whole library when the camera folder was empty, so a tab headed
     * "kamera rulosu" showed screenshots and downloads and everything else. An empty camera roll
     * is now an empty camera roll, which the page says in as many words.
     */
    fun getCameraRollImages(allImages: List<MediaImage>): List<MediaImage> =
        allImages.filter { isCameraRoll(it) }

    suspend fun getAlbums(): List<MediaAlbum> = withContext(Dispatchers.IO) { getAlbums(getAllImages()) }

    fun getAlbums(allImages: List<MediaImage>): List<MediaAlbum> =
        allImages.groupBy { it.bucketId }
            .map { (bucketId, inBucket) ->
                val newest = inBucket.first()
                MediaAlbum(
                    bucketId = bucketId,
                    bucketName = newest.bucketName,
                    // The newest picture is the one somebody recognises the album by.
                    coverUri = newest.uri,
                    photoCount = inBucket.size,
                    videoCount = inBucket.count { it.isVideo }
                )
            }
            .sortedByDescending { it.photoCount }

    private companion object {
        const val TAG = "MediaRepository"

        /** What an album with no name of its own is called, before the UI translates it. */
        const val UNSORTED = ""
    }
}
