package com.serkantkn.zunelauncher.ui.tv

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.asImageBitmap
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.MusicBridge
import com.serkantkn.zunelauncher.ui.screens.home.HomeHubViewModel
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

/**
 * Start on a television, in the design chosen in settings: Media Center's (Windows Vista's) or the
 * Xbox 360's dashboard. Both are worked with a remote alone and lead with the film left part way
 * when there is one; both take the remote whole and draw their own focus.
 */
@Composable
fun TvStartScreen(
    homeHubViewModel: HomeHubViewModel,
    onOpenHub: (HubType) -> Unit,
    modifier: Modifier = Modifier,
    hubOpen: Boolean = false,
    /** Goes up by one each time Home is pressed on Start: back to the first strip's first tile. */
    homeResetSignal: Int = 0
) {
    val context = LocalContext.current
    // Both of a TV's Starts take the remote whole and draw their own focus.
    val vista = LocalVista.current
    val startKey = VISTA_KEY
    // What had the focus when a hub was opened gets it back when the hub closes, as on the Xbox.
    val memory = remember { TvFocusMemory() }
    var wasOpen by remember { mutableStateOf(hubOpen) }
    // A hub opened over Start (from a notification, a shortcut, as the launcher starts) must not
    // leave the remote on Start's tiles underneath it: the focus is let go of, and the hub takes it.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var startHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hubOpen, startHasFocus) {
        // The hub is given a moment to take it itself (its headings do); clearing at once raced
        // that and could leave nothing focused at all.
        if (hubOpen && startHasFocus) {
            delay(HUB_TAKES_FOCUS_MILLIS)
            focusManager.clearFocus(force = true)
        }
    }
    // Start lends the remote a way back too, for when nothing has the focus while it is showing.
    val rescueScope = rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(memory) {
        val rescue: () -> Unit = { rescueScope.launch { memory.restoreOr(startKey) } }
        TvFocus.startRescue = rescue
        onDispose { if (TvFocus.startRescue === rescue) TvFocus.startRescue = null }
    }
    LaunchedEffect(hubOpen) {
        if (wasOpen && !hubOpen) {
            delay(FIRST_FOCUS_DELAY_MILLIS)
            // A hub opened before anything on Start had the focus returns it to the first tile.
            memory.restoreOr(startKey)
        }
        wasOpen = hubOpen
    }
    // Read again whenever Start is come back to: from a hub, from another app, from standby.
    var liveTurn by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { liveTurn++ }
    LaunchedEffect(hubOpen) { if (!hubOpen) liveTurn++ }
    // The apps too: one put on from the store, or taken off in the TV's settings, shows or goes
    // when Start is back - listed again only if something did change.
    var appsSeen by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var apps by remember { mutableStateOf(emptyList<TvApp>()) }
    LaunchedEffect(liveTurn) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val signature = tvAppsSignature(context)
            if (signature != appsSeen) {
                apps = loadTvApps(context)
                appsSeen = signature
            }
        }
    }
    // Null until first read: Start gives its first focus only then, to "continue" if there is a
    // film to carry on with, and not to the strip that would stand under it a moment later.
    val read by produceState<TvLive?>(initialValue = null, liveTurn) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadTvLive(context) }
    }
    val live = read ?: TvLive()
    val places = tvPlaces(homeHubViewModel, live, onOpenHub)
    MediaCenterBackground(modifier = modifier.onFocusChanged { startHasFocus = it.hasFocus }) {
        if (vista) {
            var libraryOpen by remember { mutableStateOf(false) }
            var shutdownOpen by remember { mutableStateOf(false) }
            val categories = vistaCategories(places, apps, onOpenHub, { libraryOpen = true }, { shutdownOpen = true })
            // Given at first, and again after Home: Home lets go of the focus on its way (for a
            // hub's text field), and Start, which takes the remote whole, must take it back.
            LaunchedEffect(homeResetSignal) {
                delay(FIRST_FOCUS_DELAY_MILLIS)
                if (!hubOpen) memory.focus(VISTA_KEY)
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalTvFocusMemory provides memory) {
                VistaStart(
                    categories = categories,
                    apps = apps,
                    hubOpen = hubOpen,
                    homeResetSignal = homeResetSignal,
                    libraryOpen = libraryOpen,
                    onLibraryOpen = { libraryOpen = it },
                    shutdownOpen = shutdownOpen,
                    onShutdownOpen = { shutdownOpen = it },
                    focusModifier = Modifier.remembered(VISTA_KEY)
                )
            }
            // The library is a page of its own, with its name where the clock stands.
            if (!libraryOpen) VistaClock(modifier = Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 40.dp))
        } else {
            // The Xbox 360's dashboard. Everything it can open is known by key, for pins, recent
            // and the guide; opening a hub through it counts it as recently used.
            remember(context) { XboxShelf.attach(context) }
            val items = xboxItems(places, apps, live)
            androidx.compose.runtime.SideEffect { XboxShelf.items.value = items }
            LaunchedEffect(homeResetSignal) {
                delay(FIRST_FOCUS_DELAY_MILLIS)
                if (!hubOpen) memory.focus(VISTA_KEY)
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalTvFocusMemory provides memory) {
                XboxStart(
                    items = items,
                    apps = apps,
                    hubOpen = hubOpen,
                    homeResetSignal = homeResetSignal,
                    onOpenHub = onOpenHub,
                    focusModifier = Modifier.remembered(VISTA_KEY)
                )
            }
        }
    }
}

