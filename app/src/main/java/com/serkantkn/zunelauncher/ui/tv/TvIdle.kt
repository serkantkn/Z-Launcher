package com.serkantkn.zunelauncher.ui.tv

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.service.ActiveMediaState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import kotlin.random.Random

/** What a TV's screen does when the remote is left alone: nothing, dims, or shows the TV's pictures. */
enum class TvSaver { OFF, DIM, PHOTOS }

/**
 * The remote left alone. Every key the launcher is given passes through [onKey]; while the
 * launcher is on the screen, [TvIdleScreen] watches how long it has been and puts up the
 * screensaver, or the music's own screen while something plays. The key that wakes it does
 * nothing else, as on the Xbox; the media and volume keys work through it.
 */
internal object TvIdle {
    enum class Shown { DIM, PHOTOS, MUSIC }

    @Volatile
    var lastInput = SystemClock.uptimeMillis()
        private set

    @Volatile
    var resumed = false
        private set

    /** Things that must not be covered - a slide show - while they last. */
    @Volatile
    var holds = 0
        private set

    private val _shown = MutableStateFlow<Shown?>(null)
    val shown: StateFlow<Shown?> = _shown.asStateFlow()
    private var swallowUp: Int? = null

    fun onResume() {
        resumed = true
        lastInput = SystemClock.uptimeMillis()
    }

    fun onPause() {
        resumed = false
        _shown.value = null
    }

    /** True if the key only woke the screen and is spent. */
    fun onKey(event: KeyEvent): Boolean {
        if (!TvDesign.onTv) return false
        lastInput = SystemClock.uptimeMillis()
        if (passesThrough(event.keyCode)) return false
        if (event.action == KeyEvent.ACTION_DOWN && _shown.value != null) {
            _shown.value = null
            swallowUp = event.keyCode
            return true
        }
        if (swallowUp == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) swallowUp = null
            return true
        }
        return false
    }

    fun show(what: Shown) {
        if (!resumed) return
        XboxGuide.hide()
        _shown.value = what
    }

    fun hide() {
        _shown.value = null
    }

    fun hold(on: Boolean) {
        holds = (holds + if (on) 1 else -1).coerceAtLeast(0)
        lastInput = SystemClock.uptimeMillis()
    }

    private fun passesThrough(code: Int) = KeyEvent.isMediaSessionKey(code) ||
        code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE
}

/** Keeps the screensaver away while [active]: a slide show is a screensaver of its own. */
@Composable
fun TvIdleHold(active: Boolean) {
    DisposableEffect(active) {
        if (active) TvIdle.hold(true)
        onDispose { if (active) TvIdle.hold(false) }
    }
}

/**
 * The TV's sleep timer: when it runs out the music stops and, with the accessibility service on,
 * the TV goes to sleep. Kept while the launcher lives, which on a TV is all the time.
 */
internal object TvSleep {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private val _endsAt = MutableStateFlow(0L)
    /** When it runs out, on the elapsed-time clock; zero when it is not set. */
    val endsAt: StateFlow<Long> = _endsAt.asStateFlow()
    private val _chosen = MutableStateFlow(0)
    /** The minutes it was set to. */
    val chosen: StateFlow<Int> = _chosen.asStateFlow()

    fun set(context: Context, minutes: Int) {
        pending?.let(handler::removeCallbacks)
        pending = null
        if (minutes <= 0) {
            _endsAt.value = 0L
            return
        }
        _chosen.value = minutes
        val app = context.applicationContext
        val run = Runnable { fire(app) }
        pending = run
        _endsAt.value = SystemClock.elapsedRealtime() + minutes * 60_000L
        handler.postDelayed(run, minutes * 60_000L)
    }

    fun minutesLeft(): Int {
        val end = _endsAt.value
        if (end == 0L) return 0
        return ((end - SystemClock.elapsedRealtime() + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
    }

    private fun fire(context: Context) {
        pending = null
        _endsAt.value = 0L
        val audio = context.getSystemService(AudioManager::class.java)
        runCatching {
            audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
        com.serkantkn.zunelauncher.data.service.ZuneKeyAccessibilityService.current
            ?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }
}

/**
 * Watches the remote and, when it has rested long enough, puts up what the settings ask: the music's
 * own screen while something plays, else the screen dimmed or the TV's pictures one after another.
 */
@Composable
fun TvIdleScreen(media: ActiveMediaState, modifier: Modifier = Modifier) {
    val song = rememberTvSong(media)
    val playing by rememberUpdatedState(song?.playing == true)
    val saver by TvDesign.saver.collectAsState()
    val minutes by TvDesign.saverMinutes.collectAsState()
    val visualizer by TvDesign.visualizer.collectAsState()
    val shown by TvIdle.shown.collectAsState()
    LaunchedEffect(saver, minutes, visualizer) {
        while (true) {
            delay(IDLE_CHECK_MILLIS)
            val idle = SystemClock.uptimeMillis() - TvIdle.lastInput
            if (!TvIdle.resumed || TvIdle.holds > 0) continue
            val now = TvIdle.shown.value
            val saverMode = TvSaver.entries.firstOrNull { it.name == saver } ?: TvSaver.DIM
            when {
                now == null && playing && visualizer && idle >= MUSIC_AFTER_MILLIS -> TvIdle.show(TvIdle.Shown.MUSIC)
                (now == null || now == TvIdle.Shown.MUSIC && !playing) && !playing && saverMode != TvSaver.OFF && idle >= minutes * 60_000L ->
                    TvIdle.show(if (saverMode == TvSaver.PHOTOS) TvIdle.Shown.PHOTOS else TvIdle.Shown.DIM)
            }
        }
    }
    // Kept for the fade out, after it is put away.
    var last by remember { mutableStateOf(TvIdle.Shown.DIM) }
    shown?.let { last = it }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(tween(FADE_IN_MILLIS)),
        exit = fadeOut(tween(FADE_OUT_MILLIS)),
        modifier = modifier
    ) {
        when (last) {
            TvIdle.Shown.DIM -> TvDimSaver()
            TvIdle.Shown.PHOTOS -> TvPhotoSaver()
            TvIdle.Shown.MUSIC -> song?.let { TvVisualizer(it) } ?: TvDimSaver()
        }
    }
}

/** The screen dimmed, as the Xbox dimmed it, the time wandering so nothing burns in. */
@Composable
private fun TvDimSaver() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f))) {
        WanderingClock()
    }
}

