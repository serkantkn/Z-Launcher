package com.serkantkn.zunelauncher.ui.tv

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
 * Start on a television, in the two ways Metro came to the living room:
 *
 * - **the board** ([HomeScreenLayout.WINDOWS_PHONE]): the Xbox 360's - sections named along the
 *   top, large tiles under them running off to the right;
 * - **the list** ([HomeScreenLayout.ZUNE]): Windows Media Center's and the Zune's - the places
 *   as huge words down the left, and what is in the one chosen beside it.
 *
 * Both are worked with a remote: every tile and word takes focus, the focused one stands out in
 * white and a little larger, and nothing is reached by touch alone. The edges keep the 5% a
 * television may cut off.
 */
@Composable
fun TvStartScreen(
    layout: HomeScreenLayout,
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
    LaunchedEffect(hubOpen) {
        if (wasOpen && !hubOpen) {
            delay(FIRST_FOCUS_DELAY_MILLIS)
            // A hub opened before anything on Start had the focus returns it to the first tile.
            memory.restoreOr("videos")
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TV_GROUND)
            .onFocusChanged { startHasFocus = it.hasFocus }
            .padding(horizontal = TV_SAFE_X, vertical = TV_SAFE_Y)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalTvFocusMemory provides memory,
            LocalTvHubOpen provides hubOpen
        ) {
            when (layout) {
                HomeScreenLayout.ZUNE -> TvZuneStart(places = places, apps = apps)
                else -> TvBoardStart(places = places, apps = apps)
            }
        }
        TvClock(modifier = Modifier.align(Alignment.TopEnd))
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

// ── A: the Xbox 360's board ─────────────────────────────────────────────────────────────────

/**
 * The sections as one row running right, their names along the top: the one the focus is in is
 * white, the rest dimmed, as the Xbox 360's channels were. Up from the tiles reaches the names;
 * a name taken goes to its section.
 */
@Composable
private fun TvBoardStart(places: List<TvPlace>, apps: List<TvApp>) {
    val byKey = places.associateBy { it.key }
    val sections = listOf(
        TvSection(stringResource(R.string.browser_home), buildList {
            // The big tile is the film left part way when there is one, the videos when not.
            // The rest follow two to a column, with no hole left in either case.
            val lead = byKey["continue"]
            add(TvColumn.Big(lead ?: byKey.getValue("videos")))
            val rest = listOfNotNull(
                byKey["videos"].takeIf { lead != null },
                byKey["music"], byKey["pictures"], byKey["weather"], byKey["news"], byKey["files"], byKey["clock"]
            )
            rest.chunked(2).forEach { add(TvColumn.Pair(it[0], it.getOrNull(1))) }
        }),
        TvSection(stringResource(R.string.apps_hub), apps.chunked(2).map { TvColumn.Apps(it) }),
        TvSection(stringResource(R.string.settings_hub), listOf(
            TvColumn.Pair(byKey.getValue("settings"), byKey.getValue("android_settings"))
        ))
    )
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var current by remember { mutableIntStateOf(0) }
    val firstTile = remember { FocusRequester() }
    // Asked for once the row has been laid out, or the focus falls on the first section's name.
    // Read when the time comes, not when the wait began: a hub opened meanwhile keeps the focus.
    val hubOpen by androidx.compose.runtime.rememberUpdatedState(LocalTvHubOpen.current)
    LaunchedEffect(Unit) {
        delay(FIRST_FOCUS_DELAY_MILLIS)
        if (!hubOpen) runCatching { firstTile.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            sections.forEachIndexed { index, section ->
                TvFocusText(
                    text = section.title,
                    size = 26.sp,
                    selected = index == current,
                    onFocus = {
                        current = index
                        scope.launch { listState.animateScrollToItem(index) }
                    },
                    onClick = { scope.launch { listState.animateScrollToItem(index) } }
                )
            }
        }
        // Room round the tiles for the focused one to grow into: a row clips what it holds, and
        // the first tile's edge and its words were cut off. The row is moved back by as much,
        // so the tiles stand where they did.
        LazyRow(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(FOCUS_ROOM),
            modifier = Modifier.fillMaxWidth().weight(1f).offset(x = -FOCUS_ROOM, y = -FOCUS_ROOM),
            horizontalArrangement = Arrangement.spacedBy(SECTION_GAP),
            verticalAlignment = Alignment.Top
        ) {
            itemsIndexed(sections) { index, section ->
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onFocusChanged { if (it.hasFocus) current = index },
                    horizontalArrangement = Arrangement.spacedBy(TILE_GAP)
                ) {
                    section.columns.forEachIndexed { columnIndex, column ->
                        val focus = if (index == 0 && columnIndex == 0) Modifier.focusRequester(firstTile) else Modifier
                        TvBoardColumn(column, focus)
                    }
                }
            }
        }
    }
}

