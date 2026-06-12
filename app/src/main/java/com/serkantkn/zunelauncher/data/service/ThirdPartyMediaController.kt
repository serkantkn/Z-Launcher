package com.serkantkn.zunelauncher.data.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveMediaState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val albumArt: Bitmap? = null,
    val controller: MediaController? = null
)

class ThirdPartyMediaController(private val context: Context) {

    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componentName = ComponentName(context, SocialNotificationListener::class.java)

    private val _mediaState = MutableStateFlow(ActiveMediaState())
    val mediaState: StateFlow<ActiveMediaState> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateState()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateState()
        }
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers)
    }

    fun startListening() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateActiveController(controllers)
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
        } catch (e: SecurityException) {
            // Notification access not granted
            e.printStackTrace()
        }
    }

    fun stopListening() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
            activeController?.unregisterCallback(callback)
            activeController = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        val newController = controllers?.firstOrNull { 
            it.playbackState?.state == PlaybackState.STATE_PLAYING || 
            it.playbackState?.state == PlaybackState.STATE_BUFFERING 
        } ?: controllers?.firstOrNull()

        if (activeController != newController) {
            activeController?.unregisterCallback(callback)
            activeController = newController
            activeController?.registerCallback(callback)
        }
        updateState()
    }

    private fun updateState() {
        val controller = activeController
        if (controller == null) {
            _mediaState.value = ActiveMediaState()
            return
        }

        val playbackState = controller.playbackState
        val metadata = controller.metadata

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING || 
                        playbackState?.state == PlaybackState.STATE_BUFFERING ||
                        playbackState?.state == PlaybackState.STATE_FAST_FORWARDING ||
                        playbackState?.state == PlaybackState.STATE_REWINDING

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: 
                       metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        _mediaState.value = ActiveMediaState(
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            albumArt = albumArt,
            controller = controller
        )
    }

    fun playPause() {
        activeController?.let {
            val state = it.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                it.transportControls.pause()
            } else {
                it.transportControls.play()
            }
        }
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }
}
