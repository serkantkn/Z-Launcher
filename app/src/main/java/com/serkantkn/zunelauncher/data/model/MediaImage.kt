package com.serkantkn.zunelauncher.data.model

import android.net.Uri

data class MediaImage(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,
    val bucketId: Long,
    val bucketName: String,
    val displayName: String,
    val relativePath: String = ""
)

data class MediaAlbum(
    val bucketId: Long,
    val bucketName: String,
    val coverUri: Uri,
    val photoCount: Int
)
