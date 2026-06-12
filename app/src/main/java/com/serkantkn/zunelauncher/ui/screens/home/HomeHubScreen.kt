package com.serkantkn.zunelauncher.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zunelauncher.ui.components.W10MAppTile
import com.serkantkn.zunelauncher.ui.components.ZuneClock
import com.serkantkn.zunelauncher.ui.components.ZuneDate
import com.serkantkn.zunelauncher.ui.components.ZuneHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWeather
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.annotation.SuppressLint
import android.util.Log

@SuppressLint("WrongConstant")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeHubScreen(
    isHubOpen: Boolean = false,
    isCurrentPage: Boolean = true,
    onHubSelected: (HubType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeHubViewModel = viewModel()
) {
    val favoriteAppsFlow by viewModel.favoriteApps.collectAsState()
    val hubOrderFlow by viewModel.hubOrder.collectAsState()
    val latestNotification by viewModel.latestNotification.collectAsState()
    val zuneColors = LocalZuneColors.current

    var favoriteApps by remember(favoriteAppsFlow) { mutableStateOf(favoriteAppsFlow) }
    var localHubOrder by remember(hubOrderFlow) { mutableStateOf(hubOrderFlow) }

    var isEditMode by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
    }

    val animationProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasRunInitialAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasRunInitialAnimation) {
            hasRunInitialAnimation = true
            animationProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (hasRunInitialAnimation) {
                    coroutineScope.launch {
                        animationProgress.snapTo(0f)
                        animationProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                isEditMode = false
            }
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
        }
    }

    LaunchedEffect(isHubOpen) {
        if (!isHubOpen && animationProgress.value > 1f) {
            clickedItemKey = null
            animationProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
            )
        }
    }

    fun handleLaunch(key: String, action: () -> Unit) {
        if (isEditMode) return
        clickedItemKey = key
        coroutineScope.launch {
            animationProgress.animateTo(
                targetValue = 2f,
                animationSpec = tween(durationMillis = 700, easing = androidx.compose.animation.core.LinearEasing)
            )
            action()
        }
    }

    val gridState = rememberLazyGridState()
    
    fun handleHeaderClick(action: () -> Unit) {
        if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
            coroutineScope.launch {
                gridState.animateScrollToItem(0)
            }
        } else {
            action()
        }
    }
    val density = LocalDensity.current

    var headerHeightPx by remember { mutableStateOf(0f) }
    val minHeaderHeightPx = with(density) { 150.dp.toPx() }
    val maxTransitionPx = (headerHeightPx - minHeaderHeightPx).coerceAtLeast(0f)

    val headerScrollOffset by remember(maxTransitionPx) {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex == 0) {
                gridState.firstVisibleItemScrollOffset.toFloat().coerceIn(0f, maxTransitionPx)
            } else {
                maxTransitionPx
            }
        }
    }

    val gridDragDropState = rememberGridDragDropState(
        gridState = gridState,
        isEditMode = isEditMode,
        canSwap = { _, _ -> true },
        onMove = { fromIndex, toIndex ->
            // In the grid, spacer is 0, fav_label is 1, tiles start at 2
            if (fromIndex >= 2 && toIndex >= 2) {
                val fromFavIndex = fromIndex - 2
                val toFavIndex = toIndex - 2
                val newList = favoriteApps.toMutableList()
                val item = newList.removeAt(fromFavIndex)
                val insertIndex = if (toFavIndex > newList.size) newList.size else toFavIndex
                newList.add(insertIndex, item)
                favoriteApps = newList
                viewModel.updateFavoritesOrder(newList)
            }
        }
    )

    // Intercept fling for the magnet snap effect
    
    val context = LocalContext.current

    val nestedScrollConnection = remember(maxTransitionPx) {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (maxTransitionPx <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                val offset = headerScrollOffset
                if (offset > 0 && offset < maxTransitionPx) {
                    val velocityY = available.y
                    // If you scroll up lightly, it goes back. If you scroll enough, it snaps to top.
                    // "Ufacık bir hareket yeterli olmalı"
                    val targetOffset = if (velocityY < -50f) maxTransitionPx else if (velocityY > 50f) 0f else if (offset > maxTransitionPx / 4) maxTransitionPx else 0f
                    
                    coroutineScope.launch {
                        gridState.animateScrollToItem(0, targetOffset.toInt())
                    }
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // 1. FOREGROUND GRID (Scrollable Favorites)
        if (headerHeightPx > 0f) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    top = 0.dp,
                    end = ZuneDimens.ScreenPaddingHorizontal,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp + 150.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Header spacer to push tiles down
                item(key = "header_spacer", span = { GridItemSpan(4) }) {
                    val heightDp = with(density) { headerHeightPx.toDp() }
                    Spacer(modifier = Modifier.height(heightDp))
                }

                if (favoriteApps.isNotEmpty()) {
                    item(key = "fav_label", span = { GridItemSpan(4) }) {
                        val favProgress = if (maxTransitionPx > 0f) (headerScrollOffset / maxTransitionPx).coerceIn(0f, 1f) else 0f
                        val dynamicFontSize = 24.dp + ((32.dp - 24.dp) * favProgress)
                        
                        Text(
                            text = "favoriler",
                            fontSize = with(density) { dynamicFontSize.toSp() },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                            color = zuneColors.textMuted,
                            modifier = Modifier
                                .w10mStaggeredAnimation(animationProgress.value, 8)
                                .padding(vertical = ZuneDimens.SpacingMd)
                        )
                    }

                    favoriteApps.forEachIndexed { favIndex, favApp ->
                        val absoluteIndex = favIndex + 2 // spacer + label + tiles
                        val isDragging = gridDragDropState.draggingItemIndex == absoluteIndex

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
                                onClick = { handleLaunch(key) { viewModel.launchApp(favApp.appInfo.packageName) } },
                                onLongClick = { isEditMode = true },
                                onRemoveClick = { viewModel.removeFavorite(favApp.appInfo.packageName) },
                                onResizeClick = { viewModel.toggleAppSize(favApp.appInfo.packageName) },
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
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .pointerInput(isEditMode) {
                                        if (!isEditMode) return@pointerInput
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { gridDragDropState.startDrag(absoluteIndex) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                gridDragDropState.onDrag(dragAmount)
                                            },
                                            onDragEnd = { gridDragDropState.onDragInterrupted() },
                                            onDragCancel = { gridDragDropState.onDragInterrupted() }
                                        )
                                    }
                                    .graphicsLayer {
                                        if (isDragging) {
                                            val currentLayoutOffset = gridState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == absoluteIndex }
                                                ?.offset
                                            if (currentLayoutOffset != null) {
                                                val desiredX = gridDragDropState.draggingItemInitialOffset.x + gridDragDropState.totalDragAmount.x
                                                val desiredY = gridDragDropState.draggingItemInitialOffset.y + gridDragDropState.totalDragAmount.y
                                                this.translationX = desiredX - currentLayoutOffset.x
                                                this.translationY = desiredY - currentLayoutOffset.y
                                            }
                                        }
                                    }
                                    .w10mStaggeredAnimation(animationProgress.value, 9 + favIndex, clickedItemKey == key)
                            )
                        }
                    }
                }
            }
        }

        // 2. BACKGROUND HEADER (Pinned and Shrunk dynamically)
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                .scrollable(
                    state = gridState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true
                )
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val originalHeight = placeable.height.toFloat()
                    if (originalHeight > 0f && headerHeightPx != originalHeight) {
                        coroutineScope.launch { headerHeightPx = originalHeight }
                    }
                    
                    val progress = if (maxTransitionPx > 0f) (headerScrollOffset / maxTransitionPx).coerceIn(0f, 1f) else 0f
                    val targetScale = if (originalHeight > 0) minHeaderHeightPx / originalHeight else 1f
                    val scale = 1f - (progress * (1f - targetScale))
                    val currentHeight = (originalHeight * scale).toInt()
                    
                    layout(placeable.width, currentHeight) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .graphicsLayer {
                    if (headerHeightPx > 0f) {
                        val progress = if (maxTransitionPx > 0f) (headerScrollOffset / maxTransitionPx).coerceIn(0f, 1f) else 0f
                        val targetScale = minHeaderHeightPx / headerHeightPx
                        val scale = 1f - (progress * (1f - targetScale))

                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (progress * 0.4f)
                        transformOrigin = TransformOrigin(0f, 0f) // PIN TO LEFT EDGE
                    }
                }
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            
            ZuneClock(
                notification = latestNotification,
                onClick = { handleHeaderClick { onHubSelected(HubType.CLOCK) } },
                modifier = Modifier.w10mStaggeredAnimation(animationProgress.value, 1)
            )
            ZuneDate(
                modifier = Modifier.w10mStaggeredAnimation(animationProgress.value, 2)
            )
            ZuneWeather(
                modifier = Modifier
                    .padding(top = ZuneDimens.SpacingSm)
                    .w10mStaggeredAnimation(animationProgress.value, 3)
            )

            Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))

            localHubOrder.forEachIndexed { index, hubType ->
                ZuneHubTitle(
                    title = hubType.title, // Fixed: use translated title instead of English name
                    accentColor = zuneColors.textMuted,
                    onClick = { handleHeaderClick { handleLaunch("hub_$hubType") { onHubSelected(hubType) } } },
                    modifier = Modifier.w10mStaggeredAnimation(animationProgress.value, 4 + index)
                )
            }
        }
    }
}
