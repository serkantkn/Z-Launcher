package com.serkantkn.zeta.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.serkantkn.zeta.ui.tv.tvTake
import com.serkantkn.zeta.ui.tv.tvFocusFence
import com.serkantkn.zeta.R
import androidx.compose.ui.res.stringResource
import com.serkantkn.zeta.util.ZuneLog
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.serkantkn.zeta.data.model.HomeScreenLayout
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.model.HubBackgroundMode
import com.serkantkn.zeta.data.model.NotificationStyle
import com.serkantkn.zeta.data.repository.SocialRepository
import com.serkantkn.zeta.di.appContainer
import kotlinx.coroutines.flow.first
import com.serkantkn.zeta.ui.animation.HingeAnimation
import com.serkantkn.zeta.ui.components.BackgroundMode
import com.serkantkn.zeta.ui.components.WpToastNotification
import com.serkantkn.zeta.ui.components.WpVolumeControl
import com.serkantkn.zeta.ui.components.ZuneBackground
import com.serkantkn.zeta.ui.components.ZuneWallpaperOverlay
import com.serkantkn.zeta.ui.navigation.ZuneNavigationState
import com.serkantkn.zeta.ui.navigation.rememberZuneNavigationState
import com.serkantkn.zeta.ui.screens.apps.AppsHubScreen
import com.serkantkn.zeta.ui.screens.home.HomeHubScreen
import com.serkantkn.zeta.ui.screens.music.MusicHubScreen
import com.serkantkn.zeta.ui.screens.people.PeopleHubScreen
import com.serkantkn.zeta.ui.screens.phone.PhoneHubScreen
import com.serkantkn.zeta.ui.screens.pictures.PicturesHubScreen
import com.serkantkn.zeta.ui.screens.settings.SettingsScreen
import com.serkantkn.zeta.ui.screens.settings.SettingsViewModel
import com.serkantkn.zeta.ui.screens.social.SocialHubScreen
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalScreenAwake
import androidx.compose.ui.draw.drawWithContent
import com.serkantkn.zeta.ui.screens.onboarding.FirstRunScreen
import com.serkantkn.zeta.ui.screens.onboarding.OnboardingScreen
import com.serkantkn.zeta.ui.screens.onboarding.OnboardingViewModel
import com.serkantkn.zeta.ui.screens.onboarding.WhatsNewScreen
import com.serkantkn.zeta.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneColors
import com.serkantkn.zeta.ui.animation.rememberHingeSpec
import com.serkantkn.zeta.ui.screens.home.HomeHubViewModel

/**
 * What stands behind a hub: the mode, the launcher's own picture if one is wanted, and whether the
 * phone's wallpaper is wanted instead of it. The last is said in so many words because "no custom
 * path" used to fall back to the launcher's wallpaper, which is exactly what "system" is not.
 */
private data class HubBackdrop(
    val mode: BackgroundMode,
    val customPath: String?,
    val systemWallpaper: Boolean
)

