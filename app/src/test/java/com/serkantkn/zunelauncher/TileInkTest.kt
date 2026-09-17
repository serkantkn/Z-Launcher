package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.TileInk
import com.serkantkn.zunelauncher.data.model.tileInkIsDark
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What colour a tile writes in. The rule has to hold for the board and for the settings
 * preview alike, which is why it is one function rather than two opinions.
 */
class TileInkTest {

    private val SOLID = 1f
    private val SEE_THROUGH = 0.45f
    private val PALE = 0.8f      // a light accent, e.g. yellow
    private val DEEP = 0.2f      // a dark accent, e.g. navy

    // ── Chosen by hand ──────────────────────────────────────────────────

    @Test
    fun `black stays black wherever it is asked`() {
        assertTrue(tileInkIsDark(TileInk.DARK, isDarkTheme = true, opacity = SOLID, fillLuminance = DEEP))
        assertTrue(tileInkIsDark(TileInk.DARK, isDarkTheme = false, opacity = SEE_THROUGH, fillLuminance = PALE))
    }

    @Test
    fun `white stays white wherever it is asked`() {
        assertFalse(tileInkIsDark(TileInk.LIGHT, isDarkTheme = false, opacity = SEE_THROUGH, fillLuminance = PALE))
        assertFalse(tileInkIsDark(TileInk.LIGHT, isDarkTheme = true, opacity = SOLID, fillLuminance = PALE))
    }

    // ── Left to the launcher ────────────────────────────────────────────

    @Test
    fun `a see-through tile follows the theme`() {
        // This is the case the setting exists for: a pale theme used to force black.
        assertTrue(tileInkIsDark(TileInk.AUTO, isDarkTheme = false, opacity = SEE_THROUGH, fillLuminance = DEEP))
        assertFalse(tileInkIsDark(TileInk.AUTO, isDarkTheme = true, opacity = SEE_THROUGH, fillLuminance = DEEP))
    }

    @Test
    fun `a solid tile follows its own colour, not the theme`() {
        assertTrue(tileInkIsDark(TileInk.AUTO, isDarkTheme = true, opacity = SOLID, fillLuminance = PALE))
        assertFalse(tileInkIsDark(TileInk.AUTO, isDarkTheme = false, opacity = SOLID, fillLuminance = DEEP))
    }

    @Test
    fun `the change of mind happens where the wallpaper starts showing through`() {
        // Just either side of the point, on a light theme with a dark tile colour.
        assertTrue(tileInkIsDark(TileInk.AUTO, isDarkTheme = false, opacity = 0.59f, fillLuminance = DEEP))
        assertFalse(tileInkIsDark(TileInk.AUTO, isDarkTheme = false, opacity = 0.61f, fillLuminance = DEEP))
    }
}
