package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

/**
 * Internet Hub bookmark. JSON keys match the field names of the previously stored
 * records so existing favorites keep loading.
 */
data class BrowserFavorite(
    val title: String,
    val url: String,
    /** The folder it has been filed under, or blank for one sitting loose on the page. */
    val folder: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("url", url)
        .apply { if (folder.isNotBlank()) put("folder", folder) }

    companion object {
        /** Throws JSONException when "url" is missing. */
        fun fromJson(obj: JSONObject): BrowserFavorite = BrowserFavorite(
            title = obj.optString("title", ""),
            url = obj.getString("url"),
            folder = obj.optString("folder", "")
        )
    }
}

/**
 * Internet Hub history entry. JSON keys match the field names of the previously stored
 * records so existing history keeps loading.
 */
data class BrowserHistory(
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("url", url)
        .put("timestamp", timestamp)

    companion object {
        /** Throws JSONException when "url" is missing. */
        fun fromJson(obj: JSONObject): BrowserHistory = BrowserHistory(
            title = obj.optString("title", ""),
            url = obj.getString("url"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    }
}
