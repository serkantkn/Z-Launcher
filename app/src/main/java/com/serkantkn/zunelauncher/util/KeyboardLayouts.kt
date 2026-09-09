package com.serkantkn.zunelauncher.util

import android.text.InputType
import com.serkantkn.zunelauncher.data.model.KeyAction
import com.serkantkn.zunelauncher.data.model.KeyboardKey
import com.serkantkn.zunelauncher.data.model.KeyboardLanguage
import com.serkantkn.zunelauncher.data.model.KeyboardLayout
import com.serkantkn.zunelauncher.data.model.KeyboardPage
import com.serkantkn.zunelauncher.data.model.KeyboardPanel
import com.serkantkn.zunelauncher.data.model.KeyboardRow
import com.serkantkn.zunelauncher.data.model.KeyboardVariant
import java.util.Locale

/**
 * Every board the Z keyboard can draw, as plain data.
 *
 * All rows of a board add up to the same total weight, so a key is exactly one "unit" wide no
 * matter which row it sits in; short rows are centred with half-unit margins the way the Windows
 * Phone keyboard does. Nothing here touches Android UI, which keeps it unit testable.
 */
object KeyboardLayouts {

    /** Caption of the key that switches to the symbol board, as Windows Phone labels it. */
    const val SYMBOLS_LABEL = "&123"
    const val LETTERS_LABEL = "ABC"
    private const val MORE_LABEL = "→"
    private const val BACK_LABEL = "←"

    private const val FN_WEIGHT = 1.5f

    private val DIGIT_ROW = "1234567890"

    fun letters(
        language: KeyboardLanguage,
        variant: KeyboardVariant,
        numberRow: Boolean = false,
        languageKey: Boolean = false
    ): KeyboardLayout {
        val rows = when (language) {
            KeyboardLanguage.TURKISH -> listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç")
            KeyboardLanguage.ENGLISH -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        val total = rows[0].length.toFloat()
        val middleMargin = (total - rows[1].length) / 2f
        val digitMargin = (total - DIGIT_ROW.length) / 2f

        // Without a number row the top letters carry the digits as their hold-to-type alternates,
        // exactly like the Windows Phone keyboard.
        val topAlternates: (Int) -> List<String> = { index ->
            if (numberRow || index >= DIGIT_ROW.length) emptyList() else listOf(DIGIT_ROW[index].toString())
        }

        val board = ArrayList<KeyboardRow>(5)
        if (numberRow) {
            board += KeyboardRow(
                keys = DIGIT_ROW.map { KeyboardKey(it.toString()) },
                leadingWeight = digitMargin,
                trailingWeight = digitMargin
            )
        }
        board += KeyboardRow(
            rows[0].mapIndexed { index, char ->
                letterKey(char, extraAlternates = topAlternates(index))
            }
        )
        board += KeyboardRow(
            keys = rows[1].map { letterKey(it) },
            leadingWeight = middleMargin,
            trailingWeight = middleMargin
        )
        board += KeyboardRow(
            listOf(shiftKey()) + rows[2].map { letterKey(it) } + backspaceKey()
        )
        board += bottomRow(total, variant, SYMBOLS_LABEL, KeyboardPage.SYMBOLS, languageKey)

        return KeyboardLayout(KeyboardPage.LETTERS, language, board)
    }

    fun symbols(
        language: KeyboardLanguage,
        variant: KeyboardVariant,
        languageKey: Boolean = false
    ): KeyboardLayout = symbolBoard(
        page = KeyboardPage.SYMBOLS,
        language = language,
        variant = variant,
        languageKey = languageKey,
        first = DIGIT_ROW,
        second = "-/:;()₺&@\"",
        third = ".,?!'+=",
        switchLabel = MORE_LABEL,
        switchTarget = KeyboardPage.MORE_SYMBOLS
    )

    fun moreSymbols(
        language: KeyboardLanguage,
        variant: KeyboardVariant,
        languageKey: Boolean = false
    ): KeyboardLayout = symbolBoard(
        page = KeyboardPage.MORE_SYMBOLS,
        language = language,
        variant = variant,
        languageKey = languageKey,
        first = "~`|•√π÷×¶∆",
        second = "£¢€¥^°*{}\\",
        third = "©®™%[]<",
        switchLabel = BACK_LABEL,
        switchTarget = KeyboardPage.SYMBOLS
    )

    /** Numeric keypad for number and date fields. */
    fun numeric(language: KeyboardLanguage): KeyboardLayout = keypad(
        page = KeyboardPage.NUMERIC,
        language = language,
        rightColumn = listOf("-", ",")
    )

    /** Dialer-style keypad for phone-number fields. */
    fun phone(language: KeyboardLanguage): KeyboardLayout = keypad(
        page = KeyboardPage.PHONE,
        language = language,
        rightColumn = listOf("*", "#")
    )

    fun forPage(
        page: KeyboardPage,
        language: KeyboardLanguage,
        variant: KeyboardVariant,
        numberRow: Boolean = false,
        languageKey: Boolean = false
    ): KeyboardLayout = when (page) {
        KeyboardPage.LETTERS -> letters(language, variant, numberRow, languageKey)
        KeyboardPage.SYMBOLS -> symbols(language, variant, languageKey)
        KeyboardPage.MORE_SYMBOLS -> moreSymbols(language, variant, languageKey)
        KeyboardPage.NUMERIC -> numeric(language)
        KeyboardPage.PHONE -> phone(language)
    }

    /** The board an editor should open on, from its EditorInfo.inputType. */
    fun pageFor(inputType: Int): KeyboardPage = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_PHONE -> KeyboardPage.PHONE
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> KeyboardPage.NUMERIC
        else -> KeyboardPage.LETTERS
    }

