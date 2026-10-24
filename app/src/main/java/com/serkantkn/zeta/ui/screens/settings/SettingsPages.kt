package com.serkantkn.zeta.ui.screens.settings

import com.serkantkn.zeta.ui.theme.findActivity
import com.serkantkn.zeta.util.AppLanguage
import com.serkantkn.zeta.R
import com.serkantkn.zeta.util.ActionCenterPermissions
import androidx.compose.ui.res.stringResource
import com.serkantkn.zeta.util.ZuneLog
import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.SolidColor
import com.serkantkn.zeta.ui.components.ZuneDialogButton
import com.serkantkn.zeta.ui.components.ZuneFlipDialog
import com.serkantkn.zeta.data.datastore.KeyboardDataStore
import com.serkantkn.zeta.data.model.*
import com.serkantkn.zeta.data.model.SearchEngine
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.data.model.TileIconStyle
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.items

/**
 * The look tab: a list of the pages it was split into, over a preview of what they add up to.
 *
 * It used to be every look setting in one scroll, which meant scrolling past nine headings to
 * reach the tenth. Each heading is now its own page; what stays here is the preview — so a change
 * can still be seen — and a line under each entry saying what it is currently set to.
 */
@Composable
internal fun LookSettingsPage(
    viewModel: SettingsViewModel,
    themeMode: ThemeMode,
    accentColor: AccentColor,
    onOpenPage: (LookPage) -> Unit
) {
    val customWallpaperPath by viewModel.customWallpaperPath.collectAsState()
    val hubBackgroundMode by viewModel.hubBackgroundMode.collectAsState()
    val hubBackgroundOpacity by viewModel.hubBackgroundOpacity.collectAsState()
    val solidBackgroundEnabled by viewModel.solidBackgroundEnabled.collectAsState()
    val customThemeColor by viewModel.customThemeColor.collectAsState()
    val dynamicThemeColor by viewModel.dynamicThemeColor.collectAsState()
    val tileCornerStyle by viewModel.tileCornerStyle.collectAsState()
    val tileSpacing by viewModel.tileSpacing.collectAsState()
    val homeScreenLayout by viewModel.homeScreenLayout.collectAsState()
    // A television is Media Center and nothing else: its own Start and blue, no tiles or icons to
    // dress, no wallpaper behind the hubs - and no phone's Start to preview.
    val isTv = com.serkantkn.zeta.ui.theme.LocalIsTv.current
    val tvLeftOut = if (isTv) setOf(
        LookPage.WALLPAPER, LookPage.HUB_BACKGROUND, LookPage.START_SCREEN, LookPage.TILES, LookPage.ICONS
    ) else emptySet()
    // A TV's two designs have their own colours: the theme and the accent colour change nothing there.
    val vistaLeftOut = if (isTv) setOf(LookPage.THEME) else emptySet()

    SettingsLazyColumn {
        if (!isTv) item(key = "preview") {
            val tileOpacity by viewModel.tileOpacity.collectAsState()
            val tileBlur by viewModel.tileBlur.collectAsState()
            val tileInk by viewModel.tileInk.collectAsState()
            val zuneGlow by viewModel.zuneGlow.collectAsState()
            LookPreviewCard(
                themeMode = themeMode,
                accentColor = accentColor,
                customThemeColor = customThemeColor,
                dynamicThemeColor = dynamicThemeColor,
                solidBackgroundEnabled = solidBackgroundEnabled,
                customWallpaperPath = customWallpaperPath,
                tileCornerStyle = tileCornerStyle,
                tileSpacing = tileSpacing,
                tileOpacity = tileOpacity,
                tileBlur = tileBlur,
                tileInk = tileInk,
                hubBackgroundOpacity = hubBackgroundOpacity,
                hubBackgroundMode = hubBackgroundMode,
                homeScreenLayout = homeScreenLayout,
                zuneGlow = zuneGlow
            )
        }

        if (isTv) item(key = "tv_design") { TvDesignGroup() }

        // One item: the list puts a group's gap between its items, and these rows are one group.
        item(key = "look_pages") {
            Column {
                LookPage.entries.filter { it !in tvLeftOut && it !in vistaLeftOut }.forEach { page ->
                    SystemSettingRow(
                        title = stringResource(page.titleRes),
                        subtitle = lookPageSummary(page, viewModel),
                        onClick = { onOpenPage(page) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun HubSettingsPage(
    viewModel: SettingsViewModel,
    directCallEnabled: Boolean,
    onDirectCallChanged: (Boolean) -> Unit,
    onOpenSocialSources: () -> Unit = {},
    onOpenCalendars: () -> Unit = {},
    onOpenHubLog: () -> Unit = {}
) {
    val socialHubLayout by viewModel.socialHubLayout.collectAsState()
    val socialSourceList by viewModel.socialSourceList.collectAsState()
    val hubOrder by viewModel.hubOrder.collectAsState()
    var showHistoryClearToast by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showHistoryClearToast) {
        LaunchedEffect(Unit) {
            android.widget.Toast.makeText(context, context.getString(R.string.settings_browser_history_cleared), android.widget.Toast.LENGTH_SHORT).show()
            showHistoryClearToast = false
        }
    }

    // A television has none of these: the hubs, the tour and the permissions they are for.
    val isTv = com.serkantkn.zeta.ui.theme.LocalIsTv.current
    SettingsLazyColumn {
        if (!isTv) item(key = "social") {
            SettingGroup(title = stringResource(R.string.settings_group_social)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_social_sources),
                        subtitle = stringResource(
                            R.string.settings_social_sources_count,
                            socialSourceList.count { it.isOn }
                        ),
                        selected = true,
                        onClick = onOpenSocialSources
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_social_timeline),
                        subtitle = stringResource(R.string.settings_social_timeline_sub),
                        selected = socialHubLayout == SocialHubLayout.TIMELINE,
                        onClick = { viewModel.setSocialHubLayout(SocialHubLayout.TIMELINE) }
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_social_grouped),
                        subtitle = stringResource(R.string.settings_social_grouped_sub),
                        selected = socialHubLayout == SocialHubLayout.GROUPED,
                        onClick = { viewModel.setSocialHubLayout(SocialHubLayout.GROUPED) }
                    )
                }
            }
        }

        if (!isTv) item(key = "calendar") {
            val calendars by viewModel.calendars.collectAsState()
            val visible by viewModel.visibleCalendars.collectAsState()
            val reminder by viewModel.calendarReminderMinutes.collectAsState()
            val weekStart by viewModel.calendarWeekStart.collectAsState()
            val showPast by viewModel.calendarShowPast.collectAsState()

            LaunchedEffect(Unit) { viewModel.refreshCalendars() }

            SettingGroup(title = stringResource(R.string.settings_calendar_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.cal_calendars),
                        subtitle = stringResource(
                            R.string.cal_calendar_count,
                            visible?.size ?: calendars.size,
                            calendars.size
                        ),
                        selected = true,
                        onClick = onOpenCalendars
                    )
                    Text(
                        text = stringResource(R.string.cal_default_reminder),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted
                    )
                    (listOf(-1) + com.serkantkn.zeta.data.model.CalendarEvent.REMINDER_CHOICES)
                        .forEach { minutes ->
                            SettingChoiceRow(
                                title = when {
                                    minutes < 0 -> stringResource(R.string.cal_no_reminder)
                                    minutes == 0 -> stringResource(R.string.cal_reminder_at_start)
                                    minutes >= 1440 -> stringResource(R.string.cal_reminder_days, minutes / 1440)
                                    minutes >= 60 -> stringResource(R.string.clock_hours_short, minutes / 60)
                                    else -> stringResource(R.string.clock_minutes_short, minutes)
                                },
                                subtitle = "",
                                selected = (reminder ?: -1) == minutes,
                                onClick = { viewModel.setCalendarReminderMinutes(minutes.takeIf { it >= 0 }) }
                            )
                        }
                    Text(
                        text = stringResource(R.string.cal_week_start),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted
                    )
                    listOf(
                        0 to stringResource(R.string.cal_week_start_locale),
                        java.util.Calendar.MONDAY to com.serkantkn.zeta.ui.screens.clock.shortDayName(java.util.Calendar.MONDAY),
                        java.util.Calendar.SUNDAY to com.serkantkn.zeta.ui.screens.clock.shortDayName(java.util.Calendar.SUNDAY)
                    ).forEach { (day, label) ->
                        SettingChoiceRow(
                            title = label,
                            subtitle = "",
                            selected = weekStart == day,
                            onClick = { viewModel.setCalendarWeekStart(day) }
                        )
                    }
                    SettingSwitchRow(
                        title = stringResource(R.string.cal_show_past),
                        subtitle = stringResource(R.string.cal_show_past_hint),
                        checked = showPast,
                        onCheckedChange = { viewModel.setCalendarShowPast(it) }
                    )
                }
            }
        }

        item(key = "clock") {
            val snoozeMinutes by viewModel.alarmSnoozeMinutes.collectAsState()
            val autoSilence by viewModel.alarmAutoSilenceMinutes.collectAsState()
            val showSeconds by viewModel.clockShowSeconds.collectAsState()

            SettingGroup(title = stringResource(R.string.settings_clock_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.clock_snooze_length),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted
                    )
                    listOf(5, 10, 15, 20, 30).forEach { minutes ->
                        SettingChoiceRow(
                            title = stringResource(R.string.clock_minutes_short, minutes),
                            subtitle = "",
                            selected = snoozeMinutes == minutes,
                            onClick = { viewModel.setAlarmSnoozeMinutes(minutes) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.clock_auto_silence),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted
                    )
                    listOf(5, 10, 15, 30, 0).forEach { minutes ->
                        SettingChoiceRow(
                            title = if (minutes == 0) {
                                stringResource(R.string.clock_never)
                            } else {
                                stringResource(R.string.clock_minutes_short, minutes)
                            },
                            subtitle = "",
                            selected = autoSilence == minutes,
                            onClick = { viewModel.setAlarmAutoSilenceMinutes(minutes) }
                        )
                    }
                    SettingSwitchRow(
                        title = stringResource(R.string.clock_show_seconds),
                        subtitle = stringResource(R.string.clock_show_seconds_hint),
                        checked = showSeconds,
                        onCheckedChange = { viewModel.setClockShowSeconds(it) }
                    )
                }
            }
        }

        if (!isTv) item(key = "phone") {
            SettingGroup(title = stringResource(R.string.settings_group_phone)) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_direct_call),
                    subtitle = if (directCallEnabled) stringResource(R.string.settings_direct_call_on) else stringResource(R.string.settings_direct_call_off),
                    checked = directCallEnabled,
                    onCheckedChange = onDirectCallChanged
                )
            }
        }

        if (!isTv) item(key = "messaging") {
            val deliveryReports by viewModel.smsDeliveryReports.collectAsState()
            val deliveredNotice by viewModel.smsDeliveredNotice.collectAsState()
            val autoDownload by viewModel.mmsAutoDownload.collectAsState()
            val askRead by viewModel.mmsAskReadReports.collectAsState()
            val sendRead by viewModel.mmsSendReadReports.collectAsState()
            val keys = com.serkantkn.zeta.data.datastore.SettingsDataStore
            SettingGroup(title = stringResource(R.string.settings_group_messaging)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_sms_delivery_reports),
                        subtitle = stringResource(R.string.settings_sms_delivery_reports_sub),
                        checked = deliveryReports,
                        onCheckedChange = { viewModel.setMessageSetting(keys.SMS_DELIVERY_REPORTS, it) }
                    )
                    // Being told needs something to be told about.
                    if (deliveryReports) {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_sms_delivered_notice),
                            subtitle = stringResource(R.string.settings_sms_delivered_notice_sub),
                            checked = deliveredNotice,
                            onCheckedChange = { viewModel.setMessageSetting(keys.SMS_DELIVERED_NOTICE, it) }
                        )
                    }
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_mms_auto_download),
                        subtitle = stringResource(R.string.settings_mms_auto_download_sub),
                        checked = autoDownload,
                        onCheckedChange = { viewModel.setMessageSetting(keys.MMS_AUTO_DOWNLOAD, it) }
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_mms_ask_read),
                        subtitle = stringResource(R.string.settings_mms_ask_read_sub),
                        checked = askRead,
                        onCheckedChange = { viewModel.setMessageSetting(keys.MMS_ASK_READ_REPORTS, it) }
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_mms_send_read),
                        subtitle = stringResource(R.string.settings_mms_send_read_sub),
                        checked = sendRead,
                        onCheckedChange = { viewModel.setMessageSetting(keys.MMS_SEND_READ_REPORTS, it) }
                    )
                }
            }
        }

        if (!isTv) item(key = "internet") {
            val searchEngine by viewModel.searchEngine.collectAsState()
            val suggestionsOn by viewModel.searchSuggestionsEnabled.collectAsState()
            var showClearDialog by remember { mutableStateOf(false) }

            SettingGroup(title = stringResource(R.string.settings_group_internet)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_search_engine),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted
                    )
                    SearchEngine.entries.forEach { engine ->
                        SettingChoiceRow(
                            title = engine.label,
                            subtitle = if (engine.hasLuckySearch) {
                                stringResource(R.string.settings_search_engine_lucky)
                            } else {
                                ""
                            },
                            selected = searchEngine == engine,
                            onClick = { viewModel.setSearchEngine(engine) }
                        )
                    }

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_search_suggestions),
                        subtitle = stringResource(R.string.settings_search_suggestions_sub, searchEngine.label),
                        checked = suggestionsOn,
                        onCheckedChange = viewModel::setSearchSuggestionsEnabled
                    )

                    SystemSettingRow(
                        title = stringResource(R.string.settings_clear_browsing),
                        subtitle = stringResource(R.string.settings_clear_browsing_sub),
                        onClick = { showClearDialog = true }
                    )
                }
            }

            if (showClearDialog) {
                ZuneFlipDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = stringResource(R.string.settings_clear_browsing),
                    confirmButton = {
                        ZuneDialogButton(
                            text = stringResource(R.string.common_delete),
                            onClick = {
                                viewModel.clearBrowsingData()
                                showClearDialog = false
                                showHistoryClearToast = true
                            },
                            borderColor = MaterialTheme.colorScheme.error
                        )
                    },
                    dismissButton = {
                        ZuneDialogButton(
                            text = stringResource(R.string.common_cancel),
                            onClick = { showClearDialog = false },
                            borderColor = LocalZuneColors.current.textMuted
                        )
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_browsing_confirm),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalZuneColors.current.textMuted
                    )
                }
            }
        }

        if (!isTv) item(key = "hub_order") {
            SettingGroup(title = stringResource(R.string.settings_hub_order)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.settings_hub_order_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    hubOrder.forEachIndexed { index, hubType ->
                        Surface(
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, LocalZuneColors.current.textMuted.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ${com.serkantkn.zeta.ui.components.hubTitle(hubType).lowercase()}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .alpha(if (index > 0) 1f else 0.3f)
                                        .clickable(enabled = index > 0) {
                                            viewModel.moveHub(index, -1)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.settings_move_up),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .alpha(if (index < hubOrder.size - 1) 1f else 0.3f)
                                        .clickable(enabled = index < hubOrder.size - 1) {
                                            viewModel.moveHub(index, 1)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.settings_move_down),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { viewModel.resetHubOrder() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, LocalZuneColors.current.accentColor)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_reset_default),
                            color = LocalZuneColors.current.accentColor
                        )
                    }
                }
            }
        }

        // Other apps' hubs (PLATFORM.md): which are installed, which are added. Not on a TV.
        if (!isTv) item(key = "external_hubs") {
            val installed by com.serkantkn.zeta.data.hubs.HubDirectory.installed.collectAsState()
            val approved by viewModel.approvedHubs.collectAsState()
            SettingGroup(title = stringResource(R.string.settings_external_hubs)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(
                            if (installed.isEmpty()) R.string.settings_external_hubs_none else R.string.external_hub_note
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    val developer by viewModel.hubDeveloperMode.collectAsState()
                    installed.forEach { hub ->
                        SettingSwitchRow(
                            title = hub.label,
                            subtitle = if (hub.speakable) stringResource(R.string.external_hub_from, hub.appLabel)
                                else stringResource(R.string.settings_external_hub_newer, hub.appLabel),
                            checked = hub.certificate != null && approved[hub.hub.key] == hub.certificate,
                            onCheckedChange = { on -> if (hub.speakable) viewModel.setExternalHubAdded(hub, on) }
                        )
                    }
                    // For whoever is writing a hub: what Zeta and the hub say to each other.
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_hub_developer),
                        subtitle = stringResource(R.string.settings_hub_developer_sub),
                        checked = developer,
                        onCheckedChange = viewModel::setHubDeveloperMode
                    )
                    if (developer) {
                        SystemSettingRow(
                            title = stringResource(R.string.settings_hub_log),
                            subtitle = stringResource(R.string.settings_hub_log_sub),
                            onClick = onOpenHubLog
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NotificationsSettingsPage(
    viewModel: SettingsViewModel
) {
    val notificationStyle by viewModel.notificationStyle.collectAsState()
    val context = LocalContext.current

    SettingsLazyColumn {
        actionCenterItem(viewModel)

        item(key = "notification_style") {
            SettingGroup(title = stringResource(R.string.settings_notification_style)) {
                // Windows Phone's banners are Pro's; the free build leaves notifications to Android.
                com.serkantkn.zeta.ui.components.ProLocked {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.notification_style_wp),
                        subtitle = stringResource(R.string.settings_notification_wp_sub),
                        selected = notificationStyle == NotificationStyle.WINDOWS_PHONE,
                        onClick = { viewModel.setNotificationStyle(NotificationStyle.WINDOWS_PHONE) }
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.notification_style_system),
                        subtitle = stringResource(R.string.settings_notification_system_sub),
                        selected = notificationStyle == NotificationStyle.SYSTEM,
                        onClick = { viewModel.setNotificationStyle(NotificationStyle.SYSTEM) }
                    )

                    val hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
                    if (notificationStyle == NotificationStyle.WINDOWS_PHONE && hasOverlayPermission) {
                        Text(
                            text = stringResource(R.string.settings_overlay_on_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalZuneColors.current.textMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (notificationStyle == NotificationStyle.WINDOWS_PHONE && !hasOverlayPermission) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalZuneColors.current.accentColor,
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.settings_overlay_grant))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val currentAccentColor = LocalZuneColors.current.accentColor
                    OutlinedButton(
                        onClick = {
                            com.serkantkn.zeta.data.repository.SocialRepository.sendTestNotification(context, currentAccentColor)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, currentAccentColor)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_test_notification),
                            color = currentAccentColor
                        )
                    }
                }
                }
            }
        }
    }
}

