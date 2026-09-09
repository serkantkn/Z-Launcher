package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.ClipboardEntry
import com.serkantkn.zunelauncher.data.model.KeyboardLanguage
import com.serkantkn.zunelauncher.data.model.OneHandedMode
import com.serkantkn.zunelauncher.data.model.TextShortcut
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.parseJsonStringList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import com.serkantkn.zunelauncher.data.model.toJsonStringArray
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject

private val Context.keyboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "keyboard")

/**
 * Preferences and learned data of the system-wide Z keyboard (house convention: one flow and one
 * setter per key; lists are JSON arrays).
 */
class KeyboardDataStore(private val context: Context) {

    private val SOUND_KEY = booleanPreferencesKey("key_sound")
    private val VIBRATION_KEY = booleanPreferencesKey("key_vibration")
    private val PREVIEW_KEY = booleanPreferencesKey("key_preview")
    private val HEIGHT_KEY = floatPreferencesKey("height_scale")
    private val NUMBER_ROW_KEY = booleanPreferencesKey("number_row")
    private val SUGGESTIONS_KEY = booleanPreferencesKey("suggestions")
    private val AUTO_CORRECT_KEY = booleanPreferencesKey("auto_correct")
    private val ONE_HANDED_KEY = stringPreferencesKey("one_handed")
    private val SPLIT_KEY = booleanPreferencesKey("split_keyboard")
    private val LANGUAGES_KEY = stringPreferencesKey("languages")
    private val ACTIVE_LANGUAGE_KEY = stringPreferencesKey("active_language")
    private val LEARNED_KEY = stringPreferencesKey("learned_words")
    private val CLIPBOARD_KEY = stringPreferencesKey("clipboard_history")
    private val SHORTCUTS_KEY = stringPreferencesKey("text_shortcuts")
    private val RECENT_EMOJI_KEY = stringPreferencesKey("recent_emoji")
    private val BOTTOM_PADDING_KEY = intPreferencesKey("bottom_padding_dp")

    val soundEnabled: Flow<Boolean> = context.keyboardDataStore.data.map { it[SOUND_KEY] ?: false }
    val vibrationEnabled: Flow<Boolean> = context.keyboardDataStore.data.map { it[VIBRATION_KEY] ?: true }
    val keyPreviewEnabled: Flow<Boolean> = context.keyboardDataStore.data.map { it[PREVIEW_KEY] ?: true }
    val numberRowEnabled: Flow<Boolean> = context.keyboardDataStore.data.map { it[NUMBER_ROW_KEY] ?: false }
    val suggestionsEnabled: Flow<Boolean> = context.keyboardDataStore.data.map { it[SUGGESTIONS_KEY] ?: true }
    val autoCorrectEnabled: Flow<Boolean> = context.keyboardDataStore.data.map { it[AUTO_CORRECT_KEY] ?: true }
    val splitEnabled: Flow<Boolean> = context.keyboardDataStore.data.map { it[SPLIT_KEY] ?: false }

    /** Row height multiplier; [HEIGHT_CHOICES] are the values offered in settings. */
    val heightScale: Flow<Float> = context.keyboardDataStore.data.map { it[HEIGHT_KEY] ?: 1.0f }

    /**
     * Extra space under the bottom row, in dp. The keyboard already keeps clear of the navigation
     * bar on its own; this is the manual adjustment for devices whose keyboard buttons ("hide" and
     * "switch keyboard") sit even higher. [BOTTOM_PADDING_CHOICES] are the values offered.
     */
    val bottomPaddingDp: Flow<Int> = context.keyboardDataStore.data.map { it[BOTTOM_PADDING_KEY] ?: 0 }

    val oneHandedMode: Flow<OneHandedMode> = context.keyboardDataStore.data.map { preferences ->
        preferences[ONE_HANDED_KEY]?.let { stored ->
            OneHandedMode.entries.firstOrNull { it.name == stored }
        } ?: OneHandedMode.OFF
    }

