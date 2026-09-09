package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
// VERTICAL BOARD (phone Start screen)
// ════════════════════════════════════════════════════════════

/**
 * The Windows Phone Start board: a gapless packed grid of [columns] cells, scrolling vertically.
 * Long-pressing a tile enters edit mode and drags it to another slot.
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
    tile: @Composable (model: StartTileUIModel, flatIndex: Int, isDragging: Boolean, tileModifier: Modifier) -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scrollState = rememberScrollState()

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragAmount by remember { mutableStateOf(Offset.Zero) }
    val currentTiles by rememberUpdatedState(tiles)

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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val startPad = contentPadding.calculateStartPadding(layoutDirection)
        val endPad = contentPadding.calculateEndPadding(layoutDirection)
        val boardWidth = (maxWidth - startPad - endPad).coerceAtLeast(1.dp)
        val cell = ((boardWidth - gap * (columns - 1)) / columns).coerceAtLeast(1.dp)
        val pitch = cell + gap
        val rows = cells.maxOfOrNull { it.row + it.rows } ?: 0
        val boardHeight = if (rows == 0) 0.dp else rows * pitch - gap

        val cellRects = remember(cells, cell, gap) {
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
        val currentRects by rememberUpdatedState(cellRects)

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
            Box(
                modifier = Modifier
                    .padding(start = startPad, end = endPad)
                    .width(boardWidth)
                    .height(boardHeight)
            ) {
                cells.forEach { c ->
                    val flatIndex = tiles.indexOfFirst { it.id == c.id }
                    if (flatIndex < 0) return@forEach
                    val model = tiles[flatIndex]
                    val isDragging = dragId == c.id

                    key(c.id) {
                        Box(
                            modifier = Modifier
                                .offset(x = c.col * pitch, y = c.row * pitch)
                                .width(c.cols * cell + (c.cols - 1) * gap)
                                .zIndex(if (isDragging) 10f else 0f)
                        ) {
                            tile(
                                model,
                                flatIndex,
                                isDragging,
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        if (isDragging) {
                                            translationX = dragAmount.x
                                            translationY = dragAmount.y
                                        }
                                    }
                                    .pointerInput(c.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                onEnterEditMode()
                                                dragId = c.id
                                                dragAmount = Offset.Zero
                                            },
                                            onDrag = { change, delta ->
                                                change.consume()
                                                dragAmount += delta
                                                moveTowards(dragId, dragAmount, currentRects, currentTiles, onMoveTile) { adjust ->
                                                    dragAmount += adjust
                                                }
                                            },
                                            onDragEnd = { dragId = null; dragAmount = Offset.Zero },
                                            onDragCancel = { dragId = null; dragAmount = Offset.Zero }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

/**
 * Shared drag step for both boards: when the dragged tile's centre lands on another tile, swap
 * their positions in the list and shift the drag offset so the tile stays under the finger.
 */
internal fun moveTowards(
    dragId: String?,
    dragAmount: Offset,
    rects: Map<String, Rect>,
    tiles: List<StartTileUIModel>,
    onMoveTile: (Int, Int) -> Unit,
    adjustDrag: (Offset) -> Unit
) {
    val id = dragId ?: return
    val fromRect = rects[id] ?: return
    val pointer = fromRect.center + dragAmount
    val hit = rects.entries.firstOrNull { it.key != id && it.value.contains(pointer) } ?: return
    val from = tiles.indexOfFirst { it.id == id }
    val to = tiles.indexOfFirst { it.id == hit.key }
    if (from < 0 || to < 0) return
    adjustDrag(Offset(fromRect.left - hit.value.left, fromRect.top - hit.value.top))
    onMoveTile(from, to)
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