/**
 * The action center's switch and everything it leans on. Each permission is a row that opens the
 * system page for it; the dots are re-read whenever the user comes back from one.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.actionCenterItem(viewModel: SettingsViewModel) {
    item(key = "action_center") {
        val context = LocalContext.current
        val on by viewModel.wpActionCenter.collectAsState()
        var checks by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks++ }
        val status = remember(checks) { ActionCenterPermissions.read(context) }
        var disclosureOpen by remember { mutableStateOf(false) }
        if (disclosureOpen) {
            AccessibilityDisclosureDialog(
                onAccept = { ActionCenterPermissions.open(context, ActionCenterPermissions.SERVICE) },
                onDismiss = { disclosureOpen = false }
            )
        }

        SettingGroup(title = stringResource(R.string.ac_settings_group)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingSwitchRow(
                    title = stringResource(R.string.ac_settings_switch),
                    subtitle = stringResource(if (on) R.string.ac_settings_switch_on else R.string.ac_settings_switch_off),
                    checked = on,
                    onCheckedChange = viewModel::setWpActionCenter
                )
                if (on) {
                    Text(
                        text = stringResource(R.string.ac_settings_needs),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.ac_perm_service),
                        subtitle = if (status.service) stringResource(R.string.settings_enabled) else stringResource(R.string.ac_perm_service_sub),
                        isActive = status.service,
                        onClick = {
                            if (status.service) ActionCenterPermissions.open(context, ActionCenterPermissions.SERVICE)
                            else disclosureOpen = true
                        }
                    )
                    if (!status.service && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        SystemSettingRow(
                            title = stringResource(R.string.ac_perm_restricted),
                            subtitle = stringResource(R.string.ac_perm_restricted_sub),
                            onClick = { ActionCenterPermissions.open(context, ActionCenterPermissions.APP_INFO) }
                        )
                    }
                    SystemSettingRow(
                        title = stringResource(R.string.ac_perm_notifications),
                        subtitle = if (status.notifications) stringResource(R.string.settings_permission_granted) else stringResource(R.string.ac_perm_notifications_sub),
                        isActive = status.notifications,
                        onClick = { ActionCenterPermissions.open(context, ActionCenterPermissions.NOTIFICATIONS) }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.settings_overlay_permission),
                        subtitle = if (status.overlay) stringResource(R.string.settings_permission_granted) else stringResource(R.string.ac_perm_overlay_sub),
                        isActive = status.overlay,
                        onClick = { ActionCenterPermissions.open(context, ActionCenterPermissions.OVERLAY) }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.ac_perm_write),
                        subtitle = if (status.writeSettings) stringResource(R.string.settings_permission_granted) else stringResource(R.string.ac_perm_write_sub),
                        isActive = status.writeSettings,
                        onClick = { ActionCenterPermissions.open(context, ActionCenterPermissions.WRITE_SETTINGS) }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.ac_perm_dnd),
                        subtitle = if (status.dnd) stringResource(R.string.settings_permission_granted) else stringResource(R.string.ac_perm_dnd_sub),
                        isActive = status.dnd,
                        onClick = { ActionCenterPermissions.open(context, ActionCenterPermissions.DND) }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.ac_perm_battery),
                        subtitle = if (status.battery) stringResource(R.string.settings_permission_granted) else stringResource(R.string.ac_perm_battery_sub),
                        isActive = status.battery,
                        onClick = { ActionCenterPermissions.open(context, ActionCenterPermissions.BATTERY) }
                    )
                    Text(
                        text = stringResource(R.string.ac_settings_lock_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
    item(key = "quick_actions") {
        val on by viewModel.wpActionCenter.collectAsState()
        if (on) QuickActionsEditor(viewModel)
    }
}

/**
 * Which squares the action center shows, and in what order: the first four always, the rest when
 * it is expanded. Squares this phone cannot offer (NFC without NFC) are not listed.
 */