    /** Languages the user switched on; empty means "follow the launcher's language". */
    val languages: Flow<List<KeyboardLanguage>> = context.keyboardDataStore.data.map { preferences ->
        parseJsonStringList(preferences[LANGUAGES_KEY], TAG)
            .mapNotNull { tag -> KeyboardLanguage.entries.firstOrNull { it.tag == tag } }
    }

    /** Language the language key last switched to, null while it follows the launcher. */
    val activeLanguage: Flow<KeyboardLanguage?> = context.keyboardDataStore.data.map { preferences ->
        preferences[ACTIVE_LANGUAGE_KEY]?.let { tag ->
            KeyboardLanguage.entries.firstOrNull { it.tag == tag }
        }
    }

    /** Words the keyboard picked up while typing, mapped to how often they were seen. */
    val learnedWords: Flow<Map<String, Int>> = context.keyboardDataStore.data.map { preferences ->
        parseCounts(preferences[LEARNED_KEY])
    }

    /** Newest first; pinned entries survive the [MAX_CLIPBOARD] cap. */
    val clipboardHistory: Flow<List<ClipboardEntry>> = context.keyboardDataStore.data.map { preferences ->
        parseJsonObjectList(preferences[CLIPBOARD_KEY], TAG, ClipboardEntry::fromJson)
    }

    val textShortcuts: Flow<List<TextShortcut>> = context.keyboardDataStore.data.map { preferences ->
        parseJsonObjectList(preferences[SHORTCUTS_KEY], TAG, TextShortcut::fromJson)
    }

    val recentEmoji: Flow<List<String>> = context.keyboardDataStore.data.map { preferences ->
        parseJsonStringList(preferences[RECENT_EMOJI_KEY], TAG)
    }

    suspend fun setSoundEnabled(enabled: Boolean) = edit { it[SOUND_KEY] = enabled }

    suspend fun setVibrationEnabled(enabled: Boolean) = edit { it[VIBRATION_KEY] = enabled }

    suspend fun setKeyPreviewEnabled(enabled: Boolean) = edit { it[PREVIEW_KEY] = enabled }

    suspend fun setNumberRowEnabled(enabled: Boolean) = edit { it[NUMBER_ROW_KEY] = enabled }

    suspend fun setSuggestionsEnabled(enabled: Boolean) = edit { it[SUGGESTIONS_KEY] = enabled }

    suspend fun setAutoCorrectEnabled(enabled: Boolean) = edit { it[AUTO_CORRECT_KEY] = enabled }

    suspend fun setSplitEnabled(enabled: Boolean) = edit { it[SPLIT_KEY] = enabled }

    suspend fun setOneHandedMode(mode: OneHandedMode) = edit { it[ONE_HANDED_KEY] = mode.name }

    suspend fun setBottomPaddingDp(dp: Int) =
        edit { it[BOTTOM_PADDING_KEY] = dp.coerceIn(BOTTOM_PADDING_CHOICES.first(), BOTTOM_PADDING_CHOICES.last()) }

    suspend fun setHeightScale(scale: Float) =
        edit { it[HEIGHT_KEY] = scale.coerceIn(HEIGHT_CHOICES.first(), HEIGHT_CHOICES.last()) }

    suspend fun setLanguages(languages: List<KeyboardLanguage>) = edit { preferences ->
        preferences[LANGUAGES_KEY] = languages.map { it.tag }.toJsonStringArray()
        // A language that is no longer switched on must not stay selected.
        val active = preferences[ACTIVE_LANGUAGE_KEY]
        if (active != null && languages.none { it.tag == active }) preferences.remove(ACTIVE_LANGUAGE_KEY)
    }

    suspend fun setActiveLanguage(language: KeyboardLanguage) =
        edit { it[ACTIVE_LANGUAGE_KEY] = language.tag }

    /** Counts a word the user typed; [TextPredictor] treats it as known from the second time on. */
    suspend fun learnWord(word: String) {
        if (word.length < MIN_LEARNED_LENGTH) return
        edit { preferences ->
            val counts = parseCounts(preferences[LEARNED_KEY]).toMutableMap()
            counts[word] = (counts[word] ?: 0) + 1
            if (counts.size > MAX_LEARNED) {
                val keep = counts.entries.sortedByDescending { it.value }.take(MAX_LEARNED)
                counts.clear()
                keep.forEach { counts[it.key] = it.value }
            }
            preferences[LEARNED_KEY] = writeCounts(counts)
        }
    }

