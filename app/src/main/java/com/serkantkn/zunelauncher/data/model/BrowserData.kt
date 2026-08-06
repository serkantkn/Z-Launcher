package com.serkantkn.zunelauncher.data.model

import com.google.gson.annotations.SerializedName

data class BrowserFavorite(
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String
)

data class BrowserHistory(
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
