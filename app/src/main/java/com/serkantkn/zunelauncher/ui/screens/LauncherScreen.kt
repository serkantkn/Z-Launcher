package com.serkantkn.zunelauncher.ui.screens
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.BackgroundMode
import com.serkantkn.zunelauncher.ui.components.ZuneBackground
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
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { 3 }
    )
    val navState = rememberZuneNavigationState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    // ── Hinge animation state ──
    // 0f = pager fully visible (hub closed)
    // 1f = hub fully visible (pager hinged away)
    val hingeProgress = remember { Animatable(0f) }
    val isHubOpen = navState.currentHub != null
    val zuneColors = LocalZuneColors.current

    // Drive the hinge animation when hub opens/closes
    LaunchedEffect(isHubOpen) {
        hingeProgress.animateTo(
            targetValue = if (isHubOpen) 1f else 0f,
            animationSpec = tween(
                durationMillis = HingeAnimation.DURATION_MS,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Handle back press when a hub overlay is open
    BackHandler(enabled = navState.currentHub != null) {
        navState.closeHub()
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
    val context = LocalContext.current
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
                        if (accumulatedOverscroll > 100f) {
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

    Box(
        modifier = modifier.fillMaxSize().nestedScroll(globalNestedScrollConnection)
    ) {
            ZuneBackground(
                mode = BackgroundMode.GRADIENT,
                accentColor = accentColor
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

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
                            onHubSelected = { hub ->
                                when (hub) {
                                    HubType.APPS -> {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(2)
                                        }
                                    }
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
                val hub = navState.lastHub

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
                        when (hub) {
                            HubType.MUSIC -> MusicHubScreen(onBack = { navState.closeHub() })
                            HubType.PEOPLE -> PeopleHubScreen(onBack = { navState.closeHub() })
                            HubType.PICTURES -> PicturesHubScreen(onBack = { navState.closeHub() })
                            HubType.PHONE -> PhoneHubScreen(onBack = { navState.closeHub() })
                            HubType.SETTINGS -> SettingsScreen(onBack = { navState.closeHub() })
                            HubType.CLOCK -> com.serkantkn.zunelauncher.ui.screens.clock.ClockHubScreen(onBack = { navState.closeHub() })
                            else -> {}
                        }
                    }
                }
                }
            }
        }
    }
}

