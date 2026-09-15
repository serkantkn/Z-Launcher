package com.serkantkn.zunelauncher.ui.screens.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.WorldCity
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.foldForSearch
import com.serkantkn.zunelauncher.util.formatOffset
import com.serkantkn.zunelauncher.util.selectableZoneIds
import com.serkantkn.zunelauncher.util.zoneOffsetMinutes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.MetroRule
import com.serkantkn.zunelauncher.ui.components.MetroSubScreen
import com.serkantkn.zunelauncher.ui.components.metroDigitStyle

/**
 * Choosing a city.
 *
 * The old picker offered nine cities from a hard-coded list with radio buttons beside them. This
 * one offers every zone the phone knows — several hundred — and finds them the way the app list
 * finds apps, folding Turkish letters so "istanbul" and "İstanbul" are the same word. The dozen
 * people actually look for sit at the top until something is typed.
 */
@Composable
internal fun CityPickerScreen(
    existing: List<WorldCity>,
    now: Long,
    onPick: (WorldCity) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val taken = remember(existing) { existing.map { it.timeZoneId }.toSet() }

    // Resolving several hundred names is not free, so it happens once and not per keystroke.
    val everywhere = remember(context) {
        selectableZoneIds(TimeZone.getAvailableIDs()).map { id ->
            val city = WorldCity(id)
            city to foldForSearch(city.searchText(context))
        }.sortedBy { it.second }
    }

    val results = remember(query, everywhere, taken) {
        val needle = foldForSearch(query.trim())
        if (needle.isEmpty()) {
            emptyList()
        } else {
            everywhere.filter { it.second.contains(needle) }.map { it.first }.take(MAX_RESULTS)
        }
    }

    val popular = remember(taken) { WorldCity.POPULAR.filterNot { it.timeZoneId in taken } }

    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_clock),
        title = stringResource(R.string.clock_add_city),
        onClose = onClose,
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            ZuneSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.clock_search_cities),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding
            ) {
                if (query.isBlank()) {
                    item { MetroRule(title = stringResource(R.string.clock_popular_cities)) }
                    items(popular, key = { it.timeZoneId }) { city ->
                        CityPickerRow(city = city, now = now, onClick = { onPick(city) })
                    }
                } else if (results.isEmpty()) {
                    item { MetroEmpty(message = stringResource(R.string.clock_no_city_found)) }
                } else {
                    items(results, key = { it.timeZoneId }) { city ->
                        CityPickerRow(
                            city = city,
                            now = now,
                            alreadyAdded = city.timeZoneId in taken,
                            onClick = { if (city.timeZoneId !in taken) onPick(city) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CityPickerRow(
    city: WorldCity,
    now: Long,
    alreadyAdded: Boolean = false,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val timeFormat = remember(city.timeZoneId) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(city.timeZoneId)
        }
    }
    val offset = formatOffset(zoneOffsetMinutes(now, city.timeZoneId))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !alreadyAdded, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = city.cityName(context),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = if (alreadyAdded) zuneColors.textDim else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    city.countryName(context).takeIf { it.isNotBlank() },
                    offset
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (alreadyAdded) {
                stringResource(R.string.clock_city_already_added)
            } else {
                timeFormat.format(Date(now))
            },
            style = if (alreadyAdded) {
                MaterialTheme.typography.labelMedium
            } else {
                metroDigitStyle(fontSize = 22.sp, weight = FontWeight.Light, letterSpacing = (-1).sp)
            },
            color = if (alreadyAdded) zuneColors.textDim else MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Enough to scroll through; a query that matches more than this is not a query yet. */
private const val MAX_RESULTS = 80
