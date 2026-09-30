package com.serkantkn.zunelauncher.ui.screens.home

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.serkantkn.zunelauncher.ui.components.wpTilt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.animation.w10mEditWiggle
import com.serkantkn.zunelauncher.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.components.W10MAppTile
import com.serkantkn.zunelauncher.ui.components.LocalTileStyle
import com.serkantkn.zunelauncher.ui.components.LocalTileLook
import com.serkantkn.zunelauncher.ui.components.LocalTileCustomize
import com.serkantkn.zunelauncher.ui.components.LocalTileIconResolver
import com.serkantkn.zunelauncher.ui.components.LocalTileIsPreview
import com.serkantkn.zunelauncher.data.model.TileLook
import com.serkantkn.zunelauncher.data.repository.StartParallax
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.components.TileStyle
import com.serkantkn.zunelauncher.ui.components.W10MFolderTile
import com.serkantkn.zunelauncher.ui.components.W10MHubTile
import com.serkantkn.zunelauncher.ui.components.W10MMusicTile
import com.serkantkn.zunelauncher.ui.components.W10MPeopleTile
import com.serkantkn.zunelauncher.ui.components.W10MWeatherTile
import com.serkantkn.zunelauncher.ui.screens.weather.conditionIcon
import com.serkantkn.zunelauncher.ui.components.W10MCalendarTile
import com.serkantkn.zunelauncher.ui.components.W10MClockTile
import com.serkantkn.zunelauncher.ui.components.W10MNoteTile
import com.serkantkn.zunelauncher.ui.components.W10MPersonTile
import com.serkantkn.zunelauncher.ui.components.W10MThreadTile
import com.serkantkn.zunelauncher.ui.components.W10MAlbumTile
import com.serkantkn.zunelauncher.ui.components.W10MWebTile
import com.serkantkn.zunelauncher.data.repository.BrowserBridge
import com.serkantkn.zunelauncher.data.repository.PeopleBridge
import com.serkantkn.zunelauncher.data.repository.NotesBridge
import com.serkantkn.zunelauncher.data.repository.MusicBridge
import com.serkantkn.zunelauncher.data.repository.PicturesBridge
import com.serkantkn.zunelauncher.ui.components.ZuneClock
import com.serkantkn.zunelauncher.ui.components.ZuneDate
import com.serkantkn.zunelauncher.ui.components.ZuneHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWeather
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.toImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.serkantkn.zunelauncher.util.TileIconFace
import com.serkantkn.zunelauncher.ui.components.TileIconImage
import com.serkantkn.zunelauncher.data.model.AppInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.serkantkn.zunelauncher.data.model.TileIcon
import com.serkantkn.zunelauncher.ui.components.TileIconPicker

/**
 * Start. Everything below it draws tiles, so the tile look the user picked — how see-through the
 * tiles are and how their live faces move — is handed down from here.
 */
/** An open folder lays its tiles out on a four-cell board of its own. */
private const val FOLDER_COLUMNS = 4

/**
 * How many cells the start board is wide on a phone.
 *
 * Eight, and not a setting: Windows Phone's grid was eight small cells across, and the four-cell
 * board this used to offer as an alternative drew tiles too large to be worth the choice.
 */
private const val START_TILE_COLUMNS = 8

