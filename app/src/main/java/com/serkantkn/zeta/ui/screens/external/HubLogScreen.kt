package com.serkantkn.zeta.ui.screens.external

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.hubs.HubDirectory
import com.serkantkn.zeta.data.hubs.HubLog
import com.serkantkn.zeta.data.model.ExternalHub
import com.serkantkn.zeta.ui.components.MetroEmpty
import com.serkantkn.zeta.ui.components.MetroRule
import com.serkantkn.zeta.ui.components.MetroSubScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Developer mode's log (PLATFORM.md): what Zeta and other apps' hubs said to each other, newest
 * first, with what was wrong in each description.
 */
@Composable
fun HubLogScreen(onClose: () -> Unit) {
    val entries by HubLog.entries.collectAsState()
    val installed by HubDirectory.installed.collectAsState()
    val zuneColors = LocalZuneColors.current
    val time = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }
    MetroSubScreen(
        breadcrumb = stringResource(R.string.common_settings),
        title = stringResource(R.string.settings_hub_log),
        onClose = onClose
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            MetroRule(
                title = stringResource(R.string.settings_hub_log_count, entries.size),
                actionLabel = stringResource(R.string.settings_hub_log_clear),
                onAction = { HubLog.clear() }
            )
            if (entries.isEmpty()) {
                MetroEmpty(message = stringResource(R.string.settings_hub_log_empty))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
                items(entries.asReversed()) { entry ->
                    val name = ExternalHub.fromKey(entry.hub)
                        ?.let { hub -> installed.firstOrNull { it.hub == hub }?.label ?: hub.packageName }
                        ?: entry.hub
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "${time.format(Date(entry.at))} · $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = zuneColors.textDim
                        )
                        Text(
                            text = entry.what,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = if (entry.problem) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground
                        )
                        if (entry.detail.isNotBlank()) {
                            Text(
                                text = entry.detail,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = zuneColors.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
