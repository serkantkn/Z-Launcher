package com.serkantkn.zeta

import com.serkantkn.zeta.util.SafeReplace
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafeReplaceTest {
    private lateinit var folder: File

    @Before
    fun setUp() {
        folder = Files.createTempDirectory("zune-replace").toFile()
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun write(name: String, text: String) = File(folder, name).apply { writeText(text) }

    @Test
    fun `the new one takes the old one's place and the old one is gone`() {
        val old = write("rapor.txt", "eski")
        val incoming = write(SafeReplace.incomingName("rapor.txt"), "yeni")

        assertTrue(SafeReplace.swapIn(incoming, old, File(folder, "rapor.txt")))

        assertEquals("yeni", File(folder, "rapor.txt").readText())
        assertEquals(listOf("rapor.txt"), folder.list()!!.toList())
    }

    @Test
    fun `a folder is replaced whole`() {
        val old = File(folder, "Projeler").apply { mkdirs() }
        File(old, "a.txt").writeText("eski")
        val incoming = File(folder, SafeReplace.incomingName("Projeler")).apply { mkdirs() }
        File(incoming, "b.txt").writeText("yeni")

        assertTrue(SafeReplace.swapIn(incoming, old, File(folder, "Projeler")))

        assertEquals(listOf("b.txt"), File(folder, "Projeler").list()!!.toList())
        assertEquals(1, folder.list()!!.size)
    }

    @Test
    fun `when the new one cannot take the name the old one is put back`() {
        val old = write("rapor.txt", "eski")
        // Nothing to move in: the second rename fails.
        val missing = File(folder, SafeReplace.incomingName("rapor.txt"))

        assertFalse(SafeReplace.swapIn(missing, old, File(folder, "rapor.txt")))

        assertEquals("eski", File(folder, "rapor.txt").readText())
        assertEquals(listOf("rapor.txt"), folder.list()!!.toList())
    }

    @Test
    fun `a half-written new one is cleared and the old one never moved`() {
        write("rapor.txt", "eski")
        write(SafeReplace.incomingName("rapor.txt"), "yar")

        SafeReplace.recover(folder)

        assertEquals("eski", File(folder, "rapor.txt").readText())
        assertEquals(listOf("rapor.txt"), folder.list()!!.toList())
    }

    @Test
    fun `an old one left aside goes back when the new one never arrived`() {
        write(SafeReplace.asideName("rapor.txt"), "eski")
        write(SafeReplace.incomingName("rapor.txt"), "yeni")

        SafeReplace.recover(folder)

        assertEquals("eski", File(folder, "rapor.txt").readText())
        assertEquals(listOf("rapor.txt"), folder.list()!!.toList())
    }

    @Test
    fun `an old one left aside is deleted once the new one is in place`() {
        write("rapor.txt", "yeni")
        write(SafeReplace.asideName("rapor.txt"), "eski")

        SafeReplace.recover(folder)

        assertEquals("yeni", File(folder, "rapor.txt").readText())
        assertEquals(listOf("rapor.txt"), folder.list()!!.toList())
    }

    @Test
    fun `ordinary names are not taken for leftovers`() {
        assertNull(SafeReplace.leftover("rapor.txt"))
        assertNull(SafeReplace.leftover(".nomedia"))
        assertNull(SafeReplace.leftover(".zune-incoming."))
        assertEquals(SafeReplace.Leftover.ASIDE to "a.txt", SafeReplace.leftover(SafeReplace.asideName("a.txt")))
    }
}
