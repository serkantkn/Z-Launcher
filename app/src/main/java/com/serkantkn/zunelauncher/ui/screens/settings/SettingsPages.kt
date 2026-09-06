package com.serkantkn.zunelauncher.ui.screens.settings

import com.serkantkn.zunelauncher.ui.theme.findActivity
import com.serkantkn.zunelauncher.util.AppLanguage
import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import com.serkantkn.zunelauncher.util.ZuneLog
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
import com.serkantkn.zunelauncher.data.model.*
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

@Composable
internal fun LookSettingsPage(
    viewModel: SettingsViewModel,
    themeMode: ThemeMode,
    accentColor: AccentColor,
    fontScale: Float,
    animationsEnabled: Boolean,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onAccentColorChanged: (AccentColor) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onAnimationsChanged: (Boolean) -> Unit
) {
    val customWallpaperPath by viewModel.customWallpaperPath.collectAsState()
    val customHubWallpaperPath by viewModel.customHubWallpaperPath.collectAsState()
    val hubBackgroundMode by viewModel.hubBackgroundMode.collectAsState()
    val hubBackgroundOpacity by viewModel.hubBackgroundOpacity.collectAsState()
    val solidBackgroundEnabled by viewModel.solidBackgroundEnabled.collectAsState()
    val customThemeColor by viewModel.customThemeColor.collectAsState()
    val dynamicThemeColor by viewModel.dynamicThemeColor.collectAsState()
    val tileCornerStyle by viewModel.tileCornerStyle.collectAsState()
    val tileSpacing by viewModel.tileSpacing.collectAsState()
    val homeScreenLayout by viewModel.homeScreenLayout.collectAsState()

    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var hubCropUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) cropUri = uri
        }
    )

    val hubWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) hubCropUri = uri
        }
    )

    cropUri?.let { uri ->
        Dialog(
            onDismissRequest = { cropUri = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            WallpaperCropScreen(
                uri = uri,
                onApply = { bitmap ->
                    viewModel.saveCroppedWallpaper(bitmap)
                    cropUri = null
                },
                onCancel = { cropUri = null }
            )
        }
    }

    hubCropUri?.let { uri ->
        Dialog(
            onDismissRequest = { hubCropUri = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            WallpaperCropScreen(
                uri = uri,
                onApply = { bitmap ->
                    viewModel.saveCroppedHubWallpaper(bitmap)
                    hubCropUri = null
                },
                onCancel = { hubCropUri = null }
            )
        }
    }

    SettingsLazyColumn {
        item(key = "theme") {
            SettingGroup(title = stringResource(R.string.theme_setting)) {
                ThemeChoiceRow(
                    selectedMode = themeMode,
                    onSelected = onThemeModeChanged
                )
            }
        }

        item(key = "wallpaper") {
            val context = LocalContext.current
            SettingGroup(title = stringResource(R.string.settings_wallpaper)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.theme_system),
                        subtitle = stringResource(R.string.settings_wallpaper_system_sub),
                        selected = !solidBackgroundEnabled && customWallpaperPath == null,
                        onClick = {
                            viewModel.setSolidBackgroundEnabled(false)
                            viewModel.clearCustomWallpaper()
                        }
                    )

                    SettingChoiceRow(
                        title = stringResource(R.string.settings_wallpaper_custom),
                        subtitle = if (customWallpaperPath != null) stringResource(R.string.settings_wallpaper_custom_active) else stringResource(R.string.settings_wallpaper_none),
                        selected = !solidBackgroundEnabled && customWallpaperPath != null,
                        onClick = {
                            viewModel.setSolidBackgroundEnabled(false)
                            if (customWallpaperPath == null) {
                                if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                    wallpaperPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_wallpaper_pro_only),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )

                    if (!solidBackgroundEnabled && customWallpaperPath != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                        wallpaperPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocalZuneColors.current.accentColor,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(stringResource(R.string.settings_wallpaper_change))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearCustomWallpaper()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = LocalZuneColors.current.textMuted
                                )
                            ) {
                                Text(stringResource(R.string.common_clear))
                            }
                        }
                    }

                    SettingChoiceRow(
                        title = stringResource(R.string.settings_solid_background),
                        subtitle = stringResource(R.string.settings_solid_background_sub),
                        selected = solidBackgroundEnabled,
                        onClick = {
                            viewModel.setSolidBackgroundEnabled(true)
                        }
                    )
                }
            }
        }

        item(key = "hub_wallpaper") {
            val context = LocalContext.current
            SettingGroup(title = stringResource(R.string.settings_hub_background)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_hub_background_same),
                        subtitle = stringResource(R.string.settings_hub_background_same_sub),
                        selected = hubBackgroundMode == HubBackgroundMode.MATCH_LAUNCHER,
                        onClick = {
                            viewModel.setHubBackgroundMode(HubBackgroundMode.MATCH_LAUNCHER)
                        }
                    )

                    SettingChoiceRow(
                        title = stringResource(R.string.settings_wallpaper_custom),
                        subtitle = if (customHubWallpaperPath != null) stringResource(R.string.settings_hub_wallpaper_active) else stringResource(R.string.settings_wallpaper_none),
                        selected = hubBackgroundMode == HubBackgroundMode.CUSTOM,
                        onClick = {
                            if (customHubWallpaperPath == null) {
                                if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                    hubWallpaperPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_wallpaper_pro_only),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                viewModel.setHubBackgroundMode(HubBackgroundMode.CUSTOM)
                            }
                        }
                    )

                    if (hubBackgroundMode == HubBackgroundMode.CUSTOM && customHubWallpaperPath != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                        hubWallpaperPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocalZuneColors.current.accentColor,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(stringResource(R.string.settings_wallpaper_change))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearCustomHubWallpaper()
                                    viewModel.setHubBackgroundMode(HubBackgroundMode.MATCH_LAUNCHER)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = LocalZuneColors.current.textMuted
                                )
                            ) {
                                Text(stringResource(R.string.common_clear))
                            }
                        }
                    }

                    SettingChoiceRow(
                        title = stringResource(R.string.theme_system),
                        subtitle = stringResource(R.string.settings_wallpaper_system_sub),
                        selected = hubBackgroundMode == HubBackgroundMode.SYSTEM,
                        onClick = {
                            viewModel.setHubBackgroundMode(HubBackgroundMode.SYSTEM)
                        }
                    )

                    SettingChoiceRow(
                        title = stringResource(R.string.settings_solid_background),
                        subtitle = stringResource(R.string.settings_solid_background_sub),
                        selected = hubBackgroundMode == HubBackgroundMode.SOLID,
                        onClick = {
                            viewModel.setHubBackgroundMode(HubBackgroundMode.SOLID)
                        }
                    )

                    if (hubBackgroundMode != HubBackgroundMode.SOLID) {
                        val hubBackgroundOpacity by viewModel.hubBackgroundOpacity.collectAsState()
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_dim_opacity),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "%${(hubBackgroundOpacity * 100).toInt()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalZuneColors.current.accentColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = hubBackgroundOpacity,
                                onValueChange = { viewModel.setHubBackgroundOpacity(it) },
                                valueRange = 0.0f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = LocalZuneColors.current.accentColor,
                                    activeTrackColor = LocalZuneColors.current.accentColor,
                                    inactiveTrackColor = (if (LocalZuneColors.current.isDark) Color.White else Color.Black).copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }
        }

        item(key = "accent") {
            val customThemeColor by viewModel.customThemeColor.collectAsState()
            SettingGroup(title = stringResource(R.string.settings_accent_color)) {
                AccentColorChoiceRow(
                    selectedColor = accentColor,
                    customThemeColor = customThemeColor,
                    onSelected = onAccentColorChanged,
                    onCustomColorSelected = { colorInt ->
                        viewModel.setCustomThemeColor(colorInt)
                        onAccentColorChanged(AccentColor.CUSTOM)
                    }
                )
            }
        }

        item(key = "preview") {
            val tileColumns by viewModel.tileColumns.collectAsState()
            LookPreviewCard(
                themeMode = themeMode,
                accentColor = accentColor,
                customThemeColor = customThemeColor,
                dynamicThemeColor = dynamicThemeColor,
                solidBackgroundEnabled = solidBackgroundEnabled,
                customWallpaperPath = customWallpaperPath,
                tileCornerStyle = tileCornerStyle,
                tileSpacing = tileSpacing,
                tileColumns = tileColumns,
                hubBackgroundOpacity = hubBackgroundOpacity,
                hubBackgroundMode = hubBackgroundMode,
                homeScreenLayout = homeScreenLayout
            )
        }

        item(key = "home_screen_layout") {
            SettingGroup(title = stringResource(R.string.settings_start_layout)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_layout_zune),
                        subtitle = stringResource(R.string.settings_layout_zune_sub),
                        selected = homeScreenLayout == HomeScreenLayout.ZUNE,
                        onClick = {
                            viewModel.setHomeScreenLayout(HomeScreenLayout.ZUNE)
                        }
                    )

                    SettingChoiceRow(
                        title = stringResource(R.string.settings_layout_wp),
                        subtitle = stringResource(R.string.settings_layout_wp_sub),
                        selected = homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE,
                        onClick = {
                            viewModel.setHomeScreenLayout(HomeScreenLayout.WINDOWS_PHONE)
                        }
                    )
                }
            }
        }

        item(key = "tile_columns") {
            val tileColumns by viewModel.tileColumns.collectAsState()
            SettingGroup(title = stringResource(R.string.settings_tile_columns)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_columns_4),
                        subtitle = stringResource(R.string.settings_columns_4_sub),
                        selected = tileColumns == 4,
                        onClick = {
                            viewModel.setTileColumns(4)
                        }
                    )

                    SettingChoiceRow(
                        title = stringResource(R.string.settings_columns_8),
                        subtitle = stringResource(R.string.settings_columns_8_sub),
                        selected = tileColumns == 8,
                        onClick = {
                            viewModel.setTileColumns(8)
                        }
                    )
                }
            }
        }

        item(key = "favorite_tiles") {
            val tileCornerStyle by viewModel.tileCornerStyle.collectAsState()
            val tileSpacing by viewModel.tileSpacing.collectAsState()

            SettingGroup(title = stringResource(R.string.settings_favorite_tiles)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_corners_square),
                        subtitle = stringResource(R.string.settings_corners_square_sub),
                        selected = tileCornerStyle == TileCornerStyle.SHARP,
                        onClick = {
                            viewModel.setTileCornerStyle(TileCornerStyle.SHARP)
                        }
                    )

                    SettingChoiceRow(
                        title = stringResource(R.string.settings_corners_rounded),
                        subtitle = stringResource(R.string.settings_corners_rounded_sub),
                        selected = tileCornerStyle == TileCornerStyle.ROUNDED,
                        onClick = {
                            viewModel.setTileCornerStyle(TileCornerStyle.ROUNDED)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_tile_spacing),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "$tileSpacing dp",
                                style = MaterialTheme.typography.titleMedium,
                                color = LocalZuneColors.current.accentColor
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = tileSpacing.toFloat(),
                            onValueChange = { viewModel.setTileSpacing(it.toInt()) },
                            valueRange = 0f..16f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = LocalZuneColors.current.accentColor,
                                activeTrackColor = LocalZuneColors.current.accentColor,
                                inactiveTrackColor = (if (LocalZuneColors.current.isDark) Color.White else Color.Black).copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HubSettingsPage(
    viewModel: SettingsViewModel,
    socialHubLayout: SocialHubLayout,
    directCallEnabled: Boolean,
    onSocialHubLayoutChanged: (SocialHubLayout) -> Unit,
    onDirectCallChanged: (Boolean) -> Unit,
    onClearBrowserHistory: () -> Unit
) {
    val hubOrder by viewModel.hubOrder.collectAsState()
    var showHistoryClearToast by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showHistoryClearToast) {
        LaunchedEffect(Unit) {
            android.widget.Toast.makeText(context, context.getString(R.string.settings_browser_history_cleared), android.widget.Toast.LENGTH_SHORT).show()
            showHistoryClearToast = false
        }
    }

    SettingsLazyColumn {
        item(key = "social") {
            SettingGroup(title = stringResource(R.string.settings_group_social)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_social_timeline),
                        subtitle = stringResource(R.string.settings_social_timeline_sub),
                        selected = socialHubLayout == SocialHubLayout.TIMELINE,
                        onClick = { onSocialHubLayoutChanged(SocialHubLayout.TIMELINE) }
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_social_grouped),
                        subtitle = stringResource(R.string.settings_social_grouped_sub),
                        selected = socialHubLayout == SocialHubLayout.GROUPED,
                        onClick = { onSocialHubLayoutChanged(SocialHubLayout.GROUPED) }
                    )
                }
            }
        }

        item(key = "phone") {
            SettingGroup(title = stringResource(R.string.settings_group_phone)) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_direct_call),
                    subtitle = if (directCallEnabled) stringResource(R.string.settings_direct_call_on) else stringResource(R.string.settings_direct_call_off),
                    checked = directCallEnabled,
                    onCheckedChange = onDirectCallChanged
                )
            }
        }

        item(key = "internet") {
            SettingGroup(title = stringResource(R.string.settings_group_internet)) {
                SystemSettingRow(
                    title = stringResource(R.string.settings_clear_history),
                    subtitle = stringResource(R.string.settings_clear_history_sub),
                    onClick = {
                        onClearBrowserHistory()
                        showHistoryClearToast = true
                    }
                )
            }
        }

        item(key = "hub_order") {
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
                                    text = "${index + 1}. ${stringResource(hubType.titleRes).lowercase()}",
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
    }
}

