package com.serkantkn.zeta

import com.serkantkn.zeta.data.repository.RecycleBin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The Files hub's recycle bin, on a real folder.
 *
 * A bin that loses track of where something came from, puts it back over a file that took its
 * name, or forgets a delete because two landed at once has turned a safety net into a way to lose
 * things twice. Each of those is pinned down here.
 */
class RecycleBinTest {

    private lateinit var volume: File
    private lateinit var card: File
    private var now = 1_000_000_000_000L
    private val bin = RecycleBin(io = Dispatchers.Unconfined, clock = { now })

    @Before
    fun setUp() {
        val base = Files.createTempDirectory("zune-bin").toFile()
        volume = File(base, "emulated0").apply { mkdirs() }
        card = File(base, "card").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        volume.parentFile?.deleteRecursively()
    }

    private val roots get() = listOf(volume, card)

    private fun write(parent: File, name: String, text: String = name): File =
        File(parent, name).apply { parentFile?.mkdirs(); writeText(text) }

    @Test
    fun `a delete is a move into the volume's own hidden bin`() = runBlocking {
        val photo = write(File(volume, "DCIM"), "tatil.jpg", "sun")
        val onCard = write(card, "notes.txt")

        val result = bin.trash(listOf(photo, onCard), roots)

        assertTrue(result.failed.isEmpty())
        assertFalse(photo.exists())
        assertFalse(onCard.exists())
        // Each waits on its own volume, and the scanner is told to keep out of both.
        assertTrue(File(volume, "${RecycleBin.FOLDER_NAME}/.nomedia").exists())
        assertTrue(File(card, "${RecycleBin.FOLDER_NAME}/.nomedia").exists())
        val entries = bin.list(roots)
        assertEquals(2, entries.size)
        val entry = entries.first { it.name == "tatil.jpg" }
        assertEquals(photo.absolutePath, entry.originalPath)
        assertEquals(3L, entry.size)
        assertEquals(now, entry.deletedAt)
        assertEquals("sun", entry.payload.readText())
    }

    @Test
    fun `the index survives being read back by a fresh bin`() = runBlocking {
        val folder = File(volume, "Projeler").apply { mkdirs() }
        write(folder, "a.txt", "12345")
        write(File(folder, "alt"), "b.txt", "123")
        bin.trash(listOf(folder), roots)

        val again = RecycleBin(io = Dispatchers.Unconfined).list(roots)

        assertEquals(1, again.size)
        val entry = again.single()
        assertTrue(entry.isDirectory)
        assertEquals("Projeler", entry.name)
        assertEquals(folder.absolutePath, entry.originalPath)
        assertEquals(volume.absolutePath, entry.originalParent)
        assertEquals(8L, entry.size)
        assertEquals(now, entry.deletedAt)
    }

    @Test
    fun `restore puts a thing back where it was, making its folder again if it went`() = runBlocking {
        val doc = write(File(volume, "Documents/2026"), "rapor.pdf", "pdf")
        bin.trash(listOf(doc), roots)
        File(volume, "Documents").deleteRecursively()

        val result = bin.restore(bin.list(roots))

        assertTrue(result.failed.isEmpty())
        assertEquals(listOf(doc.absolutePath), result.restored.map { it.absolutePath })
        assertEquals("pdf", doc.readText())
        assertTrue(bin.list(roots).isEmpty())
    }

    @Test
    fun `a name taken meanwhile gets a number instead of being replaced`() = runBlocking {
        val doc = write(volume, "rapor.pdf", "old")
        bin.trash(listOf(doc), roots)
        write(volume, "rapor.pdf", "new")

        val result = bin.restore(bin.list(roots))

        assertEquals("new", File(volume, "rapor.pdf").readText())
        assertEquals(File(volume, "rapor (2).pdf").absolutePath, result.restored.single().absolutePath)
        assertEquals("old", File(volume, "rapor (2).pdf").readText())
    }

    @Test
    fun `things older than the keeping time are purged and the rest stay`() = runBlocking {
        val old = write(volume, "old.txt")
        bin.trash(listOf(old), roots)
        now += 31L * 24 * 60 * 60 * 1000
        val recent = write(volume, "recent.txt")
        bin.trash(listOf(recent), roots)

        val purged = bin.purgeOlderThan(roots, days = 30)

        assertEquals(1, purged)
        assertEquals(listOf("recent.txt"), bin.list(roots).map { it.name })
        assertFalse(File(volume, "${RecycleBin.FOLDER_NAME}/files").listFiles().orEmpty().any { it.readText() == "old.txt" })
    }

    @Test
    fun `delete forever and empty leave nothing behind`() = runBlocking {
        bin.trash(listOf(write(volume, "a.txt"), write(volume, "b.txt"), write(card, "c.txt")), roots)
        val a = bin.list(roots).first { it.name == "a.txt" }

        assertTrue(bin.deleteForever(listOf(a)).isEmpty())
        assertFalse(a.payload.exists())
        assertEquals(setOf("b.txt", "c.txt"), bin.list(roots).map { it.name }.toSet())

        assertTrue(bin.empty(roots).isEmpty())
        assertTrue(bin.list(roots).isEmpty())
    }

    @Test
    fun `the bin itself, and a file on no known volume, are refused`() = runBlocking {
        bin.trash(listOf(write(volume, "a.txt")), roots)
        val stranger = Files.createTempFile("elsewhere", ".txt").toFile()
        try {
            val result = bin.trash(listOf(File(volume, RecycleBin.FOLDER_NAME), stranger), roots)
            assertEquals(2, result.failed.size)
            assertTrue(stranger.exists())
            assertEquals(1, bin.list(roots).size)
        } finally {
            stranger.delete()
        }
    }

    @Test
    fun `a file found in the bin with no record is listed rather than lost`() = runBlocking {
        bin.trash(listOf(write(volume, "a.txt")), roots)
        // As if the app had stopped between the move and the index write.
        write(File(volume, "${RecycleBin.FOLDER_NAME}/files"), "stray-id", "stray")

        val entries = bin.list(roots)

        assertEquals(setOf("a.txt", "stray-id"), entries.map { it.name }.toSet())
    }

    @Test
    fun `a broken index is not a crash`() = runBlocking {
        bin.trash(listOf(write(volume, "a.txt")), roots)
        File(volume, "${RecycleBin.FOLDER_NAME}/index.json").writeText("{ not json")

        // The record is gone, but the file is still found and can still be dealt with.
        assertEquals(1, bin.list(roots).size)
    }
}
