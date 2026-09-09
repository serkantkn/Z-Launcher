package com.serkantkn.zunelauncher

import android.text.InputType
import com.serkantkn.zunelauncher.data.model.KeyAction
import com.serkantkn.zunelauncher.data.model.KeyboardLanguage
import com.serkantkn.zunelauncher.data.model.KeyboardLayout
import com.serkantkn.zunelauncher.data.model.KeyboardPage
import com.serkantkn.zunelauncher.data.model.KeyboardVariant
import com.serkantkn.zunelauncher.ui.keyboard.splitIndex
import com.serkantkn.zunelauncher.util.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutsTest {

    private val boards: List<KeyboardLayout> = KeyboardLanguage.entries.flatMap { language ->
        listOf(
            KeyboardLayouts.letters(language, KeyboardVariant.NORMAL),
            KeyboardLayouts.letters(language, KeyboardVariant.EMAIL),
            KeyboardLayouts.letters(language, KeyboardVariant.URL),
            KeyboardLayouts.symbols(language, KeyboardVariant.NORMAL),
            KeyboardLayouts.moreSymbols(language, KeyboardVariant.NORMAL),
            KeyboardLayouts.numeric(language),
            KeyboardLayouts.phone(language)
        )
    }

    private fun KeyboardLayout.characters() =
        rows.flatMap { it.keys }.filter { it.action == KeyAction.CHARACTER }.map { it.output }

    private fun KeyboardLayout.actions(action: KeyAction) =
        rows.flatMap { it.keys }.count { it.action == action }

    @Test
    fun everyRowOfABoardIsTheSameWidth() {
        boards.forEach { board ->
            val widths = board.rows.map { it.totalWeight }
            widths.forEach { width ->
                assertEquals("${board.page}/${board.language} row widths: $widths", widths[0], width, 0.001f)
            }
        }
    }

    @Test
    fun everyBoardHasExactlyOneSpaceAndOneEnter() {
        boards.forEach { board ->
            if (board.page == KeyboardPage.NUMERIC || board.page == KeyboardPage.PHONE) {
                assertEquals("${board.page} enter", 1, board.actions(KeyAction.ENTER))
            } else {
                assertEquals("${board.page} space", 1, board.actions(KeyAction.SPACE))
                assertEquals("${board.page} enter", 1, board.actions(KeyAction.ENTER))
            }
            assertTrue("${board.page} backspace", board.actions(KeyAction.BACKSPACE) >= 1)
        }
    }

    @Test
    fun onlyLetterBoardsCarryAShiftKey() {
        boards.forEach { board ->
            val expected = if (board.page == KeyboardPage.LETTERS) 1 else 0
            assertEquals("${board.page} shift", expected, board.actions(KeyAction.SHIFT))
        }
    }

    @Test
    fun turkishBoardHasTheTurkishAlphabet() {
        val letters = KeyboardLayouts.letters(KeyboardLanguage.TURKISH, KeyboardVariant.NORMAL)
            .rows.take(3).flatMap { it.keys }
            .filter { it.action == KeyAction.CHARACTER }
            .map { it.output }
        // 29 Turkish letters plus q, w and x, exactly like the Windows Phone Q layout.
        assertEquals(32, letters.size)
        listOf("ğ", "ü", "ş", "ı", "i", "ö", "ç", "q", "w", "x").forEach {
            assertTrue("missing $it", letters.contains(it))
        }
        assertFalse("q-layout must not contain a stray uppercase", letters.any { it != it.lowercase() })
    }

    @Test
    fun englishBoardHasTwentySixLetters() {
        val letters = KeyboardLayouts.letters(KeyboardLanguage.ENGLISH, KeyboardVariant.NORMAL)
            .rows.take(3).flatMap { it.keys }
            .filter { it.action == KeyAction.CHARACTER }
        assertEquals(26, letters.size)
    }

    @Test
    fun emailAndUrlBoardsGetTheirOwnBottomRow() {
        val email = KeyboardLayouts.letters(KeyboardLanguage.TURKISH, KeyboardVariant.EMAIL).characters()
        assertTrue(email.contains("@"))
        assertTrue(email.contains(".com"))

        val url = KeyboardLayouts.letters(KeyboardLanguage.TURKISH, KeyboardVariant.URL).characters()
        assertTrue(url.contains("/"))
        assertTrue(url.contains(".com"))

        val normal = KeyboardLayouts.letters(KeyboardLanguage.TURKISH, KeyboardVariant.NORMAL).characters()
        assertTrue(normal.contains(","))
        assertFalse(normal.contains(".com"))
    }

    @Test
    fun keypadsCarryEveryDigit() {
        listOf(
            KeyboardLayouts.numeric(KeyboardLanguage.ENGLISH),
            KeyboardLayouts.phone(KeyboardLanguage.ENGLISH)
        ).forEach { board ->
            val digits = board.characters()
            (0..9).forEach { assertTrue("${board.page} missing $it", digits.contains(it.toString())) }
        }
    }

    @Test
    fun inputTypePicksTheBoard() {
        assertEquals(KeyboardPage.LETTERS, KeyboardLayouts.pageFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(KeyboardPage.PHONE, KeyboardLayouts.pageFor(InputType.TYPE_CLASS_PHONE))
        assertEquals(KeyboardPage.NUMERIC, KeyboardLayouts.pageFor(InputType.TYPE_CLASS_NUMBER))
        assertEquals(
            KeyboardPage.NUMERIC,
            KeyboardLayouts.pageFor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )
    }

    @Test
    fun inputTypePicksTheBottomRowVariant() {
        assertEquals(
            KeyboardVariant.EMAIL,
            KeyboardLayouts.variantFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        )
        assertEquals(
            KeyboardVariant.URL,
            KeyboardLayouts.variantFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        )
        assertEquals(KeyboardVariant.NORMAL, KeyboardLayouts.variantFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(KeyboardVariant.NORMAL, KeyboardLayouts.variantFor(InputType.TYPE_CLASS_NUMBER))
    }

    @Test
    fun passwordFieldsAreDetected() {
        assertTrue(
            KeyboardLayouts.isPasswordField(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            )
        )
        assertTrue(
            KeyboardLayouts.isPasswordField(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            )
        )
        assertFalse(KeyboardLayouts.isPasswordField(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun shiftUsesTurkishCasingRules() {
        assertEquals("İ", KeyboardLayouts.shifted("i", KeyboardLanguage.TURKISH))
        assertEquals("I", KeyboardLayouts.shifted("ı", KeyboardLanguage.TURKISH))
        assertEquals("I", KeyboardLayouts.shifted("i", KeyboardLanguage.ENGLISH))
    }

    @Test
    fun languageTagsMapToLayouts() {
        assertEquals(KeyboardLanguage.TURKISH, KeyboardLanguage.fromTag("tr-TR"))
        assertEquals(KeyboardLanguage.TURKISH, KeyboardLanguage.fromTag("tr_TR"))
        assertEquals(KeyboardLanguage.ENGLISH, KeyboardLanguage.fromTag("en-US"))
        assertEquals(KeyboardLanguage.ENGLISH, KeyboardLanguage.fromTag(null))
        assertEquals(KeyboardLanguage.ENGLISH, KeyboardLanguage.fromTag("de-DE"))
    }

    @Test
    fun theNumberRowIsAnExtraRowOfTheSameWidth() {
        val plain = KeyboardLayouts.letters(KeyboardLanguage.TURKISH, KeyboardVariant.NORMAL)
        val withDigits = KeyboardLayouts.letters(
            KeyboardLanguage.TURKISH,
            KeyboardVariant.NORMAL,
            numberRow = true
        )
        assertEquals(plain.rows.size + 1, withDigits.rows.size)
        val digits = withDigits.rows.first().keys.map { it.output }
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), digits)
        withDigits.rows.forEach {
            assertEquals(withDigits.rows[0].totalWeight, it.totalWeight, 0.001f)
        }
    }

    @Test
    fun theTopRowHoldsTheDigitsOnlyWhileTheNumberRowIsOff() {
        val plain = KeyboardLayouts.letters(KeyboardLanguage.ENGLISH, KeyboardVariant.NORMAL)
        assertEquals("1", plain.rows[0].keys[0].altKeys.first())
        assertEquals("0", plain.rows[0].keys[9].altKeys.first())

        val withDigits = KeyboardLayouts.letters(
            KeyboardLanguage.ENGLISH,
            KeyboardVariant.NORMAL,
            numberRow = true
        )
        assertTrue(withDigits.rows[1].keys[0].altKeys.none { it == "1" })
    }

    @Test
    fun lettersOfferAccentsWhenHeld() {
        val board = KeyboardLayouts.letters(KeyboardLanguage.TURKISH, KeyboardVariant.NORMAL)
        val keys = board.rows.flatMap { it.keys }.associateBy { it.output }
        assertTrue(keys.getValue("s").altKeys.contains("ş"))
        assertTrue(keys.getValue("g").altKeys.contains("ğ"))
        assertTrue(keys.getValue("i").altKeys.contains("ı"))
    }

    @Test
    fun theLanguageKeyAppearsOnlyWhenAsked() {
        val single = KeyboardLayouts.letters(KeyboardLanguage.TURKISH, KeyboardVariant.NORMAL)
        assertEquals(0, single.actions(KeyAction.LANGUAGE))

        val multi = KeyboardLayouts.letters(
            KeyboardLanguage.TURKISH,
            KeyboardVariant.NORMAL,
            languageKey = true
        )
        assertEquals(1, multi.actions(KeyAction.LANGUAGE))
        multi.rows.forEach { assertEquals(multi.rows[0].totalWeight, it.totalWeight, 0.001f) }
    }

    @Test
    fun theEmojiKeySitsOnPlainTextBoardsOnly() {
        val normal = KeyboardLayouts.letters(KeyboardLanguage.ENGLISH, KeyboardVariant.NORMAL)
        assertEquals(1, normal.actions(KeyAction.PANEL))
        val email = KeyboardLayouts.letters(KeyboardLanguage.ENGLISH, KeyboardVariant.EMAIL)
        assertEquals(0, email.actions(KeyAction.PANEL))
    }

    @Test
    fun fieldsThatWantNoSuggestionsAreDetected() {
        assertTrue(
            KeyboardLayouts.suppressesSuggestions(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            )
        )
        assertTrue(
            KeyboardLayouts.suppressesSuggestions(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            )
        )
        assertTrue(KeyboardLayouts.suppressesSuggestions(InputType.TYPE_CLASS_NUMBER))
        assertFalse(
            KeyboardLayouts.suppressesSuggestions(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            )
        )
    }

    @Test
    fun aSplitBoardBreaksInTheMiddle() {
        val row = KeyboardLayouts.letters(KeyboardLanguage.ENGLISH, KeyboardVariant.NORMAL).rows[0]
        assertEquals(4, splitIndex(row.keys.map { it.weight }))
        assertEquals(-1, splitIndex(listOf(1f, 1f)))
    }
}
