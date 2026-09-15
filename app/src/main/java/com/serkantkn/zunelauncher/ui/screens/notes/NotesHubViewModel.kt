package com.serkantkn.zunelauncher.ui.screens.notes

import com.serkantkn.zunelauncher.util.localizedString
import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.ZuneLog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.repository.NotesBridge
import com.serkantkn.zunelauncher.di.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

/** Ordering options offered in the "sırala" dialog. */
enum class NoteSortMode(@StringRes val titleRes: Int) {
    DATE(R.string.note_sort_date),
    NAME(R.string.note_sort_name),
    COLOR(R.string.note_sort_color)
}

/**
 * Notes Hub state. Follows the house ViewModel template: AndroidViewModel, repositories from
 * AppContainer, one StateFlow per field, one-shot results delivered via callback parameters.
 */
class NotesHubViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val notesDataStore = container.notesDataStore
    private val settingsDataStore = container.settingsDataStore
    private val calendarDataStore = container.calendarDataStore
    private val alarmDataStore = container.alarmDataStore
    private val alarmScheduler = container.alarmScheduler
    private val contactRepository = container.contactRepository

    // ── Source flows ──────────────────────────────────────────────────────

    private val rawNotes: StateFlow<List<Note>> = notesDataStore.notesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Every note that is not in the trash. */
    val allNotes: StateFlow<List<Note>> = rawNotes
        .map { notes -> notes.filter { !it.isInTrash } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sortMode = MutableStateFlow(NoteSortMode.DATE)
    val sortMode: StateFlow<NoteSortMode> = _sortMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    /** Ids selected in multi-select mode. */
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    /** The note currently open in the editor, or null when the editor is closed. */
    private val _editingNote = MutableStateFlow<Note?>(null)
    val editingNote: StateFlow<Note?> = _editingNote.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactModel>>(emptyList())
    val contacts: StateFlow<List<ContactModel>> = _contacts.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath: StateFlow<String?> = _playingPath.asStateFlow()

    /** Short status text shown by the hub after export/backup operations. */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val filtered: StateFlow<List<Note>> = combine(allNotes, _searchQuery, _sortMode) { notes, query, sort ->
        val q = query.trim().lowercase()
        val base = if (q.isEmpty()) notes else notes.filter { note ->
            note.title.lowercase().contains(q) ||
                note.content.lowercase().contains(q) ||
                note.tags.any { it.lowercase().contains(q) } ||
                note.items.any { it.text.lowercase().contains(q) }
        }
        sortNotes(base, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "notlar" tab: everything not archived. */
    val activeNotes: StateFlow<List<Note>> = filtered
        .map { notes -> notes.filter { !it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "listeler" tab: checklist notes that are not archived. */
    val checklistNotes: StateFlow<List<Note>> = filtered
        .map { notes -> notes.filter { !it.isArchived && it.isChecklist } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "arşiv" tab. */
    val archivedNotes: StateFlow<List<Note>> = filtered
        .map { notes -> notes.filter { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "çöp" tab: trashed notes, newest deletion first. */
    val trashNotes: StateFlow<List<Note>> = combine(rawNotes, _searchQuery) { notes, query ->
        val q = query.trim().lowercase()
        notes.filter { it.isInTrash && (q.isEmpty() || it.displayTitle.lowercase().contains(q)) }
            .sortedByDescending { it.deletedAt ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Distinct tags across active notes, used for the dynamic tag pivots and the tag editor. */
    val allTags: StateFlow<List<String>> = allNotes
        .map { notes -> notes.filter { !it.isArchived }.flatMap { it.tags }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Note ids currently pinned to Start as their own tile. */
    val startPinnedNoteIds: StateFlow<Set<String>> = settingsDataStore.startTiles
        .map { tiles -> tiles.mapNotNull { it.noteId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isQuickNoteTileOnStart: StateFlow<Boolean> = settingsDataStore.startTiles
        .map { tiles -> tiles.any { it.isQuickNote } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        purgeExpiredTrash()
    }

    fun notesForTag(tag: String): List<Note> = activeNotes.value.filter { tag in it.tags }

    private fun sortNotes(notes: List<Note>, mode: NoteSortMode): List<Note> = when (mode) {
        NoteSortMode.DATE -> notes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })
        NoteSortMode.NAME -> notes.sortedWith(
            compareByDescending<Note> { it.isPinned }.thenBy { it.displayTitle.lowercase() }
        )
        NoteSortMode.COLOR -> notes.sortedWith(
            compareByDescending<Note> { it.isPinned }.thenBy { it.colorHex ?: "" }.thenByDescending { it.updatedAt }
        )
    }

    // ── Bridge (cross-hub requests) ───────────────────────────────────────

    /**
     * Consumes a pending NotesBridge request and returns the note the editor should open,
     * along with whether it is brand-new. Returns null when nothing is pending.
     */
    suspend fun consumeBridgeRequest(): Pair<Note, Boolean>? {
        val request = NotesBridge.consume() ?: return null
        return when (request) {
            is NotesBridge.Request.Open -> {
                val note = notesDataStore.notesFlow.first().firstOrNull { it.id == request.noteId }
                note?.let { it to false }
            }
            is NotesBridge.Request.New -> Note(
                title = request.title,
                content = request.content,
                isList = request.asChecklist,
                linkedContactId = request.linkedContactId,
                linkedContactName = request.linkedContactName
            ) to true
        }
    }

    // ── Sort / search ─────────────────────────────────────────────────────

    fun setSortMode(mode: NoteSortMode) {
        _sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        val open = !_isSearchOpen.value
        _isSearchOpen.value = open
        if (!open) _searchQuery.value = ""
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    // ── Selection ─────────────────────────────────────────────────────────

    fun enterSelectionMode(initialId: String? = null) {
        _isSelectionMode.value = true
        _selectedIds.value = if (initialId != null) setOf(initialId) else emptySet()
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelected(id: String) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun selectAll(ids: List<String>) {
        _selectedIds.value = ids.toSet()
    }

    // ── Editor ────────────────────────────────────────────────────────────

    fun openNewNote(asChecklist: Boolean = false) {
        _editingNote.value = Note(isList = asChecklist)
    }

    fun openNote(note: Note) {
        _editingNote.value = note
    }

    fun closeEditor() {
        _editingNote.value = null
    }

    /**
     * Persists the note. Blank notes are dropped (and removed if they existed) so the
     * list never fills with empty cards. [onSaved] runs after the write completes.
     */
    fun saveNote(note: Note, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val current = notesDataStore.notesFlow.first()
            val cleanedItems = note.items.filter { it.text.isNotBlank() }
            val cleaned = note.copy(items = cleanedItems, tags = note.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
            val updated = if (cleaned.isBlank) {
                current.filter { it.id != note.id }
            } else {
                val stamped = cleaned.copy(updatedAt = System.currentTimeMillis())
                if (current.any { it.id == note.id }) {
                    current.map { if (it.id == note.id) stamped else it }
                } else {
                    current + stamped
                }
            }
            notesDataStore.saveNotes(updated)
            onSaved()
        }
    }

    // ── Trash lifecycle ───────────────────────────────────────────────────

    /** Moves notes to the trash (30-day retention). Pinned/archived flags are kept for restore. */
    fun moveToTrash(ids: Set<String>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = notesDataStore.notesFlow.first()
            notesDataStore.saveNotes(current.map { if (it.id in ids) it.copy(deletedAt = now) else it })
            // A trashed note should not keep a Start tile.
            ids.forEach { unpinFromStart(it) }
            if (_editingNote.value?.id in ids) _editingNote.value = null
            exitSelectionMode()
            onDone()
        }
    }

    fun deleteNote(id: String, onDeleted: () -> Unit = {}) = moveToTrash(setOf(id), onDeleted)

    fun deleteNotes(ids: Set<String>, onDeleted: () -> Unit = {}) = moveToTrash(ids, onDeleted)

    fun restoreFromTrash(ids: Set<String>) {
        viewModelScope.launch {
            val current = notesDataStore.notesFlow.first()
            notesDataStore.saveNotes(current.map { if (it.id in ids) it.copy(deletedAt = null) else it })
            exitSelectionMode()
        }
    }

    /** Permanently deletes notes and cleans up their alarms, calendar events and media files. */
    fun deleteForever(ids: Set<String>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val current = notesDataStore.notesFlow.first()
            val doomed = current.filter { it.id in ids }
            doomed.forEach { cleanupNoteResources(it) }
            notesDataStore.saveNotes(current.filter { it.id !in ids })
            exitSelectionMode()
            onDone()
        }
    }

    fun emptyTrash(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val ids = notesDataStore.notesFlow.first().filter { it.isInTrash }.map { it.id }.toSet()
            deleteForever(ids, onDone)
        }
    }

    private fun purgeExpiredTrash() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expired = notesDataStore.notesFlow.first()
                .filter { it.deletedAt != null && now - it.deletedAt > Note.TRASH_RETENTION_MS }
                .map { it.id }
                .toSet()
            if (expired.isNotEmpty()) deleteForever(expired)
        }
    }

    private suspend fun cleanupNoteResources(note: Note) {
        note.items.mapNotNull { it.alarmId }.forEach { cancelAlarmRecord(it) }
        note.linkedEventId?.let { removeCalendarEvent(it) }
        note.imagePaths.forEach { runCatching { File(it).delete() } }
        note.audioPath?.let { runCatching { File(it).delete() } }
    }

    // ── Bulk / single edits ───────────────────────────────────────────────

    fun togglePinned(id: String) = updateNote(id) { it.copy(isPinned = !it.isPinned) }

    fun setArchived(ids: Set<String>, archived: Boolean) {
        viewModelScope.launch {
            val current = notesDataStore.notesFlow.first()
            notesDataStore.saveNotes(
                current.map { if (it.id in ids) it.copy(isArchived = archived, isPinned = false) else it }
            )
            exitSelectionMode()
        }
    }

    fun setArchived(id: String, archived: Boolean) = setArchived(setOf(id), archived)

    /** "arşivi temizle": archived notes go to the trash rather than being lost outright. */
    fun clearArchive(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val ids = notesDataStore.notesFlow.first().filter { it.isArchived && !it.isInTrash }.map { it.id }.toSet()
            moveToTrash(ids, onDone)
        }
    }

    fun duplicateNotes(ids: Set<String>) {
        viewModelScope.launch {
            val current = notesDataStore.notesFlow.first()
            val now = System.currentTimeMillis()
            val copies = current.filter { it.id in ids }.map { source ->
                source.copy(
                    id = UUID.randomUUID().toString(),
                    title = if (source.title.isBlank()) "" else getApplication<Application>().localizedString(R.string.notes_copy_suffix, source.title),
                    items = source.items.map { it.copy(id = UUID.randomUUID().toString(), reminderAt = null, alarmId = null) },
                    isPinned = false,
                    linkedEventId = null,
                    reminderAt = null,
                    createdAt = now,
                    updatedAt = now
                )
            }
            notesDataStore.saveNotes(current + copies)
            exitSelectionMode()
        }
    }

    fun copyToClipboard(note: Note) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(note.displayTitle, note.toPlainText()))
        _statusMessage.value = getApplication<Application>().localizedString(R.string.notes_copied)
    }

    /** Toggles a checklist row directly from the list card without opening the editor. */
    fun toggleChecklistItem(noteId: String, itemId: String) = updateNote(noteId) { note ->
        note.copy(items = note.items.map { if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it })
    }

    private fun updateNote(id: String, transform: (Note) -> Note) {
        viewModelScope.launch {
            val current = notesDataStore.notesFlow.first()
            notesDataStore.saveNotes(
                current.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it }
            )
            _editingNote.value?.let { editing ->
                if (editing.id == id) _editingNote.value = transform(editing)
            }
        }
    }

    // ── Start tiles ───────────────────────────────────────────────────────

    fun togglePinToStart(note: Note) {
        viewModelScope.launch {
            val tiles = settingsDataStore.startTiles.first()
            val id = "note:${note.id}"
            val updated = if (tiles.any { it.id == id }) tiles.filter { it.id != id }
            else tiles + StartTileItem.fromNote(note.id, 2)
            settingsDataStore.setStartTiles(updated)
            // Make sure the note itself exists before the tile shows up.
            if (updated.any { it.id == id }) saveNote(note)
        }
    }

    fun toggleQuickNoteTile() {
        viewModelScope.launch {
            val tiles = settingsDataStore.startTiles.first()
            val updated = if (tiles.any { it.isQuickNote }) tiles.filter { !it.isQuickNote }
            else tiles + StartTileItem(StartTileItem.QUICK_NOTE_ID, 1)
            settingsDataStore.setStartTiles(updated)
        }
    }

    private suspend fun unpinFromStart(noteId: String) {
        val tiles = settingsDataStore.startTiles.first()
        if (tiles.any { it.noteId == noteId }) {
            settingsDataStore.setStartTiles(tiles.filter { it.noteId != noteId })
        }
    }

    // ── Reminders (Calendar + Alarm integration) ──────────────────────────

    /** Creates or updates the CalendarEvent bound to this note. Returns the updated draft. */
    suspend fun setNoteReminder(note: Note, timeMillis: Long): Note {
        val events = calendarDataStore.eventsFlow.first()
        val existing = events.firstOrNull { it.id == note.linkedEventId }
        // A note's reminder is the launcher's own business, so it stays in the launcher's own
        // store rather than being written into somebody's real calendar.
        val event = (existing ?: CalendarEvent(title = note.displayTitle, startMillis = timeMillis)).copy(
            title = note.displayTitle,
            description = if (note.isChecklist) getApplication<Application>().localizedString(R.string.notes_items_list, note.items.size) else note.content.stripForEvent(),
            startMillis = timeMillis,
            endMillis = timeMillis + CalendarEvent.DEFAULT_DURATION_MS,
            colorHex = note.colorHex ?: CalendarEvent.DEFAULT_COLOR,
            linkedNoteId = note.id,
            isLocal = true
        )
        val updatedEvents = if (existing != null) events.map { if (it.id == event.id) event else it } else events + event
        calendarDataStore.saveEvents(updatedEvents)
        // Also ring at that time through the alarm stack.
        val alarm = Alarm(
            id = "note-${note.id}",
            hour = event.hour,
            minute = event.minute,
            label = note.displayTitle,
            noteId = note.id,
            exactTimeMillis = timeMillis
        )
        upsertAlarmRecord(alarm)
        return note.copy(reminderAt = timeMillis, linkedEventId = event.id)
    }

    suspend fun clearNoteReminder(note: Note): Note {
        note.linkedEventId?.let { removeCalendarEvent(it) }
        cancelAlarmRecord("note-${note.id}")
        return note.copy(reminderAt = null, linkedEventId = null)
    }

    /** Schedules an alarm for a single checklist row. Returns the updated draft. */
    suspend fun setItemReminder(note: Note, itemId: String, timeMillis: Long): Note {
        val item = note.items.firstOrNull { it.id == itemId } ?: return note
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val alarmId = item.alarmId ?: "item-${item.id}"
        val alarm = Alarm(
            id = alarmId,
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            label = item.text.ifBlank { note.displayTitle },
            noteId = note.id,
            exactTimeMillis = timeMillis
        )
        upsertAlarmRecord(alarm)
        return note.copy(items = note.items.map {
            if (it.id == itemId) it.copy(reminderAt = timeMillis, alarmId = alarmId) else it
        })
    }

    suspend fun clearItemReminder(note: Note, itemId: String): Note {
        val item = note.items.firstOrNull { it.id == itemId } ?: return note
        item.alarmId?.let { cancelAlarmRecord(it) }
        return note.copy(items = note.items.map {
            if (it.id == itemId) it.copy(reminderAt = null, alarmId = null) else it
        })
    }

    private suspend fun upsertAlarmRecord(alarm: Alarm) {
        val alarms = alarmDataStore.alarmsFlow.first()
        val updated = if (alarms.any { it.id == alarm.id }) alarms.map { if (it.id == alarm.id) alarm else it } else alarms + alarm
        alarmDataStore.saveAlarms(updated)
        alarmScheduler.schedule(alarm)
    }

    private suspend fun cancelAlarmRecord(alarmId: String) {
        val alarms = alarmDataStore.alarmsFlow.first()
        val existing = alarms.firstOrNull { it.id == alarmId }
        if (existing != null) {
            alarmScheduler.cancel(existing)
            alarmDataStore.saveAlarms(alarms.filter { it.id != alarmId })
        }
    }

    private suspend fun removeCalendarEvent(eventId: String) {
        val events = calendarDataStore.eventsFlow.first()
        if (events.any { it.id == eventId }) {
            calendarDataStore.saveEvents(events.filter { it.id != eventId })
        }
    }

    private fun String.stripForEvent(): String = trim().replace('\n', ' ').take(80)

    // ── Contacts ──────────────────────────────────────────────────────────

    fun loadContacts() {
        val app = getApplication<Application>()
        val granted = ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            _contacts.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _contacts.value = contactRepository.getContacts()
            } catch (e: Exception) {
                ZuneLog.e("NotesHubViewModel", "loadContacts failed", e)
            }
        }
    }

    // ── Images ────────────────────────────────────────────────────────────

    /** Copies a picked image into app-private storage and returns the file path. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val app = getApplication<Application>()
            val dir = File(app.filesDir, "notes_images").apply { mkdirs() }
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            app.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            target.absolutePath
        } catch (e: Exception) {
            ZuneLog.e("NotesHubViewModel", "importImage failed", e)
            null
        }
    }

    fun deleteMediaFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { File(path).delete() } }
    }

    // ── Audio ─────────────────────────────────────────────────────────────

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var player: MediaPlayer? = null

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startRecording(): Boolean {
        if (_isRecording.value) return true
        val app = getApplication<Application>()
        return try {
            val dir = File(app.filesDir, "notes_audio").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(app) else @Suppress("DEPRECATION") MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(96_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            recordingFile = file
            _isRecording.value = true
            true
        } catch (e: Exception) {
            ZuneLog.e("NotesHubViewModel", "startRecording failed", e)
            recorder = null
            recordingFile = null
            _isRecording.value = false
            false
        }
    }

    /** Stops the recorder and returns the saved file path. */
    fun stopRecording(): String? {
        val rec = recorder ?: return null
        val path = try {
            rec.stop()
            recordingFile?.absolutePath
        } catch (e: Exception) {
            ZuneLog.e("NotesHubViewModel", "stopRecording failed", e)
            recordingFile?.delete()
            null
        } finally {
            rec.release()
            recorder = null
            recordingFile = null
            _isRecording.value = false
        }
        return path
    }

    fun togglePlayback(path: String) {
        if (_playingPath.value == path) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.setOnCompletionListener { stopPlayback() }
            mp.prepare()
            mp.start()
            player = mp
            _playingPath.value = path
        } catch (e: Exception) {
            ZuneLog.e("NotesHubViewModel", "togglePlayback failed", e)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) { ZuneLog.w("NotesHubViewModel", "stopPlayback ignored Exception", e) }
        player = null
        _playingPath.value = null
    }

    // ── Export / backup ───────────────────────────────────────────────────

    /** Writes a .txt or .md copy of the note into Documents/ZuneNotes (Files Hub can browse it). */
    fun exportNoteToDocuments(note: Note, markdown: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val safeName = note.displayTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { getApplication<Application>().localizedString(R.string.notes_untitled_note) }
            val fileName = if (markdown) "$safeName.md" else "$safeName.txt"
            val body = if (markdown) note.toMarkdown() else note.toPlainText()
            val result = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, if (markdown) "text/markdown" else "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/ZuneNotes")
                    }
                    val uri = app.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                    if (uri != null) {
                        app.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
                        getApplication<Application>().localizedString(R.string.notes_export_path, fileName)
                    } else null
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ZuneNotes").apply { mkdirs() }
                    File(dir, fileName).writeText(body)
                    getApplication<Application>().localizedString(R.string.notes_export_path, fileName)
                }
            } catch (e: Exception) {
                ZuneLog.e("NotesHubViewModel", "exportNoteToDocuments failed", e)
                null
            }
            _statusMessage.value = if (result != null) getApplication<Application>().localizedString(R.string.notes_exported, result) else getApplication<Application>().localizedString(R.string.notes_export_failed)
        }
    }

    /** Writes the full backup to a user-chosen document (SAF). */
    fun writeBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val json = notesDataStore.exportJson()
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } != null
            } catch (e: Exception) {
                ZuneLog.e("NotesHubViewModel", "writeBackup failed", e)
                false
            }
            _statusMessage.value = if (ok) getApplication<Application>().localizedString(R.string.notes_backup_saved) else getApplication<Application>().localizedString(R.string.notes_backup_failed)
        }
    }

    /** Restores notes from a user-chosen backup document (SAF); merges by id. */
    fun readBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = try {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: ""
                notesDataStore.importJson(json)
            } catch (e: Exception) {
                ZuneLog.e("NotesHubViewModel", "readBackup failed", e)
                -1
            }
            _statusMessage.value = when {
                count < 0 -> getApplication<Application>().localizedString(R.string.notes_backup_invalid)
                count == 0 -> getApplication<Application>().localizedString(R.string.notes_backup_empty)
                else -> getApplication<Application>().localizedString(R.string.notes_restored_count, count)
            }
        }
    }

    fun shareNote(note: Note) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.displayTitle)
            putExtra(Intent.EXTRA_TEXT, note.toPlainText())
        }
        val chooser = Intent.createChooser(intent, getApplication<Application>().localizedString(R.string.notes_share_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(chooser)
        } catch (e: Exception) {
            ZuneLog.e("NotesHubViewModel", "shareNote failed", e)
        }
    }

    override fun onCleared() {
        stopRecording()
        stopPlayback()
        super.onCleared()
    }
}
