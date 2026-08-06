package com.serkantkn.zunelauncher.data.model

import java.io.File

data class FileItemModel(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String = ""
)
