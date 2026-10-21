package com.serkantkn.zeta

import com.serkantkn.zeta.util.WallpaperAccent
import com.serkantkn.zeta.util.WallpaperAccent.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperAccentTest {

    private fun hsl(argb: Int) = WallpaperAccent.toHsl(argb)

    @Test
    fun `dark leaves give a green that reads as an accent, not near-black`() {
        // The dominant colour of the phone's wallpaper was #182018.
        val accent = WallpaperAccent.pick(
            listOf(Candidate(0xFF182018.toInt(), 900), Candidate(0xFF2F5A2A.toInt(), 300), Candidate(0xFF0A0C0A.toInt(), 600))
        )
        val (h, s, l) = hsl(accent).toList()
        assertTrue("hue $h should stay green", h in 90f..150f)
        assertTrue("saturation $s", s >= 0.49f)
        assertTrue("lightness $l", l in 0.35f..0.51f)
    }

    @Test
    fun `a small bright colour beats a large grey`() {
        val accent = WallpaperAccent.pick(
            listOf(Candidate(0xFF808080.toInt(), 5000), Candidate(0xFFE53935.toInt(), 400))
        )
        val h = hsl(accent)[0]
        assertTrue("hue $h should be red", h < 15f || h > 345f)
    }

    @Test
    fun `a wallpaper with no colour gets steel`() {
        val accent = WallpaperAccent.pick(
            listOf(Candidate(0xFF101010.toInt(), 800), Candidate(0xFFF0F0F0.toInt(), 400), Candidate(0xFF777777.toInt(), 300))
        )
        assertEquals(WallpaperAccent.STEEL, accent)
    }

    @Test
    fun `hsl goes there and back`() {
        listOf(0xFF1BA1E2.toInt(), 0xFF60A917.toInt(), 0xFFD80073.toInt(), 0xFFF0A30A.toInt()).forEach { c ->
            val (h, s, l) = hsl(c).toList()
            val back = WallpaperAccent.fromHsl(h, s, l)
            for (shift in listOf(16, 8, 0)) {
                val a = (c shr shift) and 0xFF
                val b = (back shr shift) and 0xFF
                assertTrue("channel $shift of ${Integer.toHexString(c)}: $a vs $b", kotlin.math.abs(a - b) <= 2)
            }
        }
    }
}