@Composable
fun HomeHubScreen(
    isHubOpen: Boolean = false,
    isCurrentPage: Boolean = true,
    onHubSelected: (HubType) -> Unit,
    onNavigateToSocialHub: () -> Unit = {},
    onNavigateToAppsHub: () -> Unit = {},
    onExpandProgressChange: (Float) -> Unit = {},
    /** Bumped when the Home key is pressed while Start is already in front. */
    homeResetSignal: Int = 0,
    runningHubs: List<HubType> = emptyList(),
    onStopHub: (HubType) -> Unit = {},
    onStopAllHubs: () -> Unit = {},
    timeFormat: String = "HH:mm",
    dateFormat: String = "EEEE, MMMM d",
    modifier: Modifier = Modifier,
    viewModel: HomeHubViewModel = viewModel()
) {
    val tileOpacity by viewModel.tileOpacity.collectAsState()
    val tileAnimation by viewModel.tileAnimation.collectAsState()
    val tileInk by viewModel.tileInk.collectAsState()
    CompositionLocalProvider(
        LocalTileStyle provides TileStyle(
            opacity = tileOpacity / 100f,
            animation = tileAnimation,
            ink = tileInk
        )
    ) {
        HomeHubScreenContent(
            isHubOpen = isHubOpen,
            isCurrentPage = isCurrentPage,
            onHubSelected = onHubSelected,
            onNavigateToSocialHub = onNavigateToSocialHub,
            onNavigateToAppsHub = onNavigateToAppsHub,
            onExpandProgressChange = onExpandProgressChange,
            homeResetSignal = homeResetSignal,
            runningHubs = runningHubs,
            onStopHub = onStopHub,
            onStopAllHubs = onStopAllHubs,
            timeFormat = timeFormat,
            dateFormat = dateFormat,
            modifier = modifier,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeHubScreenContent(
    isHubOpen: Boolean,
    isCurrentPage: Boolean,
    onHubSelected: (HubType) -> Unit,
    onNavigateToSocialHub: () -> Unit,
    onNavigateToAppsHub: () -> Unit,
    onExpandProgressChange: (Float) -> Unit,
    homeResetSignal: Int,
    runningHubs: List<HubType>,
    onStopHub: (HubType) -> Unit,
    onStopAllHubs: () -> Unit,
    timeFormat: String,
    dateFormat: String,
    modifier: Modifier,
    viewModel: HomeHubViewModel
) {
    val unifiedStartTilesFlow by viewModel.unifiedStartTiles.collectAsState()
    val favoriteAppsFlow by viewModel.favoriteApps.collectAsState()
    val hubOrderFlow by viewModel.hubOrder.collectAsState()
    val favoritePhotos by viewModel.favoritePhotoUris.collectAsState()
    val latestNotification by viewModel.latestNotification.collectAsState()
    val notificationCounts by viewModel.notificationCounts.collectAsState()
    val notesTileSubtitle by viewModel.notesTileSubtitle.collectAsState()
    val pinnedNotesCount by viewModel.pinnedNotesCount.collectAsState()
    val emailUnreadCount by viewModel.emailUnreadCount.collectAsState()
    val calculatorTileSubtitle by viewModel.calculatorTileSubtitle.collectAsState()
    val enabledAlarms by viewModel.enabledAlarms.collectAsState()
    val upcomingEvents by viewModel.upcomingEvents.collectAsState()

    // The phone's calendars are read afresh whenever Start comes back, so the takvim tile is not
    // showing what the diary looked like when the launcher happened to start.
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCalendarEvents()
    }
    val latestMessages by viewModel.latestMessages.collectAsState()
    val peopleFaces by viewModel.peopleFaces.collectAsState()
    val missedCalls by viewModel.missedCalls.collectAsState()
    val lastMissedCaller by viewModel.lastMissedCaller.collectAsState()
    val unreadMessages by viewModel.unreadMessages.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val emailTilePreview by viewModel.emailTilePreview.collectAsState()
    val weatherSnapshot by viewModel.weatherSnapshot.collectAsState()
    val weatherUnit by viewModel.weatherUnit.collectAsState()
    val tileCornerStyle by viewModel.tileCornerStyle.collectAsState()
    val tileSpacing by viewModel.tileSpacing.collectAsState()
    val homeScreenLayout by viewModel.homeScreenLayout.collectAsState()
    val taskSwitcherVisible by viewModel.taskSwitcherVisible.collectAsState()
    val hubPreviews by com.serkantkn.zunelauncher.data.repository.HubPreviewStore.previews.collectAsState()
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current

    // A tablet is about twice as wide as a phone, so twice the columns keeps a tile the same
    // size in the hand rather than making it twice as big.
    val gridColumns = if (isWideScreen) START_TILE_COLUMNS * 2 else START_TILE_COLUMNS

    // ── State ──
    var isEditMode by remember { mutableStateOf(false) }
    var isFavoritesExpanded by remember { mutableStateOf(false) }

    var localStartTiles by remember(unifiedStartTilesFlow) { mutableStateOf(unifiedStartTilesFlow) }
    var favoriteApps by remember(favoriteAppsFlow) { mutableStateOf(favoriteAppsFlow) }
    var localHubOrder by remember(hubOrderFlow) { mutableStateOf(hubOrderFlow) }

    LaunchedEffect(unifiedStartTilesFlow) {
        if (!isEditMode) {
            localStartTiles = unifiedStartTilesFlow
        }
    }

    val onRemoveTile: (String) -> Unit = { tileId ->
        val current = localStartTiles.filterNot { it.id == tileId }
        localStartTiles = current
        viewModel.removeTile(tileId)
    }

    BackHandler(enabled = isEditMode || isFavoritesExpanded) {
        if (isEditMode) isEditMode = false
        else isFavoritesExpanded = false
    }

    // ── Expand / Collapse animation ──
    // 0f = hub foreground (favorites collapsed), 1f = favorites foreground (hub collapsed)
    val expandProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isFavoritesExpanded) {
        expandProgress.animateTo(
            targetValue = if (isFavoritesExpanded) 1f else 0f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    // Keyed on Unit, not on the value: keying on the animation cancelled and restarted a
    // coroutine on every frame of the expansion, which is a lot of rubbish for a callback.
    LaunchedEffect(Unit) {
        snapshotFlow { expandProgress.value }.collect { onExpandProgressChange(it) }
    }

    // Exit edit mode when favorites collapse
    LaunchedEffect(isFavoritesExpanded) {
        if (!isFavoritesExpanded) isEditMode = false
    }

    // ── Staggered entrance animation ──
    val animationProgress = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasRunInitialAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasRunInitialAnimation) {
            hasRunInitialAnimation = true
            animationProgress.snapTo(0f)
            animationProgress.animateTo(1f, tween(1200, easing = LinearEasing))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refreshLiveTiles()
            if (event == Lifecycle.Event.ON_START && hasRunInitialAnimation) {
                coroutineScope.launch {
                    animationProgress.snapTo(0f)
                    animationProgress.animateTo(1f, tween(1200, easing = LinearEasing))
                }
            }
            if (event == Lifecycle.Event.ON_PAUSE) isEditMode = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var clickedItemKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            clickedItemKey = null
            animationProgress.snapTo(1f)
            isEditMode = false
            isFavoritesExpanded = false
        }
    }

    LaunchedEffect(isHubOpen) {
        if (!isHubOpen && animationProgress.value > 1f) {
            clickedItemKey = null
            animationProgress.snapTo(0f)
            animationProgress.animateTo(1f, tween(1200, easing = LinearEasing))
        }
    }

    fun handleLaunch(key: String, action: () -> Unit) {
        if (isEditMode) return
        clickedItemKey = key
        coroutineScope.launch {
            // Turnstile feather out (1f -> 2f). The launch itself fires slightly before the
            // chosen tile has fully turned away so the hub's hinge-in overlaps it, WP style.
            launch { animationProgress.animateTo(2f, tween(1000, easing = LinearEasing)) }
            delay(860)
            action()
        }
    }

    // ── Grid state for expanded favorites ──
    val gridState = rememberLazyGridState()
    // Hoisted: the Home key, pressed on a Start screen that is already in front, takes the board
    // back to its beginning.
    val hubsOffHome by viewModel.hubsOffHome.collectAsState()
    var addingHub by remember { mutableStateOf(false) }
    var previewTarget by remember { mutableStateOf<RunningHubTarget?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    val boardScrollState = rememberScrollState()
    val zuneScrollState = rememberScrollState()
    val win8ScrollState = rememberScrollState()
    val localDensity = LocalDensity.current

    val gridDragDropState = rememberGridDragDropState(
        gridState = gridState,
        isEditMode = isEditMode,
        canSwap = { _, _ -> true },
        onMove = { fromIndex, toIndex ->
            // Index 0 = "fav_label", tiles start at 1
            if (fromIndex >= 1 && toIndex >= 1) {
                val fromFavIndex = fromIndex - 1
                val toFavIndex = toIndex - 1
                val newList = favoriteApps.toMutableList()
                if (fromFavIndex < newList.size) {
                    val item = newList.removeAt(fromFavIndex)
                    val insertIndex = toFavIndex.coerceAtMost(newList.size)
                    newList.add(insertIndex, item)
                    favoriteApps = newList
                    viewModel.updateFavoritesOrder(newList)
                }
            }
        }
    )

    // ── Layout ──
    val p = expandProgress.value

    // Track the vertical offset where hub titles begin
    // so collapsed favorites can align their first tile with the first hub title.
    var hubTitlesTopPx by remember { mutableStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }

    val swipeGestureModifier = Modifier.pointerInput(isFavoritesExpanded, isEditMode) {
        if (isEditMode) return@pointerInput
        detectHorizontalDragGestures(
            onDragStart = { totalDragX = 0f },
            onDragEnd = {
                if (!isFavoritesExpanded) {
                    if (totalDragX > 40f) {
                        // First swipe right on main screen (finger left -> right) -> expand favorites!
                        isFavoritesExpanded = true
                    } else if (totalDragX < -40f) {
                        // Swipe left on main screen (finger right -> left) -> open Apps Hub (Page 2)!
                        onNavigateToAppsHub()
                    }
                } else {
                    if (totalDragX > 40f) {
                        // Second swipe right on expanded favorites (finger left -> right) -> open Social Hub (Page 0)!
                        onNavigateToSocialHub()
                    } else if (totalDragX < -40f) {
                        // Swipe left on expanded favorites (finger right -> left) -> collapse back to normal home screen!
                        isFavoritesExpanded = false
                    }
                }
            },
            onDragCancel = { totalDragX = 0f },
            onHorizontalDrag = { change, dragAmount ->
                totalDragX += dragAmount
                // Intercept drag events to control exact 2-stage expansion and navigation
                change.consume()
            }
        )
    }

    // ── Shared Start-tile plumbing for both Metro boards (phone grid + Windows 8 board) ──
    var resizeTargetId by remember { mutableStateOf<String?>(null) }
    // The tile whose own settings page is open, the one whose icon is being picked (over that
    // page), and the one a picture is being fetched for.
    var lookTargetId by remember { mutableStateOf<String?>(null) }
    var iconTargetId by remember { mutableStateOf<String?>(null) }
    var pictureTargetId by remember { mutableStateOf<String?>(null) }
    val tileLooks by viewModel.tileLooks.collectAsState()

    // A hub whose tap has been pointed at another app opens that app; the rest open themselves.
    val hubTargetApps by viewModel.hubTargetApps.collectAsState()
    val quickplay by viewModel.quickplay.collectAsState()
    val allImages by viewModel.allImages.collectAsState()
    val animationsOn = LocalAnimationsEnabled.current

    // The Zune list's wallpaper drifts a little behind the list, and only there.
    LaunchedEffect(homeScreenLayout, isWideScreen, animationsOn) {
        if (homeScreenLayout == HomeScreenLayout.ZUNE && !isWideScreen && animationsOn) {
            snapshotFlow { zuneScrollState.value }.collect { StartParallax.set(it * StartParallax.FACTOR) }
        } else {
            StartParallax.set(0f)
        }
    }

    /**
     * What a hub says under its name in the Zune list — the same facts its tile turns over to
     * show, in a line of words. A hub whose tile was told to keep quiet keeps quiet here too.
     */
    @Composable
    fun liveLineFor(hub: HubType): HubLiveLine {
        val look = tileLooks["hub:${hub.name}"] ?: TileLook.DEFAULT
        val quiet = !look.notifications
        return when (hub) {
            HubType.MESSAGING -> {
                val first = unreadMessages.firstOrNull()
                val count = maxOf(unreadMessages.size, notificationCounts["com.google.android.apps.messaging"] ?: 0)
                if (quiet) HubLiveLine.NONE else HubLiveLine(
                    text = first?.let { "${it.contactName.ifBlank { it.address }}: ${it.snippet}" },
                    count = count
                )
            }
            HubType.PHONE -> if (quiet) HubLiveLine.NONE else HubLiveLine(
                text = lastMissedCaller?.let { "$it · ${stringResource(R.string.zune_line_missed)}" },
                count = maxOf(missedCalls, notificationCounts["com.google.android.dialer"] ?: 0)
            )
            HubType.EMAIL -> if (quiet) HubLiveLine.NONE else HubLiveLine(
                text = emailTilePreview?.let { (from, subject) -> if (subject.isBlank()) from else "$from: $subject" },
                count = emailUnreadCount
            )
            HubType.MUSIC -> HubLiveLine(
                text = nowPlaying.takeIf { it.isPlaying && it.title.isNotBlank() }?.let {
                    "${it.title}${if (it.artist.isNotBlank()) " — ${it.artist}" else ""} · ${stringResource(R.string.zune_line_playing)}"
                }
            )
            HubType.CALENDAR -> HubLiveLine(
                text = upcomingEvents.firstOrNull()?.let { "${it.formattedTime} ${it.title}" }
            )
            HubType.CLOCK -> {
                val now = System.currentTimeMillis()
                val next = enabledAlarms.map { it.nextTriggerMillis(now) }.filter { it > now }.minOrNull()
                HubLiveLine(
                    text = next?.let {
                        stringResource(
                            R.string.zune_line_alarm,
                            java.text.SimpleDateFormat(
                                if (timeFormat.contains("H")) "HH:mm" else "h:mm a", java.util.Locale.getDefault()
                            ).format(java.util.Date(it))
                        )
                    }
                )
            }
            HubType.WEATHER -> HubLiveLine(
                text = weatherSnapshot?.let {
                    "${weatherUnit.of(it.now.temperature)}° ${stringResource(it.now.condition.labelRes)} · ${it.place.name}"
                }
            )
            HubType.NOTES -> HubLiveLine(
                text = notesTileSubtitle,
                count = if (quiet) 0 else pinnedNotesCount
            )
            HubType.PICTURES -> {
                val dayStart = remember { java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis }
                // The store keeps DATE_ADDED in seconds.
                val today = allImages.count { it.dateAdded * 1000L >= dayStart }
                HubLiveLine(text = if (today > 0) pluralStringResource(R.plurals.zune_line_new_photos, today, today) else null)
            }
            else -> HubLiveLine.NONE
        }
    }
    var hubTargetPickFor by remember { mutableStateOf<HubType?>(null) }
    val openHub: (HubType) -> Unit = { hub -> if (!viewModel.openHubTarget(hub)) onHubSelected(hub) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    val openFolder = localStartTiles.firstOrNull { it.id == openFolderId } as? StartTileUIModel.Folder

    LaunchedEffect(runningHubs) {
        if (previewTarget?.hub !in runningHubs) previewTarget = null
    }

    // The Home key on a Start screen that is already in front: back to the top, with whatever
    // was opened over it — edit mode, the favourites pane, an open folder — put away first.
    LaunchedEffect(homeResetSignal) {
        if (homeResetSignal == 0) return@LaunchedEffect
        isEditMode = false
        isFavoritesExpanded = false
        openFolderId = null
        launch { boardScrollState.animateScrollTo(0) }
        launch { zuneScrollState.animateScrollTo(0) }
        launch { win8ScrollState.animateScrollTo(0) }
        launch { gridState.animateScrollToItem(0) }
    }

    // A folder that has been emptied (or whose last tile was taken out) closes itself.
    LaunchedEffect(openFolderId, localStartTiles) {
        if (openFolderId != null && openFolder == null) openFolderId = null
    }

    /** Folders never nest, so a folder being dragged can not be dropped into anything. */
    val canMerge: (String, String) -> Boolean = { sourceId, targetId ->
        val source = localStartTiles.firstOrNull { it.id == sourceId }
        val target = localStartTiles.firstOrNull { it.id == targetId }
        source != null && source !is StartTileUIModel.Folder && target != null
    }

    val onMergeTiles: (String, String) -> Unit = { sourceId, targetId ->
        val source = localStartTiles.firstOrNull { it.id == sourceId }
        if (source !is StartTileUIModel.Folder) {
            openFolderId = null
            viewModel.mergeIntoFolder(sourceId, targetId)
        }
    }

    // The open folder answers the back gesture before edit mode does.
    BackHandler(enabled = openFolderId != null) { openFolderId = null }

    val onSetTileSize: (String, Int) -> Unit = { tileId, newSpan ->
        val current = localStartTiles.toMutableList()
        val index = current.indexOfFirst { it.id == tileId }
        if (index == -1 && tileId.startsWith("app:")) {
            // Zune layout: the tile lives in the favourites strip, not on the Start board.
            val packageName = tileId.removePrefix("app:")
            val updated = favoriteApps.map { fav ->
                if (fav.appInfo.packageName == packageName) FavoriteAppUIModel(fav.appInfo, newSpan) else fav
            }
            favoriteApps = updated
            viewModel.updateFavoritesOrder(updated)
        }
        if (index != -1) {
            val old = current[index]
            current[index] = when (old) {
                is StartTileUIModel.Hub -> StartTileUIModel.Hub(old.hubType, newSpan)
                is StartTileUIModel.App -> StartTileUIModel.App(old.appInfo, newSpan)
                is StartTileUIModel.NoteTile -> StartTileUIModel.NoteTile(old.note, newSpan)
                is StartTileUIModel.QuickNote -> StartTileUIModel.QuickNote(newSpan)
                is StartTileUIModel.Web -> old.copy(span = newSpan)
            is StartTileUIModel.Album -> old.copy(span = newSpan)
            is StartTileUIModel.MusicAlbum -> old.copy(span = newSpan)
                is StartTileUIModel.Person -> old.copy(span = newSpan)
                is StartTileUIModel.Thread -> old.copy(span = newSpan)
                is StartTileUIModel.Folder -> old.copy(span = newSpan)
            }
            localStartTiles = current
            viewModel.updateStartTilesOrder(current)
        }
    }

    fun moveStartTile(from: Int, to: Int) {
        val newList = localStartTiles.toMutableList()
        if (from in newList.indices && to in newList.indices) {
            val item = newList.removeAt(from)
            newList.add(to, item)
            localStartTiles = newList
            viewModel.updateStartTilesOrder(newList)
        }
    }

    // Collected here, not inside the tiles: this is what makes the board redraw when the icon
    // style, the icon pack or a picked icon changes.
    val tileIconStyle by viewModel.tileIconStyle.collectAsState()
    val tileIconPack by viewModel.iconPackPackage.collectAsState()
    val tileIconOverrides by viewModel.tileIconOverrides.collectAsState()
    val tileFace: (AppInfo) -> TileIconFace = { app ->
        viewModel.tileIconFace(app, tileIconStyle, tileIconPack, tileIconOverrides)
    }

    val tileIconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = iconTargetId
        if (uri != null && target != null) viewModel.setTileLookIconPicture(target, uri)
        iconTargetId = null
    }
    val tilePicturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = pictureTargetId
        if (uri != null && target != null) viewModel.setTileLookPicture(target, uri)
        pictureTargetId = null
    }

    /** What a tile is called, for the heading of the pages that are about it. */
    @Composable
    fun tileSubject(model: StartTileUIModel): String = when (model) {
        is StartTileUIModel.Hub -> stringResource(model.hubType.titleRes)
        is StartTileUIModel.App -> model.appInfo.label
        is StartTileUIModel.NoteTile -> model.note.title.ifBlank { stringResource(HubType.NOTES.titleRes) }
        is StartTileUIModel.QuickNote -> stringResource(R.string.notes_quick_note)
        is StartTileUIModel.Web -> model.label
        is StartTileUIModel.Album -> model.name.ifBlank { stringResource(R.string.pics_unsorted_album) }
        is StartTileUIModel.MusicAlbum -> model.name.ifBlank { stringResource(R.string.music_unknown_album) }
        is StartTileUIModel.Person -> model.label
        is StartTileUIModel.Thread -> model.label
        is StartTileUIModel.Folder -> model.name.ifBlank { stringResource(R.string.start_folder_default_name) }
    }

    /** A tile by id, wherever it lives: on the board, inside a folder, or in the favourites strip. */
    fun tileModel(id: String): StartTileUIModel? =
        localStartTiles.firstOrNull { it.id == id }
            ?: localStartTiles.filterIsInstance<StartTileUIModel.Folder>()
                .flatMap { it.children }.firstOrNull { it.id == id }
            ?: favoriteApps.firstOrNull { "app:${it.appInfo.packageName}" == id }
                ?.let { StartTileUIModel.App(it.appInfo, it.span) }

    /** The page over the customise page: the glyph, icon-pack drawable or picture this tile shows. */
    @Composable
    fun TileIconPopup() {
        val target = iconTargetId ?: return
        val model = tileModel(target)
        val packDrawables = remember(tileIconPack) {
            tileIconPack?.let { viewModel.iconPackDrawables(it) }.orEmpty()
        }
        val look = tileLooks[target] ?: TileLook.DEFAULT
        TileIconPicker(
            subject = model?.let { tileSubject(it) } ?: "",
            current = look.icon,
            packPackage = tileIconPack,
            packDrawables = packDrawables,
            packPreview = { name ->
                tileIconPack?.let { viewModel.iconPackPreview(it, name) }
            },
            onPick = { icon ->
                viewModel.setTileLook(target, look.copy(icon = icon))
                iconTargetId = null
            },
            onPickPicture = {
                tileIconPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { iconTargetId = null }
        )
    }

    /** The resize button's popup: pick 1x1 / 2x2 / 2x4 / 4x4 for the tile being edited. */
    @Composable
    fun TileSizePopup() {
        val targetId = resizeTargetId ?: return
        TileSizeDialog(
            currentSpan = localStartTiles.firstOrNull { it.id == targetId }?.span
                ?: favoriteApps.firstOrNull { "app:${it.appInfo.packageName}" == targetId }?.span
                ?: TileSpan.MEDIUM,
            onSelect = { span ->
                onSetTileSize(targetId, span)
                resizeTargetId = null
            },
            onDismiss = { resizeTargetId = null }
        )
    }

    /** One Start tile: hub (with badge / live face), favourite app, note, or folder. */
    @Composable
    fun StartTile(
        model: StartTileUIModel,
        index: Int,
        isDragging: Boolean,
        isMergeTarget: Boolean,
        tileModifier: Modifier,
        gridColumns: Int
    ) {
        val launchKey = when (model) {
            is StartTileUIModel.Hub -> "hub_${model.hubType.name}"
            is StartTileUIModel.App -> "app_${model.appInfo.packageName}"
            is StartTileUIModel.NoteTile -> "note_${model.note.id}"
            is StartTileUIModel.QuickNote -> "note_new"
            is StartTileUIModel.Web -> "web_${model.url}"
            is StartTileUIModel.Album -> "album_${model.bucketId}"
            is StartTileUIModel.MusicAlbum -> "record_${model.albumId}"
            is StartTileUIModel.Person -> "person_${model.contactId}"
            is StartTileUIModel.Thread -> "sms_${model.threadId}"
            is StartTileUIModel.Folder -> "folder_${model.id}"
        }
        val animated = tileModifier.w10mStaggeredAnimation(
            progress = { animationProgress.value },
            index = index,
            isClicked = clickedItemKey == launchKey
        )
        val onResize = { resizeTargetId = model.id }
        val look = tileLooks[model.id] ?: TileLook.DEFAULT
        val preview = LocalTileIsPreview.current
        CompositionLocalProvider(
            LocalTileLook provides look,
            // The preview on the customise page is not a way into another customise page.
            LocalTileCustomize provides if (preview) null else ({ lookTargetId = model.id })
        ) {
        when (model) {
            is StartTileUIModel.Hub -> {
                val hubType = model.hubType
                when (hubType) {
                    HubType.CLOCK -> W10MClockTile(
                        span = model.span,
                        gridColumns = gridColumns,
                        spacing = tileSpacing.dp,
                        isEditing = isEditMode,
                        isDragging = isDragging,
                        cornerStyle = tileCornerStyle,
                        timeFormat = timeFormat,
                        alarms = enabledAlarms,
                        onClick = { handleLaunch(launchKey) { openHub(hubType) } },
                        onLongClick = { isEditMode = true },
                        onRemoveClick = { onRemoveTile(model.id) },
                        onResizeClick = onResize,
                        modifier = animated
                    )
                    HubType.CALENDAR -> W10MCalendarTile(
                        span = model.span,
                        gridColumns = gridColumns,
                        spacing = tileSpacing.dp,
                        isEditing = isEditMode,
                        isDragging = isDragging,
                        cornerStyle = tileCornerStyle,
                        events = upcomingEvents,
                        onClick = { handleLaunch(launchKey) { openHub(hubType) } },
                        onLongClick = { isEditMode = true },
                        onRemoveClick = { onRemoveTile(model.id) },
                        onResizeClick = onResize,
                        modifier = animated
                    )
                    HubType.PEOPLE -> W10MPeopleTile(
                        span = model.span,
                        gridColumns = gridColumns,
                        spacing = tileSpacing.dp,
                        isEditing = isEditMode,
                        isDragging = isDragging,
                        cornerStyle = tileCornerStyle,
                        contacts = peopleFaces,
                        onClick = { handleLaunch(launchKey) { openHub(hubType) } },
                        onLongClick = { isEditMode = true },
                        onRemoveClick = { onRemoveTile(model.id) },
                        onResizeClick = onResize,
                        modifier = animated
                    )
                    HubType.WEATHER -> W10MWeatherTile(
                        snapshot = weatherSnapshot,
                        temperatureUnit = weatherUnit,
                        span = model.span,
                        gridColumns = gridColumns,
                        spacing = tileSpacing.dp,
                        isEditing = isEditMode,
                        isDragging = isDragging,
                        cornerStyle = tileCornerStyle,
                        onClick = { handleLaunch(launchKey) { openHub(hubType) } },
                        onLongClick = { isEditMode = true },
                        onRemoveClick = { onRemoveTile(model.id) },
                        onResizeClick = onResize,
                        modifier = animated
                    )
                    HubType.MUSIC -> W10MMusicTile(
                        span = model.span,
                        gridColumns = gridColumns,
                        spacing = tileSpacing.dp,
                        isEditing = isEditMode,
                        isDragging = isDragging,
                        cornerStyle = tileCornerStyle,
                        albumArt = nowPlaying.albumArt,
                        trackTitle = nowPlaying.title,
                        trackArtist = nowPlaying.artist,
                        onClick = { handleLaunch(launchKey) { openHub(hubType) } },
                        onLongClick = { isEditMode = true },
                        onRemoveClick = { onRemoveTile(model.id) },
                        onResizeClick = onResize,
                        modifier = animated
                    )
                    else -> W10MHubTile(
                        hubType = hubType,
                        span = model.span,
                        gridColumns = gridColumns,
                        spacing = tileSpacing.dp,
                        photoUris = favoritePhotos,
                        isEditing = isEditMode,
                        isDragging = isDragging,
                        cornerStyle = tileCornerStyle,
                        badgeCount = if (!look.notifications) 0 else when (hubType) {
                            HubType.MESSAGING -> maxOf(unreadMessages.size, notificationCounts["com.google.android.apps.messaging"] ?: 0)
                            HubType.PHONE -> maxOf(missedCalls, notificationCounts["com.google.android.dialer"] ?: 0)
                            HubType.NOTES -> pinnedNotesCount
                            HubType.EMAIL -> emailUnreadCount
                            else -> 0
                        },
                        liveTitle = if (!look.notifications) null else when (hubType) {
                            HubType.MESSAGING -> unreadMessages.firstOrNull()?.let { it.contactName.ifBlank { it.address } }
                            HubType.PHONE -> lastMissedCaller
                            HubType.EMAIL -> emailTilePreview?.first
                            else -> null
                        },
                        // Notes and the calculator are the tile's own words, not notifications,
                        // so they stay when notifications are switched off for the tile.
                        liveSubtitle = when (hubType) {
                            HubType.NOTES -> notesTileSubtitle
                            HubType.CALCULATOR -> calculatorTileSubtitle
                            HubType.MESSAGING -> if (look.notifications) unreadMessages.firstOrNull()?.snippet else null
                            HubType.PHONE -> if (look.notifications) missedCalls.takeIf { it > 0 }
                                ?.let { pluralStringResource(R.plurals.tile_missed_calls, it, it) } else null
                            HubType.EMAIL -> if (look.notifications) emailTilePreview?.second else null
                            else -> null
                        },
                        onClick = { handleLaunch(launchKey) { openHub(hubType) } },
                        onLongClick = { isEditMode = true },
                        onRemoveClick = { onRemoveTile(model.id) },
                        onResizeClick = onResize,
                        modifier = animated
                    )
                }
            }
            is StartTileUIModel.App -> W10MAppTile(
                label = model.appInfo.label,
                tileKey = model.appInfo.packageName,
                face = tileFace(model.appInfo),
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
                isMergeTarget = isMergeTarget,
                cornerStyle = tileCornerStyle,
                notificationCount = notificationCounts[model.appInfo.packageName] ?: 0,
                notificationTitle = latestMessages[model.appInfo.packageName]?.title,
                notificationText = latestMessages[model.appInfo.packageName]?.text,
                onClick = { handleLaunch(launchKey) { viewModel.launchApp(model.appInfo.packageName) } },
                onLongClick = { isEditMode = true },
                onRemoveClick = { onRemoveTile(model.id) },
                onResizeClick = onResize,
                modifier = animated
            )
            is StartTileUIModel.Folder -> W10MFolderTile(
                folder = model,
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
                isMergeTarget = isMergeTarget,
                isOpen = openFolderId == model.id,
                cornerStyle = tileCornerStyle,
                appFace = tileFace,
                onClick = { openFolderId = if (openFolderId == model.id) null else model.id },
                onLongClick = { isEditMode = true },
                onRemoveClick = { viewModel.dissolveFolder(model.id) },
                onResizeClick = onResize,
                modifier = animated
            )
            is StartTileUIModel.Person -> W10MPersonTile(
                label = model.label,
                photoUri = model.contact?.photoUri?.toString(),
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
                isMergeTarget = isMergeTarget,
                cornerStyle = tileCornerStyle,
                onClick = {
                    handleLaunch(launchKey) {
                        PeopleBridge.open(model.contactId)
                        onHubSelected(HubType.PEOPLE)
                    }
                },
                onLongClick = { isEditMode = true },
                onRemoveClick = { onRemoveTile(model.id) },
                onResizeClick = onResize,
                modifier = animated
            )

            is StartTileUIModel.Thread -> W10MThreadTile(
                label = model.label,
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
                isMergeTarget = isMergeTarget,
                cornerStyle = tileCornerStyle,
                onClick = {
                    handleLaunch(launchKey) {
                        com.serkantkn.zunelauncher.data.repository.MessagingBridge.openThread(model.threadId)
                        onHubSelected(HubType.MESSAGING)
                    }
                },
                onLongClick = { isEditMode = true },
                onRemoveClick = { onRemoveTile(model.id) },
                onResizeClick = onResize,
                modifier = animated
            )

            is StartTileUIModel.MusicAlbum -> W10MAlbumTile(
                label = model.name.ifBlank { stringResource(R.string.music_unknown_album) },
                photoUris = listOfNotNull(model.artUri),
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
                isMergeTarget = isMergeTarget,
                cornerStyle = tileCornerStyle,
                onClick = {
                    handleLaunch(launchKey) {
                        MusicBridge.playAlbum(model.albumId)
                        onHubSelected(HubType.MUSIC)
                    }
                },
                onLongClick = { isEditMode = true },
                onRemoveClick = { onRemoveTile(model.id) },
                onResizeClick = onResize,
                modifier = animated
            )

            is StartTileUIModel.Album -> W10MAlbumTile(
                label = model.name.ifBlank { stringResource(R.string.pics_unsorted_album) },
                photoUris = model.covers,
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
                isMergeTarget = isMergeTarget,
                cornerStyle = tileCornerStyle,
                onClick = {
                    handleLaunch(launchKey) {
                        PicturesBridge.openAlbum(model.bucketId)
                        onHubSelected(HubType.PICTURES)
                    }
                },
                onLongClick = { isEditMode = true },
                onRemoveClick = { onRemoveTile(model.id) },
                onResizeClick = onResize,
                modifier = animated
            )

            is StartTileUIModel.Web -> W10MWebTile(
                label = model.label,
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
                isMergeTarget = isMergeTarget,
                cornerStyle = tileCornerStyle,
                onClick = {
                    handleLaunch(launchKey) {
                        BrowserBridge.open(model.url)
                        onHubSelected(HubType.INTERNET)
                    }
                },
                onLongClick = { isEditMode = true },
                onRemoveClick = { onRemoveTile(model.id) },
                onResizeClick = onResize,
                modifier = animated
            )

            is StartTileUIModel.NoteTile, is StartTileUIModel.QuickNote -> {
                val note = (model as? StartTileUIModel.NoteTile)?.note
                W10MNoteTile(
                    note = note,
                    span = model.span,
                    gridColumns = gridColumns,
                    spacing = tileSpacing.dp,
                    isEditing = isEditMode,
                    isDragging = isDragging,
                    cornerStyle = tileCornerStyle,
                    onClick = {
                        handleLaunch(launchKey) {
                            if (note != null) NotesBridge.open(note.id) else NotesBridge.newNote()
                            onHubSelected(HubType.NOTES)
                        }
                    },
                    onLongClick = { isEditMode = true },
                    onRemoveClick = { onRemoveTile(model.id) },
                    onResizeClick = onResize,
                    modifier = animated
                )
            }
        }
        }
    }

    /** The page over the customise page (or the Zune list) choosing what a hub opens. */
    @Composable
    fun HubTargetPopup() {
        val hub = hubTargetPickFor ?: return
        val apps by viewModel.allApps.collectAsState()
        HubTargetPicker(
            hub = hub,
            apps = apps,
            current = hubTargetApps[hub],
            face = tileFace,
            onPick = { pkg ->
                viewModel.setHubTargetApp(hub, pkg)
                hubTargetPickFor = null
            },
            onDismiss = { hubTargetPickFor = null }
        )
    }

    /** The brush button's page: everything one tile can be told to do differently. */
    @Composable
    fun TileLookPopup() {
        val target = lookTargetId ?: return
        val model = tileModel(target) ?: run {
            lookTargetId = null
            return
        }
        val look = tileLooks[target] ?: TileLook.DEFAULT
        TileLookPage(
            subject = tileSubject(model),
            look = look,
            boardOpacity = (LocalTileStyle.current.opacity * 100).toInt(),
            onChange = { viewModel.setTileLook(target, it) },
            onPickIcon = { iconTargetId = target },
            onPickPicture = {
                pictureTargetId = target
                tilePicturePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearPicture = { viewModel.clearTileLookPicture(target) },
            onReset = {
                viewModel.resetTileLook(target)
                (model as? StartTileUIModel.Hub)?.let { viewModel.setHubTargetApp(it.hubType, null) }
            },
            onDismiss = { lookTargetId = null },
            openTargetLabel = (model as? StartTileUIModel.Hub)?.let { hubModel ->
                hubTargetApps[hubModel.hubType]?.let { pkg ->
                    favoriteApps.firstOrNull { it.appInfo.packageName == pkg }?.appInfo?.label
                        ?: viewModel.allApps.value.firstOrNull { it.packageName == pkg }?.label
                        ?: pkg
                }
            },
            onPickOpenTarget = (model as? StartTileUIModel.Hub)?.let { hubModel ->
                { hubTargetPickFor = hubModel.hubType }
            },
            preview = {
                CompositionLocalProvider(LocalTileIsPreview provides true) {
                    StartTile(model, 0, false, false, Modifier, gridColumns = 4)
                }
            }
        )
    }

    // Any icon a tile was given is turned into a face here, once, for every tile on the screen.
    val iconResolver: (TileIcon) -> TileIconFace? = remember(tileIconStyle) {
        { icon -> viewModel.customIconFace(icon, tileIconStyle) }
    }

    // One box over all three Start layouts, so the preview balloon has a single place to live
    // and a single origin to measure the held tile against.
    CompositionLocalProvider(LocalTileIconResolver provides iconResolver) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOrigin = it.boundsInRoot().topLeft }
    ) {
        if (homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE && isWideScreen) {
            // ═══════════════════════════════════════════════
            // TABLET — Windows 8 full-screen Start menu
            // ═══════════════════════════════════════════════
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .clickable(
                        enabled = isEditMode,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        isEditMode = false
                    }
            ) {
                Windows8StartScreen(
                    tiles = localStartTiles,
                    tileSpacing = tileSpacing.dp,
                    isEditMode = isEditMode,
                    scrollState = win8ScrollState,
                    onEnterEditMode = { isEditMode = true },
                    onMoveTile = { from, to -> moveStartTile(from, to) },
                    onOpenApps = onNavigateToAppsHub,
                    onOpenSettings = { handleLaunch("hub_SETTINGS") { onHubSelected(HubType.SETTINGS) } },
                    onMergeTiles = onMergeTiles,
                    canMerge = canMerge,
                    tile = { model, index, isDragging, isMergeTarget, tileModifier ->
                        StartTile(model, index, isDragging, isMergeTarget, tileModifier, gridColumns = 4)
                    }
                )

                TileSizePopup()
                TileLookPopup()
                TileIconPopup()
                HubTargetPopup()

                StartFolderPanel(
                    folder = openFolder,
                    isEditMode = isEditMode,
                    columns = FOLDER_COLUMNS,
                    gap = tileSpacing.dp,
                    onEnterEditMode = { isEditMode = true },
                    onRename = { name -> openFolderId?.let { viewModel.renameFolder(it, name) } },
                    onTakeOut = { childId ->
                        openFolderId?.let { viewModel.removeFromFolder(it, childId) }
                    },
                    onMoveChild = { from, to ->
                        openFolderId?.let { viewModel.moveWithinFolder(it, from, to) }
                    },
                    onClose = { openFolderId = null },
                    tile = { model, index, isDragging, isMergeTarget, tileModifier ->
                        StartTile(model, index, isDragging, isMergeTarget, tileModifier, gridColumns = FOLDER_COLUMNS)
                    }
                )

                // The tablet board scrolls sideways, so the strip and the add button are laid
                // over its bottom-left corner rather than under the last row.
                if (isEditMode) {
                    AddHubButton(
                        missing = hubsOffHome,
                        onClick = { addingHub = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 32.dp, bottom = 150.dp)
                    )
                }
                RunningHubsSection(
                    hubs = if (taskSwitcherVisible) runningHubs else emptyList(),
                    cornerStyle = tileCornerStyle,
                    onOpen = { hub -> handleLaunch("running_$hub") { onHubSelected(hub) } },
                    onStop = onStopHub,
                    onStopAll = onStopAllHubs,
                    onPreview = { previewTarget = it },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 32.dp, bottom = 24.dp)
                        .width(320.dp)
                )

                StartEditBar(
                    visible = isEditMode && openFolder == null,
                    onDone = { isEditMode = false },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

            }
        } else if (homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE) {
            val swipeGestureModifierWp = Modifier.pointerInput(isEditMode) {
                if (isEditMode) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (totalDragX > 40f) {
                            onNavigateToSocialHub()
                        } else if (totalDragX < -40f) {
                            onNavigateToAppsHub()
                        }
                    },
                    onDragCancel = { totalDragX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDragX += dragAmount
                        change.consume()
                    }
                )
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .then(swipeGestureModifierWp)
                    .clickable(
                        enabled = isEditMode,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        isEditMode = false
                    }
            ) {
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                MetroStartBoard(
                    tiles = localStartTiles,
                    columns = gridColumns,
                    gap = tileSpacing.dp,
                    contentPadding = PaddingValues(
                        start = if (isWideScreen) 32.dp else 12.dp,
                        end = if (isWideScreen) 32.dp else 12.dp,
                        top = statusBarPadding + 16.dp,
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + 24.dp
                    ),
                    scrollState = boardScrollState,
                    footer = {
                        if (isEditMode) {
                            AddHubButton(missing = hubsOffHome, onClick = { addingHub = true })
                        }
                        RunningHubsSection(
                            hubs = if (taskSwitcherVisible) runningHubs else emptyList(),
                            cornerStyle = tileCornerStyle,
                            onOpen = { hub -> handleLaunch("running_$hub") { onHubSelected(hub) } },
                            onStop = onStopHub,
                            onStopAll = onStopAllHubs,
                            onPreview = { previewTarget = it }
                        )
                    },
                    onEnterEditMode = { isEditMode = true },
                    onMoveTile = { from, to -> moveStartTile(from, to) },
                    onMergeTiles = onMergeTiles,
                    canMerge = canMerge,
                    // The folder does not open over the board: it unfolds into it.
                    openFolder = openFolder,
                    isEditMode = isEditMode,
                    onFolderRename = { name -> openFolderId?.let { viewModel.renameFolder(it, name) } },
                    onFolderTakeOut = { childId ->
                        openFolderId?.let { viewModel.removeFromFolder(it, childId) }
                    },
                    onFolderMoveChild = { from, to ->
                        openFolderId?.let { viewModel.moveWithinFolder(it, from, to) }
                    },
                    tile = { model, index, isDragging, isMergeTarget, tileModifier ->
                        StartTile(model, index, isDragging, isMergeTarget, tileModifier, gridColumns = gridColumns)
                    }
                )

                TileSizePopup()
                TileLookPopup()
                TileIconPopup()
                HubTargetPopup()

                StartEditBar(
                    visible = isEditMode,
                    onDone = { isEditMode = false },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

            }
        } else {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .then(swipeGestureModifier)
                    .clickable(
                        enabled = isEditMode,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        isEditMode = false
                    }
            ) {

                // ════════════════════════════════════════════════
                // LAYER 1 — Hub Section (Clock, Date, Weather, Hub Titles)
                //
                // Always laid out at FULL screen width so text/clock
                // has room to measure properly. graphicsLayer slides
                // it right and fades it when favorites expand.
                // ════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .then(
                            if (isWideScreen) {
                                Modifier.fillMaxWidth(0.35f).fillMaxHeight().align(Alignment.CenterStart)
                            } else {
                                Modifier.fillMaxSize()
                            }
                        )
                        .verticalScroll(zuneScrollState)
                        .padding(
                            start = 72.dp,
                            end = ZuneDimens.ScreenPaddingHorizontal
                        )
                        .graphicsLayer {
                            if (!isWideScreen) {
                                translationX = size.width * 0.7f * p
                                val scale = lerp(1f, 0.75f, p)
                                scaleX = scale
                                scaleY = scale
                                alpha = lerp(1f, 0.3f, p)
                                rotationY = lerp(0f, 25f, p)
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                cameraDistance = 12f * density
                            }
                        }
                ) {
                    Spacer(modifier = Modifier.height(80.dp))

                    ZuneClock(
                        timeFormat = timeFormat,
                        onClick = {
                            if (isFavoritesExpanded) {
                                isFavoritesExpanded = false
                            } else {
                                handleLaunch("hub_CLOCK") { openHub(HubType.CLOCK) }
                            }
                        },
                        modifier = Modifier.w10mStaggeredAnimation(
                            progress = { animationProgress.value },
                            index = 1,
                            isClicked = clickedItemKey == "hub_CLOCK"
                        )
                    )
                    ZuneDate(
                        dateFormat = dateFormat,
                        onClick = {
                            if (isFavoritesExpanded) {
                                isFavoritesExpanded = false
                            } else {
                                handleLaunch("hub_CALENDAR") { openHub(HubType.CALENDAR) }
                            }
                        },
                        modifier = Modifier.w10mStaggeredAnimation(
                            progress = { animationProgress.value },
                            index = 2,
                            isClicked = clickedItemKey == "hub_CALENDAR"
                        )
                    )
                    ZuneWeather(
                        temperature = weatherSnapshot?.let { "${weatherUnit.of(it.now.temperature)}°" },
                        conditionLabel = weatherSnapshot?.let { stringResource(it.now.condition.labelRes) },
                        icon = weatherSnapshot?.let { conditionIcon(it.now.condition, it.now.isDay) },
                        onClick = { handleLaunch("hub_WEATHER") { openHub(HubType.WEATHER) } },
                        modifier = Modifier
                            .padding(top = ZuneDimens.SpacingSm)
                            .w10mStaggeredAnimation({ animationProgress.value }, 3)
                    )

                    Spacer(modifier = Modifier
                        .height(ZuneDimens.SpacingLg)
                        .onGloballyPositioned { coordinates ->
                            // Bottom of this spacer = top of hub titles
                            hubTitlesTopPx = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y +
                                    coordinates.size.height.toFloat()
                        }
                    )

                    localHubOrder.forEachIndexed { index, hubType ->
                        val key = "hub_$hubType"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEditMode) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                        .clickable { onRemoveTile("hub:${hubType.name}") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.home_running_hub_close),
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                // The list has no tile to customise, so what the title opens is
                                // set from here.
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (hubTargetApps.containsKey(hubType)) zuneColors.accentColor
                                            else Color.Black.copy(alpha = 0.55f)
                                        )
                                        .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                        .clickable { hubTargetPickFor = hubType },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = stringResource(R.string.hub_target_title),
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            val line = liveLineFor(hubType)
                            val ink = if (zuneColors.isDark) Color.White else Color.Black
                            Column(
                                modifier = Modifier.w10mStaggeredAnimation(
                                    progress = { animationProgress.value },
                                    index = 4 + index,
                                    isClicked = clickedItemKey == key
                                )
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    ZuneHubTitle(
                                        title = stringResource(hubType.titleRes),
                                        accentColor = rememberHubNameColour(line.hasNews, ink),
                                        verticalPadding = if (isWideScreen) ZuneDimens.SpacingXs else 0.dp,
                                        onClick = {
                                            if (isFavoritesExpanded) {
                                                isFavoritesExpanded = false
                                            } else if (isEditMode) {
                                                isEditMode = false
                                            } else {
                                                handleLaunch(key) { openHub(hubType) }
                                            }
                                        },
                                        // The Zune list has no tiles to hold, so its titles are what
                                        // opens editing.
                                        onLongClick = { isEditMode = true }
                                    )
                                    if (!isEditMode) {
                                        ZuneHubCount(line.count, modifier = Modifier.padding(start = 6.dp, top = 12.dp))
                                    }
                                }
                                // Editing wants a plain list; the lines come back when it is done.
                                if (!isEditMode) ZuneHubLiveLine(line.text)
                            }
                        }
                    }

                    if (isEditMode) {
                        AddHubButton(missing = hubsOffHome, onClick = { addingHub = true })
                    }

                    RunningHubsSection(
                        hubs = if (taskSwitcherVisible) runningHubs else emptyList(),
                        cornerStyle = tileCornerStyle,
                        onOpen = { hub -> handleLaunch("running_$hub") { onHubSelected(hub) } },
                        onStop = onStopHub,
                        onStopAll = onStopAllHubs,
                        onPreview = { previewTarget = it },
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    if (!isEditMode) {
                        ZuneQuickplay(
                            state = quickplay,
                            cornerStyle = tileCornerStyle,
                            face = tileFace,
                            onOpenAlbum = {
                                quickplay.lastAlbum?.let { album ->
                                    handleLaunch("qp_album") {
                                        MusicBridge.playAlbum(album.id)
                                        onHubSelected(HubType.MUSIC)
                                    }
                                }
                            },
                            onOpenPhoto = {
                                quickplay.lastPhoto?.let { photo ->
                                    handleLaunch("qp_photo") {
                                        PicturesBridge.openPhoto(photo.uri)
                                        onHubSelected(HubType.PICTURES)
                                    }
                                }
                            },
                            onOpenNote = {
                                quickplay.lastNote?.let { note ->
                                    handleLaunch("qp_note") {
                                        NotesBridge.open(note.id)
                                        onHubSelected(HubType.NOTES)
                                    }
                                }
                            },
                            onOpenApp = { app -> handleLaunch("qp_${app.packageName}") { viewModel.launchApp(app.packageName) } },
                            modifier = Modifier
                                .padding(bottom = 28.dp)
                                .w10mStaggeredAnimation({ animationProgress.value }, 4 + localHubOrder.size)
                        )
                        ZuneListEnd(
                            onApps = onNavigateToAppsHub,
                            onSettings = { handleLaunch("hub_SETTINGS") { openHub(HubType.SETTINGS) } },
                            modifier = Modifier.padding(bottom = 32.dp)
                        )
                    }
                }

                // ════════════════════════════════════════════════
                // LAYER 2 — Favorites Section
                //
                // Overlays the left portion of the screen.
                // Width animates from 15 % (collapsed) to 85 % (expanded).
                // Contains two sub-layers that cross-fade:
                //   • Collapsed: LazyColumn of small icon tiles
                //   • Expanded:  LazyVerticalGrid (4 cols) with editing
                // ════════════════════════════════════════════════
                Box(
                    modifier = Modifier
                        .then(
                            if (isWideScreen) {
                                Modifier.fillMaxWidth(0.65f).fillMaxHeight().align(Alignment.CenterEnd)
                            } else {
                                Modifier.fillMaxWidth(lerp(0.15f, 0.85f, p)).fillMaxHeight()
                            }
                        )
                ) {
                    // ── Collapsed favourites (single column, small tiles) ──
                    if (!isWideScreen && p < 0.7f) {
                        val collapsedAlpha = lerp(0.6f, 0f, (p / 0.5f).coerceIn(0f, 1f))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = collapsedAlpha },
                            contentPadding = PaddingValues(
                                start = 6.dp,
                                end = 4.dp,
                                top = with(localDensity) {
                                    if (hubTitlesTopPx > 0f) hubTitlesTopPx.toDp()
                                    else 260.dp // fallback
                                },
                                bottom = WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding() + 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                count = favoriteApps.size,
                                key = { index -> "c_${favoriteApps[index].appInfo.packageName}" }
                            ) { index ->
                                val favApp = favoriteApps[index]
                                val key = "app_${favApp.appInfo.packageName}"
                                val favLook = tileLooks["app:${favApp.appInfo.packageName}"] ?: TileLook.DEFAULT
                                SmallFavoriteTile(
                                    // The icon the big tile was given is the icon the small one shows.
                                    face = favLook.icon.takeIf { it != TileIcon.Default }?.let(iconResolver)
                                        ?: tileFace(favApp.appInfo),
                                    label = favLook.name?.takeIf { it.isNotBlank() } ?: favApp.appInfo.label,
                                    // A tap opens the app, like a tap on anything else with an icon
                                    // on it. Widening the strip is what the swipe across is for, and
                                    // a tile that only makes itself bigger is a tile that does
                                    // nothing — the whole point of having favourites in reach.
                                    onClick = {
                                        handleLaunch(key) { viewModel.launchApp(favApp.appInfo.packageName) }
                                    },
                                    onLongClick = { isFavoritesExpanded = true },
                                    modifier = Modifier
                                        .w10mStaggeredAnimation(
                                            { animationProgress.value },
                                            2 + index,
                                            clickedItemKey == key
                                        )
                                )
                            }
                        }
                    }

                    // ── Expanded favourites (4-column grid with editing) ──
                    if (isWideScreen || p > 0.3f) {
                        val expandedAlpha = if (isWideScreen) 1f else lerp(0f, 1f, ((p - 0.3f) / 0.5f).coerceIn(0f, 1f))

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(gridColumns),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = expandedAlpha }
                                .clickable(
                                    enabled = isEditMode,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    isEditMode = false
                                },
                            contentPadding = PaddingValues(
                                start = if (isWideScreen) 32.dp else 12.dp,
                                end = if (isWideScreen) 32.dp else 12.dp,
                                top = 80.dp,
                                bottom = WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding() + 24.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(tileSpacing.dp),
                            verticalArrangement = Arrangement.spacedBy(tileSpacing.dp)
                        ) {
                            item(key = "fav_label", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = stringResource(R.string.people_tab_favorites),
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontWeight = FontWeight.Light,
                                            fontSize = 96.sp,
                                            letterSpacing = (-4).sp,
                                            lineHeight = 96.sp
                                        ),
                                        color = if (zuneColors.isDark) Color.White else Color.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .w10mStaggeredAnimation({ animationProgress.value }, 8)
                                            .graphicsLayer {
                                                translationY = with(localDensity) { (-24).dp.toPx() }
                                            }
                                            .padding(bottom = 4.dp)
                                    )
                            }

                            if (favoriteApps.isEmpty()) {
                                item(key = "fav_empty", span = { GridItemSpan(maxLineSpan) }) {
                                    FavoritesEmptyState(
                                        onOpenAppList = onNavigateToAppsHub,
                                        modifier = Modifier
                                            .w10mStaggeredAnimation({ animationProgress.value }, 9)
                                    )
                                }
                            } else {
                                favoriteApps.forEachIndexed { favIndex, favApp ->
                                    val absoluteIndex = favIndex + 1 // label is item 0
                                    val isDragging =
                                        gridDragDropState.draggingItemIndex == absoluteIndex

                                    item(
                                        key = "fav_${favApp.appInfo.packageName}",
                                        span = { GridItemSpan(favApp.span) }
                                    ) {
                                        val key = "app_${favApp.appInfo.packageName}"

                                        val favId = "app:${favApp.appInfo.packageName}"
                                        CompositionLocalProvider(
                                            LocalTileLook provides (tileLooks[favId] ?: TileLook.DEFAULT),
                                            LocalTileCustomize provides { lookTargetId = favId }
                                        ) {
                                        W10MAppTile(
                                            label = favApp.appInfo.label,
                                            face = tileFace(favApp.appInfo),
                                            span = favApp.span.coerceAtMost(gridColumns),
                                            gridColumns = gridColumns,
                                            isEditing = isEditMode,
                                            isDragging = isDragging,
                                            cornerStyle = tileCornerStyle,
                                            notificationCount = notificationCounts[favApp.appInfo.packageName] ?: 0,
                                            notificationTitle = latestMessages[favApp.appInfo.packageName]?.title,
                                            notificationText = latestMessages[favApp.appInfo.packageName]?.text,
                                            onClick = {
                                                handleLaunch(key) {
                                                    viewModel.launchApp(favApp.appInfo.packageName)
                                                }
                                            },
                                            onLongClick = { isEditMode = true },
                                            onRemoveClick = {
                                                viewModel.removeFavorite(favApp.appInfo.packageName)
                                            },
                                            onResizeClick = {
                                                resizeTargetId = "app:${favApp.appInfo.packageName}"
                                            },
                                            modifier = Modifier
                                                .zIndex(if (isDragging) 1f else 0f)
                                                .then(
                                                    if (!isDragging) {
                                                        Modifier.animateItem(
                                                            fadeInSpec = null,
                                                            fadeOutSpec = null,
                                                            placementSpec = spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        )
                                                    } else Modifier
                                                )
                                                .pointerInput(isEditMode) {
                                                    if (!isEditMode) return@pointerInput
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            gridDragDropState.startDrag(absoluteIndex)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            gridDragDropState.onDrag(dragAmount)
                                                        },
                                                        onDragEnd = {
                                                            gridDragDropState.onDragInterrupted()
                                                        },
                                                        onDragCancel = {
                                                            gridDragDropState.onDragInterrupted()
                                                        }
                                                    )
                                                }
                                                .graphicsLayer {
                                                    if (isDragging) {
                                                        val info = gridState.layoutInfo
                                                            .visibleItemsInfo
                                                            .firstOrNull { it.index == absoluteIndex }
                                                            ?.offset
                                                        if (info != null) {
                                                            translationX =
                                                                gridDragDropState.draggingItemInitialOffset.x +
                                                                        gridDragDropState.totalDragAmount.x -
                                                                        info.x
                                                            translationY =
                                                                gridDragDropState.draggingItemInitialOffset.y +
                                                                        gridDragDropState.totalDragAmount.y -
                                                                        info.y
                                                        }
                                                    }
                                                }
                                                .w10mEditWiggle(
                                                    isEditing = isEditMode,
                                                    isDragging = isDragging,
                                                    index = absoluteIndex
                                                )
                                                .w10mStaggeredAnimation(
                                                    { animationProgress.value },
                                                    9 + favIndex,
                                                    clickedItemKey == key
                                                )
                                        )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TileSizePopup()
                TileLookPopup()
                TileIconPopup()
                HubTargetPopup()
            }
        }

        if (addingHub) {
            AddHubDialog(
                missing = hubsOffHome,
                onPick = { hub ->
                    addingHub = false
                    viewModel.addHub(hub)
                },
                onDismiss = { addingHub = false }
            )
        }

        previewTarget?.let { target ->
            HubPreviewBalloon(
                target = target,
                preview = hubPreviews[target.hub],
                hostOrigin = hostOrigin,
                onOpen = {
                    previewTarget = null
                    handleLaunch("running_${target.hub}") { onHubSelected(target.hub) }
                },
                onStop = {
                    previewTarget = null
                    onStopHub(target.hub)
                },
                onDismiss = { previewTarget = null }
            )
        }
    }
    }
}