@Composable
private fun WanderingClock() {
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val u = maxWidth / REFERENCE_WIDTH
        var place by remember { mutableIntStateOf(0) }
        val time by produceState(clockNow(context)) {
            while (true) {
                delay(60_000L - System.currentTimeMillis() % 60_000L)
                value = clockNow(context)
                place++
            }
        }
        val random = remember(place) { Random(place * 7919 + 13) }
        Text(
            time,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = SelawikFamily, fontWeight = FontWeight.Light,
                fontSize = with(LocalDensity.current) { (u * 72f).toSp() }
            ),
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.offset(x = u * (120f + random.nextFloat() * 900f), y = u * (90f + random.nextFloat() * 480f))
        )
    }
}

private fun clockNow(context: Context) = android.text.format.DateFormat.getTimeFormat(context).format(Date())

/** The TV's pictures, newest first in a shuffle, one fading into the next and each drawn slowly in. */
@Composable
private fun TvPhotoSaver() {
    val context = LocalContext.current
    val photos by produceState<List<android.net.Uri>?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { tvPhotos(context, SAVER_PHOTOS).shuffled() }
    }
    val list = photos ?: return Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    // No pictures, or none it may read: the screen dims instead.
    if (list.isEmpty()) return TvDimSaver()
    val picture by produceState<ImageBitmap?>(null, list) {
        var i = 0
        while (true) {
            val next = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { tvPicture(context, list[i % list.size], SAVER_PICTURE_SIDE) }
            if (next != null) {
                value = next
                delay(PHOTO_MILLIS)
            }
            i++
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
        Crossfade(targetState = picture, animationSpec = tween(PHOTO_FADE_MILLIS), label = "tvPhotoSaver") { shownPicture ->
            if (shownPicture != null) {
                val zoom = remember { Animatable(1f) }
                LaunchedEffect(Unit) { zoom.animateTo(1.08f, tween((PHOTO_MILLIS + PHOTO_FADE_MILLIS).toInt(), easing = LinearEasing)) }
                Image(
                    shownPicture, null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom.value
                        scaleY = zoom.value
                    }
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)) {
            XboxClock()
        }
    }
}

/**
 * The music's own screen, as Xbox Music filled the TV while it played: the cover large at the
 * left, softly rising and falling, the song's name and the artist's beside it with how far in it
 * is; behind, the cover blurred over the whole screen and the artist's name drifting across it,
 * huge and faint, as the Zune's "now playing" ran it.
 */
/** The song the music's screen shows: the launcher's own player's, or another app's. */
internal class TvSong(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap?,
    /** How far in: a share, and "1:23 / 3:45"; nothing when the player does not say. */
    val progress: () -> Pair<Float, String>
)

/** What plays now: the launcher's own music first - it needs no notification access to be seen - then another app's. */
@Composable
internal fun rememberTvSong(media: ActiveMediaState): TvSong? {
    val context = LocalContext.current
    val local by com.serkantkn.zunelauncher.data.service.LauncherPlayback.state.collectAsState()
    val localArt by produceState<ImageBitmap?>(null, local.artworkUri) {
        value = local.artworkUri?.let { uri -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { tvPicture(context, uri, ART_SIDE) } }
    }
    val mediaArt = remember(media.albumArt) { media.albumArt?.asImageBitmap() }
    return when {
        local.hasQueue && local.title.isNotBlank() && (local.isPlaying || !media.isPlaying) ->
            TvSong(local.title, local.artist, local.isPlaying, localArt) {
                val now = com.serkantkn.zunelauncher.data.service.LauncherPlayback.state.value
                if (now.durationMillis <= 0L) 0f to ""
                else now.positionMillis.toFloat() / now.durationMillis to "${minutes(now.positionMillis)} / ${minutes(now.durationMillis)}"
            }
        media.title.isNotBlank() -> TvSong(media.title, media.artist, media.isPlaying, mediaArt) { progressOf(media) }
        else -> null
    }
}