private class TvSection(val title: String, val columns: List<TvColumn>)

private sealed interface TvColumn {
    class Big(val place: TvPlace) : TvColumn
    class Pair(val top: TvPlace, val bottom: TvPlace?) : TvColumn
    class Apps(val apps: List<TvApp>) : TvColumn
}

@Composable
private fun TvBoardColumn(column: TvColumn, firstFocus: Modifier) {
    val accent = LocalZuneColors.current.accentColor
    when (column) {
        is TvColumn.Big -> TvTile(
            title = column.place.title,
            line = column.place.line,
            color = accent,
            // The film's own picture is wide: its tile is too.
            width = if (column.place.image != null && column.place.key == "continue") BIG_WIDE else BIG_TILE,
            height = BIG_TILE,
            onClick = column.place.open,
            modifier = firstFocus.remembered(column.place.key),
            image = column.place.image,
            progress = column.place.progress
        )
        is TvColumn.Pair -> Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            TvTile(column.top.title, column.top.line, accent.shade(0.8f), SMALL_TILE, SMALL_TILE, column.top.open, firstFocus.remembered(column.top.key), column.top.image, column.top.progress)
            column.bottom?.let { TvTile(it.title, it.line, accent.shade(0.62f), SMALL_TILE, SMALL_TILE, it.open, Modifier.remembered(it.key), it.image, it.progress) }
        }
        is TvColumn.Apps -> {
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                column.apps.forEachIndexed { i, app ->
                    TvAppTile(app, BANNER_WIDTH, SMALL_TILE, (if (i == 0) firstFocus else Modifier).remembered(app.packageName)) {
                        context.startSafely(app.launch)
                    }
                }
            }
        }
    }
}

// ── C: the Zune's / Media Center's list ─────────────────────────────────────────────────────

/**
 * The places as huge words down the left, the one with the focus in white and the rest fading
 * with their distance from it, the way Media Center's strip read across a room. Beside the words
 * is what the chosen place holds; right from a word goes into it.
 */
@Composable
private fun TvZuneStart(places: List<TvPlace>, apps: List<TvApp>) {
    val appsWord = stringResource(R.string.apps_hub)
    val words = buildList {
        places.firstOrNull { it.key == "continue" }?.let { add(it) }
        add(places.first { it.key == "videos" })
        add(places.first { it.key == "music" })
        add(places.first { it.key == "pictures" })
        add(TvPlace("apps", appsWord, null) {})
        addAll(places.filter { it.key in setOf("weather", "news", "files", "clock", "settings") })
    }
    var chosen by remember { mutableIntStateOf(0) }
    val firstWord = remember { FocusRequester() }
    val wordsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Read when the time comes, not when the wait began: a hub opened meanwhile keeps the focus.
    val hubOpen by androidx.compose.runtime.rememberUpdatedState(LocalTvHubOpen.current)
    LaunchedEffect(Unit) {
        delay(FIRST_FOCUS_DELAY_MILLIS)
        if (!hubOpen) runCatching { firstWord.requestFocus() }
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        LazyColumn(
            state = wordsState,
            modifier = Modifier.fillMaxHeight().width(WORDS_WIDTH),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 60.dp)
        ) {
            itemsIndexed(words) { index, place ->
                val distance = kotlin.math.abs(index - chosen)
                TvFocusText(
                    text = place.title,
                    size = 54.sp,
                    selected = index == chosen,
                    fade = (1f - distance * 0.22f).coerceIn(0.3f, 1f),
                    onFocus = {
                        chosen = index
                        // The chosen word rides near the middle, as Media Center's strip did,
                        // with the words before it still in sight above.
                        scope.launch { wordsState.animateScrollToItem((index - WORDS_ABOVE).coerceAtLeast(0)) }
                    },
                    onClick = place.open,
                    modifier = (if (index == 0) Modifier.focusRequester(firstWord) else Modifier).remembered(place.key)
                )
            }
        }
        val place = words[chosen]
        Box(modifier = Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.CenterStart) {
            if (place.key == "apps") {
                TvAppsPreview(apps)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = place.line ?: stringResource(R.string.tv_open_hint),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                        color = Color.White.copy(alpha = 0.82f)
                    )
                    TvTile(
                        title = place.title,
                        line = null,
                        color = LocalZuneColors.current.accentColor,
                        width = if (place.image != null) PREVIEW_WIDE else BANNER_WIDTH,
                        height = if (place.image != null) PREVIEW_TALL else SMALL_TILE,
                        onClick = place.open,
                        image = place.image,
                        progress = place.progress
                    )
                }
            }
        }
    }
}

