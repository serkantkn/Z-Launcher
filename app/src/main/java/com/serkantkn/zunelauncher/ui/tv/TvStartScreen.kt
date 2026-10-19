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
 * Start on a television: Windows Media Center's - its strips one above another (pictures and
 * videos, music, apps, extras, tasks), the one the remote is in bright with its things in a row,
 * the film left part way leading when there is one. Worked with a remote alone; the edges keep
 * the 5% a television may cut off. A TV has this Start and no other.
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
    // Vista's Start is one thing to the remote; Windows 7's hands it from tile to tile.
    val vista = LocalVista.current
    val startKey = if (vista) VISTA_KEY else "pictures"
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
            val strips = tvStrips(places, apps)
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = TV_SAFE_X, vertical = TV_SAFE_Y)) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTvFocusMemory provides memory,
                    LocalTvHubOpen provides hubOpen
                ) {
                    MediaCenterStart(strips, homeResetSignal, ready = read != null)
                }
                TvClock(modifier = Modifier.align(Alignment.TopEnd))
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

/** Whether a hub stands over Start, which then asks for no focus of its own. */
private val LocalTvHubOpen = androidx.compose.runtime.compositionLocalOf { false }

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
    val playingLine = playing?.let { listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · ") }
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

// ── Media Center's Start ────────────────────────────────────────────────────────────────────

/** One of Media Center's strips: its name, and what it holds. */
private class TvStrip(val title: String, val items: List<TvStripItem>)

private class TvStripItem(
    val key: String,
    val label: String,
    val line: String?,
    val icon: ImageVector?,
    val image: ImageBitmap?,
    /** An app's own banner, which fills the tile and carries its name. */
    val banner: Boolean = false,
    val progress: Float? = null,
    val open: () -> Unit
)

@Composable
private fun tvStrips(places: List<TvPlace>, apps: List<TvApp>): List<TvStrip> {
    val context = LocalContext.current
    val byKey = places.associateBy { it.key }
    fun item(key: String, icon: ImageVector): TvStripItem? = byKey[key]?.let {
        TvStripItem(it.key, it.title, it.line, icon, it.image, progress = it.progress, open = it.open)
    }
    return buildList {
        byKey["continue"]?.let { left ->
            add(TvStrip(left.title, listOf(
                TvStripItem("continue", left.line.orEmpty(), null, Icons.Default.PlayArrow, left.image, progress = left.progress, open = left.open)
            )))
        }
        add(TvStrip(stringResource(R.string.tv_strip_pictures_videos), listOfNotNull(
            item("pictures", Icons.Default.Photo), item("videos", Icons.Default.Movie)
        )))
        add(TvStrip(stringResource(R.string.zune_row_music), listOfNotNull(item("music", Icons.Default.MusicNote))))
        if (apps.isNotEmpty()) {
            add(TvStrip(stringResource(R.string.apps_hub), apps.map { app ->
                TvStripItem(app.packageName, app.label, null, null, app.banner ?: app.icon, banner = app.banner != null) {
                    context.startSafely(app.launch)
                }
            }))
        }
        add(TvStrip(stringResource(R.string.tv_strip_extras), listOfNotNull(
            item("weather", Icons.Default.WbSunny), item("news", Icons.Default.Article), item("clock", Icons.Default.Alarm)
        )))
        add(TvStrip(stringResource(R.string.tv_strip_tasks), listOfNotNull(
            item("files", Icons.Default.Folder), item("settings", Icons.Default.Settings), item("android_settings", Icons.Default.Tv)
        )))
    }
}

/**
 * Media Center's Start: its strips one above another down the middle of the screen, the one the
 * remote is in bright and at full size with its things in a row beside its name, the others pale
 * and smaller above and below it. Up and down go from strip to strip, left and right along one.
 */
@Composable
private fun MediaCenterStart(strips: List<TvStrip>, homeResetSignal: Int, ready: Boolean) {
    val scope = rememberCoroutineScope()
    // The strip the remote is in, by its name: "continue" can come in above the others once the
    // videos are read, and the strips' places move down under it.
    var current by remember { mutableStateOf(strips.firstOrNull()?.title) }
    val memory = LocalTvFocusMemory.current
    val hubOpen by androidx.compose.runtime.rememberUpdatedState(LocalTvHubOpen.current)
    val firstKey = strips.firstOrNull()?.items?.firstOrNull()?.key
    // The first focus is given once, when what Start shows has been read; strips that come
    // later must not pull the remote back from where it has gone.
    // The window hands the focus to the first tile it finds as soon as it has one - before the
    // films are read, that is pictures - so what counts is whether the remote has been touched.
    val first by androidx.compose.runtime.rememberUpdatedState(strips.firstOrNull())
    val shownAt = remember { android.os.SystemClock.uptimeMillis() }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        delay(FIRST_FOCUS_DELAY_MILLIS)
        val strip = first ?: return@LaunchedEffect
        if (!hubOpen && TvFocus.lastKeyAt < shownAt) {
            current = strip.title
            strip.items.firstOrNull()?.let { memory.focus(it.key) }
        }
    }
    // Home on Start, and Back away from the first tile, go back to the top as Google TV's own
    // Start does: the first strip, its first tile. Home first let go of the focus (LauncherScreen
    // clears it, for a hub's text field), so it is given here again.
    val toTop: () -> Unit = {
        if (firstKey != null) {
            current = strips.first().title
            memory.focus(firstKey)
        }
    }
    var homeSeen by remember { mutableIntStateOf(homeResetSignal) }
    LaunchedEffect(homeResetSignal) {
        if (homeResetSignal != homeSeen) {
            homeSeen = homeResetSignal
            if (!hubOpen) toTop()
        }
    }
    androidx.activity.compose.BackHandler(enabled = !hubOpen && firstKey != null && memory.last != firstKey) {
        toTop()
    }
    // Where each strip stands in the column, to bring the one with the remote to its place.
    val tops = remember { androidx.compose.runtime.mutableStateMapOf<String, Float>() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
        // The strip with the remote stands a little above the middle, as Media Center's did.
        val above = with(LocalDensity.current) { (maxHeight * 0.30f).toPx() }
        val shift by animateFloatAsState(above - (tops[current] ?: 0f), tween(260), label = "stripShift")
        // Every strip is laid out, not only those on screen: a remote held down outruns the
        // slide, and a strip not there yet would give the press nowhere to go. There are six.
        Column(
            verticalArrangement = Arrangement.spacedBy(STRIP_GAP),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = shift }
        ) {
            strips.forEach { strip ->
                androidx.compose.runtime.key(strip.title) {
                    MediaCenterStrip(
                        strip = strip,
                        active = strip.title == current,
                        modifier = Modifier
                            .onPlaced { tops[strip.title] = it.positionInParent().y }
                            .onFocusChanged { if (it.hasFocus) current = strip.title }
                    )
                }
            }
        }
    }
}