    /** Bottom-row tweaks (@ and .com keys) for e-mail and address fields. */
    fun variantFor(inputType: Int): KeyboardVariant {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return KeyboardVariant.NORMAL
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> KeyboardVariant.EMAIL
            InputType.TYPE_TEXT_VARIATION_URI -> KeyboardVariant.URL
            else -> KeyboardVariant.NORMAL
        }
    }

    /** True for password-ish fields, where nothing may be learned, suggested or auto-corrected. */
    fun isPasswordField(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /**
     * True when the editor asks for no suggestions at all (password fields, "no suggestions" flag
     * or a field that filters a list itself).
     */
    fun suppressesSuggestions(inputType: Int): Boolean {
        if (isPasswordField(inputType)) return true
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return true
        val flags = inputType and InputType.TYPE_MASK_FLAGS
        if (flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return true
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER -> true
            else -> false
        }
    }

    /** Locale-aware upper case, so Turkish "i" becomes "İ" and "ı" becomes "I". */
    fun shifted(text: String, language: KeyboardLanguage): String =
        text.uppercase(Locale.forLanguageTag(language.tag))

    /** Locale-aware lower case; the counterpart of [shifted]. */
    fun unshifted(text: String, language: KeyboardLanguage): String =
        text.lowercase(Locale.forLanguageTag(language.tag))

    private fun letterKey(char: Char, extraAlternates: List<String> = emptyList()): KeyboardKey {
        val base = char.toString()
        return KeyboardKey(base, altKeys = extraAlternates + (ACCENTS[base] ?: emptyList()))
    }

    private fun symbolBoard(
        page: KeyboardPage,
        language: KeyboardLanguage,
        variant: KeyboardVariant,
        languageKey: Boolean,
        first: String,
        second: String,
        third: String,
        switchLabel: String,
        switchTarget: KeyboardPage
    ): KeyboardLayout {
        val total = first.length.toFloat()
        fun symbolKey(char: Char): KeyboardKey {
            val base = char.toString()
            return KeyboardKey(base, altKeys = SYMBOL_ALTERNATES[base] ?: emptyList())
        }
        return KeyboardLayout(
            page = page,
            language = language,
            rows = listOf(
                KeyboardRow(first.map { symbolKey(it) }),
                KeyboardRow(second.map { symbolKey(it) }),
                KeyboardRow(
                    listOf(
                        KeyboardKey(switchLabel, action = KeyAction.PAGE, weight = FN_WEIGHT, targetPage = switchTarget)
                    ) + third.map { symbolKey(it) } + backspaceKey()
                ),
                bottomRow(total, variant, LETTERS_LABEL, KeyboardPage.LETTERS, languageKey)
            )
        )
    }

    private fun keypad(
        page: KeyboardPage,
        language: KeyboardLanguage,
        rightColumn: List<String>
    ): KeyboardLayout = KeyboardLayout(
        page = page,
        language = language,
        rows = listOf(
            KeyboardRow(digits("123") + backspaceKey(weight = 1f)),
            KeyboardRow(digits("456") + KeyboardKey(rightColumn[0])),
            KeyboardRow(digits("789") + KeyboardKey(rightColumn[1])),
            KeyboardRow(
                listOf(KeyboardKey("+"), KeyboardKey("0"), KeyboardKey(".")) +
                    KeyboardKey("", action = KeyAction.ENTER)
            )
        )
    )

    private fun digits(text: String) = text.map { KeyboardKey(it.toString()) }

    private fun shiftKey() = KeyboardKey("", action = KeyAction.SHIFT, weight = FN_WEIGHT)

    private fun backspaceKey(weight: Float = FN_WEIGHT) =
        KeyboardKey("", action = KeyAction.BACKSPACE, weight = weight)

    /**
     * "&123 · emoji · , · space · . · enter" — proportional so it lines up with boards of any
     * width. E-mail and address fields trade the emoji key for ".com", and a language key appears
     * as soon as more than one keyboard language is switched on.
     */
    private fun bottomRow(
        total: Float,
        variant: KeyboardVariant,
        switchLabel: String,
        switchTarget: KeyboardPage,
        languageKey: Boolean
    ): KeyboardRow {
        val fn = total * 0.15f
        val enter = total * 0.20f
        val small = total * 0.10f
        val dotCom = total * 0.15f

        val keys = ArrayList<KeyboardKey>(7)
        keys += KeyboardKey(switchLabel, action = KeyAction.PAGE, weight = fn, targetPage = switchTarget)
        if (variant == KeyboardVariant.NORMAL) {
            keys += KeyboardKey("☺", action = KeyAction.PANEL, weight = small, targetPanel = KeyboardPanel.EMOJI)
        }
        if (languageKey) {
            keys += KeyboardKey("", action = KeyAction.LANGUAGE, weight = small)
        }
        keys += when (variant) {
            KeyboardVariant.EMAIL -> KeyboardKey("@", weight = small)
            KeyboardVariant.URL -> KeyboardKey("/", weight = small)
            KeyboardVariant.NORMAL -> KeyboardKey(",", weight = small, altKeys = ACCENTS[","].orEmpty())
        }
        val fixed = keys.sumOf { it.weight.toDouble() }.toFloat() +
            small + enter + if (variant == KeyboardVariant.NORMAL) 0f else dotCom
        keys += KeyboardKey("", action = KeyAction.SPACE, weight = total - fixed)
        if (variant != KeyboardVariant.NORMAL) keys += KeyboardKey(".com", weight = dotCom)
        keys += KeyboardKey(".", weight = small, altKeys = ACCENTS["."].orEmpty())
        keys += KeyboardKey("", action = KeyAction.ENTER, weight = enter)
        return KeyboardRow(keys)
    }

    /** Hold-to-type alternates: accents first, then the characters of the other alphabet. */
    private val ACCENTS: Map<String, List<String>> = mapOf(
        "a" to listOf("â", "ä", "à", "á", "ã", "å"),
        "c" to listOf("ç", "ć"),
        "e" to listOf("ê", "ë", "è", "é"),
        "g" to listOf("ğ"),
        "i" to listOf("î", "ï", "ì", "í", "ı"),
        "ı" to listOf("i", "î"),
        "n" to listOf("ñ"),
        "o" to listOf("ö", "ô", "ò", "ó", "õ"),
        "s" to listOf("ş", "ß"),
        "u" to listOf("ü", "û", "ù", "ú"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž"),
        "ç" to listOf("c"),
        "ğ" to listOf("g"),
        "ö" to listOf("o", "ô", "ò", "ó"),
        "ş" to listOf("s"),
        "ü" to listOf("u", "û", "ù", "ú"),
        "." to listOf(",", "?", "!", ":", ";", "…"),
        "," to listOf(".", "?", "!", "'", "\"")
    )

    private val SYMBOL_ALTERNATES: Map<String, List<String>> = mapOf(
        "-" to listOf("–", "—", "_"),
        "/" to listOf("\\"),
        "(" to listOf("[", "{", "<"),
        ")" to listOf("]", "}", ">"),
        "₺" to listOf("$", "€", "£", "¥", "₽"),
        "&" to listOf("§"),
        "\"" to listOf("“", "”", "«", "»"),
        "'" to listOf("‘", "’"),
        "!" to listOf("¡"),
        "?" to listOf("¿"),
        "+" to listOf("±"),
        "%" to listOf("‰"),
        "*" to listOf("†")
    )
}