/** Which of Start's tiles or words last held the focus, so it can be handed back. */
internal class TvFocusMemory {
    private val requesters = mutableMapOf<String, FocusRequester>()
    /** Read while drawing: Back on Start does something only away from the first tile. */
    var last: String? by mutableStateOf(null)
        private set

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun focused(key: String) {
        last = key
    }

    /**
     * Hands the focus back; true if it went. A tile that is gone - the film finished, the app
     * taken off - takes nothing, and the caller's fallback has it instead.
     */
    fun restore(): Boolean = last?.let(::focus) == true

    /** Hands the focus to [key] if what had it before is not there to take it. */
    fun restoreOr(key: String) {
        if (!restore()) focus(key)
    }

    /** Hands the focus to [key] whatever had it before. */
    fun focus(key: String): Boolean =
        runCatching { requesters[key]?.requestFocus() == true }.getOrDefault(false)
}

internal val LocalTvFocusMemory = androidx.compose.runtime.staticCompositionLocalOf { TvFocusMemory() }

/** Gives [key]'s tile or word a place in [TvFocusMemory]. */
@Composable
private fun Modifier.remembered(key: String): Modifier {
    val memory = LocalTvFocusMemory.current
    return this
        .focusRequester(memory.requester(key))
        .onFocusChanged { if (it.isFocused) memory.focused(key) }
}

/** One of the launcher's places on a TV: a hub, or the system's own settings. */
internal class TvPlace(
    val key: String,
    val title: String,
    val line: String?,
    /** A picture for its tile: the album playing, the last photo, the film left part way. */
    val image: ImageBitmap? = null,
    /** How far in something is, 0-1, drawn along the tile's foot. */
    val progress: Float? = null,
    val open: () -> Unit
)

@Composable
private fun tvPlaces(viewModel: HomeHubViewModel, live: TvLive, onOpenHub: (HubType) -> Unit): List<TvPlace> {
    val context = LocalContext.current
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val weather by viewModel.weatherSnapshot.collectAsState()
    val news by viewModel.newsStories.collectAsState()
    val alarms by viewModel.enabledAlarms.collectAsState()
    val playing = nowPlaying.takeIf { it.isPlaying && it.title.isNotBlank() }
    // The launcher's own music too, which is seen without notification access.
    val own by com.serkantkn.zunelauncher.data.service.LauncherPlayback.state.collectAsState()
    val ownPlaying = own.takeIf { it.isPlaying && it.title.isNotBlank() }
    val playingLine = (ownPlaying?.let { listOf(it.title, it.artist) } ?: playing?.let { listOf(it.title, it.artist) })
        ?.filter(String::isNotBlank)?.joinToString(" · ")
    val weatherLine = weather?.let { "${it.now.temperature.roundToInt()}° ${it.place.name}" }
    val nextAlarm = remember(alarms) {
        val now = System.currentTimeMillis()
        alarms.mapNotNull { it.dueAt(now) }.minOrNull()
    }
    val alarmLine = nextAlarm?.let {
        stringResource(R.string.zune_line_alarm, android.text.format.DateFormat.getTimeFormat(context).format(Date(it)))
    }
    val left = live.continueWatching
    return buildList {
        // The film left part way, ahead of everything, as the Xbox put what you were doing first.
        if (left != null) {
            add(TvPlace("continue", stringResource(R.string.tv_continue), left.title, left.thumbnail, left.progress) {
                com.serkantkn.zunelauncher.ui.screens.video.VideoPlayer.open(context, left.uri, left.title)
            })
        }
        add(TvPlace("videos", stringResource(R.string.music_tab_videos), null) {
            MusicBridge.openVideos()
            onOpenHub(HubType.MUSIC)
        })
        add(TvPlace("music", stringResource(R.string.zune_row_music), playingLine, playing?.albumArt?.asImageBitmap()) { onOpenHub(HubType.MUSIC) })
        add(TvPlace("pictures", stringResource(HubType.PICTURES.titleRes), null, live.latestPhoto) { onOpenHub(HubType.PICTURES) })
        add(TvPlace("weather", stringResource(HubType.WEATHER.titleRes), weatherLine) { onOpenHub(HubType.WEATHER) })
        add(TvPlace("news", stringResource(HubType.NEWS.titleRes), news.firstOrNull()?.title) { onOpenHub(HubType.NEWS) })
        add(TvPlace("files", stringResource(HubType.FILES.titleRes), null) { onOpenHub(HubType.FILES) })
        add(TvPlace("clock", stringResource(HubType.CLOCK.titleRes), alarmLine) { onOpenHub(HubType.CLOCK) })
        add(TvPlace("settings", stringResource(R.string.settings_hub), null) { onOpenHub(HubType.SETTINGS) })
        add(TvPlace("android_settings", stringResource(R.string.tv_android_settings), null) {
            context.startSafely(Intent(Settings.ACTION_SETTINGS))
        })
    }
}

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { ZuneLog.w("TvStart", "could not open $intent", it) }
}

private const val FIRST_FOCUS_DELAY_MILLIS = 120L
/** Start takes the remote whole, under this name in [TvFocusMemory]. */
private const val VISTA_KEY = "vista"
private const val HUB_TAKES_FOCUS_MILLIS = 600L
