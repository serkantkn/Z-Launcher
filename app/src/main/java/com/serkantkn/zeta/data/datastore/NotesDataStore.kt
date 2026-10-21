package com.serkantkn.zeta.data.datastore

import com.serkantkn.zeta.data.model.optLongOrNull
import com.serkantkn.zeta.data.model.optStringOrNull
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zeta.data.model.ChecklistItem
import com.serkantkn.zeta.data.model.Note
import com.serkantkn.zeta.data.model.NoteFont
import com.serkantkn.zeta.util.SecretCipher
import com.serkantkn.zeta.util.ZuneLog
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.notesDataStore: DataStore<Preferences> by preferencesDataStore(name = "notes")

/**
 * Persists Notes Hub content as a single JSON array, following the CalendarDataStore /
 * AlarmDataStore convention (org.json, read-only Flow + suspend writer).
 *
 * The same JSON format doubles as the backup file format ([exportJson] / [importJson]).
 */
class NotesDataStore(private val context: Context) {

    private val NOTES_KEY = stringPreferencesKey("notes_json")

    /**
     * The key the locked notes are written under. Its own, not the account vault's: taking one
     * away — reinstalling, clearing the app — should not take the other with it.
     */
    private val cipher = SecretCipher("zune_notes_key")

    /**
     * Sealed notes this phone cannot open, kept by id so they are written back untouched.
     *
     * A note whose seal will not open is not an empty note. Without this, reading one and saving
     * the list — which happens on any edit to any other note — would replace the ciphertext with
     * a blank, and that is somebody's note gone for good.
     */
    private val unreadable = java.util.concurrent.ConcurrentHashMap<String, String>()

    val notesFlow: Flow<List<Note>> = context.notesDataStore.data.map { preferences ->
        parseNotes(preferences[NOTES_KEY])
    }

    /**
     * Reads the list, lets [transform] rewrite it and writes the result, all inside one DataStore
     * edit. Every write goes through here: a read followed by a separate write lets two callers —
     * the editor saving and the share sheet adding, say — each write their own copy of the list,
     * and whichever lands second silently drops the other's note.
     *
     * A store that is not a JSON array at all is not replaced: its text is set aside under its
     * own key, so that what was there can still be recovered, before the new list is written.
     */
    suspend fun update(transform: (List<Note>) -> List<Note>) {
        context.notesDataStore.edit { preferences ->
            val stored = preferences[NOTES_KEY]
            val split = splitNotesJson(stored)
            if (split.corrupt && !stored.isNullOrBlank() && preferences[CORRUPT_KEY] == null) {
                ZuneLog.w(TAG, "notes store unreadable; keeping its text aside before writing")
                preferences[CORRUPT_KEY] = stored
            }
            val (current, broken) = readStore(stored)
            preferences[NOTES_KEY] = serializeNotes(transform(current), broken)
        }
    }

    suspend fun saveNotes(notes: List<Note>) = update { notes }

