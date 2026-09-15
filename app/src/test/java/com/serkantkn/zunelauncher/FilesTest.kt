package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.CloudPage
import com.serkantkn.zunelauncher.data.model.CloudQuota
import com.serkantkn.zunelauncher.data.model.CloudTransfer
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.util.FileCategory
import com.serkantkn.zunelauncher.util.FileSort
import com.serkantkn.zunelauncher.util.categoryOf
import com.serkantkn.zunelauncher.util.formatFileSize
import com.serkantkn.zunelauncher.util.isInsideItself
import com.serkantkn.zunelauncher.util.matchesFileQuery
import com.serkantkn.zunelauncher.util.sortFiles
import com.serkantkn.zunelauncher.util.uniqueFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The Files hub's arithmetic and ordering.
 *
 * Most of what goes wrong in a file manager is quiet: a list sorted by the wrong alphabet, a copy
 * that overwrites what was there, a folder pasted inside itself and filling the disc. None of it
 * announces itself on screen, so it is pinned down here.
 */
class FilesTest {

    // ── Sizes ───────────────────────────────────────────────────────────────

    @Test
    fun `a size is written the way a person would say it`() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("999 B", formatFileSize(999L))
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("1.5 KB", formatFileSize(1536L))
        assertEquals("1.0 MB", formatFileSize(1024L * 1024))
        assertEquals("2.5 GB", formatFileSize((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `past a hundred the decimal stops meaning anything`() {
        assertEquals("500 KB", formatFileSize(512_000L))
    }

    @Test
    fun `a nonsense size is not a crash`() {
        assertEquals("0 B", formatFileSize(-1L))
    }

    // ── Ordering ────────────────────────────────────────────────────────────

    @Test
    fun `folders lead, whichever way the rest is ordered`() {
        val items = listOf(file("apple.txt"), folder("zebra"), file("banana.txt"), folder("apricot"))
        val byName = sortFiles(items, FileSort.NAME, ascending = true)
        assertEquals(listOf("apricot", "zebra", "apple.txt", "banana.txt"), byName.map { it.name })

        val reversed = sortFiles(items, FileSort.NAME, ascending = false)
        // Still folders first, but each group turned around.
        assertEquals(listOf("zebra", "apricot", "banana.txt", "apple.txt"), reversed.map { it.name })
    }

    @Test
    fun `Turkish letters sort where Turkish puts them, not where their codes do`() {
        val items = listOf(file("zeytin"), file("çilek"), file("armut"), file("şeftali"))
        val sorted = sortFiles(items, FileSort.NAME, ascending = true, locale = Locale("tr", "TR"))
        // Comparing the characters themselves would put ç and ş after z.
        assertEquals(listOf("armut", "çilek", "şeftali", "zeytin"), sorted.map { it.name })
    }

    @Test
    fun `by size and by date order the files and leave the folders alone`() {
        val items = listOf(
            file("big.bin", size = 900L),
            folder("folder"),
            file("small.bin", size = 10L)
        )
        assertEquals(
            listOf("folder", "small.bin", "big.bin"),
            sortFiles(items, FileSort.SIZE, ascending = true).map { it.name }
        )
        assertEquals(
            listOf("folder", "big.bin", "small.bin"),
            sortFiles(items, FileSort.SIZE, ascending = false).map { it.name }
        )
    }

    @Test
    fun `an empty folder sorts to an empty list rather than falling over`() {
        assertEquals(emptyList<String>(), sortFiles(emptyList(), FileSort.TYPE, true).map { it.name })
    }

    // ── Kinds ───────────────────────────────────────────────────────────────

    @Test
    fun `a file is known by its extension, whatever case it is written in`() {
        assertEquals(FileCategory.IMAGE, categoryOf("JPG"))
        assertEquals(FileCategory.VIDEO, categoryOf("mkv"))
        assertEquals(FileCategory.AUDIO, categoryOf("flac"))
        assertEquals(FileCategory.DOCUMENT, categoryOf("pdf"))
        assertEquals(FileCategory.ARCHIVE, categoryOf("zip"))
        assertEquals(FileCategory.APP, categoryOf("apk"))
        assertEquals(FileCategory.OTHER, categoryOf("qqq"))
    }

    @Test
    fun `a folder is a folder before it is anything else`() {
        assertEquals(FileCategory.FOLDER, categoryOf("jpg", isDirectory = true))
    }

    // ── Searching ───────────────────────────────────────────────────────────

    @Test
    fun `searching folds Turkish, which ignore-case does not`() {
        assertTrue(matchesFileQuery("Şarkı.mp3", "sarki"))
        assertTrue(matchesFileQuery("İSTANBUL.pdf", "istanbul"))
        assertFalse(matchesFileQuery("Şarkı.mp3", "kitap"))
    }

    @Test
    fun `an empty query matches everything`() {
        assertTrue(matchesFileQuery("anything", ""))
        assertTrue(matchesFileQuery("anything", "   "))
    }

    // ── Names ───────────────────────────────────────────────────────────────

    @Test
    fun `a name that is taken gets a number, before the extension`() {
        assertEquals("rapor.pdf", uniqueFileName("rapor.pdf", emptySet()))
        assertEquals("rapor (2).pdf", uniqueFileName("rapor.pdf", setOf("rapor.pdf")))
        assertEquals("rapor (3).pdf", uniqueFileName("rapor.pdf", setOf("rapor.pdf", "rapor (2).pdf")))
    }

    @Test
    fun `a name with no extension is numbered too`() {
        assertEquals("klasör (2)", uniqueFileName("klasör", setOf("klasör")))
    }

    @Test
    fun `a dotfile keeps its dot rather than being treated as an extension`() {
        assertEquals(".gitignore (2)", uniqueFileName(".gitignore", setOf(".gitignore")))
    }

    // ── Pasting somewhere silly ─────────────────────────────────────────────

    @Test
    fun `a folder cannot be pasted inside itself`() {
        assertTrue(isInsideItself("/storage/a", "/storage/a"))
        assertTrue(isInsideItself("/storage/a", "/storage/a/b"))
        assertTrue(isInsideItself("/storage/a/", "/storage/a/b/c"))
        assertFalse(isInsideItself("/storage/a", "/storage/b"))
        // A name that merely begins the same way is a different folder.
        assertFalse(isInsideItself("/storage/a", "/storage/ab"))
    }

    // ── Drives ──────────────────────────────────────────────────────────────

    @Test
    fun `a page knows whether the service is holding more back`() {
        assertFalse(CloudPage(emptyList()).hasMore)
        assertFalse(CloudPage(emptyList(), "").hasMore)
        assertTrue(CloudPage(emptyList(), "token").hasMore)
    }

    @Test
    fun `a quota with no limit reports no fraction rather than a full bar`() {
        assertEquals(0.5f, CloudQuota(usedBytes = 50L, totalBytes = 100L).fraction)
        assertEquals(null, CloudQuota(usedBytes = 50L, totalBytes = 0L).fraction)
    }

    @Test
    fun `a transfer whose size is unknown counts up instead of filling a bar`() {
        assertEquals(0.25f, CloudTransfer("a", false, 25L, 100L).fraction)
        assertEquals(null, CloudTransfer("a", true, 25L, 0L).fraction)
        // More arrived than was promised: the bar stops at full rather than overflowing.
        assertEquals(1f, CloudTransfer("a", false, 150L, 100L).fraction)
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private fun file(name: String, size: Long = 1L, modified: Long = 1_000L) = FileItemModel(
        file = File("/storage/$name"),
        name = name,
        isDirectory = false,
        size = size,
        lastModified = modified,
        extension = name.substringAfterLast('.', "")
    )

    private fun folder(name: String, modified: Long = 1_000L) = FileItemModel(
        file = File("/storage/$name"),
        name = name,
        isDirectory = true,
        size = 0L,
        lastModified = modified
    )
}
