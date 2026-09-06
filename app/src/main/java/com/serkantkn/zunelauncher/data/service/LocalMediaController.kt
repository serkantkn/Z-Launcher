package com.serkantkn.zunelauncher.data.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalMediaController(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun start() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            _isReady.value = true
        }, MoreExecutors.directExecutor())
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int) {
        controller?.let {
            it.setMediaItems(items, startIndex, 0L)
            it.prepare()
            it.play()
        }
    }

    fun stop() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
            controllerFuture = null
            controller = null
            _isReady.value = false
        }
    }
}
