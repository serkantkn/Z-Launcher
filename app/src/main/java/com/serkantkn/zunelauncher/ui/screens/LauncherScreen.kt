package com.serkantkn.zunelauncher.ui.screens

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.HubBackgroundMode
import com.serkantkn.zunelauncher.data.model.NotificationStyle
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import com.serkantkn.zunelauncher.di.appContainer
import kotlinx.coroutines.flow.first
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.BackgroundMode
import com.serkantkn.zunelauncher.ui.components.WpToastNotification
import com.serkantkn.zunelauncher.ui.components.WpVolumeControl
import com.serkantkn.zunelauncher.ui.components.ZuneBackground
import com.serkantkn.zunelauncher.ui.components.ZuneWallpaperOverlay
import com.serkantkn.zunelauncher.ui.navigation.ZuneNavigationState
import com.serkantkn.zunelauncher.ui.navigation.rememberZuneNavigationState
import com.serkantkn.zunelauncher.ui.screens.apps.AppsHubScreen
import com.serkantkn.zunelauncher.ui.screens.home.HomeHubScreen
import com.serkantkn.zunelauncher.ui.screens.music.MusicHubScreen
import com.serkantkn.zunelauncher.ui.screens.people.PeopleHubScreen
import com.serkantkn.zunelauncher.ui.screens.phone.PhoneHubScreen
import com.serkantkn.zunelauncher.ui.screens.pictures.PicturesHubScreen
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsScreen
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsViewModel
import com.serkantkn.zunelauncher.ui.screens.social.SocialHubScreen
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.screens.onboarding.FirstRunScreen
import com.serkantkn.zunelauncher.ui.screens.onboarding.OnboardingScreen
import com.serkantkn.zunelauncher.ui.screens.onboarding.OnboardingViewModel
import com.serkantkn.zunelauncher.ui.screens.onboarding.WhatsNewScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import com.serkantkn.zunelauncher.ui.animation.rememberHingeSpec

private fun setStatusBarExpandDisabled(context: Context, disabled: Boolean) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val disableMethod = statusBarManager.getMethod("disable", Int::class.javaPrimitiveType)

        val flag = if (disabled) {
            val field = statusBarManager.getField("DISABLE_EXPAND")
            field.getInt(null)
        } else {
            0
        }
        disableMethod.invoke(statusBarService, flag)
    } catch (e: Exception) {
        ZuneLog.e("LauncherScreen", "setStatusBarExpandDisabled failed", e)
    }
}

