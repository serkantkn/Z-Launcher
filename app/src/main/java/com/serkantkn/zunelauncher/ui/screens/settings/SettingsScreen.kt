package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

private enum class SettingsTab(val title: String) {
    LOOK("görünüm"),
    HUBS("hub"),
    NOTIFICATIONS("bildirimler"),
    DISPLAY_AND_SOUND("ekran+ses"),
    CONNECTIVITY("bağlantılar"),
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
    val disabledNotificationApps by viewModel.disabledNotificationApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState(initial = emptyList())

    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val tabs = SettingsTab.entries
    val actualPageCount = tabs.size
    val loopCount = 1000
    val initialPage = (loopCount / 2) * actualPageCount
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { loopCount * actualPageCount }
    )
    val targetTab by viewModel.targetTab.collectAsState()

    LaunchedEffect(targetTab) {
        targetTab?.let { tabName ->
            val foundIndex = tabs.indexOfFirst {
                it.name.equals(tabName, ignoreCase = true) || it.title.equals(tabName, ignoreCase = true)
            }
            if (foundIndex >= 0) {
                val current = pagerState.currentPage
                val size = actualPageCount
                val currentActual = ((current % size) + size) % size
                var diff = foundIndex - currentActual
                if (diff > size / 2) diff -= size
                if (diff < -size / 2) diff += size
                val targetPage = current + diff
                pagerState.scrollToPage(targetPage)
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
                    val configuration = LocalConfiguration.current
                    val overflowYPx = with(density) { (-24).dp.toPx() }

                    if (isWideScreen) {
                        Text(
                            text = "ayarlar",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 96.sp,
                                letterSpacing = (-4).sp,
                                lineHeight = 96.sp
                            ),
                            color = if (zuneColors.isDark) Color.White else Color.Black,
                            modifier = Modifier
                                .padding(
                                    start = 72.dp,
                                    top = 4.dp,
                                    bottom = 24.dp
                                )
                                .graphicsLayer { translationY = overflowYPx }
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 72.dp,
                                end = 48.dp
                            ),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(48.dp)
                        ) {
                            items(tabs.size) { index ->
                                Column(modifier = Modifier.width(360.dp)) {
                                    Text(
                                        text = tabs[index].title,
                                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                                        color = zuneColors.accentColor,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
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
                            }
                        }
                    } else {
                        // Fixed small header title
                        Text(
                            text = "ayarlar",
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
                            tabs = tabs.map { it.title },
                            pagerState = pagerState,
                            onSelected = { index ->
                                val current = pagerState.currentPage
                                val size = actualPageCount
                                val currentActual = ((current % size) + size) % size
                                var diff = index - currentActual
                                if (diff > size / 2) diff -= size
                                if (diff < -size / 2) diff += size
                                val targetPage = current + diff
                                scope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            },
                            modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                        )

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = ZuneDimens.ScreenPaddingHorizontal,
                                end = 48.dp
                            ),
                            pageSpacing = 24.dp
                        ) { page ->
                            val actualPage = page % actualPageCount
                            when (tabs[actualPage]) {
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
