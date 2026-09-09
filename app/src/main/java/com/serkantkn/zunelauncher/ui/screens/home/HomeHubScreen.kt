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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.serkantkn.zunelauncher.ui.components.W10MHubTile
import com.serkantkn.zunelauncher.ui.components.W10MCalendarTile
import com.serkantkn.zunelauncher.ui.components.W10MClockTile
import com.serkantkn.zunelauncher.ui.components.W10MNoteTile
import com.serkantkn.zunelauncher.data.repository.NotesBridge
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeHubScreen(
    isHubOpen: Boolean = false,
    isCurrentPage: Boolean = true,
    onHubSelected: (HubType) -> Unit,
    onNavigateToSocialHub: () -> Unit = {},
    onNavigateToAppsHub: () -> Unit = {},
    onExpandProgressChange: (Float) -> Unit = {},
    timeFormat: String = "HH:mm",
    dateFormat: String = "EEEE, MMMM d",
    modifier: Modifier = Modifier,
    viewModel: HomeHubViewModel = viewModel()
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
    val latestMessages by viewModel.latestMessages.collectAsState()
    val tileCornerStyle by viewModel.tileCornerStyle.collectAsState()
    val tileSpacing by viewModel.tileSpacing.collectAsState()
    val tileColumns by viewModel.tileColumns.collectAsState()
    val homeScreenLayout by viewModel.homeScreenLayout.collectAsState()
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current

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

    LaunchedEffect(expandProgress.value) {
        onExpandProgressChange(expandProgress.value)
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

    /** One Start tile: hub (with badge / live face), favourite app, or note. */
    @Composable
    fun StartTile(
        model: StartTileUIModel,
        index: Int,
        isDragging: Boolean,
        tileModifier: Modifier,
        gridColumns: Int
    ) {
        val launchKey = when (model) {
            is StartTileUIModel.Hub -> "hub_${model.hubType.name}"
            is StartTileUIModel.App -> "app_${model.appInfo.packageName}"
            is StartTileUIModel.NoteTile -> "note_${model.note.id}"
            is StartTileUIModel.QuickNote -> "note_new"
        }
        val animated = tileModifier.w10mStaggeredAnimation(
            progress = animationProgress.value,
            index = index,
            isClicked = clickedItemKey == launchKey
        )
        val onResize = { resizeTargetId = model.id }
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
                        onClick = { handleLaunch(launchKey) { onHubSelected(hubType) } },
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
                        onClick = { handleLaunch(launchKey) { onHubSelected(hubType) } },
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
                        badgeCount = when (hubType) {
                            HubType.MESSAGING -> notificationCounts["com.google.android.apps.messaging"] ?: 0
                            HubType.PHONE -> notificationCounts["com.google.android.dialer"] ?: 0
                            HubType.NOTES -> pinnedNotesCount
                            HubType.EMAIL -> emailUnreadCount
                            else -> 0
                        },
                        liveSubtitle = when (hubType) {
                            HubType.NOTES -> notesTileSubtitle
                            HubType.CALCULATOR -> calculatorTileSubtitle
                            else -> null
                        },
                        onClick = { handleLaunch(launchKey) { onHubSelected(hubType) } },
                        onLongClick = { isEditMode = true },
                        onRemoveClick = { onRemoveTile(model.id) },
                        onResizeClick = onResize,
                        modifier = animated
                    )
                }
            }
            is StartTileUIModel.App -> W10MAppTile(
                label = model.appInfo.label,
                icon = viewModel.getAppIcon(model.appInfo.packageName),
                span = model.span,
                gridColumns = gridColumns,
                spacing = tileSpacing.dp,
                isEditing = isEditMode,
                isDragging = isDragging,
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
                onEnterEditMode = { isEditMode = true },
                onMoveTile = { from, to -> moveStartTile(from, to) },
                onOpenApps = onNavigateToAppsHub,
                onOpenSettings = { handleLaunch("hub_SETTINGS") { onHubSelected(HubType.SETTINGS) } },
                tile = { model, index, isDragging, tileModifier ->
                    StartTile(model, index, isDragging, tileModifier, gridColumns = 4)
                }
            )

            TileSizePopup()
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
            val gridCols = if (isWideScreen) tileColumns * 2 else tileColumns

            MetroStartBoard(
                tiles = localStartTiles,
                columns = gridCols,
                gap = tileSpacing.dp,
                contentPadding = PaddingValues(
                    start = if (isWideScreen) 32.dp else 12.dp,
                    end = if (isWideScreen) 32.dp else 12.dp,
                    top = statusBarPadding + 16.dp,
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 24.dp
                ),
                onEnterEditMode = { isEditMode = true },
                onMoveTile = { from, to -> moveStartTile(from, to) },
                tile = { model, index, isDragging, tileModifier ->
                    StartTile(model, index, isDragging, tileModifier, gridColumns = gridCols)
                }
            )

            TileSizePopup()
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
                    .verticalScroll(rememberScrollState())
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
                            handleLaunch("hub_CLOCK") { onHubSelected(HubType.CLOCK) }
                        }
                    },
                    modifier = Modifier.w10mStaggeredAnimation(
                        progress = animationProgress.value,
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
                            handleLaunch("hub_CALENDAR") { onHubSelected(HubType.CALENDAR) }
                        }
                    },
                    modifier = Modifier.w10mStaggeredAnimation(
                        progress = animationProgress.value,
                        index = 2,
                        isClicked = clickedItemKey == "hub_CALENDAR"
                    )
                )
                ZuneWeather(
                    modifier = Modifier
                        .padding(top = ZuneDimens.SpacingSm)
                        .w10mStaggeredAnimation(animationProgress.value, 3)
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
                    ZuneHubTitle(
                        title = stringResource(hubType.titleRes),
                        accentColor = if (zuneColors.isDark) Color.White else Color.Black,
                        verticalPadding = if (isWideScreen) ZuneDimens.SpacingXs else 0.dp,
                        onClick = {
                            if (isFavoritesExpanded) {
                                isFavoritesExpanded = false
                            } else {
                                handleLaunch(key) { onHubSelected(hubType) }
                            }
                        },
                        modifier = Modifier.w10mStaggeredAnimation(
                            progress = animationProgress.value,
                            index = 4 + index,
                            isClicked = clickedItemKey == key
                        )
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
                            SmallFavoriteTile(
                                icon = viewModel.getAppIcon(favApp.appInfo.packageName),
                                label = favApp.appInfo.label,
                                onClick = { isFavoritesExpanded = true },
                                modifier = Modifier
                                    .w10mStaggeredAnimation(animationProgress.value, 2 + index)
                            )
                        }
                    }
                }

                // ── Expanded favourites (4-column grid with editing) ──
                if (isWideScreen || p > 0.3f) {
                    val expandedAlpha = if (isWideScreen) 1f else lerp(0f, 1f, ((p - 0.3f) / 0.5f).coerceIn(0f, 1f))

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(if (isWideScreen) tileColumns * 2 else tileColumns),
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
                        if (favoriteApps.isNotEmpty()) {
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
                                        .w10mStaggeredAnimation(animationProgress.value, 8)
                                        .graphicsLayer {
                                            translationY = with(localDensity) { (-24).dp.toPx() }
                                        }
                                        .padding(bottom = 4.dp)
                                )
                            }

                            favoriteApps.forEachIndexed { favIndex, favApp ->
                                val absoluteIndex = favIndex + 1 // label is item 0
                                val isDragging =
                                    gridDragDropState.draggingItemIndex == absoluteIndex

                                item(
                                    key = "fav_${favApp.appInfo.packageName}",
                                    span = { GridItemSpan(favApp.span) }
                                ) {
                                    val key = "app_${favApp.appInfo.packageName}"

                                    W10MAppTile(
                                        label = favApp.appInfo.label,
                                        icon = viewModel.getAppIcon(favApp.appInfo.packageName),
                                        span = favApp.span.coerceAtMost(if (isWideScreen) tileColumns * 2 else tileColumns),
                                        gridColumns = if (isWideScreen) tileColumns * 2 else tileColumns,
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
                                                animationProgress.value,
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

            TileSizePopup()
        }
    }
}



// ────────────────────────────────────────────────────────
// Small favourite tile used in the collapsed single-column
// ────────────────────────────────────────────────────────
@Composable
private fun SmallFavoriteTile(
    icon: Drawable?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val stroke = if (zuneColors.isDark)
        Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(zuneColors.accentColor)
                .border(0.5.dp, stroke, RoundedCornerShape(8.dp))
        ) {
            icon?.let { drawable ->
                val bitmap = remember(drawable) { drawable.toImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}
