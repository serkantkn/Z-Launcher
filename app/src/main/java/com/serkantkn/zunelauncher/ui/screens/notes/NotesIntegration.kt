package com.serkantkn.zunelauncher.ui.screens.notes

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.data.repository.NotesBridge
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import java.util.Calendar

/**
 * Small, self-contained Notes widgets embedded in other hubs. They read the notes store
 * straight from AppContainer (ZuneBackground / ContactDetailScreen style) and hand off to
 * the Notes hub through [NotesBridge], so host screens need a single call site.
 */

/** Compact one-line note row used by the embedded sections. */
@Composable
private fun EmbeddedNoteRow(note: Note, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val stripe = noteColor(note.colorHex, zuneColors.accentColor)
    val cardBackground = if (zuneColors.isDark) zuneColors.overlay else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackground, RoundedCornerShape(0.dp))
            .clickable { onClick() }
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(stripe)
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = note.displayTitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = when {
                note.isLocked -> stringResource(R.string.notes_locked)
                note.isChecklist -> stringResource(R.string.notes_completed, note.checkedCount, note.items.size)
                else -> note.preview
            }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * People Hub: notes linked to a contact, plus a "yeni not" entry that pre-links the contact.
 * Rendered inside ContactDetailScreen's scrolling column.
 */
@Composable
fun ContactNotesSection(
    contactId: String,
    contactName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val allNotes by context.appContainer.notesDataStore.notesFlow.collectAsState(initial = emptyList())
    val linked = remember(allNotes, contactId) {
        allNotes.filter { it.linkedContactId == contactId && !it.isInTrash }.sortedByDescending { it.updatedAt }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.hub_notes),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 22.sp),
                color = if (zuneColors.isDark) Color.White else Color.Black,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.notes_new_note_plus),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .clickable { NotesBridge.newNote(linkedContactId = contactId, linkedContactName = contactName) }
                    .padding(vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (linked.isEmpty()) {
            Text(
                text = stringResource(R.string.notes_none_for_contact),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textDim,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                linked.take(5).forEach { note ->
                    EmbeddedNoteRow(note = note, onClick = { NotesBridge.open(note.id) })
                }
                if (linked.size > 5) {
                    Text(
                        text = stringResource(R.string.notes_more_notes, linked.size - 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = zuneColors.textDim
                    )
                }
            }
        }
    }
}

/**
 * Social page: stringResource(R.string.notes_edited_today) group shown above the notification stream.
 * Renders nothing when no note was touched today.
 */
@Composable
fun TodayNotesGroup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val allNotes by context.appContainer.notesDataStore.notesFlow.collectAsState(initial = emptyList())
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val today = remember(allNotes) {
        allNotes.filter { !it.isInTrash && !it.isArchived && it.updatedAt >= todayStart }
            .sortedByDescending { it.updatedAt }
            .take(3)
    }
    if (today.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(
            text = stringResource(R.string.notes_edited_today),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 18.sp, letterSpacing = 1.sp),
            color = zuneColors.textMuted,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            today.forEach { note ->
                EmbeddedNoteRow(note = note, onClick = { NotesBridge.open(note.id) })
            }
        }
    }
}
