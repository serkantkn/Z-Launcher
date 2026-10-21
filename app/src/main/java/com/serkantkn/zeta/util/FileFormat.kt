package com.serkantkn.zeta.util

import com.serkantkn.zeta.data.model.FileItemModel
import java.text.Collator
import java.util.Locale

/**
 * What the Files hub needs to know about names, sizes and kinds — none of it needing Android, so
 * all of it testable.
 */

// ── Sizes ───────────────────────────────────────────────────────────────────

/** A byte count as a person would say it. */
fun formatFileSize(bytes: Long): String {
    if (bytes < 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    // One decimal place until it stops meaning anything.
    return if (value >= 100.0) {
        String.format(Locale.ROOT, "%.0f %s", value, units[unit])
    } else {
        String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }
}

// ── Sorting ─────────────────────────────────────────────────────────────────

/** The orders the list can be put in. */
enum class FileSort { NAME, DATE, SIZE, TYPE }

/** How a folder is shown: rows of names and details, or a grid of pictures. */
enum class FilesView { LIST, THUMBNAILS }

/**
 * Puts a folder's contents in order.
 *
 * Folders always lead, whichever way the rest is sorted — that is what every file manager does and
 * what anybody scanning a list expects. Names are compared with a [Collator] for the reader's own
 * language, so ç lands after c and ş after s rather than after z, which is where comparing the raw
 * characters puts them.
 */
fun sortFiles(
    items: List<FileItemModel>,
    sort: FileSort,
    ascending: Boolean,
    locale: Locale = Locale.getDefault()
): List<FileItemModel> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val byKind = compareByDescending<FileItemModel> { it.isDirectory }
    val comparator = when (sort) {
        FileSort.NAME -> byKind.thenComparator { a, b -> collator.compare(a.name, b.name) }
        FileSort.DATE -> byKind.thenComparator { a, b -> a.lastModified.compareTo(b.lastModified) }
        FileSort.SIZE -> byKind.thenComparator { a, b -> a.size.compareTo(b.size) }
        FileSort.TYPE -> byKind
            .thenComparator { a, b -> collator.compare(a.extension, b.extension) }
            .thenComparator { a, b -> collator.compare(a.name, b.name) }
    }
    val sorted = items.sortedWith(comparator)
    // Reversing would put the files above the folders, so only the second key is turned around.
    return if (ascending) sorted else sorted.sortedWith(reverseWithin(comparator))
}

private fun reverseWithin(comparator: Comparator<FileItemModel>): Comparator<FileItemModel> =
    Comparator { a, b ->
        when {
            a.isDirectory != b.isDirectory -> if (a.isDirectory) -1 else 1
            else -> -comparator.compare(a, b)
        }
    }

// ── Kinds ───────────────────────────────────────────────────────────────────

/** The kinds of file the hub tells apart, for icons, colours and the categories page. */
enum class FileCategory { FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APP, OTHER }

fun categoryOf(extension: String, isDirectory: Boolean = false): FileCategory {
    if (isDirectory) return FileCategory.FOLDER
    return when (extension.lowercase(Locale.ROOT)) {
        in IMAGE -> FileCategory.IMAGE
        in VIDEO -> FileCategory.VIDEO
        in AUDIO -> FileCategory.AUDIO
        in DOCUMENT -> FileCategory.DOCUMENT
        in ARCHIVE -> FileCategory.ARCHIVE
        in APP -> FileCategory.APP
        else -> FileCategory.OTHER
    }
}

fun FileItemModel.category(): FileCategory = categoryOf(extension, isDirectory)

private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "avif")
private val VIDEO = setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp", "m4v", "flv")
private val AUDIO = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "amr")
private val DOCUMENT = setOf(
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "odt", "ods", "csv", "epub"
)
private val ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso")
private val APP = setOf("apk", "apks", "xapk")

// ── What the hub can show by itself ─────────────────────────────────────────

/** The viewers the Files hub has of its own; [NONE] hands the file to another app. */
enum class FileViewer { TEXT, IMAGE, AUDIO, VIDEO, PDF, NONE }

