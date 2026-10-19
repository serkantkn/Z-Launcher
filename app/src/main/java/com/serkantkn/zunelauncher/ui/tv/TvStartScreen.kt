package com.serkantkn.zunelauncher.ui.tv

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.asImageBitmap
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.MusicBridge
import com.serkantkn.zunelauncher.ui.screens.home.HomeHubViewModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    hubOpen: Boolean = false
) {
    val context = LocalContext.current
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
        val rescue: () -> Unit = { rescueScope.launch { memory.restoreOr("pictures") } }
        TvFocus.startRescue = rescue
        onDispose { if (TvFocus.startRescue === rescue) TvFocus.startRescue = null }
    }
    LaunchedEffect(hubOpen) {
        if (wasOpen && !hubOpen) {
            delay(FIRST_FOCUS_DELAY_MILLIS)
            // A hub opened before anything on Start had the focus returns it to the first tile.
            memory.restoreOr("pictures")
        }
        wasOpen = hubOpen
    }
    val apps by produceState(initialValue = emptyList<TvApp>(), context) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadTvApps(context) }
    }
    // Read again whenever Start is come back to: from a hub, from another app, from standby.
    var liveTurn by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { liveTurn++ }
    LaunchedEffect(hubOpen) { if (!hubOpen) liveTurn++ }
    val live by produceState(initialValue = TvLive(), liveTurn) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadTvLive(context) }
    }
    val places = tvPlaces(homeHubViewModel, live, onOpenHub)
    val strips = tvStrips(places, apps)
    MediaCenterBackground(modifier = modifier.onFocusChanged { startHasFocus = it.hasFocus }) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = TV_SAFE_X, vertical = TV_SAFE_Y)) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalTvFocusMemory provides memory,
                LocalTvHubOpen provides hubOpen
            ) {
                MediaCenterStart(strips)
            }
            TvClock(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Which of Start's tiles or words last held the focus, so it can be handed back. */
internal class TvFocusMemory {
    private val requesters = mutableMapOf<String, FocusRequester>()
    private var last: String? = null

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun focused(key: String) {
        last = key
    }

    /** Hands the focus back; true if there was somewhere to hand it. */
    fun restore(): Boolean {
        val key = last ?: return false
        return runCatching { requesters[key]?.requestFocus() }.isSuccess
    }

    /** Hands the focus to [key] if nothing had it before. */
    fun restoreOr(key: String) {
        if (!restore()) runCatching { requesters[key]?.requestFocus() }
    }
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
        stringResource(R.string.zune_line_alarm, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)))
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
private fun MediaCenterStart(strips: List<TvStrip>) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The strip the remote is in, by its name: "continue" can come in above the others once the
    // videos are read, and the strips' places move down under it.
    var current by remember { mutableStateOf(strips.firstOrNull()?.title) }
    val memory = LocalTvFocusMemory.current
    val hubOpen by androidx.compose.runtime.rememberUpdatedState(LocalTvHubOpen.current)
    val firstKey = strips.firstOrNull()?.items?.firstOrNull()?.key
    // The first focus is given once, when the strips are first there; strips that come later
    // must not pull the remote back from where it has gone.
    LaunchedEffect(firstKey != null) {
        delay(FIRST_FOCUS_DELAY_MILLIS)
        if (!hubOpen && firstKey != null) memory.restoreOr(firstKey)
    }
    val currentIndex = strips.indexOfFirst { it.title == current }
    LaunchedEffect(strips.size) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The strip with the remote stands a little above the middle, as Media Center's did.
        val above = maxHeight * 0.30f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = above, bottom = maxHeight * 0.45f),
            verticalArrangement = Arrangement.spacedBy(STRIP_GAP)
        ) {
            itemsIndexed(strips, key = { _, strip -> strip.title }) { index, strip ->
                val active = strip.title == current
                val scale by animateFloatAsState(if (active) 1f else 0.78f, tween(220), label = "stripScale")
                val fade by animateFloatAsState(if (active) 1f else 0.42f, tween(220), label = "stripFade")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.hasFocus && current != strip.title) {
                                current = strip.title
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        }
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
    val time by produceState(initialValue = clockNow()) {
        while (true) {
            value = clockNow()
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

private fun clockNow(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun Color.shade(factor: Float): Color = Color(red * factor, green * factor, blue * factor, alpha)

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
private const val HUB_TAKES_FOCUS_MILLIS = 600L
