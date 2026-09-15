package com.serkantkn.zunelauncher.ui.screens.people

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A birthday as the address book keeps it, written out the way people say it.
 *
 * Contacts stores "1986-04-03", and "--04-03" for somebody whose year nobody knows — a real shape
 * the provider uses, not an edge case. Anything else is handed back untouched rather than mangled,
 * because other apps are free to write what they like in that column.
 */
internal fun formatBirthday(raw: String, locale: Locale = Locale.getDefault()): String {
    if (raw.isBlank()) return raw
    val withoutYear = raw.startsWith("--")
    val inPattern = if (withoutYear) "--MM-dd" else "yyyy-MM-dd"
    val outPattern = if (withoutYear) "d MMMM" else "d MMMM yyyy"
    return try {
        val parsed = SimpleDateFormat(inPattern, locale).apply { isLenient = false }.parse(raw)
            ?: return raw
        SimpleDateFormat(outPattern, locale).format(parsed)
    } catch (e: Exception) {
        raw
    }
}
