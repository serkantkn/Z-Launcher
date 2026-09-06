package com.serkantkn.zunelauncher.ui.screens.notes

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import com.serkantkn.zunelauncher.util.ZuneLog
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.ChecklistItem
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

/**
 * Full-screen Windows Phone style note editor. Pure-prop for the note itself: the parent owns
 * the draft and persists it; this screen reports changes through [onNoteChange]. Side effects
 * that need the data layer (media import, reminders, contacts) go through [viewModel].
 *
 * Layout: breadcrumb header, giant Light title field, optional formatting strip, free-text or
 * checklist body, attachments, and a WindowsPhoneBottomBar (kaydet / geri al / ekle / biçim / sil).
 */
@Composable
internal fun NoteEditorScreen(
    note: Note,
    onNoteChange: (Note) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onToggleArchive: () -> Unit,
    viewModel: NotesHubViewModel,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    isPinnedToStart: Boolean = false,
    onTogglePinToStart: () -> Unit = {},
    onStatus: (String) -> Unit = {},
    bottomBarModifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val zuneColors = LocalZuneColors.current
    val accent = noteColor(note.colorHex, zuneColors.accentColor)
    val textColor = MaterialTheme.colorScheme.onBackground
    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

    val allTags by viewModel.allTags.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val playingPath by viewModel.playingPath.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showInsertDialog by remember { mutableStateOf(false) }
    var itemReminderTarget by remember { mutableStateOf<ChecklistItem?>(null) }
    var showFormatBar by remember { mutableStateOf(false) }

    // ── Undo / redo (coalesces edits closer than 800 ms) ──────────────────
    val undoStack = remember(note.id) { mutableStateListOf<Note>() }
    val redoStack = remember(note.id) { mutableStateListOf<Note>() }
    var lastEditAt by remember(note.id) { mutableLongStateOf(0L) }

    fun change(updated: Note, coalesce: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!coalesce || now - lastEditAt > 800 || undoStack.isEmpty()) {
            undoStack.add(note)
            if (undoStack.size > 60) undoStack.removeAt(0)
        }
        lastEditAt = now
        redoStack.clear()
        onNoteChange(updated)
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(note)
        lastEditAt = 0L
        onNoteChange(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(note)
        lastEditAt = 0L
        onNoteChange(next)
    }

    // ── Content field keeps a cursor so formatting can insert at the caret ──
    var contentField by remember(note.id) { mutableStateOf(TextFieldValue(note.content)) }
    LaunchedEffect(note.content) {
        if (contentField.text != note.content) {
            val caret = contentField.selection.end.coerceIn(0, note.content.length)
            contentField = TextFieldValue(note.content, TextRange(caret))
        }
    }

    fun applyFormat(kind: String) {
        val text = contentField.text
        val sel = contentField.selection
        val start = sel.min.coerceIn(0, text.length)
        val end = sel.max.coerceIn(0, text.length)
        val result: Pair<String, Int> = when (kind) {
            "bold" -> {
                if (start != end) {
                    val wrapped = text.substring(0, start) + "**" + text.substring(start, end) + "**" + text.substring(end)
                    wrapped to end + 4
                } else {
                    val inserted = text.substring(0, start) + "****" + text.substring(start)
                    inserted to start + 2
                }
            }
            else -> {
                val marker = if (kind == "heading") "# " else "- "
                val lineStart = text.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
                val lineHasMarker = text.startsWith(marker, lineStart)
                if (lineHasMarker) {
                    val removed = text.removeRange(lineStart, lineStart + marker.length)
                    removed to (start - marker.length).coerceAtLeast(lineStart)
                } else {
                    val inserted = text.substring(0, lineStart) + marker + text.substring(lineStart)
                    inserted to start + marker.length
                }
            }
        }
        contentField = TextFieldValue(result.first, TextRange(result.second.coerceIn(0, result.first.length)))
        change(note.copy(content = result.first), coalesce = false)
    }

    // ── Launchers: image picker, mic permission, speech ───────────────────
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = viewModel.importImage(uri)
                if (path != null) change(note.copy(imagePaths = note.imagePaths + path), coalesce = false)
            }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording()
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (note.isChecklist) {
                    change(note.copy(items = note.items + ChecklistItem(text = spoken)), coalesce = false)
                } else {
                    val joined = if (note.content.isBlank()) spoken else note.content.trimEnd() + "\n" + spoken
                    change(note.copy(content = joined), coalesce = false)
                }
            }
        }
    }

    fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.notes_speech_prompt))
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            ZuneLog.e("NoteEditorScreen", "startSpeech failed", e)
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            val path = viewModel.stopRecording()
            if (path != null) {
                note.audioPath?.let { old -> if (old != path) viewModel.deleteMediaFile(old) }
                change(note.copy(audioPath = path), coalesce = false)
            }
        } else if (viewModel.hasRecordPermission()) {
            viewModel.startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    val newItemFocus = remember { FocusRequester() }

    // Focus the title for a brand-new note so the keyboard appears immediately.
    LaunchedEffect(note.id) {
        if (isNew) {
            try {
                titleFocus.requestFocus()
            } catch (e: Exception) { ZuneLog.w("NoteEditorScreen", "toggleRecording ignored Exception", e) }
        }
    }

    // Stop playback when the editor leaves composition.
    DisposableEffect(note.id) {
        onDispose { viewModel.stopPlayback() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- BREADCRUMB HEADER ---
            Row(
                modifier = Modifier.padding(
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    top = 56.dp,
                    bottom = 4.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hub_notes),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = headerColor,
                    modifier = Modifier.clickable { onClose() }
                )
                Text(
                    text = "  >  ${if (isNew) stringResource(R.string.common_new) else stringResource(R.string.common_edit)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = zuneColors.textMuted
                )
                if (isRecording) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(accent)
                            .clickable { toggleRecording() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.notes_recording_stop), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // --- TITLE ---
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (note.title.isEmpty()) {
                        Text(
                            text = stringResource(R.string.notes_title_hint),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 36.sp
                            ),
                            color = zuneColors.textDim
                        )
                    }
                    BasicTextField(
                        value = note.title,
                        onValueChange = { change(note.copy(title = it)) },
                        textStyle = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 36.sp,
                            color = textColor
                        ),
                        cursorBrush = SolidColor(accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = {
                            try {
                                if (note.isChecklist) newItemFocus.requestFocus() else bodyFocus.requestFocus()
                            } catch (e: Exception) { ZuneLog.w("NoteEditorScreen", "toggleRecording ignored Exception", e) }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocus)
                    )
                }

                // Accent underline in the note color
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 12.dp)
                        .width(48.dp)
                        .height(3.dp)
                        .background(accent)
                )

                // --- LINKS ROW (contact, reminder, tags) ---
                if (note.linkedContactName != null || note.reminderAt != null || note.tags.isNotEmpty()) {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        note.linkedContactName?.let { name ->
                            LinkLine(text = stringResource(R.string.notes_contact_line, name.lowercase()), accent = accent, onClick = { viewModel.loadContacts(); showContactDialog = true })
                        }
                        note.reminderAt?.let { at ->
                            LinkLine(text = stringResource(R.string.notes_reminder_line, formatReminderDate(at)), accent = accent, onClick = { showReminderDialog = true })
                        }
                        if (note.tags.isNotEmpty()) {
                            LinkLine(text = note.tags.joinToString("  ") { "#$it" }, accent = accent, onClick = { showTagsDialog = true })
                        }
                    }
                }

                // --- FORMAT STRIP (text notes only) ---
                AnimatedVisibility(visible = showFormatBar && !note.isChecklist) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        FormatButton(Icons.Default.FormatBold, stringResource(R.string.notes_bold), accent) { applyFormat("bold") }
                        FormatButton(Icons.Default.Title, stringResource(R.string.notes_heading), accent) { applyFormat("heading") }
                        FormatButton(Icons.Default.FormatListBulleted, stringResource(R.string.notes_bullet), accent) { applyFormat("bullet") }
                    }
                }

                if (note.isChecklist) {
                    ChecklistEditor(
                        note = note,
                        accent = accent,
                        textColor = textColor,
                        onNoteChange = { change(it) },
                        onItemReminder = { item -> itemReminderTarget = item },
                        newItemFocus = newItemFocus
                    )
                } else {
                    // --- BODY ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 200.dp)
                    ) {
                        if (contentField.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.notes_content_hint),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Light,
                                    fontSize = 18.sp
                                ),
                                color = zuneColors.textDim
                            )
                        }
                        BasicTextField(
                            value = contentField,
                            onValueChange = { value ->
                                contentField = value
                                if (value.text != note.content) change(note.copy(content = value.text))
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 18.sp,
                                lineHeight = 26.sp,
                                color = textColor
                            ),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 200.dp)
                                .focusRequester(bodyFocus)
                        )
                    }
                }

                // --- ATTACHMENTS ---
                if (note.imagePaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ImageStrip(
                        paths = note.imagePaths,
                        onRemove = { path ->
                            viewModel.deleteMediaFile(path)
                            change(note.copy(imagePaths = note.imagePaths - path), coalesce = false)
                        }
                    )
                }
                note.audioPath?.let { path ->
                    Spacer(modifier = Modifier.height(16.dp))
                    AudioRow(
                        path = path,
                        isPlaying = playingPath == path,
                        accent = accent,
                        onTogglePlay = { viewModel.togglePlayback(path) },
                        onRemove = {
                            viewModel.stopPlayback()
                            viewModel.deleteMediaFile(path)
                            change(note.copy(audioPath = null), coalesce = false)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                val counts = if (note.isChecklist) {
                    stringResource(R.string.notes_completed, note.checkedCount, note.items.size)
                } else {
                    stringResource(R.string.notes_word_count, wordCount(note.content), note.content.length)
                }
                Text(
                    text = stringResource(R.string.notes_last_edited, counts, formatNoteDate(note.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.textDim
                )

                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        // --- WINDOWS PHONE STYLE BOTTOM MENU BAR ---
        WindowsPhoneBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(bottomBarModifier),
            actions = listOf(
                WpBarAction(Icons.Default.Done, stringResource(R.string.common_save)) { onClose() },
                WpBarAction(Icons.Default.Undo, stringResource(R.string.common_undo)) { undo() },
                WpBarAction(Icons.Default.AddCircle, stringResource(R.string.common_add)) { showInsertDialog = true },
                WpBarAction(Icons.Default.TextFormat, stringResource(R.string.notes_format)) {
                    if (note.isChecklist) showTagsDialog = true else showFormatBar = !showFormatBar
                },
                WpBarAction(Icons.Default.Delete, stringResource(R.string.common_delete)) { showDeleteDialog = true }
            ),
            menuItems = buildList {
                add(WpBarMenuItem(stringResource(R.string.notes_redo)) { redo() })
                add(WpBarMenuItem(if (note.isPinned) stringResource(R.string.notes_unpin) else stringResource(R.string.notes_pin)) {
                    change(note.copy(isPinned = !note.isPinned), coalesce = false)
                })
                add(WpBarMenuItem(if (isPinnedToStart) stringResource(R.string.notes_unpin_start) else stringResource(R.string.notes_pin_start)) { onTogglePinToStart() })
                add(WpBarMenuItem(stringResource(R.string.notes_color)) { showColorDialog = true })
                add(WpBarMenuItem(stringResource(R.string.notes_tags)) { showTagsDialog = true })
                add(WpBarMenuItem(if (note.isLocked) stringResource(R.string.notes_unlock) else stringResource(R.string.notes_lock)) {
                    if (!note.isLocked && !canUseDeviceLock(context)) {
                        onStatus(context.getString(R.string.notes_lock_needs_device_lock))
                    } else {
                        change(note.copy(isLocked = !note.isLocked), coalesce = false)
                    }
                })
                add(WpBarMenuItem(stringResource(R.string.common_share)) { onShare() })
                add(WpBarMenuItem(stringResource(R.string.notes_export_txt)) { viewModel.exportNoteToDocuments(note, markdown = false) })
                add(WpBarMenuItem(stringResource(R.string.notes_export_md)) { viewModel.exportNoteToDocuments(note, markdown = true) })
                add(WpBarMenuItem(if (note.isArchived) stringResource(R.string.notes_unarchive) else stringResource(R.string.notes_archive)) { onToggleArchive() })
                if (note.isChecklist) {
                    add(WpBarMenuItem(stringResource(R.string.notes_delete_checked)) {
                        change(note.copy(items = note.items.filter { !it.isChecked }), coalesce = false)
                    })
                    add(WpBarMenuItem(stringResource(R.string.notes_uncheck_all)) {
                        change(note.copy(items = note.items.map { it.copy(isChecked = false) }), coalesce = false)
                    })
                }
                add(WpBarMenuItem(if (note.isChecklist) stringResource(R.string.notes_to_note) else stringResource(R.string.notes_to_list)) {
                    change(convertNoteType(note), coalesce = false)
                })
            }
        )
    }

    // ── DIALOGS ───────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        DeleteNotesDialog(
            count = 1,
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false }
        )
    }
    if (showColorDialog) {
        NoteColorDialog(
            selectedHex = note.colorHex,
            onSelect = { hex -> showColorDialog = false; change(note.copy(colorHex = hex), coalesce = false) },
            onDismiss = { showColorDialog = false }
        )
    }
    if (showTagsDialog) {
        TagsDialog(
            current = note.tags,
            allTags = allTags,
            onSave = { tags -> showTagsDialog = false; change(note.copy(tags = tags), coalesce = false) },
            onDismiss = { showTagsDialog = false }
        )
    }
    if (showContactDialog) {
        ContactPickerDialog(
            contacts = contacts,
            currentContactId = note.linkedContactId,
            onPick = { contact ->
                showContactDialog = false
                change(note.copy(linkedContactId = contact.id, linkedContactName = contact.name), coalesce = false)
            },
            onClear = {
                showContactDialog = false
                change(note.copy(linkedContactId = null, linkedContactName = null), coalesce = false)
            },
            onDismiss = { showContactDialog = false }
        )
    }
    if (showReminderDialog) {
        ReminderDialog(
            title = stringResource(R.string.notes_remind_cap),
            initial = note.reminderAt,
            onSet = { time ->
                showReminderDialog = false
                scope.launch { change(viewModel.setNoteReminder(note, time), coalesce = false) }
            },
            onClear = {
                showReminderDialog = false
                scope.launch { change(viewModel.clearNoteReminder(note), coalesce = false) }
            },
            onDismiss = { showReminderDialog = false }
        )
    }
    itemReminderTarget?.let { item ->
        ReminderDialog(
            title = stringResource(R.string.notes_remind_item_cap),
            initial = item.reminderAt,
            onSet = { time ->
                itemReminderTarget = null
                scope.launch { change(viewModel.setItemReminder(note, item.id, time), coalesce = false) }
            },
            onClear = {
                itemReminderTarget = null
                scope.launch { change(viewModel.clearItemReminder(note, item.id), coalesce = false) }
            },
            onDismiss = { itemReminderTarget = null }
        )
    }
    if (showInsertDialog) {
        InsertDialog(
            hasAudio = note.audioPath != null,
            onPickImage = {
                showInsertDialog = false
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRecordAudio = { showInsertDialog = false; toggleRecording() },
            onSpeech = { showInsertDialog = false; startSpeech() },
            onLinkContact = { showInsertDialog = false; viewModel.loadContacts(); showContactDialog = true },
            onReminder = { showInsertDialog = false; showReminderDialog = true },
            onDismiss = { showInsertDialog = false }
        )
    }
}