@Composable
private fun QuickActionsEditor(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val chosen by viewModel.quickActions.collectAsState()
    val shown = chosen.filter { com.serkantkn.zeta.util.QuickActions.available(context, it) }
    val addable = com.serkantkn.zeta.util.QuickAction.entries
        .filter { it !in chosen && com.serkantkn.zeta.util.QuickActions.available(context, it) }

    SettingGroup(title = stringResource(R.string.ac_quick_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.ac_quick_sub),
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            shown.forEachIndexed { index, action ->
                QuickActionEditRow(
                    label = "${index + 1}. ${stringResource(action.labelRes)}",
                    divider = index == com.serkantkn.zeta.util.QuickAction.ROW - 1 && shown.size > com.serkantkn.zeta.util.QuickAction.ROW,
                    onUp = if (index > 0) ({ viewModel.moveQuickAction(action, -1) }) else null,
                    onDown = if (index < shown.lastIndex) ({ viewModel.moveQuickAction(action, 1) }) else null,
                    onRemove = if (shown.size > 1) ({ viewModel.setQuickActionShown(action, false) }) else null
                )
            }
            if (addable.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.ac_quick_hidden),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(top = 10.dp)
                )
                addable.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setQuickActionShown(action, true) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(action.labelRes),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.ac_quick_add),
                            style = MaterialTheme.typography.labelLarge,
                            color = zuneColors.accentLink
                        )
                    }
                }
            }
            if ((com.serkantkn.zeta.util.QuickAction.SCREENSHOT in shown || com.serkantkn.zeta.util.QuickAction.LOCK_SCREEN in shown) &&
                com.serkantkn.zeta.data.service.ZuneKeyAccessibilityService.current == null
            ) {
                Text(
                    text = stringResource(R.string.ac_quick_needs_service),
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionEditRow(
    label: String,
    divider: Boolean,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LocalZuneColors.current.textMuted.copy(alpha = 0.25f))
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = ink,
                modifier = Modifier.weight(1f)
            )
            listOf(
                Triple(Icons.Default.KeyboardArrowUp, R.string.settings_move_up, onUp),
                Triple(Icons.Default.KeyboardArrowDown, R.string.settings_move_down, onDown),
                Triple(Icons.Default.Close, R.string.ac_quick_remove, onRemove)
            ).forEach { (icon, desc, action) ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .alpha(if (action != null) 1f else 0.3f)
                        .clickable(enabled = action != null) { action?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = stringResource(desc), tint = ink)
                }
            }
        }
        // Where the first row ends and the expanded part begins.
        if (divider) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(LocalZuneColors.current.accentColor.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
internal fun KeyboardSettingsPage(viewModel: SettingsViewModel) {
    // The two setup rows reflect system state, so they are re-read every time settings come back.
    var setupTrigger by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { setupTrigger++ }

    val isEnabled = remember(setupTrigger) { viewModel.isKeyboardEnabled() }
    val isDefault = remember(setupTrigger) { viewModel.isKeyboardDefault() }

    val soundEnabled by viewModel.keyboardSoundEnabled.collectAsState()
    val vibrationEnabled by viewModel.keyboardVibrationEnabled.collectAsState()
    val previewEnabled by viewModel.keyboardPreviewEnabled.collectAsState()
    val heightScale by viewModel.keyboardHeightScale.collectAsState()
    val numberRow by viewModel.keyboardNumberRow.collectAsState()
    val suggestions by viewModel.keyboardSuggestions.collectAsState()
    val autoCorrect by viewModel.keyboardAutoCorrect.collectAsState()
    val split by viewModel.keyboardSplit.collectAsState()
    val oneHanded by viewModel.keyboardOneHanded.collectAsState()
    val bottomPadding by viewModel.keyboardBottomPadding.collectAsState()
    val languages by viewModel.keyboardLanguages.collectAsState()
    val shortcuts by viewModel.keyboardShortcuts.collectAsState()
    val learnedCount by viewModel.keyboardLearnedCount.collectAsState()
    val clipboardCount by viewModel.keyboardClipboardCount.collectAsState()

    var showShortcutDialog by remember { mutableStateOf(false) }

    SettingsLazyColumn {
        item(key = "keyboard_setup") {
            SettingGroup(title = stringResource(R.string.keyboard_setup_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = stringResource(R.string.keyboard_enable_title),
                        subtitle = stringResource(R.string.keyboard_enable_sub),
                        isActive = isEnabled,
                        onClick = viewModel::openKeyboardSettings
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.keyboard_select_title),
                        subtitle = if (isDefault) {
                            stringResource(R.string.keyboard_ready)
                        } else {
                            stringResource(R.string.keyboard_select_sub)
                        },
                        isActive = isDefault,
                        onClick = viewModel::showKeyboardPicker
                    )
                }
            }
        }

        item(key = "keyboard_typing") {
            SettingGroup(title = stringResource(R.string.keyboard_typing_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.keyboard_number_row_title),
                        subtitle = stringResource(R.string.keyboard_number_row_sub),
                        checked = numberRow,
                        onCheckedChange = viewModel::setKeyboardNumberRow
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.keyboard_suggestions_title),
                        subtitle = stringResource(R.string.keyboard_suggestions_sub),
                        checked = suggestions,
                        onCheckedChange = viewModel::setKeyboardSuggestions
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.keyboard_autocorrect_title),
                        subtitle = stringResource(R.string.keyboard_autocorrect_sub),
                        checked = autoCorrect,
                        onCheckedChange = viewModel::setKeyboardAutoCorrect
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.keyboard_sound_title),
                        subtitle = stringResource(R.string.keyboard_sound_sub),
                        checked = soundEnabled,
                        onCheckedChange = viewModel::setKeyboardSoundEnabled
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.keyboard_vibration_title),
                        subtitle = stringResource(R.string.keyboard_vibration_sub),
                        checked = vibrationEnabled,
                        onCheckedChange = viewModel::setKeyboardVibrationEnabled
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.keyboard_preview_title),
                        subtitle = stringResource(R.string.keyboard_preview_sub),
                        checked = previewEnabled,
                        onCheckedChange = viewModel::setKeyboardPreviewEnabled
                    )
                }
            }
        }

        item(key = "keyboard_languages") {
            SettingGroup(title = stringResource(R.string.keyboard_languages_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyboardLanguage.entries.forEach { language ->
                        SettingSwitchRow(
                            title = language.displayLabel,
                            subtitle = "",
                            checked = languages.contains(language),
                            onCheckedChange = { enabled ->
                                viewModel.setKeyboardLanguageEnabled(language, enabled)
                            }
                        )
                    }
                    Text(
                        text = stringResource(R.string.keyboard_languages_note),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.keyboard_language_note),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        item(key = "keyboard_height") {
            SettingGroup(title = stringResource(R.string.keyboard_height_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val labels = listOf(
                        R.string.keyboard_height_small,
                        R.string.keyboard_height_medium,
                        R.string.keyboard_height_large
                    )
                    KeyboardDataStore.HEIGHT_CHOICES.forEachIndexed { index, scale ->
                        SettingChoiceRow(
                            title = stringResource(labels[index]),
                            subtitle = "",
                            selected = kotlin.math.abs(heightScale - scale) < 0.01f,
                            onClick = { viewModel.setKeyboardHeightScale(scale) }
                        )
                    }
                }
            }
        }

        item(key = "keyboard_bottom_padding") {
            SettingGroup(title = stringResource(R.string.keyboard_bottom_padding_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.keyboard_bottom_padding_sub),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    val labels = listOf(
                        R.string.keyboard_bottom_padding_none,
                        R.string.keyboard_bottom_padding_small,
                        R.string.keyboard_bottom_padding_medium,
                        R.string.keyboard_bottom_padding_large
                    )
                    KeyboardDataStore.BOTTOM_PADDING_CHOICES.forEachIndexed { index, dp ->
                        SettingChoiceRow(
                            title = stringResource(labels[index]),
                            subtitle = "",
                            selected = bottomPadding == dp,
                            onClick = { viewModel.setKeyboardBottomPadding(dp) }
                        )
                    }
                }
            }
        }

        item(key = "keyboard_one_handed") {
            SettingGroup(title = stringResource(R.string.keyboard_one_handed_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val labels = mapOf(
                        OneHandedMode.OFF to R.string.keyboard_one_handed_off,
                        OneHandedMode.LEFT to R.string.keyboard_one_handed_left,
                        OneHandedMode.RIGHT to R.string.keyboard_one_handed_right
                    )
                    OneHandedMode.entries.forEach { mode ->
                        SettingChoiceRow(
                            title = stringResource(labels.getValue(mode)),
                            subtitle = "",
                            selected = oneHanded == mode,
                            onClick = { viewModel.setKeyboardOneHanded(mode) }
                        )
                    }
                    SettingSwitchRow(
                        title = stringResource(R.string.keyboard_split_title),
                        subtitle = stringResource(R.string.keyboard_split_sub),
                        checked = split,
                        onCheckedChange = viewModel::setKeyboardSplit
                    )
                }
            }
        }

        item(key = "keyboard_shortcuts") {
            SettingGroup(title = stringResource(R.string.keyboard_shortcuts_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.keyboard_shortcuts_sub),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    if (shortcuts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.keyboard_shortcuts_empty),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = LocalZuneColors.current.textDim,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    shortcuts.forEach { shortcut ->
                        SettingRowContent(
                            title = shortcut.trigger,
                            subtitle = shortcut.expansion,
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = LocalZuneColors.current.textMuted,
                                    modifier = Modifier.clickable {
                                        viewModel.removeKeyboardShortcut(shortcut.id)
                                    }
                                )
                            }
                        )
                    }
                    SystemSettingRow(
                        title = stringResource(R.string.keyboard_shortcuts_add),
                        subtitle = "",
                        onClick = { showShortcutDialog = true }
                    )
                }
            }
        }

        item(key = "keyboard_data") {
            SettingGroup(title = stringResource(R.string.keyboard_data_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = stringResource(R.string.keyboard_learned_title),
                        subtitle = stringResource(R.string.keyboard_learned_sub, learnedCount),
                        onClick = viewModel::clearKeyboardLearnedWords
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.keyboard_clipboard_history_title),
                        subtitle = stringResource(R.string.keyboard_clipboard_history_sub, clipboardCount),
                        onClick = viewModel::clearKeyboardClipboard
                    )
                }
            }
        }
    }

    if (showShortcutDialog) {
        TextShortcutDialog(
            onAdd = { trigger, expansion -> viewModel.addKeyboardShortcut(trigger, expansion) },
            onDismiss = { showShortcutDialog = false }
        )
    }
}

