package com.serkantkn.zunelauncher.data.model

import java.io.File

data class FileItemModel(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String = "",
    /** How many things a folder holds - counting hidden ones only while they are shown; -1 when not known. */
    val childCount: Int = -1
)
