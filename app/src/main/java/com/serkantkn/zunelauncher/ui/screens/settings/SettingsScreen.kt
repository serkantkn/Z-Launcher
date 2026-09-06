package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

private enum class SettingsTab(@StringRes val titleRes: Int) {
    LOOK(R.string.settings_tab_look),
    HUBS(R.string.settings_tab_hubs),
    NOTIFICATIONS(R.string.settings_tab_notifications),
    DISPLAY_AND_SOUND(R.string.settings_tab_display_sound),
    CONNECTIVITY(R.string.settings_tab_connectivity),
    SYSTEM(R.string.settings_tab_system),
    ABOUT(R.string.settings_tab_about)
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
    val disabledNotificationApps by viewModel.disabledNotificationApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState(initial = emptyList())

    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val tabs = SettingsTab.entries
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    val targetTab by viewModel.targetTab.collectAsState()

    LaunchedEffect(targetTab) {
        targetTab?.let { tabName ->
            val foundIndex = tabs.indexOfFirst {
                it.name.equals(tabName, ignoreCase = true)
            }
            if (foundIndex >= 0) {
                pager.scrollToPage(foundIndex)
            }
            viewModel.clearTargetTab()
        }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 3D Door Hinge transition state for AppNotificationFilterScreen
    val filterHingeAnim = remember { Animatable(0f) }
    var isFilterOpen by remember { mutableStateOf(false) }

    fun openAppFilter() {
        isFilterOpen = true
        scope.launch {
            filterHingeAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing)
            )
        }
    }

    fun closeAppFilter() {
        scope.launch {
            filterHingeAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing)
            )
            isFilterOpen = false
        }
    }

    // 3D Door Hinge transition state for DateTimeSettingsScreen
    val dateTimeHingeAnim = remember { Animatable(0f) }
    var isDateTimeOpen by remember { mutableStateOf(false) }

    fun openDateTimeSettings() {
        isDateTimeOpen = true
        scope.launch {
            dateTimeHingeAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing)
            )
        }
    }

    fun closeDateTimeSettings() {
        scope.launch {
            dateTimeHingeAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing)
            )
            isDateTimeOpen = false
        }
    }

    val subScreenHingeProgress = maxOf(filterHingeAnim.value, dateTimeHingeAnim.value)

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Settings Main Screen (Hinges Out to -90° when filter screen opens)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = subScreenHingeProgress
                    rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * p
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                    alpha = (1f - p * 1.5f).coerceIn(0f, 1f)
                }
        ) {
            ZuneHubEntranceLayout { bottomBarModifier ->
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isWideScreen) {
                        ZuneWideHubTitle(text = stringResource(R.string.common_settings))

                        ZuneWidePanorama(tabs = tabs.map { stringResource(it.titleRes) }) { index ->
                            when (tabs[index]) {
                                SettingsTab.LOOK -> LookSettingsPage(
                                    viewModel = viewModel,
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
                                    viewModel = viewModel,
                                    socialHubLayout = socialHubLayout,
                                    directCallEnabled = directCallEnabled,
                                    onSocialHubLayoutChanged = viewModel::setSocialHubLayout,
                                    onDirectCallChanged = viewModel::setDirectCallEnabled,
                                    onClearBrowserHistory = viewModel::clearBrowserHistory
                                )
                                SettingsTab.NOTIFICATIONS -> NotificationsSettingsPage(
                                    viewModel = viewModel,
                                    onOpenAppFilter = { openAppFilter() }
                                )
                                SettingsTab.DISPLAY_AND_SOUND -> DisplayAndSoundSettingsPage(viewModel = viewModel)
                                SettingsTab.CONNECTIVITY -> ConnectivitySettingsPage(viewModel = viewModel)
                                SettingsTab.SYSTEM -> SystemSettingsPage(viewModel = viewModel)
                                SettingsTab.ABOUT -> AboutSettingsPage()
                            }
                        }
                    } else {
                        // Fixed small header title
                        Text(
                            text = stringResource(R.string.common_settings),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                letterSpacing = 1.sp
                            ),
                            color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(
                                top = 28.dp,
                                bottom = 4.dp,
                                start = ZuneDimens.ScreenPaddingHorizontal
                            )
                        )

                        // Giant pivot tabs
                        ZunePivotTabs(
                            tabs = tabs.map { stringResource(it.titleRes) },
                            state = pager,
                            modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                        )

                        ZuneLoopingPager(
                            state = pager,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = ZuneDimens.ScreenPaddingHorizontal,
                                end = 48.dp
                            ),
                            pageSpacing = 24.dp
                        ) { page ->
                            when (tabs[page]) {
                                SettingsTab.LOOK -> LookSettingsPage(
                                    viewModel = viewModel,
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
                                    viewModel = viewModel,
                                    socialHubLayout = socialHubLayout,
                                    directCallEnabled = directCallEnabled,
                                    onSocialHubLayoutChanged = viewModel::setSocialHubLayout,
                                    onDirectCallChanged = viewModel::setDirectCallEnabled,
                                    onClearBrowserHistory = viewModel::clearBrowserHistory
                                )
                                SettingsTab.NOTIFICATIONS -> NotificationsSettingsPage(
                                    viewModel = viewModel,
                                    onOpenAppFilter = { openAppFilter() }
                                )
                                SettingsTab.DISPLAY_AND_SOUND -> DisplayAndSoundSettingsPage(viewModel = viewModel)
                                SettingsTab.CONNECTIVITY -> ConnectivitySettingsPage(viewModel = viewModel)
                                SettingsTab.SYSTEM -> SystemSettingsPage(
                                    viewModel = viewModel,
                                    onOpenDateTimeSettings = { openDateTimeSettings() }
                                )
                                SettingsTab.ABOUT -> AboutSettingsPage()
                            }
                        }
                    }
                }
            }
        }

        // 2. Standalone AppNotificationFilterScreen (Hinges In from 90° to 0°)
        if (isFilterOpen || filterHingeAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = filterHingeAnim.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                AppNotificationFilterScreen(
                    disabledApps = disabledNotificationApps,
                    installedApps = installedApps,
                    onClose = { closeAppFilter() },
                    onSave = { updatedApps ->
                        viewModel.setDisabledNotificationApps(updatedApps)
                    }
                )
            }
        }

        // 3. Standalone DateTimeSettingsScreen (Hinges In from 90° to 0°)
        if (isDateTimeOpen || dateTimeHingeAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = dateTimeHingeAnim.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                DateTimeSettingsScreen(
                    viewModel = viewModel,
                    onClose = { closeDateTimeSettings() }
                )
            }
        }
    }
}
