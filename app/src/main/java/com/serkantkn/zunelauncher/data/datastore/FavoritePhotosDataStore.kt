package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites_datastore")

class FavoritePhotosDataStore(private val context: Context) {

    private val FAVORITES_KEY = stringSetPreferencesKey("favorite_photo_ids")

    val favoritePhotoIds: Flow<Set<String>> = context.favoritesDataStore.data
        .map { preferences ->
            preferences[FAVORITES_KEY] ?: emptySet()
        }

    suspend fun toggleFavorite(photoId: Long) {
        context.favoritesDataStore.edit { preferences ->
            val currentFavorites = preferences[FAVORITES_KEY]?.toMutableSet() ?: mutableSetOf()
            val idStr = photoId.toString()
            if (currentFavorites.contains(idStr)) {
                currentFavorites.remove(idStr)
            } else {
                currentFavorites.add(idStr)
            }
            preferences[FAVORITES_KEY] = currentFavorites
        }
    }
}
