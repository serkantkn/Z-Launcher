package com.serkantkn.zunelauncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.serkantkn.zunelauncher.data.model.StartFolders
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.ui.screens.home.MERGE_DWELL_MILLIS
import com.serkantkn.zunelauncher.ui.screens.home.MetroCell
import com.serkantkn.zunelauncher.ui.screens.home.StartDragState
import com.serkantkn.zunelauncher.ui.screens.home.folderSplitRow
import com.serkantkn.zunelauncher.ui.screens.home.mergeZoneOf
import com.serkantkn.zunelauncher.ui.screens.home.passedMiddleOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing the Start board: where a dragged tile lands, and what folders do with it. */
class StartBoardEditTest {

    // A row of three 100×100 tiles, side by side.
    private val order = listOf("a", "b", "c")
    private val rects = mapOf(
        "a" to Rect(0f, 0f, 100f, 100f),
        "b" to Rect(100f, 0f, 200f, 100f),
        "c" to Rect(200f, 0f, 300f, 100f)
    )

    /** A drag of "a" that has already been picked up, ready to be moved about. */
    private fun heldTileA(): StartDragState = StartDragState().apply {
        start("a", rects.getValue("a").topLeft, Offset(50f, 50f))
    }

    private fun StartDragState.dragTo(x: Float, y: Float, now: Long, moves: MutableList<Pair<Int, Int>>) {
        moveBy(Offset(x, y) - pointer)
        update(
            nowMillis = now,
            order = order,
            rects = rects,
            canMergeInto = { true },
            onReorder = { from, to -> moves += from to to }
        )
    }

    // ── Moving a tile ─────────────────────────────────────────────────────────────────────────

    @Test
    fun brushingATilesEdgeDoesNotMoveIt() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        // Just inside b's left edge, nowhere near its middle.
        drag.dragTo(110f, 50f, now = 0, moves = moves)
        drag.dragTo(120f, 50f, now = 40, moves = moves)

