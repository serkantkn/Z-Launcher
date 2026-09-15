package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════
// LAYOUT MODEL (pure logic, unit-tested)
// ════════════════════════════════════════════════════════════

/** Which Windows 8 group a tile belongs to. Groups are derived from the tile type. */
internal enum class Win8GroupKind { HUBS, APPS, NOTES }

/** One laid-out group: its cells and how many cells wide it is (always whole medium columns). */
internal data class Win8GroupLayout(val kind: Win8GroupKind, val cells: List<MetroCell>, val columns: Int)

/**
 * Packs one group into [rows] cell rows with the shared Metro packer, filling column by column,
 * then rounds the group width up to a whole number of medium columns.
 */
internal fun layoutWin8Group(kind: Win8GroupKind, specs: List<MetroTileSpec>, rows: Int): Win8GroupLayout {
    val cells = packMetroTiles(specs, crossAxisCells = rows, columnMajor = true)
    val maxCol = cells.maxOfOrNull { it.col + it.cols } ?: 0
    val columns = (((maxCol + 1) / 2) * 2).coerceAtLeast(2)
    return Win8GroupLayout(kind, cells, columns)
}

// ════════════════════════════════════════════════════════════
// SCREEN
// ════════════════════════════════════════════════════════════

private val TargetMediumTile = 150.dp
private val MaxMediumTile = 168.dp
private val GroupHeaderHeight = 34.dp

/**
 * Windows 8 full-screen Start menu, used as the tablet home screen.
 *
 * A horizontally scrolling board of named tile groups: tiles pack column by column into a fixed
 * number of rows, medium/wide tiles snap to the medium grid and small tiles fill the quadrants.
 * Pinching zooms out to the semantic-zoom group overview; a tap on a group zooms back into it.
 * Long-pressing a tile enters edit mode and drags it to a new position.
 */
