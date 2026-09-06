package com.serkantkn.zunelauncher.ui.screens.pictures

import com.serkantkn.zunelauncher.util.toUserMessage
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.MediaAlbum
import com.serkantkn.zunelauncher.data.model.MediaImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PicturesHubViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaRepository = application.appContainer.mediaRepository
    private val favoritesDataStore = application.appContainer.favoritePhotosDataStore

    private val _hasPermission = MutableStateFlow(checkPermissions())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _allImages = MutableStateFlow<List<MediaImage>>(emptyList())
    private val _cameraRollImages = MutableStateFlow<List<MediaImage>>(emptyList())
    private val _albums = MutableStateFlow<List<MediaAlbum>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Last load failure as user text, or null. Shown by the hub's empty state. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    val allImages: StateFlow<List<MediaImage>> = _allImages.asStateFlow()
    val cameraRollImages: StateFlow<List<MediaImage>> = _cameraRollImages.asStateFlow()
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

    private val mediaContentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            super.onChange(selfChange, uri)
            if (_hasPermission.value) {
                loadMedia()
            }
        }
    }

    init {
        try {
            application.contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaContentObserver
            )
        } catch (e: Exception) {
            ZuneLog.e("PicturesHubViewModel", "onChange failed", e)
        }
        if (_hasPermission.value) {
            loadMedia()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().contentResolver.unregisterContentObserver(mediaContentObserver)
        } catch (e: Exception) {
            ZuneLog.e("PicturesHubViewModel", "onCleared failed", e)
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

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val images = mediaRepository.getAllImages()
                _allImages.value = images
                _cameraRollImages.value = mediaRepository.getCameraRollImages(images)
                _albums.value = mediaRepository.getAlbums(images)
                _errorMessage.value = null
            } catch (e: Exception) {
                ZuneLog.e("PicturesHubViewModel", "loadMedia failed", e)
                _errorMessage.value = e.toUserMessage(getApplication())
            } finally {
                _isLoading.value = false
            }
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

    fun deletePhoto(
        activity: android.app.Activity,
        photo: MediaImage,
        onIntentSenderRequired: (android.content.IntentSender) -> Unit,
        onDeleted: () -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pi = android.provider.MediaStore.createDeleteRequest(
                        activity.contentResolver,
                        listOf(photo.uri)
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onIntentSenderRequired(pi.intentSender)
                    }
                } catch (e: Exception) {
                    ZuneLog.e("PicturesHubViewModel", "deletePhoto failed", e)
                }
            } else {
                try {
                    val count = activity.contentResolver.delete(photo.uri, null, null)
                    if (count > 0) {
                        loadMedia()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onDeleted()
                        }
                    }
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onIntentSenderRequired(e.userAction.actionIntent.intentSender)
                        }
                    }
                } catch (e: Exception) {
                    ZuneLog.e("PicturesHubViewModel", "deletePhoto failed", e)
                }
            }
        }
    }

    fun saveEditedPhoto(bitmap: android.graphics.Bitmap, onSaved: (android.net.Uri) -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val filename = "Zune_Edit_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Zune")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { stream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                loadMedia()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSaved(imageUri)
                }
            }
        }
    }
}
