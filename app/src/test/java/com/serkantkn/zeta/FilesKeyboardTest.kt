package com.serkantkn.zeta

import androidx.compose.ui.input.key.Key
import com.serkantkn.zeta.ui.screens.files.CursorDirection
import com.serkantkn.zeta.ui.screens.files.CursorDirection.DOWN
import com.serkantkn.zeta.ui.screens.files.CursorDirection.LEFT
import com.serkantkn.zeta.ui.screens.files.CursorDirection.RIGHT
import com.serkantkn.zeta.ui.screens.files.CursorDirection.UP
import com.serkantkn.zeta.ui.screens.files.FilesGridShape
import com.serkantkn.zeta.ui.screens.files.FilesKeyCommand
import com.serkantkn.zeta.ui.screens.files.filesKeyCommand
import com.serkantkn.zeta.ui.screens.files.moveCursor
import com.serkantkn.zeta.ui.screens.files.rangeBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Files hub with a keyboard and a mouse: which key means what, where the arrows take the
 * cursor in a list and in the two grids, and what Shift chooses.
 *
 * A wrong answer here is quiet on screen — Shift+Delete passing as a letter, a step right in the
 * tablet's grid landing three files away — so the table is pinned down.
 */
class FilesKeyboardTest {

    private fun command(key: Key, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) =
        filesKeyCommand(key, ctrl, shift, alt)

    // ── The table ───────────────────────────────────────────────────────────

    @Test
    fun `ctrl with a letter is the clipboard and choosing everything`() {
        assertEquals(FilesKeyCommand.Copy, command(Key.C, ctrl = true))
        assertEquals(FilesKeyCommand.Cut, command(Key.X, ctrl = true))
        assertEquals(FilesKeyCommand.Paste, command(Key.V, ctrl = true))
        assertEquals(FilesKeyCommand.SelectAll, command(Key.A, ctrl = true))
        assertEquals(FilesKeyCommand.Search, command(Key.F, ctrl = true))
    }

    @Test
    fun `a letter without ctrl is left alone`() {
        assertNull(command(Key.C))
        assertNull(command(Key.A, shift = true))
        // Ctrl+Alt is AltGr on many keyboards: a character, not a command.
        assertNull(command(Key.C, ctrl = true, alt = true))
    }

    @Test
    fun `delete asks either way and shift says which kind`() {
        assertEquals(FilesKeyCommand.Delete, command(Key.Delete))
        assertEquals(FilesKeyCommand.DeletePermanently, command(Key.Delete, shift = true))
    }

    @Test
    fun `the function keys are the desktop's`() {
        assertEquals(FilesKeyCommand.Rename, command(Key.F2))
        assertEquals(FilesKeyCommand.Search, command(Key.F3))
        assertEquals(FilesKeyCommand.Refresh, command(Key.F5))
    }

    @Test
    fun `backspace and alt-left go back, alt-up goes to the folder above`() {
        assertEquals(FilesKeyCommand.Back, command(Key.Backspace))
        assertEquals(FilesKeyCommand.Back, command(Key.DirectionLeft, alt = true))
        assertEquals(FilesKeyCommand.Up, command(Key.DirectionUp, alt = true))
        assertNull(command(Key.DirectionRight, alt = true))
    }

    @Test
    fun `enter opens, space chooses, escape puts away`() {
        assertEquals(FilesKeyCommand.Open, command(Key.Enter))
        assertEquals(FilesKeyCommand.Open, command(Key.NumPadEnter))
        assertEquals(FilesKeyCommand.Toggle, command(Key.Spacebar))
        assertEquals(FilesKeyCommand.Dismiss, command(Key.Escape))
    }