        assertTrue(moves.isEmpty())
    }

    @Test
    fun passingATilesMiddlePushesItAsideOnce() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        drag.dragTo(110f, 50f, now = 0, moves = moves)
        drag.dragTo(160f, 50f, now = 40, moves = moves)
        drag.dragTo(180f, 50f, now = 80, moves = moves)
        drag.dragTo(195f, 50f, now = 120, moves = moves)

        assertEquals(listOf(0 to 1), moves)
    }

    @Test
    fun restingOnATileLightsItUpAsAFolder() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        drag.dragTo(150f, 50f, now = 0, moves = moves)
        drag.dragTo(152f, 51f, now = 200, moves = moves)
        assertNull(drag.mergeTargetId)

        drag.dragTo(152f, 51f, now = 200 + MERGE_DWELL_MILLIS, moves = moves)

        assertEquals("b", drag.mergeTargetId)
        assertTrue("a resting tile is not also rearranged", moves.isEmpty())
    }

    @Test
    fun movingOnAgainCancelsTheFolder() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        drag.dragTo(150f, 50f, now = 0, moves = moves)
        drag.dragTo(150f, 50f, now = MERGE_DWELL_MILLIS, moves = moves)
        assertEquals("b", drag.mergeTargetId)

        drag.dragTo(190f, 50f, now = MERGE_DWELL_MILLIS + 40, moves = moves)

        assertNull(drag.mergeTargetId)
    }

    @Test
    fun droppingOnARestedTileReportsTheFolder() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        drag.dragTo(150f, 50f, now = 0, moves = moves)
        drag.dragTo(150f, 50f, now = MERGE_DWELL_MILLIS, moves = moves)

        assertEquals("b", drag.finish())
        assertFalse(drag.isDragging)
    }

    @Test
    fun droppingAfterDrivingClearAcrossATileReportsNoFolder() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        drag.dragTo(110f, 50f, now = 0, moves = moves)
        drag.dragTo(196f, 50f, now = 40, moves = moves)

        assertEquals(listOf(0 to 1), moves)
        assertNull(drag.finish())
    }

    @Test
    fun mostOfATileIsItsMiddle() {
        val zone = mergeZoneOf(rects.getValue("b"))

        // Comfortably more than half the tile, so a folder is not a thing you have to aim at.
        assertTrue(zone.width > 55f)
        assertTrue(zone.contains(Offset(125f, 50f)))
        assertTrue(zone.contains(Offset(175f, 50f)))
    }

    @Test
    fun crossingTheMiddleSlowlyNeverRearrangesIt() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        // A finger creeping across the middle of b is aiming at a folder, not at b's slot.
        listOf(125f, 140f, 150f, 160f, 175f).forEachIndexed { step, x ->
            drag.dragTo(x, 50f, now = step * 40L, moves = moves)
        }

        assertTrue(moves.isEmpty())
    }

    @Test
    fun lettingGoInTheMiddleMakesTheFolderWithoutWaiting() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        // Dropped after 40 ms, far short of the dwell that lights the tile up.
        drag.dragTo(150f, 50f, now = 0, moves = moves)
        drag.dragTo(155f, 50f, now = 40, moves = moves)

        assertNull("nothing has lit up yet", drag.mergeTargetId)
        assertEquals("b", drag.finish())
    }

    @Test
    fun lettingGoAfterPushingATileAsideDoesNotAlsoFolderIt() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        // Past b's far edge, then back into its middle without leaving it.
        drag.dragTo(110f, 50f, now = 0, moves = moves)
        drag.dragTo(196f, 50f, now = 40, moves = moves)
        drag.dragTo(150f, 50f, now = 80, moves = moves)

        assertNull(drag.finish())
    }

    @Test
    fun draggingPastTheEndPutsTheTileLast() {
        val drag = heldTileA()
        val moves = mutableListOf<Pair<Int, Int>>()

        drag.dragTo(150f, 400f, now = 0, moves = moves)

        assertEquals(listOf(0 to 2), moves)
    }

    @Test
    fun aTileThatRefusesFoldersIsNeverATarget() {
        val drag = heldTileA()
        drag.moveBy(Offset(150f, 50f) - drag.pointer)
        drag.update(0, order, rects, canMergeInto = { false }) { _, _ -> }
        drag.update(MERGE_DWELL_MILLIS, order, rects, canMergeInto = { false }) { _, _ -> }

        assertNull(drag.mergeTargetId)
    }

    @Test
    fun theMiddleIsOnlyPassedInTheDirectionOfTravel() {
        val b = rects.getValue("b")

        // Entering from the left, x has to get past 150.
        assertFalse(passedMiddleOf(b, entry = Offset(105f, 50f), pointer = Offset(140f, 50f)))
        assertTrue(passedMiddleOf(b, entry = Offset(105f, 50f), pointer = Offset(160f, 50f)))
        // Entering from the right, the other way round.
        assertFalse(passedMiddleOf(b, entry = Offset(195f, 50f), pointer = Offset(160f, 50f)))
        assertTrue(passedMiddleOf(b, entry = Offset(195f, 50f), pointer = Offset(140f, 50f)))
    }

    // ── Folders ───────────────────────────────────────────────────────────────────────────────

    private fun board(vararg ids: String) = ids.map { StartTileItem(it, span = 2) }

    @Test
    fun droppingOneTileOnAnotherMakesAFolderWhereTheTargetStood() {
        val result = StartFolders.merge(board("hub:PHONE", "app:x", "app:y"), "app:y", "app:x")

        assertEquals(2, result.size)
        val folder = result[1]
        assertTrue(folder.isFolder)
        assertEquals(listOf("app:x", "app:y"), folder.children)
        assertTrue(result.none { it.id == "app:y" && !it.isFolder })
    }

    @Test
    fun droppingOntoAFolderJoinsIt() {
        val start = listOf(
            StartTileItem.newFolder(listOf("app:a", "app:b")),
            StartTileItem("app:c", 2)
        )

        val result = StartFolders.merge(start, "app:c", start.first().id)

        assertEquals(1, result.size)
        assertEquals(listOf("app:a", "app:b", "app:c"), result.first().children)
    }

    @Test
    fun foldersNeverNest() {
        val folder = StartTileItem.newFolder(listOf("app:a", "app:b"))
        val start = listOf(folder, StartTileItem("app:c", 2))

        assertEquals(start, StartFolders.merge(start, folder.id, "app:c"))
    }

    @Test
    fun takingATileOutPutsItBackBesideTheFolder() {
        val folder = StartTileItem.newFolder(listOf("app:a", "app:b", "app:c"))
        val start = listOf(StartTileItem("hub:PHONE", 2), folder)

        val result = StartFolders.extract(start, folder.id, "app:b", span = 2)

        assertEquals(listOf("app:a", "app:c"), result.first { it.isFolder }.children)
        assertEquals("app:b", result.last().id)
    }

    @Test
    fun aFolderDownToOneTileGivesItBackAndDisappears() {
        val folder = StartTileItem.newFolder(listOf("app:a", "app:b"))

        val result = StartFolders.extract(listOf(folder), folder.id, "app:b", span = 2)

        assertTrue(result.none { it.isFolder })
        assertEquals(setOf("app:a", "app:b"), result.map { it.id }.toSet())
    }

    @Test
    fun emptyingAFolderLaysItsTilesOutWhereItStood() {
        val folder = StartTileItem.newFolder(listOf("app:a", "app:b", "app:c"))
        val start = listOf(StartTileItem("hub:PHONE", 2), folder, StartTileItem("hub:CLOCK", 2))

        val result = StartFolders.dissolve(start, folder.id) { 2 }

        assertEquals(
            listOf("hub:PHONE", "app:a", "app:b", "app:c", "hub:CLOCK"),
            result.map { it.id }
        )
    }

    @Test
    fun unpinningATileTakesItOutOfItsFolderToo() {
        val folder = StartTileItem.newFolder(listOf("app:a", "app:b", "app:c"))

        val result = StartFolders.removeTile(listOf(folder), "app:b")

        assertEquals(listOf("app:a", "app:c"), result.first().children)
    }

    @Test
    fun anEmptiedFolderDoesNotLinger() {
        val folder = StartTileItem.newFolder(listOf("app:a"))

        assertTrue(StartFolders.collapseThinFolders(listOf(folder)).none { it.isFolder })
        assertTrue(StartFolders.collapseThinFolders(listOf(folder.copy(children = emptyList()))).isEmpty())
    }

    @Test
    fun reorderingInsideAFolderKeepsEveryTile() {
        val folder = StartTileItem.newFolder(listOf("app:a", "app:b", "app:c"))

        val result = StartFolders.reorderChildren(listOf(folder), folder.id, from = 2, to = 0)

        assertEquals(listOf("app:c", "app:a", "app:b"), result.first().children)
    }

    @Test
    fun everyTileOnTheBoardIsAccountedForFoldersIncluded() {
        val tiles = listOf(
            StartTileItem("hub:PHONE", 2),
            StartTileItem.newFolder(listOf("app:a", "app:b"))
        )

        assertEquals(setOf("hub:PHONE", "app:a", "app:b"), StartFolders.allTileIds(tiles))
    }

    // ── Where an open folder unfolds ──────────────────────────────────────────────────────────

    @Test
    fun aFolderUnfoldsBelowTheRowBlockItStandsIn() {
        val cells = listOf(
            MetroCell("folder:x", col = 0, row = 0, cols = 2, rows = 2),
            MetroCell("app:tall", col = 2, row = 0, cols = 2, rows = 4),
            MetroCell("app:below", col = 0, row = 4, cols = 2, rows = 2)
        )

        // Not row 2, which would drive the band through the middle of the tall tile beside it.
        assertEquals(4, folderSplitRow(cells, "folder:x"))
    }

    @Test
    fun aFolderThatIsNotOnTheBoardUnfoldsNowhere() {
        assertNull(folderSplitRow(emptyList(), "folder:x"))
    }

    // ── Storage ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun aFolderSurvivesBeingWrittenAndReadBack() {
        val folder = StartTileItem.newFolder(listOf("app:a", "app:b"), span = 4, name = "işler")

        val restored = StartTileItem.fromJson(JSONObject(folder.toJson().toString()))

        assertEquals(folder.id, restored.id)
        assertEquals(4, restored.span)
        assertEquals(listOf("app:a", "app:b"), restored.children)
        assertEquals("işler", restored.name)
        assertTrue(restored.isFolder)
    }

    @Test
    fun anOrdinaryTileStoresNothingAboutFolders() {
        val stored = StartTileItem("app:a", 2).toJson()

        assertFalse(stored.has("children"))
        assertTrue(StartTileItem.fromJson(JSONObject(stored.toString())).children.isEmpty())
    }
}
