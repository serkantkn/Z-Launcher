package com.serkantkn.zeta

import com.serkantkn.zeta.util.ConflictChoice
import com.serkantkn.zeta.util.ConflictPlan
import com.serkantkn.zeta.util.FileTransferProgress
import com.serkantkn.zeta.util.TransferMeter
import com.serkantkn.zeta.util.TransferSpeed
import com.serkantkn.zeta.util.findTakenName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A paste's arithmetic: how far, how fast, and which names collide.
 *
 * What goes wrong here is seen rather than lost — a percent that says 100 before the end, a speed
 * that still counts the minute spent on a question, a "3 more" that is really 2 — but it is seen
 * by everybody who copies anything.
 */
class FileTransferTest {

    private fun progress(
        doneBytes: Long = 0L,
        totalBytes: Long = 0L,
        doneItems: Int = 0,
        totalItems: Int = 1
    ) = FileTransferProgress(
        isMove = false,
        doneItems = doneItems,
        totalItems = totalItems,
        currentName = "a",
        doneBytes = doneBytes,
        totalBytes = totalBytes,
        bytesPerSecond = 0L
    )

    // ── Percent ──

    @Test
    fun `percent is counted in bytes when there are bytes`() {
        assertEquals(43, progress(doneBytes = 430, totalBytes = 1000).percent)
    }

    @Test
    fun `percent never says 100 before the last byte`() {
        assertEquals(99, progress(doneBytes = 999_999, totalBytes = 1_000_000).percent)
        assertEquals(100, progress(doneBytes = 1_000_000, totalBytes = 1_000_000).percent)
    }

    @Test
    fun `percent falls back to items when there are no bytes to count`() {
        assertEquals(50, progress(doneItems = 1, totalItems = 2).percent)
        assertEquals(0, progress(totalItems = 0).percent)
    }

    @Test
    fun `percent stays within bounds when the disk changed under it`() {
        assertEquals(100, progress(doneBytes = 1500, totalBytes = 1000).percent)
        assertEquals(1f, progress(doneBytes = 1500, totalBytes = 1000).fraction)
    }

    // ── Speed ──

    @Test
    fun `speed needs two samples`() {
        val speed = TransferSpeed()
        assertEquals(0L, speed.bytesPerSecond())
        speed.record(0L, 0L)
        assertEquals(0L, speed.bytesPerSecond())
        speed.record(1_000L, 2_000_000L)
        assertEquals(2_000_000L, speed.bytesPerSecond())
    }

    @Test
    fun `speed follows the last few seconds rather than the whole copy`() {
        val speed = TransferSpeed(windowMillis = 2_000L)
        // A slow start: a megabyte a second for ten seconds…
        for (second in 0..10) speed.record(second * 1_000L, second * 1_000_000L)
        // …then ten megabytes a second.
        for (second in 11..14) speed.record(second * 1_000L, 10_000_000L + (second - 10) * 10_000_000L)
        assertEquals(10_000_000L, speed.bytesPerSecond())
    }

    @Test
    fun `a reset speed forgets the pause before it`() {
        val speed = TransferSpeed()
        speed.record(0L, 0L)
        speed.record(1_000L, 1_000L)
        speed.reset()
        assertEquals(0L, speed.bytesPerSecond())
        speed.record(60_000L, 1_000L)
        speed.record(61_000L, 5_000L)
        assertEquals(4_000L, speed.bytesPerSecond())
    }

    // ── The meter ──

    @Test
    fun `the meter publishes about ten times a second unless forced`() {
        val meter = TransferMeter(isMove = false, totalItems = 1)
        assertTrue(meter.due(0L))
        assertFalse(meter.due(50L))
        assertTrue(meter.due(100L))
        assertFalse(meter.due(150L))
        assertTrue(meter.due(160L, force = true))
        assertFalse(meter.due(200L))
    }

    @Test
    fun `a skipped thing no longer counts towards the bytes to go`() {
        val meter = TransferMeter(isMove = true, totalItems = 2)
        meter.setTotalBytes(300L)
        meter.advance(100L, 0L)
        meter.finishItem()
        meter.drop(200L)
        meter.finishItem()
        val shot = meter.snapshot(isPaused = false)
        assertEquals(100, shot.percent)
        assertEquals(2, shot.doneItems)
        assertTrue(shot.isMove)
    }

    @Test
    fun `a paused snapshot says no speed`() {
        val meter = TransferMeter(isMove = false, totalItems = 1)
        meter.setTotalBytes(10_000L)
        meter.advance(0L, 0L)
        meter.advance(5_000L, 1_000L)
        assertEquals(5_000L, meter.snapshot(isPaused = false).bytesPerSecond)
        val paused = meter.snapshot(isPaused = true)
        assertEquals(0L, paused.bytesPerSecond)
        assertTrue(paused.isPaused)
    }

    @Test
    fun `items done never pass the items there are`() {
        val meter = TransferMeter(isMove = false, totalItems = 1)
        meter.finishItem()
        meter.finishItem()
        assertEquals(1, meter.doneItems)
    }

    // ── Names already taken ──

    @Test
    fun `a name already in the folder collides whatever its case`() {
        val plan = ConflictPlan(listOf("Rapor.pdf", "yeni.txt"), setOf("rapor.pdf"))
        assertEquals(1, plan.expected)
        assertEquals(0, plan.othersAfter(0))
    }

    @Test
    fun `two things on one clipboard with one name collide with each other`() {
        val plan = ConflictPlan(listOf("a.jpg", "b.jpg", "a.jpg"), emptySet())
        assertEquals(1, plan.expected)
        assertEquals(1, plan.othersAfter(1))
        assertEquals(0, plan.othersAfter(2))
    }

    @Test
    fun `things that will not be pasted are not counted`() {
        val plan = ConflictPlan(listOf(null, "a", "b", "c"), setOf("a", "b", "c"))
        assertEquals(3, plan.expected)
        // Asked about "a", there are two more to come.
        assertEquals(2, plan.othersAfter(1))
    }

    @Test
    fun `an answer stands for the rest only when it was given for all`() {
        val plan = ConflictPlan(listOf("a", "b"), setOf("a", "b"))
        plan.record(ConflictChoice.REPLACE, applyToAll = false)
        assertNull(plan.standing)
        plan.record(ConflictChoice.KEEP_BOTH, applyToAll = true)
        assertEquals(ConflictChoice.KEEP_BOTH, plan.standing)
    }

    @Test
    fun `the taken name found is the one on disk`() {
        assertEquals("Rapor.PDF", findTakenName("rapor.pdf", listOf("x", "Rapor.PDF")))
        assertNull(findTakenName("rapor.pdf", listOf("rapor.pdf.bak")))
    }
}