@Composable
private fun LinkLine(text: String, accent: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = accent,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 3.dp)
    )
}

@Composable
private fun FormatButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(BorderStroke(1.5.dp, accent), RoundedCornerShape(0.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ════════════════════════════════════════════════════════════
// CHECKLIST EDITOR
// ════════════════════════════════════════════════════════════

@Composable
private fun ChecklistEditor(
    note: Note,
    accent: Color,
    textColor: Color,
    onNoteChange: (Note) -> Unit,
    onItemReminder: (ChecklistItem) -> Unit,
    newItemFocus: FocusRequester
) {
    val zuneColors = LocalZuneColors.current
    var newItemText by remember(note.id) { mutableStateOf("") }

    fun addItem() {
        val text = newItemText.trim()
        if (text.isEmpty()) return
        onNoteChange(note.copy(items = note.items + ChecklistItem(text = text)))
        newItemText = ""
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        note.items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                WpCheckBox(
                    checked = item.isChecked,
                    onCheckedChange = { checked ->
                        onNoteChange(note.copy(items = note.items.map {
                            if (it.id == item.id) it.copy(isChecked = checked) else it
                        }))
                    },
                    accent = accent
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = item.text,
                        onValueChange = { newText ->
                            onNoteChange(note.copy(items = note.items.map {
                                if (it.id == item.id) it.copy(text = newText) else it
                            }))
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 18.sp,
                            color = if (item.isChecked) zuneColors.textMuted else textColor,
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        cursorBrush = SolidColor(accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    item.reminderAt?.let { at ->
                        Text(
                            text = formatReminderDate(at),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.notes_remind_item),
                    tint = if (item.reminderAt != null) accent else zuneColors.textDim,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onItemReminder(item) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.notes_remove_item),
                    tint = zuneColors.textDim,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            onNoteChange(note.copy(items = note.items.filter { it.id != item.id }))
                        }
                )
            }
        }

        // --- NEW ITEM ROW ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { addItem() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (newItemText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notes_add_item),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 18.sp
                        ),
                        color = zuneColors.textDim
                    )
                }
                BasicTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 18.sp,
                        color = textColor
                    ),
                    cursorBrush = SolidColor(accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addItem() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(newItemFocus)
                )
            }
        }
    }
}

/** Text note ↔ checklist conversion. Lines become items and vice versa. */
internal fun convertNoteType(note: Note): Note {
    return if (note.isChecklist) {
        val lines = note.items.joinToString("\n") { (if (it.isChecked) "✓ " else "") + it.text }
        note.copy(
            isList = false,
            items = emptyList(),
            content = listOf(note.content.trim(), lines).filter { it.isNotBlank() }.joinToString("\n\n")
        )
    } else {
        val items = note.content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ChecklistItem(text = it.removePrefix("✓ ").removePrefix("- ").removePrefix("* ").removePrefix("# ")) }
        note.copy(
            isList = true,
            content = "",
            items = items
        )
    }
}
