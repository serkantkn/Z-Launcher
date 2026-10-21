package com.serkantkn.zeta

import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.ui.components.countText
import com.serkantkn.zeta.ui.components.fittedGlyphSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileCountTest {

    @Test
    fun aCountPastNinetyNineIsWrittenShort() {
        assertEquals("7", countText(7))
        assertEquals("99", countText(99))
        assertEquals("99+", countText(140))
    }

    @Test
    fun theGlyphKeepsItsSizeWhileTheCountFits() {
        assertEquals(40.dp, fittedGlyphSize(40.dp, characters = 1, maxWidth = 200.dp))
    }

    @Test
    fun aCountTooWideForTheTileShrinksTheGlyphWithIt() {
        val fitted = fittedGlyphSize(40.dp, characters = 3, maxWidth = 60.dp)
        assertTrue(fitted < 40.dp)
        // Shrunk just enough: asked again with the same room, it stays as it is.
        assertEquals(fitted.value, fittedGlyphSize(fitted, characters = 3, maxWidth = 60.dp).value, 0.01f)
    }
}
