package com.serkantkn.zunelauncher.ui.screens.video

import com.serkantkn.zunelauncher.util.Pro
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
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.repository.CloudStream
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import com.serkantkn.zunelauncher.util.AppLocale
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * The launcher's own video player.
 *
 * A screen of black with the picture in the middle and, at a tap, the Metro controls over it: the
 * name at the top, ringed buttons in the middle, and a thin line in the accent along the bottom to
 * find a place with. Two taps at either side jump ten seconds, more taps further; the controls step
 * back out of the way on their own after a few seconds of playing. Two fingers spread the picture
 * to fill the screen and pinch it back.
 *
 * A video opened from a folder or an album has the others beside it queued after it, in the order
 * of their names, so the next episode follows. Subtitles kept beside a film under its name are
 * picked up, one can be picked by hand, and a film with several languages can be heard in any.
 *
 * Left while playing, it carries on in a small window in the corner (picture in picture) with its
 * own three buttons. Each video is remembered where it was left, and starts from there again.
 *
 * A video on a drive is played as it arrives, with the account's token on each request to the
 * drive and to nowhere else; a token that runs out mid-film is fetched again once, and the film
 * goes on from where it stopped.
 *
 * The player takes the audio focus, so music that was playing pauses for it, and keeps a media
 * session of its own so the buttons on headphones and keyboards reach it. The hubs that show what
 * is playing leave that session out: a film is not the song on the music tile.
 */
