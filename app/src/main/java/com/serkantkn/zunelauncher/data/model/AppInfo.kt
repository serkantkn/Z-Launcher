package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

data class AppInfo(
    val packageName: String,
    val label: String,
    val activityName: String,
    /**
     * When the app first arrived on the phone. Zero when the phone would not say — an app that
     * cannot be dated simply never appears under "new".
     */
    val firstInstallTime: Long = 0L
)


/** A pinned app on the Start screen. Persisted by FavoriteAppsDataStore as a JSON array. */
data class FavoriteAppItem(
    val packageName: String,
    val span: Int = 2
) {
    fun toJson(): JSONObject = JSONObject()
        .put(KEY_PACKAGE_NAME, packageName)
        .put(KEY_SPAN, span)

    companion object {
        const val DEFAULT_SPAN = 2
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_SPAN = "span"

        /** Throws JSONException when [KEY_PACKAGE_NAME] is missing. */
        fun fromJson(obj: JSONObject): FavoriteAppItem = FavoriteAppItem(
            packageName = obj.getString(KEY_PACKAGE_NAME),
            span = obj.optInt(KEY_SPAN, DEFAULT_SPAN)
        )

        /** Legacy "pkg:span" element of the pre-JSON comma-delimited list; null when malformed. */
        fun fromLegacyString(itemStr: String): FavoriteAppItem? {
            val parts = itemStr.split(":")
            if (parts.size != 2) return null
            return FavoriteAppItem(parts[0], parts[1].toIntOrNull() ?: DEFAULT_SPAN)
        }
    }
}