@Composable
fun LauncherScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val navState = rememberZuneNavigationState()
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { 3 }
    )

    val notificationStyle by settingsViewModel.notificationStyle.collectAsState()
    val socialSources by settingsViewModel.socialSources.collectAsState()
    val solidBackgroundEnabled by settingsViewModel.solidBackgroundEnabled.collectAsState()
    val timeFormat by settingsViewModel.timeFormat.collectAsState()
    val dateFormat by settingsViewModel.dateFormat.collectAsState()
    val hubBackgroundMode by settingsViewModel.hubBackgroundMode.collectAsState()
    val hubBackgroundOpacity by settingsViewModel.hubBackgroundOpacity.collectAsState()
    val customHubWallpaperPath by settingsViewModel.customHubWallpaperPath.collectAsState()
    val customWallpaperPath by settingsViewModel.customWallpaperPath.collectAsState()

    // The notification listener asks the repository on every single notification, so the answer is
    // pushed here — from the launcher, which is always composed — rather than from the hub's own
    // screen, which is not alive until somebody swipes to it.
    val launcherContext = LocalContext.current
    LaunchedEffect(socialSources) {
        val chosen = socialSources
        val resolved = chosen ?: com.serkantkn.zunelauncher.data.model.SocialApps.defaultSources(
            launcherContext.appContainer.appRepository
                .getInstalledApps()
                .first()
                .map { app -> app.packageName }
        )
        SocialRepository.updateSources(resolved)
    }

    val latestNotification by SocialRepository.latestToastMessage.collectAsState()

    // Cross-hub requests for the Notes hub (Start tiles, Calendar, People, Social, share sheet)
    val pendingNoteRequest by com.serkantkn.zunelauncher.data.repository.NotesBridge.pending.collectAsState()
    LaunchedEffect(pendingNoteRequest) {
        if (pendingNoteRequest != null && navState.currentHub != HubType.NOTES) {
            navState.openHub(HubType.NOTES)
        }
    }
    // Cross-hub requests for the Email hub (notifications, mailto:, share sheet)
    val pendingEmailRequest by com.serkantkn.zunelauncher.data.repository.EmailBridge.pending.collectAsState()
    LaunchedEffect(pendingEmailRequest) {
        if (pendingEmailRequest != null && navState.currentHub != HubType.EMAIL) {
            navState.openHub(HubType.EMAIL)
        }
    }
    // Cross-hub requests for the Messaging hub (pinned conversations, notifications, sms: links)
    val pendingMessageThread by com.serkantkn.zunelauncher.data.repository.MessagingBridge.pendingThreadId.collectAsState()
    LaunchedEffect(pendingMessageThread) {
        if (pendingMessageThread != null && navState.currentHub != HubType.MESSAGING) {
            navState.openHub(HubType.MESSAGING)
        }
    }
    val pendingMessageCompose by com.serkantkn.zunelauncher.data.repository.MessagingBridge.pendingCompose.collectAsState()
    LaunchedEffect(pendingMessageCompose) {
        if (pendingMessageCompose != null && navState.currentHub != HubType.MESSAGING) {
            navState.openHub(HubType.MESSAGING)
        }
    }
    // The Social hub handing a notification to one of the launcher's own hubs
    val pendingHub by com.serkantkn.zunelauncher.data.repository.HubBridge.pendingHub.collectAsState()
    LaunchedEffect(pendingHub) {
        val hub = pendingHub ?: return@LaunchedEffect
        com.serkantkn.zunelauncher.data.repository.HubBridge.consume()
        if (navState.currentHub != hub) navState.openHub(hub)
    }
    // The keyboard's quick panel asks for the settings hub on its keyboard tab
    val pendingSettingsTab by com.serkantkn.zunelauncher.data.repository.SettingsBridge.pendingTab.collectAsState()
    LaunchedEffect(pendingSettingsTab) {
        val tab = pendingSettingsTab ?: return@LaunchedEffect
        settingsViewModel.setTargetTab(tab)
        if (navState.currentHub != HubType.SETTINGS) navState.openHub(HubType.SETTINGS)
        com.serkantkn.zunelauncher.data.repository.SettingsBridge.consume()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isWideScreen = LocalIsWideScreen.current

    val (effectiveHubMode, effectiveHubCustomPath) = remember(hubBackgroundMode, solidBackgroundEnabled, customHubWallpaperPath, customWallpaperPath) {
        when (hubBackgroundMode) {
            HubBackgroundMode.SOLID -> BackgroundMode.SOLID to null
            HubBackgroundMode.SYSTEM -> BackgroundMode.WALLPAPER to null
            HubBackgroundMode.CUSTOM -> (if (customHubWallpaperPath != null) BackgroundMode.WALLPAPER else BackgroundMode.GRADIENT) to customHubWallpaperPath
            HubBackgroundMode.MATCH_LAUNCHER -> (if (solidBackgroundEnabled) BackgroundMode.SOLID else BackgroundMode.WALLPAPER) to customWallpaperPath
        }
    }

    var wallpaperOverlayAlpha by remember { mutableStateOf(1f) }
    val density = LocalDensity.current.density

    // ── Hinge animation state ──
    val hingeSpec = rememberHingeSpec()
    val hingeProgress = remember { Animatable(0f) }
    var activeHub by remember { mutableStateOf(navState.currentHub) }
    // Hinge direction, Windows Phone style: hubs ENTER from outside the screen (in front, -90° -> 0°,
    // like the Start turnstile-in) and LEAVE into the depth of the screen (0° -> +90°).
    var isHubHingeOpening by remember { mutableStateOf(true) }
    val isHubOpen = navState.currentHub != null
    val zuneColors = LocalZuneColors.current

    // Drive 2-stage hinge animation when switching hubs or returning to home screen
    LaunchedEffect(navState.currentHub) {
        val targetHub = navState.currentHub
        if (targetHub != activeHub) {
            if (activeHub != null && targetHub != null) {
                // Current hub turns away into the depth, the next one swings in from outside
                isHubHingeOpening = false
                hingeProgress.animateTo(0f, animationSpec = tween(280, easing = FastOutSlowInEasing))
                activeHub = targetHub
                isHubHingeOpening = true
                hingeProgress.animateTo(1f, animationSpec = tween(280, easing = FastOutSlowInEasing))
            } else if (targetHub != null) {
                activeHub = targetHub
                isHubHingeOpening = true
                hingeProgress.animateTo(1f, animationSpec = hingeSpec)
            } else {
                isHubHingeOpening = false
                hingeProgress.animateTo(0f, animationSpec = hingeSpec)
                activeHub = null
            }
        }
    }

    // Split mode left-to-right shrink animation
    val splitAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(navState.isSplitMode) {
        if (navState.isSplitMode) {
            splitAnimProgress.snapTo(0f)
            splitAnimProgress.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        } else {
            splitAnimProgress.snapTo(0f)
        }
    }

    // Handle back press
    BackHandler(enabled = navState.currentHub != null || navState.isSplitMode) {
        val popped = navState.popHub()
        if (!popped) {
            navState.closeHub()
        }
    }

    // Interpolate accent color based on pager scroll position
    val accentColor by remember {
        derivedStateOf {
            val offset = (pagerState.currentPage + pagerState.currentPageOffsetFraction - 1f)
                .coerceIn(0f, 1f)
            lerp(ZuneColors.Magenta, ZuneColors.Orange, offset)
        }
    }

    var accumulatedOverscroll by remember { mutableFloatStateOf(0f) }
    val globalNestedScrollConnection = remember(context) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < 0) accumulatedOverscroll = 0f
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    if (available.y > 0 && navState.currentHub == null) {
                        accumulatedOverscroll += available.y
                        if (accumulatedOverscroll > 8f) {
                            expandNotificationPanel(context)
                            accumulatedOverscroll = 0f
                        }
                        return available
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // 4-finger swipe gesture detection to trigger split screen mode on Tablet
    val multiTouchGestureModifier = if (isWideScreen && navState.currentHub != null && !navState.isSplitMode) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.size >= 4) {
                        var totalDragX = 0f
                        var hasRightDrag = false
                        event.changes.forEach { change ->
                            val dragAmount = change.position.x - change.previousPosition.x
                            totalDragX += dragAmount
                            if (dragAmount > 0) hasRightDrag = true
                        }
                        if (hasRightDrag && totalDragX > 60f) {
                            event.changes.forEach { it.consume() }
                            navState.enterSplitMode()
                        }
                    }
                }
            }
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(multiTouchGestureModifier)
            .nestedScroll(globalNestedScrollConnection)
    ) {
        ZuneBackground(
            mode = if (solidBackgroundEnabled) BackgroundMode.SOLID else BackgroundMode.WALLPAPER,
            accentColor = accentColor
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!solidBackgroundEnabled) {
                    ZuneWallpaperOverlay(alpha = wallpaperOverlayAlpha)
                }

                val messagingViewModel: com.serkantkn.zunelauncher.ui.screens.messaging.MessagingHubViewModel = viewModel()
                val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

                if (isWideScreen && navState.isSplitMode) {
                    // ════════════════════════════════════════════════════════
                    // TABLET SPLIT SCREEN DUAL-HUB MODE (50% / 50%)
                    // Hubs & Left Home Screen inside split panes forced to PHONE DESIGN MODE!
                    // ════════════════════════════════════════════════════════
                    Row(modifier = Modifier.fillMaxSize()) {
                        // ── Left Pane (50% width) ──
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    alpha = splitAnimProgress.value
                                }
                        ) {
                            if (navState.leftHub == null) {
                                // Main Launcher Pager on the left side (Forced to Phone Design Mode)
                                CompositionLocalProvider(LocalIsWideScreen provides false) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        userScrollEnabled = true
                                    ) { page ->
                                        when (page) {
                                            0 -> SocialHubScreen(isCurrentPage = pagerState.currentPage == 0)
                                            1 -> HomeHubScreen(
                                                isHubOpen = false,
                                                isCurrentPage = pagerState.currentPage == 1,
                                                onExpandProgressChange = { _ -> wallpaperOverlayAlpha = 1f },
                                                onHubSelected = { hub -> navState.openHub(hub) },
                                                timeFormat = timeFormat,
                                                dateFormat = dateFormat
                                            )
                                            2 -> AppsHubScreen(isCurrentPage = pagerState.currentPage == 2)
                                        }
                                    }
                                }
                            } else {
                                // Left Docked Hub (Forced to Phone Design Mode)
                                CompositionLocalProvider(LocalIsWideScreen provides false) {
                                    ZuneBackground(
                                        mode = effectiveHubMode,
                                        accentColor = zuneColors.accentColor,
                                        customWallpaperPathOverride = effectiveHubCustomPath,
                                        forceModeOverride = effectiveHubMode
                                    ) {
                                        if (effectiveHubMode != BackgroundMode.SOLID) {
                                            ZuneWallpaperOverlay(alpha = hubBackgroundOpacity, isHubOverlay = true)
                                        }
                                        RenderHubScreen(
                                            hub = navState.leftHub!!,
                                            navState = navState,
                                            messagingViewModel = messagingViewModel,
                                            settingsViewModel = settingsViewModel,
                                            context = context
                                        )
                                    }
                                }

                                // Action Buttons (Maximize & Close) for Left Hub
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .statusBarsPadding()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { navState.expandLeftSplitHubToFullscreen() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fullscreen,
                                            contentDescription = stringResource(R.string.launcher_fullscreen),
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(
                                        onClick = { navState.closeLeftSplitHub() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.launcher_close_hub),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(zuneColors.accentColor.copy(alpha = 0.6f))
                        )

                        // ── Right Pane (50% width) ──
                        // Animates smoothly from left to right as it shrinks
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    val progress = splitAnimProgress.value
                                    translationX = (1f - progress) * (-screenWidthPx / 2f)
                                    alpha = (progress * 1.5f - 0.2f).coerceIn(0f, 1f)
                                }
                        ) {
                            if (navState.rightHub != null) {
                                // Right Docked Hub (Forced to Phone Design Mode)
                                CompositionLocalProvider(LocalIsWideScreen provides false) {
                                    ZuneBackground(
                                        mode = effectiveHubMode,
                                        accentColor = zuneColors.accentColor,
                                        customWallpaperPathOverride = effectiveHubCustomPath,
                                        forceModeOverride = effectiveHubMode
                                    ) {
                                        if (effectiveHubMode != BackgroundMode.SOLID) {
                                            ZuneWallpaperOverlay(alpha = hubBackgroundOpacity, isHubOverlay = true)
                                        }
                                        RenderHubScreen(
                                            hub = navState.rightHub!!,
                                            navState = navState,
                                            messagingViewModel = messagingViewModel,
                                            settingsViewModel = settingsViewModel,
                                            context = context
                                        )
                                    }
                                }

                                // Action Buttons (Maximize & Close) for Right Hub
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .statusBarsPadding()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { navState.expandRightSplitHubToFullscreen() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fullscreen,
                                            contentDescription = stringResource(R.string.launcher_fullscreen),
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(
                                        onClick = { navState.closeRightSplitHub() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.launcher_close_hub),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ════════════════════════════════════════════════════════
                    // NORMAL SINGLE HUB LAUNCHER MODE
                    // ════════════════════════════════════════════════════════
                    // LAYER 1 — Main pager (Home + Apps)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val progress = hingeProgress.value
                                rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * progress
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density
                                alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
                            }
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                            userScrollEnabled = navState.currentHub == null
                        ) { page ->
                            when (page) {
                                0 -> SocialHubScreen(isCurrentPage = pagerState.currentPage == 0)
                                1 -> HomeHubScreen(
                                    isHubOpen = isHubOpen,
                                    isCurrentPage = pagerState.currentPage == 1,
                                    onExpandProgressChange = { _ -> wallpaperOverlayAlpha = 1f },
                                    onNavigateToSocialHub = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(0)
                                        }
                                    },
                                    onNavigateToAppsHub = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(2)
                                        }
                                    },
                                    onHubSelected = { hub ->
                                        when (hub) {
                                            HubType.HOME -> { /* Already on home */ }
                                            else -> navState.openHub(hub)
                                        }
                                    },
                                    timeFormat = timeFormat,
                                    dateFormat = dateFormat
                                )
                                2 -> AppsHubScreen(
                                    isCurrentPage = pagerState.currentPage == 2
                                )
                            }
                        }
                    }

                    // LAYER 2 — Hub overlay
                    if (hingeProgress.value > 0f) {
                        val hub = activeHub

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val progress = hingeProgress.value
                                    // Opening: -90° (outside, in front) -> 0°. Closing: 0° -> +90° (into the depth).
                                    val direction = if (isHubHingeOpening) -1f else 1f
                                    rotationY = direction * HingeAnimation.MAX_ROTATION_DEGREES * (1f - progress)
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density
                                    alpha = (progress * 1.5f - 0.2f).coerceIn(0f, 1f)
                                }
                        ) {
                            ZuneBackground(
                                mode = effectiveHubMode,
                                accentColor = zuneColors.accentColor,
                                customWallpaperPathOverride = effectiveHubCustomPath,
                                forceModeOverride = effectiveHubMode
                            ) {
                                if (effectiveHubMode != BackgroundMode.SOLID) {
                                    ZuneWallpaperOverlay(alpha = hubBackgroundOpacity, isHubOverlay = true)
                                }
                                if (hub != null) {
                                    RenderHubScreen(
                                        hub = hub,
                                        navState = navState,
                                        messagingViewModel = messagingViewModel,
                                        settingsViewModel = settingsViewModel,
                                        context = context
                                    )
                                }
                            }
                        }
                    }
                }

                // Windows Phone Toast Notification Banner
                if (notificationStyle == NotificationStyle.WINDOWS_PHONE) {
                    WpToastNotification(
                        message = latestNotification,
                        onDismiss = { SocialRepository.clearToast() }
                    )
                }

                // Windows Phone Call Screen Overlay
                val callStatus by com.serkantkn.zunelauncher.data.service.CallManager.callStatus.collectAsState()
                if (callStatus != com.serkantkn.zunelauncher.data.service.CallStatus.IDLE) {
                    com.serkantkn.zunelauncher.ui.screens.phone.WpCallScreen()
                }

                // Windows Phone Style Volume Control Banner
                WpVolumeControl()

                // The tour, and the note about what changed. Over everything, because a first run
                // that can be swiped away behind the launcher is not a first run.
                val onboardingViewModel: OnboardingViewModel = viewModel()
                val firstRun by onboardingViewModel.screen.collectAsState()
                when (val screen = firstRun) {
                    is FirstRunScreen.Tour -> OnboardingScreen(
                        tour = screen,
                        onNext = onboardingViewModel::next,
                        onBack = onboardingViewModel::back,
                        onSkip = onboardingViewModel::finish
                    )

                    is FirstRunScreen.WhatsNew -> WhatsNewScreen(
                        notes = screen.notes,
                        onDismiss = onboardingViewModel::dismissWhatsNew
                    )

                    FirstRunScreen.None -> Unit
                }
            }
        }
    }
}

