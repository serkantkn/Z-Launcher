package com.serkantkn.zunelauncher.data.model

import java.util.UUID

/**
 * A single checklist row inside a [Note]. A note with a non-empty [Note.items] list
 * (or [Note.isList]) is rendered as a checklist instead of free text.
 *
 * [reminderAt] is an epoch-millis reminder scheduled through AlarmScheduler; [alarmId]
 * identifies the Alarm record created for it so it can be cancelled.
 */
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false,
    val reminderAt: Long? = null,
    val alarmId: String? = null
)

/**
 * Notes Hub model. Persisted locally by NotesDataStore.
 *
 * - [colorHex] is null when the note should follow the launcher accent color.
 * - [deletedAt] non-null means the note is in the trash (purged after 30 days).
 * - [linkedEventId] points at the CalendarEvent created by "hatırlat".
 * - [linkedContactId] / [linkedContactName] bind the note to a People Hub contact.
 * - [imagePaths] are app-private file copies, [audioPath] is an app-private recording.
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val items: List<ChecklistItem> = emptyList(),
    /** Marks a checklist note even while it has no rows yet. */
    val isList: Boolean = false,
    val colorHex: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false,
    val tags: List<String> = emptyList(),
    val imagePaths: List<String> = emptyList(),
    val audioPath: String? = null,
    val reminderAt: Long? = null,
    val linkedEventId: String? = null,
    val linkedContactId: String? = null,
    val linkedContactName: String? = null,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isChecklist: Boolean
        get() = isList || items.isNotEmpty()

    val isInTrash: Boolean
        get() = deletedAt != null

    /** True when the user typed nothing worth keeping. */
    val isBlank: Boolean
        get() = title.isBlank() && content.isBlank() && items.all { it.text.isBlank() } &&
            imagePaths.isEmpty() && audioPath == null

    /** Title shown in lists and on the live tile; falls back to the first content line. */
    val displayTitle: String
        get() = title.trim().ifEmpty {
            if (isChecklist) items.firstOrNull { it.text.isNotBlank() }?.text?.trim() ?: "liste"
            else content.trim().lineSequence().firstOrNull()?.take(40)?.stripMarkdown()?.ifEmpty { "not" } ?: "not"
        }

    /** Short body preview for list cards. */
    val preview: String
        get() = if (isChecklist) {
            items.take(3).joinToString("\n") { (if (it.isChecked) "☑ " else "☐ ") + it.text }
        } else {
            content.trim().replace('\n', ' ').take(120)
        }

    val checkedCount: Int
        get() = items.count { it.isChecked }

    /** Plain-text export used by share, .txt export and the clipboard. */
    fun toPlainText(): String = buildString {
        if (title.isNotBlank()) appendLine(title).appendLine()
        if (isChecklist) {
            items.forEach { appendLine((if (it.isChecked) "[x] " else "[ ] ") + it.text) }
        } else {
            append(content.stripMarkdown())
        }
    }.trim()

    /** Markdown export used by .md export. */
    fun toMarkdown(): String = buildString {
        if (title.isNotBlank()) appendLine("# $title").appendLine()
        if (isChecklist) {
            items.forEach { appendLine((if (it.isChecked) "- [x] " else "- [ ] ") + it.text) }
        } else {
            append(content)
        }
        if (tags.isNotEmpty()) {
            appendLine().appendLine()
            append(tags.joinToString(" ") { "#$it" })
        }
    }.trim()

    companion object {
        /** Trash retention window. */
        const val TRASH_RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000
    }
}

/** Removes the lightweight markdown markers used by the editor ("**", "# ", "- "). */
fun String.stripMarkdown(): String = this
    .lineSequence()
    .joinToString("\n") { line ->
        line.removePrefix("# ").removePrefix("## ").removePrefix("- ").removePrefix("* ")
    }
    .replace("**", "")
