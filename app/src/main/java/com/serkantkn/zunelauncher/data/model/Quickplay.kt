package com.serkantkn.zunelauncher.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * A record that was played, remembered so the Zune list can offer it again.
 *
 * The Zune HD's home screen kept a "quickplay" beside its list: what you had pinned and what you
 * had lately played, as pictures. This is the "lately played" half, written by the music hub the
 * moment a record starts, so it costs no permission and asks the system nothing.
 */
data class RecentAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: String?
) {
    fun toJson(): JSONObject = JSONObject()
        .put(KEY_ID, id)
        .put(KEY_TITLE, title)
        .put(KEY_ARTIST, artist)
        .apply { artUri?.let { put(KEY_ART, it) } }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "t"
        private const val KEY_ARTIST = "a"
        private const val KEY_ART = "u"

        fun fromJson(obj: JSONObject): RecentAlbum? {
            if (!obj.has(KEY_ID)) return null
            return RecentAlbum(
                id = obj.optLong(KEY_ID),
                title = obj.optString(KEY_TITLE),
                artist = obj.optString(KEY_ARTIST),
                artUri = obj.optString(KEY_ART).takeIf { it.isNotBlank() }
            )
        }

        fun listToJson(albums: List<RecentAlbum>): String =
            JSONArray().apply { albums.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<RecentAlbum> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { fromJson(it) }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

/**
 * Puts [item] at the front of a most-recent-first list, dropping any earlier copy of it and
 * anything beyond [limit]. Pure, so the two "lately" lists share it and it can be tested.
 */
fun <T> mostRecentFirst(current: List<T>, item: T, limit: Int, sameAs: (T, T) -> Boolean): List<T> =
    (listOf(item) + current.filterNot { sameAs(it, item) }).take(limit)

/** How many of each the Zune list keeps. More than it shows, so a pruned app still has a stand-in. */
const val RECENT_APPS_LIMIT = 8
const val RECENT_ALBUMS_LIMIT = 8

/** What the quickplay page has to offer right now, newest first; every part may be empty. */
data class Quickplay(
    val recentAlbums: List<RecentAlbum> = emptyList(),
    val recentPhotos: List<MediaImage> = emptyList(),
    val recentNotes: List<Note> = emptyList(),
    val recentApps: List<AppInfo> = emptyList()
) {
    val lastAlbum: RecentAlbum? get() = recentAlbums.firstOrNull()
    val lastPhoto: MediaImage? get() = recentPhotos.firstOrNull()
    val lastNote: Note? get() = recentNotes.firstOrNull()
    val isEmpty: Boolean
        get() = recentAlbums.isEmpty() && recentPhotos.isEmpty() && recentNotes.isEmpty() && recentApps.isEmpty()
}

/** How much of each the page shows. */
const val QUICKPLAY_ALBUMS = 4
const val QUICKPLAY_PHOTOS = 6
const val QUICKPLAY_NOTES = 3
const val QUICKPLAY_APPS = 8
