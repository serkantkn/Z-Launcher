package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.FavoriteAppItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoriteAppsDataStore by preferencesDataStore(name = "favorite_apps_datastore")

class FavoriteAppsDataStore(private val context: Context) {

    companion object {
        // Deprecated old key
        private val OLD_FAVORITE_APPS_KEY = stringSetPreferencesKey("favorite_app_packages")
        // New ordered list key format: "pkg1:span1,pkg2:span2"
        private val FAVORITE_APPS_DATA_KEY = stringPreferencesKey("favorite_apps_data_v2")
    }

    val favoritePackages: Flow<List<FavoriteAppItem>> = context.favoriteAppsDataStore.data
        .map { preferences ->
            val newData = preferences[FAVORITE_APPS_DATA_KEY]
            if (newData != null) {
                // Parse new data
                if (newData.isEmpty()) return@map emptyList()
                newData.split(",").mapNotNull {
                    val parts = it.split(":")
                    if (parts.size == 2) {
                        FavoriteAppItem(parts[0], parts[1].toIntOrNull() ?: 1)
                    } else null
                }
            } else {
                // Migrate from old data if it exists
                val oldData = preferences[OLD_FAVORITE_APPS_KEY] ?: emptySet()
                oldData.map { FavoriteAppItem(it, 1) }
            }
        }

    suspend fun addFavorite(packageName: String) {
        context.favoriteAppsDataStore.edit { preferences ->
            val currentList = getListFromPreferences(preferences).toMutableList()
            if (currentList.none { it.packageName == packageName }) {
                currentList.add(FavoriteAppItem(packageName, 1))
                saveListToPreferences(preferences, currentList)
            }
        }
    }

    suspend fun removeFavorite(packageName: String) {
        context.favoriteAppsDataStore.edit { preferences ->
            val currentList = getListFromPreferences(preferences).toMutableList()
            currentList.removeAll { it.packageName == packageName }
            saveListToPreferences(preferences, currentList)
        }
    }

    suspend fun toggleFavorite(packageName: String) {
        context.favoriteAppsDataStore.edit { preferences ->
            val currentList = getListFromPreferences(preferences).toMutableList()
            if (currentList.any { it.packageName == packageName }) {
                currentList.removeAll { it.packageName == packageName }
            } else {
                currentList.add(FavoriteAppItem(packageName, 1))
            }
            saveListToPreferences(preferences, currentList)
        }
    }

    suspend fun updateSpan(packageName: String, span: Int) {
        context.favoriteAppsDataStore.edit { preferences ->
            val currentList = getListFromPreferences(preferences).toMutableList()
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

    private fun getListFromPreferences(preferences: androidx.datastore.preferences.core.Preferences): List<FavoriteAppItem> {
        val newData = preferences[FAVORITE_APPS_DATA_KEY]
        if (newData != null) {
            if (newData.isEmpty()) return emptyList()
            return newData.split(",").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) {
                    FavoriteAppItem(parts[0], parts[1].toIntOrNull() ?: 1)
                } else null
            }
        }
        val oldData = preferences[OLD_FAVORITE_APPS_KEY] ?: emptySet()
        return oldData.map { FavoriteAppItem(it, 1) }
    }

    private fun saveListToPreferences(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        list: List<FavoriteAppItem>
    ) {
        val serialized = list.joinToString(",") { "${it.packageName}:${it.span}" }
        preferences[FAVORITE_APPS_DATA_KEY] = serialized
        // Clean up old key on first save
        if (preferences.contains(OLD_FAVORITE_APPS_KEY)) {
            preferences.remove(OLD_FAVORITE_APPS_KEY)
        }
    }
}