@Composable
internal fun NotificationsSettingsPage(
    viewModel: SettingsViewModel,
    onOpenAppFilter: () -> Unit = {}
) {
    val notificationStyle by viewModel.notificationStyle.collectAsState()
    val disabledNotificationApps by viewModel.disabledNotificationApps.collectAsState()
    val context = LocalContext.current

    SettingsLazyColumn {
        item(key = "notification_filter") {
            SettingGroup(title = stringResource(R.string.settings_notification_filter)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_notification_apps),
                        subtitle = if (disabledNotificationApps.isEmpty()) stringResource(R.string.settings_notification_apps_all) else stringResource(R.string.settings_notification_apps_blocked, disabledNotificationApps.size),
                        selected = true,
                        onClick = onOpenAppFilter
                    )
                }
            }
        }

        item(key = "notification_style") {
            SettingGroup(title = stringResource(R.string.settings_notification_style)) {
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
                            com.serkantkn.zunelauncher.data.repository.SocialRepository.sendTestNotification(context, currentAccentColor)
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

@Composable
internal fun DisplayAndSoundSettingsPage(viewModel: SettingsViewModel) {
    val brightness by viewModel.brightness.collectAsState()
    val mediaVolume by viewModel.mediaVolume.collectAsState()
    val ringVolume by viewModel.ringVolume.collectAsState()
    val volumeBarStyle by viewModel.volumeBarStyle.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshSystemSettings()
    }

    SettingsLazyColumn {
        item(key = "screen") {
            SettingGroup(title = stringResource(R.string.settings_display_brightness)) {
                GlassPanel {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.settings_brightness),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Light
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Slider(
                            value = brightness,
                            onValueChange = viewModel::setBrightness,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = LocalZuneColors.current.accentColor,
                                activeTrackColor = LocalZuneColors.current.accentColor,
                                inactiveTrackColor = LocalZuneColors.current.textDim.copy(alpha = 0.28f)
                            )
                        )
                    }
                }
            }
        }

        item(key = "volume_bar_style") {
            SettingGroup(title = stringResource(R.string.settings_volume_style)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.volume_bar_wp),
                        subtitle = stringResource(R.string.settings_volume_wp_sub),
                        selected = volumeBarStyle == com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE,
                        onClick = { viewModel.setVolumeBarStyle(com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE) }
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.volume_bar_system),
                        subtitle = stringResource(R.string.settings_volume_system_sub),
                        selected = volumeBarStyle == com.serkantkn.zunelauncher.data.model.VolumeBarStyle.SYSTEM,
                        onClick = { viewModel.setVolumeBarStyle(com.serkantkn.zunelauncher.data.model.VolumeBarStyle.SYSTEM) }
                    )

                    if (volumeBarStyle == com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE) {
                        val ctx = LocalContext.current
                        val hasOverlay = android.provider.Settings.canDrawOverlays(ctx)
                        val hasKeyService = com.serkantkn.zunelauncher.data.service.ZuneKeyAccessibilityService.isEnabled(ctx)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_volume_permissions_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalZuneColors.current.textMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SystemSettingRow(
                            title = stringResource(R.string.settings_overlay_permission),
                            subtitle = if (hasOverlay) stringResource(R.string.settings_permission_granted) else stringResource(R.string.settings_overlay_permission_sub),
                            isActive = hasOverlay,
                            onClick = {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${ctx.packageName}")
                                    )
                                )
                            }
                        )
                        SystemSettingRow(
                            title = stringResource(R.string.settings_key_service),
                            subtitle = if (hasKeyService) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_key_service_sub),
                            isActive = hasKeyService,
                            onClick = { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        )
                    }
                }
            }
        }

        item(key = "volume") {
            SettingGroup(title = stringResource(R.string.settings_volume_levels)) {
                GlassPanel {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.settings_media_volume),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Light
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Slider(
                            value = mediaVolume,
                            onValueChange = viewModel::setMediaVolume,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = LocalZuneColors.current.accentColor,
                                activeTrackColor = LocalZuneColors.current.accentColor,
                                inactiveTrackColor = LocalZuneColors.current.textDim.copy(alpha = 0.28f)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.settings_ring_volume),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Light
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Slider(
                            value = ringVolume,
                            onValueChange = viewModel::setRingVolume,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = LocalZuneColors.current.accentColor,
                                activeTrackColor = LocalZuneColors.current.accentColor,
                                inactiveTrackColor = LocalZuneColors.current.textDim.copy(alpha = 0.28f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConnectivitySettingsPage(viewModel: SettingsViewModel) {
    SettingsLazyColumn {
        item(key = "connections") {
            SettingGroup(title = stringResource(R.string.settings_tab_connectivity)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = stringResource(R.string.settings_wifi),
                        subtitle = stringResource(R.string.settings_wifi_sub),
                        onClick = viewModel::openWifiSettings
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.settings_bluetooth),
                        subtitle = stringResource(R.string.settings_bluetooth_sub),
                        onClick = viewModel::openBluetoothSettings
                    )
                }
            }
        }
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

    LaunchedEffect(Unit) {
        viewModel.refreshSystemSettings()
    }

    val appLanguage by viewModel.appLanguage.collectAsState()

    SettingsLazyColumn {

        item(key = "language") {
            SettingGroup(title = stringResource(R.string.settings_language)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        SettingChoiceRow(
                            title = stringResource(language.titleRes),
                            subtitle = if (language == AppLanguage.SYSTEM) stringResource(R.string.settings_language_subtitle) else "",
                            selected = appLanguage == language,
                            onClick = {
                                if (appLanguage != language && viewModel.setAppLanguage(language)) {
                                    context.findActivity()?.recreate()
                                }
                            }
                        )
                    }
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

        item(key = "device") {
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
    }
}

@Composable
internal fun AboutSettingsPage() {
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

    val requestContactsLauncher = rememberLauncherForActivityResult(
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

    SettingsLazyColumn {
        item(key = "about") {
            SettingGroup(title = stringResource(R.string.settings_about_group)) {
                Column(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_version),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
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

        item(key = "permissions") {
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
                                requestCallLogLauncher.launch(Manifest.permission.READ_CALL_LOG)
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
                                requestSmsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.SEND_SMS
                                    )
                                )
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