/** One strip: its name, bright while the remote is in it, and its things in a row beside it. */
@Composable
private fun MediaCenterStrip(strip: TvStrip, active: Boolean, modifier: Modifier) {
    val scale by animateFloatAsState(if (active) 1f else 0.78f, tween(220), label = "stripScale")
    val fade by animateFloatAsState(if (active) 1f else 0.42f, tween(220), label = "stripFade")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = fade
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
    ) {
        Text(
            text = strip.title,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 36.sp, lineHeight = 38.sp),
            color = if (active) Color.White else MediaCenter.Dim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(STRIP_TITLE_WIDTH)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
            contentPadding = PaddingValues(FOCUS_ROOM),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(strip.items, key = { _, item -> item.key }) { _, item ->
                MediaCenterTile(item, Modifier.remembered(item.key))
            }
        }
    }
}

/** A thing on a strip: its picture, or its sign on Media Center's blue, its name under it. */
@Composable
private fun MediaCenterTile(item: TvStripItem, modifier: Modifier) {
    Column(modifier = Modifier.width(TILE_WIDTH)) {
        Box(
            modifier = modifier
                .size(TILE_WIDTH, TILE_HEIGHT)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF2A62B0), Color(0xFF123E80))),
                    RoundedCornerShape(4.dp)
                )
                .clip(RoundedCornerShape(4.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = item.open),
            contentAlignment = Alignment.Center
        ) {
            when {
                item.image != null && item.banner -> Image(item.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                item.image != null && item.icon == null -> Image(item.image, null, modifier = Modifier.size(72.dp))
                item.image != null -> Image(item.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                item.icon != null -> Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            if (item.progress != null) {
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart).background(Color.White.copy(alpha = 0.3f)))
                Box(modifier = Modifier.fillMaxWidth(item.progress).height(4.dp).align(Alignment.BottomStart).background(MediaCenter.Glow))
            }
        }
        if (!item.banner) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (item.line != null) {
                Text(
                    text = item.line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediaCenter.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The time in the corner, as the Xbox kept it; read off the minute, not polled. */
@Composable
private fun TvClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // As the TV is set: 24 hours, or 12 with am/pm.
    val time by produceState(initialValue = clockNow(context)) {
        while (true) {
            value = clockNow(context)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Text(
        text = time,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
        color = Color.White.copy(alpha = 0.85f),
        modifier = modifier
    )
}

private fun clockNow(context: Context): String = android.text.format.DateFormat.getTimeFormat(context).format(Date())

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { ZuneLog.w("TvStart", "could not open $intent", it) }
}

/** The part of the picture a TV may crop: 5% of 960 × 540 dp. */
private val TV_SAFE_X = 48.dp
private val TV_SAFE_Y = 27.dp
/** How far a focused tile may glow past its row's edge without being cut off. */
private val FOCUS_ROOM = 20.dp
private val TILE_WIDTH = 200.dp
private val TILE_HEIGHT = 112.dp
private val TILE_GAP = 14.dp
private val STRIP_GAP = 8.dp
private val STRIP_TITLE_WIDTH = 210.dp
private const val FIRST_FOCUS_DELAY_MILLIS = 120L
/** Vista's Start takes the remote whole, under this name in [TvFocusMemory]. */
private const val VISTA_KEY = "vista"
private const val HUB_TAKES_FOCUS_MILLIS = 600L
