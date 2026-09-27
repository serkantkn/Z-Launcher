package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ════════════════════════════════════════════════════════════
// TILE SIZES
// ════════════════════════════════════════════════════════════

/**
 * The four Metro tile sizes. The stored value is the tile's width in grid cells (one cell = one
 * small tile), which is also its LazyGrid span in the older layouts; [cellsOf] turns it into the
 * width × height the boards lay out.
 */
object TileSpan {
    const val SMALL = 1     // 1 × 1
    const val MEDIUM = 2    // 2 × 2
    const val WIDE = 4      // 4 × 2
    const val LARGE = 8     // 4 × 4

    val ALL = listOf(SMALL, MEDIUM, WIDE, LARGE)

    /** width × height in grid cells. */
    fun cellsOf(span: Int): Pair<Int, Int> = when (span) {
        SMALL -> 1 to 1
        WIDE -> 4 to 2
        LARGE -> 4 to 4
        else -> 2 to 2
    }

    fun labelRes(span: Int): Int = when (span) {
        SMALL -> R.string.tile_size_small
        WIDE -> R.string.tile_size_wide
        LARGE -> R.string.tile_size_large
        else -> R.string.tile_size_medium
    }
}

// ════════════════════════════════════════════════════════════
// PACKING (pure logic, unit-tested)
// ════════════════════════════════════════════════════════════

/** A tile handed to the packer: [cols] × [rows] measured in grid cells. */
internal data class MetroTileSpec(val id: String, val cols: Int, val rows: Int)

/** Where a tile ended up on the board, in grid cells. */
internal data class MetroCell(val id: String, val col: Int, val row: Int, val cols: Int, val rows: Int)

private const val MAX_MAIN_AXIS_CELLS = 400

/**
 * Packs [specs] into a board that is [crossAxisCells] cells wide (row-major, vertical boards) or
 * tall (column-major, the Windows 8 horizontal board), the way Metro start screens do: tiles are
 * taken in order and dropped into the first free slot, so a later small tile fills the hole a
 * bigger tile left behind — the layout stays gapless and asymmetric. Tiles wider or taller than
 * one cell snap to the even (medium) grid.
 */
internal fun packMetroTiles(
    specs: List<MetroTileSpec>,
    crossAxisCells: Int,
    columnMajor: Boolean
): List<MetroCell> {
    val cross = crossAxisCells.coerceAtLeast(1)
    val occupied = HashSet<Long>()
    fun key(col: Int, row: Int): Long = col.toLong() * 100_000L + row
    fun free(col: Int, row: Int, w: Int, h: Int): Boolean {
        for (dc in 0 until w) for (dr in 0 until h) if (occupied.contains(key(col + dc, row + dr))) return false
        return true
    }

    val cells = mutableListOf<MetroCell>()
    for (spec in specs) {
        // A tile can never be wider (or taller) than the board's cross axis.
        val w = if (columnMajor) spec.cols else spec.cols.coerceAtMost(cross)
        val h = if (columnMajor) spec.rows.coerceAtMost(cross) else spec.rows
        var placed = false
        var main = 0
        while (!placed && main < MAX_MAIN_AXIS_CELLS) {
            var crossPos = 0
            while (crossPos < cross) {
                val col = if (columnMajor) main else crossPos
                val row = if (columnMajor) crossPos else main
                val insideBoard = if (columnMajor) row + h <= cross else col + w <= cross
                val aligned = (w == 1 || col % 2 == 0) && (h == 1 || row % 2 == 0)
                if (insideBoard && aligned && free(col, row, w, h)) {
                    for (dc in 0 until w) for (dr in 0 until h) occupied.add(key(col + dc, row + dr))
                    cells += MetroCell(spec.id, col, row, w, h)
                    placed = true
                    break
                }
                crossPos++
            }
            main++
        }
    }
    return cells
}

// ════════════════════════════════════════════════════════════
// OPEN FOLDER (pure logic, unit-tested)
// ════════════════════════════════════════════════════════════

/**
 * Where an open folder's band goes and what is in it.
 *
 * The band is not a window over the board: it is pushed in between two rows of it, under the whole
 * block of tiles the folder sits in, and everything below slides down to make room.
 */
