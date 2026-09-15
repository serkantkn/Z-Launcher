package com.serkantkn.zunelauncher.ui.screens.browser

import com.serkantkn.zunelauncher.data.model.BrowserHistory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Puts the history under day headings.
 *
 * A flat list of a hundred addresses tells you nothing about when you were there; a run under
 * "today" or "3 April 2026" does. Kept as a plain function so the rule — consecutive entries from
 * the same day share a heading, and the newest day comes first — can be tested without a screen.
 */
internal fun groupHistoryByDay(
    history: List<BrowserHistory>,
    todayLabel: String,
    yesterdayLabel: String,
    now: Long = System.currentTimeMillis()
): List<Pair<String, List<BrowserHistory>>> {
    if (history.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<String, MutableList<BrowserHistory>>>()
    history.forEach { entry ->
        val label = dayLabel(entry.timestamp, now, todayLabel, yesterdayLabel)
        val last = result.lastOrNull()
        if (last != null && last.first == label) {
            last.second += entry
        } else {
            result += label to mutableListOf(entry)
        }
    }
    return result.map { (label, entries) -> label to entries.toList() }
}

private fun dayLabel(
    timestamp: Long,
    now: Long,
    todayLabel: String,
    yesterdayLabel: String
): String {
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = today.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDelta = today.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDelta == 0 -> todayLabel
        sameYear && dayDelta == 1 -> yesterdayLabel
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
