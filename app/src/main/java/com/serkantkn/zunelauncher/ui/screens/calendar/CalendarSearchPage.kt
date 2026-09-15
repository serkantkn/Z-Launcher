package com.serkantkn.zunelauncher.ui.screens.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.util.startOfDay

/**
 * Finding something in the diary.
 *
 * The old page searched with a plain case-insensitive `contains`, which in Turkish is not
 * case-insensitive at all: "sınav" does not find "Sınav", because the capital of ı is I and the
 * capital of i is İ and Java's idea of "ignore case" knows neither. This folds the way the app
 * list folds, so the two are the same word.
 */
@Composable
internal fun CalendarSearchPage(
    query: String,
    results: List<CalendarEvent>,
    now: Long,
    onQueryChange: (String) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
    onLongPress: (CalendarEvent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    // Nearest first, and the things still to come before the things already gone.
    val ordered = remember(results, now) {
        results.sortedWith(compareBy({ it.endMillis < now }, { kotlin.math.abs(it.startMillis - now) }))
    }
    val grouped = remember(ordered) { ordered.groupBy { startOfDay(it.startMillis) } }

    Column(modifier = modifier.fillMaxSize()) {
        ZuneSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.cal_search_hint),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            if (ordered.isEmpty()) {
                item {
                    MetroEmpty(
                        message = stringResource(
                            if (query.isBlank()) R.string.cal_no_events else R.string.cal_no_search_results
                        )
                    )
                }
            }

            grouped.forEach { (day, dayEvents) ->
                item(key = "sd_$day") {
                    DayHeading(dayMillis = day, nowMillis = now, count = dayEvents.size)
                }
                dayEvents.forEach { event ->
                    item(key = "se_${event.id}") {
                        EventRow(
                            event = event,
                            onClick = { onOpen(event) },
                            onLongClick = { onLongPress(event) }
                        )
                    }
                }
            }
        }
    }
}
