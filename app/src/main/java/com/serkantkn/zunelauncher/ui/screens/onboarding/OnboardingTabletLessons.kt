package com.serkantkn.zunelauncher.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.components.W10MHubTile
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubEndPadding
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubStartPadding
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWideNavHub
import com.serkantkn.zunelauncher.ui.components.getHubIcon
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.home.StartTileUIModel
import com.serkantkn.zunelauncher.ui.screens.home.TileSpan
import com.serkantkn.zunelauncher.ui.screens.home.Windows8StartScreen
import com.serkantkn.zunelauncher.ui.screens.home.win8SwipeReveal
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch

/**
 * The lessons on a tablet, played on the tablet's own Start.
 *
 * The phone's lessons teach a phone: three screens side by side, a pivot to swipe. A tablet is
 * moved about differently - the app list rises from under Start, the social page is pulled out
 * from its left edge, the board pinches down to its groups, and a hub keeps its pages as headings
 * down the left - so the tablet practises those. The board is the real one, laid out with the
 * real tiles, and as on the phone nothing on it opens anything.
 */

/** The tiles the rehearsal board is laid out with, in sizes that show what a board can hold. */
private val TABLET_TILES = listOf(
    StartTileUIModel.Hub(HubType.PEOPLE, TileSpan.WIDE),
    StartTileUIModel.Hub(HubType.PHONE, TileSpan.MEDIUM),
    StartTileUIModel.Hub(HubType.MESSAGING, TileSpan.MEDIUM),
    StartTileUIModel.Hub(HubType.PICTURES, TileSpan.LARGE),
    StartTileUIModel.Hub(HubType.MUSIC, TileSpan.WIDE),
    StartTileUIModel.Hub(HubType.EMAIL, TileSpan.MEDIUM),
    StartTileUIModel.Hub(HubType.CALENDAR, TileSpan.MEDIUM),
    StartTileUIModel.Hub(HubType.WEATHER, TileSpan.WIDE),
    StartTileUIModel.Hub(HubType.INTERNET, TileSpan.MEDIUM),
    StartTileUIModel.Hub(HubType.CAMERA, TileSpan.SMALL),
    StartTileUIModel.Hub(HubType.SETTINGS, TileSpan.SMALL),
    StartTileUIModel.Hub(HubType.CLOCK, TileSpan.SMALL),
    StartTileUIModel.Hub(HubType.FILES, TileSpan.SMALL)
)

/**
 * The stage and the band over its bottom. The stage is told how much of its bottom the band
 * covers, beyond the navigation bar it would have kept clear of anyway, so nothing it lays out
 * ends up under the band.
 */
@Composable
private fun TabletLessonStage(
    chrome: TourChrome,
    title: String,
    line: String,
    ticks: List<Pair<String, Boolean>>,
    stage: @Composable BoxScope.(bandReserve: Dp) -> Unit
) {
    val density = LocalDensity.current
    var bandPx by remember { mutableIntStateOf(0) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val reserve = (with(density) { bandPx.toDp() } - navBottom).coerceAtLeast(0.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        stage(reserve)
        TabletLessonBand(
            chrome = chrome,
            title = title,
            line = line,
            ticks = ticks,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bandPx = it.height }
        )
    }
}

/** A board tile for the rehearsal: the real hub tile, wired to nothing but the lesson. */
@Composable
private fun RehearsalTile(
    model: StartTileUIModel,
    isEditing: Boolean,
    isDragging: Boolean,
    modifier: Modifier,
    onLongClick: () -> Unit = {},
    onResize: () -> Unit = {}
) {
    val hub = (model as? StartTileUIModel.Hub)?.hubType ?: return
    W10MHubTile(
        hubType = hub,
        span = model.span,
        gridColumns = 4,
        isEditing = isEditing,
        isDragging = isDragging,
        onClick = { },
        onLongClick = onLongClick,
        onRemoveClick = { },
        onResizeClick = onResize,
        modifier = modifier
    )
}

// -- Start, the app list under it, and the social page beside it -------------

@Composable
fun TabletStartLesson(onCompleted: () -> Unit) {
    val chrome = LocalTourChrome.current ?: return
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    val pager = rememberPagerState(initialPage = 1, pageCount = { 2 })
    val reveal = remember { Animatable(0f) }
    var appsOpen by remember { mutableStateOf(false) }
    var sawApps by remember { mutableStateOf(false) }
    var sawSocial by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page -> if (page == 0) sawSocial = true }
    }
    LaunchedEffect(appsOpen) { if (appsOpen) sawApps = true }
    LaunchedEffect(sawApps, sawSocial) { if (sawApps && sawSocial) onCompleted() }

    TabletLessonStage(
        chrome = chrome,
        title = stringResource(R.string.onboarding_tstart_title),
        line = stringResource(R.string.onboarding_tstart_line),
        ticks = listOf(
            stringResource(R.string.onboarding_tstart_apps) to sawApps,
            stringResource(R.string.onboarding_tstart_social) to sawSocial
        )
    ) { reserve ->
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                TabletSideClone(stringResource(R.string.social_hub))
            } else {
                var heightPx by remember { mutableFloatStateOf(0f) }
                Box(modifier = Modifier.fillMaxSize().onSizeChanged { heightPx = it.height.toFloat() }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = -heightPx * reveal.value }
                            .win8SwipeReveal(
                                reveal = reveal,
                                heightPx = { heightPx },
                                opening = true,
                                enabled = true,
                                animate = animate,
                                onSettled = { open -> appsOpen = open }
                            )
                    ) {
                        Windows8StartScreen(
                            tiles = TABLET_TILES,
                            tileSpacing = 8.dp,
                            isEditMode = false,
                            onEnterEditMode = { },
                            onMoveTile = { _, _ -> },
                            onOpenApps = {
                                scope.launch {
                                    reveal.animateTo(1f, tween(if (animate) REVEAL_MS else 0))
                                    appsOpen = true
                                }
                            },
                            onOpenSettings = { },
                            onPullToSocial = { scope.launch { pager.animateScrollToPage(0) } },
                            bottomReserve = reserve,
                            tile = { model, _, isDragging, _, tileModifier ->
                                RehearsalTile(model, isEditing = false, isDragging = isDragging, modifier = tileModifier)
                            }
                        )
                    }
                    if (appsOpen || reveal.value > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = heightPx * (1f - reveal.value) }
                                .win8SwipeReveal(
                                    reveal = reveal,
                                    heightPx = { heightPx },
                                    opening = false,
                                    enabled = true,
                                    animate = animate,
                                    onSettled = { open -> appsOpen = open }
                                )
                        ) {
                            TabletAppsClone(bottomReserve = reserve)
                        }
                    }
                }
            }
        }
    }
}

