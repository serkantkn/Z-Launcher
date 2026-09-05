package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.ChecklistItem
import com.serkantkn.zunelauncher.data.model.Note
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

    val notesFlow: Flow<List<Note>> = context.notesDataStore.data.map { preferences ->
        parseNotes(preferences[NOTES_KEY])
    }

    suspend fun saveNotes(notes: List<Note>) {
        val jsonString = serializeNotes(notes)
        context.notesDataStore.edit { preferences ->
            preferences[NOTES_KEY] = jsonString
        }
    }

    /** Full backup as a JSON string (versioned envelope). */
    suspend fun exportJson(): String {
        val notes = notesFlow.first()
        val envelope = JSONObject()
        envelope.put("format", BACKUP_FORMAT)
        envelope.put("version", BACKUP_VERSION)
        envelope.put("exportedAt", System.currentTimeMillis())
        envelope.put("notes", JSONArray(serializeNotes(notes)))
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
        val current = notesFlow.first()
        val importedIds = imported.map { it.id }.toSet()
        val merged = current.filter { it.id !in importedIds } + imported
        saveNotes(merged)
        return imported.size
    }

    // ── JSON mapping ──────────────────────────────────────────────────────

    private fun serializeNotes(notes: List<Note>): String {
        val array = JSONArray()
        for (note in notes) array.put(note.toJson())
        return array.toString()
    }

    private fun parseNotes(jsonString: String?): List<Note> {
        if (jsonString == null) return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<Note>()
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i).toNote())
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Note.toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("content", content)
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

    private fun JSONObject.toNote(): Note {
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

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifEmpty { null }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

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
        const val BACKUP_FORMAT = "zune-notes-backup"
        const val BACKUP_VERSION = 1
    }
}
