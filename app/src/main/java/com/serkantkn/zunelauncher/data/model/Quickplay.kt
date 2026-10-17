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

/**
 * Something pinned to quickplay - the first of the Zune HD's three parts, "pins", ahead of
 * "history" and "new". It keeps enough of itself to be drawn and opened without asking anybody:
 * a record its cover, a picture its address, a note and an app their names.
 */
data class QuickplayPin(
    val kind: Kind,
    /** What opens it: the album's id, the picture's or video's address, the note's id, the package. */
    val key: String,
    val title: String,
    val subtitle: String = "",
    /** A picture for its square: the cover, or the photo itself. */
    val art: String? = null
) {
    enum class Kind { ALBUM, PHOTO, VIDEO, NOTE, APP }

    /** Two pins are the same thing pinned twice when they open the same thing. */
    fun sameAs(other: QuickplayPin): Boolean = kind == other.kind && key == other.key

    fun toJson(): JSONObject = JSONObject()
        .put(KEY_KIND, kind.name)
        .put(KEY_KEY, key)
        .put(KEY_TITLE, title)
        .put(KEY_SUBTITLE, subtitle)
        .apply { art?.let { put(KEY_ART, it) } }

    companion object {
        private const val KEY_KIND = "k"
        private const val KEY_KEY = "id"
        private const val KEY_TITLE = "t"
        private const val KEY_SUBTITLE = "s"
        private const val KEY_ART = "u"

        fun fromJson(obj: JSONObject): QuickplayPin? {
            val kind = runCatching { Kind.valueOf(obj.optString(KEY_KIND)) }.getOrNull() ?: return null
            val key = obj.optString(KEY_KEY).takeIf { it.isNotBlank() } ?: return null
            return QuickplayPin(
                kind = kind,
                key = key,
                title = obj.optString(KEY_TITLE),
                subtitle = obj.optString(KEY_SUBTITLE),
                art = obj.optString(KEY_ART).takeIf { it.isNotBlank() }
            )
        }

        fun listToJson(pins: List<QuickplayPin>): String =
            JSONArray().apply { pins.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<QuickplayPin> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { fromJson(it) } }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

/**
 * Pins [pin], or takes it off if it is already there. A new pin goes to the front, and the oldest
 * falls off past [QUICKPLAY_PINS_LIMIT]. Pure, so it can be tested.
 */
fun togglePin(current: List<QuickplayPin>, pin: QuickplayPin): List<QuickplayPin> =
    if (current.any { it.sameAs(pin) }) current.filterNot { it.sameAs(pin) }
    else (listOf(pin) + current).take(QUICKPLAY_PINS_LIMIT)

/**
 * The records added most lately, newest first, from every track: an album counts as new when its
 * newest track arrived within [withinSeconds] of [nowSeconds].
 */
fun newestAlbums(songs: List<SongModel>, nowSeconds: Long, withinSeconds: Long, limit: Int): List<RecentAlbum> =
    newestGroups(songs, nowSeconds, withinSeconds, limit, { it.albumId }, { it.dateAdded }).map { (id, tracks) ->
        val first = tracks.first()
        RecentAlbum(id = id, title = first.album, artist = first.artist, artUri = first.albumArtUri?.toString())
    }

/**
 * The groups whose newest member arrived within [withinSeconds] of [nowSeconds], newest first and
 * at most [limit] of them; a member with no group (id 0) or no date is left out. Pure, for tests.
 */
fun <T> newestGroups(
    items: List<T>,
    nowSeconds: Long,
    withinSeconds: Long,
    limit: Int,
    groupOf: (T) -> Long,
    addedOf: (T) -> Long
): List<Pair<Long, List<T>>> =
    items.filter { groupOf(it) != 0L && addedOf(it) > 0 && nowSeconds - addedOf(it) <= withinSeconds }
        .groupBy(groupOf)
        .entries
        .sortedByDescending { (_, members) -> members.maxOf(addedOf) }
        .take(limit)
        .map { (id, members) -> id to members }

/** What the quickplay page has to offer right now, newest first; every part may be empty. */
data class Quickplay(
    val pins: List<QuickplayPin> = emptyList(),
    val recentAlbums: List<RecentAlbum> = emptyList(),
    val recentPhotos: List<MediaImage> = emptyList(),
    val recentNotes: List<Note> = emptyList(),
    val recentApps: List<AppInfo> = emptyList(),
    /** Records added to the phone lately: the Zune HD's "new". */
    val newAlbums: List<RecentAlbum> = emptyList(),
    /** Videos taken or added lately. */
    val newVideos: List<MediaImage> = emptyList()
) {
    val lastAlbum: RecentAlbum? get() = recentAlbums.firstOrNull()
    val lastPhoto: MediaImage? get() = recentPhotos.firstOrNull()
    val lastNote: Note? get() = recentNotes.firstOrNull()
    val isEmpty: Boolean
        get() = pins.isEmpty() && recentAlbums.isEmpty() && recentPhotos.isEmpty() && recentNotes.isEmpty() &&
            recentApps.isEmpty() && newAlbums.isEmpty() && newVideos.isEmpty()
}

/** How much of each the page shows. */
const val QUICKPLAY_ALBUMS = 4
const val QUICKPLAY_PHOTOS = 6
const val QUICKPLAY_NOTES = 3
const val QUICKPLAY_APPS = 8
const val QUICKPLAY_NEW_ALBUMS = 4
const val QUICKPLAY_NEW_VIDEOS = 3
const val QUICKPLAY_PINS_LIMIT = 12

/** How long something counts as "new": a month, as the Zune's own "new" did. */
const val QUICKPLAY_NEW_SECONDS = 30L * 24 * 60 * 60
