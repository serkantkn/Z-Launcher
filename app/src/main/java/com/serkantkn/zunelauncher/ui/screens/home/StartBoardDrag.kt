package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/*
 * Dragging tiles around the Start board.
 *
 * Every tile is two targets at once: its middle makes a folder, its outer band moves it aside.
 * The middle is deliberately huge - most of the tile - because dropping one tile onto another is
 * the whole gesture for making a folder, and a gesture you have to aim at is no gesture at all.
 *
 * Three rules:
 *
 *  1. The finger owns the tile. The pointer is tracked in board coordinates, so the held tile is
 *     drawn under the finger no matter how the rest of the board re-flows or scrolls.
 *  2. The middle of a tile never rearranges it. A tile only steps aside once the finger has driven
 *     clear across it: out of the middle and past its far side, in the direction of travel.
 *  3. Resting in the middle lights it up, and letting go there makes the folder. The light is a
 *     preview of what dropping would do, not a condition for it.
 */

/** How long the finger has to sit in a tile's middle before it lights up as a folder. */
internal const val MERGE_DWELL_MILLIS = 240L

/** How much of a tile, across and down, counts as its middle. The rest is the step-aside band. */
internal const val MERGE_ZONE_FRACTION = 0.62f

/** A wobble smaller than this is not travel, so a shaking finger still counts as holding still. */
internal const val MERGE_JITTER_PX = 14f

/** The middle of [rect]: the part of a tile that makes a folder rather than stepping aside. */
internal fun mergeZoneOf(rect: Rect): Rect {
    val inset = (1f - MERGE_ZONE_FRACTION) / 2f
    return Rect(
        rect.left + rect.width * inset,
        rect.top + rect.height * inset,
        rect.right - rect.width * inset,
        rect.bottom - rect.height * inset
    )
}

/**
 * The board's live drag. Holds the pointer in board coordinates, the grab point inside the tile,
 * and which tile is lit up as a folder target.
 */
class StartDragState {

    /** The tile under the finger, or null when nothing is being dragged. */
    var dragId by mutableStateOf<String?>(null)
        private set

    /** Where the finger is, in board coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** Where inside the tile it was grabbed, so it keeps hanging off the same spot. */
    var grabWithinTile by mutableStateOf(Offset.Zero)
        private set

    /** The tile lit up as a folder target: the finger has settled in its middle. */
    var mergeTargetId by mutableStateOf<String?>(null)
        private set

    /** The tile the finger is over, and where it came in. */
    private var hoverId: String? = null
    private var hoverEntry = Offset.Zero

    /** When the finger entered the middle of [hoverId], and whether letting go there would merge. */
    private var middleSince: Long? = null
    private var middleCandidate: String? = null

    /** The tile that has already been pushed aside for this pass, so it moves once. */
    private var reorderedFor: String? = null

    val isDragging: Boolean get() = dragId != null

    fun start(id: String, tileTopLeft: Offset, grabPoint: Offset) {
        dragId = id
        grabWithinTile = grabPoint
        pointer = tileTopLeft + grabPoint
        clearHover()
    }

    fun moveBy(delta: Offset) {
        pointer += delta
    }

    /** Auto-scrolling moves the board under a still finger; the pointer has to follow. */
    fun scrolledBy(delta: Float) {
        pointer += Offset(0f, delta)
    }

    /**
     * Reads the finger's position and decides what it means. [nowMillis] is passed in rather than
     * read here so the rule can be tested without waiting for a clock.
     */
    fun update(
        nowMillis: Long,
        order: List<String>,
        rects: Map<String, Rect>,
        canMergeInto: (String) -> Boolean,
        onReorder: (from: Int, to: Int) -> Unit
    ) {
        val id = dragId ?: return
        val hitId = order.firstOrNull { it != id && rects[it]?.contains(pointer) == true }

        if (hitId == null) {
            // Past the end of the board the held tile belongs last; anywhere else empty, wait.
            val alreadyAppended = reorderedFor == END_OF_BOARD
            clearHover()
            val lastBottom = rects.filterKeys { it != id }.values.maxOfOrNull { it.bottom }
            if (lastBottom != null && pointer.y > lastBottom && order.isNotEmpty()) {
                val from = order.indexOf(id)
                if (from >= 0 && from != order.lastIndex && !alreadyAppended) {
                    reorderedFor = END_OF_BOARD
                    onReorder(from, order.lastIndex)
                }
            }
            return
        }

        if (hitId != hoverId) {
            hoverId = hitId
            hoverEntry = pointer
            middleSince = null
            middleCandidate = null
            reorderedFor = null
            mergeTargetId = null
        }

        val rect = rects.getValue(hitId)

        // The middle: a folder in waiting.
        if (mergeZoneOf(rect).contains(pointer) && reorderedFor != hitId && canMergeInto(hitId)) {
            middleCandidate = hitId
            val since = middleSince ?: nowMillis.also { middleSince = it }
            if (nowMillis - since >= MERGE_DWELL_MILLIS) mergeTargetId = hitId
            return
        }

        middleSince = null
        middleCandidate = null
        mergeTargetId = null

        // The outer band: step aside, once.
        if (reorderedFor == hitId) return
        if (!passedMiddleOf(rect, hoverEntry, pointer)) return

        val from = order.indexOf(id)
        val to = order.indexOf(hitId)
        if (from < 0 || to < 0 || from == to) return
        reorderedFor = hitId
        onReorder(from, to)
    }

    /**
     * Ends the drag and reports the folder it was dropped into, if any. Letting go in a tile's
     * middle makes the folder whether or not the tile had already lit up: the light is feedback,
     * not a gate.
     */
    fun finish(): String? {
        val merge = mergeTargetId ?: middleCandidate
        clear()
        return merge
    }

    fun cancel() = clear()

    private fun clearHover() {
        hoverId = null
        middleSince = null
        middleCandidate = null
        reorderedFor = null
        mergeTargetId = null
    }

    private fun clear() {
        dragId = null
        pointer = Offset.Zero
        clearHover()
    }

    private companion object {
        const val END_OF_BOARD = "-end-of-board-"
    }
}

/**
 * True once [pointer] has travelled past the middle of [rect] in the direction it came from.
 *
 * Entering a tile is not enough to push it aside, and neither is a wobble: a finger that lands
 * just past the middle and holds still is making a folder, so anything below the resting
 * threshold does not count as travel at all.
 */
internal fun passedMiddleOf(rect: Rect, entry: Offset, pointer: Offset): Boolean {
    val travel = pointer - entry
    if (travel.getDistance() < MERGE_JITTER_PX) return false
    val centre = rect.center
    return if (kotlin.math.abs(travel.x) >= kotlin.math.abs(travel.y)) {
        if (travel.x >= 0f) pointer.x > centre.x else pointer.x < centre.x
    } else {
        if (travel.y >= 0f) pointer.y > centre.y else pointer.y < centre.y
    }
}

@Composable
fun rememberStartDragState(): StartDragState = remember { StartDragState() }