internal data class FolderExpansion(
    val folderId: String,
    val splitRow: Int,
    val childCells: List<MetroCell>,
    val childRows: Int
)

/**
 * The row the folder's band opens at: below every tile that starts above the folder's own bottom,
 * so the band can never be pushed through the middle of a tall tile standing beside it.
 */
internal fun folderSplitRow(cells: List<MetroCell>, folderId: String): Int? {
    val folder = cells.firstOrNull { it.id == folderId } ?: return null
    val folderBottom = folder.row + folder.rows
    return cells.filter { it.row < folderBottom }.maxOf { it.row + it.rows }
}

// ════════════════════════════════════════════════════════════
// VERTICAL BOARD (phone Start screen)
// ════════════════════════════════════════════════════════════

/** How close to the edge a held tile has to be before the board starts scrolling itself. */
private val AutoScrollEdge = 96.dp

/** Fastest the board scrolls under a held tile, per frame. */
private const val AUTO_SCROLL_MAX_STEP = 22f

/** The glide tiles use when the board re-flows around a held one, or opens a folder. */
private val TileReflowSpring = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/** How much a tile grows while it is held, so it reads as picked up off the board. */
private const val DRAG_LIFT = 1.06f

/** Long enough for the band's spring to reach zero before its tiles are let go of. */
private const val FOLDER_COLLAPSE_MILLIS = 500L

private val FolderBandTopPadding = 14.dp
private val FolderBandBottomPadding = 18.dp
private val FolderBandHeaderGap = 10.dp

/**
 * The Windows Phone Start board: a gapless packed grid of [columns] cells, scrolling vertically.
 *
 * Long-pressing a tile picks it up. While it is held the tile hangs off the finger and the rest of
 * the board glides out of its way; dropping it in the middle of another tile makes a folder, and
 * dragging to the top or bottom edge scrolls the board.
 *
 * An [openFolder] is not a pop-up: its tiles unfold into a band inserted between the board's rows,
 * pushing everything below it down.
 */