@Composable
private fun RenderHubScreen(
    hub: HubType,
    navState: ZuneNavigationState,
    messagingViewModel: com.serkantkn.zunelauncher.ui.screens.messaging.MessagingHubViewModel,
    settingsViewModel: SettingsViewModel,
    context: Context
) {
    when (hub) {
        HubType.MUSIC -> MusicHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
        HubType.PEOPLE -> PeopleHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() },
            onOpenMessaging = { contactName, phoneNumber ->
                messagingViewModel.openConversationWithContact(context, contactName, phoneNumber)
                navState.openHub(HubType.MESSAGING)
            }
        )
        HubType.PICTURES -> PicturesHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
        HubType.PHONE -> PhoneHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
        HubType.SETTINGS -> SettingsScreen(onBack = { if (!navState.popHub()) navState.closeHub() }, viewModel = settingsViewModel)
        HubType.CLOCK -> com.serkantkn.zunelauncher.ui.screens.clock.ClockHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
        HubType.INTERNET -> com.serkantkn.zunelauncher.ui.screens.browser.BrowserHubScreen(
            onClose = { if (!navState.popHub()) navState.closeHub() },
            onOpenSettings = { targetTab ->
                settingsViewModel.setTargetTab(targetTab)
                navState.openHub(HubType.SETTINGS)
            }
        )
        HubType.CALENDAR -> com.serkantkn.zunelauncher.ui.screens.calendar.CalendarHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
        HubType.MESSAGING -> com.serkantkn.zunelauncher.ui.screens.messaging.MessagingHubScreen(
            onClose = { if (!navState.popHub()) navState.closeHub() },
            viewModel = messagingViewModel
        )
        HubType.FILES -> com.serkantkn.zunelauncher.ui.screens.files.FilesHubScreen(
            onClose = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.NOTES -> com.serkantkn.zunelauncher.ui.screens.notes.NotesHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.EMAIL -> com.serkantkn.zunelauncher.ui.screens.email.EmailHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.CALCULATOR -> com.serkantkn.zunelauncher.ui.screens.calculator.CalculatorHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.WEATHER -> com.serkantkn.zunelauncher.ui.screens.weather.WeatherHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.CAMERA -> com.serkantkn.zunelauncher.ui.screens.camera.CameraHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        else -> {}
    }
}

private fun expandNotificationPanel(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val expand = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            statusBarManager.getMethod("expandNotificationsPanel")
        } else {
            statusBarManager.getMethod("expand")
        }
        expand.invoke(statusBarService)
    } catch (e: Exception) {
        ZuneLog.e("LauncherScreen", "expandNotificationPanel failed", e)
    }
}
