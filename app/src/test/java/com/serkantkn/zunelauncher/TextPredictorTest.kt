package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.KeyboardLanguage
import com.serkantkn.zunelauncher.util.TextPredictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPredictorTest {

    private val turkish = listOf(
        "bir", "bu", "ve", "için", "merhaba", "merak", "teşekkür", "kitap", "gelmek", "güzel", "yapmak"
    )
    private val english = listOf(
        "the", "and", "hello", "help", "world", "keyboard", "thanks", "please", "morning"
    )

    private val predictor = TextPredictor { language ->
        when (language) {
            KeyboardLanguage.TURKISH -> turkish
            KeyboardLanguage.ENGLISH -> english
        }
    }

    @Test
    fun theTypedWordIsAlwaysTheFirstEntry() {
        val suggestions = predictor.suggest("mer", KeyboardLanguage.TURKISH)
        assertEquals("mer", suggestions.first().word)
        assertTrue(suggestions.first().isTyped)
    }

    @Test
    fun prefixesAreCompleted() {
        val words = predictor.suggest("mer", KeyboardLanguage.TURKISH).map { it.word }
        assertTrue(words.contains("merhaba"))
        assertTrue(words.contains("merak"))
    }

    @Test
    fun aNeighbouringKeyTypoIsCorrected() {
        // "j" sits next to "h" on the layout, so "merjaba" is one cheap edit from "merhaba".
        assertEquals("merhaba", predictor.correction("merjaba", KeyboardLanguage.TURKISH))
    }

    @Test
    fun aWordInTheListIsNeverCorrected() {
        assertNull(predictor.correction("merhaba", KeyboardLanguage.TURKISH))
        assertNull(predictor.correction("hello", KeyboardLanguage.ENGLISH))
    }

    @Test
    fun shortWordsAreLeftAlone() {
        assertNull(predictor.correction("vd", KeyboardLanguage.TURKISH))
    }

    @Test
    fun correctionKeepsTheCapitalisation() {
        assertEquals("Merhaba", predictor.correction("Merjaba", KeyboardLanguage.TURKISH))
        assertEquals("HELLO", predictor.correction("HELLP", KeyboardLanguage.ENGLISH))
    }

    @Test
    fun learnedWordsAreSuggestedAndBecomeKnown() {
        val learned = mapOf("serkan" to 3)
        val words = predictor.suggest("serk", KeyboardLanguage.TURKISH, learned).map { it.word }
        assertTrue(words.contains("serkan"))
        assertTrue(predictor.isKnown("serkan", KeyboardLanguage.TURKISH, learned))
        assertFalse(predictor.isKnown("serkan", KeyboardLanguage.TURKISH, mapOf("serkan" to 1)))
    }

    @Test
    fun turkishSuffixesCountAsKnownWords() {
        assertTrue(predictor.isKnown("kitabımda", KeyboardLanguage.TURKISH))
        assertTrue(predictor.isKnown("güzeldi", KeyboardLanguage.TURKISH))
        assertFalse(predictor.isKnown("zzzzqq", KeyboardLanguage.TURKISH))
    }

    @Test
    fun anInflectedWordIsNotMangled() {
        assertNull(predictor.correction("kitabımda", KeyboardLanguage.TURKISH))
    }

    @Test
    fun atMostOneEntryIsMarkedAsTheCorrection() {
        val suggestions = predictor.suggest("helo", KeyboardLanguage.ENGLISH)
        assertEquals(1, suggestions.count { it.isCorrection })
        assertEquals("hello", suggestions.first { it.isCorrection }.word)
    }

    @Test
    fun nonLetterInputIsIgnored() {
        assertTrue(predictor.suggest("12", KeyboardLanguage.ENGLISH).isEmpty())
        assertTrue(predictor.suggest("", KeyboardLanguage.ENGLISH).isEmpty())
    }
}
