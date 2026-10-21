package com.serkantkn.zeta.ui.screens.news

import com.serkantkn.zeta.util.Pro
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.model.NewsTopic
import com.serkantkn.zeta.data.repository.NewsPresets
import com.serkantkn.zeta.ui.components.MetroSubScreen
import com.serkantkn.zeta.ui.components.MetroSwitchRow
import com.serkantkn.zeta.ui.components.MetroTextField
import com.serkantkn.zeta.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zeta.ui.components.WpBarAction
import com.serkantkn.zeta.ui.components.ZuneWidePaneHeader
import com.serkantkn.zeta.ui.components.bottomBarHeight
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneDimens
import java.net.URL
import java.util.Locale

/**
 * What the news hub follows: the Google News topics that stand as headings, the ready-made
 * feeds, and the user's own added by address. A sub-page over the hub on a phone, the pane on a
 * tablet.
 */
@Composable
internal fun NewsSettingsScreen(
    viewModel: NewsHubViewModel,
    inPane: Boolean,
    onClose: () -> Unit,
    barModifier: Modifier = Modifier
) {
    val title = stringResource(R.string.news_settings)
    if (inPane) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ZuneWidePaneHeader(section = title, onClose = onClose)
                NewsSettingsBody(
                    viewModel = viewModel,
                    contentPadding = PaddingValues(
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = 32.dp,
                        top = 8.dp,
                        bottom = bottomBarHeight() + 24.dp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            WindowsPhoneBottomBar(
                actions = listOf(WpBarAction(Icons.Default.Check, stringResource(R.string.common_done)) { onClose() }),
                modifier = barModifier.align(Alignment.BottomCenter)
            )
        }
    } else {
        MetroSubScreen(
            breadcrumb = stringResource(HubType.NEWS.titleRes).uppercase(Locale.getDefault()),
            title = title,
            onClose = onClose
        ) { padding ->
            NewsSettingsBody(viewModel = viewModel, contentPadding = padding, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewsSettingsBody(
    viewModel: NewsHubViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val topics by viewModel.topics.collectAsState()
    val enabledPresets by viewModel.enabledPresetIds.collectAsState()
    val custom by viewModel.customSources.collectAsState()
    val adding by viewModel.addingSource.collectAsState()
    val addError by viewModel.addError.collectAsState()
    var address by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()
    // The box for a new feed is at the very end: when the keyboard comes up for it, the page
    // follows it up so the box and its button stay above the keys.
    val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(keyboardUp, addError, adding) {
        if (keyboardUp || addError != null || adding) scroll.animateScrollTo(scroll.maxValue)
    }
    val add = { viewModel.addSource(address) { address = "" } }
    val language = remember { Locale.getDefault().language }
    val presets = remember(language) { NewsPresets.forLanguage(language) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(scroll)
            .padding(contentPadding)
    ) {
        SettingsHeading(stringResource(R.string.news_settings_topics))
        SettingsHint(stringResource(R.string.news_settings_topics_hint))
        NewsTopic.entries.filter { it != NewsTopic.TOP }.forEach { topic ->
            MetroSwitchRow(
                title = stringResource(topic.titleRes),
                checked = topic in topics,
                onCheckedChange = { viewModel.setTopicShown(topic, it) }
            )
        }

        SettingsHeading(stringResource(R.string.news_settings_sources))
        SettingsHint(stringResource(R.string.news_settings_sources_hint))
        // The sites' own feeds are Pro's; Google News's topics above are not.
        com.serkantkn.zeta.ui.components.ProLocked {
        Column {
        presets.forEach { preset ->
            MetroSwitchRow(
                title = preset.name,
                subtitle = hostOf(preset.url),
                checked = preset.id in enabledPresets,
                onCheckedChange = { viewModel.setPresetEnabled(preset.id, it) }
            )
        }
        custom.forEach { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = hostOf(source.url),
                        style = MaterialTheme.typography.labelMedium,
                        color = zuneColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.news_remove_source),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .clickable { viewModel.removeSource(source.id) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }
        }

        SettingsHeading(stringResource(R.string.news_add_source))
        SettingsHint(stringResource(R.string.news_add_source_hint))
        MetroTextField(
            value = address,
            onValueChange = {
                address = it
                viewModel.clearAddError()
            },
            placeholder = stringResource(R.string.news_add_source_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { add() }),
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            MetroButton(
                label = stringResource(R.string.news_add),
                enabled = !adding && address.isNotBlank(),
                onClick = { add() }
            )
            if (adding) {
                Text(
                    text = stringResource(R.string.news_adding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
        addError?.let { error ->
            Text(
                text = stringResource(error),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.accentColor,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        }
        }

        Text(
            text = stringResource(R.string.news_settings_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = zuneColors.textDim,
            modifier = Modifier.padding(top = 32.dp)
        )
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 26.sp),
        color = LocalZuneColors.current.accentColor,
        modifier = Modifier.padding(top = 24.dp)
    )
}

@Composable
private fun SettingsHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalZuneColors.current.textMuted,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
    )
}

private fun hostOf(url: String): String =
    runCatching { URL(url).host.removePrefix("www.") }.getOrDefault(url)
