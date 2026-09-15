package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.parseJsonStringList
import com.serkantkn.zunelauncher.data.model.toJsonStringArray
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites_datastore")

/**
 * The pictures marked as favourites, stored as a JSON array in the order they were marked.
 *
 * What is stored is each picture's [com.serkantkn.zunelauncher.data.model.MediaImage.stableKey],
 * not its media-store id: ids are handed out by the store and change when the library is rebuilt,
 * which would quietly empty somebody's favourites. Keys written by older builds were plain ids and
 * are still matched, so nothing already marked is lost.
 */
class FavoritePhotosDataStore(private val context: Context) {

    companion object {
        private const val TAG = "FavoritePhotosDataStore"
        /** v1: unordered string set of photo ids. */
        private val LEGACY_SET_KEY = stringSetPreferencesKey("favorite_photo_ids")
        /** v2: JSON array of photo id strings, order preserved. */
        private val FAVORITES_JSON_KEY = stringPreferencesKey("favorite_photo_ids_json")
    }

    val favoritePhotoIds: Flow<Set<String>> = flow {
        migrateLegacyIfNeeded()
        emitAll(context.favoritesDataStore.data.map { preferences -> readIds(preferences) })
    }

    /** Marks or unmarks one picture, by whichever key it is already remembered under. */
    suspend fun toggleFavorite(key: String, legacyId: Long? = null) {
        context.favoritesDataStore.edit { preferences ->
            val current = readIds(preferences).toMutableSet()
            val legacy = legacyId?.toString()
            when {
                current.remove(key) -> Unit
                legacy != null && current.remove(legacy) -> Unit
                else -> current.add(key)
            }
            saveIds(preferences, current)
        }
    }

    /** Takes several out at once, whichever way each was stored. */
    suspend fun removeAll(keys: Set<String>, legacyIds: Set<String> = emptySet()) {
        context.favoritesDataStore.edit { preferences ->
            val current = readIds(preferences).toMutableSet()
            current.removeAll(keys)
            current.removeAll(legacyIds)
            saveIds(preferences, current)
        }
    }

    /** JSON first, then the legacy set. The result keeps insertion order. */
    private fun readIds(preferences: Preferences): Set<String> {
        val json = preferences[FAVORITES_JSON_KEY]
        if (json != null) return LinkedHashSet(parseJsonStringList(json, TAG))
        return preferences[LEGACY_SET_KEY]?.let { LinkedHashSet(it) } ?: emptySet()
    }

    /** Writes the JSON form and drops the legacy key. */
    private fun saveIds(preferences: MutablePreferences, ids: Set<String>) {
        preferences[FAVORITES_JSON_KEY] = ids.toJsonStringArray()
        preferences.remove(LEGACY_SET_KEY)
    }

    /** Rewrites the legacy set as JSON once, so the set parser is only a fallback. */
    @Volatile
    private var legacyMigrated = false

    private suspend fun migrateLegacyIfNeeded() {
        if (legacyMigrated) return
        try {
            val preferences = context.favoritesDataStore.data.first()
            legacyMigrated = true
            if (preferences.contains(FAVORITES_JSON_KEY) || !preferences.contains(LEGACY_SET_KEY)) return
            context.favoritesDataStore.edit { mutable ->
                if (!mutable.contains(FAVORITES_JSON_KEY)) {
                    saveIds(mutable, readIds(mutable))
                }
            }
            ZuneLog.d(TAG, "migrated legacy favorite photos to json")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ZuneLog.w(TAG, "legacy favorite photos migration failed; legacy value kept as fallback", e)
        }
    }
}
