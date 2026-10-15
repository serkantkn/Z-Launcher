package com.serkantkn.zunelauncher.data.service

import com.serkantkn.zunelauncher.util.ZuneLog
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
            ZuneLog.e("ThirdPartyMediaController", "startListening failed", e)
        }
    }

    fun stopListening() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
            activeController?.unregisterCallback(callback)
            activeController = null
        } catch (e: Exception) {
            ZuneLog.e("ThirdPartyMediaController", "stopListening failed", e)
        }
    }

    private fun updateActiveController(all: List<MediaController>?) {
        // The launcher's own video player keeps a session for the buttons on headphones; a film
        // is not what the music tile and the hub mean by what is playing.
        val controllers = all?.filterNot { isLauncherVideo(it) }
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

    private fun isLauncherVideo(controller: MediaController): Boolean =
        controller.packageName == context.packageName &&
            controller.tag?.endsWith(com.serkantkn.zunelauncher.ui.screens.video.VideoPlayerActivity.SESSION_ID) == true

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

    /**
     * Pauses the other app's session if it is playing. Returns whether there was one to pause:
     * the sleep timer has to say so when the music it was set for is not the launcher's to stop.
     */
    fun pauseIfPlaying(): Boolean {
        val controller = activeController ?: return false
        if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) return false
        return runCatching { controller.transportControls.pause() }.isSuccess
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }
}
