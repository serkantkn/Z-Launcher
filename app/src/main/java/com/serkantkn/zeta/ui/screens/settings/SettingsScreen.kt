package com.serkantkn.zeta.ui.screens.settings

import com.serkantkn.zeta.ui.animation.pageHingeAway
import com.serkantkn.zeta.ui.animation.pageHingeIn
import androidx.annotation.StringRes
import com.serkantkn.zeta.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zeta.ui.animation.HingeAnimation
import com.serkantkn.zeta.ui.components.ZuneHub
import com.serkantkn.zeta.ui.components.rememberLoopingPagerState
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch
import com.serkantkn.zeta.ui.animation.rememberHingeSpec

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

/** The settings pages a television does without. */
private val TV_LEFT_OUT = setOf(SettingsTab.NOTIFICATIONS, SettingsTab.DISPLAY_AND_SOUND, SettingsTab.KEYBOARD)

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
    val isTv = com.serkantkn.zeta.ui.theme.LocalIsTv.current
    val tabs = remember {
        SettingsTab.entries.filter {
            // A television has no shade, no volume bar of ours, no screen to dim and no keyboard
            // of ours to set up: those pages stay on the phone.
            if (isTv && it in TV_LEFT_OUT) return@filter false
            it != SettingsTab.KEYBOARD || com.serkantkn.zeta.util.Pro.unlocked
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

    // 3D Door Hinge transition state for the launcher's own page (version and release notes)
    val versionHingeAnim = remember { Animatable(0f) }
    var isVersionOpen by remember { mutableStateOf(false) }

    fun openVersion() {
        isVersionOpen = true
        scope.launch { versionHingeAnim.animateTo(targetValue = 1f, animationSpec = hingeSpec) }
    }

    fun closeVersion() {
        scope.launch {
            versionHingeAnim.animateTo(targetValue = 0f, animationSpec = hingeSpec)
            isVersionOpen = false
        }
    }

    // 3D Door Hinge transition state for the log of other apps' hubs (developer mode)
    val hubLogHingeAnim = remember { Animatable(0f) }
    var isHubLogOpen by remember { mutableStateOf(false) }

    fun openHubLog() {
        isHubLogOpen = true
        scope.launch { hubLogHingeAnim.animateTo(targetValue = 1f, animationSpec = hingeSpec) }
    }

    fun closeHubLog() {
        scope.launch {
            hubLogHingeAnim.animateTo(targetValue = 0f, animationSpec = hingeSpec)
            isHubLogOpen = false
        }
    }

    val subScreenHingeProgress =
        maxOf(dateTimeHingeAnim.value, lookHingeAnim.value, sourcesHingeAnim.value, calendarsHingeAnim.value, versionHingeAnim.value, hubLogHingeAnim.value)

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Settings Main Screen (Hinges Out to -90° when filter screen opens)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pageHingeAway { subScreenHingeProgress }
        ) {
            ZuneHub(
                title = stringResource(R.string.common_settings),
                tabs = tabs.map { stringResource(it.titleRes) },
                pager = pager,
                zuneTitleKey = "SETTINGS",
                phonePeek = true
            ) { index ->
                // A settings page is a list of lines; across a whole tablet they would be too
                // long to read, so there the page keeps to a column.
                Box(
                    modifier = if (isWideScreen) Modifier.widthIn(max = 720.dp).fillMaxHeight() else Modifier.fillMaxSize()
                ) {
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
                            onOpenCalendars = { openCalendars() },
                            onOpenHubLog = { openHubLog() }
                        )
                        SettingsTab.NOTIFICATIONS -> NotificationsSettingsPage(viewModel = viewModel)
                        SettingsTab.DISPLAY_AND_SOUND -> DisplayAndSoundSettingsPage(viewModel = viewModel)
                        SettingsTab.KEYBOARD -> KeyboardSettingsPage(viewModel = viewModel)
                        SettingsTab.CONNECTIVITY -> ConnectivitySettingsPage()
                        SettingsTab.SYSTEM -> SystemSettingsPage(
                            viewModel = viewModel,
                            onOpenDateTimeSettings = { openDateTimeSettings() }
                        )
                        SettingsTab.ABOUT -> AboutSettingsPage(onOpenVersion = { openVersion() })
                    }
                }
            }
        }

        // 3. Standalone look sub-page (Hinges In from 90° to 0°)
        openLookPage?.let { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pageHingeIn { lookHingeAnim.value }
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
                    .pageHingeIn { sourcesHingeAnim.value }
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
                    .pageHingeIn { calendarsHingeAnim.value }
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
                    .pageHingeIn { dateTimeHingeAnim.value }
            ) {
                DateTimeSettingsScreen(
                    viewModel = viewModel,
                    onClose = { closeDateTimeSettings() }
                )
            }
        }

        // 6. The launcher's own page: icon, name, version and release notes (Hinges In)
        if (isVersionOpen || versionHingeAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pageHingeIn { versionHingeAnim.value }
            ) {
                AboutVersionScreen(onClose = { closeVersion() })
            }
        }

        // 7. The log of other apps' hubs, in developer mode (Hinges In)
        if (isHubLogOpen || hubLogHingeAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pageHingeIn { hubLogHingeAnim.value }
            ) {
                com.serkantkn.zeta.ui.screens.external.HubLogScreen(onClose = { closeHubLog() })
            }
        }
    }
}
