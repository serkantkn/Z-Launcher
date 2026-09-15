package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

/**
 * Someone kept one press away on the phone hub's speed dial.
 *
 * The name and the picture are copied rather than looked up: a speed dial entry has to draw
 * instantly and has to keep working when the contacts permission has not been given yet.
 */
data class SpeedDialEntry(
    val number: String,
    val name: String,
    val photoUri: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put(KEY_NUMBER, number)
        .put(KEY_NAME, name)
        .apply { if (!photoUri.isNullOrBlank()) put(KEY_PHOTO, photoUri) }

    companion object {
        private const val KEY_NUMBER = "number"
        private const val KEY_NAME = "name"
        private const val KEY_PHOTO = "photo"

        /** Throws JSONException when [KEY_NUMBER] is missing. */
        fun fromJson(obj: JSONObject): SpeedDialEntry = SpeedDialEntry(
            number = obj.getString(KEY_NUMBER),
            name = obj.optString(KEY_NAME, ""),
            photoUri = obj.optString(KEY_PHOTO).takeIf { it.isNotBlank() }
        )
    }
}