    /**
     * Locks any note that was marked private before notes were encrypted.
     *
     * Reading is enough to unseal, but nothing is written back until something changes, so a
     * note locked by an older build would sit there in plain text. Called once on start; it
     * costs one read and, only if it finds something, one write.
     */
    suspend fun sealLegacyNotes() {
        try {
            val stored = context.notesDataStore.data.first()[NOTES_KEY] ?: return
            if (!hasUnsealedLockedNote(stored)) return
            // Reading and writing the same list back is what seals it.
            update { it }
            ZuneLog.d(TAG, "locked notes written back sealed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ZuneLog.w(TAG, "locked notes could not be sealed", e)
        }
    }

    /** True when the stored array holds a locked note whose body is still readable as it sits. */
    private fun hasUnsealedLockedNote(jsonString: String): Boolean = try {
        val array = JSONArray(jsonString)
        (0 until array.length()).any { index ->
            val obj = array.getJSONObject(index)
            obj.optBoolean("isLocked", false) && !obj.has(SEALED_KEY)
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Full backup as a JSON string (versioned envelope).
     *
     * Locked notes are written out readable rather than sealed, because the seal is tied to this
     * phone's keystore: a backup file full of blobs no other phone could open is not a backup.
     * The file goes where the person asking for it says it goes, so it is worth saying out loud
     * that it holds their locked notes in plain text.
     */
    suspend fun exportJson(): String {
        val notes = notesFlow.first()
        val array = JSONArray()
        for (note in notes) array.put(note.plainJson())
        val envelope = JSONObject()
        envelope.put("format", BACKUP_FORMAT)
        envelope.put("version", BACKUP_VERSION)
        envelope.put("exportedAt", System.currentTimeMillis())
        envelope.put("notes", array)
        return envelope.toString(2)
    }

    /**
     * Restores notes from a backup produced by [exportJson]. Existing notes with the same id
     * are replaced, others are kept, so a restore merges rather than wipes.
     * Returns the number of imported notes, or -1 when the payload is not a notes backup.
     */
    suspend fun importJson(json: String): Int {
        val imported: List<Note> = try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                parseNotes(trimmed)
            } else {
                val envelope = JSONObject(trimmed)
                if (envelope.optString("format") != BACKUP_FORMAT) return -1
                parseNotes(envelope.optJSONArray("notes")?.toString())
            }
        } catch (e: Exception) {
            return -1
        }
        if (imported.isEmpty()) return 0
        val importedIds = imported.map { it.id }.toSet()
        update { current -> current.filter { it.id !in importedIds } + imported }
        return imported.size
    }

    // ── JSON mapping ──────────────────────────────────────────────────────

    /**
     * The notes, followed by the entries that could not be read as notes, as they were written.
     *
     * One bad entry used to empty the whole list, and the next save — any save — wrote that
     * empty list back over everything. The entry that will not parse is skipped on read and
     * written back untouched, so a fixed build can still get at it.
     */
    private fun serializeNotes(notes: List<Note>, broken: List<String> = emptyList()): String {
        val array = JSONArray()
        for (note in notes) array.put(note.toJson())
        for (raw in broken) runCatching { array.put(JSONObject(raw)) }
        return array.toString()
    }

    private fun parseNotes(jsonString: String?): List<Note> = readStore(jsonString).first

    /** The notes that could be read, and the raw text of every entry that could not. */
    private fun readStore(jsonString: String?): Pair<List<Note>, List<String>> {
        val split = splitNotesJson(jsonString)
        val notes = mutableListOf<Note>()
        val broken = split.broken.toMutableList()
        for (entry in split.entries) {
            try {
                notes += entry.toNote()
            } catch (e: Exception) {
                ZuneLog.w(TAG, "a note could not be read and is kept as it is", e)
                broken += entry.toString()
            }
        }
        return notes to broken
    }

    /**
     * A locked note is written with its words in a sealed blob and nothing else: the list still
     * knows it exists, where it sits, what colour it is and when it was touched, because that is
     * what draws the list. What it says is not in the file.
     *
     * If this phone could not open the note when it was read, whatever was in it is still sealed
     * and is written straight back — unless something has since been typed into it, in which
     * case the new words are what is worth keeping and the old, unopenable blob is let go.
     */
    private fun Note.toJson(): JSONObject {
        if (isLocked) {
            val kept = unreadable[id]
            if (kept != null && title.isBlank() && content.isBlank() && items.isEmpty()) {
                return lockedShell().put(SEALED_KEY, kept)
            }
            cipher.seal(privateParts().toString())?.let { sealed ->
                unreadable.remove(id)
                return lockedShell().put(SEALED_KEY, sealed)
            }
            // The keystore would not seal it. Better a note that is still there in plain text
            // than a note that is not there at all, so fall through and write it as it is.
            ZuneLog.w(TAG, "a locked note could not be sealed; it stays readable on this phone")
        }
        return plainJson()
    }

    /** Everything about a locked note that is safe to leave in the open. */
    private fun Note.lockedShell(): JSONObject = JSONObject()
        .put("id", id)
        .put("isList", isList)
        .put("colorHex", colorHex ?: JSONObject.NULL)
        .put("isPinned", isPinned)
        .put("isArchived", isArchived)
        .put("isLocked", true)
        .put("reminderAt", reminderAt ?: JSONObject.NULL)
        .put("deletedAt", deletedAt ?: JSONObject.NULL)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    /** The words, the lists, the attachments, the people: what the seal is for. */
    private fun Note.privateParts(): JSONObject {
        val whole = plainJson()
        val shell = lockedShell()
        val private = JSONObject()
        whole.keys().forEach { key -> if (!shell.has(key)) private.put(key, whole.get(key)) }
        return private
    }

    private fun Note.plainJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("content", content)
        obj.put("richContent", richContent ?: JSONObject.NULL)
        obj.put("fontFamily", fontFamily?.name ?: JSONObject.NULL)
        obj.put("isList", isList)
        obj.put("colorHex", colorHex ?: JSONObject.NULL)
        obj.put("isPinned", isPinned)
        obj.put("isArchived", isArchived)
        obj.put("isLocked", isLocked)
        obj.put("tags", JSONArray(tags))
        obj.put("imagePaths", JSONArray(imagePaths))
        obj.put("audioPath", audioPath ?: JSONObject.NULL)
        obj.put("reminderAt", reminderAt ?: JSONObject.NULL)
        obj.put("linkedEventId", linkedEventId ?: JSONObject.NULL)
        obj.put("linkedContactId", linkedContactId ?: JSONObject.NULL)
        obj.put("linkedContactName", linkedContactName ?: JSONObject.NULL)
        obj.put("deletedAt", deletedAt ?: JSONObject.NULL)
        obj.put("createdAt", createdAt)
        obj.put("updatedAt", updatedAt)
        val itemsArray = JSONArray()
        for (item in items) {
            val itemObj = JSONObject()
            itemObj.put("id", item.id)
            itemObj.put("text", item.text)
            itemObj.put("isChecked", item.isChecked)
            itemObj.put("reminderAt", item.reminderAt ?: JSONObject.NULL)
            itemObj.put("alarmId", item.alarmId ?: JSONObject.NULL)
            itemsArray.put(itemObj)
        }
        obj.put("items", itemsArray)
        return obj
    }

