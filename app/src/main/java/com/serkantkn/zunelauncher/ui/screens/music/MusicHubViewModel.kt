package com.serkantkn.zunelauncher.ui.screens.music

import com.serkantkn.zunelauncher.util.toUserMessage
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.serkantkn.zunelauncher.data.model.SongModel
import com.serkantkn.zunelauncher.data.service.ThirdPartyMediaController
import com.serkantkn.zunelauncher.data.service.LocalMediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicHubViewModel(application: Application) : AndroidViewModel(application) {

    private val musicRepository = application.appContainer.musicRepository
    val globalMediaController = ThirdPartyMediaController(application)
    val localMediaController = LocalMediaController(application)

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Last load failure as user text, or null. Shown by the hub's empty state. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _localSongs = MutableStateFlow<List<SongModel>>(emptyList())
    val localSongs: StateFlow<List<SongModel>> = _localSongs.asStateFlow()

    private val _dominantColor = MutableStateFlow<Color?>(null)
    val dominantColor: StateFlow<Color?> = _dominantColor.asStateFlow()

    val mediaState = globalMediaController.mediaState
        .stateIn(viewModelScope, SharingStarted.Lazily, globalMediaController.mediaState.value)

    init {
        globalMediaController.startListening()
        localMediaController.start()
        loadLocalSongs()
        
        viewModelScope.launch {
            mediaState.collect { state ->
                extractDominantColor(state.albumArt)
            }
        }
    }

    private fun loadLocalSongs() {
        viewModelScope.launch {
            try {
                _localSongs.value = musicRepository.getLocalSongs()
                _errorMessage.value = null
            } catch (e: Exception) {
                ZuneLog.e("MusicHubViewModel", "loadLocalSongs failed", e)
                _errorMessage.value = e.toUserMessage(getApplication())
            }
        }
    }

    fun playLocalSong(index: Int) {
        val songs = _localSongs.value
        if (songs.isEmpty() || index !in songs.indices) return

        val mediaItems = songs.map { song ->
            androidx.media3.common.MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri)
                        .build()
                )
                .build()
        }

        localMediaController.playMediaItems(mediaItems, index)
    }

    private fun extractDominantColor(bitmap: Bitmap?) {
        if (bitmap == null) {
            _dominantColor.value = null
            return
        }
        
        Palette.from(bitmap).generate { palette ->
            // Zune HD prioritized vibrant accent colors.
            // We fall back to dominant if vibrant is unavailable.
            val colorInt = palette?.vibrantSwatch?.rgb 
                ?: palette?.lightVibrantSwatch?.rgb 
                ?: palette?.dominantSwatch?.rgb 
                ?: palette?.mutedSwatch?.rgb

            if (colorInt != null) {
                val hsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(colorInt, hsl)
                
                // Ensure lightness is at least 0.5 for readability on dark backgrounds
                if (hsl[2] < 0.5f) {
                    hsl[2] = 0.5f
                }
                
                val adjustedColorInt = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                _dominantColor.value = Color(adjustedColorInt)
            } else {
                _dominantColor.value = null
            }
        }
    }

    override fun onCleared() {
        globalMediaController.stopListening()
        localMediaController.stop()
        super.onCleared()
    }
}
