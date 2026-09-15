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
import com.serkantkn.zunelauncher.data.model.StartFolders
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.model.MediaImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    ) { images, favourites ->
        // A favourite marked by an older build was remembered by its store id; one marked now is
        // remembered by a key that survives a rescan. Either counts.
        images.filter { it.stableKey in favourites || it.id.toString() in favourites }
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

    fun toggleFavorite(photo: MediaImage) {
        viewModelScope.launch {
            favoritesDataStore.toggleFavorite(photo.stableKey, photo.id)
        }
    }

    /** Whether a picture is marked, under either the old key or the new one. */
    fun isFavorite(photo: MediaImage, favourites: Set<String>): Boolean =
        photo.stableKey in favourites || photo.id.toString() in favourites

    // ── How the grid is laid out ────────────────────────────────────────────

    private val settings = application.appContainer.settingsDataStore

    /** Chosen by hand, or zero to let the number of pictures decide. */
    val storedColumns: StateFlow<Int> = settings.galleryColumns
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun setColumns(columns: Int) {
        viewModelScope.launch { settings.setGalleryColumns(columns) }
    }

    // ── Choosing several ────────────────────────────────────────────────────

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    /** Ids of the chosen pictures. */
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    /**
     * Whether choosing is on.
     *
     * Kept apart from whether anything is chosen: taking the last picture back out of a selection
     * should leave the mode running, not throw the person back into browsing mid-thought.
     */
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    fun startSelection() {
        _selectionMode.value = true
    }

    fun toggleSelection(photo: MediaImage) {
        _selectionMode.value = true
        _selection.value = if (photo.id in _selection.value) {
            _selection.value - photo.id
        } else {
            _selection.value + photo.id
        }
    }

    fun selectAll(photos: List<MediaImage>) {
        _selectionMode.value = true
        _selection.value = photos.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selection.value = emptySet()
        _selectionMode.value = false
    }

    fun selectedOf(photos: List<MediaImage>): List<MediaImage> =
        photos.filter { it.id in _selection.value }

    // ── Keeping an album on the Start screen ────────────────────────────────

    /** The albums already on the board, so the menu can say "pin" or "unpin" truthfully. */
    val pinnedAlbums: StateFlow<Set<Long>> = settings.startTiles
        .map { tiles -> tiles.mapNotNull { it.albumBucketId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Puts an album on the Start screen, or takes it off again.
     *
     * A pinned album is a live tile of its own: it cycles its own pictures and opens straight into
     * itself, which is what Windows Phone's own picture tiles did.
     */
    fun togglePinAlbum(album: MediaAlbum) {
        viewModelScope.launch {
            val tiles = settings.startTiles.first()
            val id = "${StartTileItem.ALBUM_PREFIX}${album.bucketId}"
            val updated = if (tiles.any { it.id == id }) {
                StartFolders.removeTile(tiles, id)
            } else {
                tiles + StartTileItem.fromAlbum(album.bucketId, album.bucketName)
            }
            settings.setStartTiles(updated)
        }
    }

    /** Opens the album a pinned tile asked for, once the albums have been read. */
    fun openPendingAlbum(bucketId: Long): Boolean {
        val album = _albums.value.firstOrNull { it.bucketId == bucketId } ?: return false
        _selectedAlbum.value = album
        return true
    }

    // ── Searching ───────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ── Sending pictures elsewhere ──────────────────────────────────────────

    /** Hands one picture, or a whole selection, to whatever else can take it. */
    fun share(context: android.content.Context, photos: List<MediaImage>) {
        if (photos.isEmpty()) return
        runCatching {
            val intent = if (photos.size == 1) {
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    putExtra(android.content.Intent.EXTRA_STREAM, photos.first().uri)
                    type = photos.first().mimeType.ifBlank { "image/*" }
                }
            } else {
                android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    putParcelableArrayListExtra(
                        android.content.Intent.EXTRA_STREAM,
                        ArrayList(photos.map { it.uri })
                    )
                    // A mixture of stills and videos is just media as far as the chooser cares.
                    type = if (photos.all { it.isVideo }) "video/*"
                    else if (photos.none { it.isVideo }) "image/*" else "*/*"
                }
            }
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(
                android.content.Intent.createChooser(intent, null)
                    .apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }.onFailure { ZuneLog.w("PicturesHubViewModel", "nothing could take the picture", it) }
    }

    /**
     * Offers a picture to the phone as its wallpaper.
     *
     * The system's own chooser is used rather than setting it outright: it lets the picture be
     * positioned and cropped, and asks which screen it is for — decisions the launcher has no
     * business taking on somebody's behalf.
     */
    fun setAsWallpaper(context: android.content.Context, photo: MediaImage) {
        runCatching {
            val manager = android.app.WallpaperManager.getInstance(context)
            val intent = manager.getCropAndSetWallpaperIntent(photo.uri)
                .apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }.onFailure {
            ZuneLog.w("PicturesHubViewModel", "the wallpaper chooser would not open", it)
            // Some phones refuse the crop intent for a content URI; the plain "use as" chooser
            // reaches the same wallpaper picker by another road.
            runCatching {
                val fallback = android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA).apply {
                    setDataAndType(photo.uri, photo.mimeType.ifBlank { "image/*" })
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("mimeType", photo.mimeType.ifBlank { "image/*" })
                }
                context.startActivity(android.content.Intent.createChooser(fallback, null))
            }
        }
    }

    /** Opens a video in whatever plays videos on this phone. */
    fun playVideo(context: android.content.Context, photo: MediaImage) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(photo.uri, photo.mimeType.ifBlank { "video/*" })
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { ZuneLog.w("PicturesHubViewModel", "nothing on this phone plays video", it) }
    }

    /** Deletes several at once; the system asks for permission on modern Android. */
    fun deletePhotos(
        activity: android.app.Activity,
        photos: List<MediaImage>,
        onIntentSenderRequired: (android.content.IntentSender) -> Unit,
        onDeleted: () -> Unit
    ) {
        if (photos.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    val request = android.provider.MediaStore.createDeleteRequest(
                        activity.contentResolver,
                        photos.map { it.uri }
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onIntentSenderRequired(request.intentSender)
                    }
                }.onFailure { ZuneLog.e("PicturesHubViewModel", "deletePhotos failed", it) }
            } else {
                val deleted = photos.count {
                    runCatching { activity.contentResolver.delete(it.uri, null, null) > 0 }
                        .getOrDefault(false)
                }
                if (deleted > 0) {
                    loadMedia()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDeleted() }
                }
            }
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