    /**
     * Opens the seal if there is one. A note that will not open keeps its place in the list with
     * nothing in it, and its sealed form is remembered so writing the list back cannot lose it.
     */
    private fun JSONObject.toNote(): Note {
        val sealed = optStringOrNull(SEALED_KEY) ?: return readNote()
        val id = optString("id", "")
        val opened = cipher.open(sealed)
        if (opened == null) {
            if (id.isNotEmpty()) unreadable[id] = sealed
            return readNote()
        }
        unreadable.remove(id)
        val merged = JSONObject(opened)
        keys().forEach { key -> if (key != SEALED_KEY) merged.put(key, get(key)) }
        return merged.readNote()
    }

    private fun JSONObject.readNote(): Note {
        val itemsArray = optJSONArray("items")
        val items = mutableListOf<ChecklistItem>()
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                items.add(
                    ChecklistItem(
                        id = itemObj.optString("id", java.util.UUID.randomUUID().toString()),
                        text = itemObj.optString("text", ""),
                        isChecked = itemObj.optBoolean("isChecked", false),
                        reminderAt = itemObj.optLongOrNull("reminderAt"),
                        alarmId = itemObj.optStringOrNull("alarmId")
                    )
                )
            }
        }
        val now = System.currentTimeMillis()
        return Note(
            id = getString("id"),
            title = optString("title", ""),
            content = optString("content", ""),
            richContent = optStringOrNull("richContent"),
            fontFamily = optStringOrNull("fontFamily")?.let { name -> NoteFont.entries.firstOrNull { it.name == name } },
            items = items,
            isList = optBoolean("isList", false) || items.isNotEmpty(),
            colorHex = optStringOrNull("colorHex"),
            isPinned = optBoolean("isPinned", false),
            isArchived = optBoolean("isArchived", false),
            isLocked = optBoolean("isLocked", false),
            tags = optJSONArray("tags").toStringList(),
            imagePaths = optJSONArray("imagePaths").toStringList(),
            audioPath = optStringOrNull("audioPath"),
            reminderAt = optLongOrNull("reminderAt"),
            linkedEventId = optStringOrNull("linkedEventId"),
            linkedContactId = optStringOrNull("linkedContactId"),
            linkedContactName = optStringOrNull("linkedContactName"),
            deletedAt = optLongOrNull("deletedAt"),
            createdAt = optLong("createdAt", now),
            updatedAt = optLong("updatedAt", now)
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val value = optString(i, "")
            if (value.isNotEmpty()) list.add(value)
        }
        return list
    }

    companion object {
        private const val TAG = "NotesDataStore"

        /** The field a locked note's words live in, once they are locked. */
        private const val SEALED_KEY = "sealed"

        /** Where the text of a store that was not a JSON array is put aside, never overwritten. */
        private val CORRUPT_KEY = stringPreferencesKey("notes_json_unreadable")
        const val BACKUP_FORMAT = "zune-notes-backup"
        const val BACKUP_VERSION = 1
    }
}

/** The stored array taken apart: the objects in it, the elements that are not objects, and
 *  whether the text was an array at all. */
internal class NotesSplit(
    val entries: List<JSONObject>,
    val broken: List<String>,
    val corrupt: Boolean
)

/**
 * Takes the stored notes array apart one element at a time, so that one element that is not an
 * object does not take the rest with it. Text that is not a JSON array is reported as corrupt
 * with nothing in it; an absent or blank store is simply empty.
 */
internal fun splitNotesJson(jsonString: String?): NotesSplit {
    if (jsonString.isNullOrBlank()) return NotesSplit(emptyList(), emptyList(), corrupt = false)
    val array = try {
        JSONArray(jsonString)
    } catch (e: Exception) {
        return NotesSplit(emptyList(), emptyList(), corrupt = true)
    }
    val entries = mutableListOf<JSONObject>()
    val broken = mutableListOf<String>()
    for (i in 0 until array.length()) {
        val element = array.opt(i)
        if (element is JSONObject) entries += element else if (element != null) broken += element.toString()
    }
    return NotesSplit(entries, broken, corrupt = false)
}
