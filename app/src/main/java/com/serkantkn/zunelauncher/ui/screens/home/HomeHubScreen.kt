package com.serkantkn.zunelauncher.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import java.util.Collections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

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

    // Local state for UI reordering so we don't spam the DB
    var favoriteApps by remember(favoriteAppsFlow) { mutableStateOf(favoriteAppsFlow) }
    var localHubOrder by remember(hubOrderFlow) { mutableStateOf(hubOrderFlow) }

    var isEditMode by remember { mutableStateOf(false) }

    // Close edit mode on back press
    BackHandler(enabled = isEditMode && isCurrentPage) {
        isEditMode = false
    }

    // Global progress: 0f (Wait to Enter) -> 1f (Idle) -> 2f (Exit)
    val animationProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe app lifecycle to trigger enter animation on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    animationProgress.snapTo(0f)
                    animationProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
                    )
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

    // Reset animation instantly when swiping away so it doesn't play the enter animation when swiping back
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            clickedItemKey = null
            animationProgress.snapTo(1f)
            isEditMode = false
        }
    }

    // Trigger enter animation when returning from a Hub
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

    // Wrapper to play exit animation before executing action
    fun handleLaunch(key: String, action: () -> Unit) {
        if (isEditMode) return // Don't launch apps in edit mode
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

    val dragDropState = rememberGridDragDropState(
        gridState = gridState,
        isEditMode = isEditMode,
        canSwap = { draggingIndex, targetIndex ->
            val hubCount = localHubOrder.size
            val favStartIndex = 3 + hubCount + 1
            
            val inHubs = draggingIndex in 3 until 3 + hubCount && targetIndex in 3 until 3 + hubCount
            val inFavs = draggingIndex >= favStartIndex && targetIndex >= favStartIndex
            
            inHubs || inFavs
        },
        onMove = { fromIndex, toIndex ->
            val hubCount = localHubOrder.size
            val favStartIndex = 3 + hubCount + 1

            if (fromIndex in 3 until 3 + hubCount && toIndex in 3 until 3 + hubCount) {
                val fromHubIndex = fromIndex - 3
                val toHubIndex = toIndex - 3
                val newList = localHubOrder.toMutableList()
                val item = newList.removeAt(fromHubIndex)
                val insertIndex = if (toHubIndex > newList.size) newList.size else toHubIndex
                newList.add(insertIndex, item)
                localHubOrder = newList
                viewModel.updateHubOrder(newList)
            } else if (fromIndex >= favStartIndex && toIndex >= favStartIndex) {
                val fromFavIndex = fromIndex - favStartIndex
                val toFavIndex = toIndex - favStartIndex
                val newList = favoriteApps.toMutableList()
                val item = newList.removeAt(fromFavIndex)
                val insertIndex = if (toFavIndex > newList.size) newList.size else toFavIndex
                newList.add(insertIndex, item)
                favoriteApps = newList
                viewModel.updateFavoritesOrder(newList)
            }
        }
    )

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        contentPadding = PaddingValues(
            top = 80.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ── Clock (full-width span) ──
        item(key = "clock", span = { GridItemSpan(4) }) {
            ZuneClock(
                notification = latestNotification,
                modifier = Modifier
                    .w10mStaggeredAnimation(animationProgress.value, 0)
                    .padding(bottom = ZuneDimens.SpacingXs)
            )
        }

        // ── Date ──
        item(key = "date", span = { GridItemSpan(4) }) {
            ZuneDate(
                modifier = Modifier
                    .w10mStaggeredAnimation(animationProgress.value, 1)
                    .padding(bottom = ZuneDimens.SpacingMd)
            )
        }

        // ── Weather ──
        item(key = "weather", span = { GridItemSpan(4) }) {
            ZuneWeather(
                modifier = Modifier
                    .w10mStaggeredAnimation(animationProgress.value, 2)
                    .padding(bottom = ZuneDimens.SpacingXxl)
            )
        }

        // ── Hub titles (full-width) ──
        localHubOrder.forEachIndexed { hubIndex, hubType ->
            val absoluteIndex = 3 + hubIndex
            val isDragging = dragDropState.draggingItemIndex == absoluteIndex

            item(key = "hub_${hubType.name}", span = { GridItemSpan(4) }) {
                ZuneHubTitle(
                    title = hubType.title,
                    accentColor = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        if (!isEditMode) {
                            handleLaunch("hub_${hubType.name.lowercase()}") { onHubSelected(hubType) }
                        }
                    },
                    onLongClick = { isEditMode = true },
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
                                onDragStart = { dragDropState.startDrag(absoluteIndex) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDropState.onDrag(dragAmount)
                                },
                                onDragEnd = { dragDropState.onDragInterrupted() },
                                onDragCancel = { dragDropState.onDragInterrupted() }
                            )
                        }
                        .graphicsLayer {
                            if (isDragging) {
                                val currentLayoutOffset = gridState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == absoluteIndex }
                                    ?.offset
                                if (currentLayoutOffset != null) {
                                    val desiredX = dragDropState.draggingItemInitialOffset.x + dragDropState.totalDragAmount.x
                                    val desiredY = dragDropState.draggingItemInitialOffset.y + dragDropState.totalDragAmount.y
                                    this.translationX = desiredX - currentLayoutOffset.x
                                    this.translationY = desiredY - currentLayoutOffset.y
                                }
                            }
                        }
                        .w10mStaggeredAnimation(animationProgress.value, 3 + hubIndex, clickedItemKey == "hub_${hubType.name.lowercase()}")
                        // Apply extra padding to the last hub title to match previous layout
                        .padding(bottom = if (hubIndex == localHubOrder.lastIndex) ZuneDimens.SpacingXl else 0.dp)
                )
            }
        }

        // ── Favorites (4-column W10M tiles, vertical) ──
        if (favoriteApps.isNotEmpty()) {
            item(key = "fav_label", span = { GridItemSpan(4) }) {
                Text(
                    text = "favoriler",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Light
                    ),
                    color = zuneColors.textMuted,
                    modifier = Modifier
                        .w10mStaggeredAnimation(animationProgress.value, 8)
                        .padding(bottom = ZuneDimens.SpacingMd)
                )
            }

            val hubCount = localHubOrder.size
            val favStartIndex = 3 + hubCount + 1

            favoriteApps.forEachIndexed { favIndex, favApp ->
                val absoluteIndex = favStartIndex + favIndex
                val isDragging = dragDropState.draggingItemIndex == absoluteIndex

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
                                    onDragStart = { dragDropState.startDrag(absoluteIndex) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDropState.onDrag(dragAmount)
                                    },
                                    onDragEnd = { dragDropState.onDragInterrupted() },
                                    onDragCancel = { dragDropState.onDragInterrupted() }
                                )
                            }
                            .graphicsLayer {
                                if (isDragging) {
                                    val currentLayoutOffset = gridState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == absoluteIndex }
                                        ?.offset
                                    if (currentLayoutOffset != null) {
                                        val desiredX = dragDropState.draggingItemInitialOffset.x + dragDropState.totalDragAmount.x
                                        val desiredY = dragDropState.draggingItemInitialOffset.y + dragDropState.totalDragAmount.y
                                        this.translationX = desiredX - currentLayoutOffset.x
                                        this.translationY = desiredY - currentLayoutOffset.y
                                    }
                                }
                            }
                            .w10mStaggeredAnimation(animationProgress.value, favStartIndex + favIndex, clickedItemKey == key)
                    )
                }
            }
        }
    }
}