    @Test
    fun `the arrows move the cursor and shift stretches the choice`() {
        assertEquals(FilesKeyCommand.Move(DOWN, extend = false), command(Key.DirectionDown))
        assertEquals(FilesKeyCommand.Move(UP, extend = true), command(Key.DirectionUp, shift = true))
        assertEquals(FilesKeyCommand.Move(LEFT, extend = false), command(Key.DirectionLeft))
        assertEquals(FilesKeyCommand.Move(RIGHT, extend = true), command(Key.DirectionRight, shift = true))
    }

    // ── The cursor ──────────────────────────────────────────────────────────

    private fun move(index: Int, count: Int, direction: CursorDirection, shape: FilesGridShape) =
        moveCursor(index, count, direction, shape)

    @Test
    fun `in a list up and down step and the ends hold`() {
        val list = FilesGridShape.LIST
        assertEquals(1, move(0, 5, DOWN, list))
        assertEquals(4, move(4, 5, DOWN, list))
        assertEquals(0, move(0, 5, UP, list))
        assertEquals(2, move(3, 5, UP, list))
        // A list has nothing beside its rows.
        assertEquals(3, move(3, 5, LEFT, list))
        assertEquals(3, move(3, 5, RIGHT, list))
    }

    @Test
    fun `with no cursor yet the first arrow lands on the first item`() {
        assertEquals(0, move(-1, 5, DOWN, FilesGridShape.LIST))
        assertEquals(0, move(-1, 5, LEFT, FilesGridShape(3, sideways = true)))
        assertEquals(-1, move(-1, 0, DOWN, FilesGridShape.LIST))
    }

    @Test
    fun `the phone's grid fills across a row and steps a row down`() {
        // Three to a row, eight items: 0 1 2 / 3 4 5 / 6 7
        val grid = FilesGridShape(span = 3, sideways = false)
        assertEquals(1, move(0, 8, RIGHT, grid))
        assertEquals(3, move(2, 8, RIGHT, grid))
        assertEquals(4, move(1, 8, DOWN, grid))
        assertEquals(1, move(4, 8, UP, grid))
        assertEquals(1, move(1, 8, UP, grid))
        // Down from 5 into the short last row lands on its last item.
        assertEquals(7, move(5, 8, DOWN, grid))
        // Down from the last row stays.
        assertEquals(6, move(6, 8, DOWN, grid))
    }

    @Test
    fun `the tablet's grid fills down a column and steps a column right`() {
        // Three rows, eight items, column by column: 0 3 6 / 1 4 7 / 2 5
        val grid = FilesGridShape(span = 3, sideways = true)
        assertEquals(1, move(0, 8, DOWN, grid))
        assertEquals(3, move(2, 8, DOWN, grid))
        assertEquals(3, move(0, 8, RIGHT, grid))
        assertEquals(0, move(3, 8, LEFT, grid))
        assertEquals(0, move(0, 8, LEFT, grid))
        assertEquals(2, move(3, 8, UP, grid))
        // Right from 5 into the short last column lands on its last item.
        assertEquals(7, move(5, 8, RIGHT, grid))
        assertEquals(7, move(7, 8, RIGHT, grid))
    }

    // ── Shift ───────────────────────────────────────────────────────────────

    @Test
    fun `a range runs in the order shown whichever end it starts from`() {
        val paths = listOf("/a", "/b", "/c", "/d", "/e")
        assertEquals(listOf("/b", "/c", "/d"), rangeBetween(paths, "/b", "/d"))
        assertEquals(listOf("/b", "/c", "/d"), rangeBetween(paths, "/d", "/b"))
        assertEquals(listOf("/c"), rangeBetween(paths, "/c", "/c"))
    }

    @Test
    fun `a range with nowhere to start from is the one item`() {
        val paths = listOf("/a", "/b", "/c")
        assertEquals(listOf("/b"), rangeBetween(paths, null, "/b"))
        assertEquals(listOf("/b"), rangeBetween(paths, "/elsewhere", "/b"))
        assertEquals(emptyList<String>(), rangeBetween(paths, "/a", "/gone"))
    }
}
