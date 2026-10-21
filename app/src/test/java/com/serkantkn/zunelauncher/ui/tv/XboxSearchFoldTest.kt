package com.serkantkn.zunelauncher.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search is typed on a TV's keyboard, often without the Turkish letters: it must find them anyway. */
class XboxSearchFoldTest {
    @Test
    fun turkishLettersFoldToPlainOnes() {
        assertEquals("parca 1", XboxSearch.fold("Parça 1"))
        assertEquals("isik", XboxSearch.fold("IŞIK"))
        assertEquals("isik", XboxSearch.fold("ışık"))
        assertEquals("gunes ogle", XboxSearch.fold("Güneş Öğle"))
    }

    @Test
    fun otherMarksAreDroppedAndSpaceTrimmed() {
        assertEquals("cafe", XboxSearch.fold("  Café "))
        assertTrue(XboxSearch.fold("Parça 2").contains(XboxSearch.fold("parca")))
    }
}
