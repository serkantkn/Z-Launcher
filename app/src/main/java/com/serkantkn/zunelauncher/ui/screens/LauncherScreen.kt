package com.serkantkn.zunelauncher.ui.screens
import android.annotation.SuppressLint
import android.content.Context
import com.serkantkn.zunelauncher.MainActivityEvents
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.NotificationStyle
import com.serkantkn.zunelauncher.data.model.NotificationCenterStyle
import com.serkantkn.zunelauncher.ui.components.WpActionCenterPanel
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.BackgroundMode
import com.serkantkn.zunelauncher.ui.components.ZuneBackground
import com.serkantkn.zunelauncher.ui.components.ZuneWallpaperOverlay
import com.serkantkn.zunelauncher.ui.components.WpToastNotification
import com.serkantkn.zunelauncher.ui.navigation.rememberZuneNavigationState
import com.serkantkn.zunelauncher.ui.screens.apps.AppsHubScreen
import com.serkantkn.zunelauncher.ui.screens.home.HomeHubScreen
import com.serkantkn.zunelauncher.ui.screens.music.MusicHubScreen
import com.serkantkn.zunelauncher.ui.screens.people.PeopleHubScreen
import com.serkantkn.zunelauncher.ui.screens.phone.PhoneHubScreen
import com.serkantkn.zunelauncher.ui.screens.pictures.PicturesHubScreen
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsScreen
import com.serkantkn.zunelauncher.ui.screens.social.SocialHubScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsViewModel

@SuppressLint("WrongConstant")
fun expandNotificationPanel(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val expand = statusBarManager.getMethod("expandNotificationsPanel")
        expand.invoke(statusBarService)
    } catch (e: Exception) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expand = statusBarManager.getMethod("expand")
            expand.invoke(statusBarService)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

fun setStatusBarExpandDisabled(context: Context, disabled: Boolean) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
        val disableMethod = statusBarManagerClass.getMethod("disable", Int::class.javaPrimitiveType)
        disableMethod.invoke(statusBarService, if (disabled) 0x00010000 else 0)
    } catch (e: Exception) {
        // Ignored if restricted
    }
}

/**
 * Main launcher screen composable.
 *
 * Structure:
 * - HorizontalPager with Home Hub (page 0) and Apps Hub (page 1)
 * - Hub detail overlays (Music, People, Pictures, Settings)
 * - 3D hinge animation: left edge is the pivot, screens rotate like a door
 * - Page indicator dots
 * - Background gradient interpolates between hub accent colors
 */
