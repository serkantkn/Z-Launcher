package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.LyricsSource
import com.serkantkn.zunelauncher.util.currentLyricIndex
import com.serkantkn.zunelauncher.util.parseLrc
import com.serkantkn.zunelauncher.util.readId3Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Reading lyrics: the LRC format, and the frames a music file carries them in.
 *
 * Both are string and byte work with no Android in them, which is the point - what the words are
 * and when they are sung should be right before anything is drawn or fetched.
 */
class LyricsTest {

    // -- LRC --------------------------------------------------------------

    @Test
    fun `a timed line carries its time`() {
        val lyrics = parseLrc("[00:12.34]sisli bir sabah")
        assertTrue(lyrics.isSynced)
        assertEquals(1, lyrics.lines.size)
        assertEquals(12_340L, lyrics.lines.first().timeMillis)
        assertEquals("sisli bir sabah", lyrics.lines.first().text)
    }

    @Test
    fun `hundredths and thousandths are both understood`() {
        assertEquals(1_500L, parseLrc("[00:01.5]a").lines.first().timeMillis)
        assertEquals(1_500L, parseLrc("[00:01.50]a").lines.first().timeMillis)
        assertEquals(1_500L, parseLrc("[00:01.500]a").lines.first().timeMillis)
    }

    @Test
    fun `a colon before the hundredths is allowed too`() {
        assertEquals(62_250L, parseLrc("[01:02:25]a").lines.first().timeMillis)
    }

    @Test
    fun `a chorus written once can be pointed at from several places`() {
        val lyrics = parseLrc("[00:30.00][01:30.00]nakarat")
        assertEquals(2, lyrics.lines.size)
        assertEquals(listOf(30_000L, 90_000L), lyrics.lines.map { it.timeMillis })
        assertTrue(lyrics.lines.all { it.text == "nakarat" })
    }

    @Test
    fun `lines come back in the order they are sung`() {
        val lyrics = parseLrc("[00:30.00]iki\n[00:10.00]bir")
        assertEquals(listOf("bir", "iki"), lyrics.lines.map { it.text })
    }

    @Test
    fun `the offset tag moves every line`() {
        val lyrics = parseLrc("[offset:500]\n[00:10.00]bir")
        assertEquals(9_500L, lyrics.lines.first().timeMillis)
    }

    @Test
    fun `an offset can never push a line before the start`() {
        val lyrics = parseLrc("[offset:5000]\n[00:01.00]bir")
        assertEquals(0L, lyrics.lines.first().timeMillis)
    }

    @Test
    fun `the other metadata tags are dropped`() {
        val lyrics = parseLrc("[ar:Mavi Ada]\n[ti:Sisli Sabah]\n[00:01.00]bir")
        assertEquals(listOf("bir"), lyrics.lines.map { it.text })
    }

    @Test
    fun `a plain block of words is still lyrics, just untimed`() {
        val lyrics = parseLrc("bir\niki\nuc")
        assertFalse(lyrics.isSynced)
        assertEquals(3, lyrics.lines.size)
        assertEquals(-1L, lyrics.lines.first().timeMillis)
        assertEquals("bir\niki\nuc", lyrics.plainText)
    }

    @Test
    fun `nothing in means nothing out`() {
        assertTrue(parseLrc("").isEmpty)
        assertTrue(parseLrc("   \n  ").isEmpty)
    }

    @Test
    fun `a bracket that is not a timestamp stays in the words`() {
        val lyrics = parseLrc("[nakarat] bir")
        assertEquals("[nakarat] bir", lyrics.lines.first().text)
    }

    // -- Which line is being sung -----------------------------------------

    @Test
    fun `before the first line nothing is highlighted`() {
        val lyrics = parseLrc("[00:10.00]bir\n[00:20.00]iki")
        assertEquals(-1, currentLyricIndex(lyrics.lines, 5_000L))
    }

    @Test
    fun `the line that is up is the last one whose time has passed`() {
        val lyrics = parseLrc("[00:10.00]bir\n[00:20.00]iki\n[00:30.00]uc")
        assertEquals(0, currentLyricIndex(lyrics.lines, 15_000L))
        assertEquals(1, currentLyricIndex(lyrics.lines, 20_000L))
        assertEquals(2, currentLyricIndex(lyrics.lines, 99_000L))
    }

