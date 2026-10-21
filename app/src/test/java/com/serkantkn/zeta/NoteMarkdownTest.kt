package com.serkantkn.zeta

import com.serkantkn.zeta.data.model.stripMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMarkdownTest {

    @Test
    fun `the marks the rich editor writes come off for plain text`() {
        assertEquals("peynir", "**peynir**".stripMarkdown())
        assertEquals("eğik", "*eğik*".stripMarkdown())
        assertEquals("eğik", "_eğik_".stripMarkdown())
        assertEquals("çizik", "~~çizik~~".stripMarkdown())
        assertEquals("altı çizili", "<u>altı çizili</u>".stripMarkdown())
        assertEquals("Başlık\nalt", "# Başlık\n## alt".stripMarkdown())
        assertEquals("süt\nyumurta", "- süt\n* yumurta".stripMarkdown())
    }

    @Test
    fun `words with stars or underscores inside them are left alone`() {
        assertEquals("dosya_adi_v2.txt", "dosya_adi_v2.txt".stripMarkdown())
        assertEquals("ekmek peynir", "ekmek peynir".stripMarkdown())
    }
}