/** "kib" -> "kolay gelsin, iyi bayramlar": trigger and full text for a new text shortcut. */
@Composable
private fun TextShortcutDialog(
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var trigger by remember { mutableStateOf("") }
    var expansion by remember { mutableStateOf("") }

    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.keyboard_shortcuts_add),
        confirmButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_save_cap),
                onClick = {
                    dismissWithAnim {
                        onAdd(trigger, expansion)
                        onDismiss()
                    }
                },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim { onDismiss() } },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WpDialogTextField(
                value = trigger,
                onValueChange = { trigger = it },
                placeholder = stringResource(R.string.keyboard_shortcut_trigger)
            )
            WpDialogTextField(
                value = expansion,
                onValueChange = { expansion = it },
                placeholder = stringResource(R.string.keyboard_shortcut_expansion)
            )
        }
    }
}

/** White, square Windows Phone text box. */
@Composable
private fun WpDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(BorderStroke(2.dp, zuneColors.accentColor))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8A8A8A)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
            cursorBrush = SolidColor(zuneColors.accentColor),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SystemSettingsPage(
    viewModel: SettingsViewModel,
    onOpenDateTimeSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    var defaultAppsTrigger by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        defaultAppsTrigger++
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        defaultAppsTrigger++
    }

    val isDefaultDialer = remember(defaultAppsTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            telecomManager?.defaultDialerPackage == context.packageName
        }
    }

    val isDefaultSms = remember(defaultAppsTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    val isDefaultBrowser = remember(defaultAppsTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    val isDefaultLauncher = remember(defaultAppsTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    val hasNotificationAccess = remember(defaultAppsTrigger) {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        enabledListeners.contains(context.packageName)
    }

    val appLanguage by viewModel.appLanguage.collectAsState()

    // A television has none of these: the hubs, the tour and the permissions they are for.
    val isTv = com.serkantkn.zeta.ui.theme.LocalIsTv.current
    SettingsLazyColumn {

        item(key = "language") {
            SettingGroup(title = stringResource(R.string.settings_language)) {
                val choose: (AppLanguage) -> Unit = { language ->
                    if (appLanguage != language && viewModel.setAppLanguage(language)) {
                        context.findActivity()?.recreate()
                    }
                }
                if (isTv) {
                    // Worked by remote: a row for each, every one a stop for the focus.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { language ->
                            SettingChoiceRow(
                                title = stringResource(language.titleRes),
                                subtitle = if (language == AppLanguage.SYSTEM) stringResource(R.string.settings_language_subtitle) else "",
                                selected = appLanguage == language,
                                onClick = { choose(language) }
                            )
                        }
                    }
                } else {
                    // Seven of them: a box that opens in place, as a phone's settings had it.
                    com.serkantkn.zeta.ui.components.WpListPicker(
                        items = AppLanguage.entries,
                        selected = appLanguage,
                        label = { stringResource(it.titleRes) },
                        onSelect = choose,
                        header = stringResource(R.string.settings_language_subtitle),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            }
        }

        item(key = "date_time") {
            SettingGroup(title = stringResource(R.string.settings_date_time)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = stringResource(R.string.settings_date_time_title),
                        subtitle = stringResource(R.string.settings_date_time_sub),
                        onClick = onOpenDateTimeSettings
                    )
                }
            }
        }

        // A television: the Home button, instead of a phone's default apps.
        if (isTv) item(key = "tv_home") { TvHomeGroup(defaultAppsTrigger, roleLauncher) }
        if (!isTv) item(key = "device") {
            SettingGroup(title = stringResource(R.string.settings_device)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = stringResource(R.string.settings_default_phone),
                        subtitle = stringResource(R.string.settings_default_phone_sub),
                        isActive = isDefaultDialer,
                        onClick = { viewModel.requestDefaultPhoneApp(context, roleLauncher) }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.settings_default_sms),
                        subtitle = stringResource(R.string.settings_default_sms_sub),
                        isActive = isDefaultSms,
                        onClick = { viewModel.requestDefaultSmsApp(context, roleLauncher) }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.settings_default_browser),
                        subtitle = stringResource(R.string.settings_default_browser_sub),
                        isActive = isDefaultBrowser,
                        onClick = { viewModel.requestDefaultBrowserApp(context, roleLauncher) }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.settings_default_launcher),
                        subtitle = stringResource(R.string.settings_default_launcher_sub),
                        isActive = isDefaultLauncher,
                        onClick = viewModel::openDefaultAppsSettings
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.settings_notification_access),
                        subtitle = stringResource(R.string.settings_notification_access_sub),
                        isActive = hasNotificationAccess,
                        onClick = viewModel::openNotificationAccessSettings
                    )
                }
            }
        }

        if (!isTv) item(key = "replay_onboarding") {
            // The tour is not only for the first run: it is where the gestures are written down,
            // and somebody who forgot how to move a tile should be able to go back and look.
            val onboardingViewModel: com.serkantkn.zeta.ui.screens.onboarding.OnboardingViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel()
            SettingGroup(title = stringResource(R.string.settings_replay_onboarding)) {
                SystemSettingRow(
                    title = stringResource(R.string.settings_replay_onboarding),
                    subtitle = stringResource(R.string.settings_replay_onboarding_sub),
                    isActive = false,
                    onClick = onboardingViewModel::startTour
                )
            }
        }
    }
}

