package com.serkantkn.zunelauncher.ui.tv

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apps by produceState(initialValue = emptyList<TvApp>(), context) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadTvApps(context) }
    }
    val places = tvPlaces(homeHubViewModel, onOpenHub)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TV_GROUND)
            .padding(horizontal = TV_SAFE_X, vertical = TV_SAFE_Y)
    ) {
        when (layout) {
            HomeScreenLayout.ZUNE -> TvZuneStart(places = places, apps = apps)
            else -> TvBoardStart(places = places, apps = apps)
        }
        TvClock(modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** One of the launcher's places on a TV: a hub, or the system's own settings. */
internal class TvPlace(
    val key: String,
    val title: String,
    val line: String?,
    val open: () -> Unit
)

@Composable
private fun tvPlaces(viewModel: HomeHubViewModel, onOpenHub: (HubType) -> Unit): List<TvPlace> {
    val context = LocalContext.current
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val weather by viewModel.weatherSnapshot.collectAsState()
    val playing = nowPlaying.takeIf { it.isPlaying && it.title.isNotBlank() }
        ?.let { listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · ") }
    val weatherLine = weather?.let { "${it.now.temperature.roundToInt()}° ${it.place.name}" }
    return listOf(
        TvPlace("videos", stringResource(R.string.music_tab_videos), null) {
            MusicBridge.openVideos()
            onOpenHub(HubType.MUSIC)
        },
        TvPlace("music", stringResource(R.string.zune_row_music), playing) { onOpenHub(HubType.MUSIC) },
        TvPlace("pictures", stringResource(HubType.PICTURES.titleRes), null) { onOpenHub(HubType.PICTURES) },
        TvPlace("weather", stringResource(HubType.WEATHER.titleRes), weatherLine) { onOpenHub(HubType.WEATHER) },
        TvPlace("files", stringResource(HubType.FILES.titleRes), null) { onOpenHub(HubType.FILES) },
        TvPlace("news", stringResource(HubType.NEWS.titleRes), null) { onOpenHub(HubType.NEWS) },
        TvPlace("settings", stringResource(R.string.settings_hub), null) { onOpenHub(HubType.SETTINGS) },
        TvPlace("android_settings", stringResource(R.string.tv_android_settings), null) {
            context.startSafely(Intent(Settings.ACTION_SETTINGS))
        }
    )
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
        TvSection(stringResource(R.string.browser_home), listOf(
            TvColumn.Big(byKey.getValue("videos")),
            TvColumn.Pair(byKey.getValue("music"), byKey.getValue("pictures")),
            TvColumn.Pair(byKey.getValue("weather"), byKey.getValue("news")),
            TvColumn.Pair(byKey.getValue("files"), null)
        )),
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
    LaunchedEffect(Unit) {
        delay(FIRST_FOCUS_DELAY_MILLIS)
        runCatching { firstTile.requestFocus() }
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
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
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
            width = BIG_TILE,
            height = BIG_TILE,
            onClick = column.place.open,
            modifier = firstFocus
        )
        is TvColumn.Pair -> Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            TvTile(column.top.title, column.top.line, accent.shade(0.8f), SMALL_TILE, SMALL_TILE, column.top.open, firstFocus)
            column.bottom?.let { TvTile(it.title, it.line, accent.shade(0.62f), SMALL_TILE, SMALL_TILE, it.open) }
        }
        is TvColumn.Apps -> {
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                column.apps.forEachIndexed { i, app ->
                    TvAppTile(app, BANNER_WIDTH, SMALL_TILE, if (i == 0) firstFocus else Modifier) {
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
        add(places.first { it.key == "videos" })
        add(places.first { it.key == "music" })
        add(places.first { it.key == "pictures" })
        add(TvPlace("apps", appsWord, null) {})
        addAll(places.filter { it.key in setOf("weather", "news", "files", "settings") })
    }
    var chosen by remember { mutableIntStateOf(0) }
    val firstWord = remember { FocusRequester() }
    val wordsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(FIRST_FOCUS_DELAY_MILLIS)
        runCatching { firstWord.requestFocus() }
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
                    modifier = if (index == 0) Modifier.focusRequester(firstWord) else Modifier
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
                        width = BANNER_WIDTH,
                        height = SMALL_TILE,
                        onClick = place.open
                    )
                }
            }
        }
    }
}

@Composable
private fun TvAppsPreview(apps: List<TvApp>) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        itemsIndexed(apps.chunked(2)) { _, pair ->
            Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                pair.forEach { app ->
                    TvAppTile(app, BANNER_WIDTH, SMALL_TILE, Modifier) { context.startSafely(app.launch) }
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
    image: ImageBitmap? = null
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width, height)
            .tvFocus(source)
            .background(color)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
        modifier = modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
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
private val SMALL_TILE = 170.dp
private val BANNER_WIDTH = 302.dp
private val TILE_GAP = 12.dp
private val SECTION_GAP = 64.dp
private val WORDS_WIDTH = 380.dp
private const val FOCUS_SCALE = 1.06f
private const val WORDS_ABOVE = 2
private const val FIRST_FOCUS_DELAY_MILLIS = 120L
private const val FOCUS_MILLIS = 140
