package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.Playlist
import com.serkantkn.zunelauncher.data.model.parseJsonStringList
import com.serkantkn.zunelauncher.data.model.toJsonStringArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.serkantkn.zunelauncher.util.ZuneLog
import org.json.JSONArray

private val Context.musicDataStore by preferencesDataStore(name = "music_datastore")

/** What was playing, and how the hub was set up, kept between one run and the next. */
data class RememberedQueue(
    val mediaIds: List<String>,
    val index: Int,
    val positionMillis: Long
) {
    val isEmpty: Boolean get() = mediaIds.isEmpty()
}

/**
 * The music hub's own memory.
 *
 * A player that forgets its queue the moment the launcher is closed is not much of a player: the
 * album somebody was halfway through is gone, and there is no way back to it but finding it again.
 * The queue is kept as a list of media-store ids, so it survives a restart and quietly drops any
 * track that has since been deleted.
 */
class MusicDataStore(private val context: Context) {

    val rememberedQueue: Flow<RememberedQueue> = context.musicDataStore.data.map { preferences ->
        RememberedQueue(
            mediaIds = parseJsonStringList(preferences[QUEUE_IDS].orEmpty(), TAG),
            index = preferences[QUEUE_INDEX] ?: 0,
            positionMillis = preferences[QUEUE_POSITION] ?: 0L
        )
    }

    suspend fun rememberQueue(mediaIds: List<String>, index: Int, positionMillis: Long) {
        context.musicDataStore.edit { preferences ->
            preferences[QUEUE_IDS] = mediaIds.toJsonStringArray()
            preferences[QUEUE_INDEX] = index
            preferences[QUEUE_POSITION] = positionMillis
        }
    }

    suspend fun forgetQueue() {
        context.musicDataStore.edit { preferences ->
            preferences.remove(QUEUE_IDS)
            preferences.remove(QUEUE_INDEX)
            preferences.remove(QUEUE_POSITION)
        }
    }

    /** How the song list was last sorted, by the name of a [com.serkantkn.zunelauncher.util.MusicSort]. */
    val sortName: Flow<String> = context.musicDataStore.data.map { it[SORT] ?: "" }

    suspend fun setSortName(name: String) {
        context.musicDataStore.edit { it[SORT] = name }
    }

    /** Whether the hub was left shuffling, so the button looks right before the player connects. */
    val shuffleEnabled: Flow<Boolean> = context.musicDataStore.data.map { it[SHUFFLE] ?: false }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.musicDataStore.edit { it[SHUFFLE] = enabled }
    }

    val repeatMode: Flow<Int> = context.musicDataStore.data.map { it[REPEAT] ?: 0 }

    suspend fun setRepeatMode(mode: Int) {
        context.musicDataStore.edit { it[REPEAT] = mode }
    }

    // ── Playlists ───────────────────────────────────────────────────────────

    val playlists: Flow<List<Playlist>> = context.musicDataStore.data.map { preferences ->
        parsePlaylists(preferences[PLAYLISTS].orEmpty())
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList(), now: Long): Playlist {
        val playlist = Playlist(name = name.trim(), songIds = songIds, createdAt = now)
        editPlaylists { it + playlist }
        return playlist
    }

    suspend fun renamePlaylist(id: String, name: String) {
        editPlaylists { lists ->
            lists.map { if (it.id == id) it.copy(name = name.trim()) else it }
        }
    }

    suspend fun deletePlaylist(id: String) {
        editPlaylists { lists -> lists.filterNot { it.id == id } }
    }

    /** Adds tracks, skipping any the list already holds. */
    suspend fun addToPlaylist(id: String, songIds: List<String>) {
        editPlaylists { lists ->
            lists.map { playlist ->
                if (playlist.id != id) playlist
                else playlist.copy(songIds = playlist.songIds + songIds.filterNot { it in playlist.songIds })
            }
        }
    }

    suspend fun removeFromPlaylist(id: String, songId: String) {
        editPlaylists { lists ->
            lists.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it }
        }
    }

    suspend fun moveInPlaylist(id: String, from: Int, to: Int) {
        editPlaylists { lists ->
            lists.map { playlist ->
                if (playlist.id != id) return@map playlist
                val songs = playlist.songIds.toMutableList()
                if (from !in songs.indices || to !in songs.indices) return@map playlist
                songs.add(to, songs.removeAt(from))
                playlist.copy(songIds = songs)
            }
        }
    }

    private suspend fun editPlaylists(change: (List<Playlist>) -> List<Playlist>) {
        context.musicDataStore.edit { preferences ->
            val current = parsePlaylists(preferences[PLAYLISTS].orEmpty())
            preferences[PLAYLISTS] = JSONArray().apply {
                change(current).forEach { put(it.toJson()) }
            }.toString()
        }
    }

    private fun parsePlaylists(json: String): List<Playlist> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                runCatching { Playlist.fromJson(array.getJSONObject(index)) }.getOrNull()
            }
        }.getOrElse {
            ZuneLog.w(TAG, "the playlists could not be read; starting from none", it)
            emptyList()
        }
    }

    /** Reads once, for the cold start that puts the queue back. */
    suspend fun currentQueue(): RememberedQueue = rememberedQueue.first()

    private companion object {
        const val TAG = "MusicDataStore"
        val QUEUE_IDS = stringPreferencesKey("queue_media_ids")
        val QUEUE_INDEX = intPreferencesKey("queue_index")
        val QUEUE_POSITION = longPreferencesKey("queue_position")
        val SORT = stringPreferencesKey("song_sort")
        val SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val REPEAT = intPreferencesKey("repeat_mode")
        val PLAYLISTS = stringPreferencesKey("playlists_json")
    }
}