/**
 * Which of the hub's own viewers a file goes to. The kinds are narrower than [categoryOf]: a
 * .docx is a document, but nothing here can draw one.
 */
fun viewerFor(extension: String): FileViewer = when (extension.lowercase(Locale.ROOT)) {
    "pdf" -> FileViewer.PDF
    in VIEWABLE_TEXT -> FileViewer.TEXT
    in VIEWABLE_IMAGE -> FileViewer.IMAGE
    in PLAYABLE_AUDIO -> FileViewer.AUDIO
    in PLAYABLE_VIDEO -> FileViewer.VIDEO
    else -> FileViewer.NONE
}

private val VIEWABLE_TEXT = setOf(
    "txt", "md", "json", "xml", "log", "csv", "tsv", "ini", "cfg", "conf", "properties", "yaml",
    "yml", "kt", "kts", "java", "py", "js", "ts", "html", "htm", "css", "sh", "gradle", "srt",
    "nfo", "toml", "sql", "c", "h", "cpp", "rs", "go", "swift", "m3u", "vtt", "lrc"
)
private val VIEWABLE_IMAGE = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "avif")
private val PLAYABLE_AUDIO = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "amr", "mid")
private val PLAYABLE_VIDEO = setOf("mp4", "mkv", "webm", "3gp", "m4v", "mov", "ts")

// ── Searching ───────────────────────────────────────────────────────────────

/** Whether a name answers to a query, folded so Turkish case works. */
fun matchesFileQuery(name: String, query: String): Boolean {
    val needle = foldForSearch(query.trim())
    return needle.isEmpty() || foldForSearch(name).contains(needle)
}

// ── Names that do not collide ───────────────────────────────────────────────

/**
 * A name that is not already taken: "rapor.pdf" becomes "rapor (2).pdf" rather than overwriting
 * whatever was there. The number goes before the extension, where a person would put it.
 *
 * Names are compared without regard to case: shared storage on Android is case-insensitive, so
 * "Rapor.pdf" and "rapor.pdf" are the same file there, and writing the one would replace the other.
 */
fun uniqueFileName(desired: String, taken: Set<String>): String {
    val folded = taken.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
    if (desired.lowercase(Locale.ROOT) !in folded) return desired
    val dot = desired.lastIndexOf('.')
    val stem = if (dot > 0) desired.substring(0, dot) else desired
    val suffix = if (dot > 0) desired.substring(dot) else ""
    var index = 2
    while ("$stem ($index)$suffix".lowercase(Locale.ROOT) in folded && index < MAX_TRIES) index++
    return "$stem ($index)$suffix"
}

/**
 * Whether a folder may be moved or copied into a place — you cannot put a folder inside itself,
 * and doing so copies until the disk is full.
 */
fun isInsideItself(sourcePath: String, destinationPath: String): Boolean {
    val source = sourcePath.trimEnd('/')
    val destination = destinationPath.trimEnd('/')
    return destination == source || destination.startsWith("$source/")
}

/**
 * Whether a paste would put a thing back where it already is. Pasting a cut file into its own
 * folder used to rename it to "(2)"; it should do nothing and say so.
 */
fun isAlreadyThere(sourcePath: String, destinationPath: String): Boolean {
    val parent = sourcePath.trimEnd('/').substringBeforeLast('/', "")
    return parent == destinationPath.trimEnd('/')
}

/**
 * A display name from a content provider, made safe to use as a file name. A provider is any
 * app, and "../../shared_prefs/x.xml" is a perfectly legal display name: the separators go, as
 * does every "..", and what is left of nothing is [fallback].
 */
fun sanitizeFileName(name: String?, fallback: String = "file"): String {
    val cleaned = name.orEmpty()
        .replace('/', '_')
        .replace('\\', '_')
        .replace("\u0000", "")
        .filterNot { it.isISOControl() }
        .trim()
    if (cleaned.isEmpty() || cleaned.all { it == '.' }) return fallback
    return cleaned.take(MAX_NAME_LENGTH)
}

/** What a filesystem will take; the longest names are cut, not refused. */
private const val MAX_NAME_LENGTH = 200

private const val MAX_TRIES = 1000
