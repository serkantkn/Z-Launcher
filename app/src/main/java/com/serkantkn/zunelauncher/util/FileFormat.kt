package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.FileItemModel
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
 */
fun uniqueFileName(desired: String, taken: Set<String>): String {
    if (desired !in taken) return desired
    val dot = desired.lastIndexOf('.')
    val stem = if (dot > 0) desired.substring(0, dot) else desired
    val suffix = if (dot > 0) desired.substring(dot) else ""
    var index = 2
    while ("$stem ($index)$suffix" in taken && index < MAX_TRIES) index++
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

private const val MAX_TRIES = 1000
