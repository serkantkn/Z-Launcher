package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.serkantkn.zunelauncher.data.model.MediaAlbum
import com.serkantkn.zunelauncher.data.model.MediaImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context) {

    suspend fun getAllImages(): List<MediaImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<MediaImage>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projectionList = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Images.Media.RELATIVE_PATH)
        }
        val projection = projectionList.toTypedArray()

        val selectionList = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selectionList.add("${MediaStore.MediaColumns.IS_PENDING} = 0")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            selectionList.add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
        }
        selectionList.add("${MediaStore.MediaColumns.SIZE} > 0")
        val selection = selectionList.joinToString(" AND ")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    // Check if file physically exists on storage
                    if (dataColumn != -1) {
                        val filePath = cursor.getString(dataColumn)
                        if (!filePath.isNullOrEmpty()) {
                            val file = File(filePath)
                            if (!file.exists()) {
                                continue
                            }
                        }
                    }

                    val id = cursor.getLong(idColumn)
                    val date = cursor.getLong(dateColumn)
                    val bucketId = cursor.getLong(bucketIdColumn)
                    val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                    val name = cursor.getString(nameColumn) ?: "Image"
                    val relativePath = if (relativePathColumn != -1) {
                        cursor.getString(relativePathColumn) ?: ""
                    } else ""
                    val uri = ContentUris.withAppendedId(collection, id)

                    images.add(
                        MediaImage(
                            id = id,
                            uri = uri,
                            dateAdded = date,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            displayName = name,
                            relativePath = relativePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            ZuneLog.e("MediaRepository", "getAllImages failed", e)
            throw e
        }
        images
    }

    fun isCameraRoll(image: MediaImage): Boolean {
        val bName = image.bucketName.lowercase()
        val rPath = image.relativePath.lowercase()
        return bName == "camera" || bName == "kamera" || bName == "dcim" ||
                rPath.contains("dcim/camera") || rPath.contains("dcim/100media") || rPath.contains("dcim/100andro")
    }

    fun getCameraRollImages(allImages: List<MediaImage>): List<MediaImage> {
        val cameraRoll = allImages.filter { isCameraRoll(it) }
        return if (cameraRoll.isNotEmpty()) cameraRoll else allImages
    }

    suspend fun getAlbums(): List<MediaAlbum> = withContext(Dispatchers.IO) {
        val allImages = getAllImages()
        getAlbums(allImages)
    }

    fun getAlbums(allImages: List<MediaImage>): List<MediaAlbum> {
        return allImages.groupBy { it.bucketId }
            .map { (bucketId, imagesInBucket) ->
                val firstImage = imagesInBucket.first()
                MediaAlbum(
                    bucketId = bucketId,
                    bucketName = firstImage.bucketName,
                    coverUri = firstImage.uri,
                    photoCount = imagesInBucket.size
                )
            }
            .sortedByDescending { it.photoCount }
    }
}
