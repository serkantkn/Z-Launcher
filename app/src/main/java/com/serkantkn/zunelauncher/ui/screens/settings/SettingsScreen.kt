package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.ui.components.ZuneGlassSurface
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.ui.theme.toColor
import kotlinx.coroutines.launch

private enum class SettingsTab(val title: String) {
    LOOK("görünüm"),
    HUBS("hub"),
    SYSTEM("sistem"),
    ABOUT("hakkında")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val socialHubLayout by viewModel.socialHubLayout.collectAsState()
    val directCallEnabled by viewModel.directCallEnabled.collectAsState()
    val zuneColors = LocalZuneColors.current
    val tabs = SettingsTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = "ayarlar",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            ),
            color = zuneColors.accentColor,
            modifier = Modifier.padding(
                top = 60.dp,
                bottom = 4.dp,
                start = ZuneDimens.ScreenPaddingHorizontal,
                end = ZuneDimens.ScreenPaddingHorizontal
            )
        )

        ZunePivotTabs(
            tabs = tabs.map { it.title },
            pagerState = pagerState,
            onSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ZuneDimens.ScreenPaddingHorizontal,
                end = 48.dp
            ),
            pageSpacing = 24.dp
        ) { page ->
            when (tabs[page]) {
                SettingsTab.LOOK -> LookSettingsPage(
                    themeMode = themeMode,
                    accentColor = accentColor,
                    fontScale = fontScale,
                    animationsEnabled = animationsEnabled,
                    onThemeModeChanged = viewModel::setThemeMode,
                    onAccentColorChanged = viewModel::setAccentColor,
                    onFontScaleChanged = viewModel::setFontScale,
                    onAnimationsChanged = viewModel::setAnimationsEnabled
                )

                SettingsTab.HUBS -> HubSettingsPage(
                    socialHubLayout = socialHubLayout,
                    directCallEnabled = directCallEnabled,
                    onSocialHubLayoutChanged = viewModel::setSocialHubLayout,
                    onDirectCallChanged = viewModel::setDirectCallEnabled
                )

                SettingsTab.SYSTEM -> SystemSettingsPage(viewModel = viewModel)
                SettingsTab.ABOUT -> AboutSettingsPage()
            }
        }
    }
}

