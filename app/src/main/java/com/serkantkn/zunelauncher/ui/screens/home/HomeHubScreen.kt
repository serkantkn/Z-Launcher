package com.serkantkn.zunelauncher.ui.screens.home

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.serkantkn.zunelauncher.ui.screens.expandNotificationPanel
import com.serkantkn.zunelauncher.ui.screens.apps.Windows8AppsScreen
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
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
    /** The page to the right of Start: quickplay in the Zune layout, the app list otherwise. */
    onNavigateNext: () -> Unit = onNavigateToAppsHub,
    /** Bumped when the Home key is pressed while Start is already in front. */
    homeResetSignal: Int = 0,
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
            onNavigateNext = onNavigateNext,
            homeResetSignal = homeResetSignal,
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
    onNavigateNext: () -> Unit,
    homeResetSignal: Int,
    timeFormat: String,
    dateFormat: String,
    modifier: Modifier,
    viewModel: HomeHubViewModel
) {
    val unifiedStartTilesFlow by viewModel.unifiedStartTiles.collectAsState()
    val favoriteAppsFlow by viewModel.favoriteApps.collectAsState()
    val hubOrderFlow by viewModel.hubOrder.collectAsState()
    val favoritePhotos by viewModel.favoritePhotoUris.collectAsState()
    val notificationCounts by viewModel.notificationCounts.collectAsState()
    // Whose notifications count on the messaging and phone tiles: whatever app holds the role,
    // not the two Google apps by name. The launcher holding it keeps its own counts.
    val defaultSmsPackage by viewModel.defaultSmsPackage.collectAsState()
    val defaultDialerPackage by viewModel.defaultDialerPackage.collectAsState()
    val selfPackage = LocalContext.current.packageName
    fun messagingCount(own: Int) = com.serkantkn.zunelauncher.util.StartBadges.count(
        own, notificationCounts, defaultSmsPackage, selfPackage
    )
    fun phoneCount(own: Int) = com.serkantkn.zunelauncher.util.StartBadges.count(
        own, notificationCounts, defaultDialerPackage, selfPackage
    )
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
    // Set while a tap is on its way out: the turnstile takes most of a second, and two quick
    // taps used to open both things one after the other.
    var launching by remember { mutableStateOf(false) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    val animationsOn = LocalAnimationsEnabled.current

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            clickedItemKey = null
            launching = false
            animationProgress.snapTo(1f)
            isEditMode = false
            isFavoritesExpanded = false
        }
    }

    LaunchedEffect(isHubOpen) {
        if (!isHubOpen && animationProgress.value > 1f) {
            clickedItemKey = null
            launching = false
            animationProgress.snapTo(0f)
            animationProgress.animateTo(1f, tween(1200, easing = LinearEasing))
        }
    }

    fun handleLaunch(key: String, action: () -> Unit) {
        if (isEditMode || launching) return
        clickedItemKey = key
        if (!animationsOn) {
            // Nothing turns away, so there is nothing to wait for.
            openFolderId = null
            action()
            return
        }
        launching = true
        coroutineScope.launch {
            // Turnstile feather out (1f -> 2f). The launch itself fires slightly before the
            // chosen tile has fully turned away so the hub's hinge-in overlaps it, WP style.
            launch { animationProgress.animateTo(2f, tween(1000, easing = LinearEasing)) }
            delay(860)
            // A folder left open under the hub would answer the Back key in the hub's stead.
            openFolderId = null
            action()
            launching = false
        }
    }

    // ── Grid state for expanded favorites ──
    val gridState = rememberLazyGridState()
    // Hoisted: the Home key, pressed on a Start screen that is already in front, takes the board
    // back to its beginning.
    val hubsOffHome by viewModel.hubsOffHome.collectAsState()
    var addingHub by remember { mutableStateOf(false) }
    val boardScrollState = rememberScrollState()
    val zuneScrollState = rememberScrollState()
    val win8ScrollState = rememberScrollState()
    val localDensity = LocalDensity.current

    val gridDragDropState = rememberGridDragDropState(
        gridState = gridState,
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
                        // Swipe left on main screen (finger right -> left) -> the next page, which
                        // in the Zune layout is quickplay, with the app list beyond it.
                        onNavigateNext()
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
    val allImages by viewModel.allImages.collectAsState()
    // The clock line and the pictures line both read the time of day; once a minute is often
    // enough for either, and reading the clock in composition never updated them at all.
    val minuteNow = com.serkantkn.zunelauncher.ui.components.rememberMinuteTicker()
    val dayStart = remember(minuteNow) { com.serkantkn.zunelauncher.util.startOfDay(minuteNow) }

    // Tablet: Start and the app list are one tall page. This is how far the list has risen over
    // Start (0 = Start, 1 = the list) and whether it is meant to stay up; the counter asks the
    // list to arrive with its search box open.
    val appsReveal = remember { Animatable(0f) }
    var appsOpen by remember { mutableStateOf(false) }
    var appsSearchRequests by remember { mutableIntStateOf(0) }
    fun openApps(withSearch: Boolean) {
        if (withSearch) appsSearchRequests++
        appsOpen = true
        coroutineScope.launch {
            if (animationsOn) appsReveal.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
            else appsReveal.snapTo(1f)
        }
    }
    fun closeApps() {
        coroutineScope.launch {
            if (animationsOn) appsReveal.animateTo(0f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
            else appsReveal.snapTo(0f)
            appsOpen = false
        }
    }

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
                if (quiet) HubLiveLine.NONE else HubLiveLine(
                    text = first?.let { "${it.contactName.ifBlank { it.address }}: ${it.snippet}" },
                    count = messagingCount(unreadMessages.size)
                )
            }
            HubType.PHONE -> if (quiet) HubLiveLine.NONE else HubLiveLine(
                text = lastMissedCaller?.let { "$it · ${stringResource(R.string.zune_line_missed)}" },
                count = phoneCount(missedCalls)
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
                val now = minuteNow
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
                // The store keeps DATE_ADDED in seconds.
                val today = allImages.count { it.dateAdded * 1000L >= dayStart }
                HubLiveLine(text = if (today > 0) pluralStringResource(R.plurals.zune_line_new_photos, today, today) else null)
            }
            else -> HubLiveLine.NONE
        }
    }
    var hubTargetPickFor by remember { mutableStateOf<HubType?>(null) }
    val openHub: (HubType) -> Unit = { hub -> if (!viewModel.openHubTarget(hub)) onHubSelected(hub) }

    // A held app tile offers the app's shortcuts, with editing Start among them. An app with
    // none, or a board already being edited, goes straight to editing as before.
    val shortcutsContext = LocalContext.current
    var shortcutApp by remember { mutableStateOf<AppInfo?>(null) }
    var shortcutList by remember { mutableStateOf<List<android.content.pm.ShortcutInfo>>(emptyList()) }
    val holdApp: (AppInfo) -> Unit = { app ->
        val found = if (isEditMode) emptyList() else com.serkantkn.zunelauncher.util.AppShortcuts.of(shortcutsContext, app.packageName)
        if (found.isEmpty()) {
            isEditMode = true
        } else {
            shortcutApp = app
            shortcutList = found
        }
    }
    val openFolder = localStartTiles.firstOrNull { it.id == openFolderId } as? StartTileUIModel.Folder

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
        if (appsOpen) closeApps()
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

    // The open folder answers the back gesture before edit mode does — but never over a hub,
    // which is Start's to answer only once it is back in front.
    BackHandler(enabled = openFolderId != null && !isHubOpen) { openFolderId = null }

    val onSetTileSize: (String, Int) -> Unit = { tileId, newSpan ->
        val current = localStartTiles.toMutableList()
        val index = current.indexOfFirst { it.id == tileId }
        val folderOf = current.filterIsInstance<StartTileUIModel.Folder>()
            .firstOrNull { folder -> folder.children.any { it.id == tileId } }
        if (index == -1 && folderOf != null) {
            // Inside a folder: the folder keeps the size, and the favourites strip — which may
            // list the same app — is left alone, or the wrong tile changed.
            val folderIndex = current.indexOf(folderOf)
            current[folderIndex] = folderOf.copy(
                children = folderOf.children.map { if (it.id == tileId) it.withSpan(newSpan) else it }
            )
            localStartTiles = current
            viewModel.setFolderChildSpan(tileId, newSpan)
        } else if (index == -1 && tileId.startsWith("app:")) {
            // Zune layout: the tile lives in the favourites strip, not on the Start board.
            val packageName = tileId.removePrefix("app:")
            val updated = favoriteApps.map { fav ->
                if (fav.appInfo.packageName == packageName) FavoriteAppUIModel(fav.appInfo, newSpan) else fav
            }
            favoriteApps = updated
            viewModel.updateFavoritesOrder(updated)
        }
        if (index != -1) {
            current[index] = current[index].withSpan(newSpan)
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
            currentSpan = tileModel(targetId)?.span ?: TileSpan.MEDIUM,
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
                            HubType.MESSAGING -> messagingCount(unreadMessages.size)
                            HubType.PHONE -> phoneCount(missedCalls)
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
                onLongClick = { holdApp(model.appInfo) },
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
                    // The board's own column count, so the preview is the tile as it is drawn
                    // and not a four-column tile twice the size.
                    StartTile(model, 0, false, false, Modifier, gridColumns = gridColumns)
                }
            }
        )
    }

    // Any icon a tile was given is turned into a face here, once, for every tile on the screen.
    val iconResolver: (TileIcon) -> TileIconFace? = remember(tileIconStyle) {
        { icon -> viewModel.customIconFace(icon, tileIconStyle) }
    }

    // One box over all three Start layouts.
    CompositionLocalProvider(LocalTileIconResolver provides iconResolver) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE && isWideScreen) {
            // ═══════════════════════════════════════════════
            // TABLET — Windows 8 full-screen Start menu
            // ═══════════════════════════════════════════════
            // Start and the app list are one tall page, as on Windows 8.1: a swipe up, or the
            // arrow in the corner, slides Start off the top while the list comes up from below;
            // a swipe down, the arrow, or Back brings Start back. Nothing is to the right of
            // Start here, and the social hub on its left is reached by pulling the board past
            // its first group.
            var stackHeightPx by remember { mutableFloatStateOf(0f) }
            val launcherContext = LocalContext.current
            val appsShown = appsOpen || appsReveal.value > 0f

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .onSizeChanged { stackHeightPx = it.height.toFloat() }
                    .clickable(
                        enabled = isEditMode,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        isEditMode = false
                    }
            ) {
                // Start, sliding up and away as the list is revealed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = -stackHeightPx * appsReveal.value }
                        .win8SwipeReveal(
                            reveal = appsReveal,
                            heightPx = { stackHeightPx },
                            opening = true,
                            enabled = !isEditMode && openFolder == null,
                            animate = animationsOn,
                            onSettled = { open -> appsOpen = open },
                            onPullDown = { expandNotificationPanel(launcherContext) }
                        )
                ) {
                    Windows8StartScreen(
                        tiles = localStartTiles,
                        tileSpacing = tileSpacing.dp,
                        isEditMode = isEditMode,
                        scrollState = win8ScrollState,
                        onEnterEditMode = { isEditMode = true },
                        onMoveTile = { from, to -> moveStartTile(from, to) },
                        onOpenApps = { openApps(withSearch = false) },
                        onSearch = { openApps(withSearch = true) },
                        onPullToSocial = onNavigateToSocialHub,
                        // Through openHub like every other way into a hub, so a hub pointed at
                        // another app opens that app from here too.
                        onOpenUser = { handleLaunch("hub_PEOPLE") { openHub(HubType.PEOPLE) } },
                        onOpenSettings = { handleLaunch("hub_SETTINGS") { openHub(HubType.SETTINGS) } },
                        onMergeTiles = onMergeTiles,
                        canMerge = canMerge,
                        tile = { model, index, isDragging, isMergeTarget, tileModifier ->
                            StartTile(model, index, isDragging, isMergeTarget, tileModifier, gridColumns = 4)
                        }
                    )

                    // The tablet board scrolls sideways, so the add button is laid over its
                    // bottom-left corner, to the right of the all-apps arrow.
                    if (isEditMode) {
                        AddHubButton(
                            missing = hubsOffHome,
                            onClick = { addingHub = true },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 104.dp, bottom = 24.dp)
                        )
                    }
                    StartEditBar(
                        visible = isEditMode && openFolder == null,
                        onDone = { isEditMode = false },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // The app list, coming up from under Start
                if (appsShown) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = stackHeightPx * (1f - appsReveal.value) }
                            .win8SwipeReveal(
                                reveal = appsReveal,
                                heightPx = { stackHeightPx },
                                opening = false,
                                enabled = true,
                                animate = animationsOn,
                                onSettled = { open -> appsOpen = open }
                            )
                    ) {
                        Windows8AppsScreen(
                            searchRequests = appsSearchRequests,
                            onBackToStart = { closeApps() }
                        )
                    }
                }

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
                            // Bottom of this spacer = top of hub titles, as it stands with the
                            // list at rest. The scroll is added back so the favourites strip,
                            // which lines up with this, stays put while the list moves: the two
                            // scroll on their own, as on the Zune.
                            hubTitlesTopPx = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y +
                                    coordinates.size.height.toFloat() + zuneScrollState.value
                        }
                    )

                    /** Moves a hub one place up or down the list and keeps the new order. */
                    fun moveHub(hubType: HubType, by: Int) {
                        val order = localHubOrder.toMutableList()
                        val from = order.indexOf(hubType)
                        val to = from + by
                        if (from < 0 || to !in order.indices) return
                        order.add(to, order.removeAt(from))
                        localHubOrder = order
                        viewModel.setHubOrder(order)
                    }

                    localHubOrder.forEachIndexed { index, hubType ->
                        val key = "hub_$hubType"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEditMode) {
                                ZuneListEditButton(
                                    icon = Icons.Default.Close,
                                    label = stringResource(R.string.home_running_hub_close),
                                    onClick = { onRemoveTile("hub:${hubType.name}") }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // The list has no tile to customise, so what the title opens is
                                // set from here.
                                ZuneListEditButton(
                                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                                    label = stringResource(R.string.hub_target_title),
                                    filled = hubTargetApps.containsKey(hubType),
                                    onClick = { hubTargetPickFor = hubType }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // The list has nothing to drag, so a hub moves a step at a time.
                                ZuneListEditButton(
                                    icon = Icons.Default.KeyboardArrowUp,
                                    label = stringResource(R.string.start_hub_move_up),
                                    enabled = index > 0,
                                    onClick = { moveHub(hubType, -1) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ZuneListEditButton(
                                    icon = Icons.Default.KeyboardArrowDown,
                                    label = stringResource(R.string.start_hub_move_down),
                                    enabled = index < localHubOrder.lastIndex,
                                    onClick = { moveHub(hubType, 1) }
                                )
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
                                        // Re-fitted when the edit buttons take their share of the row.
                                        fitKey = isEditMode,
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

                    // What was pinned from the hubs — notes, people, conversations, sites,
                    // albums, records and folders — used to be kept by the Zune list and never
                    // shown by it. They stand under the hubs as tiles, four to a row.
                    val pinnedTiles = localStartTiles.filter {
                        it !is StartTileUIModel.Hub && it !is StartTileUIModel.App
                    }
                    if (pinnedTiles.isNotEmpty()) {
                        ZuneSectionWord(
                            text = stringResource(R.string.start_pinned_section),
                            modifier = Modifier
                                .padding(top = 20.dp, bottom = 10.dp)
                                .w10mStaggeredAnimation({ animationProgress.value }, 4 + localHubOrder.size)
                        )
                        ZunePinnedBoard(
                            tiles = pinnedTiles,
                            columns = FOLDER_COLUMNS,
                            gap = tileSpacing.dp
                        ) { model, index ->
                            StartTile(
                                model = model,
                                index = 5 + localHubOrder.size + index,
                                isDragging = false,
                                isMergeTarget = false,
                                tileModifier = Modifier.fillMaxWidth(),
                                gridColumns = FOLDER_COLUMNS
                            )
                        }
                    }

                    if (!isEditMode) {
                        ZuneListEnd(
                            onApps = onNavigateToAppsHub,
                            onSettings = { handleLaunch("hub_SETTINGS") { openHub(HubType.SETTINGS) } },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    // The last hub used to end under the navigation bar, where it could not be
                    // reached; the list now ends clear of it.
                    Spacer(
                        modifier = Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
                        )
                    )
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
                                    cornerStyle = tileCornerStyle,
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

                                    // A large tile is four cells wide and four tall; its stored
                                    // span of eight is a width only in the packed boards, and
                                    // here it used to swallow a whole row.
                                    val gridSpan = TileSpan.cellsOf(favApp.span).first.coerceAtMost(gridColumns)
                                    item(
                                        key = "fav_${favApp.appInfo.packageName}",
                                        span = { GridItemSpan(gridSpan) }
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
                                            onLongClick = { holdApp(favApp.appInfo) },
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

                // The pinned folders open over the list, the way they do on the tablet board.
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

                StartEditBar(
                    visible = isEditMode && openFolder == null,
                    onDone = { isEditMode = false },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        shortcutApp?.let { app ->
            AppShortcutsDialog(
                appLabel = app.label,
                shortcuts = shortcutList,
                onPick = { shortcut ->
                    shortcutApp = null
                    if (!com.serkantkn.zunelauncher.util.AppShortcuts.start(shortcutsContext, shortcut)) {
                        viewModel.launchApp(app.packageName)
                    }
                },
                onEdit = {
                    shortcutApp = null
                    isEditMode = true
                },
                onDismiss = { shortcutApp = null }
            )
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
                color = zuneColors.accentLink
            )
            Spacer(modifier = Modifier.width(ZuneDimens.SpacingSm))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = zuneColors.accentLink,
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
    cornerStyle: TileCornerStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val stroke = if (zuneColors.isDark)
        Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.4f)
    // The same corners as the big tiles: these kept their own rounding whatever was chosen.
    val shape = RoundedCornerShape(if (cornerStyle == TileCornerStyle.SHARP) 0.dp else 8.dp)

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
                .clip(shape)
                .background(zuneColors.accentColor)
                .border(0.5.dp, stroke, shape)
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