@Composable
fun MetroStartBoard(
    tiles: List<StartTileUIModel>,
    columns: Int,
    gap: Dp,
    contentPadding: PaddingValues,
    onEnterEditMode: () -> Unit,
    onMoveTile: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    onMergeTiles: (sourceId: String, targetId: String) -> Unit = { _, _ -> },
    canMerge: (sourceId: String, targetId: String) -> Boolean = { _, _ -> false },
    openFolder: StartTileUIModel.Folder? = null,
    isEditMode: Boolean = false,
    onFolderRename: (String) -> Unit = {},
    onFolderTakeOut: (childId: String) -> Unit = {},
    onFolderMoveChild: (from: Int, to: Int) -> Unit = { _, _ -> },
    /** Hoisted so the Home key can take the board back to its beginning. */
    scrollState: ScrollState = rememberScrollState(),
    /** Drawn under the last row of tiles; the running-hubs strip lives here. */
    footer: @Composable () -> Unit = {},
    tile: @Composable (
        model: StartTileUIModel,
        flatIndex: Int,
        isDragging: Boolean,
        isMergeTarget: Boolean,
        tileModifier: Modifier
    ) -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val drag = rememberStartDragState()

    val currentTiles by rememberUpdatedState(tiles)
    val currentCanMerge by rememberUpdatedState(canMerge)
    val currentOnMove by rememberUpdatedState(onMoveTile)

    val cells = remember(tiles, columns) {
        packMetroTiles(
            specs = tiles.map { model ->
                val (w, h) = TileSpan.cellsOf(model.span)
                MetroTileSpec(model.id, w, h)
            },
            crossAxisCells = columns,
            columnMajor = false
        )
    }

    // What the open folder unfolds into, and what the closing one is still showing on its way out.
    val expansion = remember(cells, openFolder, columns) {
        val folder = openFolder ?: return@remember null
        val splitRow = folderSplitRow(cells, folder.id) ?: return@remember null
        val childCells = packMetroTiles(
            specs = folder.children.map { child ->
                val (w, h) = TileSpan.cellsOf(child.span)
                MetroTileSpec(child.id, w, h)
            },
            crossAxisCells = columns,
            columnMajor = false
        )
        FolderExpansion(
            folderId = folder.id,
            splitRow = splitRow,
            childCells = childCells,
            childRows = childCells.maxOfOrNull { it.row + it.rows } ?: 0
        )
    }
    var closing by remember { mutableStateOf<Pair<FolderExpansion, StartTileUIModel.Folder>?>(null) }
    LaunchedEffect(expansion) {
        // A folder that has just been closed keeps its tiles while the band folds away under them.
        if (expansion != null && openFolder != null) {
            closing = expansion to openFolder
        } else {
            delay(FOLDER_COLLAPSE_MILLIS)
            closing = null
        }
    }
    val band = expansion ?: closing?.first
    val bandFolder = openFolder ?: closing?.second

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val startPad = contentPadding.calculateStartPadding(layoutDirection)
        val endPad = contentPadding.calculateEndPadding(layoutDirection)
        val boardWidth = (maxWidth - startPad - endPad).coerceAtLeast(1.dp)
        val cell = ((boardWidth - gap * (columns - 1)) / columns).coerceAtLeast(1.dp)
        val pitch = cell + gap
        val rows = cells.maxOfOrNull { it.row + it.rows } ?: 0
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val edgePx = with(density) { AutoScrollEdge.toPx() }
        val topPadPx = with(density) { contentPadding.calculateTopPadding().toPx() }

        // The band's full height, and the shift every tile below it takes. Tiles glide there on
        // their own springs, so the band and the board below it move as one.
        val bandFullHeight = if (band == null || band.childRows == 0) 0.dp else {
            FolderBandTopPadding + FolderHeaderHeight + FolderBandHeaderGap +
                (band.childRows * pitch - gap) + FolderBandBottomPadding
        }
        val shift = if (expansion == null) 0.dp else bandFullHeight
        val splitRow = band?.splitRow
        val bandHeight by animateDpAsState(shift, TileReflowSpring, label = "folder_band")
        val boardHeight = (if (rows == 0) 0.dp else rows * pitch - gap) + bandHeight

        val cellRects = remember(cells, cell, gap, splitRow, shift) {
            with(density) {
                buildMap<String, Rect> {
                    cells.forEach { c ->
                        val left = (c.col * pitch).toPx()
                        val top = (c.row * pitch + if (splitRow != null && c.row >= splitRow) shift else 0.dp).toPx()
                        val w = (c.cols * cell + (c.cols - 1) * gap).toPx()
                        val h = (c.rows * cell + (c.rows - 1) * gap).toPx()
                        put(c.id, Rect(left, top, left + w, top + h))
                    }
                }
            }
        }
        val currentRects by rememberUpdatedState(cellRects)

        fun applyDrag() {
            drag.update(
                nowMillis = System.currentTimeMillis(),
                order = currentTiles.map { it.id },
                rects = currentRects,
                canMergeInto = { target -> drag.dragId?.let { currentCanMerge(it, target) } == true },
                onReorder = { from, to -> currentOnMove(from, to) }
            )
        }

        // A tile held against the top or bottom edge scrolls the board, at a speed that grows as
        // it gets closer — without this a tile cannot be moved further than one screen.
        LaunchedEffect(drag.isDragging) {
            if (!drag.isDragging) return@LaunchedEffect
            while (isActive) {
                withFrameNanos { }
                val viewportY = drag.pointer.y + topPadPx - scrollState.value
                val step = when {
                    viewportY < edgePx -> -(edgePx - viewportY) / edgePx * AUTO_SCROLL_MAX_STEP
                    viewportY > viewportHeightPx - edgePx ->
                        (viewportY - (viewportHeightPx - edgePx)) / edgePx * AUTO_SCROLL_MAX_STEP

                    else -> 0f
                }
                if (step != 0f) {
                    val consumed = scrollState.scrollBy(step)
                    if (consumed != 0f) {
                        drag.scrolledBy(consumed)
                        applyDrag()
                    }
                }
            }
        }

        // A folder that unfolds below the fold brings itself into view.
        LaunchedEffect(expansion?.folderId) {
            val open = expansion ?: return@LaunchedEffect
            repeat(3) { withFrameNanos { } }
            val bandBottom = with(density) {
                (open.splitRow * pitch).toPx() + topPadPx + bandFullHeight.toPx()
            }
            val overshoot = bandBottom - (scrollState.value + viewportHeightPx)
            if (overshoot > 0f) {
                scrollState.animateScrollTo((scrollState.value + overshoot).toInt())
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
            Box(
                modifier = Modifier
                    .padding(start = startPad, end = endPad)
                    .width(boardWidth)
                    .height(boardHeight)
            ) {
                PackedTiles(
                    tiles = tiles,
                    cells = cells,
                    cell = cell,
                    gap = gap,
                    pitch = pitch,
                    drag = drag,
                    rects = cellRects,
                    splitRow = splitRow,
                    shift = shift,
                    onGrab = onEnterEditMode,
                    onDragMoved = { applyDrag() },
                    onDropped = { held, mergeInto -> if (mergeInto != null) onMergeTiles(held, mergeInto) },
                    tile = tile
                )

                if (band != null && bandFolder != null && bandHeight > 0.dp) {
                    // The band has no ground of its own: the board has simply been pushed apart,
                    // and what shows through the gap is the wallpaper, as everywhere else.
                    Box(
                        modifier = Modifier
                            .offset(y = band.splitRow * pitch)
                            .width(boardWidth)
                            .height(bandHeight)
                            .clipToBounds()
                    ) {
                        FolderBand(
                            folder = bandFolder,
                            cells = band.childCells,
                            cell = cell,
                            gap = gap,
                            pitch = pitch,
                            isEditMode = isEditMode,
                            onEnterEditMode = onEnterEditMode,
                            onRename = onFolderRename,
                            onTakeOut = onFolderTakeOut,
                            onMoveChild = onFolderMoveChild,
                            tile = tile
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(start = startPad, end = endPad)) {
                footer()
            }
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

/**
 * Places packed [cells] in the Box around it and wires each one up for dragging. Tiles below
 * [splitRow] are pushed down by [shift] to make room for an open folder.
 */
@Composable
private fun PackedTiles(
    tiles: List<StartTileUIModel>,
    cells: List<MetroCell>,
    cell: Dp,
    gap: Dp,
    pitch: Dp,
    drag: StartDragState,
    rects: Map<String, Rect>,
    splitRow: Int?,
    shift: Dp,
    onGrab: () -> Unit,
    onDragMoved: () -> Unit,
    onDropped: (held: String, mergeInto: String?) -> Unit,
    tile: @Composable (
        model: StartTileUIModel,
        flatIndex: Int,
        isDragging: Boolean,
        isMergeTarget: Boolean,
        tileModifier: Modifier
    ) -> Unit
) {
    val currentRects by rememberUpdatedState(rects)

    cells.forEach { c ->
        val flatIndex = tiles.indexOfFirst { it.id == c.id }
        if (flatIndex < 0) return@forEach
        val model = tiles[flatIndex]

        key(c.id) {
            val isDragging = drag.dragId == c.id
            val isMergeTarget = drag.mergeTargetId == c.id
            val targetX = c.col * pitch
            val targetY = c.row * pitch + if (splitRow != null && c.row >= splitRow) shift else 0.dp
            // The board glides around a held tile; the held one is placed by the finger.
            val glideX by animateDpAsState(targetX, TileReflowSpring, label = "tile_x")
            val glideY by animateDpAsState(targetY, TileReflowSpring, label = "tile_y")
            val x = if (isDragging) targetX else glideX
            val y = if (isDragging) targetY else glideY

            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .width(c.cols * cell + (c.cols - 1) * gap)
                    .zIndex(if (isDragging) 10f else 0f)
            ) {
                tile(
                    model,
                    flatIndex,
                    isDragging,
                    isMergeTarget,
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (!isDragging) return@graphicsLayer
                            // Wherever the board has since put this tile, draw it back under
                            // the finger.
                            val here = Offset(x.toPx(), y.toPx())
                            val wanted = drag.pointer - drag.grabWithinTile
                            translationX = wanted.x - here.x
                            translationY = wanted.y - here.y
                            scaleX = DRAG_LIFT
                            scaleY = DRAG_LIFT
                            shadowElevation = 18.dp.toPx()
                        }
                        .pointerInput(c.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { grabPoint ->
                                    onGrab()
                                    val rect = currentRects[c.id] ?: return@detectDragGesturesAfterLongPress
                                    drag.start(c.id, rect.topLeft, grabPoint)
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    drag.moveBy(delta)
                                    onDragMoved()
                                },
                                onDragEnd = {
                                    val held = drag.dragId
                                    val mergeInto = drag.finish()
                                    if (held != null) onDropped(held, mergeInto)
                                },
                                onDragCancel = { drag.cancel() }
                            )
                        }
                )
            }
        }
    }
}

/**
 * The open folder's band: its name, then its tiles, laid out on the same grid as the board they
 * came from so the folder reads as part of the board rather than a window on top of it.
 */
@Composable
private fun FolderBand(
    folder: StartTileUIModel.Folder,
    cells: List<MetroCell>,
    cell: Dp,
    gap: Dp,
    pitch: Dp,
    isEditMode: Boolean,
    onEnterEditMode: () -> Unit,
    onRename: (String) -> Unit,
    onTakeOut: (childId: String) -> Unit,
    onMoveChild: (from: Int, to: Int) -> Unit,
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
    val childDrag = rememberStartDragState()

    val children = folder.children
    val currentChildren by rememberUpdatedState(children)
    val currentOnMoveChild by rememberUpdatedState(onMoveChild)

    val childRects = remember(cells, cell, gap) {
        with(density) {
            buildMap<String, Rect> {
                cells.forEach { c ->
                    val left = (c.col * pitch).toPx()
                    val top = (c.row * pitch).toPx()
                    val w = (c.cols * cell + (c.cols - 1) * gap).toPx()
                    val h = (c.rows * cell + (c.rows - 1) * gap).toPx()
                    put(c.id, Rect(left, top, left + w, top + h))
                }
            }
        }
    }
    val currentChildRects by rememberUpdatedState(childRects)

    val rows = cells.maxOfOrNull { it.row + it.rows } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FolderBandTopPadding, bottom = FolderBandBottomPadding)
    ) {
        FolderHeaderBar(
            folderId = folder.id,
            name = folder.name,
            onRename = onRename
        )

        Spacer(modifier = Modifier.height(FolderBandHeaderGap))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (rows == 0) 0.dp else rows * pitch - gap)
        ) {
            PackedTiles(
                tiles = children,
                cells = cells,
                cell = cell,
                gap = gap,
                pitch = pitch,
                drag = childDrag,
                rects = childRects,
                splitRow = null,
                shift = 0.dp,
                onGrab = onEnterEditMode,
                onDragMoved = {
                    childDrag.update(
                        nowMillis = System.currentTimeMillis(),
                        order = currentChildren.map { it.id },
                        rects = currentChildRects,
                        // Folders never nest, so nothing inside one can become another.
                        canMergeInto = { false },
                        onReorder = { from, to -> currentOnMoveChild(from, to) }
                    )
                },
                onDropped = { _, _ -> },
                tile = { model, index, isDragging, isMergeTarget, tileModifier ->
                    Box {
                        tile(model, index, isDragging, isMergeTarget, tileModifier)
                        if (isEditMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .background(zuneColors.accentColor)
                                    .clickable { onTakeOut(model.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileMove,
                                    contentDescription = stringResource(R.string.start_folder_take_out),
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// SIZE PICKER
// ════════════════════════════════════════════════════════════

/** Popup opened by a tile's resize button: the four Metro sizes, each with a shape preview. */
@Composable
fun TileSizeDialog(
    currentSpan: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.tile_size_title),
        dismissButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim { onDismiss() } },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TileSpan.ALL.forEach { span ->
                val (w, h) = TileSpan.cellsOf(span)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { dismissWithAnim { onSelect(span) } }
                        .padding(vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(width = 46.dp, height = 46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = (w * 10).dp, height = (h * 10).dp)
                                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(1.dp))
                        )
                    }
                    Text(
                        text = stringResource(TileSpan.labelRes(span)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(22.dp).border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (span == currentSpan) {
                            Box(modifier = Modifier.size(11.dp).background(Color.White, CircleShape))
                        }
                    }
                }
            }
        }
    }
}
