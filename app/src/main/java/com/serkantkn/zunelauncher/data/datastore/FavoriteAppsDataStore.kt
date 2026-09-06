package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.FavoriteAppItem
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

private val Context.favoriteAppsDataStore by preferencesDataStore(name = "favorite_apps_datastore")

/**
 * Ordered list of apps pinned to the Start screen, stored as a JSON array of
 * [FavoriteAppItem.toJson] objects. Two legacy formats are still read and migrated on first
 * collection: an unordered string set of package names (v1) and a "pkg:span,pkg:span" string (v2).
 */
class FavoriteAppsDataStore(private val context: Context) {

    companion object {
        private const val TAG = "FavoriteAppsDataStore"
        /** v1: unordered set of package names (span defaults to 2). */
        private val LEGACY_SET_KEY = stringSetPreferencesKey("favorite_app_packages")
        /** v2: ordered "pkg1:span1,pkg2:span2" string. */
        private val LEGACY_DELIMITED_KEY = stringPreferencesKey("favorite_apps_data_v2")
        /** v3: JSON array of {"packageName","span"} objects, order preserved. */
        private val FAVORITE_APPS_JSON_KEY = stringPreferencesKey("favorite_apps_json")
    }

    val favoritePackages: Flow<List<FavoriteAppItem>> = flow {
        migrateLegacyIfNeeded()
        emitAll(context.favoriteAppsDataStore.data.map { preferences -> readList(preferences) })
    }

    suspend fun addFavorite(packageName: String) {
        context.favoriteAppsDataStore.edit { preferences ->
            val currentList = readList(preferences).toMutableList()
            if (currentList.none { it.packageName == packageName }) {
                currentList.add(FavoriteAppItem(packageName, FavoriteAppItem.DEFAULT_SPAN))
                saveListToPreferences(preferences, currentList)
            }
        }
    }

    suspend fun removeFavorite(packageName: String) {
        context.favoriteAppsDataStore.edit { preferences ->
            val currentList = readList(preferences).toMutableList()
            currentList.removeAll { it.packageName == packageName }
            saveListToPreferences(preferences, currentList)
        }
    }

    suspend fun toggleFavorite(packageName: String) {
        context.favoriteAppsDataStore.edit { preferences ->
            val currentList = readList(preferences).toMutableList()
            if (currentList.any { it.packageName == packageName }) {
                currentList.removeAll { it.packageName == packageName }
            } else {
                currentList.add(FavoriteAppItem(packageName, FavoriteAppItem.DEFAULT_SPAN))
            }
            saveListToPreferences(preferences, currentList)
        }
    }

    suspend fun updateSpan(packageName: String, span: Int) {
        context.favoriteAppsDataStore.edit { preferences ->
            val currentList = readList(preferences).toMutableList()
            val index = currentList.indexOfFirst { it.packageName == packageName }
            if (index != -1) {
                currentList[index] = currentList[index].copy(span = span)
                saveListToPreferences(preferences, currentList)
            }
        }
    }

    suspend fun updateOrder(newList: List<FavoriteAppItem>) {
        context.favoriteAppsDataStore.edit { preferences ->
            saveListToPreferences(preferences, newList)
        }
    }

    /** JSON first, then the v2 delimited string, then the v1 set (in its iteration order). */
    private fun readList(preferences: Preferences): List<FavoriteAppItem> {
        val json = preferences[FAVORITE_APPS_JSON_KEY]
        if (json != null) return parseJsonObjectList(json, TAG, FavoriteAppItem::fromJson)
        val delimited = preferences[LEGACY_DELIMITED_KEY]
        if (delimited != null) {
            if (delimited.isEmpty()) return emptyList()
            return delimited.split(",").mapNotNull { FavoriteAppItem.fromLegacyString(it) }
        }
        return preferences[LEGACY_SET_KEY]?.map { FavoriteAppItem(it, FavoriteAppItem.DEFAULT_SPAN) }
            ?: emptyList()
    }

    /** Writes the JSON form and drops both legacy keys. */
    private fun saveListToPreferences(preferences: MutablePreferences, list: List<FavoriteAppItem>) {
        preferences[FAVORITE_APPS_JSON_KEY] = list.toJsonArrayString { it.toJson() }
        preferences.remove(LEGACY_DELIMITED_KEY)
        preferences.remove(LEGACY_SET_KEY)
    }

    /** Rewrites a legacy value as JSON once, so the legacy parsers are only a fallback. */
    @Volatile
    private var legacyMigrated = false

    private suspend fun migrateLegacyIfNeeded() {
        if (legacyMigrated) return
        try {
            val preferences = context.favoriteAppsDataStore.data.first()
            legacyMigrated = true
            if (preferences.contains(FAVORITE_APPS_JSON_KEY)) return
            if (!preferences.contains(LEGACY_DELIMITED_KEY) && !preferences.contains(LEGACY_SET_KEY)) return
            context.favoriteAppsDataStore.edit { mutable ->
                if (!mutable.contains(FAVORITE_APPS_JSON_KEY)) {
                    saveListToPreferences(mutable, readList(mutable))
                }
            }
            ZuneLog.d(TAG, "migrated legacy favorite apps to json")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ZuneLog.w(TAG, "legacy favorite apps migration failed; legacy value kept as fallback", e)
        }
    }
}
