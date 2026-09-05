package com.serkantkn.zunelauncher.ui.screens.settings

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
            SettingGroup(title = "tema") {
                ThemeChoiceRow(
                    selectedMode = themeMode,
                    onSelected = onThemeModeChanged
                )
            }
        }

        item(key = "wallpaper") {
            val context = LocalContext.current
            SettingGroup(title = "duvar kağıdı") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "sistem",
                        subtitle = "sistem duvar kağıdı",
                        selected = !solidBackgroundEnabled && customWallpaperPath == null,
                        onClick = {
                            viewModel.setSolidBackgroundEnabled(false)
                            viewModel.clearCustomWallpaper()
                        }
                    )

                    SettingChoiceRow(
                        title = "özel resim",
                        subtitle = if (customWallpaperPath != null) "özel resim aktif" else "resim seçilmedi",
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
                                        "Özel duvar kağıdı sadece Z Launcher Pro'da geçerlidir.",
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
                                Text("Resmi Değiştir")
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
                                Text("Temizle")
                            }
                        }
                    }

                    SettingChoiceRow(
                        title = "saf arkaplan",
                        subtitle = "koyuda siyah, açıkta beyaz arkaplan",
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
            SettingGroup(title = "hub arkaplanı") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "başlangıç ekranı ile aynı",
                        subtitle = "başlangıç ekranı duvar kağıdını kullan",
                        selected = hubBackgroundMode == HubBackgroundMode.MATCH_LAUNCHER,
                        onClick = {
                            viewModel.setHubBackgroundMode(HubBackgroundMode.MATCH_LAUNCHER)
                        }
                    )

                    SettingChoiceRow(
                        title = "özel resim",
                        subtitle = if (customHubWallpaperPath != null) "özel hub resmi aktif" else "resim seçilmedi",
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
                                        "Özel duvar kağıdı sadece Z Launcher Pro'da geçerlidir.",
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
                                Text("Resmi Değiştir")
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
                                Text("Temizle")
                            }
                        }
                    }

                    SettingChoiceRow(
                        title = "sistem",
                        subtitle = "sistem duvar kağıdı",
                        selected = hubBackgroundMode == HubBackgroundMode.SYSTEM,
                        onClick = {
                            viewModel.setHubBackgroundMode(HubBackgroundMode.SYSTEM)
                        }
                    )

                    SettingChoiceRow(
                        title = "saf arkaplan",
                        subtitle = "koyuda siyah, açıkta beyaz arkaplan",
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
                                    text = "karartma opaklığı",
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
            SettingGroup(title = "vurgu rengi") {
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
            SettingGroup(title = "başlangıç ekranı düzeni") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "zune",
                        subtitle = "kayar hub listesi ve yan favoriler",
                        selected = homeScreenLayout == HomeScreenLayout.ZUNE,
                        onClick = {
                            viewModel.setHomeScreenLayout(HomeScreenLayout.ZUNE)
                        }
                    )

                    SettingChoiceRow(
                        title = "windows phone",
                        subtitle = "canlı hub ve uygulama karoları",
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
            SettingGroup(title = "karo düzeni (sütun sayısı)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "4 sütun",
                        subtitle = "standart karo boyutu",
                        selected = tileColumns == 4,
                        onClick = {
                            viewModel.setTileColumns(4)
                        }
                    )

                    SettingChoiceRow(
                        title = "8 sütun",
                        subtitle = "daha fazla karo (küçük karolar)",
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

            SettingGroup(title = "favoriler karoları") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "köşeli",
                        subtitle = "düz keskin köşeler (0 dp)",
                        selected = tileCornerStyle == TileCornerStyle.SHARP,
                        onClick = {
                            viewModel.setTileCornerStyle(TileCornerStyle.SHARP)
                        }
                    )

                    SettingChoiceRow(
                        title = "yuvarlatılmış",
                        subtitle = "kavisli köşeler (8 dp)",
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
                                text = "karolar arası mesafe",
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
            android.widget.Toast.makeText(context, "Tarayıcı geçmişi temizlendi", android.widget.Toast.LENGTH_SHORT).show()
            showHistoryClearToast = false
        }
    }

    SettingsLazyColumn {
        item(key = "social") {
            SettingGroup(title = "social hub") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "zaman akışı",
                        subtitle = "tek liste",
                        selected = socialHubLayout == SocialHubLayout.TIMELINE,
                        onClick = { onSocialHubLayoutChanged(SocialHubLayout.TIMELINE) }
                    )
                    SettingChoiceRow(
                        title = "gruplu",
                        subtitle = "uygulamalara göre",
                        selected = socialHubLayout == SocialHubLayout.GROUPED,
                        onClick = { onSocialHubLayoutChanged(SocialHubLayout.GROUPED) }
                    )
                }
            }
        }

        item(key = "phone") {
            SettingGroup(title = "telefon hub") {
                SettingSwitchRow(
                    title = "doğrudan arama",
                    subtitle = if (directCallEnabled) "açık (hemen ara)" else "kapalı (çeviriciye kopyala)",
                    checked = directCallEnabled,
                    onCheckedChange = onDirectCallChanged
                )
            }
        }

        item(key = "internet") {
            SettingGroup(title = "internet hub") {
                SystemSettingRow(
                    title = "geçmişi temizle",
                    subtitle = "tarayıcı geçmişini siler",
                    onClick = {
                        onClearBrowserHistory()
                        showHistoryClearToast = true
                    }
                )
            }
        }

        item(key = "hub_order") {
            SettingGroup(title = "ana ekran hub sırası") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ana ekrandaki hub başlıklarının sıralamasını yukarı/aşağı butonları ile değiştirin:",
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
                                    text = "${index + 1}. ${hubType.title.lowercase()}",
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
                                        contentDescription = "Yukarı Taşımak",
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
                                        contentDescription = "Aşağı Taşımak",
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
                            text = "varsayılana sıfırla",
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
            SettingGroup(title = "bildirim filtreleme") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "bildirim izinli uygulamalar",
                        subtitle = if (disabledNotificationApps.isEmpty()) "tüm uygulamalar izinli" else "${disabledNotificationApps.size} uygulama engellendi",
                        selected = true,
                        onClick = onOpenAppFilter
                    )
                }
            }
        }

        item(key = "notification_style") {
            SettingGroup(title = "bildirim stili") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "windows phone (pop-up kart)",
                        subtitle = "üstten kayan kart + aşağı çekince hızlı yanıt",
                        selected = notificationStyle == NotificationStyle.WINDOWS_PHONE,
                        onClick = { viewModel.setNotificationStyle(NotificationStyle.WINDOWS_PHONE) }
                    )
                    SettingChoiceRow(
                        title = "android varsayılan bildirimleri",
                        subtitle = "sistem bildirimleri kullanılır",
                        selected = notificationStyle == NotificationStyle.SYSTEM,
                        onClick = { viewModel.setNotificationStyle(NotificationStyle.SYSTEM) }
                    )

                    val hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
                    if (notificationStyle == NotificationStyle.WINDOWS_PHONE && hasOverlayPermission) {
                        Text(
                            text = "diğer uygulamaların üstünde gösterim açık: WhatsApp gibi uygulamalar açıkken de Windows Phone kartı görünür",
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
                            Text("Üstte Gösterim İzni Ver (Gerekli)")
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
                            text = "test bildirimi gönder",
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
            SettingGroup(title = "ekran ve parlaklık") {
                GlassPanel {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "parlaklık",
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
            SettingGroup(title = "ses arayüzü stili") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = "windows phone (üst bar)",
                        subtitle = "ekranın üstünden kayan köşeli wp ses kontrolü",
                        selected = volumeBarStyle == com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE,
                        onClick = { viewModel.setVolumeBarStyle(com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE) }
                    )
                    SettingChoiceRow(
                        title = "android varsayılan ses barı",
                        subtitle = "sistemin kendi ses paneli kullanılır",
                        selected = volumeBarStyle == com.serkantkn.zunelauncher.data.model.VolumeBarStyle.SYSTEM,
                        onClick = { viewModel.setVolumeBarStyle(com.serkantkn.zunelauncher.data.model.VolumeBarStyle.SYSTEM) }
                    )

                    if (volumeBarStyle == com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE) {
                        val ctx = LocalContext.current
                        val hasOverlay = android.provider.Settings.canDrawOverlays(ctx)
                        val hasKeyService = com.serkantkn.zunelauncher.data.service.ZuneKeyAccessibilityService.isEnabled(ctx)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "diğer uygulamalar açıkken de wp ses barı için iki izin gerekir",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalZuneColors.current.textMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SystemSettingRow(
                            title = "üstte gösterim izni",
                            subtitle = if (hasOverlay) "verildi" else "ses barının diğer uygulamaların üstünde çizilmesi için",
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
                            title = "ses tuşlarını yakalama (erişilebilirlik)",
                            subtitle = if (hasKeyService) "etkin" else "Z Launcher ses tuşları servisini erişilebilirlik ayarlarından aç",
                            isActive = hasKeyService,
                            onClick = { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        )
                    }
                }
            }
        }

        item(key = "volume") {
            SettingGroup(title = "ses seviyeleri") {
                GlassPanel {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "medya sesi",
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
                            text = "zil sesi",
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
            SettingGroup(title = "bağlantılar") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = "wi-fi",
                        subtitle = "ağları yönet",
                        onClick = viewModel::openWifiSettings
                    )
                    SystemSettingRow(
                        title = "bluetooth",
                        subtitle = "cihazları eşleştir",
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

    SettingsLazyColumn {

        item(key = "date_time") {
            SettingGroup(title = "tarih ve saat") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = "tarih+saat ayarları",
                        subtitle = "ana ekran saat ve tarih formatını değiştirin",
                        onClick = onOpenDateTimeSettings
                    )
                }
            }
        }

        item(key = "device") {
            SettingGroup(title = "cihaz") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = "varsayılan telefon uygulaması",
                        subtitle = "arama ve çevirici uygulaması",
                        isActive = isDefaultDialer,
                        onClick = { viewModel.requestDefaultPhoneApp(context, roleLauncher) }
                    )
                    SystemSettingRow(
                        title = "varsayılan sms uygulaması",
                        subtitle = "mesajlaşma uygulaması",
                        isActive = isDefaultSms,
                        onClick = { viewModel.requestDefaultSmsApp(context, roleLauncher) }
                    )
                    SystemSettingRow(
                        title = "varsayılan internet tarayıcısı",
                        subtitle = "web tarayıcı uygulaması",
                        isActive = isDefaultBrowser,
                        onClick = { viewModel.requestDefaultBrowserApp(context, roleLauncher) }
                    )
                    SystemSettingRow(
                        title = "varsayılan launcher",
                        subtitle = "ana ekran uygulaması",
                        isActive = isDefaultLauncher,
                        onClick = viewModel::openDefaultAppsSettings
                    )
                    SystemSettingRow(
                        title = "bildirim erişimi",
                        subtitle = "social hub izinleri",
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
            SettingGroup(title = "zune launcher") {
                Column(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = "sürüm 1.0",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "microsoft zune hd ve windows phone metro tasarımından ilham alınmıştır",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = LocalZuneColors.current.textMuted
                    )
                }
            }
        }

        item(key = "permissions") {
            SettingGroup(title = "uygulamaya verilen izinler") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "uygulamanın tüm özelliklerini sorunsuz kullanabilmek için aşağıdaki izinleri kontrol edebilir ve değiştirebilirsiniz:",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textMuted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )

                    SettingSwitchRow(
                        title = "kişiler erişimi",
                        subtitle = "kişiler hubı ve telefon rehberi eşleştirmeleri için",
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
                        title = "arama kayıtları",
                        subtitle = "telefon hubında son aramaları ve geçmişi göstermek için",
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
                        title = "sms ve mesajlaşma",
                        subtitle = "mesajlar hubında sms almak ve yanıtlamak için",
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
                        title = "dosya ve depolama",
                        subtitle = "dosyalar hubı ve özel duvar kağıdı yüklemek için",
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
                        title = "bildirim dinleme izni",
                        subtitle = "social hub ve kilit ekranında canlı bildirimler için",
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
                        title = "üstte gösterim izni",
                        subtitle = "windows phone tarzı pop-up kartlar ve bildirim paneli için",
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
        e.printStackTrace()
    }
}