/** The social page beside Start: its name across the top, and a few made-up messages under it. */
@Composable
private fun TabletSideClone(label: String) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 36.dp)) {
        ZuneWideHubTitle(text = label)
        SampleSocialFeed(
            modifier = Modifier
                .padding(start = ZuneWideHubStartPadding, end = ZuneWideHubEndPadding)
                .widthIn(max = 720.dp)
        )
    }
}

/**
 * The app list as Windows 8.1 laid it out: its name and the order it is in across the top, the
 * apps in columns under it, each beside a square of the accent.
 */
@Composable
private fun TabletAppsClone(bottomReserve: Dp) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    val hubs = remember { HubType.entries.filter { it != HubType.HOME } }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (zuneColors.isDark) Color(0xFF0A0A0A) else Color.White)
            .statusBarsPadding()
            .padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = navBottom + bottomReserve)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.apps_hub),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 42.sp),
                color = fg
            )
            Text(
                text = stringResource(R.string.win8_apps_by_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
                color = zuneColors.textMuted,
                modifier = Modifier.padding(start = 22.dp, bottom = 6.dp)
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.padding(top = 36.dp)
        ) {
            hubs.chunked(APPS_PER_COLUMN).forEach { column ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(240.dp)) {
                    column.forEach { hub ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(40.dp).background(zuneColors.accentColor)
                            ) {
                                Icon(getHubIcon(hub), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Text(
                                text = stringResource(hub.titleRes),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 19.sp),
                                color = fg,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -- Holding a tile, and pinching the board ---------------------------------

@Composable
fun TabletTileLesson(onCompleted: () -> Unit) {
    val chrome = LocalTourChrome.current ?: return
    var tiles by remember { mutableStateOf(TABLET_TILES) }
    var editing by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(held, zoomed) { if (held && zoomed) onCompleted() }

    TabletLessonStage(
        chrome = chrome,
        title = stringResource(R.string.onboarding_ttile_title),
        line = stringResource(R.string.onboarding_ttile_line),
        ticks = listOf(
            stringResource(R.string.onboarding_tile_try) to held,
            stringResource(R.string.onboarding_ttile_pinch) to zoomed
        )
    ) { reserve ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // A tap off the tiles lets go of them, as it does on Start.
                .clickable(
                    enabled = editing,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { editing = false }
        ) {
            Windows8StartScreen(
                tiles = tiles,
                tileSpacing = 8.dp,
                isEditMode = editing,
                onEnterEditMode = {
                    editing = true
                    held = true
                },
                onMoveTile = { from, to ->
                    if (from in tiles.indices && to in tiles.indices) {
                        tiles = tiles.toMutableList().apply { add(to, removeAt(from)) }
                    }
                },
                onOpenApps = { },
                onOpenSettings = { },
                bottomReserve = reserve,
                onZoomChange = { out -> if (out) zoomed = true },
                tile = { model, index, isDragging, _, tileModifier ->
                    RehearsalTile(
                        model = model,
                        isEditing = editing,
                        isDragging = isDragging,
                        modifier = tileModifier,
                        onLongClick = {
                            editing = true
                            held = true
                        },
                        onResize = {
                            // The size button goes round the sizes, as the real one does.
                            val next = TileSpan.ALL[(TileSpan.ALL.indexOf(model.span) + 1) % TileSpan.ALL.size]
                            tiles = tiles.toMutableList().also { list ->
                                (list[index] as? StartTileUIModel.Hub)?.let { list[index] = it.copy(span = next) }
                            }
                        }
                    )
                }
            )
        }
    }
}

// -- Inside a hub -----------------------------------------------------------

@Composable
fun TabletHubLesson(onCompleted: () -> Unit) {
    val chrome = LocalTourChrome.current ?: return
    val tabs = listOf(
        stringResource(R.string.onboarding_pivot_one),
        stringResource(R.string.onboarding_pivot_two),
        stringResource(R.string.onboarding_pivot_three)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    var moved by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page -> if (page != 0) moved = true }
    }
    LaunchedEffect(moved) { if (moved) onCompleted() }

    TabletLessonStage(
        chrome = chrome,
        title = stringResource(R.string.onboarding_pivot_title),
        line = stringResource(R.string.onboarding_thub_line),
        ticks = listOf(stringResource(R.string.onboarding_thub_try) to moved)
    ) { reserve ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ZuneWideNavHub(
            title = stringResource(R.string.onboarding_pivot_hub),
            tabs = tabs,
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = navBottom + reserve)
        ) { index ->
            // Today, what comes after it, and the agenda, drawn as the calendar draws them.
            SampleCalendarPage(page = index, modifier = Modifier.widthIn(max = 720.dp), bottomPadding = 24.dp)
        }
    }
}

private const val APPS_PER_COLUMN = 6
private const val REVEAL_MS = 420