    @Test
    fun `an untimed block never highlights anything`() {
        val lyrics = parseLrc("bir\niki")
        assertEquals(-1, currentLyricIndex(lyrics.lines, 60_000L))
    }

    // -- ID3 --------------------------------------------------------------

    @Test
    fun `a file with no tag at all gives nothing`() {
        assertNull(readId3Lyrics(ByteArray(0)))
        assertNull(readId3Lyrics("not an mp3".toByteArray()))
    }

    @Test
    fun `a plain lyrics frame is read`() {
        val body = ByteArrayOutputStream().apply {
            write(0)                       // ISO-8859-1
            write("tur".toByteArray())     // language
            write(0)                       // empty description, terminated
            write("bir\niki".toByteArray())
        }.toByteArray()

        val lyrics = readId3Lyrics(id3TagOf("USLT", body))
        assertEquals("bir\niki", lyrics?.plainText)
        assertEquals(LyricsSource.EMBEDDED_TAG, lyrics?.source)
        assertFalse(lyrics!!.isSynced)
    }

    @Test
    fun `a description before the words is skipped`() {
        val body = ByteArrayOutputStream().apply {
            write(0)
            write("eng".toByteArray())
            write("Lyrics".toByteArray())
            write(0)
            write("bir".toByteArray())
        }.toByteArray()

        assertEquals("bir", readId3Lyrics(id3TagOf("USLT", body))?.plainText)
    }

    @Test
    fun `a timed lyrics frame comes back with its times`() {
        val body = ByteArrayOutputStream().apply {
            write(0)                       // ISO-8859-1
            write("tur".toByteArray())     // language
            write(2)                       // times are milliseconds
            write(1)                       // content: lyrics
            write(0)                       // empty description
            write("bir".toByteArray()); write(0); writeInt(this, 10_000)
            write("iki".toByteArray()); write(0); writeInt(this, 20_000)
        }.toByteArray()

        val lyrics = readId3Lyrics(id3TagOf("SYLT", body))
        assertTrue(lyrics!!.isSynced)
        assertEquals(listOf(10_000L, 20_000L), lyrics.lines.map { it.timeMillis })
        assertEquals(listOf("bir", "iki"), lyrics.lines.map { it.text })
    }

    @Test
    fun `times counted in mpeg frames are refused rather than guessed at`() {
        val body = ByteArrayOutputStream().apply {
            write(0)
            write("tur".toByteArray())
            write(1)                       // 1 = MPEG frames, which is not a time
            write(1)
            write(0)
            write("bir".toByteArray()); write(0); writeInt(this, 100)
        }.toByteArray()

        assertNull(readId3Lyrics(id3TagOf("SYLT", body)))
    }

    @Test
    fun `a frame claiming to be longer than the tag does not run off the end`() {
        val tag = id3TagOf("USLT", byteArrayOf(0, 't'.code.toByte(), 'u'.code.toByte(), 'r'.code.toByte(), 0, 'a'.code.toByte()))
        // Rewrite the frame size to something absurd.
        tag[14] = 0x7F
        tag[15] = 0x7F
        assertNull(readId3Lyrics(tag))
    }

    /** Wraps one frame in the smallest valid ID3v2.3 tag. */
    private fun id3TagOf(frame: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val frameSize = body.size
        val tagSize = 10 + frameSize
        out.write("ID3".toByteArray())
        out.write(3); out.write(0)      // version 2.3.0
        out.write(0)                    // no flags
        // Tag size, seven bits per byte.
        out.write((tagSize shr 21) and 0x7F)
        out.write((tagSize shr 14) and 0x7F)
        out.write((tagSize shr 7) and 0x7F)
        out.write(tagSize and 0x7F)
        out.write(frame.toByteArray())
        writeInt(out, frameSize)
        out.write(0); out.write(0)      // frame flags
        out.write(body)
        return out.toByteArray()
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