@Composable
internal fun AboutSettingsPage(onOpenVersion: () -> Unit = {}) {
    val context = LocalContext.current
    var permissionTrigger by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionTrigger++
    }

    val hasContacts = remember(permissionTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val hasCallLog = remember(permissionTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }
    val hasSms = remember(permissionTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }
    val hasStorage = remember(permissionTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    val hasNotificationAccess = remember(permissionTrigger) {
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val hasOverlayAccess = remember(permissionTrigger) {
        android.provider.Settings.canDrawOverlays(context)
    }
    val hasCalendar = remember(permissionTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    val hasLocation = remember(permissionTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val hasWifiName = remember(permissionTrigger) { com.serkantkn.zeta.util.Connections.mayReadWifiName(context) }
    val hasBluetoothDevices = remember(permissionTrigger) {
        com.serkantkn.zeta.util.Connections.mayReadBluetoothDevices(context)
    }
    val askWifiName = rememberPermissionAsk(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ) { permissionTrigger++ }
    val askBluetoothDevices = rememberPermissionAsk(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    ) { permissionTrigger++ }
    val hasNotifications = remember(permissionTrigger) {
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val hasExactAlarms = remember(permissionTrigger) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager)
                ?.canScheduleExactAlarms() == true
    }
    val hasUsageAccess = remember(permissionTrigger) {
        com.serkantkn.zeta.util.AppUsage.isGranted(context)
    }

    val requestContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { permissionTrigger++ }
    )
    val requestCalendarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionTrigger++ }
    )
    val requestLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { permissionTrigger++ }
    )
    val requestNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { permissionTrigger++ }
    )
    val requestCallLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { permissionTrigger++ }
    )
    val requestSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionTrigger++ }
    )
    // The call log and SMS are Google Play's to hand out only to the phone's dialler and messaging
    // app, so until the launcher is one, these switches ask for the role; Android grants the
    // permission with it.
    val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { permissionTrigger++ }
    )

    // A television has none of these: the hubs, the tour and the permissions they are for.
    val isTv = com.serkantkn.zeta.ui.theme.LocalIsTv.current
    SettingsLazyColumn {
        item(key = "about") {
            SettingGroup(title = stringResource(R.string.settings_about_group)) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    // Pro: what it adds and the way to buy it, that it is already bought, or how
                    // much of the trial is left.
                    val pro = com.serkantkn.zeta.util.Pro.unlocked
                    val trial by com.serkantkn.zeta.data.billing.ProTrial.state.collectAsState()
                    SystemSettingRow(
                        title = stringResource(R.string.pro_settings_row),
                        subtitle = when {
                            com.serkantkn.zeta.util.Pro.purchased -> stringResource(R.string.pro_settings_row_owned)
                            trial is com.serkantkn.zeta.data.billing.ProTrial.State.Running -> {
                                val days = (trial as com.serkantkn.zeta.data.billing.ProTrial.State.Running).daysLeft
                                androidx.compose.ui.res.pluralStringResource(R.plurals.pro_trial_days_left, days, days)
                            }
                            trial == com.serkantkn.zeta.data.billing.ProTrial.State.Over -> stringResource(R.string.pro_settings_row_trial_over)
                            trial == com.serkantkn.zeta.data.billing.ProTrial.State.Available -> stringResource(R.string.pro_settings_row_trial_available)
                            else -> stringResource(R.string.pro_settings_row_free)
                        },
                        isActive = pro,
                        onClick = {
                            context.startActivity(Intent(context, com.serkantkn.zeta.ui.pro.ProActivity::class.java))
                        }
                    )
                    // The name the build was given, so a bug report says which build it came
                    // from; it opens the launcher's own page with what each version brought.
                    SystemSettingRow(
                        title = stringResource(R.string.settings_version, com.serkantkn.zeta.BuildConfig.VERSION_NAME),
                        subtitle = stringResource(R.string.settings_release_notes),
                        onClick = onOpenVersion
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_about_inspired),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = LocalZuneColors.current.textMuted
                    )
                }
            }
        }

        item(key = "this_device") {
            AboutDeviceGroup()
        }

        if (!isTv) item(key = "permissions") {
            SettingGroup(title = stringResource(R.string.settings_permissions_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_permissions_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_contacts),
                        subtitle = stringResource(R.string.settings_perm_contacts_sub),
                        checked = hasContacts,
                        onCheckedChange = {
                            if (!hasContacts) {
                                requestContactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                            } else {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_call_log),
                        subtitle = stringResource(R.string.settings_perm_call_log_sub),
                        checked = hasCallLog,
                        onCheckedChange = {
                            if (!hasCallLog) {
                                if (com.serkantkn.zeta.util.PhoneCaller.isDefaultDialer(context)) {
                                    requestCallLogLauncher.launch(Manifest.permission.READ_CALL_LOG)
                                } else {
                                    settingsViewModel.requestDefaultPhoneApp(context, roleLauncher)
                                }
                            } else {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_sms),
                        subtitle = stringResource(R.string.settings_perm_sms_sub),
                        checked = hasSms,
                        onCheckedChange = {
                            if (!hasSms) {
                                if (com.serkantkn.zeta.data.repository.SmsRepository.isDefaultSmsApp(context)) {
                                    requestSmsLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_SMS,
                                            Manifest.permission.SEND_SMS
                                        )
                                    )
                                } else {
                                    settingsViewModel.requestDefaultSmsApp(context, roleLauncher)
                                }
                            } else {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_storage),
                        subtitle = stringResource(R.string.settings_perm_storage_sub),
                        checked = hasStorage,
                        onCheckedChange = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    openAppSettings(context)
                                }
                            } else {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_notification_listener),
                        subtitle = stringResource(R.string.settings_perm_notification_listener_sub),
                        checked = hasNotificationAccess,
                        onCheckedChange = {
                            try {
                                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_overlay_permission),
                        subtitle = stringResource(R.string.settings_perm_overlay_sub),
                        checked = hasOverlayAccess,
                        onCheckedChange = {
                            try {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_calendar),
                        subtitle = stringResource(R.string.settings_perm_calendar_sub),
                        checked = hasCalendar,
                        onCheckedChange = {
                            if (!hasCalendar) {
                                requestCalendarLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                )
                            } else {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_location),
                        subtitle = stringResource(R.string.settings_perm_location_sub),
                        checked = hasLocation,
                        onCheckedChange = {
                            if (!hasLocation) {
                                requestLocationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            } else {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_wifi_name),
                        subtitle = stringResource(R.string.settings_perm_wifi_name_short),
                        checked = hasWifiName,
                        onCheckedChange = { if (hasWifiName) openAppSettings(context) else askWifiName() }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_perm_bt_devices),
                            subtitle = stringResource(R.string.settings_perm_bt_devices_short),
                            checked = hasBluetoothDevices,
                            onCheckedChange = { if (hasBluetoothDevices) openAppSettings(context) else askBluetoothDevices() }
                        )
                    }

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_notifications),
                        subtitle = stringResource(R.string.settings_perm_notifications_sub),
                        checked = hasNotifications,
                        onCheckedChange = {
                            if (!hasNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                requestNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                // Switched off in the system's own page, or being switched off: go there.
                                try {
                                    context.startActivity(
                                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                } catch (e: Exception) {
                                    openAppSettings(context)
                                }
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_exact_alarms),
                        subtitle = stringResource(R.string.settings_perm_exact_alarms_sub),
                        checked = hasExactAlarms,
                        onCheckedChange = {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    context.startActivity(
                                        Intent(
                                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } else {
                                    openAppSettings(context)
                                }
                            } catch (e: Exception) {
                                openAppSettings(context)
                            }
                        }
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_perm_usage_access),
                        subtitle = stringResource(R.string.settings_perm_usage_access_sub),
                        checked = hasUsageAccess,
                        onCheckedChange = {
                            try {
                                context.startActivity(com.serkantkn.zeta.util.AppUsage.settingsIntent())
                            } catch (e: Exception) {
                                openAppSettings(context)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    try {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    } catch (e: Exception) {
        ZuneLog.e("SettingsPages", "openAppSettings failed", e)
    }
}

/** A TV's two designs - the Xbox 360's dashboard and Media Center - and their sounds. */
@Composable
private fun TvDesignGroup() {
    val vista by com.serkantkn.zeta.ui.tv.TvDesign.vista.collectAsState()
    val sounds by com.serkantkn.zeta.ui.tv.TvDesign.sounds.collectAsState()
    SettingGroup(title = stringResource(R.string.tv_design_group)) {
        SettingChoiceRow(
            title = stringResource(R.string.tv_design_xbox),
            subtitle = stringResource(R.string.tv_design_xbox_sub),
            selected = !vista,
            onClick = { com.serkantkn.zeta.ui.tv.TvDesign.setVista(false) }
        )
        SettingChoiceRow(
            title = stringResource(R.string.tv_design_vista),
            subtitle = stringResource(R.string.tv_design_vista_sub),
            selected = vista,
            onClick = { com.serkantkn.zeta.ui.tv.TvDesign.setVista(true) }
        )
        SettingSwitchRow(
            title = stringResource(R.string.tv_design_sounds),
            subtitle = stringResource(R.string.tv_design_sounds_sub),
            checked = sounds,
            onCheckedChange = { com.serkantkn.zeta.ui.tv.TvDesign.setSounds(it) }
        )
    }
    // The screensaver and the music's own screen, in either design.
    val saver by com.serkantkn.zeta.ui.tv.TvDesign.saver.collectAsState()
    val visualizer by com.serkantkn.zeta.ui.tv.TvDesign.visualizer.collectAsState()
    SettingGroup(title = stringResource(R.string.tv_design_saver)) {
        listOf(
            com.serkantkn.zeta.ui.tv.TvSaver.OFF to R.string.xbox_saver_off,
            com.serkantkn.zeta.ui.tv.TvSaver.DIM to R.string.xbox_saver_dim,
            com.serkantkn.zeta.ui.tv.TvSaver.PHOTOS to R.string.xbox_saver_photos
        ).forEach { (mode, label) ->
            SettingChoiceRow(
                title = stringResource(label),
                subtitle = if (mode == com.serkantkn.zeta.ui.tv.TvSaver.DIM) stringResource(R.string.tv_design_saver_sub) else "",
                selected = saver == mode.name,
                onClick = { com.serkantkn.zeta.ui.tv.TvDesign.setSaver(mode) }
            )
        }
        SettingSwitchRow(
            title = stringResource(R.string.tv_design_visualizer),
            subtitle = stringResource(R.string.tv_design_visualizer_sub),
            checked = visualizer,
            onCheckedChange = { com.serkantkn.zeta.ui.tv.TvDesign.setVisualizer(it) }
        )
    }
}