    suspend fun forgetWord(word: String) = edit { preferences ->
        val counts = parseCounts(preferences[LEARNED_KEY]).toMutableMap()
        counts.remove(word)
        preferences[LEARNED_KEY] = writeCounts(counts)
    }

    suspend fun clearLearnedWords() = edit { it.remove(LEARNED_KEY) }

    /** Adds [text] as the newest clip; an identical entry is moved to the front instead. */
    suspend fun rememberClip(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_CLIP_LENGTH) return
        val current = clipboardHistory.first()
        if (current.firstOrNull()?.text == trimmed) return
        val existing = current.firstOrNull { it.text == trimmed }
        val entry = existing ?: ClipboardEntry(
            id = "${System.currentTimeMillis()}-${trimmed.hashCode()}",
            text = trimmed
        )
        val rest = current.filter { it.text != trimmed }
        val pinned = rest.filter { it.pinned }
        val unpinned = rest.filterNot { it.pinned }.take(MAX_CLIPBOARD - 1)
        saveClipboard(listOf(entry) + pinned + unpinned)
    }

    suspend fun setClipPinned(id: String, pinned: Boolean) {
        saveClipboard(clipboardHistory.first().map { if (it.id == id) it.copy(pinned = pinned) else it })
    }

    suspend fun removeClip(id: String) {
        saveClipboard(clipboardHistory.first().filterNot { it.id == id })
    }

    suspend fun clearClipboard() {
        saveClipboard(clipboardHistory.first().filter { it.pinned })
    }

    suspend fun addShortcut(shortcut: TextShortcut) {
        val current = textShortcuts.first().filterNot { it.trigger.equals(shortcut.trigger, ignoreCase = true) }
        saveShortcuts(current + shortcut)
    }

    suspend fun removeShortcut(id: String) {
        saveShortcuts(textShortcuts.first().filterNot { it.id == id })
    }

    /** Moves [emoji] to the front of the recently used list. */
    suspend fun rememberEmoji(emoji: String) = edit { preferences ->
        val current = parseJsonStringList(preferences[RECENT_EMOJI_KEY], TAG)
        preferences[RECENT_EMOJI_KEY] =
            (listOf(emoji) + current.filterNot { it == emoji }).take(MAX_RECENT_EMOJI).toJsonStringArray()
    }

    private suspend fun saveClipboard(entries: List<ClipboardEntry>) = edit {
        it[CLIPBOARD_KEY] = entries.toJsonArrayString { entry -> entry.toJson() }
    }

    private suspend fun saveShortcuts(shortcuts: List<TextShortcut>) = edit {
        it[SHORTCUTS_KEY] = shortcuts.toJsonArrayString { shortcut -> shortcut.toJson() }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.keyboardDataStore.edit(block)
    }

    private fun parseCounts(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val counts = HashMap<String, Int>(obj.length())
            obj.keys().forEach { key -> counts[key] = obj.optInt(key, 1) }
            counts
        } catch (e: JSONException) {
            ZuneLog.w(TAG, "learned words are not a json object, treating as empty", e)
            emptyMap()
        }
    }

    private fun writeCounts(counts: Map<String, Int>): String {
        val obj = JSONObject()
        counts.forEach { (word, count) -> obj.put(word, count) }
        return obj.toString()
    }

    companion object {
        val HEIGHT_CHOICES = listOf(0.85f, 1.0f, 1.15f)
        val BOTTOM_PADDING_CHOICES = listOf(0, 8, 16, 24)
        const val MAX_CLIPBOARD = 25
        private const val TAG = "KeyboardDataStore"
        private const val MAX_LEARNED = 2000
        private const val MIN_LEARNED_LENGTH = 3
        private const val MAX_CLIP_LENGTH = 5000
        private const val MAX_RECENT_EMOJI = 30
    }
}
