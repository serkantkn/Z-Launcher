package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.ui.screens.home.MetroCell
import com.serkantkn.zunelauncher.ui.screens.home.MetroTileSpec
import com.serkantkn.zunelauncher.ui.screens.home.TileSpan
import com.serkantkn.zunelauncher.ui.screens.home.Win8GroupKind
import com.serkantkn.zunelauncher.ui.screens.home.layoutWin8Group
import com.serkantkn.zunelauncher.ui.screens.home.packMetroTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Packing rules shared by the phone Start grid and the Windows 8 tablet board. */
class MetroPackingTest {

    private fun spec(id: String, span: Int): MetroTileSpec {
        val (w, h) = TileSpan.cellsOf(span)
        return MetroTileSpec(id, w, h)
    }

    private fun rowMajor(vararg spans: Pair<String, Int>, columns: Int = 4): List<MetroCell> =
        packMetroTiles(spans.map { spec(it.first, it.second) }, columns, columnMajor = false)

    private fun columnMajor(vararg spans: Pair<String, Int>, rows: Int = 6): List<MetroCell> =
        packMetroTiles(spans.map { spec(it.first, it.second) }, rows, columnMajor = true)

    private fun at(cells: List<MetroCell>, id: String) = cells.first { it.id == id }.let { it.col to it.row }

    // ── sizes ──────────────────────────────────────────────────────────

    @Test fun fourSizesMapToCells() {
        assertEquals(1 to 1, TileSpan.cellsOf(TileSpan.SMALL))
        assertEquals(2 to 2, TileSpan.cellsOf(TileSpan.MEDIUM))
        assertEquals(4 to 2, TileSpan.cellsOf(TileSpan.WIDE))
        assertEquals(4 to 4, TileSpan.cellsOf(TileSpan.LARGE))
    }

    @Test fun everySizeHasItsOwnLabel() {
        assertEquals(TileSpan.ALL.size, TileSpan.ALL.map { TileSpan.labelRes(it) }.distinct().size)
    }

    // ── vertical board (phone) ─────────────────────────────────────────

    @Test fun mediumTilesFillTheRowBeforeWrapping() {
        val cells = rowMajor("a" to 2, "b" to 2, "c" to 2)
        assertEquals(0 to 0, at(cells, "a"))
        assertEquals(2 to 0, at(cells, "b"))
        assertEquals(0 to 2, at(cells, "c"))
    }

    @Test fun aLaterSmallTileFillsTheHoleABigTileLeft() {
        // medium + wide leaves a 2x2 hole at the top right; the small must move into it.
        val cells = rowMajor("m" to 2, "w" to 4, "s" to 1)
        assertEquals(0 to 0, at(cells, "m"))
        assertEquals(0 to 2, at(cells, "w"))
        assertEquals(2 to 0, at(cells, "s"))
    }

    @Test fun largeTileTakesFourByFour() {
        val cells = rowMajor("l" to 8, "s" to 1)
        val large = cells.first { it.id == "l" }
        assertEquals(4, large.cols)
        assertEquals(4, large.rows)
        assertEquals(0 to 0, large.col to large.row)
        assertEquals(0 to 4, at(cells, "s"))
    }

    @Test fun largeTileFitsAnEightColumnGrid() {
        val cells = rowMajor("l" to 8, "m" to 2, columns = 8)
        assertEquals(0 to 0, at(cells, "l"))
        assertEquals(4 to 0, at(cells, "m"))
    }

    @Test fun bigTilesStayOnTheMediumGrid() {
        val cells = rowMajor("s" to 1, "m" to 2, "w" to 4, "l" to 8)
        cells.filter { it.cols > 1 }.forEach { assertTrue(it.col % 2 == 0 && it.row % 2 == 0) }
    }

    @Test fun tilesNeverOverlapOrLeaveTheGrid() {
        val cells = rowMajor("a" to 2, "b" to 1, "c" to 4, "d" to 1, "e" to 8, "f" to 1, "g" to 2)
        val used = mutableSetOf<Pair<Int, Int>>()
        cells.forEach { cell ->
            assertTrue(cell.col + cell.cols <= 4)
            for (dc in 0 until cell.cols) for (dr in 0 until cell.rows) {
                assertTrue("overlap", used.add((cell.col + dc) to (cell.row + dr)))
            }
        }
        assertEquals(7, cells.size)
    }

    // ── horizontal board (Windows 8) ───────────────────────────────────

    @Test fun windows8BoardFillsColumnFirst() {
        val cells = columnMajor("a" to 2, "b" to 2, "c" to 2, "d" to 2)
        assertEquals(listOf(0 to 0, 0 to 2, 0 to 4, 2 to 0), cells.map { it.col to it.row })
    }

    @Test fun windows8GroupWidthIsWholeMediumColumns() {
        val group = layoutWin8Group(Win8GroupKind.HUBS, listOf(spec("a", 8), spec("b", 1)), rows = 6)
        assertEquals(4, group.columns)
        assertEquals(0 to 0, at(group.cells, "a"))
        assertEquals(0 to 4, at(group.cells, "b"))
    }

    @Test fun windows8LargeTileNeverExceedsTheRowBudget() {
        val cells = columnMajor("l" to 8, "m" to 2, rows = 4)
        assertTrue(cells.all { it.row + it.rows <= 4 })
    }
}
