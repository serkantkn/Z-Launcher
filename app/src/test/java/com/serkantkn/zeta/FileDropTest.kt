package com.serkantkn.zeta

import com.serkantkn.zeta.util.DropAction
import com.serkantkn.zeta.util.dropActionFor
import com.serkantkn.zeta.util.volumeRootOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What letting go of dragged files does.
 *
 * A drag is quicker than a menu and so easier to get wrong without noticing: a move that should
 * have been a copy leaves the phone without the photos once the card is out, and a folder let go
 * over itself copies until the disk is full. The rules are Windows' and are pinned down here.
 */
class FileDropTest {

    private val phone = "/storage/emulated/0"
    private val card = "/storage/1A2B-3C4D"
    private val roots = listOf(phone, card)

    // ── Which volume ────────────────────────────────────────────────────────

    @Test
    fun `a path belongs to the volume it sits under`() {
        assertEquals(phone, volumeRootOf("$phone/DCIM/Camera/a.jpg", roots))
        assertEquals(card, volumeRootOf("$card/Music", roots))
        assertEquals(phone, volumeRootOf(phone, roots))
        assertEquals(phone, volumeRootOf("$phone/", roots))
    }

    @Test
    fun `a volume is a folder, not the start of a name`() {
        // "/storage/emulated/01" is not inside "/storage/emulated/0".
        assertNull(volumeRootOf("/storage/emulated/01/a.txt", roots))
        assertNull(volumeRootOf("/data/local/tmp/a.txt", roots))
    }

    @Test
    fun `the deepest volume wins when one is mounted inside another`() {
        val nested = listOf(phone, "$phone/usb")
        assertEquals("$phone/usb", volumeRootOf("$phone/usb/song.mp3", nested))
        assertEquals(phone, volumeRootOf("$phone/usbnot/song.mp3", nested))
    }

    // ── Move or copy ────────────────────────────────────────────────────────

    @Test
    fun `a drop on the same volume moves`() {
        assertEquals(
            DropAction.MOVE,
            dropActionFor(listOf("$phone/Download/a.jpg", "$phone/Download/b.jpg"), "$phone/Pictures", roots)
        )
    }

    @Test
    fun `a drop on another volume copies`() {
        assertEquals(DropAction.COPY, dropActionFor(listOf("$phone/Download/a.jpg"), "$card/Pictures", roots))
        assertEquals(DropAction.COPY, dropActionFor(listOf("$card/Music/s.mp3"), "$phone/Music", roots))
    }

    @Test
    fun `one thing from another volume makes the whole drop a copy`() {
        assertEquals(
            DropAction.COPY,
            dropActionFor(listOf("$phone/Download/a.jpg", "$card/b.jpg"), "$phone/Pictures", roots)
        )
    }

    @Test
    fun `a place on no known volume is copied to, never moved to`() {
        assertEquals(DropAction.COPY, dropActionFor(listOf("$phone/a.jpg"), "/mnt/somewhere", roots))
        assertEquals(DropAction.COPY, dropActionFor(listOf("$phone/a.jpg"), "$phone/Pictures", emptyList()))
    }

    @Test
    fun `a drop on the top of a volume is a drop on that volume`() {
        assertEquals(DropAction.MOVE, dropActionFor(listOf("$phone/Download/a.jpg"), phone, roots))
        assertEquals(DropAction.COPY, dropActionFor(listOf("$phone/Download/a.jpg"), card, roots))
    }

    // ── Refused ─────────────────────────────────────────────────────────────

    @Test
    fun `a folder cannot be dropped on itself`() {
        assertEquals(DropAction.REFUSED, dropActionFor(listOf("$phone/Trips"), "$phone/Trips", roots))
    }

    @Test
    fun `a folder cannot be dropped into one of its own folders`() {
        assertEquals(DropAction.REFUSED, dropActionFor(listOf("$phone/Trips"), "$phone/Trips/Rome/day 1", roots))
        // Not even as part of a larger drop.
        assertEquals(
            DropAction.REFUSED,
            dropActionFor(listOf("$phone/a.jpg", "$phone/Trips"), "$phone/Trips/Rome", roots)
        )
    }

    @Test
    fun `a sibling whose name starts the same is not inside`() {
        assertEquals(DropAction.MOVE, dropActionFor(listOf("$phone/Trips"), "$phone/Trips 2024", roots))
    }

    @Test
    fun `things dropped on the folder they are already in stay put`() {
        assertEquals(DropAction.REFUSED, dropActionFor(listOf("$phone/Pictures/a.jpg"), "$phone/Pictures", roots))
        assertEquals(
            DropAction.REFUSED,
            dropActionFor(listOf("$phone/Pictures/a.jpg", "$phone/Pictures/b.jpg"), "$phone/Pictures/", roots)
        )
    }

    @Test
    fun `a drop only partly already there goes ahead`() {
        // Search results can come from several folders; the paste skips the one already there.
        assertEquals(
            DropAction.MOVE,
            dropActionFor(listOf("$phone/Pictures/a.jpg", "$phone/Download/b.jpg"), "$phone/Pictures", roots)
        )
    }

    @Test
    fun `nothing dragged is nothing to drop`() {
        assertEquals(DropAction.REFUSED, dropActionFor(emptyList(), "$phone/Pictures", roots))
    }
}
