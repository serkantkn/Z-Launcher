package com.serkantkn.zunelauncher.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.serkantkn.zunelauncher.data.model.MediaAlbum
import com.serkantkn.zunelauncher.data.model.MediaImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    suspend fun getAllImages(): List<MediaImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<MediaImage>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val date = cursor.getLong(dateColumn)
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                val name = cursor.getString(nameColumn) ?: "Image"
                val uri = ContentUris.withAppendedId(collection, id)

                images.add(
                    MediaImage(
                        id = id,
                        uri = uri,
                        dateAdded = date,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        displayName = name
                    )
                )
            }
        }
        images
    }

    suspend fun getAlbums(): List<MediaAlbum> = withContext(Dispatchers.IO) {
        val allImages = getAllImages()
        allImages.groupBy { it.bucketId }
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
