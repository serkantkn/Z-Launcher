package com.serkantkn.zeta

import com.serkantkn.zeta.data.repository.escapeLike
import com.serkantkn.zeta.data.repository.searchVariants
import com.serkantkn.zeta.ui.screens.messaging.markMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MessageSearchTest {

    private val turkish = Locale.forLanguageTag("tr-TR")

    @Test
    fun turkishLettersAreAskedForInEveryCase() {
        val variants = searchVariants("şimdi", turkish)
        assertTrue("Şimdi" in variants)
        assertTrue("ŞİMDİ" in variants)
        assertTrue("şimdi" in variants)
    }

    @Test
    fun theDottedAndDotlessIAreTurkishOnes() {
        assertTrue("istanbul" in searchVariants("İstanbul", turkish))
        assertTrue("ılık" in searchVariants("ILIK", turkish))
    }

    @Test
    fun aQueryIsNotAskedForTwice() {
        assertEquals(listOf("123"), searchVariants("123", turkish))
        assertEquals(searchVariants(" akşam ", turkish), searchVariants("akşam", turkish))
    }

    @Test
    fun wildcardsStayLiteral() {
        assertEquals("100\\% \\_ \\\\", escapeLike("100% _ \\"))
    }

    @Test
    fun everyMatchIsMarkedWhateverItsCase() {
        val marked = markMatches("Akşam yemeği, akşam saat yedi", "AKŞAM")
        assertEquals(listOf(0 to 5, 14 to 19), marked.spanStyles.map { it.start to it.end })
        assertTrue(markMatches("bir şey", "").spanStyles.isEmpty())
    }
}
