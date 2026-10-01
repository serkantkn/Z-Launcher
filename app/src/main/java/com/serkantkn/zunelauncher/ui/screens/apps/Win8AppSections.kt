package com.serkantkn.zunelauncher.ui.screens.apps

import com.serkantkn.zunelauncher.data.model.AppInfo

/**
 * The three orders the Windows 8.1 Apps view offered from the little menu beside its title.
 */
enum class Win8AppsSort { BY_NAME, BY_DATE, BY_USAGE }

/** When an app arrived, as the "by date installed" order groups it. */
enum class Win8DateBucket { TODAY, THIS_WEEK, THIS_MONTH, EARLIER }

/**
 * One group of the Apps view: a heading and the apps under it, which the screen then stands in
 * columns. Exactly one of [letter], [bucket] and [usage] says what the heading is; [usage] is
 * true for the apps actually opened and false for the rest when the list is ordered by use.
 */
data class Win8AppSection(
    val id: String,
    val apps: List<AppInfo>,
    val letter: Char? = null,
    val bucket: Win8DateBucket? = null,
    val usage: Boolean? = null
)

/**
 * The sections of the Apps view, in the order asked for. Pure, so it can be tested without a
 * screen: [grouped] is the alphabetical grouping the list already keeps, [usage] the time in
 * front by package (empty until the phone is allowed to say), and [now] the moment to date
 * installs against.
 */
fun buildWin8AppSections(
    sort: Win8AppsSort,
    grouped: Map<Char, List<AppInfo>>,
    usage: Map<String, Long>,
    now: Long,
    startOfToday: Long
): List<Win8AppSection> = when (sort) {
    Win8AppsSort.BY_NAME -> grouped.map { (letter, apps) ->
        Win8AppSection(id = "letter_$letter", apps = apps, letter = letter)
    }

    Win8AppsSort.BY_DATE -> {
        val all = grouped.values.flatten()
        val buckets = all.groupBy { app -> dateBucketOf(app.firstInstallTime, now, startOfToday) }
        Win8DateBucket.entries.mapNotNull { bucket ->
            val members = buckets[bucket] ?: return@mapNotNull null
            Win8AppSection(
                id = "date_${bucket.name}",
                apps = members.sortedByDescending { it.firstInstallTime },
                bucket = bucket
            )
        }
    }

    Win8AppsSort.BY_USAGE -> {
        val all = grouped.values.flatten()
        val used = all.filter { (usage[it.packageName] ?: 0L) > 0L }
            .sortedByDescending { usage[it.packageName] ?: 0L }
        val rest = all.filterNot { (usage[it.packageName] ?: 0L) > 0L }
        buildList {
            if (used.isNotEmpty()) add(Win8AppSection(id = "usage_used", apps = used, usage = true))
            if (rest.isNotEmpty()) add(Win8AppSection(id = "usage_rest", apps = rest, usage = false))
        }
    }
}

/** Which bucket an install time falls in. An app the phone will not date counts as old. */
fun dateBucketOf(installedAt: Long, now: Long, startOfToday: Long): Win8DateBucket = when {
    installedAt <= 0L -> Win8DateBucket.EARLIER
    installedAt >= startOfToday -> Win8DateBucket.TODAY
    installedAt >= now - WEEK_MILLIS -> Win8DateBucket.THIS_WEEK
    installedAt >= now - MONTH_MILLIS -> Win8DateBucket.THIS_MONTH
    else -> Win8DateBucket.EARLIER
}

/**
 * Stands a section's apps in columns [rows] deep, filling each column top to bottom before the
 * next, the way the Apps view read: down, then across.
 */
fun <T> columnsOf(items: List<T>, rows: Int): List<List<T>> {
    val depth = rows.coerceAtLeast(1)
    return items.chunked(depth)
}

private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
private const val MONTH_MILLIS = 30L * 24 * 60 * 60 * 1000
