package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.AppInfo
import java.text.Normalizer
import java.util.Locale

/**
 * Finding an app by typing at it.
 *
 * Two things make this harder than `contains`. The first is Turkish: nobody reaches for the ş when
 * they are looking for an app in a hurry, and Android's own case rules turn I into ı and İ into i
 * depending on which language the phone happens to be in. The second is that a list of matches is
 * not an answer — "ma" matching thirty apps is only useful if the one whose name *starts* with it
 * is at the top.
 *
 * So every name is folded down to plain letters first, and then matches are scored rather than
 * merely collected.
 */

private val TURKISH_FOLD = mapOf(
    'ç' to 'c', 'Ç' to 'c',
    'ğ' to 'g', 'Ğ' to 'g',
    'ı' to 'i', 'I' to 'i', 'İ' to 'i',
    'ö' to 'o', 'Ö' to 'o',
    'ş' to 's', 'Ş' to 's',
    'ü' to 'u', 'Ü' to 'u'
)

/**
 * Strips a name down to what somebody would actually type: lower case, no accents, no Turkish
 * letters that need a long press to reach.
 */
fun foldForSearch(text: String): String {
    if (text.isEmpty()) return ""
    val mapped = buildString(text.length) {
        text.forEach { char -> append(TURKISH_FOLD[char] ?: char) }
    }
    // Everything else — é, ñ, å — loses its mark the general way.
    val decomposed = Normalizer.normalize(mapped, Normalizer.Form.NFD)
    val stripped = buildString(decomposed.length) {
        decomposed.forEach { char ->
            if (Character.getType(char) != Character.NON_SPACING_MARK.toInt()) append(char)
        }
    }
    // Locale.ROOT, not the phone's: the Turkish letters are already gone and Turkish rules here
    // would turn a plain I back into ı.
    return stripped.lowercase(Locale.ROOT)
}

/**
 * How well an app answers [query]; 0 means it does not.
 *
 * The order the numbers encode, best first: the whole name, the name's beginning, the beginning of
 * any word in the name, the first letters of the words taken together ("gps" for Google Play
 * Store), somewhere inside the name, and last of all the package name — which nobody types on
 * purpose but which is the only way to find an app whose label says something else entirely.
 */
fun matchScore(label: String, packageName: String, query: String): Int {
    val needle = foldForSearch(query).trim()
    if (needle.isEmpty()) return 0
    val name = foldForSearch(label)

    if (name == needle) return 1000
    if (name.startsWith(needle)) return 900
    val words = name.split(' ', '-', '_', '.', '&').filter { it.isNotEmpty() }
    if (words.any { it.startsWith(needle) }) return 800
    if (words.size > 1 && words.joinToString("") { it.first().toString() }.startsWith(needle)) return 700
    if (name.contains(needle)) return 600
    if (foldForSearch(packageName).contains(needle)) return 300
    return 0
}

/**
 * The apps that answer [query], best first. A blank query matches nothing here: the list has its
 * own order when nobody is searching, and this should not quietly become that order.
 */
fun rankApps(apps: List<AppInfo>, query: String): List<AppInfo> {
    if (query.isBlank()) return emptyList()
    return apps
        .map { app -> app to matchScore(app.label, app.packageName, query) }
        .filter { (_, score) -> score > 0 }
        // Same score, same order every time: by name, so the list does not shuffle as you type.
        .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }
            .thenBy { foldForSearch(it.first.label) })
        .map { (app, _) -> app }
}

/**
 * Which letter an app files under, in the reader's own alphabet.
 *
 * Turkish sorts Ç after C and Ş after S rather than folding them together, so the heading keeps
 * the letter the name actually starts with; only names that start with something that is not a
 * letter at all — numbers, symbols, emoji — fall under "#".
 */
fun sectionLetterOf(label: String, locale: Locale = Locale.getDefault()): Char {
    val first = label.trim().firstOrNull() ?: return '#'
    if (!first.isLetter()) return '#'
    return first.uppercase(locale).first()
}
