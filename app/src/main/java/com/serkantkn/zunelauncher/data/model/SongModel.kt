package com.serkantkn.zunelauncher.data.model

import android.net.Uri

data class SongModel(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val data: String?
)
