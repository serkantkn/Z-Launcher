package com.serkantkn.zunelauncher.ui.screens.pictures

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.datastore.FavoritePhotosDataStore
import com.serkantkn.zunelauncher.data.model.MediaAlbum
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PicturesHubViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaRepository = MediaRepository(application)
    private val favoritesDataStore = FavoritePhotosDataStore(application)

    private val _hasPermission = MutableStateFlow(checkPermissions())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _allImages = MutableStateFlow<List<MediaImage>>(emptyList())
    private val _albums = MutableStateFlow<List<MediaAlbum>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val allImages: StateFlow<List<MediaImage>> = _allImages.asStateFlow()
    val albums: StateFlow<List<MediaAlbum>> = _albums.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Expose favorites seamlessly by combining allImages with DataStore
    val favoriteImages: StateFlow<List<MediaImage>> = combine(
        _allImages,
        favoritesDataStore.favoritePhotoIds
    ) { images, favIds ->
        images.filter { favIds.contains(it.id.toString()) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // To check if a specific ID is favorite
    val favoritePhotoIds: StateFlow<Set<String>> = favoritesDataStore.favoritePhotoIds
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private val _selectedAlbum = MutableStateFlow<MediaAlbum?>(null)
    val selectedAlbum: StateFlow<MediaAlbum?> = _selectedAlbum.asStateFlow()

    init {
        if (_hasPermission.value) {
            loadMedia()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            loadMedia()
        }
    }

    private fun checkPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            getApplication(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            _allImages.value = mediaRepository.getAllImages()
            _albums.value = mediaRepository.getAlbums()
            _isLoading.value = false
        }
    }

    fun selectAlbum(album: MediaAlbum?) {
        _selectedAlbum.value = album
    }

    fun toggleFavorite(photoId: Long) {
        viewModelScope.launch {
            favoritesDataStore.toggleFavorite(photoId)
        }
    }
}