@Composable
fun Windows8StartScreen(
    tiles: List<StartTileUIModel>,
    tileSpacing: Dp,
    isEditMode: Boolean,
    onEnterEditMode: () -> Unit,
    onMoveTile: (from: Int, to: Int) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onMergeTiles: (sourceId: String, targetId: String) -> Unit = { _, _ -> },
    canMerge: (sourceId: String, targetId: String) -> Boolean = { _, _ -> false },
    tile: @Composable (
        model: StartTileUIModel,
        flatIndex: Int,
        isDragging: Boolean,
        isMergeTarget: Boolean,
        tileModifier: Modifier
    ) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val fg = if (zuneColors.isDark) Color.White else Color.Black

    var zoomedOut by remember { mutableStateOf(false) }
    val drag = rememberStartDragState()

    val currentTiles by rememberUpdatedState(tiles)
    val currentCanMerge by rememberUpdatedState(canMerge)
    val currentOnMove by rememberUpdatedState(onMoveTile)

    // Tiles are grouped by kind, keeping the user's order inside each group.
    val groupedTiles = remember(tiles) {
        val order = listOf(Win8GroupKind.HUBS, Win8GroupKind.APPS, Win8GroupKind.NOTES)
        order.mapNotNull { kind ->
            val members = tiles.filter { kindOf(it) == kind }
            if (members.isEmpty()) null else kind to members
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val headerHeight = 92.dp
        val footerHeight = 56.dp
        val sidePadding = 40.dp

        val gap = tileSpacing.coerceIn(4.dp, 12.dp)
        val boardArea = (maxHeight - statusTop - navBottom - headerHeight - footerHeight - GroupHeaderHeight)
            .coerceAtLeast(TargetMediumTile * 2)
        val rowsMedium = ((boardArea + gap) / (TargetMediumTile + gap)).toInt().coerceIn(2, 6)
        val medium = ((boardArea + gap) / rowsMedium - gap).coerceAtMost(MaxMediumTile)
        val small = (medium - gap) / 2
        val unit = small + gap                    // pitch of one small-tile cell
        val rowsSmall = rowsMedium * 2
        val groupGap = medium * 0.7f

        val layouts = remember(groupedTiles, rowsSmall) {
            groupedTiles.map { (kind, members) ->
                layoutWin8Group(kind, members.map { model ->
                    val (w, h) = TileSpan.cellsOf(model.span)
                    MetroTileSpec(model.id, w, h)
                }, rowsSmall)
            }
        }
        val groupOffsets = remember(layouts, unit, gap, groupGap) {
            var x = 0.dp
            layouts.map { layout ->
                val start = x
                x += (layout.columns * unit - gap) + groupGap
                start
            }
        }
        val boardWidth = remember(layouts, groupOffsets, unit, gap, groupGap) {
            val last = layouts.lastIndex
            if (last < 0) 1.dp else groupOffsets[last] + (layouts[last].columns * unit - gap)
        }
        val boardHeight = GroupHeaderHeight + (rowsSmall * unit - gap)

        // Pixel rectangles of every tile, for drag hit-testing.
        val cellRects = remember(layouts, groupOffsets, unit, small, gap, boardHeight) {
            with(density) {
                buildMap<String, Rect> {
                    layouts.forEachIndexed { groupIndex, layout ->
                        layout.cells.forEach { cell ->
                            val left = (groupOffsets[groupIndex] + cell.col * unit).toPx()
                            val top = (GroupHeaderHeight + cell.row * unit).toPx()
                            val width = (cell.cols * small + (cell.cols - 1) * gap).toPx()
                            val height = (cell.rows * small + (cell.rows - 1) * gap).toPx()
                            put(cell.id, Rect(left, top, left + width, top + height))
                        }
                    }
                }
            }
        }
        val currentRects by rememberUpdatedState(cellRects)

        val viewportWidth = maxWidth - sidePadding * 2
        val zoomOutScale = remember(boardWidth, viewportWidth) {
            (viewportWidth / boardWidth).coerceIn(0.3f, 0.65f)
        }
        val scale by animateFloatAsState(
            targetValue = if (zoomedOut) zoomOutScale else 1f,
            animationSpec = tween(320),
            label = "win8_semantic_zoom"
        )

        Column(modifier = Modifier.fillMaxSize().padding(top = statusTop, bottom = navBottom)) {
            // ── Header: "başlangıç" + search / settings, like the Windows 8 Start header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .padding(start = sidePadding, end = sidePadding, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.win8_start_title),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 42.sp),
                    color = fg,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { zoomedOut = !zoomedOut }
                )
                HeaderIcon(Icons.Default.Search, stringResource(R.string.common_search), fg, onOpenApps)
                Spacer(modifier = Modifier.width(18.dp))
                HeaderIcon(Icons.Default.Settings, stringResource(R.string.common_settings), fg, onOpenSettings)
            }

            // ── Board ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizontalScroll(scrollState, enabled = !zoomedOut)
                    .win8PinchZoom(
                        onZoomOut = { if (!zoomedOut) { zoomedOut = true; scope.launch { scrollState.animateScrollTo(0) } } },
                        onZoomIn = { zoomedOut = false }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = sidePadding)
                        .width(boardWidth)
                        .height(boardHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                ) {
                    layouts.forEachIndexed { groupIndex, layout ->
                        val groupX = groupOffsets[groupIndex]
                        val groupWidth = layout.columns * unit - gap

                        Text(
                            text = stringResource(titleResOf(layout.kind)),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = if (zoomedOut) 22.sp else 17.sp
                            ),
                            color = fg.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .offset(x = groupX, y = 2.dp)
                                .width(groupWidth)
                        )

                        layout.cells.forEach { cell ->
                            val flatIndex = tiles.indexOfFirst { it.id == cell.id }
                            if (flatIndex < 0) return@forEach
                            val model = tiles[flatIndex]
                            val isDragging = drag.dragId == cell.id
                            val isMergeTarget = drag.mergeTargetId == cell.id

                            key(cell.id) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = groupX + cell.col * unit, y = GroupHeaderHeight + cell.row * unit)
                                        .width(cell.cols * small + (cell.cols - 1) * gap)
                                        .zIndex(if (isDragging) 10f else 0f)
                                ) {
                                    val here = with(density) {
                                        Offset(
                                            (groupX + cell.col * unit).toPx(),
                                            (GroupHeaderHeight + cell.row * unit).toPx()
                                        )
                                    }
                                    tile(
                                        model,
                                        flatIndex,
                                        isDragging,
                                        isMergeTarget,
                                        Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                if (!isDragging) return@graphicsLayer
                                                val wanted = drag.pointer - drag.grabWithinTile
                                                translationX = wanted.x - here.x
                                                translationY = wanted.y - here.y
                                                scaleX = 1.06f
                                                scaleY = 1.06f
                                                shadowElevation = 18.dp.toPx()
                                            }
                                            .pointerInput(cell.id, zoomedOut) {
                                                if (zoomedOut) return@pointerInput
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { grabPoint ->
                                                        onEnterEditMode()
                                                        val rect = currentRects[cell.id]
                                                            ?: return@detectDragGesturesAfterLongPress
                                                        drag.start(cell.id, rect.topLeft, grabPoint)
                                                    },
                                                    onDrag = { change, delta ->
                                                        change.consume()
                                                        drag.moveBy(delta)
                                                        drag.update(
                                                            nowMillis = System.currentTimeMillis(),
                                                            order = currentTiles.map { it.id },
                                                            rects = currentRects,
                                                            canMergeInto = { target ->
                                                drag.dragId?.let { currentCanMerge(it, target) } == true
                                            },
                                                            onReorder = { from, to -> currentOnMove(from, to) }
                                                        )
                                                    },
                                                    onDragEnd = {
                                                        val held = drag.dragId
                                                        val mergeInto = drag.finish()
                                                        if (held != null && mergeInto != null) {
                                                            onMergeTiles(held, mergeInto)
                                                        }
                                                    },
                                                    onDragCancel = { drag.cancel() }
                                                )
                                            }
                                    )
                                }
                            }
                        }

                        // Semantic zoom: the whole group becomes one target that zooms back in.
                        if (zoomedOut) {
                            Box(
                                modifier = Modifier
                                    .offset(x = groupX - gap, y = 0.dp)
                                    .width(groupWidth + gap * 2)
                                    .height(boardHeight)
                                    .border(1.dp, fg.copy(alpha = 0.25f))
                                    .background(fg.copy(alpha = 0.04f))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        zoomedOut = false
                                        scope.launch {
                                            scrollState.animateScrollTo(with(density) { groupX.toPx() }.toInt())
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // ── Footer: scroll indicator, all-apps arrow, semantic zoom button ──
            Column(modifier = Modifier.fillMaxWidth().height(footerHeight)) {
                Win8ScrollBar(
                    scroll = scrollState.value.toFloat(),
                    maxScroll = scrollState.maxValue.toFloat(),
                    color = fg,
                    modifier = Modifier.padding(horizontal = sidePadding, vertical = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = sidePadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CircleButton(Icons.Default.KeyboardArrowDown, stringResource(R.string.all_apps), fg, onOpenApps)
                    CircleButton(Icons.Default.Remove, stringResource(R.string.win8_zoom_out), fg) { zoomedOut = !zoomedOut }
                }
            }
        }
    }
}

private fun kindOf(model: StartTileUIModel): Win8GroupKind = when (model) {
    is StartTileUIModel.Hub -> Win8GroupKind.HUBS
    is StartTileUIModel.App -> Win8GroupKind.APPS
    is StartTileUIModel.NoteTile, is StartTileUIModel.QuickNote -> Win8GroupKind.NOTES
    // A pinned site or person is something the user put there, like an app.
    is StartTileUIModel.Web, is StartTileUIModel.Person, is StartTileUIModel.Thread,
    is StartTileUIModel.Album, is StartTileUIModel.MusicAlbum -> Win8GroupKind.APPS
    // A folder sits with the hubs; what is inside it can be anything.
    is StartTileUIModel.Folder -> Win8GroupKind.HUBS
}

private fun titleResOf(kind: Win8GroupKind): Int = when (kind) {
    Win8GroupKind.HUBS -> R.string.win8_group_hubs
    Win8GroupKind.APPS -> R.string.apps_hub
    Win8GroupKind.NOTES -> R.string.hub_notes
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint.copy(alpha = 0.85f),
        modifier = Modifier
            .size(26.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    )
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .border(1.5.dp, tint.copy(alpha = 0.55f), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
    }
}

/** Thin Windows 8 style scroll indicator; hidden when everything fits. */
@Composable
private fun Win8ScrollBar(scroll: Float, maxScroll: Float, color: Color, modifier: Modifier = Modifier) {
    if (maxScroll <= 0f) {
        Spacer(modifier = modifier.fillMaxWidth().height(3.dp))
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(3.dp)) {
        val track = maxWidth
        val fraction = (track / (track + with(LocalDensity.current) { maxScroll.toDp() })).coerceIn(0.08f, 1f)
        val thumb = track * fraction
        val offset = (track - thumb) * (scroll / maxScroll).coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(color.copy(alpha = 0.12f)).align(Alignment.Center))
        Box(modifier = Modifier.offset(x = offset).width(thumb).height(3.dp).background(color.copy(alpha = 0.55f)))
    }
}

/**
 * Two-finger pinch for semantic zoom. Single-finger events are left untouched so the board keeps
 * scrolling normally.
 */
private fun Modifier.win8PinchZoom(onZoomOut: () -> Unit, onZoomIn: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        var handled = false
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            if (event.changes.size >= 2) {
                zoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                if (!handled && zoom < 0.75f) { onZoomOut(); handled = true }
                if (!handled && zoom > 1.35f) { onZoomIn(); handled = true }
            }
        }
    }
}