@Composable
fun LauncherScreen(
    modifier: Modifier = Modifier
) {
    val navState = rememberZuneNavigationState()
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    
    val settingsViewModel: SettingsViewModel = viewModel()
    val solidBackgroundEnabled by settingsViewModel.solidBackgroundEnabled.collectAsState()
    val notificationStyle by settingsViewModel.notificationStyle.collectAsState()
    val notificationCenterStyle by settingsViewModel.notificationCenterStyle.collectAsState()
    val currentCenterStyle by rememberUpdatedState(notificationCenterStyle)
    
    var isActionCenterOpen by remember { mutableStateOf(false) }
    val latestToastMessage by SocialRepository.latestToastMessage.collectAsState()
    val context = LocalContext.current
    
    val triggerActionCenter by MainActivityEvents.triggerActionCenter.collectAsState()
    LaunchedEffect(triggerActionCenter) {
        if (triggerActionCenter) {
            if (currentCenterStyle == NotificationCenterStyle.WINDOWS_PHONE) {
                isActionCenterOpen = true
            } else {
                expandNotificationPanel(context)
            }
            MainActivityEvents.triggerActionCenter.value = false
        }
    }
    
    var wallpaperOverlayAlpha by remember { mutableStateOf(1f) }
    val density = LocalDensity.current.density

    // ── Hinge animation state ──
    val hingeProgress = remember { Animatable(0f) }
    var activeHub by remember { mutableStateOf(navState.currentHub) }
    val isHubOpen = navState.currentHub != null
    val zuneColors = LocalZuneColors.current

    // Drive 2-stage hinge animation when switching hubs or returning to home screen
    LaunchedEffect(navState.currentHub) {
        val targetHub = navState.currentHub
        if (targetHub != activeHub) {
            if (activeHub != null && targetHub != null) {
                // Stage 1: Active hub hinges AWAY to -90°
                hingeProgress.animateTo(0f, animationSpec = tween(280, easing = FastOutSlowInEasing))
                activeHub = targetHub
                // Stage 2: Target hub hinges IN from +90° to 0°
                hingeProgress.animateTo(1f, animationSpec = tween(280, easing = FastOutSlowInEasing))
            } else if (targetHub != null) {
                activeHub = targetHub
                hingeProgress.animateTo(1f, animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing))
            } else {
                hingeProgress.animateTo(0f, animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing))
                activeHub = null
            }
        }
    }

    // Handle back press when a hub overlay is open
    BackHandler(enabled = navState.currentHub != null) {
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

    var accumulatedOverscroll by remember { mutableStateOf(0f) }
    val globalNestedScrollConnection = remember(context, currentCenterStyle) {
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
                            if (currentCenterStyle == NotificationCenterStyle.WINDOWS_PHONE) {
                                isActionCenterOpen = true
                            } else {
                                expandNotificationPanel(context)
                            }
                            accumulatedOverscroll = 0f
                        }
                        return available
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    LaunchedEffect(isActionCenterOpen) {
        if (isActionCenterOpen) {
            try {
                val statusBarService = context.getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val collapse = statusBarManager.getMethod("collapsePanels")
                collapse.invoke(statusBarService)
            } catch (e: Exception) {
                try {
                    val statusBarService = context.getSystemService("statusbar")
                    val statusBarManager = Class.forName("android.app.StatusBarManager")
                    val collapse = statusBarManager.getMethod("collapse")
                    collapse.invoke(statusBarService)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    val isWideScreen = LocalIsWideScreen.current
    LaunchedEffect(notificationCenterStyle) {
        if (notificationCenterStyle == NotificationCenterStyle.WINDOWS_PHONE) {
            setStatusBarExpandDisabled(context, true)
        } else {
            setStatusBarExpandDisabled(context, false)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().nestedScroll(globalNestedScrollConnection)
    ) {
            ZuneBackground(
                mode = if (solidBackgroundEnabled) BackgroundMode.SOLID else BackgroundMode.WALLPAPER,
                accentColor = accentColor
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!solidBackgroundEnabled) {
                        ZuneWallpaperOverlay(alpha = wallpaperOverlayAlpha)
                    }

            // ════════════════════════════════════════════
            // LAYER 1 — Main pager (Home + Apps)
            // Hinges BACKWARD when a hub opens:
            //   rotation: 0° → −90°  (swings away from viewer)
            //   alpha: 1 → 0
            // ════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = hingeProgress.value
                        rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * progress
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density
                        // Fade out during the first 2/3 of the animation
                        alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = navState.currentHub == null
                ) { page ->
                    when (page) {
                        0 -> SocialHubScreen()
                        1 -> HomeHubScreen(
                            isHubOpen = isHubOpen,
                            isCurrentPage = pagerState.currentPage == 1,
                            onExpandProgressChange = { _ -> wallpaperOverlayAlpha = 1f },
                            onHubSelected = { hub ->
                                when (hub) {
                                    HubType.HOME -> { /* Already on home */ }
                                    else -> navState.openHub(hub)
                                }
                            }
                        )
                        2 -> AppsHubScreen(
                            isCurrentPage = pagerState.currentPage == 2
                        )
                    }
                }
            }

            // ════════════════════════════════════════════
            // LAYER 2 — Hub overlay
            // Hinges IN from the front when a hub opens:
            //   rotation: +90° → 0°  (swings toward viewer)
            //   alpha: 0 → 1
            // During close animation (progress 1→0), the reverse occurs.
            // ════════════════════════════════════════════
            if (hingeProgress.value > 0f) {
                val hub = activeHub

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = hingeProgress.value
                            rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - progress)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density
                            // Fade in starting ~13% into the animation
                            alpha = (progress * 1.5f - 0.2f).coerceIn(0f, 1f)
                        }
                ) {
                    ZuneBackground(
                        mode = BackgroundMode.GRADIENT,
                        accentColor = zuneColors.accentColor
                    ) {
                        val messagingViewModel: com.serkantkn.zunelauncher.ui.screens.messaging.MessagingHubViewModel = viewModel()
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
                            HubType.SETTINGS -> SettingsScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
                            HubType.CLOCK -> com.serkantkn.zunelauncher.ui.screens.clock.ClockHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
                            HubType.INTERNET -> com.serkantkn.zunelauncher.ui.screens.browser.BrowserHubScreen(onClose = { if (!navState.popHub()) navState.closeHub() })
                            HubType.CALENDAR -> com.serkantkn.zunelauncher.ui.screens.calendar.CalendarHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
                            HubType.MESSAGING -> com.serkantkn.zunelauncher.ui.screens.messaging.MessagingHubScreen(
                                onClose = { if (!navState.popHub()) navState.closeHub() },
                                viewModel = messagingViewModel
                            )
                            else -> {}
                        }
                    }
                }
            }

            // Top Status Bar Gesture Interceptor Area
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 40.dp)
                    .pointerInput(currentCenterStyle) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 8f) {
                                if (currentCenterStyle == NotificationCenterStyle.WINDOWS_PHONE) {
                                    isActionCenterOpen = true
                                } else {
                                    expandNotificationPanel(context)
                                }
                            }
                        }
                    }
            )

            WpActionCenterPanel(
                isOpen = isActionCenterOpen,
                onClose = { isActionCenterOpen = false }
            )
        }
    }
}
}



