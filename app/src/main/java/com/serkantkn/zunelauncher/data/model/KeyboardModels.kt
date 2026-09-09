package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

/**
 * Vocabulary of the system-wide Z keyboard (see ZuneKeyboardService).
 *
 * Layouts are pure data so they can be unit tested without a device: a [KeyboardLayout] is a list
 * of [KeyboardRow]s, every row carries weights that add up to the same total, and the service turns
 * a tapped [KeyboardKey] into an InputConnection call.
 */

/** What a key does when it is tapped. Everything except [CHARACTER] is handled by the service. */
enum class KeyAction { CHARACTER, SHIFT, BACKSPACE, SPACE, ENTER, PAGE, PANEL, LANGUAGE }

/** What the keyboard shows above the bottom row: the keys themselves or one of the panels. */
enum class KeyboardPanel { KEYS, EMOJI, CLIPBOARD, VOICE, QUICK_SETTINGS }

/** Shrinks the board towards one side so it can be reached with a single thumb. */
enum class OneHandedMode { OFF, LEFT, RIGHT }

/** The boards the keyboard can show. */
enum class KeyboardPage { LETTERS, SYMBOLS, MORE_SYMBOLS, NUMERIC, PHONE }

/** Shift is three-state, exactly like Windows Phone: off, one-shot, locked. */
enum class ShiftState { OFF, SHIFTED, LOCKED }

/** Letter arrangements the keyboard ships with. */
enum class KeyboardLanguage(val tag: String, val displayLabel: String) {
    TURKISH("tr", "Türkçe"),
    ENGLISH("en", "English");

    companion object {
        /** Matches a locale/language tag ("tr", "tr-TR", "en_US") to a layout; English is the fallback. */
        fun fromTag(tag: String?): KeyboardLanguage {
            val normalized = tag?.lowercase()?.replace('_', '-') ?: return ENGLISH
            return entries.firstOrNull { normalized.startsWith(it.tag) } ?: ENGLISH
        }
    }
}

/** Field-specific tweaks to the bottom row, taken from EditorInfo.inputType. */
enum class KeyboardVariant { NORMAL, EMAIL, URL }

/**
 * One key. [label] is what is drawn (empty for SPACE/ENTER, whose captions are supplied at
 * runtime), [output] is what is committed for a [KeyAction.CHARACTER] key, and [weight] is its
 * width relative to the other keys of the same row.
 */
data class KeyboardKey(
    val label: String,
    val output: String = label,
    val action: KeyAction = KeyAction.CHARACTER,
    val weight: Float = 1f,
    val targetPage: KeyboardPage? = null,
    val targetPanel: KeyboardPanel? = null,
    /** Characters offered in the popup strip when the key is held down. */
    val altKeys: List<String> = emptyList()
)

/**
 * A row of keys. [leadingWeight] / [trailingWeight] are the empty half-key margins Windows Phone
 * uses to centre a short row (the "asdfghjkl" row) under a longer one.
 */
data class KeyboardRow(
    val keys: List<KeyboardKey>,
    val leadingWeight: Float = 0f,
    val trailingWeight: Float = 0f
) {
    val totalWeight: Float get() = leadingWeight + trailingWeight + keys.sumOf { it.weight.toDouble() }.toFloat()
}

data class KeyboardLayout(
    val page: KeyboardPage,
    val language: KeyboardLanguage,
    val rows: List<KeyboardRow>
)

/** One entry of the suggestion strip. [isCorrection] marks the word auto-correct would apply. */
data class Suggestion(
    val word: String,
    val isCorrection: Boolean = false,
    val isTyped: Boolean = false
)

/** A clip the keyboard remembers. Pinned entries are never dropped by the size cap. */
data class ClipboardEntry(
    val id: String,
    val text: String,
    val pinned: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("text", text)
        .put("pinned", pinned)

    companion object {
        fun fromJson(json: JSONObject): ClipboardEntry = ClipboardEntry(
            id = json.optString("id").ifEmpty { json.optString("text").hashCode().toString() },
            text = json.getString("text"),
            pinned = json.optBoolean("pinned", false)
        )
    }
}

/** "adr" -> "Atatürk Caddesi 5": expanded as soon as the trigger word is finished. */
data class TextShortcut(
    val id: String,
    val trigger: String,
    val expansion: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("trigger", trigger)
        .put("expansion", expansion)

    companion object {
        fun fromJson(json: JSONObject): TextShortcut = TextShortcut(
            id = json.optString("id").ifEmpty { json.getString("trigger") },
            trigger = json.getString("trigger"),
            expansion = json.getString("expansion")
        )
    }
}
