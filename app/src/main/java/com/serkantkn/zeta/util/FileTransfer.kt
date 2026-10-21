package com.serkantkn.zeta.util

import java.util.Locale

/**
 * The arithmetic of a copy or a move in the Files hub: how far it has gone, how fast, and which
 * names are going to land on something already there. None of it touches the disk or the clock
 * on its own — the times are handed in — so all of it can be tested.
 */

// ── How far it has gone ─────────────────────────────────────────────────────

/** A paste as the header shows it: how many things, how far through, how fast, and what now. */
data class FileTransferProgress(
    val isMove: Boolean,
    val doneItems: Int,
    val totalItems: Int,
    val currentName: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val isPaused: Boolean = false
) {
    /**
     * How far through, from 0 to 1. Bytes when there are any to count; otherwise — a move that
     * was only a rename, a folder of empty files — the things themselves.
     */
    val fraction: Float
        get() = when {
            totalBytes > 0L -> (doneBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            totalItems > 0 -> (doneItems.toFloat() / totalItems).coerceIn(0f, 1f)
            else -> 0f
        }

    /** Rounded down, so "100" is only ever said of a copy that has finished. */
    val percent: Int
        get() = (fraction * 100f).toInt().coerceIn(0, 100)
}

/**
 * How fast the bytes are going, over the last few seconds rather than since the start: a copy
 * that crawled through a thousand small files and is now on one big video should say how fast it
 * is going now. The window keeps one sample at or before its far edge, so it always spans the
 * whole window once there is that much history.
 */
class TransferSpeed(private val windowMillis: Long = 3_000L) {
    private val times = ArrayDeque<Long>()
    private val totals = ArrayDeque<Long>()

    /** Notes that [doneBytes] had been written by [nowMillis]. */
    fun record(nowMillis: Long, doneBytes: Long) {
        times.addLast(nowMillis)
        totals.addLast(doneBytes)
        val edge = nowMillis - windowMillis
        while (times.size > 2 && times[1] <= edge) {
            times.removeFirst()
            totals.removeFirst()
        }
    }

    /** Bytes a second across the window, or 0 while there is not enough to say. */
    fun bytesPerSecond(): Long {
        if (times.size < 2) return 0L
        val elapsed = times.last() - times.first()
        if (elapsed <= 0L) return 0L
        val bytes = totals.last() - totals.first()
        return (bytes.coerceAtLeast(0L) * 1000L) / elapsed
    }

    /** Forgets the history: after a pause or a question the old pace says nothing about the new. */
    fun reset() {
        times.clear()
        totals.clear()
    }
}

/**
 * Keeps the count for a paste as it goes and decides when a new picture of it is worth drawing.
 * Every chunk of every file moves it on, but the header is redrawn about ten times a second at
 * most; more is work for nothing a person could read.
 */
class TransferMeter(
    private val isMove: Boolean,
    private val totalItems: Int,
    private val publishEveryMillis: Long = 100L,
    private val speed: TransferSpeed = TransferSpeed()
) {
    var totalBytes: Long = 0L
        private set
    var doneBytes: Long = 0L
        private set
    var doneItems: Int = 0
        private set
    var currentName: String = ""
        private set
    private var lastPublished: Long? = null

    fun setTotalBytes(bytes: Long) {
        totalBytes = bytes.coerceAtLeast(0L)
    }

    /** The thing (or, inside a folder, the file) being worked on now. */
    fun begin(name: String) {
        currentName = name
    }

    fun advance(bytes: Long, nowMillis: Long) {
        doneBytes += bytes
        speed.record(nowMillis, doneBytes)
    }

    /** A thing that will not be pasted after all no longer counts towards the bytes to go. */
    fun drop(bytes: Long) {
        totalBytes = (totalBytes - bytes).coerceAtLeast(doneBytes)
    }

    fun finishItem() {
        doneItems = (doneItems + 1).coerceAtMost(totalItems)
    }

    fun restartSpeed() = speed.reset()

    /**
     * Whether a new snapshot should go out now. [force] is for the moments a person would notice
     * being late — a new item starting, the first picture — and it restarts the interval.
     */
    fun due(nowMillis: Long, force: Boolean = false): Boolean {
        val last = lastPublished
        if (force || last == null || nowMillis - last >= publishEveryMillis) {
            lastPublished = nowMillis
            return true
        }
        return false
    }

    fun snapshot(isPaused: Boolean) = FileTransferProgress(
        isMove = isMove,
        doneItems = doneItems,
        totalItems = totalItems,
        currentName = currentName,
        doneBytes = doneBytes,
        totalBytes = totalBytes,
        bytesPerSecond = if (isPaused) 0L else speed.bytesPerSecond(),
        isPaused = isPaused
    )
}

// ── Names already taken ─────────────────────────────────────────────────────

/** What to do with a thing whose name is already used where it is being pasted. */
enum class ConflictChoice { REPLACE, SKIP, KEEP_BOTH }

/** A paste waiting on a person: the thing coming in and the thing already there under its name. */
data class FileConflict(
    /** Tells one question from the next, so a dialog answered once is not answered twice. */
    val id: Int,
    val name: String,
    val isDirectory: Boolean,
    val sourceSize: Long,
    val sourceModified: Long,
    val existingIsDirectory: Boolean,
    val existingSize: Long,
    val existingModified: Long,
    /** How many more names in this paste are expected to collide after this one. */
    val othersRemaining: Int
)

/**
 * Which things in a paste are going to collide, and the answer given "for all" once there is one.
 *
 * [names] holds, for each thing on the clipboard, its name, or null for one that will not be
 * pasted at all (gone, already there, a folder into itself). A name collides when the folder
 * already has it — compared without regard to case, as shared storage compares them — or when an
 * earlier thing on the same clipboard carries it too.
 */
class ConflictPlan(names: List<String?>, taken: Set<String>) {
    private val conflicts: List<Int>

    init {
        val seen = taken.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        conflicts = names.mapIndexedNotNull { index, name ->
            name ?: return@mapIndexedNotNull null
            val folded = name.lowercase(Locale.ROOT)
            if (!seen.add(folded)) index else null
        }
    }

    /** The answer given with "apply to all", which then stands for the rest of the paste. */
    var standing: ConflictChoice? = null
        private set

    val expected: Int get() = conflicts.size

    /** How many collisions are still to come after the thing at [index]. */
    fun othersAfter(index: Int): Int = conflicts.count { it > index }

    fun record(choice: ConflictChoice, applyToAll: Boolean) {
        if (applyToAll) standing = choice
    }
}

/** The entry in a folder that a name would land on, compared as shared storage compares names. */
fun findTakenName(name: String, taken: Collection<String>): String? {
    val folded = name.lowercase(Locale.ROOT)
    return taken.firstOrNull { it.lowercase(Locale.ROOT) == folded }
}
