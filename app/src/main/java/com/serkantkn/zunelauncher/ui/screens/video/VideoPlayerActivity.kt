package com.serkantkn.zunelauncher.ui.screens.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.repository.CloudStream
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import com.serkantkn.zunelauncher.util.AppLocale
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The launcher's own video player.
 *
 * A screen of black with the picture in the middle and, at a tap, the Metro controls over it: the
 * name at the top, three ringed buttons in the middle, and a thin line in the accent along the
 * bottom to find a place with. Two taps at either side jump ten seconds; the controls step back
 * out of the way on their own after a few seconds of playing.
 *
 * Left while playing, it carries on in a small window in the corner (picture in picture) with its
 * own three buttons. Each video is remembered where it was left, and starts from there again.
 *
 * A video on a drive is played as it arrives, with the account's token on each request; a token
 * that runs out mid-film is fetched again once, and the film goes on from where it stopped.
 *
 * The player takes the audio focus, so music that was playing pauses for it. It keeps no media
 * session: the music hub would take one for a music app of somebody else's.
 */
@OptIn(UnstableApi::class)
class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    private lateinit var resume: VideoResumeStore

    // ── What the screen shows ──
    private var title by mutableStateOf("")
    private var isPlaying by mutableStateOf(false)
    private var isBuffering by mutableStateOf(true)
    private var positionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)
    private var errorText by mutableStateOf<String?>(null)
    private var isInPip by mutableStateOf(false)
    private var resumedFromMs by mutableLongStateOf(0L)

    // ── What is playing ──
    /** The video's own uri, or null for one streamed off a drive. */
    private var sourceUri: Uri? = null
    private var sourceMime: String? = null
    private var cloudAccount: CloudAccount? = null
    private var cloudId: String? = null
    private var resumeKey: String? = null
    private var tokenRetried = false
    /** Set once the person has turned the screen themselves; the video's shape no longer decides. */
    private var orientationChosen = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(EXTRA_PIP_ACTION)) {
                PIP_PLAY_PAUSE -> togglePlay()
                PIP_BACK -> seekBy(-SEEK_STEP_MS)
                PIP_FORWARD -> seekBy(SEEK_STEP_MS)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resume = VideoResumeStore(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(listener)

        ContextCompat.registerReceiver(
            this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        load(intent)

        setContent {
            val settings = appContainer.settingsDataStore
            val accent by settings.accentColor.collectAsState(initial = com.serkantkn.zunelauncher.data.model.AccentColor.MAGENTA)
            val dynamic by settings.dynamicThemeColor.collectAsState(initial = null)
            val custom by settings.customThemeColor.collectAsState(initial = null)
            val fontScale by settings.fontScale.collectAsState(initial = 1.0f)
            val animations by settings.animationsEnabled.collectAsState(initial = true)
            // Always dark: a film is watched on black, whatever the launcher's own theme.
            ZuneLauncherTheme(
                themeMode = ThemeMode.DARK,
                accentColor = accent,
                dynamicThemeColor = dynamic,
                customThemeColor = custom,
                fontScale = fontScale,
                animationsEnabled = animations
            ) {
                VideoPlayerScreen(
                    player = player,
                    title = title,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    errorText = errorText,
                    isInPip = isInPip,
                    resumedFromMs = resumedFromMs,
                    canOpenElsewhere = sourceUri != null,
                    canPip = canPip(),
                    onTogglePlay = ::togglePlay,
                    onSeekTo = { player.seekTo(it); positionMs = it },
                    onSeekBy = ::seekBy,
                    onRotate = ::rotate,
                    onPip = { enterPip() },
                    onControlsShown = ::showSystemBars,
                    onClose = { finish() },
                    onOpenElsewhere = ::openElsewhere
                )
            }
        }

        // The position line follows the film; the player does not report it by itself.
        lifecycleScope.launch {
            while (true) {
                positionMs = player.currentPosition
                durationMs = player.duration.coerceAtLeast(0L)
                delay(if (player.isPlaying) 250L else 750L)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        rememberPlace()
        load(intent)
    }

    /** Takes in what to play - a uri, or a file on a drive - and starts it. */
    private fun load(intent: Intent) {
        errorText = null
        tokenRetried = false
        orientationChosen = false
        resumedFromMs = 0L
        val accountJson = intent.getStringExtra(VideoPlayer.EXTRA_CLOUD_ACCOUNT)
        val id = intent.getStringExtra(VideoPlayer.EXTRA_CLOUD_ID)
        if (accountJson != null && id != null) {
            sourceUri = null
            sourceMime = null
            cloudAccount = runCatching { CloudAccount.fromJson(JSONObject(accountJson)) }.getOrNull()
            cloudId = id
            title = withoutExtension(intent.getStringExtra(VideoPlayer.EXTRA_TITLE).orEmpty())
            resumeKey = "cloud:$id"
            startCloud()
        } else {
            val uri = intent.data
            if (uri == null) {
                finish()
                return
            }
            sourceUri = uri
            sourceMime = intent.type
            cloudAccount = null
            cloudId = null
            title = withoutExtension(intent.getStringExtra(VideoPlayer.EXTRA_TITLE) ?: nameOf(uri))
            resumeKey = uri.toString()
            play(MediaItem.fromUri(uri))
        }
    }

    private fun startCloud(atMs: Long? = null) {
        val account = cloudAccount
        val id = cloudId
        if (account == null || id == null) {
            errorText = localizedString(R.string.video_cant_play)
            return
        }
        isBuffering = true
        lifecycleScope.launch {
            try {
                val stream: CloudStream = appContainer.cloudStorageRepository.streamOf(account, id)
                httpFactory.setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${stream.token}"))
                play(MediaItem.fromUri(stream.url), atMs)
            } catch (e: CloudException) {
                ZuneLog.w(TAG, "no stream for the video", e)
                errorText = localizedString(e.messageRes)
            } catch (e: Exception) {
                ZuneLog.w(TAG, "no stream for the video", e)
                errorText = localizedString(R.string.video_cant_play)
            }
        }
    }

    private fun play(item: MediaItem, atMs: Long? = null) {
        val from = atMs ?: resumeKey?.let { resume.positionOf(it) } ?: 0L
        if (atMs == null && from > 0L) resumedFromMs = from
        player.setMediaItem(item, from)
        player.prepare()
        player.play()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            // The screen stays awake for a film, and only while one is playing.
            if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            updatePipParams()
        }

        override fun onPlaybackStateChanged(state: Int) {
            isBuffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE && errorText == null
            if (state == Player.STATE_ENDED) {
                resumeKey?.let { resume.remember(it, player.duration, player.duration) }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updatePipParams()
            turnForVideo()
        }

        override fun onPlayerError(error: PlaybackException) {
            // A drive's token lasts an hour; a longer film asks for a fresh one once and goes on.
            val http = error.cause as? HttpDataSource.InvalidResponseCodeException
            if (cloudId != null && http?.responseCode == 401 && !tokenRetried) {
                tokenRetried = true
                startCloud(atMs = player.currentPosition)
                return
            }
            ZuneLog.w(TAG, "the video would not play", error)
            isBuffering = false
            errorText = localizedString(R.string.video_cant_play)
        }
    }

    /**
     * On a phone a wide film turns the screen on its side by itself; a tablet is held however
     * its owner likes. Never while in the small window: asking the screen to turn then moved the
     * window off the edge of the screen.
     */
    private fun turnForVideo() {
        val size = player.videoSize
        if (isInPip || orientationChosen || resources.configuration.smallestScreenWidthDp >= 600) return
        if (size.width <= 0 || size.height <= 0) return
        requestedOrientation = if (size.width * size.pixelWidthHeightRatio > size.height) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    private fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.play()
        }
    }

    private fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        positionMs = target
    }

    /** Turns the screen to the other way up, and keeps it there for this video. */
    private fun rotate() {
        orientationChosen = true
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun showSystemBars(show: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** For a video that will not play here: the same video, offered to the other apps. */
    private fun openElsewhere() {
        val uri = sourceUri ?: return
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, sourceMime ?: "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = android.content.ClipData.newRawUri(null, uri)
        runCatching {
            startActivity(Intent.createChooser(view, localizedString(R.string.files_open_with)))
            finish()
        }.onFailure { ZuneLog.w(TAG, "nothing else plays it either", it) }
    }

    /** "tatil.mp4" is shown as "tatil": the type of the file says nothing to someone watching it. */
    private fun withoutExtension(name: String): String =
        if (Regex("\\.[A-Za-z0-9]{2,4}$").containsMatchIn(name)) name.substringBeforeLast('.') else name

    private fun nameOf(uri: Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    // ── Picture in picture ──

    private fun canPip(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): PictureInPictureParams {
        val size = player.videoSize
        val builder = PictureInPictureParams.Builder()
        if (size.width > 0 && size.height > 0) {
            // The system takes ratios between 1:2.39 and 2.39:1 only.
            val width = (size.width * size.pixelWidthHeightRatio).toInt().coerceAtLeast(1)
            val ratio = (width.toFloat() / size.height).coerceIn(1f / 2.39f, 2.39f)
            builder.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        }
        builder.setActions(
            listOf(
                pipAction(PIP_BACK, R.drawable.ic_video_back10, R.string.video_back10, 1),
                if (player.isPlaying) {
                    pipAction(PIP_PLAY_PAUSE, R.drawable.ic_video_pause, R.string.video_pause, 2)
                } else {
                    pipAction(PIP_PLAY_PAUSE, R.drawable.ic_video_play, R.string.video_play, 2)
                },
                pipAction(PIP_FORWARD, R.drawable.ic_video_forward10, R.string.video_forward10, 3)
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Leaving while it plays takes it into the corner by itself.
            builder.setAutoEnterEnabled(player.isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun pipAction(action: String, icon: Int, label: Int, requestCode: Int): RemoteAction {
        val intent = Intent(ACTION_PIP).setPackage(packageName).putExtra(EXTRA_PIP_ACTION, action)
        val pending = PendingIntent.getBroadcast(
            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = localizedString(label)
        return RemoteAction(Icon.createWithResource(this, icon), text, text, pending)
    }

    private fun updatePipParams() {
        if (canPip()) runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun enterPip() {
        if (canPip()) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Before Android 12 the corner has to be asked for at the moment of leaving.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        smallWindow = if (isInPictureInPictureMode) java.lang.ref.WeakReference(this) else null
        // The small window swiped away or closed: the activity is left stopped behind it, and
        // there is nothing left to watch.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            rememberPlace()
            player.pause()
            finishAndRemoveTask()
        } else if (!isInPictureInPictureMode) {
            // Back to the full screen: now the screen may turn for the film.
            turnForVideo()
        }
    }

    override fun onStop() {
        super.onStop()
        rememberPlace()
        if (!isInPip) player.pause()
    }

    override fun onDestroy() {
        rememberPlace()
        runCatching { unregisterReceiver(pipReceiver) }
        player.removeListener(listener)
        player.release()
        super.onDestroy()
    }

    private fun rememberPlace() {
        if (!::player.isInitialized) return
        val key = resumeKey ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        resume.remember(key, player.currentPosition, player.duration)
    }

    companion object {
        /** The player while it is in the small window, if it is. */
        @Volatile
        private var smallWindow: java.lang.ref.WeakReference<VideoPlayerActivity>? = null

        /**
         * Closes the small window, if a video is playing in one, before another video is opened:
         * the new one is wanted on the whole screen, and a video handed to the player in the
         * corner stayed in the corner.
         */
        internal fun closeSmallWindow() {
            smallWindow?.get()?.let { player ->
                player.rememberPlace()
                player.finishAndRemoveTask()
            }
            smallWindow = null
        }

        private const val TAG = "VideoPlayer"
        private const val SEEK_STEP_MS = 10_000L
        private const val ACTION_PIP = "com.serkantkn.zunelauncher.VIDEO_PIP"
        private const val EXTRA_PIP_ACTION = "pip_action"
        private const val PIP_PLAY_PAUSE = "play_pause"
        private const val PIP_BACK = "back"
        private const val PIP_FORWARD = "forward"
    }
}
