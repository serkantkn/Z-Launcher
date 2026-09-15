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
import com.serkantkn.zunelauncher.ui.animation.rememberHingeSpec

private enum class SettingsTab(@StringRes val titleRes: Int) {
    LOOK(R.string.settings_tab_look),
    HUBS(R.string.settings_tab_hubs),
    NOTIFICATIONS(R.string.settings_tab_notifications),
    DISPLAY_AND_SOUND(R.string.settings_tab_display_sound),
    KEYBOARD(R.string.settings_tab_keyboard),
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
    val directCallEnabled by viewModel.directCallEnabled.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState(initial = emptyList())

    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val tabs = remember {
        SettingsTab.entries.filter {
            it != SettingsTab.KEYBOARD || com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM
        }
    }
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

    // 3D Door Hinge transition state for SocialSourcesSettingsScreen
    val hingeSpec = rememberHingeSpec()
    val sourcesHingeAnim = remember { Animatable(0f) }
    var isSourcesOpen by remember { mutableStateOf(false) }

    fun openSocialSources() {
        isSourcesOpen = true
        scope.launch {
            sourcesHingeAnim.animateTo(
                targetValue = 1f,
                animationSpec = hingeSpec
            )
        }
    }

    fun closeSocialSources() {
        scope.launch {
            sourcesHingeAnim.animateTo(
                targetValue = 0f,
                animationSpec = hingeSpec
            )
            isSourcesOpen = false
        }
    }

    // 3D Door Hinge transition state for CalendarsSettingsScreen
    val calendarsHingeAnim = remember { Animatable(0f) }
    var isCalendarsOpen by remember { mutableStateOf(false) }

    fun openCalendars() {
        isCalendarsOpen = true
        scope.launch {
            calendarsHingeAnim.animateTo(
                targetValue = 1f,
                animationSpec = hingeSpec
            )
        }
    }

    fun closeCalendars() {
        scope.launch {
            calendarsHingeAnim.animateTo(
                targetValue = 0f,
                animationSpec = hingeSpec
            )
            isCalendarsOpen = false
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
                animationSpec = hingeSpec
            )
        }
    }

    fun closeDateTimeSettings() {
        scope.launch {
            dateTimeHingeAnim.animateTo(
                targetValue = 0f,
                animationSpec = hingeSpec
            )
            isDateTimeOpen = false
        }
    }

    // 3D Door Hinge transition state for the look sub-pages
    val lookHingeAnim = remember { Animatable(0f) }
    var openLookPage by remember { mutableStateOf<LookPage?>(null) }

    fun openLookSettings(page: LookPage) {
        openLookPage = page
        scope.launch {
            lookHingeAnim.animateTo(
                targetValue = 1f,
                animationSpec = hingeSpec
            )
        }
    }

    fun closeLookSettings() {
        scope.launch {
            lookHingeAnim.animateTo(
                targetValue = 0f,
                animationSpec = hingeSpec
            )
            openLookPage = null
        }
    }

    val subScreenHingeProgress =
        maxOf(dateTimeHingeAnim.value, lookHingeAnim.value, sourcesHingeAnim.value, calendarsHingeAnim.value)

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
                                    onOpenPage = { openLookSettings(it) }
                                )
                                SettingsTab.HUBS -> HubSettingsPage(
                                    viewModel = viewModel,
                                    directCallEnabled = directCallEnabled,
                                    onDirectCallChanged = viewModel::setDirectCallEnabled,
                                    onOpenSocialSources = { openSocialSources() },
                                    onOpenCalendars = { openCalendars() }
                                )
                                SettingsTab.NOTIFICATIONS -> NotificationsSettingsPage(viewModel = viewModel)
                                SettingsTab.DISPLAY_AND_SOUND -> DisplayAndSoundSettingsPage(viewModel = viewModel)
                                SettingsTab.KEYBOARD -> KeyboardSettingsPage(viewModel = viewModel)
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
                                    onOpenPage = { openLookSettings(it) }
                                )
                                SettingsTab.HUBS -> HubSettingsPage(
                                    viewModel = viewModel,
                                    directCallEnabled = directCallEnabled,
                                    onDirectCallChanged = viewModel::setDirectCallEnabled,
                                    onOpenSocialSources = { openSocialSources() },
                                    onOpenCalendars = { openCalendars() }
                                )
                                SettingsTab.NOTIFICATIONS -> NotificationsSettingsPage(viewModel = viewModel)
                                SettingsTab.DISPLAY_AND_SOUND -> DisplayAndSoundSettingsPage(viewModel = viewModel)
                                SettingsTab.KEYBOARD -> KeyboardSettingsPage(viewModel = viewModel)
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

        // 3. Standalone look sub-page (Hinges In from 90° to 0°)
        openLookPage?.let { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = lookHingeAnim.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                LookSubSettingsScreen(
                    page = page,
                    viewModel = viewModel,
                    onClose = { closeLookSettings() }
                )
            }
        }

        // 4. Standalone SocialSourcesSettingsScreen (Hinges In from 90° to 0°)
        if (isSourcesOpen || sourcesHingeAnim.value > 0f) {
            val socialSourceList by viewModel.socialSourceList.collectAsState()
            val socialOtherApps by viewModel.socialOtherApps.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = sourcesHingeAnim.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                SocialSourcesSettingsScreen(
                    sources = socialSourceList,
                    otherApps = socialOtherApps,
                    onToggle = viewModel::setSocialSource,
                    onReset = { viewModel.resetSocialSources() },
                    onClose = { closeSocialSources() }
                )
            }
        }

        // 4b. Standalone CalendarsSettingsScreen (Hinges In from 90° to 0°)
        if (isCalendarsOpen || calendarsHingeAnim.value > 0f) {
            val calendars by viewModel.calendars.collectAsState()
            val visibleCalendars by viewModel.visibleCalendars.collectAsState()
            val defaultCalendarId by viewModel.defaultCalendarId.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = calendarsHingeAnim.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                CalendarsSettingsScreen(
                    calendars = calendars,
                    visibleIds = visibleCalendars,
                    defaultCalendarId = defaultCalendarId,
                    onToggle = viewModel::setCalendarVisible,
                    onSetDefault = viewModel::setDefaultCalendar,
                    onClose = { closeCalendars() }
                )
            }
        }

        // 5. Standalone DateTimeSettingsScreen (Hinges In from 90° to 0°)
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
