package com.serkantkn.zunelauncher.ui.screens.home

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.components.W10MAppTile
import com.serkantkn.zunelauncher.ui.components.ZuneClock
import com.serkantkn.zunelauncher.ui.components.ZuneDate
import com.serkantkn.zunelauncher.ui.components.ZuneGlassSurface
import com.serkantkn.zunelauncher.ui.components.ZuneHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWallpaperOverlay
import com.serkantkn.zunelauncher.ui.components.ZuneWeather
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.toImageBitmap
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeHubScreen(
    isHubOpen: Boolean = false,
    isCurrentPage: Boolean = true,
    onHubSelected: (HubType) -> Unit,
    onExpandProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeHubViewModel = viewModel()
) {
    val favoriteAppsFlow by viewModel.favoriteApps.collectAsState()
    val hubOrderFlow by viewModel.hubOrder.collectAsState()
    val latestNotification by viewModel.latestNotification.collectAsState()
    val notificationCounts by viewModel.notificationCounts.collectAsState()
    val latestMessages by viewModel.latestMessages.collectAsState()
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current

    var favoriteApps by remember(favoriteAppsFlow) { mutableStateOf(favoriteAppsFlow) }
    var localHubOrder by remember(hubOrderFlow) { mutableStateOf(hubOrderFlow) }

    // ── State ──
    var isEditMode by remember { mutableStateOf(false) }
    var isFavoritesExpanded by remember { mutableStateOf(false) }

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
            animationProgress.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && hasRunInitialAnimation) {
                coroutineScope.launch {
                    animationProgress.snapTo(0f)
                    animationProgress.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
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
            animationProgress.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
        }
    }

    fun handleLaunch(key: String, action: () -> Unit) {
        if (isEditMode) return
        clickedItemKey = key
        coroutineScope.launch {
            animationProgress.animateTo(2f, tween(700, easing = LinearEasing))
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
                onClick = {
                    if (isFavoritesExpanded) {
                        isFavoritesExpanded = false
                    } else {
                        onHubSelected(HubType.CLOCK)
                    }
                },
                modifier = Modifier.w10mStaggeredAnimation(animationProgress.value, 1)
            )
            ZuneDate(
                onClick = { onHubSelected(HubType.CALENDAR) },
                modifier = Modifier.w10mStaggeredAnimation(animationProgress.value, 2)
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
                ZuneHubTitle(
                    title = hubType.title,
                    accentColor = if (zuneColors.isDark) Color.White else Color.Black,
                    verticalPadding = if (isWideScreen) ZuneDimens.SpacingXs else 0.dp,
                    onClick = {
                        if (isFavoritesExpanded) {
                            isFavoritesExpanded = false
                        } else {
                            handleLaunch("hub_$hubType") { onHubSelected(hubType) }
                        }
                    },
                    modifier = Modifier.w10mStaggeredAnimation(animationProgress.value, 4 + index)
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
                    columns = GridCells.Fixed(if (isWideScreen) 12 else 4),
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
                        start = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal,
                        end = 8.dp,
                        top = 80.dp,
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (favoriteApps.isNotEmpty()) {
                        item(key = "fav_label", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "favoriler",
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
                                    .graphicsLayer {
                                        translationX = translationX
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
                                    span = favApp.span,
                                    isEditing = isEditMode,
                                    isDragging = isDragging,
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
                                        viewModel.toggleAppSize(favApp.appInfo.packageName)
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
