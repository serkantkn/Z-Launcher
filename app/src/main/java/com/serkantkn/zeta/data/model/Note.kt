package com.serkantkn.zeta.data.model

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
    /**
     * The words, as plain text with the editor's light markdown (bold, headings, lists). This is
     * what search, previews, sharing, the .txt/.md export and the Start tile read.
     */
    val content: String = "",
    /**
     * The same words with everything the editor can do to them - colour, size, highlight,
     * underline, alignment and the rest - as HTML. Null for a note that was never formatted,
     * which the editor then reads from [content].
     */
    val richContent: String? = null,
    /** The typeface the whole note is written in; null is the launcher's own. */
    val fontFamily: NoteFont? = null,
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
            if (isChecklist) items.firstOrNull { it.text.isNotBlank() }?.text?.trim() ?: NoteFallbackTitles.list
            else content.trim().lineSequence().firstOrNull()?.take(40)?.stripMarkdown()?.ifEmpty { NoteFallbackTitles.note } ?: NoteFallbackTitles.note
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

/** A typeface a note can be written in. The rich editor keeps colour and size per word, but not
 *  a typeface, so a note takes one for the whole of itself. */
enum class NoteFont { SANS, SERIF, MONO, HAND }

/**
 * Removes the markdown markers the editor writes: headings, list bullets and numbers, bold,
 * italic, strike-through and the `<u>` it uses for underline.
 */
fun String.stripMarkdown(): String = this
    .lineSequence()
    .joinToString("\n") { line ->
        line.replace(MARKDOWN_HEADING, "").replace(MARKDOWN_BULLET, "")
    }
    .replace("**", "")
    .replace("~~", "")
    .replace(MARKDOWN_UNDERLINE_TAG, "")
    .replace(MARKDOWN_ITALIC) { it.groupValues[1] }

private val MARKDOWN_HEADING = Regex("^#{1,6} ")
private val MARKDOWN_BULLET = Regex("^\\s*[-*] ")
private val MARKDOWN_UNDERLINE_TAG = Regex("</?u>", RegexOption.IGNORE_CASE)
private val MARKDOWN_ITALIC = Regex("(?<![*\\w])[*_]([^*_\\n]+)[*_](?![*\\w])")

/**
 * Titles used when a note has neither a title nor content. The model has no Context, so the app
 * refreshes these for the active language (ZuneLauncherApp.onCreate and MainActivity.attachBaseContext).
 */
object NoteFallbackTitles {
    @Volatile var note: String = "not"
    @Volatile var list: String = "liste"

    fun refresh(context: android.content.Context) {
        note = context.getString(com.serkantkn.zeta.R.string.notes_untitled_note)
        list = context.getString(com.serkantkn.zeta.R.string.notes_untitled_list)
    }
}
