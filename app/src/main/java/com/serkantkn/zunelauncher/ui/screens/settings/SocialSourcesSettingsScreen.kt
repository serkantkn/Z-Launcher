package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.model.SocialKind
import com.serkantkn.zunelauncher.data.model.SocialSource
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.toSquareImageBitmap
import java.util.Locale

/**
 * Which apps the Social hub listens to.
 *
 * The list starts from the apps the launcher knows are social, adds anything switched on by hand,
 * and offers — separately, never automatically — any app that has been sending things that look
 * like messages. Whatever is chosen here always wins over the launcher's own guess.
 */
@Composable
internal fun SocialSourcesSettingsScreen(
    sources: List<SocialSource>,
    otherApps: List<AppInfo>,
    onToggle: (String, Boolean) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 32.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal
                    )
            ) {
                Text(
                    text = stringResource(R.string.common_settings),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.settings_social_sources),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 40.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
            }
            SourceList(
                sources = sources,
                otherApps = otherApps,
                onToggle = onToggle,
                onReset = onReset,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
            )
        }
    }
}

@Composable
private fun SourceList(
    sources: List<SocialSource>,
    otherApps: List<AppInfo>,
    onToggle: (String, Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val appRepository = remember(context) { context.appContainer.appRepository }
    var showAllApps by remember { mutableStateOf(false) }

    val suggested = sources.filter { it.isSuggested }
    val known = sources.filterNot { it.isSuggested }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
    ) {
        if (suggested.isNotEmpty()) {
            item(key = "suggested_rule") {
                SourceRule(text = stringResource(R.string.social_suggested))
            }
            item(key = "suggested_note") {
                Text(
                    text = stringResource(R.string.social_suggested_note),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(suggested, key = { "s_${it.app.packageName}" }) { source ->
                SourceRow(
                    source = source,
                    icon = appRepository.getAppIcon(source.app.packageName),
                    onToggle = { on -> onToggle(source.app.packageName, on) }
                )
            }
        }

        item(key = "sources_rule") {
            SourceRule(text = stringResource(R.string.social_sources))
        }

        if (known.isEmpty()) {
            item(key = "none") {
                Text(
                    text = stringResource(R.string.social_no_sources),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(known, key = { "k_${it.app.packageName}" }) { source ->
            SourceRow(
                source = source,
                icon = appRepository.getAppIcon(source.app.packageName),
                onToggle = { on -> onToggle(source.app.packageName, on) }
            )
        }

        item(key = "reset") {
            Text(
                text = stringResource(R.string.social_reset_sources),
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.accentColor,
                modifier = Modifier
                    .clickable(onClick = onReset)
                    .padding(top = 16.dp, bottom = 4.dp)
            )
        }

        // ── Anything else on the phone, only when asked for ──
        item(key = "others_rule") {
            Text(
                text = stringResource(
                    if (showAllApps) R.string.social_hide_other_apps else R.string.social_add_other_app
                ),
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.accentColor,
                modifier = Modifier
                    .clickable { showAllApps = !showAllApps }
                    .padding(top = 12.dp, bottom = 4.dp)
            )
        }

        if (showAllApps) {
            items(otherApps, key = { "o_${it.packageName}" }) { app ->
                SourceRow(
                    source = SocialSource(
                        app = app,
                        kind = null,
                        isOn = false,
                        isSuggested = false,
                        waiting = 0
                    ),
                    icon = appRepository.getAppIcon(app.packageName),
                    onToggle = { on -> onToggle(app.packageName, on) }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SocialSource,
    icon: android.graphics.drawable.Drawable?,
    onToggle: (Boolean) -> Unit
) {
    val zuneColors = LocalZuneColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!source.isOn) }
            .padding(vertical = 8.dp)
    ) {
        if (icon != null) {
            val bitmap = remember(icon) { icon.toSquareImageBitmap() }
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Box(
                modifier = Modifier.size(40.dp).background(zuneColors.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = source.app.label.take(1).uppercase(Locale.getDefault()),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.app.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                source.waiting > 0 -> stringResource(R.string.social_waiting, source.waiting)
                source.kind == SocialKind.MESSAGING -> stringResource(R.string.social_kind_messaging)
                source.kind == SocialKind.NETWORK -> stringResource(R.string.social_kind_network)
                else -> null
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (source.waiting > 0) zuneColors.accentColor else zuneColors.textDim
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        WindowsPhoneSwitch(checked = source.isOn, onCheckedChange = onToggle)
    }
}


/** The Zune section rule, kept here so the settings page does not depend on the hub's own parts. */
@Composable
private fun SourceRule(text: String) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = ZuneDimens.SpacingLg, bottom = ZuneDimens.SpacingSm)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = zuneColors.accentColor
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(zuneColors.accentColor.copy(alpha = 0.35f))
        )
    }
}