/**
 * What stands where the favourites would be before there are any.
 *
 * An empty pane says nothing: somebody who swipes across and finds a blank half-screen learns
 * neither what the space is for nor that they can fill it. This says both, in as few words as
 * the rest of the launcher uses, and then offers the one tap that leads to the doing — the app
 * list, where an app is held down and pinned.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesEmptyState(
    onOpenAppList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier.padding(top = 8.dp, end = 24.dp)) {
        Text(
            text = stringResource(R.string.home_favorites_empty_line),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Light,
                lineHeight = 26.sp
            ),
            color = zuneColors.textMuted
        )

        Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))

        // The way out of the empty state, and the only thing here that answers a tap.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wpTilt(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onOpenAppList
                )
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.home_favorites_empty_action),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = zuneColors.accentColor
            )
            Spacer(modifier = Modifier.width(ZuneDimens.SpacingSm))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = zuneColors.accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(ZuneDimens.SpacingXs))

        Text(
            text = stringResource(R.string.home_favorites_empty_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
            color = zuneColors.textDim
        )
    }
}

// ────────────────────────────────────────────────────────
// Small favourite tile used in the collapsed single-column
// ────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmallFavoriteTile(
    face: TileIconFace,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val stroke = if (zuneColors.isDark)
        Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(zuneColors.accentColor)
                .border(0.5.dp, stroke, RoundedCornerShape(8.dp))
        ) {
            TileIconImage(
                face = face,
                size = 24.dp,
                ink = Color.White,
                contentDescription = label,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
