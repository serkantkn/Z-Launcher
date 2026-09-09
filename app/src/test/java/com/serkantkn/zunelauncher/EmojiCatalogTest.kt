package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.ui.keyboard.EmojiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {

    @Test
    fun noCategoryRepeatsAnEmoji() {
        // The grid keys its cells by the emoji, so a repeat used to take the whole panel down.
        EmojiCategory.entries.forEach { category ->
            val emoji = category.emoji
            assertEquals("${category.name} has repeats", emoji.distinct().size, emoji.size)
        }
    }

    @Test
    fun everyCategoryExceptRecentIsFilled() {
        EmojiCategory.entries.forEach { category ->
            if (category == EmojiCategory.RECENT) {
                assertTrue(category.emoji.isEmpty())
            } else {
                assertTrue("${category.name} is empty", category.emoji.size >= 20)
            }
        }
    }

    @Test
    fun noCategoryContainsBlanks() {
        EmojiCategory.entries.forEach { category ->
            assertTrue("${category.name} has a blank cell", category.emoji.none { it.isBlank() })
        }
    }
}
