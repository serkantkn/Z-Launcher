package com.serkantkn.zunelauncher.data.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One track as the hub's own player sees it. */
data class QueueEntry(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri?
)

/**
 * Everything the hub needs to know about its own playback.
 *
 * [hasQueue] is what tells the hub whether to listen to this player at all: an empty player has
 * nothing to say, and the Now Playing page should then fall back to whatever else on the phone is
 * making noise.
 */
data class LocalPlaybackState(
    val hasQueue: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: Uri? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val shuffleEnabled: Boolean = false,
    /** One of [Player.REPEAT_MODE_OFF], `REPEAT_MODE_ONE`, `REPEAT_MODE_ALL`. */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = 0
)

/**
 * The launcher's own music player, and the one thing that knows what it is doing.
 *
 * The hub used to read playback only through [ThirdPartyMediaController], which asks the system
 * for other apps' media sessions and needs notification access to do it. That meant the launcher
 * could be playing a song through this very class and still show "no media", and the transport
 * buttons would be pressing something else. This now reports its own state directly — position,
 * queue, shuffle and all — and the hub prefers it whenever there is a queue.
 *
 * Position is polled rather than observed: a player has no "position changed" callback, because
 * the position changes continuously. Half a second is often enough for a progress line and rare
 * enough to cost nothing.
 */
class LocalMediaController(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _state = MutableStateFlow(LocalPlaybackState())
    val state: StateFlow<LocalPlaybackState> = _state.asStateFlow()

    /** Called whenever the queue or position moves, so it can be remembered across restarts. */
    var onPlaybackMoved: ((mediaIds: List<String>, index: Int, positionMillis: Long) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED
                )
            ) {
                remember()
            }
        }
    }

    fun start() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }
                .onSuccess { built ->
                    controller = built
                    built.addListener(listener)
                    _isReady.value = true
                    publish()
                    startTicking()
                }
                .onFailure { ZuneLog.e(TAG, "the player would not connect", it) }
        }, MoreExecutors.directExecutor())
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int) {
        val player = controller ?: return
        if (items.isEmpty()) return
        player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        player.prepare()
        player.play()
    }

    /**
     * Loads a queue without starting it.
     *
     * This is how a remembered queue comes back after the launcher has been closed: everything is
     * where it was, paused, waiting to be told to carry on.
     */
    fun restoreQueue(items: List<MediaItem>, index: Int, positionMillis: Long) {
        val player = controller ?: return
        if (items.isEmpty()) return
        player.setMediaItems(items, index.coerceIn(0, items.lastIndex), positionMillis.coerceAtLeast(0L))
        player.prepare()
        player.pause()
        publish()
    }

    fun playPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipToNext() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    /**
     * Back a track — or back to the beginning of this one.
     *
     * Every music player ever made does this: pressing back a few seconds in restarts the song,
     * and only pressing it again goes to the one before. Jumping straight back would make it
     * impossible to hear a song from the start.
     */
    fun skipToPrevious() {
        val player = controller ?: return
        if (player.currentPosition > RESTART_THRESHOLD_MS || !player.hasPreviousMediaItem()) {
            player.seekTo(0L)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMillis: Long) {
        controller?.seekTo(positionMillis.coerceAtLeast(0L))
        publish()
        remember()
    }

    fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    /** Off → all → one → off, the order every player cycles in. */
    fun cycleRepeatMode() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Jumps to a track in the queue, by its place in it. */
    fun playQueueIndex(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0L)
        player.play()
    }

    /** Puts a track straight after the one playing. */
    fun playNext(item: MediaItem) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            playMediaItems(listOf(item), 0)
        } else {
            player.addMediaItem((player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount), item)
        }
    }

    /** Puts tracks at the end of the queue. */
    fun addToQueue(items: List<MediaItem>) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) playMediaItems(items, 0) else player.addMediaItems(items)
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val player = controller ?: return
        val count = player.mediaItemCount
        if (from in 0 until count && to in 0 until count && from != to) player.moveMediaItem(from, to)
    }

    fun clearQueue() {
        controller?.clearMediaItems()
    }

    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                if (controller?.isPlaying == true) publish()
                delay(TICK_MS)
            }
        }
    }

    private fun publish() {
        val player = controller
        if (player == null || player.mediaItemCount == 0) {
            _state.value = LocalPlaybackState()
            return
        }
        val metadata = player.mediaMetadata
        _state.value = LocalPlaybackState(
            hasQueue = true,
            isPlaying = player.isPlaying,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            artworkUri = metadata.artworkUri,
            positionMillis = player.currentPosition.coerceAtLeast(0L),
            // A player that has not worked out the length yet reports a special "unset" value;
            // showing that as a number would put a progress line at some absurd width.
            durationMillis = player.duration.takeIf { it > 0L } ?: 0L,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = (0 until player.mediaItemCount).map { index ->
                val item = player.getMediaItemAt(index)
                QueueEntry(
                    mediaId = item.mediaId,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                    artworkUri = item.mediaMetadata.artworkUri
                )
            },
            currentIndex = player.currentMediaItemIndex
        )
    }

    private fun remember() {
        val player = controller ?: return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        onPlaybackMoved?.invoke(ids, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0L))
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _isReady.value = false
        scope.cancel()
    }

    private companion object {
        const val TAG = "LocalMediaController"
        const val TICK_MS = 500L
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