@Composable
private fun TvAppsPreview(apps: List<TvApp>) {
    val context = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(FOCUS_ROOM),
        modifier = Modifier.offset(x = -FOCUS_ROOM)
    ) {
        itemsIndexed(apps.chunked(2)) { _, pair ->
            Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                pair.forEach { app ->
                    TvAppTile(app, BANNER_WIDTH, SMALL_TILE, Modifier.remembered(app.packageName)) { context.startSafely(app.launch) }
                }
            }
        }
    }
}

// ── Shared pieces ───────────────────────────────────────────────────────────────────────────

/**
 * How a TV shows where the remote is: the focused thing grows a little and gains a white frame,
 * as the Xbox's tiles did. Focus is the only pointer a sofa has, so it is never left unmarked.
 */
@Composable
private fun Modifier.tvFocus(source: MutableInteractionSource): Modifier {
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) FOCUS_SCALE else 1f, tween(FOCUS_MILLIS), label = "tvFocus")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent)
}

@Composable
private fun TvTile(
    title: String,
    line: String?,
    color: Color,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    progress: Float? = null
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width, height)
            .tvFocus(source)
            .background(color)
            .tvOwnFocus()
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // The words at the foot stay readable over any picture.
            if (title.isNotEmpty() || line != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .align(Alignment.BottomStart)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                )
            }
        }
        if (progress != null) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart).background(Color.White.copy(alpha = 0.3f)))
            Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).align(Alignment.BottomStart).background(LocalZuneColors.current.accentColor))
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            if (line != null) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** An app by the banner it made for TVs; one without a banner shows its icon on the accent. */
@Composable
private fun TvAppTile(app: TvApp, width: Dp, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    if (app.banner != null) {
        TvTile(title = "", line = null, color = Color.Black, width = width, height = height, onClick = onClick, modifier = modifier, image = app.banner)
        return
    }
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width, height)
            .tvFocus(source)
            .background(LocalZuneColors.current.accentColor.shade(0.7f))
            .tvOwnFocus()
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        app.icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(72.dp)) }
        Text(
            text = app.label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
        )
    }
}

/** A word to be taken with the remote: white while it has the focus, dimmed while it has not. */
@Composable
private fun TvFocusText(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fade: Float = 1f
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    Text(
        text = text,
        style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.Light,
            fontSize = size,
            lineHeight = size * 1.15f
        ),
        color = when {
            focused -> Color.White
            selected -> Color.White.copy(alpha = 0.78f)
            else -> Color.White.copy(alpha = 0.4f * fade)
        },
        maxLines = 1,
        modifier = modifier.tvOwnFocus().clickable(interactionSource = source, indication = null, onClick = onClick)
    )
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
private val TV_GROUND = Color(0xFF0E0D10)
private val BIG_TILE = 352.dp
/** How far a focused tile may grow past its row's edge without being cut off. */
private val FOCUS_ROOM = 20.dp
private val BIG_WIDE = 626.dp
private val PREVIEW_WIDE = 480.dp
private val PREVIEW_TALL = 270.dp
private val SMALL_TILE = 170.dp
private val BANNER_WIDTH = 302.dp
private val TILE_GAP = 12.dp
private val SECTION_GAP = 64.dp
private val WORDS_WIDTH = 380.dp
private const val FOCUS_SCALE = 1.06f
private const val WORDS_ABOVE = 2
private const val FIRST_FOCUS_DELAY_MILLIS = 120L
private const val HUB_TAKES_FOCUS_MILLIS = 600L
private const val FOCUS_MILLIS = 140