@Composable
private fun LookSettingsPage(
    themeMode: ThemeMode,
    accentColor: AccentColor,
    fontScale: Float,
    animationsEnabled: Boolean,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onAccentColorChanged: (AccentColor) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onAnimationsChanged: (Boolean) -> Unit
) {
    SettingsLazyColumn {
        item(key = "theme") {
            SettingGroup(title = "tema") {
                ThemeChoiceRow(
                    selectedMode = themeMode,
                    onSelected = onThemeModeChanged
                )
            }
        }

        item(key = "accent") {
            SettingGroup(title = "vurgu rengi") {
                AccentColorChoiceRow(
                    selectedColor = accentColor,
                    onSelected = onAccentColorChanged
                )
            }
        }

        item(key = "font") {
            SettingGroup(title = "yazı") {
                GlassPanel {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "boyut",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Light
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            AnimatedContent(
                                targetState = "${(fontScale * 100).toInt()}%",
                                transitionSpec = {
                                    (fadeIn() + slideInHorizontally { it / 3 })
                                        .togetherWith(fadeOut() + slideOutHorizontally { -it / 3 })
                                        .using(SizeTransform(clip = false))
                                },
                                label = "font_scale_value"
                            ) { value ->
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalZuneColors.current.accentColor
                                )
                            }
                        }
                        Slider(
                            value = fontScale,
                            onValueChange = onFontScaleChanged,
                            valueRange = 0.8f..1.4f,
                            steps = 5,
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

        item(key = "animations") {
            SettingGroup(title = "hareket") {
                SettingSwitchRow(
                    title = "animasyonlar",
                    subtitle = if (animationsEnabled) "açık" else "kapalı",
                    checked = animationsEnabled,
                    onCheckedChange = onAnimationsChanged
                )
            }
        }
    }
}

@Composable
private fun HubSettingsPage(
    socialHubLayout: SocialHubLayout,
    directCallEnabled: Boolean,
    onSocialHubLayoutChanged: (SocialHubLayout) -> Unit,
    onDirectCallChanged: (Boolean) -> Unit
) {
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
    }
}

@Composable
private fun SystemSettingsPage(viewModel: SettingsViewModel) {
    val brightness by viewModel.brightness.collectAsState()
    val mediaVolume by viewModel.mediaVolume.collectAsState()
    val ringVolume by viewModel.ringVolume.collectAsState()

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

        item(key = "device") {
            SettingGroup(title = "cihaz") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemSettingRow(
                        title = "bildirim erişimi",
                        subtitle = "social hub izinleri",
                        onClick = viewModel::openNotificationAccessSettings
                    )
                    SystemSettingRow(
                        title = "varsayılan launcher",
                        subtitle = "ana ekran uygulaması",
                        onClick = viewModel::openDefaultAppsSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSettingsPage() {
    SettingsLazyColumn {
        item(key = "about") {
            SettingGroup(title = "zune launcher") {
                GlassPanel {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "sürüm 1.0",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Light
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "microsoft zune hd ve windows phone metro tasarımından ilham alınmıştır",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalZuneColors.current.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLazyColumn(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        val originalContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = content
        
        // A simple trick to wrap each item with an AnimatedVisibility
        // Wait, standard lazy column doesn't easily let us wrap individual `item` calls without redefining them.
        // Instead, we just apply a staggered slide in on the items that are present.
        originalContent()
    }
}

@Composable
private fun SettingGroup(
    title: String,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it / 4 }, 
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(300)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                ),
                color = LocalZuneColors.current.textMuted,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun ThemeChoiceRow(
    selectedMode: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.LIGHT -> "açık"
                ThemeMode.DARK -> "koyu"
                ThemeMode.SYSTEM -> "sistem"
            }
            SettingPill(
                label = label,
                selected = selectedMode == mode,
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccentColorChoiceRow(
    selectedColor: AccentColor,
    onSelected: (AccentColor) -> Unit
) {
    // We display colored circles in a horizontally scrollable row
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(AccentColor.entries.toTypedArray()) { color ->
            val composeColor = color.toColor()
            val isSelected = selectedColor == color
            
            Box(
                modifier = Modifier
                    .size(if (isSelected) 48.dp else 40.dp)
                    .background(color = composeColor, shape = CircleShape)
                    .clickable { onSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    // Draw a white/black inner dot or ring if selected
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val height by animateDpAsState(
        targetValue = if (selected) 58.dp else 52.dp,
        label = "setting_pill_height"
    )

    GlassPanel(
        modifier = modifier
            .height(height)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        selected = selected
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Light
                ),
                color = if (selected) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun SettingChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GlassPanel(
        selected = selected,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                SelectionDot(selected = selected)
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassPanel(selected = checked) {
        SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalZuneColors.current.accentColor,
                        checkedTrackColor = LocalZuneColors.current.accentColor.copy(alpha = 0.42f)
                    )
                )
            }
        )
    }
}

@Composable
private fun SystemSettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = LocalZuneColors.current.textMuted
                )
            }
        )
    }
}

@Composable
private fun SettingRowContent(
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalZuneColors.current.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        trailing()
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    val size by animateDpAsState(
        targetValue = if (selected) 14.dp else 8.dp,
        label = "selection_dot_size"
    )
    val color = if (selected) {
        LocalZuneColors.current.accentColor
    } else {
        LocalZuneColors.current.textDim.copy(alpha = 0.45f)
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(color = color, shape = CircleShape)
    )
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val tint = if (selected) {
        zuneColors.accentColor.copy(alpha = 0.18f)
    } else if (zuneColors.isDark) {
        Color.White.copy(alpha = 0.05f)
    } else {
        Color.Black.copy(alpha = 0.05f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(tint)
    ) {
        content()
    }
}
