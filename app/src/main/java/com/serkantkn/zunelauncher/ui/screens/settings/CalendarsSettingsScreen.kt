package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarInfo
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.MetroRule
import com.serkantkn.zunelauncher.ui.components.MetroSubScreen
import com.serkantkn.zunelauncher.ui.screens.calendar.parseColor
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * Which of the phone's calendars the hub shows.
 *
 * A phone usually has more calendars than anybody wants in their agenda — a birthdays feed and a
 * public holidays feed between them can fill a month with things nobody is going to attend. Each
 * one is listed with its own colour, which is the colour it will have in the hub, and can be
 * switched off without touching what the phone's own calendar app shows.
 */
@Composable
internal fun CalendarsSettingsScreen(
    calendars: List<CalendarInfo>,
    visibleIds: Set<Long>?,
    defaultCalendarId: Long?,
    onToggle: (Long, Boolean) -> Unit,
    onSetDefault: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedDefault = defaultCalendarId
        ?: calendars.firstOrNull { it.isWritable && it.isPrimary }?.id
        ?: calendars.firstOrNull { it.isWritable }?.id
        ?: CalendarInfo.LOCAL_ID

    MetroSubScreen(
        breadcrumb = stringResource(R.string.common_settings),
        title = stringResource(R.string.cal_calendars),
        onClose = onClose,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding
        ) {
            if (calendars.isEmpty()) {
                item { MetroEmpty(message = stringResource(R.string.cal_no_calendars)) }
            }

            calendars.forEach { info ->
                item(key = "show_${info.id}") {
                    CalendarRow(
                        info = info,
                        checked = visibleIds == null || info.id in visibleIds,
                        onCheckedChange = { onToggle(info.id, it) }
                    )
                }
            }

            if (calendars.any { it.isWritable }) {
                item { MetroRule(title = stringResource(R.string.cal_default_calendar_hint)) }
                calendars.filter { it.isWritable }.forEach { info ->
                    item(key = "default_${info.id}") {
                        SettingChoiceRow(
                            title = info.displayName.ifBlank { info.accountName },
                            subtitle = info.accountName,
                            selected = resolvedDefault == info.id,
                            onClick = { onSetDefault(info.id) }
                        )
                    }
                }
                item {
                    SettingChoiceRow(
                        title = stringResource(R.string.cal_local_calendar),
                        subtitle = "",
                        selected = resolvedDefault == CalendarInfo.LOCAL_ID,
                        onClick = { onSetDefault(CalendarInfo.LOCAL_ID) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CalendarRow(
    info: CalendarInfo,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .background(parseColor(info.colorHex, zuneColors.accentColor))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.displayName.ifBlank { info.accountName },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = info.accountName,
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        WindowsPhoneSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
