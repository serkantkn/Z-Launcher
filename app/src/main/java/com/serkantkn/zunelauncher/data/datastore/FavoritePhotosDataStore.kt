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
 * MediaStore ids of the photos marked as favorites, stored as a JSON array of id strings in
 * insertion order. The legacy unordered string set is still read and migrated on first collection.
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

    suspend fun toggleFavorite(photoId: Long) {
        context.favoritesDataStore.edit { preferences ->
            val currentFavorites = readIds(preferences).toMutableSet()
            val idStr = photoId.toString()
            if (currentFavorites.contains(idStr)) {
                currentFavorites.remove(idStr)
            } else {
                currentFavorites.add(idStr)
            }
            saveIds(preferences, currentFavorites)
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
