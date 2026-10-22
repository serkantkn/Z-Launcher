package com.serkantkn.zeta.ui.screens.home

import androidx.compose.ui.unit.times
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clipToBounds
import com.serkantkn.zeta.data.model.QuickplayPin
import com.serkantkn.zeta.R
import androidx.compose.ui.res.stringResource
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.serkantkn.zeta.ui.screens.expandNotificationPanel
import com.serkantkn.zeta.ui.screens.apps.Windows8AppsScreen
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
import com.serkantkn.zeta.ui.components.wpTilt
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberUpdatedState
import com.serkantkn.zeta.ui.components.getHubIcon
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Rect
import com.serkantkn.zeta.ui.animation.win8TileMotion
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import com.serkantkn.zeta.data.model.TileCornerStyle
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
import com.serkantkn.zeta.data.model.HomeScreenLayout
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.ui.animation.w10mEditWiggle
import com.serkantkn.zeta.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zeta.ui.animation.zuneListMotion
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.components.W10MAppTile
import com.serkantkn.zeta.ui.components.LocalTileStyle
import com.serkantkn.zeta.ui.components.LocalTileLook
import com.serkantkn.zeta.ui.components.LocalTileRunning
import com.serkantkn.zeta.ui.components.LocalTileCustomize
import com.serkantkn.zeta.ui.components.LocalTileIconResolver
import com.serkantkn.zeta.ui.components.LocalTileIsPreview
import com.serkantkn.zeta.data.model.TileLook
import com.serkantkn.zeta.data.repository.StartParallax
import com.serkantkn.zeta.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zeta.ui.components.TileStyle
import com.serkantkn.zeta.ui.components.W10MFolderTile
import com.serkantkn.zeta.ui.components.TileLine
import com.serkantkn.zeta.util.formatFileSize
import com.serkantkn.zeta.ui.components.W10MHubTile
import com.serkantkn.zeta.ui.components.W10MMusicTile
import com.serkantkn.zeta.ui.components.W10MPeopleTile
import com.serkantkn.zeta.ui.components.W10MWeatherTile
import com.serkantkn.zeta.ui.screens.weather.conditionIcon
import com.serkantkn.zeta.ui.components.W10MCalendarTile
import com.serkantkn.zeta.ui.components.W10MClockTile
import com.serkantkn.zeta.ui.components.W10MNoteTile
import com.serkantkn.zeta.ui.components.W10MPersonTile
import com.serkantkn.zeta.ui.components.W10MThreadTile
import com.serkantkn.zeta.ui.components.W10MAlbumTile
import com.serkantkn.zeta.ui.components.W10MWebTile
import com.serkantkn.zeta.data.repository.BrowserBridge
import com.serkantkn.zeta.data.repository.PeopleBridge
import com.serkantkn.zeta.data.repository.NotesBridge
import com.serkantkn.zeta.data.repository.MusicBridge
import com.serkantkn.zeta.data.repository.PicturesBridge
import com.serkantkn.zeta.ui.components.ZuneClock
import com.serkantkn.zeta.ui.components.ZuneDate
import com.serkantkn.zeta.ui.components.ZuneHubTitle
import com.serkantkn.zeta.ui.components.ZuneWeather
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneDimens
import com.serkantkn.zeta.util.toImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.serkantkn.zeta.util.TileIconFace
import com.serkantkn.zeta.ui.components.TileIconImage
import com.serkantkn.zeta.data.model.AppInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.serkantkn.zeta.data.model.TileIcon
import com.serkantkn.zeta.ui.components.TileIconPicker

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
    onHubSelected: (com.serkantkn.zeta.data.model.HubId) -> Unit,
    onNavigateToSocialHub: () -> Unit = {},
    onNavigateToAppsHub: () -> Unit = {},
    /** The page to the right of Start: quickplay in the Zune layout, the app list otherwise. */
    onNavigateNext: () -> Unit = onNavigateToAppsHub,
    /** Bumped when the Home key is pressed while Start is already in front. */
    homeResetSignal: Int = 0,
    /** The hubs left running; their tiles and names carry the running mark. */
    runningHubs: Set<com.serkantkn.zeta.data.model.HubId> = emptySet(),
    timeFormat: String = "HH:mm",
    dateFormat: String = "EEEE, MMMM d",
    /**
     * The Zune layout's tiles page, beside its list: the Windows Phone board, with the pinned
     * apps and what was pinned from the hubs, but not the hubs themselves - the list is those.
     */
    tilesPage: Boolean = false,
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
            runningHubs = runningHubs,
            timeFormat = timeFormat,
            dateFormat = dateFormat,
            tilesPage = tilesPage,
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
    onHubSelected: (com.serkantkn.zeta.data.model.HubId) -> Unit,
    onNavigateToSocialHub: () -> Unit,
    onNavigateToAppsHub: () -> Unit,
    onNavigateNext: () -> Unit,
    homeResetSignal: Int,
    runningHubs: Set<com.serkantkn.zeta.data.model.HubId>,
    timeFormat: String,
    dateFormat: String,
    tilesPage: Boolean,
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
    fun messagingCount(own: Int) = com.serkantkn.zeta.util.StartBadges.count(
        own, notificationCounts, defaultSmsPackage, selfPackage
    )
    fun phoneCount(own: Int) = com.serkantkn.zeta.util.StartBadges.count(
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
    val emailTileMessages by viewModel.emailTileMessages.collectAsState()
    val missedCallList by viewModel.missedCallList.collectAsState()
    val notesTileNotes by viewModel.notesTileNotes.collectAsState()
    val calculatorTileHistory by viewModel.calculatorTileHistory.collectAsState()
    val storage by viewModel.storage.collectAsState()
    val weatherSnapshot by viewModel.weatherSnapshot.collectAsState()
    val newsStories by viewModel.newsStories.collectAsState()
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
    // Set when it is the tiles being edited, not the Zune list beside them (a tablet shows both):
    // the list keeps its names plain rather than sprouting its own edit buttons.
    var tilesEditOnly by remember { mutableStateOf(false) }
    LaunchedEffect(isEditMode) { if (!isEditMode) tilesEditOnly = false }
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

    // The tablet board moves the Windows 8 way (see Win8Motion): the tiles slide in from the
    // right, and a launch flips the chosen tile into a splash that fills the screen.
    val win8Mode = homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE && isWideScreen
    // The Zune layout moves the Zune HD's way (see ZuneMotion): slides and a zoom, faster than
    // the turnstile; the Windows Phone and Windows 8 boards keep their own motion untouched.
    val zuneMode = homeScreenLayout == HomeScreenLayout.ZUNE
    val zuneModeNow by rememberUpdatedState(zuneMode)
    // The Zune layout's tiles leave the hubs out: its list is the hubs. Everything else on the
    // board - apps, and what was pinned from inside the hubs - is the same board the Windows
    // Phone layout shows, so pinning in either is pinning in both.
    val boardTiles = remember(localStartTiles, zuneMode) {
        if (!zuneMode) localStartTiles else localStartTiles.mapNotNull { tile ->
            when (tile) {
                is StartTileUIModel.Hub -> null
                is StartTileUIModel.Folder -> tile.copy(children = tile.children.filterNot { it is StartTileUIModel.Hub })
                    .takeIf { it.children.isNotEmpty() }
                else -> tile
            }
        }
    }
    // The quickplay the Zune list carries in its strip and opens beside itself.
    val quickplayState by viewModel.quickplay.collectAsState()
    val stripItems = remember(quickplayState) { quickplayStripItems(quickplayState, QUICKPLAY_STRIP_ITEMS) }
    /** The time Start takes to come in, by the layout's own motion. */
    fun enterMillis() = if (zuneModeNow) com.serkantkn.zeta.ui.animation.ZUNE_ENTER_MILLIS else 1200
    val win8Bounds = remember { com.serkantkn.zeta.ui.animation.Win8Motion.Bounds() }
    /** What each tile would show on its splash: its glyph or its face, and its colour. */
    val win8Faces = remember { HashMap<String, com.serkantkn.zeta.ui.animation.Win8Splash>() }
    var win8Splash by remember { mutableStateOf<com.serkantkn.zeta.ui.animation.Win8Splash?>(null) }
    val hubOpenNow by rememberUpdatedState(isHubOpen)

    // ── Staggered entrance animation ──
    val animationProgress = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasRunInitialAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasRunInitialAnimation) {
            hasRunInitialAnimation = true
            animationProgress.snapTo(0f)
            animationProgress.animateTo(1f, tween(enterMillis(), easing = LinearEasing))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refreshLiveTiles()
            if (event == Lifecycle.Event.ON_START && hasRunInitialAnimation && !hubOpenNow) {
                // Back from an app the splash went into: Start comes in again from clean. Not
                // behind an open hub, though: nobody sees it there, and the hub's closing brings
                // Start in anyway.
                win8Splash = null
                coroutineScope.launch {
                    animationProgress.snapTo(0f)
                    animationProgress.animateTo(1f, tween(enterMillis(), easing = LinearEasing))
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
        // The strip's "new" reads the music afresh whenever the list comes into view.
        if (isCurrentPage && zuneMode && !tilesPage) viewModel.refreshNewMusic()
        if (!isCurrentPage) {
            clickedItemKey = null
            launching = false
            win8Splash = null
            animationProgress.snapTo(1f)
            isEditMode = false
            isFavoritesExpanded = false
        }
    }

    val hubFlightNow = com.serkantkn.zeta.ui.animation.LocalZuneHubFlight.current
    // Whether a hub was open the last time this looked: Start comes in whenever one closes.
    var hubWasOpen by remember { mutableStateOf(isHubOpen) }
    LaunchedEffect(isHubOpen) {
        // Every hub that closes brings Start in on the turnstile, however it was opened. This
        // used to ask whether the tiles had turned away (progress past 1), which only a tap on a
        // tile does: a hub opened from the app list, a notification, the search or the switcher
        // closed onto a board that just stood there, and so did any hub the phone had been away
        // from - coming back replayed the board behind the hub, where nobody saw it.
        val closed = hubWasOpen && !isHubOpen
        hubWasOpen = isHubOpen
        if (closed) {
            clickedItemKey = null
            launching = false
            animationProgress.snapTo(0f)
            animationProgress.animateTo(1f, tween(enterMillis(), easing = LinearEasing))
            // The name a hub's title came back down to joins the rest of the list again.
            hubFlightNow?.landed()
        }
        // The hub comes up over the splash; once it has, the splash has done its work.
        if (isHubOpen && win8Splash != null) {
            delay(WIN8_SPLASH_LINGER_MILLIS)
            win8Splash = null
        }
    }

    fun handleLaunch(
        key: String,
        /** When, in the Zune layout, the thing is opened: a hub whose name flies up opens at once. */
        zuneOpenAt: Long = com.serkantkn.zeta.ui.animation.ZUNE_OPEN_AT_MILLIS,
        action: () -> Unit
    ) {
        if (isEditMode || launching) return
        clickedItemKey = key
        if (!animationsOn) {
            // Nothing turns away, so there is nothing to wait for.
            openFolderId = null
            action()
            return
        }
        launching = true
        if (win8Mode) {
            win8Splash = win8Faces[key]?.let { face ->
                win8Bounds.byKey[key]?.let { from ->
                    com.serkantkn.zeta.ui.animation.Win8Splash(from, face.color, face.glyph, face.face)
                }
            }
            val splash = win8Splash
            coroutineScope.launch {
                animationProgress.animateTo(
                    2f,
                    tween(com.serkantkn.zeta.ui.animation.Win8Motion.LAUNCH_MILLIS, easing = LinearEasing)
                )
                openFolderId = null
                action()
                launching = false
                // An app that never came up (or a hub that points at one) would leave the screen
                // covered; Start comes back if nothing has taken its place by then.
                delay(WIN8_SPLASH_GIVE_UP_MILLIS)
                if (win8Splash === splash && splash != null && !hubOpenNow &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    win8Splash = null
                    clickedItemKey = null
                    animationProgress.snapTo(0f)
                    animationProgress.animateTo(1f, tween(enterMillis(), easing = LinearEasing))
                }
            }
            return
        }
        coroutineScope.launch {
            if (zuneMode) {
                // The Zune's zoom through: the hub is opened while the word is still growing, so
                // it appears behind it as the word dissolves.
                launch {
                    animationProgress.animateTo(
                        2f,
                        tween(com.serkantkn.zeta.ui.animation.ZUNE_EXIT_MILLIS, easing = LinearEasing)
                    )
                }
                delay(zuneOpenAt)
                openFolderId = null
                action()
                launching = false
                return@launch
            }
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

    // ── Grid state (the Home key scrolls it back) ──
    val gridState = rememberLazyGridState()
    // Hoisted: the Home key, pressed on a Start screen that is already in front, takes the board
    // back to its beginning.
    val hubsOffHome by viewModel.hubsOffHome.collectAsState()
    var addingHub by remember { mutableStateOf(false) }
    val boardScrollState = rememberScrollState()
    val zuneScrollState = rememberScrollState()
    val win8ScrollState = rememberScrollState()
    val localDensity = LocalDensity.current

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
    val lastSite by viewModel.lastSite.collectAsState()
    val lastCalculation by viewModel.lastCalculation.collectAsState()
    val contactCount by viewModel.contactCount.collectAsState()
    val lineContext = LocalContext.current
    // The clock line and the pictures line both read the time of day; once a minute is often
    // enough for either, and reading the clock in composition never updated them at all.
    val minuteNow = com.serkantkn.zeta.ui.components.rememberMinuteTicker()
    val dayStart = remember(minuteNow) { com.serkantkn.zeta.util.startOfDay(minuteNow) }

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
        // The tiles page beside the list has no say in it.
        if (tilesPage) return@LaunchedEffect
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
    fun liveLineFor(hubId: com.serkantkn.zeta.data.model.HubId): HubLiveLine {
        // Another app's hub says what it gives its own tile (PLATFORM.md, stage 2); nothing yet.
        val hub = hubId as? HubType ?: return HubLiveLine.NONE
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
            HubType.NEWS -> HubLiveLine(text = newsStories.firstOrNull()?.title)
            HubType.NOTES -> HubLiveLine(
                text = notesTileSubtitle,
                count = if (quiet) 0 else pinnedNotesCount
            )
            HubType.PICTURES -> {
                // The store keeps DATE_ADDED in seconds.
                val today = allImages.count { it.dateAdded * 1000L >= dayStart }
                HubLiveLine(text = if (today > 0) pluralStringResource(R.plurals.zune_line_new_photos, today, today) else null)
            }
            HubType.PEOPLE -> HubLiveLine(
                text = if (contactCount > 0) pluralStringResource(R.plurals.zune_line_people, contactCount, contactCount) else null
            )
            HubType.INTERNET -> HubLiveLine(
                text = lastSite?.let { site ->
                    site.title.ifBlank { android.net.Uri.parse(site.url).host?.removePrefix("www.") ?: site.url }
                }
            )
            HubType.FILES -> {
                // Room left on the phone: worth a glance, and asks the system for nothing.
                val free = remember(minuteNow) {
                    runCatching { android.os.StatFs(android.os.Environment.getDataDirectory().path).availableBytes }.getOrNull()
                }
                HubLiveLine(
                    text = free?.let {
                        stringResource(R.string.zune_line_free_space, android.text.format.Formatter.formatShortFileSize(lineContext, it))
                    }
                )
            }
            HubType.CAMERA -> {
                // The camera's own pictures, not every screenshot and download.
                val last = remember(allImages) {
                    allImages.filter { !it.isVideo && it.relativePath.contains("DCIM/Camera", ignoreCase = true) }
                        .maxByOrNull { it.dateAdded }
                }
                HubLiveLine(
                    text = last?.let {
                        val millis = it.dateAdded * 1000L
                        val pattern = if (millis >= dayStart) {
                            if (timeFormat.contains("H")) "HH:mm" else "h:mm a"
                        } else {
                            "d MMMM"
                        }
                        stringResource(
                            R.string.zune_line_last_shot,
                            java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(millis))
                        )
                    }
                )
            }
            HubType.CALCULATOR -> HubLiveLine(
                text = lastCalculation?.let { "${it.expression} = ${it.result}" }
            )
            HubType.SETTINGS -> {
                val battery = remember(minuteNow) {
                    lineContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                        ?.let { intent ->
                            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                            if (level >= 0 && scale > 0) level * 100 / scale else null
                        }
                }
                HubLiveLine(text = battery?.let { stringResource(R.string.zune_line_battery, it) })
            }
            else -> HubLiveLine.NONE
        }
    }

    /** The videos row's line: how many there are, or how many came in today. */
    @Composable
    fun videosLine(): HubLiveLine {
        val videos = allImages.filter { it.isVideo }
        val today = videos.count { it.dateAdded * 1000L >= dayStart }
        return HubLiveLine(
            text = when {
                today > 0 -> pluralStringResource(R.plurals.zune_line_new_photos, today, today)
                videos.isNotEmpty() -> pluralStringResource(R.plurals.zune_line_videos, videos.size, videos.size)
                else -> null
            }
        )
    }
    var hubTargetPickFor by remember { mutableStateOf<HubType?>(null) }
    val openHub: (com.serkantkn.zeta.data.model.HubId) -> Unit = { hub -> if (!viewModel.openHubTarget(hub)) onHubSelected(hub) }

    // A held app tile offers the app's shortcuts, with editing Start among them. An app with
    // none, or a board already being edited, goes straight to editing as before.
    val shortcutsContext = LocalContext.current
    var shortcutApp by remember { mutableStateOf<AppInfo?>(null) }
    var shortcutList by remember { mutableStateOf<List<android.content.pm.ShortcutInfo>>(emptyList()) }
    val holdApp: (AppInfo) -> Unit = { app ->
        val found = if (isEditMode) emptyList() else com.serkantkn.zeta.util.AppShortcuts.of(shortcutsContext, app.packageName)
        if (found.isEmpty()) {
            tilesEditOnly = zuneMode
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

    /** A move on a board that leaves the hubs out, made on the whole board the hubs stay on. */
    fun moveBoardTile(from: Int, to: Int) {
        if (boardTiles === localStartTiles) return moveStartTile(from, to)
        val fromId = boardTiles.getOrNull(from)?.id ?: return
        val toId = boardTiles.getOrNull(to)?.id ?: return
        moveStartTile(localStartTiles.indexOfFirst { it.id == fromId }, localStartTiles.indexOfFirst { it.id == toId })
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
        val animated = if (win8Mode) {
            tileModifier.win8TileMotion(
                progress = { animationProgress.value },
                isClicked = clickedItemKey == launchKey,
                key = launchKey,
                bounds = win8Bounds
            )
        } else if (zuneMode) {
            tileModifier.zuneListMotion(
                progress = { animationProgress.value },
                index = index,
                isClicked = clickedItemKey == launchKey
            )
        } else {
            tileModifier.w10mStaggeredAnimation(
                progress = { animationProgress.value },
                index = index,
                isClicked = clickedItemKey == launchKey
            )
        }
        val onResize = { resizeTargetId = model.id }
        val look = tileLooks[model.id] ?: TileLook.DEFAULT
        if (win8Mode) {
            val splashColor = look.color?.let { Color(it) } ?: zuneColors.accentColor
            val splashFace = when (model) {
                is StartTileUIModel.Hub -> com.serkantkn.zeta.ui.animation.Win8Splash(
                    Rect.Zero, splashColor, glyph = getHubIcon(model.hubType)
                )
                is StartTileUIModel.App -> com.serkantkn.zeta.ui.animation.Win8Splash(
                    Rect.Zero, splashColor, face = tileFace(model.appInfo)
                )
                else -> com.serkantkn.zeta.ui.animation.Win8Splash(Rect.Zero, splashColor)
            }
            SideEffect { win8Faces[launchKey] = splashFace }
        }
        val preview = LocalTileIsPreview.current
        CompositionLocalProvider(
            LocalTileLook provides look,
            LocalTileRunning provides (model is StartTileUIModel.Hub && model.hubType in runningHubs),
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
                    HubType.NEWS -> com.serkantkn.zeta.ui.components.W10MNewsTile(
                        stories = newsStories,
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
                    else -> { val tileContext = androidx.compose.ui.platform.LocalContext.current
                    W10MHubTile(
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
                        // What the wide and large tiles list. Notes and sums are the tile's own words;
                        // the rest are notifications and go with them.
                        liveItems = when (hubType) {
                            HubType.MESSAGING -> if (!look.notifications) emptyList() else unreadMessages.take(5).map {
                                TileLine(it.contactName.ifBlank { it.address }, it.snippet, tileTime(tileContext, it.timestamp))
                            }
                            HubType.PHONE -> if (!look.notifications) emptyList() else missedCallList.map { (who, at) ->
                                TileLine(who, null, tileTime(tileContext, at))
                            }
                            HubType.EMAIL -> if (!look.notifications) emptyList() else emailTileMessages.map { (from, subject, at) ->
                                TileLine(from, subject, tileTime(tileContext, at))
                            }
                            HubType.NOTES -> notesTileNotes.map { (title, body) -> TileLine(title, body) }
                            HubType.CALCULATOR -> calculatorTileHistory.map { TileLine(it) }
                            HubType.FILES -> storage?.let { (free, total) ->
                                listOf(TileLine(
                                    stringResource(R.string.files_internal_storage).lowercase(),
                                    stringResource(R.string.tile_storage_free, formatFileSize(free), formatFileSize(total))
                                ))
                            }.orEmpty()
                            else -> emptyList()
                        },
                        onClick = { handleLaunch(launchKey) { openHub(hubType) } },
                        onLongClick = { isEditMode = true },
                        onRemoveClick = { onRemoveTile(model.id) },
                        onResizeClick = onResize,
                        modifier = animated
                    )
                    }
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
                unreadCount = unreadMessages.firstOrNull { it.threadId == model.threadId }?.unreadCount ?: 0,
                lastMessage = unreadMessages.firstOrNull { it.threadId == model.threadId }?.snippet,
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
                isMergeTarget = isMergeTarget,
                cornerStyle = tileCornerStyle,
                onClick = {
                    handleLaunch(launchKey) {
                        com.serkantkn.zeta.data.repository.MessagingBridge.openThread(model.threadId)
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

    /**
     * The pinned tiles over quickplay on a tablet in the Zune layout: each at its own size, two
     * rows of middle-sized tiles high at most, with the rest a tap away - opened, they push
     * quickplay down rather than scrolling inside themselves.
     */
    @Composable
    fun ZuneTabletTiles() {
        if (boardTiles.isEmpty()) return
        var showAll by remember { mutableStateOf(false) }
        val gap = tileSpacing.dp
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = gridColumns
            val cell = ((maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(1.dp)
            val pitch = cell + gap
            val rows = remember(boardTiles, columns) {
                packMetroTiles(
                    specs = boardTiles.map { model ->
                        val (w, h) = TileSpan.cellsOf(model.span)
                        MetroTileSpec(model.id, w, h)
                    },
                    crossAxisCells = columns,
                    columnMajor = false
                ).maxOfOrNull { it.row + it.rows } ?: 0
            }
            val fullHeight = if (rows == 0) 0.dp else rows * pitch - gap
            val overflows = rows > TABLET_TILE_CELL_ROWS
            val shownHeight by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (showAll || !overflows) fullHeight else TABLET_TILE_CELL_ROWS * pitch - gap,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "zune_tablet_tiles"
            )
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(shownHeight).clipToBounds()) {
                    MetroStartBoard(
                        tiles = boardTiles,
                        columns = columns,
                        gap = gap,
                        contentPadding = PaddingValues(0.dp),
                        onEnterEditMode = {
                            tilesEditOnly = true
                            isEditMode = true
                        },
                        onMoveTile = { from, to -> moveBoardTile(from, to) },
                        onMergeTiles = onMergeTiles,
                        canMerge = canMerge,
                        // A folder opens over the screen here, as it does from the list.
                        openFolder = null,
                        isEditMode = isEditMode,
                        scrollEnabled = false,
                        modifier = Modifier.fillMaxWidth().height(fullHeight),
                        tile = { model, index, isDragging, isMergeTarget, tileModifier ->
                            StartTile(model, index, isDragging, isMergeTarget, tileModifier, gridColumns = columns)
                        }
                    )
                }
                if (overflows) {
                    Text(
                        text = stringResource(if (showAll) R.string.zune_tiles_show_less else R.string.zune_tiles_show_more),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.accentLink,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showAll = !showAll }
                            .padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Any icon a tile was given is turned into a face here, once, for every tile on the screen.
    val iconResolver: (TileIcon) -> TileIconFace? = remember(tileIconStyle) {
        { icon -> viewModel.customIconFace(icon, tileIconStyle) }
    }

    // One box over all three Start layouts.
    CompositionLocalProvider(LocalTileIconResolver provides iconResolver) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE && isWideScreen && !tilesPage) {
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

                // The launch splash, over the board, until the hub or the app is up over it.
                win8Splash?.let { splash ->
                    com.serkantkn.zeta.ui.animation.Win8LaunchSplash(splash) { animationProgress.value }
                }

                // With frosted tiles Start shows its wallpaper bare; the list still needs the
                // shade under its names, so it fades in as the list rises and out as it goes.
                if (appsShown && com.serkantkn.zeta.ui.components.LocalTileBackdrop.current != null) {
                    com.serkantkn.zeta.ui.components.ZuneWallpaperOverlay(
                        modifier = Modifier.graphicsLayer { alpha = appsReveal.value.coerceIn(0f, 1f) }
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
        } else if (homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE || tilesPage) {
            // The Windows Phone board - and, as the tiles page, the Zune layout's
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
                    tiles = boardTiles,
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
                        // The Zune tiles carry no hubs, so there is no hub to put back here.
                        if (isEditMode && !zuneMode) {
                            AddHubButton(missing = hubsOffHome, onClick = { addingHub = true })
                        }
                        if (zuneMode && boardTiles.isEmpty()) {
                            ZuneTilesEmptyState(onOpenAppList = onNavigateToAppsHub)
                        }
                    },
                    onEnterEditMode = { isEditMode = true },
                    onMoveTile = { from, to -> moveBoardTile(from, to) },
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

                // The list's own editing; tiles being edited beside it leave it alone.
                val listEditing = isEditMode && !tilesEditOnly

                // The strip used to hold the favourite apps; once, somebody who had some is told
                // where they went - to the tiles, the next page over.
                val hintContext = LocalContext.current
                LaunchedEffect(favoriteAppsFlow.isNotEmpty()) {
                    if (isWideScreen || favoriteAppsFlow.isEmpty()) return@LaunchedEffect
                    val hints = hintContext.getSharedPreferences(HINTS_PREFS, android.content.Context.MODE_PRIVATE)
                    if (hints.getBoolean(HINT_FAVOURITES_MOVED, false)) return@LaunchedEffect
                    hints.edit().putBoolean(HINT_FAVOURITES_MOVED, true).apply()
                    android.widget.Toast.makeText(
                        hintContext, hintContext.getString(R.string.zune_favourites_moved), android.widget.Toast.LENGTH_LONG
                    ).show()
                }

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
                            // The room on the left is the favourites' strip; with none to show,
                            // the list starts at the screen's own margin instead of an empty gutter.
                            start = if (isWideScreen || stripItems.isNotEmpty()) 72.dp else ZuneDimens.ScreenPaddingHorizontal,
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
                        modifier = Modifier.zuneListMotion(
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
                        modifier = Modifier.zuneListMotion(
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
                            .zuneListMotion({ animationProgress.value }, 3)
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
                    fun moveHub(hubType: com.serkantkn.zeta.data.model.HubId, by: Int) {
                        val order = localHubOrder.toMutableList()
                        val from = order.indexOf(hubType)
                        val to = from + by
                        if (from < 0 || to !in order.indices) return
                        order.add(to, order.removeAt(from))
                        localHubOrder = order
                        viewModel.setHubOrder(order)
                    }

                    // Every name in the list is drawn at one size - the largest at which the
                    // longest of them fits - as on the Zune HD, rather than one long word alone
                    // in smaller type. Measured afresh when the room changes: the favourites'
                    // strip coming or going, or the edit buttons taking their share of each row.
                    val fittedSizes = remember(stripItems.isEmpty(), listEditing) { mutableStateMapOf<String, androidx.compose.ui.unit.TextUnit>() }
                    val listSize = fittedSizes.values.minByOrNull { it.value } ?: ZuneDimens.HubTitleFontSize

                    // The rows of the list: one a hub, except music, which the Zune HD kept as two
                    // words - "music" and "videos" - and which opens at either.
                    data class ZuneRow(val hubType: com.serkantkn.zeta.data.model.HubId, val key: String, val title: String, val videos: Boolean)
                    val musicWord = stringResource(R.string.zune_row_music)
                    val videosWord = stringResource(R.string.music_tab_videos)
                    val rows = localHubOrder.flatMap { hubType ->
                        val title = com.serkantkn.zeta.ui.components.hubTitle(hubType)
                        if (hubType == HubType.MUSIC && !listEditing) listOf(
                            ZuneRow(hubType, "hub_${hubType.key}", musicWord, videos = false),
                            ZuneRow(hubType, "hub_${hubType.key}_videos", videosWord, videos = true)
                        ) else listOf(ZuneRow(hubType, "hub_${hubType.key}", title, videos = false))
                    }
                    LaunchedEffect(rows.map { it.key }) { fittedSizes.keys.retainAll(rows.map { it.key }.toSet()) }

                    val hubFlight = com.serkantkn.zeta.ui.animation.LocalZuneHubFlight.current
                    rows.forEachIndexed { rowIndex, row ->
                      androidx.compose.runtime.key(row.key) {
                        val hubType = row.hubType
                        val key = row.key
                        // Every hub takes its name with them: it flies up and becomes its title
                        // (ZuneHubFlight), and comes back down to the list when the hub closes.
                        // A phone only; a tablet's hubs have no such title.
                        val flightKey = when {
                            isWideScreen || hubFlight == null -> null
                            // "videolar" lands on the videos page's title, "müzik" on the hub's.
                            hubType == HubType.MUSIC && row.videos ->
                                com.serkantkn.zeta.ui.screens.music.musicTitleKey(com.serkantkn.zeta.ui.screens.music.MUSIC_VIDEOS_PAGE)
                            hubType == HubType.MUSIC -> com.serkantkn.zeta.ui.screens.music.MUSIC_TITLE_KEY
                            hubType in UNTITLED_HUBS -> null
                            else -> hubType.key
                        }
                        val wordAnchor = com.serkantkn.zeta.ui.animation.rememberZuneZoomAnchor()
                        val index = localHubOrder.indexOf(hubType)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (listEditing) {
                                ZuneListEditButton(
                                    icon = Icons.Default.Close,
                                    label = stringResource(R.string.home_running_hub_close),
                                    onClick = { onRemoveTile("hub:${hubType.key}") }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // The list has no tile to customise, so what the title opens is
                                // set from here. Another app's hub is that app already.
                                if (hubType is HubType) {
                                    ZuneListEditButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        label = stringResource(R.string.hub_target_title),
                                        filled = hubTargetApps.containsKey(hubType),
                                        onClick = { hubTargetPickFor = hubType }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
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
                            val line = if (row.videos) videosLine() else liveLineFor(hubType)
                            val ink = if (zuneColors.isDark) Color.White else Color.Black
                            Column(
                                modifier = Modifier
                                    .zuneListMotion(
                                        progress = { animationProgress.value },
                                        index = 4 + rowIndex,
                                        // A name that flies to its hub does not zoom through.
                                        isClicked = clickedItemKey == key && flightKey == null,
                                        steady = { hubFlight?.lands(key) == true }
                                    )
                                    .graphicsLayer {
                                        // The word in the air is the flight's; the list's copy keeps out of sight.
                                        alpha = if (hubFlight?.hidesItem(key) == true) 0f else 1f
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.zuneRunningMark(
                                        running = hubType in runningHubs && !listEditing,
                                        color = zuneColors.accentColor
                                    )
                                ) {
                                    val nameColour = rememberHubNameColour(line.hasNews, ink)
                                    ZuneHubTitle(
                                        title = row.title,
                                        accentColor = nameColour,
                                        anchor = wordAnchor,
                                        verticalPadding = if (isWideScreen) ZuneDimens.SpacingXs else 0.dp,
                                        fontSize = listSize,
                                        // Re-fitted when the edit buttons take their share of the row.
                                        fitKey = listEditing,
                                        // Beside the edit buttons "music + videos" needs smaller type.
                                        minFontSize = if (listEditing) 20.sp else 28.sp,
                                        onFitted = { size -> if (fittedSizes[key] != size) fittedSizes[key] = size },
                                        onClick = {
                                            if (isFavoritesExpanded) {
                                                isFavoritesExpanded = false
                                            } else if (listEditing) {
                                                isEditMode = false
                                            } else {
                                                val from = wordAnchor.bounds
                                                // A hub pointed at another app opens the app; nothing flies.
                                                val flies = flightKey != null && from != null && animationsOn &&
                                                    !hubTargetApps.containsKey(hubType)
                                                if (flies && !launching && !isEditMode) {
                                                    hubFlight!!.launch(flightKey!!, row.title, from!!, listSize, nameColour.copy(alpha = 0.85f), hubType.key, key)
                                                }
                                                handleLaunch(
                                                    key,
                                                    zuneOpenAt = if (flies) com.serkantkn.zeta.ui.animation.ZUNE_FLIGHT_OPEN_AT_MILLIS
                                                        else com.serkantkn.zeta.ui.animation.ZUNE_OPEN_AT_MILLIS
                                                ) {
                                                    if (row.videos) com.serkantkn.zeta.data.repository.MusicBridge.openVideos()
                                                    openHub(hubType)
                                                }
                                            }
                                        },
                                        // The Zune list has no tiles to hold, so its titles are what
                                        // opens editing.
                                        onLongClick = { isEditMode = true }
                                    )
                                    if (!listEditing) {
                                        ZuneHubCount(line.count, modifier = Modifier.padding(start = 6.dp, top = 12.dp))
                                    }
                                }
                                // Editing wants a plain list; the lines come back when it is done.
                                if (!listEditing) ZuneHubLiveLine(line.text)
                            }
                        }
                      }
                    }

                    if (listEditing) {
                        AddHubButton(missing = hubsOffHome, onClick = { addingHub = true })
                    }

                    if (!listEditing) {
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
                // LAYER 2 — Quickplay
                //
                // Phone: a strip of small squares down the left - the
                // pins, then what was lately used - that widens into
                // the whole of quickplay as the list slides aside.
                // Tablet: quickplay stands open at the right, under
                // the pinned tiles.
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
                    // ── The strip ──
                    if (!isWideScreen && p < 0.7f) {
                        val collapsedAlpha = lerp(0.9f, 0f, (p / 0.5f).coerceIn(0f, 1f))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                // Lined up with the hub names once they have been measured; until
                                // then it is not shown, rather than shown at a guess.
                                .graphicsLayer { alpha = if (hubTitlesTopPx > 0f) collapsedAlpha else 0f },
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
                                count = stripItems.size,
                                key = { index -> "q_${stripItems[index].kind}_${stripItems[index].key}" }
                            ) { index ->
                                val item = stripItems[index]
                                val key = "quickplay_${item.kind}_${item.key}"
                                val app = if (item.kind == QuickplayPin.Kind.APP) viewModel.appFor(item.key) else null
                                QuickplayStripSquare(
                                    item = item,
                                    face = app?.let(tileFace),
                                    cornerStyle = tileCornerStyle,
                                    onClick = {
                                        handleLaunch(key) { openQuickplayItem(item, lineContext, viewModel, onHubSelected) }
                                    },
                                    // Held, the strip opens into the whole of quickplay, where
                                    // anything can be pinned or taken off.
                                    onLongClick = { isFavoritesExpanded = true },
                                    modifier = Modifier.zuneListMotion(
                                        { animationProgress.value },
                                        2 + index,
                                        clickedItemKey == key
                                    )
                                )
                            }
                        }
                    }

                    // ── Quickplay itself ──
                    if (isWideScreen || p > 0.3f) {
                        val expandedAlpha = if (isWideScreen) 1f else lerp(0f, 1f, ((p - 0.3f) / 0.5f).coerceIn(0f, 1f))
                        QuickplayScreen(
                            isCurrentPage = if (isWideScreen) isCurrentPage else isFavoritesExpanded,
                            isHubOpen = isHubOpen,
                            onHubSelected = onHubSelected,
                            modifier = Modifier.graphicsLayer { alpha = expandedAlpha },
                            // On a tablet the pinned tiles stand over it; a phone has a page of them.
                            top = if (isWideScreen) {
                                { ZuneTabletTiles() }
                            } else {
                                null
                            },
                            topGap = if (isWideScreen) 24.dp else 36.dp,
                            viewModel = viewModel
                        )
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
                    if (!com.serkantkn.zeta.util.AppShortcuts.start(shortcutsContext, shortcut)) {
                        viewModel.launchApp(app.packageName)
                    }
                },
                onEdit = {
                    shortcutApp = null
                    tilesEditOnly = zuneMode
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



/** How long the splash stays under a hub that has come up over it. */
private const val WIN8_SPLASH_LINGER_MILLIS = 450L

/** How long a splash may cover the screen with nothing coming up over it. */
private const val WIN8_SPLASH_GIVE_UP_MILLIS = 2_000L

/**
 * The Zune layout's tiles page before anything is pinned: what it is for, and the way to the app
 * list, where an app is held and pinned.
 */
@Composable
private fun ZuneTilesEmptyState(onOpenAppList: () -> Unit, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier.padding(top = 8.dp, end = 24.dp)) {
        Text(
            text = stringResource(R.string.zune_tiles_empty_line),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light, lineHeight = 26.sp),
            color = zuneColors.textMuted
        )
        Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wpTilt(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onOpenAppList)
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
    }
}

/** One-time hints, and the one that says the favourites became the tiles page. */
private const val HINTS_PREFS = "hints"
private const val HINT_FAVOURITES_MOVED = "zune_favourites_moved"

/** The hubs with no title for a name to fly to: the camera is its viewfinder from edge to edge. */
private val UNTITLED_HUBS = setOf(HubType.CAMERA, HubType.HOME)

/** How many squares the strip beside the Zune list holds. */
private const val QUICKPLAY_STRIP_ITEMS = 8

/** How high the tablet's tiles over quickplay stand before "show more": two middle-sized rows. */
private const val TABLET_TILE_CELL_ROWS = 4

/**
 * When something on a live tile happened: the time for today, "dün" for yesterday, the date
 * before that - in the phone's own 12- or 24-hour clock.
 */
private fun tileTime(context: android.content.Context, millis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayGap = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayGap == 0 -> android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))
        sameYear && dayGap == 1 -> context.getString(R.string.common_yesterday)
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(millis)).lowercase()
    }
}
