package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.MusicSort
import com.serkantkn.zunelauncher.util.formatTotalDuration
import com.serkantkn.zunelauncher.util.formatTrackDuration
import com.serkantkn.zunelauncher.util.groupByLetter
import com.serkantkn.zunelauncher.util.jumpLetterOf
import com.serkantkn.zunelauncher.util.shuffledOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

/**
 * The music hub's arithmetic.
 *
 * [com.serkantkn.zunelauncher.data.model.SongModel] carries an Android `Uri`, which a plain unit
 * test cannot make, so the ordering rules are checked against the same collator the sort uses
 * rather than against built song objects. That is the part worth pinning down: the media store's
 * own SQL sorts by byte value, which puts Turkish letters in the wrong place.
 */
class MusicTest {

    // ── Time ────────────────────────────────────────────────────────────────

    @Test
    fun `a track's length reads as minutes and seconds`() {
        assertEquals("3:42", formatTrackDuration(222_000L))
        assertEquals("0:07", formatTrackDuration(7_400L))
    }

    @Test
    fun `an hour long recording grows an hours field`() {
        assertEquals("1:02:03", formatTrackDuration(3_723_000L))
    }

    @Test
    fun `a track's length is truncated, not rounded up`() {
        // 3:59.9 is still 3:59 on every player anyone has used; rounding it to 4:00 would make
        // the running time disagree with the position as it arrives at the end.
        assertEquals("3:59", formatTrackDuration(239_900L))
    }

    @Test
    fun `a broken duration does not produce a negative time`() {
        assertEquals("0:00", formatTrackDuration(-1_000L))
    }

    @Test
    fun `a record's total is said in minutes, and in hours once there are some`() {
        assertEquals("42 dk", formatTotalDuration(42L * 60_000L, "sa", "dk"))
        assertEquals("1 sa 12 dk", formatTotalDuration(72L * 60_000L, "sa", "dk"))
    }

    @Test
    fun `a part minute counts as a minute`() {
        // A three-track EP of 40 seconds each is two minutes of music, not one.
        assertEquals("2 dk", formatTotalDuration(100_000L, "sa", "dk"))
    }

    // ── Turkish order ───────────────────────────────────────────────────────

    @Test
    fun `turkish letters sort where a turkish reader looks for them`() {
        val collator = Collator.getInstance(Locale("tr", "TR")).apply { strength = Collator.SECONDARY }
        val sorted = listOf("Zeytin", "Çınar", "Işık", "Adam", "Şeker")
            .sortedWith { a, b -> collator.compare(a, b) }
        assertEquals(listOf("Adam", "Çınar", "Işık", "Şeker", "Zeytin"), sorted)
    }

    @Test
    fun `plain byte order gets it wrong, which is why the collator is there`() {
        // The comparison the media store's own ORDER BY would make.
        val byBytes = listOf("Zeytin", "Çınar", "Adam").sorted()
        assertEquals(listOf("Adam", "Zeytin", "Çınar"), byBytes)
    }

    @Test
    fun `case and marks do not separate two spellings of the same name`() {
        val collator = Collator.getInstance(Locale("tr", "TR")).apply { strength = Collator.SECONDARY }
        assertEquals(0, collator.compare("mavi ada", "MAVİ ADA"))
    }

    @Test
    fun `every sort order exists`() {
        assertEquals(5, MusicSort.entries.size)
    }

    // ── The alphabet down the side ──────────────────────────────────────────

    @Test
    fun `a name files under its first letter, in upper case`() {
        assertEquals("M", jumpLetterOf("mavi ada"))
        assertEquals("A", jumpLetterOf("  Ayse Yildiz"))
    }

    @Test
    fun `turkish letters keep their own heading`() {
        assertEquals("Ç", jumpLetterOf("Çınar Altında"))
        assertEquals("Ş", jumpLetterOf("Şeker"))
    }

    @Test
    fun `anything that is not a letter files under hash`() {
        assertEquals("#", jumpLetterOf("1979"))
        assertEquals("#", jumpLetterOf("(intro)"))
        assertEquals("#", jumpLetterOf(""))
    }

    @Test
    fun `grouping keeps the order it was given`() {
        val names = listOf("Adam", "Ada", "Bulut", "Ateş")
        val groups = groupByLetter(names) { it }
        // "Ateş" comes after "Bulut" in the list, so it gets its own run rather than jumping back.
        assertEquals(listOf("A", "B", "A"), groups.map { it.first })
        assertEquals(listOf("Adam", "Ada"), groups.first().second)
    }

    @Test
    fun `grouping an empty list gives no headings`() {
        assertTrue(groupByLetter(emptyList<String>()) { it }.isEmpty())
    }

    // ── Shuffle ─────────────────────────────────────────────────────────────

    @Test
    fun `shuffling keeps the tapped track first`() {
        val order = shuffledOrder(size = 8, startIndex = 3, random = kotlin.random.Random(7))
        assertEquals(3, order.first())
    }

    @Test
    fun `shuffling plays everything exactly once`() {
        val order = shuffledOrder(size = 20, startIndex = 0, random = kotlin.random.Random(1))
        assertEquals(20, order.size)
        assertEquals((0 until 20).toSet(), order.toSet())
    }

    @Test
    fun `shuffling actually moves something`() {
        val order = shuffledOrder(size = 30, startIndex = 0, random = kotlin.random.Random(42))
        assertTrue("a shuffle that changes nothing is not a shuffle", order != (0 until 30).toList())
    }

    @Test
    fun `a start index outside the queue does not throw`() {
        assertEquals(listOf(4), shuffledOrder(size = 5, startIndex = 99, random = kotlin.random.Random(3)).take(1))
        assertTrue(shuffledOrder(size = 0, startIndex = 0, random = kotlin.random.Random(3)).isEmpty())
    }
}
