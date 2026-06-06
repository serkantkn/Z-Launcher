package com.serkantkn.zunelauncher.data.model

data class AppInfo(
    val packageName: String,
    val label: String,
    val activityName: String
)

data class FavoriteAppItem(
    val packageName: String,
    val span: Int = 1
)
