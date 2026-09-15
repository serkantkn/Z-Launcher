package com.serkantkn.zunelauncher.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A playlist somebody made here.
 *
 * The tracks are kept as media-store ids rather than as songs: a playlist outlives the library
 * being rescanned, and a track that has since been deleted simply stops appearing in it instead of
 * leaving a row that plays nothing.
 */
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<String> = emptyList(),
    val createdAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put(KEY_ID, id)
        .put(KEY_NAME, name)
        .put(KEY_CREATED, createdAt)
        .put(KEY_SONGS, JSONArray().apply { songIds.forEach { put(it) } })

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_CREATED = "created"
        private const val KEY_SONGS = "songs"

        /** Throws JSONException when the name is missing. */
        fun fromJson(obj: JSONObject): Playlist {
            val songs = obj.optJSONArray(KEY_SONGS)
            return Playlist(
                id = obj.optString(KEY_ID).ifBlank { UUID.randomUUID().toString() },
                name = obj.getString(KEY_NAME),
                songIds = if (songs == null) emptyList() else {
                    (0 until songs.length()).mapNotNull { songs.optString(it).takeIf { id -> id.isNotBlank() } }
                },
                createdAt = obj.optLong(KEY_CREATED, 0L)
            )
        }
    }
}