@Composable
fun LauncherScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    homeHubViewModel: HomeHubViewModel = viewModel()
) {
    val navState = rememberZuneNavigationState()
    navState.television = com.serkantkn.zeta.ui.theme.LocalIsTv.current
    // Social, Start, the app list — and, in the Zune layout on a phone, the tiles between the
    // list and the apps (quickplay lives in the list's strip). A tablet shows the tiles over
    // quickplay beside its list, so it has no page of them. The app list is the last page.
    val homeScreenLayout by settingsViewModel.homeScreenLayout.collectAsState()
    val hasTilesPage = homeScreenLayout == HomeScreenLayout.ZUNE && !LocalIsWideScreen.current
    val appsPage = if (hasTilesPage) 3 else 2
    // The tablet's Windows 8 Start has its app list under it (a swipe up), not beside it: the
    // pager stops at Start there, or a sideways drag on the empty band above or below the tiles
    // turned up the phone's app list instead.
    val tabletStart = LocalIsWideScreen.current && homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE
    // A TV - Media Center or the Xbox alike: its pages zoom - the one going grows past and fades,
    // the one coming grows into place - instead of turning or fading.
    val vistaTv = navState.television
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = {
            when {
                hasTilesPage -> 4
                tabletStart -> 2
                else -> 3
            }
        }
    )

    // Both view models answer "Zune" until the preference has been read, so a phone set to the
    // Windows Phone board drew the Zune list for a frame or two on every cold start. The pager is
    // kept invisible until the stored value has arrived and both have caught up with it.
    val launcherContext = LocalContext.current
    var layoutReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Bounded: a Start screen that never appears is worse than one that flickers once.
        kotlinx.coroutines.withTimeoutOrNull(LAYOUT_WAIT_MILLIS) {
            val stored = launcherContext.appContainer.settingsDataStore.homeScreenLayout.first()
            settingsViewModel.homeScreenLayout.first { it == stored }
            homeHubViewModel.homeScreenLayout.first { it == stored }
        }
        layoutReady = true
    }

    val notificationStyle by settingsViewModel.notificationStyle.collectAsState()
    val socialSources by settingsViewModel.socialSources.collectAsState()
    val solidBackgroundEnabled by settingsViewModel.solidBackgroundEnabled.collectAsState()
    val tileBlur by settingsViewModel.tileBlur.collectAsState()
    val tileOpacity by settingsViewModel.tileOpacity.collectAsState()
    val timeFormat by settingsViewModel.timeFormat.collectAsState()
    val dateFormat by settingsViewModel.dateFormat.collectAsState()
    val hubBackgroundMode by settingsViewModel.hubBackgroundMode.collectAsState()
    val hubBackgroundOpacity by settingsViewModel.hubBackgroundOpacity.collectAsState()
    // The Zune layout's glow of colour, when chosen, stands in for the wallpaper: behind Start and
    // behind every hub told to match it.
    val zuneGlow by settingsViewModel.zuneGlow.collectAsState()
    val glowing = homeScreenLayout == HomeScreenLayout.ZUNE &&
        zuneGlow != com.serkantkn.zeta.data.model.ZuneGlow.OFF && !solidBackgroundEnabled
    val customHubWallpaperPath by settingsViewModel.customHubWallpaperPath.collectAsState()
    val customWallpaperPath by settingsViewModel.customWallpaperPath.collectAsState()

    // The notification listener asks the repository on every single notification, so the answer is
    // pushed here — from the launcher, which is always composed — rather than from the hub's own
    // screen, which is not alive until somebody swipes to it.
    LaunchedEffect(socialSources) {
        val chosen = socialSources
        val resolved = when {
            chosen != null -> chosen
            // The free build has no social sources of its own to offer; the premium defaults
            // used to be pushed anyway, and the listener went on collecting for a hub it lacks.
            !com.serkantkn.zeta.util.Pro.unlocked -> emptySet()
            else -> com.serkantkn.zeta.data.model.SocialApps.defaultSources(
                launcherContext.appContainer.appRepository
                    .getInstalledApps()
                    .first()
                    .map { app -> app.packageName }
            )
        }
        SocialRepository.updateSources(resolved)
    }

    val latestNotification by SocialRepository.latestToastMessage.collectAsState()

    // Cross-hub requests for the Notes hub (Start tiles, Calendar, People, Social, share sheet)
    val pendingNoteRequest by com.serkantkn.zeta.data.repository.NotesBridge.pending.collectAsState()
    LaunchedEffect(pendingNoteRequest) {
        if (pendingNoteRequest != null && navState.currentHub != HubType.NOTES) {
            navState.openHub(HubType.NOTES)
        }
    }
    // Cross-hub requests for the Email hub (notifications, mailto:, share sheet)
    val pendingEmailRequest by com.serkantkn.zeta.data.repository.EmailBridge.pending.collectAsState()
    LaunchedEffect(pendingEmailRequest) {
        if (pendingEmailRequest != null && navState.currentHub != HubType.EMAIL) {
            navState.openHub(HubType.EMAIL)
        }
    }
    // Cross-hub requests for the Messaging hub (pinned conversations, notifications, sms: links)
    val pendingMessageThread by com.serkantkn.zeta.data.repository.MessagingBridge.pendingThreadId.collectAsState()
    LaunchedEffect(pendingMessageThread) {
        if (pendingMessageThread != null && navState.currentHub != HubType.MESSAGING) {
            navState.openHub(HubType.MESSAGING)
        }
    }
    val pendingMessageCompose by com.serkantkn.zeta.data.repository.MessagingBridge.pendingCompose.collectAsState()
    LaunchedEffect(pendingMessageCompose) {
        if (pendingMessageCompose != null && navState.currentHub != HubType.MESSAGING) {
            navState.openHub(HubType.MESSAGING)
        }
    }
    // The Social hub handing a notification to one of the launcher's own hubs
    val pendingHub by com.serkantkn.zeta.data.repository.HubBridge.pendingHub.collectAsState()
    LaunchedEffect(pendingHub) {
        val hub = pendingHub ?: return@LaunchedEffect
        com.serkantkn.zeta.data.repository.HubBridge.consume()
        if (navState.currentHub != hub) navState.openHub(hub)
    }
    // The keyboard's quick panel asks for the settings hub on its keyboard tab
    val pendingSettingsTab by com.serkantkn.zeta.data.repository.SettingsBridge.pendingTab.collectAsState()
    LaunchedEffect(pendingSettingsTab) {
        val tab = pendingSettingsTab ?: return@LaunchedEffect
        settingsViewModel.setTargetTab(tab)
        if (navState.currentHub != HubType.SETTINGS) navState.openHub(HubType.SETTINGS)
        com.serkantkn.zeta.data.repository.SettingsBridge.consume()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isWideScreen = LocalIsWideScreen.current

    val hubBackdrop = remember(hubBackgroundMode, solidBackgroundEnabled, customHubWallpaperPath, customWallpaperPath, glowing) {
        when (hubBackgroundMode) {
            HubBackgroundMode.SOLID -> HubBackdrop(BackgroundMode.SOLID, null, systemWallpaper = false)
            HubBackgroundMode.SYSTEM -> HubBackdrop(BackgroundMode.WALLPAPER, null, systemWallpaper = true)
            HubBackgroundMode.CUSTOM -> HubBackdrop(
                if (customHubWallpaperPath != null) BackgroundMode.WALLPAPER else BackgroundMode.GRADIENT,
                customHubWallpaperPath,
                systemWallpaper = false
            )
            HubBackgroundMode.MATCH_LAUNCHER -> HubBackdrop(
                when {
                    solidBackgroundEnabled -> BackgroundMode.SOLID
                    glowing -> BackgroundMode.GLOW
                    else -> BackgroundMode.WALLPAPER
                },
                customWallpaperPath,
                systemWallpaper = false
            )
        }
    }
    val effectiveHubMode = hubBackdrop.mode

    val density = LocalDensity.current.density
    val zuneHubSlidePx = with(LocalDensity.current) { com.serkantkn.zeta.ui.animation.ZUNE_HUB_SLIDE.toPx() }
    val zuneHubFlight = com.serkantkn.zeta.ui.animation.LocalZuneHubFlight.current

    // ── Hinge animation state ──
    val hingeSpec = rememberHingeSpec()
    // The quick turn between two hubs, and the split's slide: shorter than the hinge, but just as
    // much motion, so they stop with the setting too.
    val animationsOn by rememberUpdatedState(LocalAnimationsEnabled.current)
    fun quickSpec(millis: Int) = tween<Float>(if (animationsOn) millis else 0, easing = FastOutSlowInEasing)
    // The Zune layout has no hinge: its hubs slide in flat over the list, as the Zune HD's menus did.
    val zuneStart = homeScreenLayout == HomeScreenLayout.ZUNE
    fun hubInSpec() = when {
        vistaTv -> quickSpec(VISTA_PAGE_MILLIS)
        tabletStart -> quickSpec(WIN8_HUB_FADE_IN_MILLIS)
        zuneStart -> quickSpec(com.serkantkn.zeta.ui.animation.ZUNE_HUB_MILLIS)
        else -> hingeSpec
    }
    // A hub already open when this is first drawn stands open; it does not swing in again.
    val hingeProgress = remember { Animatable(if (navState.currentHub != null) 1f else 0f) }
    var activeHub by remember { mutableStateOf(navState.currentHub) }
    // Hinge direction, Windows Phone style: hubs ENTER from outside the screen (in front, -90° -> 0°,
    // like the Start turnstile-in) and LEAVE into the depth of the screen (0° -> +90°).
    var isHubHingeOpening by remember { mutableStateOf(true) }
    val isHubOpen = navState.currentHub != null
    val zuneColors = LocalZuneColors.current

    // Drive 2-stage hinge animation when switching hubs or returning to home screen
    LaunchedEffect(navState.currentHub) {
        val targetHub = navState.currentHub
        if (vistaTv && targetHub != activeHub) {
            com.serkantkn.zeta.ui.tv.VistaSounds.play(context, com.serkantkn.zeta.ui.tv.VistaSounds.Kind.PAGE)
        }
        if (targetHub == activeHub) {
            // The hub is the one already set, but its hinge may have been cut short: on a cold
            // start a hub asked for by a notification begins to swing in while the screen is still
            // being put together, and the effect running it is dropped and started again. Without
            // this the hinge stayed where it stopped - the hub unseen, and Start, told a hub was
            // open, refusing every touch until Back.
            if (targetHub != null && hingeProgress.value < 1f) {
                isHubHingeOpening = true
                hingeProgress.animateTo(1f, animationSpec = hubInSpec())
            }
            return@LaunchedEffect
        }
        if (targetHub != activeHub) {
            if (activeHub != null && targetHub != null) {
                // Current hub turns away into the depth, the next one swings in from outside
                zuneHubFlight?.forgetHub()
                isHubHingeOpening = false
                hingeProgress.animateTo(0f, animationSpec = quickSpec(280))
                activeHub = targetHub
                isHubHingeOpening = true
                hingeProgress.animateTo(1f, animationSpec = quickSpec(280))
            } else if (targetHub != null) {
                activeHub = targetHub
                isHubHingeOpening = true
                // On the tablet the splash has already covered the screen; the hub only has to
                // come up over it.
                hingeProgress.animateTo(1f, animationSpec = hubInSpec())
            } else {
                isHubHingeOpening = false
                // The Zune layout: the hub's title goes back down to its name in the list as the
                // hub fades from under it - the way in, backwards.
                if (zuneStart && animationsOn && !isWideScreen) activeHub?.let { zuneHubFlight?.closeHub(it.key) }
                hingeProgress.animateTo(0f, animationSpec = when {
                    vistaTv -> quickSpec(VISTA_PAGE_MILLIS)
                    tabletStart -> quickSpec(WIN8_HUB_FADE_OUT_MILLIS)
                    zuneStart -> quickSpec(com.serkantkn.zeta.ui.animation.ZUNE_HUB_MILLIS)
                    else -> hingeSpec
                })
                activeHub = null
            }
        }
    }

    // Split mode left-to-right shrink animation
    val splitAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(navState.isSplitMode) {
        if (navState.isSplitMode) {
            splitAnimProgress.snapTo(0f)
            splitAnimProgress.animateTo(1f, animationSpec = quickSpec(400))
        } else {
            splitAnimProgress.snapTo(0f)
        }
    }

    // ── The Home key ──
    // First press: whatever hub is open steps aside and keeps running, and the pager comes back
    // to the Start page. Pressed again on Start, it takes the board back to its beginning.
    val focusManager = LocalFocusManager.current
    val rootView = LocalView.current

    // The hubs the screen is actually showing — two of them in a split. A hub reads this to know
    // whether it may still answer the Back key: one left running behind Start must not, or it
    // walks its own history back a page at a time while the user is only trying to get out of a
    // folder on Start.
    val liveHubs = remember { mutableStateOf<Set<com.serkantkn.zeta.data.model.HubId>>(emptySet()) }
    val homePresses by com.serkantkn.zeta.data.repository.HomeKeyBridge.presses.collectAsState()
    var handledHomePresses by remember { mutableIntStateOf(homePresses) }
    var homeResetSignal by remember { mutableIntStateOf(0) }
    var lastHomePressAt by remember { mutableLongStateOf(0L) }
    var switcherOpen by remember { mutableStateOf(false) }
    var switcherTipShown by remember { mutableStateOf(false) }
    LaunchedEffect(homePresses) {
        if (homePresses == handledHomePresses) return@LaunchedEffect
        handledHomePresses = homePresses

        // Two quick presses ask for the switcher instead. Windows Phone held Back for this, and
        // neither a held Back nor a held Home exists to us any more: on gesture navigation there
        // is no Back button, and Home arrives as an intent with no press length on it.
        val now = android.os.SystemClock.uptimeMillis()
        val quick = now - lastHomePressAt < DOUBLE_PRESS_MILLIS
        lastHomePressAt = now
        if (quick && navState.backgroundHubs.isNotEmpty()) {
            switcherTipShown = false
            switcherOpen = true
            return@LaunchedEffect
        }
        // A hub going away must not leave a text field holding the keyboard open behind it —
        // nor an Android view inside it holding the *window* focus. A WebView that keeps focus
        // goes on answering the Back key from behind the Start screen, one page at a time,
        // because a key event reaches the focused view before it ever reaches the dispatcher.
        focusManager.clearFocus(force = true)
        rootView.clearFocus()
        // The picture for the task switcher is taken while the hub is still the whole screen,
        // before the hinge starts turning it away.
        navState.currentHub
            ?.takeIf { it.runsInBackground }
            ?.let { com.serkantkn.zeta.data.repository.HubPreviewStore.capture(rootView, it) }
        when {
            navState.sendToBackground() -> {
                if (pagerState.currentPage != 1) pagerState.animateScrollToPage(1)
                // The first hub ever left running brings the balloon that tells how to get back
                // to it, once the hinge has put the hub away. A second press meanwhile cancels
                // this, and the balloon waits for the next time.
                val settings = context.applicationContext.appContainer.settingsDataStore
                // The balloon tells of the strip under a phone's board, which a TV has not.
                if (!navState.television && !settings.switcherTipShown.first()) {
                    kotlinx.coroutines.delay(SWITCHER_TIP_DELAY_MILLIS)
                    settings.setSwitcherTipShown()
                    switcherTipShown = true
                }
            }
            pagerState.currentPage != 1 -> pagerState.animateScrollToPage(1)
            else -> homeResetSignal++
        }
    }

    LaunchedEffect(navState.backgroundHubs) {
        com.serkantkn.zeta.data.repository.HubPreviewStore.keepOnly(navState.backgroundHubs)
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

    // ── The pull that opens the notification shade ──
    // It is a pull, not a scroll: it counts only from a list that is already at its top and
    // standing still. A drag that moved the list first — or one that caught it while a fling was
    // still running — is a scroll all the way to the end of the gesture, however far past the top
    // it goes. Without that, reading a list and carrying on downwards dropped the shade over it.
    var shadePull by remember { mutableFloatStateOf(0f) }
    var gestureScrolled by remember { mutableFloatStateOf(0f) }
    var settled by remember { mutableStateOf(true) }
    val globalNestedScrollConnection = remember(context, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                // A push upward is a scroll, and it ends whatever pull was building.
                if (available.y < 0) shadePull = 0f
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                // Whatever the list took for itself, this gesture moved it. A hair is allowed for
                // the stretch a list shows at its own end, which is not a scroll.
                gestureScrolled += kotlin.math.abs(consumed.y)
                val scrolling = gestureScrolled > SCROLL_SLOP_DP * density
                if (!settled || scrolling || available.y <= 0f || navState.currentHub != null) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                shadePull += available.y
                if (shadePull > SHADE_PULL_DP * density) {
                    expandNotificationPanel(context)
                    shadePull = 0f
                    settled = false
                }
                return available
            }

            override suspend fun onPreFling(
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                // The finger is off; nothing is still until whatever it threw has come to rest.
                shadePull = 0f
                settled = false
                return androidx.compose.ui.unit.Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                gestureScrolled = 0f
                shadePull = 0f
                settled = true
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    val canSplit = navState.currentHub != null && !navState.isSplitMode
    // Two fingers drawn up the screen split it. Two, not four, because a phone has no room for
    // four; a tablet takes it as well as its four fingers, so the split is one gesture anywhere.
    // It is read before the hub sees the fingers - a list under them would otherwise take the
    // drag as its own scroll and the split could never be pulled over one - and each finger has
    // to travel up on its own, so a pinch (fingers going opposite ways) is never taken for it.
    val phoneSplitModifier = if (canSplit) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                val travel = mutableMapOf<PointerId, Offset>()
                var fired = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    // Once it has fired, the rest of the gesture belongs to nobody: the fingers
                    // are still down and the pane that has just appeared under them would read
                    // what is left as a press of its own.
                    if (fired) {
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) {
                            fired = false
                            travel.clear()
                        }
                        continue
                    }
                    if (event.changes.count { it.pressed } != 2) {
                        travel.clear()
                        continue
                    }
                    event.changes.forEach { change ->
                        travel[change.id] = (travel[change.id] ?: Offset.Zero) + (change.position - change.previousPosition)
                    }
                    val moves = travel.values
                    if (moves.size == 2 && moves.all { it.y < -SPLIT_PULL_PX / 2 && kotlin.math.abs(it.x) < -it.y }) {
                        event.changes.forEach { it.consume() }
                        fired = true
                        navState.enterSplitMode()
                    }
                }
            }
        }
    } else Modifier

    val multiTouchGestureModifier = if (isWideScreen && navState.currentHub != null && !navState.isSplitMode) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.size >= 4 && event.changes.none { it.isConsumed }) {
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
            .then(phoneSplitModifier)
            .nestedScroll(globalNestedScrollConnection)
    ) {
        ZuneBackground(
            mode = when {
                solidBackgroundEnabled -> BackgroundMode.SOLID
                glowing -> BackgroundMode.GLOW
                else -> BackgroundMode.WALLPAPER
            },
            accentColor = accentColor,
            followsStart = true,
            tileBlur = tileBlur,
            glow = zuneGlow
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // The glow is made dark enough to read on; only a wallpaper is shaded.
                if (!solidBackgroundEnabled && !glowing) {
                    StartShade(pagerState = pagerState, frosted = tileBlur > 0)
                }

                val messagingViewModel: com.serkantkn.zeta.ui.screens.messaging.MessagingHubViewModel = viewModel()

                // The Start page is the same page whichever pane it is drawn in, so it is given
                // the same ways out: the two split layouts used to leave it unable to swipe to
                // the social page, the app list or quickplay.
                val isTv = com.serkantkn.zeta.ui.theme.LocalIsTv.current
                val startPage: @Composable (isHubOpenHere: Boolean) -> Unit = { isHubOpenHere ->
                    // A television has its own Start, worked with a remote (ui/tv).
                    if (isTv) {
                        com.serkantkn.zeta.ui.tv.TvStartScreen(
                            homeHubViewModel = homeHubViewModel,
                            onOpenHub = { hub -> if (hub != HubType.HOME) navState.openHub(hub) },
                            hubOpen = isHubOpenHere,
                            homeResetSignal = homeResetSignal
                        )
                    } else HomeHubScreen(
                        isHubOpen = isHubOpenHere,
                        isCurrentPage = pagerState.currentPage == 1,
                        onNavigateToSocialHub = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        },
                        onNavigateToAppsHub = {
                            coroutineScope.launch { pagerState.animateScrollToPage(appsPage) }
                        },
                        onNavigateNext = {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        },
                        onHubSelected = { hub -> if (hub != HubType.HOME) navState.openHub(hub) },
                        homeResetSignal = homeResetSignal,
                        runningHubs = navState.backgroundHubs.toSet(),
                        timeFormat = timeFormat,
                        dateFormat = dateFormat
                    )
                }

                // The Zune layout's tiles, beside its list: Back to the list on the right swipe,
                // on to the apps on the left.
                val tilesPage: @Composable () -> Unit = {
                    HomeHubScreen(
                        tilesPage = true,
                        isHubOpen = isHubOpen,
                        isCurrentPage = pagerState.currentPage == 2,
                        onNavigateToSocialHub = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                        onNavigateToAppsHub = {
                            coroutineScope.launch { pagerState.animateScrollToPage(appsPage) }
                        },
                        onHubSelected = { hub -> if (hub != HubType.HOME) navState.openHub(hub) },
                        homeResetSignal = homeResetSignal,
                        runningHubs = navState.backgroundHubs.toSet(),
                        timeFormat = timeFormat,
                        dateFormat = dateFormat
                    )
                }

                /**
                 * A hub's ground: its wallpaper and the shade over it. The hub stands on it, and
                 * anything that covers the hub - a sub-page, the browser's start page - is handed
                 * it too (LocalHubGround), so the wallpaper does not vanish under a page.
                 */
                val groundAccent = zuneColors.accentColor
                val hubGround: com.serkantkn.zeta.ui.components.HubGroundPainter = remember(
                    effectiveHubMode, hubBackdrop, groundAccent, hubBackgroundOpacity, zuneGlow, isTv
                ) { { groundModifier, content ->
                  // A television's hubs stand on Media Center's blue, whatever the phone's wallpaper.
                  if (isTv) {
                    com.serkantkn.zeta.ui.tv.MediaCenterBackground(modifier = groundModifier, content = content)
                  } else
                    ZuneBackground(
                        mode = effectiveHubMode,
                        glow = zuneGlow,
                        accentColor = groundAccent,
                        customWallpaperPathOverride = hubBackdrop.customPath,
                        systemWallpaper = hubBackdrop.systemWallpaper,
                        forceModeOverride = effectiveHubMode,
                        modifier = groundModifier
                    ) {
                        if (effectiveHubMode != BackgroundMode.SOLID) {
                            ZuneWallpaperOverlay(alpha = hubBackgroundOpacity, isHubOverlay = true)
                        }
                        Box(modifier = Modifier.fillMaxSize(), content = content)
                    }
                } }

                /** A hub's own ground inside whichever pane it is drawn in. */
                val hubBackground: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
                    hubGround(Modifier) {
                        CompositionLocalProvider(com.serkantkn.zeta.ui.components.LocalHubGround provides hubGround) {
                            // A television may crop the picture's top: the hub's own content keeps
                            // clear of it, while its ground still runs to the edge.
                            if (isTv) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = TV_HUB_INSET_TOP)
                                ) { content() }
                            } else {
                                content()
                            }
                        }
                    }
                }

                // Every open hub is composed exactly once and stays composed for as long as it is
                // open — that is what lets a hub the Home key put away keep its page, its scroll
                // and its half-typed text. The content is *movable* so that stepping between the
                // visible chrome and the host underneath is a move rather than a rebuild, and a
                // rebuild is precisely what "still running" must not mean.
                val hubContents = remember { mutableMapOf<com.serkantkn.zeta.data.model.HubId, @Composable () -> Unit>() }
                val hubContentFor: (com.serkantkn.zeta.data.model.HubId) -> @Composable () -> Unit = { hub ->
                    hubContents.getOrPut(hub) {
                        movableContentOf {
                            // Its own navigation-event dispatcher, switched off while the hub is
                            // not the one on screen. Back arrives through this tree now, not
                            // through OnBackPressedDispatcher, and a disabled dispatcher passes
                            // nothing to the handlers under it.
                            val backOwner = rememberNavigationEventDispatcherOwner(
                                enabled = hub in liveHubs.value
                            )
                            CompositionLocalProvider(
                                LocalNavigationEventDispatcherOwner provides backOwner
                            ) {
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

                // The hub the overlay is really drawing, and only while it is drawing it: the
                // hinge reaches zero a frame before activeHub is cleared, and a hub that belongs
                // to neither layer for even one pass has its composition thrown away — which is
                // the one thing "still running" may not do.
                val visibleHub = activeHub.takeIf { hingeProgress.value > 0f }
                val shownHubs = if (navState.isSplitMode) {
                    setOfNotNull(navState.leftHub, navState.rightHub)
                } else {
                    setOfNotNull(visibleHub)
                }
                // Back goes to the hub in use: in a split, the pane last touched.
                SideEffect {
                    liveHubs.value = if (navState.isSplitMode) setOfNotNull(navState.focusedSplitHub) else shownHubs
                }

                // LAYER 0 — every open hub that is not on screen: composed so it goes on living,
                // drawn by nobody, and deaf to touch. The pointer events are consumed on the
                // Initial pass so the hubs below never see them, and the layer sits under
                // everything else, so an event reaches it only when nothing above wanted it.
                val hiddenHubs = navState.openHubs.filter { it !in shownHubs }
                if (hiddenHubs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                            .drawWithContent { /* nothing: nobody can see these */ }
                            // Nor reach them with a remote or a keyboard's arrows.
                            .tvFocusFence(true)
                            // Nor should a screen reader find them: without this, TalkBack walks
                            // straight into a hub that is not on screen.
                            .clearAndSetSemantics { }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.forEach { it.consume() }
                                    }
                                }
                            }
                    ) {
                        CompositionLocalProvider(LocalScreenAwake provides false) {
                            hiddenHubs.forEach { hub -> hubContentFor(hub)() }
                        }
                    }
                }

                if (navState.isSplitMode) {
                    // ════════════════════════════════════════════════════════
                    // SPLIT — two hubs, or a hub and Start to choose the other from: side by side
                    // on a tablet, one over the other on a phone (SplitScreen). What is already
                    // running is offered first in the empty pane, because that is nearly always
                    // what the split is being opened for; Start is underneath it for anything else.
                    // ════════════════════════════════════════════════════════
                    var offeringRunning by remember(navState.isSplitMode) { mutableStateOf(true) }
                    val runningChoices = navState.backgroundHubs.filter { it != navState.rightHub && it != navState.leftHub }
                    val previews by com.serkantkn.zeta.data.repository.HubPreviewStore
                        .previews.collectAsState()
                    SplitScreen(
                        navState = navState,
                        vertical = !isWideScreen,
                        appear = { splitAnimProgress.value },
                        modifier = Modifier.zIndex(3f),
                        start = {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (page) {
                                    0 -> SocialHubScreen(isCurrentPage = pagerState.currentPage == 0)
                                    1 -> startPage(false)
                                    2 -> if (hasTilesPage) {
                                        tilesPage()
                                    } else {
                                        AppsHubScreen(isCurrentPage = pagerState.currentPage == 2)
                                    }
                                    3 -> AppsHubScreen(isCurrentPage = pagerState.currentPage == 3)
                                }
                            }
                        },
                        startAbove = if (offeringRunning && runningChoices.isNotEmpty()) {
                            {
                                com.serkantkn.zeta.ui.screens.home.RunningHubsRow(
                                    hubs = runningChoices,
                                    previews = previews,
                                    onPick = { hub -> navState.openHub(hub) },
                                    onStop = { hub -> navState.stopHub(hub) },
                                    onDismiss = { offeringRunning = false },
                                    modifier = Modifier.statusBarsPadding(),
                                    // Half a phone: the cards keep the room under them for Start.
                                    compact = com.serkantkn.zeta.ui.components.isShortScreen()
                                )
                            }
                        } else null,
                        hub = { hub -> hubBackground { hubContentFor(hub)() } }
                    )
                } else {
                    // ════════════════════════════════════════════════════════
                    // NORMAL SINGLE HUB LAUNCHER MODE
                    // ════════════════════════════════════════════════════════
                    // LAYER 1 — Main pager (Home + Apps)
                    //
                    // Once the hub in front of it has turned far enough to be opaque, this layer
                    // is finished being looked at: it keeps its composition — the board's scroll
                    // position and the tiles' places in the turning ripple have to survive — but
                    // it stops being drawn, and LocalScreenAwake tells the tiles to stop turning
                    // over behind a hub nobody can see through. The progress is read in the draw
                    // phase, so the hinge still costs no recomposition.
                    CompositionLocalProvider(LocalScreenAwake provides !isHubOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .graphicsLayer {
                                val progress = hingeProgress.value
                                if (vistaTv) {
                                    scaleX = 1f + VISTA_ZOOM_PAST * progress
                                    scaleY = 1f + VISTA_ZOOM_PAST * progress
                                    alpha = if (layoutReady) 1f - progress else 0f
                                    return@graphicsLayer
                                }
                                if (tabletStart) {
                                    // Windows 8: Start does not turn away; the hub comes up over it.
                                    alpha = if (layoutReady) 1f else 0f
                                    return@graphicsLayer
                                }
                                if (zuneStart) {
                                    // The Zune list has already slid and zoomed away; what is left
                                    // of it fades as the hub slides in.
                                    alpha = if (layoutReady) (1f - progress * 1.5f).coerceIn(0f, 1f) else 0f
                                    return@graphicsLayer
                                }
                                rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * progress
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density
                                alpha = if (layoutReady) (1f - progress * 1.5f).coerceIn(0f, 1f) else 0f
                            }
                            .drawWithContent {
                                val seen = if (tabletStart || vistaTv) hingeProgress.value < 1f else (1f - hingeProgress.value * 1.5f) > 0f
                                if (layoutReady && seen) drawContent()
                            }
                            // Behind an open hub Start must not answer touches. On the phone it is
                            // turned edge-on and out of reach; on the tablet it stays where it is
                            // (the hub only fades up over it), so a tap on an empty part of the
                            // hub fell through to the tile underneath and opened that instead.
                            .then(if (isHubOpen) Modifier.blockPointerInput() else Modifier)
                            // Nor, behind an open hub, to a remote's arrows.
                            .tvFocusFence(isHubOpen)
                    ) {
                        // On a tablet a page is a long way across: the pager's own rule — half
                        // the page, or a throw — asks for a drag the width of a hand. There a
                        // sixth of the page is enough to turn it.
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                            // On a TV the remote moves the focus, not the pages: Start is the whole screen.
                            userScrollEnabled = navState.currentHub == null && !isTv,
                            flingBehavior = PagerDefaults.flingBehavior(
                                state = pagerState,
                                snapPositionalThreshold = if (isWideScreen) TABLET_PAGE_TURN else 0.5f
                            )
                        ) { page ->
                            when (page) {
                                0 -> SocialHubScreen(isCurrentPage = pagerState.currentPage == 0)
                                1 -> startPage(isHubOpen)
                                2 -> if (hasTilesPage) {
                                    tilesPage()
                                } else {
                                    AppsHubScreen(isCurrentPage = pagerState.currentPage == 2)
                                }
                                3 -> AppsHubScreen(isCurrentPage = pagerState.currentPage == 3)
                            }
                        }
                    }
                    }

                    // LAYER 2 — Hub overlay
                    if (visibleHub != null) {
                        // On a television the remote goes into the hub as it opens. Most hubs
                        // hand it to their chosen heading themselves; one that does not (the
                        // files hub's explorer) is given it at its first stop, so the remote is
                        // never left with nothing to move.
                        val hubFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                        var hubHasFocus by remember { mutableStateOf(false) }
                        if (isTv) {
                            LaunchedEffect(visibleHub) {
                                kotlinx.coroutines.delay(TV_HUB_FOCUS_GRACE_MILLIS)
                                if (!hubHasFocus) hubFocus.tvTake()
                            }
                            // When whatever had the focus closes under it (a photo viewer, a
                            // dialog), the next arrow puts it back where the remote last opened
                            // something, inside this hub.
                            val rescueScope = rememberCoroutineScope()
                            val rescueView = LocalView.current
                            val rescueFocus = LocalFocusManager.current
                            DisposableEffect(visibleHub) {
                                val rescue: () -> Unit = {
                                    rescueScope.launch {
                                        if (hubFocus.tvTake()) {
                                            com.serkantkn.zeta.ui.tv.TvFocus.pressedAt?.let {
                                                com.serkantkn.zeta.ui.tv.tvWalkFocusTo(rescueView, rescueFocus, it)
                                            }
                                        }
                                    }
                                }
                                com.serkantkn.zeta.ui.tv.TvFocus.hubRescue = rescue
                                onDispose {
                                    if (com.serkantkn.zeta.ui.tv.TvFocus.hubRescue === rescue) {
                                        com.serkantkn.zeta.ui.tv.TvFocus.hubRescue = null
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(2f)
                                .then(
                                    if (!isTv) Modifier
                                    else Modifier
                                        .onFocusChanged { hubHasFocus = it.hasFocus }
                                        .focusRequester(hubFocus)
                                        .focusGroup()
                                )
                                .graphicsLayer {
                                    val progress = hingeProgress.value
                                    if (vistaTv) {
                                        val grow = VISTA_ZOOM_FROM + (1f - VISTA_ZOOM_FROM) * progress
                                        scaleX = grow
                                        scaleY = grow
                                        alpha = progress
                                        return@graphicsLayer
                                    }
                                    if (tabletStart) {
                                        // Windows 8: the hub fades up over the launch splash, and
                                        // fades away again over Start's tiles coming back in; one
                                        // hub gives way to another the same way.
                                        alpha = progress
                                        return@graphicsLayer
                                    }
                                    // The people hub does not swing in: as on Windows Phone 7 it
                                    // shows itself flat once Start has turned away, and then glides
                                    // round its sections on its own.
                                    if (isHubHingeOpening && visibleHub == HubType.PEOPLE) {
                                        alpha = ((progress - PEOPLE_SHOW_FROM) / PEOPLE_SHOW_OVER).coerceIn(0f, 1f)
                                        return@graphicsLayer
                                    }
                                    if (zuneStart) {
                                        // The Zune's way: the hub slides in a little from the right
                                        // as it appears, and back the same way as it goes.
                                        // A hub whose name is flying up to it does not slide: it fades in
                                        // under its title, as the Zune HD's did.
                                        translationX = if (zuneHubFlight?.isFlying == true) 0f else zuneHubSlidePx * (1f - progress)
                                        alpha = progress
                                        return@graphicsLayer
                                    }
                                    // Opening: -90° (outside, in front) -> 0°. Closing: 0° -> +90° (into the depth).
                                    val direction = if (isHubHingeOpening) -1f else 1f
                                    rotationY = direction * HingeAnimation.MAX_ROTATION_DEGREES * (1f - progress)
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density
                                    alpha = (progress * 1.5f - 0.2f).coerceIn(0f, 1f)
                                }
                        ) {
                            CompositionLocalProvider(
                                com.serkantkn.zeta.ui.components.LocalHubArrival provides { hingeProgress.value }
                            ) {
                                hubBackground { hubContentFor(visibleHub)() }
                            }
                        }
                    }
                }

                // The banners: over Start and over any hub. Start and the hubs carry z-indices of
                // their own (1 and 2) so that a hub kept running behind Start stays behind it, and
                // anything left at zero — which these were — went under the lot: the volume bar
                // and a notification were sliding in beneath the hub names.
                // Frosted as the tiles are, when they are: the wallpaper behind them blurred.
                CompositionLocalProvider(
                    com.serkantkn.zeta.ui.components.LocalBannerAlpha provides
                        com.serkantkn.zeta.ui.components.bannerAlpha(tileBlur, tileOpacity)
                ) {
                Box(modifier = Modifier.fillMaxSize().zIndex(BANNER_Z)) {
                    // Windows Phone Toast Notification Banner
                    if (notificationStyle == NotificationStyle.WINDOWS_PHONE) {
                        WpToastNotification(
                            message = latestNotification,
                            onDismiss = { SocialRepository.clearToast() }
                        )
                    }

                    // Windows Phone Call Screen Overlay
                    val callStatus by com.serkantkn.zeta.data.service.CallManager.callStatus.collectAsState()
                    if (callStatus != com.serkantkn.zeta.data.service.CallStatus.IDLE) {
                        com.serkantkn.zeta.ui.screens.phone.WpCallScreen()
                    }

                    // Windows Phone Style Volume Control Banner
                    WpVolumeControl()
                }
                }

                // The tour, and the note about what changed. Over everything, because a first run
                // that can be swiped away behind the launcher is not a first run.
                // ── How to find the running hubs, told once ──
                if (switcherTipShown && !switcherOpen) {
                    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
                        com.serkantkn.zeta.ui.screens.home.SwitcherTip(onDismiss = { switcherTipShown = false })
                    }
                }

                // ── Going straight from one running hub to another ──
                if (switcherOpen) {
                    val previews by com.serkantkn.zeta.data.repository.HubPreviewStore
                        .previews.collectAsState()
                    // Over the hub layers, which carry a zIndex of their own.
                    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
                    com.serkantkn.zeta.ui.screens.home.HubSwitcher(
                        hubs = navState.backgroundHubs,
                        previews = previews,
                        onPick = { hub ->
                            switcherOpen = false
                            navState.openHub(hub)
                        },
                        onStop = { hub -> navState.stopHub(hub) },
                        onSplit = if (isTv) null else ({ hub ->
                            switcherOpen = false
                            navState.openSplit(hub)
                        }),
                        onDismiss = { switcherOpen = false }
                    )
                    }
                }

                val onboardingViewModel: OnboardingViewModel = viewModel()
                val firstRun by onboardingViewModel.screen.collectAsState()
                // The tour teaches touch: swipes, holds, the shade. A television gets one of its
                // own later; until then it goes straight to Start.
                LaunchedEffect(isTv, firstRun) {
                    if (isTv && firstRun is FirstRunScreen.Tour) onboardingViewModel.finish()
                }
                if (firstRun != FirstRunScreen.None && !(isTv && firstRun is FirstRunScreen.Tour)) Box(modifier = Modifier.fillMaxSize().zIndex(FIRST_RUN_Z)) {
                when (val screen = firstRun) {
                    is FirstRunScreen.Tour -> OnboardingScreen(
                        tour = screen,
                        viewModel = onboardingViewModel,
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

                // Pro's trial, offered once on Start - after the tour and what is new, never over
                // them - and begun only if the answer is yes; settings › about › Zeta Pro has it
                // for later.
                val decided by onboardingViewModel.decided.collectAsState()
                val trialState by com.serkantkn.zeta.data.billing.ProTrial.state.collectAsState()
                val trialOffered by com.serkantkn.zeta.data.billing.ProTrial.offered.collectAsState()
                var offeringTrial by remember { mutableStateOf(false) }
                val mayOffer = decided && firstRun == FirstRunScreen.None && !trialOffered &&
                    trialState == com.serkantkn.zeta.data.billing.ProTrial.State.Available && !com.serkantkn.zeta.util.Pro.purchased
                LaunchedEffect(mayOffer) {
                    if (!mayOffer) return@LaunchedEffect
                    // A moment on Start first, so it is not the first thing in the way.
                    com.serkantkn.zeta.ui.animation.motionDelay(TRIAL_OFFER_DELAY_MILLIS)
                    offeringTrial = true
                }
                if (offeringTrial) {
                    ProTrialOffer(
                        onStart = {
                            offeringTrial = false
                            com.serkantkn.zeta.data.billing.ProTrial.begin()
                        },
                        onLater = {
                            offeringTrial = false
                            com.serkantkn.zeta.data.billing.ProTrial.markOffered()
                        }
                    )
                }

                // On a television, the white frame round whatever the remote is on (ui/tv).
                if (isTv) {
                    com.serkantkn.zeta.ui.tv.TvFocusFrame(
                        modifier = Modifier.fillMaxSize().zIndex(TV_FOCUS_Z)
                    )
                }
                // A paired phone's call or message, when it cannot have a window of its own over
                // other apps: Start shows it while it is on the screen (data/link).
                if (isTv) {
                    val linkShown by com.serkantkn.zeta.data.link.TvLinkNotices.shown.collectAsState()
                    val linkInWindow by com.serkantkn.zeta.data.link.TvLinkNotices.inWindow.collectAsState()
                    if (!linkInWindow && linkShown != null) {
                        val noticeCorner = if (com.serkantkn.zeta.ui.tv.LocalVista.current) Alignment.BottomEnd else Alignment.BottomCenter
                        Box(modifier = Modifier.fillMaxSize().zIndex(BANNER_Z), contentAlignment = noticeCorner) {
                            com.serkantkn.zeta.ui.tv.TvLinkNoticeCard(linkShown)
                        }
                    }
                }
                // The screensaver and the music's own screen, under a phone's call or message.
                if (isTv) {
                    val idleMedia by homeHubViewModel.nowPlaying.collectAsState()
                    com.serkantkn.zeta.ui.tv.TvIdleScreen(idleMedia, modifier = Modifier.fillMaxSize().zIndex(BANNER_Z - 0.5f))
                }
                // The Xbox's guide, over Start or any hub (ui/tv/XboxGuidePanel).
                if (isTv && !com.serkantkn.zeta.ui.tv.LocalVista.current) {
                    val guideOpen by com.serkantkn.zeta.ui.tv.XboxGuide.open.collectAsState()
                    if (guideOpen) {
                        val media by homeHubViewModel.nowPlaying.collectAsState()
                        val hubShowing = navState.currentHub != null
                        Box(modifier = Modifier.fillMaxSize().zIndex(BANNER_Z + 1f)) {
                            com.serkantkn.zeta.ui.tv.XboxGuidePanel(
                                media = media,
                                hubOpen = hubShowing,
                                onOpenHub = { hub -> if (hub != HubType.HOME) navState.openHub(hub) },
                                onClose = { com.serkantkn.zeta.ui.tv.XboxGuide.hide() }
                            )
                        }
                    }
                    // Put away, the guide hands the remote back to what is under it.
                    var guideWasOpen by remember { mutableStateOf(false) }
                    LaunchedEffect(guideOpen) {
                        if (guideWasOpen && !guideOpen) {
                            kotlinx.coroutines.delay(GUIDE_FOCUS_BACK_MILLIS)
                            val rescue = if (navState.currentHub != null) com.serkantkn.zeta.ui.tv.TvFocus.hubRescue
                            else com.serkantkn.zeta.ui.tv.TvFocus.startRescue
                            rescue?.invoke()
                        }
                        guideWasOpen = guideOpen
                    }
                }
                // Media Center and the Xbox both opened on their logo, once each time they started.
                if (isTv) {
                    var splashDone by remember { mutableStateOf(com.serkantkn.zeta.ui.tv.TvDesign.splashShown) }
                    if (!splashDone) {
                        val finish = {
                            com.serkantkn.zeta.ui.tv.TvDesign.splashShown = true
                            splashDone = true
                        }
                        Box(modifier = Modifier.fillMaxSize().zIndex(FIRST_RUN_Z + 1f)) {
                            if (com.serkantkn.zeta.ui.tv.LocalVista.current) {
                                com.serkantkn.zeta.ui.tv.VistaSplash(onDone = finish)
                            } else {
                                com.serkantkn.zeta.ui.tv.XboxSplash(onDone = finish)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Above Start (1), the hubs (2) and the hub switcher (10): nothing on screen may cover a banner. */
/** How much of a page a tablet has to be dragged before letting go turns it. */
private const val TABLET_PAGE_TURN = 0.15f

private const val BANNER_Z = 20f

/** How long after the guide is put away the remote is handed back, the guide's window gone. */
private const val GUIDE_FOCUS_BACK_MILLIS = 120L

/** Vista's page zoom: how far the page going grows past, and how small the one coming starts. */
private const val VISTA_PAGE_MILLIS = 380
private const val VISTA_ZOOM_PAST = 0.25f
private const val VISTA_ZOOM_FROM = 0.7f

/** The first-run tour and the what's-new note, over everything including the banners. */
private const val FIRST_RUN_Z = 30f

/**
 * What a hub keeps clear of at the top of a TV, on top of its own margin. Across, the hubs' own
 * margins already keep clear of the edges, and the bar along the foot is meant to run edge to
 * edge.
 */
private val TV_HUB_INSET_TOP = 16.dp

/** How long a hub opening on a TV is given to take the remote itself before it is handed in. */
private const val TV_HUB_FOCUS_GRACE_MILLIS = 900L

/** A TV's focus frame, over everything, so it is never hidden under what it marks. */
private const val TV_FOCUS_Z = 40f

@Composable
private fun RenderHubScreen(
    hub: com.serkantkn.zeta.data.model.HubId,
    navState: ZuneNavigationState,
    messagingViewModel: com.serkantkn.zeta.ui.screens.messaging.MessagingHubViewModel,
    settingsViewModel: SettingsViewModel,
    context: Context
) {
    when (hub) {
        // Another app's hub, drawn from what it describes (PLATFORM.md).
        is com.serkantkn.zeta.data.model.ExternalHub -> com.serkantkn.zeta.ui.screens.external.ExternalHubScreen(
            hub = hub,
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        is HubType -> RenderBuiltinHubScreen(hub, navState, messagingViewModel, settingsViewModel, context)
    }
}

@Composable
private fun RenderBuiltinHubScreen(
    hub: HubType,
    navState: ZuneNavigationState,
    messagingViewModel: com.serkantkn.zeta.ui.screens.messaging.MessagingHubViewModel,
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
        HubType.CLOCK -> com.serkantkn.zeta.ui.screens.clock.ClockHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
        HubType.INTERNET -> com.serkantkn.zeta.ui.screens.browser.BrowserHubScreen(
            onClose = { if (!navState.popHub()) navState.closeHub() },
            onOpenSettings = { targetTab ->
                settingsViewModel.setTargetTab(targetTab)
                navState.openHub(HubType.SETTINGS)
            }
        )
        HubType.CALENDAR -> com.serkantkn.zeta.ui.screens.calendar.CalendarHubScreen(onBack = { if (!navState.popHub()) navState.closeHub() })
        HubType.MESSAGING -> com.serkantkn.zeta.ui.screens.messaging.MessagingHubScreen(
            onClose = { if (!navState.popHub()) navState.closeHub() },
            viewModel = messagingViewModel
        )
        HubType.FILES -> com.serkantkn.zeta.ui.screens.files.FilesHubScreen(
            onClose = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.NOTES -> com.serkantkn.zeta.ui.screens.notes.NotesHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.EMAIL -> com.serkantkn.zeta.ui.screens.email.EmailHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.CALCULATOR -> com.serkantkn.zeta.ui.screens.calculator.CalculatorHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.WEATHER -> com.serkantkn.zeta.ui.screens.weather.WeatherHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.NEWS -> com.serkantkn.zeta.ui.screens.news.NewsHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() }
        )
        HubType.CAMERA -> com.serkantkn.zeta.ui.screens.camera.CameraHubScreen(
            onBack = { if (!navState.popHub()) navState.closeHub() },
            // The shot just taken belongs in this launcher's own pictures hub, on top of the
            // camera, so closing it puts the viewfinder back rather than the Start screen.
            onOpenPictures = { uri ->
                com.serkantkn.zeta.data.repository.PicturesBridge.openPhoto(uri)
                navState.openHub(HubType.PICTURES)
            }
        )
        else -> {}
    }
}

/**
 * The pull from the top of Start. With the Windows Phone action center on (the default) it
 * brings that down and never the system shade; with it off, the system shade as before.
 */
internal fun expandNotificationPanel(context: Context) {
    // Nothing is pulled down on a television: a remote's "up" at the top of a list is only "up".
    if (com.serkantkn.zeta.ui.theme.isTelevision(context)) return
    kotlinx.coroutines.MainScope().launch {
        val wpPanel = runCatching {
            context.applicationContext.appContainer.settingsDataStore.wpActionCenter.first()
        }.getOrDefault(true)
        if (wpPanel) {
            if (!com.serkantkn.zeta.data.service.ActionCenterOverlay.open(context)) {
                ZuneLog.w("LauncherScreen", "the action center has nothing to draw with")
            }
        } else {
            expandSystemShade(context)
        }
    }
}

private fun expandSystemShade(context: Context) {
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

/** How far a list may move under the finger before the gesture counts as a scroll. */
private const val SCROLL_SLOP_DP = 3f

/** And how far it has to be pulled from a list standing at its top to bring the shade down. */
private const val SHADE_PULL_DP = 20f

/** Two presses of Home closer together than this ask for the switcher, not for Start. */
private const val DOUBLE_PRESS_MILLIS = 450L

/** The tablet's hub coming up over the launch splash, and going away over Start. */
private const val WIN8_HUB_FADE_IN_MILLIS = 220
private const val WIN8_HUB_FADE_OUT_MILLIS = 240

/** Long enough for the hinge to have put the hub away before the balloon comes. */
private const val SWITCHER_TIP_DELAY_MILLIS = 600L

/** How long Start stands before the Pro trial is offered on it. */
private const val TRIAL_OFFER_DELAY_MILLIS = 1_500L

/** The Pro trial, offered: begin it now, or not now (settings has it for later). */
@Composable
private fun ProTrialOffer(onStart: () -> Unit, onLater: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    com.serkantkn.zeta.ui.components.ZuneFlipDialog(
        onDismissRequest = onLater,
        title = stringResource(R.string.pro_trial_offer_title),
        confirmButton = {
            com.serkantkn.zeta.ui.components.ZuneDialogButton(
                text = stringResource(R.string.pro_trial_offer_start),
                onClick = { dismissWithAnim { onStart() } },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            com.serkantkn.zeta.ui.components.ZuneDialogButton(
                text = stringResource(R.string.pro_trial_offer_later),
                onClick = { dismissWithAnim { onLater() } },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Text(
            text = stringResource(R.string.pro_trial_offer_body),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
            // The dialog is dark in either theme.
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

/** How far two fingers have to be drawn up a phone before the hub steps into the top half. */
private const val SPLIT_PULL_PX = 400f

/** The longest the pager stays hidden waiting for the stored Start layout on a cold start. */
private const val LAYOUT_WAIT_MILLIS = 1_500L

/** Takes every touch before anything inside can, so nothing under it answers. */
private fun Modifier.blockPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                .changes.forEach { it.consume() }
        }
    }
}

/**
 * The shade over the wallpaper behind Start and the pages beside it. With frosted tiles, Start
 * shows its wallpaper bare - the frost under the tiles is what keeps them readable - and the
 * shade comes back as the pages slide towards the social page or the app list, which still
 * need it under their text.
 */
@Composable
private fun StartShade(pagerState: androidx.compose.foundation.pager.PagerState, frosted: Boolean) {
    if (!frosted) {
        ZuneWallpaperOverlay()
        return
    }
    ZuneWallpaperOverlay(
        modifier = Modifier.graphicsLayer {
            // Start is the second page; read here, the slide only redraws the shade.
            val fromStart = pagerState.currentPage + pagerState.currentPageOffsetFraction - 1f
            alpha = kotlin.math.abs(fromStart).coerceIn(0f, 1f)
        }
    )
}

/** How far Start has turned away before the people hub's ground shows, as a share of the hinge. */
private const val PEOPLE_SHOW_FROM = 0.55f
/** Over how much more of the hinge the ground comes up to full: done before its pages turn in. */
private const val PEOPLE_SHOW_OVER = 0.3f