@OptIn(UnstableApi::class)
class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    private lateinit var resume: VideoResumeStore
    private lateinit var prefs: VideoPrefs

    // ── What the screen shows ──
    private var title by mutableStateOf("")
    private var isPlaying by mutableStateOf(false)
    private var isBuffering by mutableStateOf(true)
    private var positionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)
    private var errorText by mutableStateOf<String?>(null)
    private var isInPip by mutableStateOf(false)
    private var resumedFromMs by mutableLongStateOf(0L)
    private var options by mutableStateOf(VideoOptions())
    private var hasPrevious by mutableStateOf(false)
    private var hasNext by mutableStateOf(false)
    /** Bumped when another video of the queue comes on, so the screen shows its name. */
    private var itemChanges by mutableIntStateOf(0)
    private var speed by mutableFloatStateOf(1f)
    /** The picture's width over its height, once the player knows it; 0 until then. */
    private var videoAspect by mutableFloatStateOf(0f)

    // ── What is playing ──
    private var cloudAccount: CloudAccount? = null
    private var cloudId: String? = null
    /** The drive's host and token, sent with requests to that host only. */
    @Volatile
    private var cloudAuth: Pair<String, String>? = null
    private var tokenRetried = false
    /** Set once the person has turned the screen themselves; the video's shape no longer decides. */
    private var orientationChosen = false
    /** A subtitle just picked by hand, chosen as soon as the player has read it. */
    private var pendingSubtitleLabel: String? = null
    /** The video last seen playing and where, for remembering it once another has come on. */
    private var lastKey: String? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(EXTRA_PIP_ACTION)) {
                PIP_PLAY_PAUSE -> togglePlay()
                PIP_BACK -> seekBy(-SEEK_STEP_MS)
                PIP_FORWARD -> seekBy(SEEK_STEP_MS)
            }
        }
    }

    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addSubtitle(uri)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resume = VideoResumeStore(this)
        prefs = VideoPrefs(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        // The volume keys turn the film up and down even while it is paused, not the ringer.
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        // The drive's token goes with requests to the drive, and to no other address a video
        // handed over later might come from.
        val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, httpFactory)) { spec ->
            val auth = cloudAuth
            if (auth != null && spec.uri.host == auth.first) {
                spec.withAdditionalHeaders(mapOf("Authorization" to "Bearer ${auth.second}"))
            } else {
                spec
            }
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
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
        applyEndAction(prefs.endAction)
        applySubtitlePreference()
        options = options.copy(fill = prefs.fill, endAction = prefs.endAction)

        session = runCatching { MediaSession.Builder(this, player).setId(SESSION_ID).build() }
            .onFailure { ZuneLog.w(TAG, "no media session for the player", it) }
            .getOrNull()

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
                    canOpenElsewhere = currentUri() != null,
                    canPip = canPip(),
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    itemChanges = itemChanges,
                    videoAspect = videoAspect,
                    options = options.copy(speed = speed),
                    optionsHandler = optionsHandler,
                    onTogglePlay = ::togglePlay,
                    onSeekTo = { player.seekTo(it); positionMs = it },
                    onSeekBy = ::seekBy,
                    onPrevious = ::playPrevious,
                    onNext = ::playNext,
                    onStartOver = ::startOver,
                    onRotate = ::rotate,
                    onPip = { enterPip() },
                    onControlsShown = ::showSystemBars,
                    onClose = { finish() },
                    onOpenElsewhere = ::openElsewhere
                )
            }
        }

        // The position line follows the film while it can be seen; the player does not report
        // it by itself, and nobody is looking at it from behind another app.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    positionMs = player.currentPosition
                    durationMs = player.duration.coerceAtLeast(0L)
                    player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let { key ->
                        lastKey = key
                        lastPositionMs = positionMs
                        lastDurationMs = durationMs
                    }
                    delay(if (player.isPlaying) 250L else 750L)
                }
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
        val accountJson = intent.getStringExtra(VideoPlayer.EXTRA_CLOUD_ACCOUNT)
        val id = intent.getStringExtra(VideoPlayer.EXTRA_CLOUD_ID)
        val uri = intent.data
        if ((accountJson == null || id == null) && uri == null) {
            // Nothing to play: an empty start is closed, and one reaching a player already
            // showing a film leaves it as it is.
            if (player.mediaItemCount == 0) finish()
            return
        }
        errorText = null
        tokenRetried = false
        orientationChosen = false
        resumedFromMs = 0L
        pendingSubtitleLabel = null
        speed = 1f
        player.setPlaybackSpeed(1f)
        // A video handed over while another was on: its name is shown, as when the player opens.
        itemChanges++
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        if (accountJson != null && id != null) {
            cloudAccount = runCatching { CloudAccount.fromJson(JSONObject(accountJson)) }.getOrNull()
            cloudId = id
            title = withoutExtension(intent.getStringExtra(VideoPlayer.EXTRA_TITLE).orEmpty())
            startCloud()
            return
        }
        cloudAccount = null
        cloudId = null
        cloudAuth = null
        val opened = QueuedVideo(
            uri = uri!!,
            title = intent.getStringExtra(VideoPlayer.EXTRA_TITLE) ?: nameOf(uri),
            mimeType = intent.type,
            path = intent.getStringExtra(VideoPlayer.EXTRA_PATH)
        )
        val withFolder = intent.getBooleanExtra(VideoPlayer.EXTRA_QUEUE_FOLDER, false)
        title = withoutExtension(opened.title)
        isBuffering = true
        lifecycleScope.launch {
            // The videos beside it, and the subtitles beside each; a folder or an album is read
            // in a moment, off the main thread.
            val items = withContext(Dispatchers.IO) { queueAround(opened, withFolder) }
            val start = items.indexOfFirst { it.mediaId == VideoResumeStore.keyOf(opened.uri) }.coerceAtLeast(0)
            play(items, start)
        }
    }

    /**
     * The video opened and the ones beside it, as the player's own list. The ones beside it, and
     * the subtitles found next to them, are Pro's: the free build plays the one opened.
     */
    private fun queueAround(opened: QueuedVideo, withFolder: Boolean): List<MediaItem> {
        if (!Pro.unlocked) return listOf(mediaItemOf(opened))
        val path = opened.path
        val around = when {
            withFolder && path != null -> VideoQueue.ofFolder(this, File(path))
            else -> VideoQueue.ofMediaStore(this, opened.uri)
        }
        val openedKey = VideoResumeStore.keyOf(opened.uri)
        val openedPath = path ?: around.firstOrNull { VideoResumeStore.keyOf(it.uri) == openedKey }?.path
        val queue = if (around.none { VideoResumeStore.keyOf(it.uri) == openedKey || (path != null && it.path == path) }) {
            listOf(opened)
        } else {
            // The one opened keeps the address it came with: it was granted that one.
            around.map { if (VideoResumeStore.keyOf(it.uri) == openedKey || (path != null && it.path == path)) opened.copy(path = openedPath) else it }
        }
        val folderNames = HashMap<String, List<String>>()
        return queue.map { video ->
            val subtitles = video.path?.let { videoPath ->
                val file = File(videoPath)
                val folder = file.parentFile ?: return@let emptyList()
                val names = folderNames.getOrPut(folder.absolutePath) {
                    runCatching { folder.list()?.toList() }.getOrNull().orEmpty()
                }
                sidecarSubtitles(file.name, names).mapNotNull { name ->
                    val subtitle = File(folder, name)
                    if (!subtitle.canRead()) return@mapNotNull null
                    // Named by its language when its name gives one ("film.tr.srt" is "türkçe"),
                    // otherwise by its name.
                    val language = subtitleLanguage(file.name, name)
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitle))
                        .setMimeType(subtitleMime(name))
                        .setLanguage(language)
                        .setLabel(if (language == null) name.substringBeforeLast('.') else null)
                        .build()
                }
            }.orEmpty()
            mediaItemOf(video, subtitles)
        }
    }

    private fun mediaItemOf(video: QueuedVideo, subtitles: List<MediaItem.SubtitleConfiguration> = emptyList()): MediaItem =
        MediaItem.Builder()
            .setUri(video.uri)
            .setMediaId(VideoResumeStore.keyOf(video.uri))
            .apply { video.mimeType?.takeIf { it.isNotBlank() && it != "video/*" }?.let { setMimeType(it) } }
            .setSubtitleConfigurations(subtitles)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(withoutExtension(video.title)).build())
            .build()

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
                val streamUri = Uri.parse(stream.url)
                cloudAuth = streamUri.host?.let { it to stream.token }
                val item = MediaItem.Builder()
                    .setUri(streamUri)
                    .setMediaId("cloud:$id")
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                    .build()
                play(listOf(item), 0, atMs)
            } catch (e: CloudException) {
                ZuneLog.w(TAG, "no stream for the video", e)
                errorText = localizedString(e.messageRes)
            } catch (e: Exception) {
                ZuneLog.w(TAG, "no stream for the video", e)
                errorText = localizedString(R.string.video_cant_play)
            }
        }
    }

    private fun play(items: List<MediaItem>, index: Int, atMs: Long? = null) {
        val key = items.getOrNull(index)?.mediaId
        val from = atMs ?: key?.let { resume.positionOf(it) } ?: 0L
        if (atMs == null && from > 0L) resumedFromMs = from
        lastKey = null
        player.setMediaItems(items, index, from)
        player.prepare()
        player.play()
        updateQueueState()
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
                player.currentMediaItem?.mediaId?.let { resume.remember(it, player.duration, player.duration) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                updateQueueState()
                return
            }
            // The one before: finished if it ran to its end, otherwise left where it was.
            lastKey?.let { key ->
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) resume.remember(key, lastDurationMs, lastDurationMs)
                else if (key != mediaItem?.mediaId) resume.remember(key, lastPositionMs, lastDurationMs)
            }
            lastKey = null
            title = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
            resumedFromMs = 0L
            pendingSubtitleLabel = null
            errorText = null
            // The next episode starts where it was left, as it would if it were opened.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                val from = mediaItem?.mediaId?.let { resume.positionOf(it) } ?: 0L
                if (from > 0L) {
                    player.seekTo(from)
                    resumedFromMs = from
                }
            }
            itemChanges++
            updateQueueState()
        }

        override fun onTracksChanged(tracks: Tracks) {
            pendingSubtitleLabel?.let { label ->
                val picked = tracks.groups.firstOrNull { group ->
                    group.type == C.TRACK_TYPE_TEXT && (0 until group.length).any { group.getTrackFormat(it).label == label }
                }
                if (picked != null) {
                    pendingSubtitleLabel = null
                    val index = (0 until picked.length).first { picked.getTrackFormat(it).label == label }
                    selectText(TrackChoice(label, false, picked, index))
                    return
                }
            }
            describeTracks(tracks)
        }

        override fun onTrackSelectionParametersChanged(parameters: androidx.media3.common.TrackSelectionParameters) {
            describeTracks(player.currentTracks)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoAspect = if (videoSize.width > 0 && videoSize.height > 0) {
                videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            } else {
                0f
            }
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

    // ── The queue ──

    private fun updateQueueState() {
        hasPrevious = player.mediaItemCount > 1 && player.currentMediaItemIndex > 0
        hasNext = player.mediaItemCount > 1 && player.currentMediaItemIndex < player.mediaItemCount - 1
        options = options.copy(hasQueue = player.mediaItemCount > 1)
    }

    /** Back to the start of this video, or - right at its start - to the one before. */
    private fun playPrevious() {
        rememberPlace()
        player.seekToPrevious()
    }

    private fun playNext() {
        if (!player.hasNextMediaItem()) return
        rememberPlace()
        player.seekToNextMediaItem()
    }

    /** A video taken up where it was left, started again from its beginning instead. */
    private fun startOver() {
        resumedFromMs = 0L
        player.seekTo(0L)
        positionMs = 0L
        player.currentMediaItem?.mediaId?.let { resume.remember(it, 0L, player.duration) }
    }

    // ── Sound, subtitles and the rest of the options ──

    private val optionsHandler = object : VideoOptionsHandler {
        override fun onAudio(choice: TrackChoice) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.index))
                .build()
        }

        override fun onText(choice: TrackChoice?) {
            if (choice == null) {
                prefs.subtitles = false
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            } else {
                selectText(choice)
            }
        }

        override fun onAddSubtitle() {
            runCatching { pickSubtitle.launch(arrayOf("*/*")) }
                .onFailure { ZuneLog.w(TAG, "nothing to pick a subtitle with", it) }
        }

        override fun onSpeed(value: Float) {
            speed = value
            player.setPlaybackSpeed(value)
        }

        override fun onFill(fill: Boolean) {
            prefs.fill = fill
            options = options.copy(fill = fill)
        }

        override fun onEndAction(action: VideoEndAction) {
            prefs.endAction = action
            applyEndAction(action)
            options = options.copy(endAction = action)
        }
    }

    private fun selectText(choice: TrackChoice) {
        prefs.subtitles = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.index))
            .build()
    }

    /**
     * Subtitles come on by themselves in the launcher's language, or in none named, unless they
     * were turned off; then they stay off until turned on again.
     */
    private fun applySubtitlePreference() {
        val language = resources.configuration.locales[0]?.language
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(language)
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !prefs.subtitles)
            .build()
    }

    private fun applyEndAction(action: VideoEndAction) {
        player.repeatMode = if (action == VideoEndAction.REPEAT) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.pauseAtEndOfMediaItems = action == VideoEndAction.STOP
    }

    /** A subtitle file picked by hand, added to the film playing and shown at once. */
    private fun addSubtitle(uri: Uri) {
        val item = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        val at = player.currentPosition
        val name = nameOf(uri)
        val label = name.substringBeforeLast('.').ifBlank { localizedString(R.string.video_subtitle_file) }
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(subtitleMime(name))
            .setLabel(label)
            .build()
        val existing = item.localConfiguration?.subtitleConfigurations.orEmpty()
        val updated = item.buildUpon().setSubtitleConfigurations(existing + subtitle).build()
        pendingSubtitleLabel = label
        // The list is laid again rather than the one video replaced in it: the player takes a
        // replaced video at the same address for the same one and leaves the new subtitle out.
        val items = (0 until player.mediaItemCount).map { if (it == index) updated else player.getMediaItemAt(it) }
        lastKey = null
        player.setMediaItems(items, index, at)
        player.prepare()
    }

    private fun describeTracks(tracks: Tracks) {
        val parameters = player.trackSelectionParameters
        options = options.copy(
            audio = choicesOf(tracks, C.TRACK_TYPE_AUDIO),
            text = choicesOf(tracks, C.TRACK_TYPE_TEXT),
            textOff = parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
                tracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        )
    }

    private fun choicesOf(tracks: Tracks, type: Int): List<TrackChoice> {
        val choices = mutableListOf<TrackChoice>()
        tracks.groups.filter { it.type == type }.forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                choices += TrackChoice(trackLabel(group.getTrackFormat(i), choices.size + 1, type), group.isTrackSelected(i), group, i)
            }
        }
        return choices
    }

    /** "türkçe · 5.1", "english · commentary", or "track 2" for one that says nothing of itself. */
    private fun trackLabel(format: Format, number: Int, type: Int): String {
        val shownIn = resources.configuration.locales[0] ?: Locale.getDefault()
        val language = format.language
            ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED && it != "und" }
            ?.let { Locale.forLanguageTag(it).getDisplayLanguage(shownIn).lowercase(shownIn) }
            ?.takeIf { it.isNotBlank() }
        val name = format.label?.takeIf { it.isNotBlank() }
        val parts = buildList {
            if (name != null) add(name)
            if (language != null && (name == null || !name.contains(language, ignoreCase = true))) add(language)
            if (type == C.TRACK_TYPE_AUDIO) {
                when (format.channelCount) {
                    6 -> add("5.1")
                    8 -> add("7.1")
                }
            }
        }
        return parts.joinToString(" · ").ifBlank { localizedString(R.string.video_track_n, number) }
    }

    private fun subtitleMime(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    // ── The screen ──

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
            val duration = player.duration
            val atEnd = duration > 0 && player.currentPosition >= duration - END_SLACK_MS
            // Stopped at the end - of the list, or of a video that is not followed by the next -
            // a press plays it again from its start.
            if (player.playbackState == Player.STATE_ENDED || (player.pauseAtEndOfMediaItems && atEnd)) {
                player.seekTo(player.currentMediaItemIndex, 0L)
            }
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

    /** The video playing, if it is one on the device another app could be handed. */
    private fun currentUri(): Uri? =
        if (cloudId != null) null else player.currentMediaItem?.localConfiguration?.uri

    /** For a video that will not play here: the same video, offered to the other apps. */
    private fun openElsewhere() {
        val uri = currentUri() ?: return
        val mime = player.currentMediaItem?.localConfiguration?.mimeType
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime ?: "video/*")
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

    /** The small window is Pro's. */
    private fun canPip(): Boolean =
        Pro.unlocked && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

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
        session?.release()
        session = null
        player.removeListener(listener)
        player.release()
        super.onDestroy()
    }

    private fun rememberPlace() {
        if (!::player.isInitialized) return
        val key = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
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

        /**
         * The player's media session goes by this name, which ends the tag the system knows it
         * by; the hubs showing what is playing pass over a session with it.
         */
        const val SESSION_ID = "zune_video"

        private const val TAG = "VideoPlayer"
        private const val SEEK_STEP_MS = 10_000L
        private const val END_SLACK_MS = 500L
        private const val ACTION_PIP = "com.serkantkn.zunelauncher.VIDEO_PIP"
        private const val EXTRA_PIP_ACTION = "pip_action"
        private const val PIP_PLAY_PAUSE = "play_pause"
        private const val PIP_BACK = "back"
        private const val PIP_FORWARD = "forward"
    }
}