@Composable
private fun TvVisualizer(song: TvSong) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val cover = song.art
    // Made tiny and drawn large, the cover blurs on any TV, without the blur only newer ones have.
    val blurred = remember(cover) {
        cover?.let { android.graphics.Bitmap.createScaledBitmap(it.asAndroidBitmap(), BLUR_SIDE, BLUR_SIDE, true).asImageBitmap() }
    }
    val motion = rememberInfiniteTransition(label = "tvVisualizer")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(DRIFT_MILLIS, easing = LinearEasing)), label = "drift")
    val bob by motion.animateFloat(-1f, 1f, infiniteRepeatable(tween(BOB_MILLIS), RepeatMode.Reverse), label = "bob")
    val breathe by motion.animateFloat(1f, 1.12f, infiniteRepeatable(tween(BREATHE_MILLIS), RepeatMode.Reverse), label = "breathe")
    val progress by rememberUpdatedState(song.progress)
    val position by produceState(0f to "") {
        while (true) {
            value = progress()
            delay(PROGRESS_TICK_MILLIS)
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF101112)).clipToBounds()) {
        val u = maxWidth / REFERENCE_WIDTH
        val density = LocalDensity.current
        fun px(v: Float) = with(density) { (u * v).toSp() }
        if (blurred != null) {
            Image(
                blurred, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                }
            )
        } else {
            // No cover: the design's own ground.
            MediaCenterBackground(modifier = Modifier.fillMaxSize()) {}
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        // The artist, huge and faint, drifting across from right to left.
        val drifting = song.artist.ifBlank { song.title }
        var wordWidth by remember { mutableIntStateOf(0) }
        val screen = with(density) { maxWidth.toPx() }
        Text(
            drifting.lowercase(),
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = px(300f)),
            color = Color.White.copy(alpha = 0.09f),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .wrapContentSize(unbounded = true, align = Alignment.BottomStart)
                .onSizeChanged { wordWidth = it.width }
                .graphicsLayer { translationX = screen - (screen + wordWidth) * drift }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = u * 104f, end = u * 90f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(u * 330f)
                    .graphicsLayer {
                        translationY = bob * 8f * u.toPx()
                        shadowElevation = 30f
                    }
                    .background(Xbox.Green)
            ) {
                if (cover != null) {
                    Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(u * 120f))
                }
            }
            Spacer(modifier = Modifier.width(u * 44f))
            Column(modifier = Modifier.fillMaxHeight(0.6f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Text(
                    stringResource(if (song.playing) R.string.xbox_now_playing_title else R.string.xbox_paused),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontSize = px(22f), shadow = xboxShadow),
                    color = Xbox.GreenLit
                )
                Text(
                    song.title,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = px(58f), lineHeight = px(64f), shadow = xboxShadow),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SelawikFamily, fontSize = px(30f), shadow = xboxShadow),
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (position.second.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(u * 26f))
                    ProgressLine(position.first, u)
                    Text(
                        position.second,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = SelawikFamily, fontSize = px(18f), shadow = xboxShadow),
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = u * 6f)
                    )
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 42f, end = u * 90f)) {
            XboxClock()
        }
    }
}

@Composable
private fun ProgressLine(done: Float, u: Dp) {
    Box(modifier = Modifier.width(u * 520f).height(u * 4f).background(Color.White.copy(alpha = 0.25f))) {
        Box(modifier = Modifier.fillMaxWidth(done.coerceIn(0f, 1f)).fillMaxHeight().background(Xbox.GreenLit))
    }
}

/** How far into the song: a share, and "1:23 / 3:45"; nothing when the player does not say. */
private fun progressOf(media: ActiveMediaState): Pair<Float, String> {
    val controller = media.controller ?: return 0f to ""
    val state = controller.playbackState ?: return 0f to ""
    val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    if (duration <= 0L) return 0f to ""
    var at = state.position
    if (state.state == android.media.session.PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
        at += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
    }
    at = at.coerceIn(0L, duration)
    return at.toFloat() / duration to "${minutes(at)} / ${minutes(duration)}"
}

private fun minutes(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private const val REFERENCE_WIDTH = 1280f
private const val IDLE_CHECK_MILLIS = 2_000L
/** The music's screen comes up after two minutes of the remote at rest. */
private const val MUSIC_AFTER_MILLIS = 120_000L
private const val FADE_IN_MILLIS = 1_200
private const val FADE_OUT_MILLIS = 250
private const val SAVER_PHOTOS = 80
private const val SAVER_PICTURE_SIDE = 1920
private const val PHOTO_MILLIS = 9_000L
private const val PHOTO_FADE_MILLIS = 1_500
private const val BLUR_SIDE = 12
private const val ART_SIDE = 800
private const val DRIFT_MILLIS = 45_000
private const val BOB_MILLIS = 3_600
private const val BREATHE_MILLIS = 14_000
private const val PROGRESS_TICK_MILLIS = 500L
