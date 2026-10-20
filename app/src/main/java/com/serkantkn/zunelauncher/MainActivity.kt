package com.serkantkn.zunelauncher

import androidx.compose.foundation.layout.fillMaxSize
import android.content.Context
import android.content.Intent
import com.serkantkn.zunelauncher.data.repository.EmailBridge
import com.serkantkn.zunelauncher.data.model.NoteFallbackTitles
import com.serkantkn.zunelauncher.util.AppLocale
import com.serkantkn.zunelauncher.util.DeviceClass
import com.serkantkn.zunelauncher.di.appContainer
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.VolumeBarStyle
import com.serkantkn.zunelauncher.ui.screens.LauncherScreen
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsViewModel
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val wrapped = AppLocale.wrap(newBase)
        NoteFallbackTitles.refresh(wrapped)
        super.attachBaseContext(wrapped)
    }

    companion object {
        /** Set by ReceiveNoteActivity; the Notes hub itself is opened through NotesBridge. */
        const val EXTRA_OPEN_NOTES = "open_notes"

        /** Set by ComposeEmailActivity / new-mail notifications; details travel through EmailBridge. */
        const val EXTRA_OPEN_EMAIL = "open_email"
        const val EXTRA_OPEN_EMAIL_ACCOUNT = "open_email_account"
        const val EXTRA_OPEN_EMAIL_FOLDER = "open_email_folder"
        const val EXTRA_OPEN_EMAIL_UID = "open_email_uid"

        /** Set by the keyboard's quick settings; carries the name of a SettingsTab entry. */
        const val EXTRA_OPEN_SETTINGS_TAB = "open_settings_tab"

        /** How long a wallpaper change is given to settle before its colour is read. */
        private const val WALLPAPER_SETTLE_MILLIS = 600L

        /** Set by a message notification being tapped; carries the conversation to open. */
        const val EXTRA_OPEN_MESSAGE_THREAD = "open_message_thread"

        /** Carries the name of a HubType to bring forward; used by the clock's own notifications. */
        const val EXTRA_OPEN_HUB = "open_hub"

        /** Carries the name of a ClockPage, for a request that names a page as well as the hub. */
        const val EXTRA_OPEN_CLOCK_PAGE = "open_clock_page"

        /** Set by the call notification: the call screen was hidden with Back and is wanted back. */
        const val EXTRA_OPEN_CALL = "open_call"

        /**
         * The remote's Home on a TV, caught by the accessibility service. It is not sent as a
         * HOME intent: that makes Android start a second Z Launcher of the home kind beside the
         * one opened from the TV's own app list.
         */
        const val EXTRA_HOME_KEY = "home_key"

        /** The remote's Home held down on a TV in the Xbox design: the guide, over whatever was on. */
        const val EXTRA_OPEN_GUIDE = "open_guide"
    }

    private var currentVolumeBarStyle = VolumeBarStyle.WINDOWS_PHONE

    private val wallpaperChanges = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val wallpaperReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            wallpaperChanges.value++
        }
    }

    /**
     * The automatic theme colour follows the wallpaper the launcher draws: worked out when the
     * launcher starts, when the wallpaper changes — from here or from the system — and when the
     * automatic colour is chosen. It used to be worked out only when a wallpaper was cropped in
     * the launcher, so a wallpaper set anywhere else never changed it.
     */
    private fun followWallpaperAccent(settingsDataStore: com.serkantkn.zunelauncher.data.datastore.SettingsDataStore) {
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            wallpaperReceiver,
            android.content.IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsDataStore.accentColor,
                settingsDataStore.customWallpaperPath,
                wallpaperChanges
            ) { accent, path, _ -> accent to path }
                .collectLatest { (accent, path) ->
                    if (accent != com.serkantkn.zunelauncher.data.model.AccentColor.DYNAMIC) return@collectLatest
                    // A wallpaper set from outside clears the launcher's own picture a moment
                    // later (see ZuneBackground); the colour is read once that has settled.
                    kotlinx.coroutines.delay(WALLPAPER_SETTLE_MILLIS)
                    val color = com.serkantkn.zunelauncher.util.WallpaperAccent.fromWallpaper(applicationContext, path)
                        ?: return@collectLatest
                    settingsDataStore.setDynamicThemeColor(color)
                }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(wallpaperReceiver) }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsDataStore = appContainer.settingsDataStore
        lifecycleScope.launch {
            settingsDataStore.volumeBarStyle.collect { style ->
                currentVolumeBarStyle = style
            }
        }

        followWallpaperAccent(settingsDataStore)

        // A call has to be answerable without unlocking the phone, and has to wake the screen.
        lifecycleScope.launch {
            com.serkantkn.zunelauncher.data.service.CallManager.callStatus.collect { status ->
                val onACall = status != com.serkantkn.zunelauncher.data.service.CallStatus.IDLE
                setShowWhenLocked(onACall)
                setTurnScreenOn(onACall)
            }
        }
        
        lockOrientation(resources.configuration.smallestScreenWidthDp)

        // Prevent back button from closing the launcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consumed — launcher should never close via back button
            }
        })

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestFastestRefreshRate()

        handleEmailIntent(intent)
        handleSettingsIntent(intent)
        handleMessageIntent(intent)
        handleHubIntent(intent)
        // Started by a held Home on a TV: the guide comes up with the launcher.
        if (intent?.getBooleanExtra(EXTRA_OPEN_GUIDE, false) == true) {
            intent.removeExtra(EXTRA_OPEN_GUIDE)
            com.serkantkn.zunelauncher.ui.tv.XboxGuide.show()
        }
        handleDialIntent(intent)
        handleWebIntent(intent)
        handleCallIntent(intent)
        setLauncherContent()
    }

    /**
     * Asks the screen for its fastest mode — on a phone that can keep up.
     *
     * Two things were wrong with asking unconditionally. A phone whose GPU cannot hold 120 Hz
     * misses more deadlines at 120 than it would at 60, so the animations it was meant to smooth
     * read as worse; and picking simply the mode with the highest refresh rate can pick a mode
     * with a *smaller* resolution, because a display's modes vary in both. Only modes the size of
     * the one the screen is already in are considered now.
     */
    private fun requestFastestRefreshRate() {
        if (!DeviceClass.wantsHighRefreshRate(this)) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val current = display?.mode ?: return
            val fastest = display?.supportedModes
                ?.filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                ?.maxByOrNull { it.refreshRate }
                ?: return
            if (fastest.refreshRate <= current.refreshRate) return
            window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
            return
        }

        @Suppress("DEPRECATION")
        val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE)
            as? android.hardware.display.DisplayManager
        @Suppress("DEPRECATION")
        val defaultDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        val maxRate = defaultDisplay?.supportedModes
            ?.filter {
                it.physicalWidth == defaultDisplay.mode.physicalWidth &&
                    it.physicalHeight == defaultDisplay.mode.physicalHeight
            }
            ?.maxOfOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            @Suppress("DEPRECATION")
            preferredRefreshRate = maxRate
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleHomeKey(intent)
        handleEmailIntent(intent)
        handleSettingsIntent(intent)
        handleMessageIntent(intent)
        handleHubIntent(intent)
        handleDialIntent(intent)
        handleWebIntent(intent)
        handleCallIntent(intent)
    }

    /**
     * A number from outside: a `tel:` link in a browser, or a contacts app asking the phone app to
     * DIAL. Both mean "show me the number on the keypad", never "ring it" — ringing is CALL, which
     * this activity does not accept at all, because an exported screen that places calls on
     * anybody's say-so is a hazard.
     */
    private fun handleDialIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val data = intent.data
        val isDial = action == Intent.ACTION_DIAL
        val isTelLink = action == Intent.ACTION_VIEW && data?.scheme == "tel"
        if (!isDial && !isTelLink) return
        val number = data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        if (number.isNotBlank()) com.serkantkn.zunelauncher.data.repository.PhoneBridge.dial(number)
        com.serkantkn.zunelauncher.data.repository.HubBridge.open(
            com.serkantkn.zunelauncher.data.model.HubType.PHONE
        )
        // Seen once; a configuration change must not dial it a second time.
        intent.action = Intent.ACTION_MAIN
        intent.data = null
    }

    /** A web address, while the launcher is the phone's browser: the Internet hub loads it. */
    private fun handleWebIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.data?.takeIf { it.scheme == "http" || it.scheme == "https" }?.toString() ?: return
        com.serkantkn.zunelauncher.data.repository.BrowserBridge.open(url)
        com.serkantkn.zunelauncher.data.repository.HubBridge.open(
            com.serkantkn.zunelauncher.data.model.HubType.INTERNET
        )
        intent.action = Intent.ACTION_MAIN
        intent.data = null
    }

    /** The ongoing-call notification tapped after the call screen was put away with Back. */
    private fun handleCallIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CALL, false) != true) return
        com.serkantkn.zunelauncher.data.service.CallManager.setScreenHidden(false)
        intent.removeExtra(EXTRA_OPEN_CALL)
    }

    /**
     * The Home key, arriving the only way it can: as a fresh MAIN/HOME intent on the activity that
     * is already in front. Only [onNewIntent] asks about it — the same intent on [onCreate] is the
     * launcher starting up, not somebody pressing Home.
     */
    private fun handleHomeKey(intent: Intent?) {
        val isHome = intent?.action == Intent.ACTION_MAIN &&
            (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra(EXTRA_HOME_KEY, false))
        if (intent?.getBooleanExtra(EXTRA_OPEN_GUIDE, false) == true) {
            intent.removeExtra(EXTRA_OPEN_GUIDE)
            com.serkantkn.zunelauncher.ui.tv.XboxGuide.show()
            return
        }
        if (isHome) com.serkantkn.zunelauncher.data.repository.HomeKeyBridge.press()
        // Seen once; a configuration change must not press it again.
        intent?.removeExtra(EXTRA_HOME_KEY)
    }

    /** A message notification tap carries the conversation; hand it to the Messaging hub. */
    private fun handleMessageIntent(intent: Intent?) {
        val threadId = intent?.getLongExtra(EXTRA_OPEN_MESSAGE_THREAD, -1L) ?: return
        if (threadId <= 0L) return
        com.serkantkn.zunelauncher.data.repository.MessagingBridge.openThread(threadId)
        intent.removeExtra(EXTRA_OPEN_MESSAGE_THREAD)
    }

    /**
     * The status bar's alarm symbol and the timer's own notification both land here: they name a
     * hub, and sometimes the page inside it.
     */
    private fun handleHubIntent(intent: Intent?) {
        val hubName = intent?.getStringExtra(EXTRA_OPEN_HUB) ?: return
        val hub = runCatching { com.serkantkn.zunelauncher.data.model.HubType.valueOf(hubName) }.getOrNull()
        if (hub != null) com.serkantkn.zunelauncher.data.repository.HubBridge.open(hub)
        intent.getStringExtra(EXTRA_OPEN_CLOCK_PAGE)?.let { pageName ->
            runCatching { com.serkantkn.zunelauncher.data.repository.ClockPage.valueOf(pageName) }
                .getOrNull()
                ?.let { com.serkantkn.zunelauncher.data.repository.ClockBridge.openPage(it) }
        }
        intent.removeExtra(EXTRA_OPEN_HUB)
        intent.removeExtra(EXTRA_OPEN_CLOCK_PAGE)
    }

    /** A new-mail notification tap carries the message address; hand it to the Email hub. */
    private fun handleEmailIntent(intent: Intent?) {
        val accountId = intent?.getStringExtra(EXTRA_OPEN_EMAIL_ACCOUNT) ?: return
        val folder = intent.getStringExtra(EXTRA_OPEN_EMAIL_FOLDER) ?: return
        val uid = intent.getLongExtra(EXTRA_OPEN_EMAIL_UID, -1L)
        if (uid >= 0) EmailBridge.open(accountId, folder, uid)
        intent.removeExtra(EXTRA_OPEN_EMAIL_ACCOUNT)
    }

    /** The keyboard's "all settings" link asks for the settings hub on a specific tab. */
    private fun handleSettingsIntent(intent: Intent?) {
        val tab = intent?.getStringExtra(EXTRA_OPEN_SETTINGS_TAB) ?: return
        com.serkantkn.zunelauncher.data.repository.SettingsBridge.open(tab)
        intent.removeExtra(EXTRA_OPEN_SETTINGS_TAB)
    }

    override fun onResume() {
        super.onResume()
        com.serkantkn.zunelauncher.data.repository.SocialRepository.isLauncherForeground = true
        // The call screen lives in here, so a call notification is only wanted while this is away.
        com.serkantkn.zunelauncher.data.service.CallManager.setScreenVisible(true)
        com.serkantkn.zunelauncher.data.service.WpToastOverlay.hide()
        com.serkantkn.zunelauncher.data.service.WpVolumeOverlay.hide()
    }

    override fun onPause() {
        com.serkantkn.zunelauncher.data.repository.SocialRepository.isLauncherForeground = false
        com.serkantkn.zunelauncher.data.service.CallManager.setScreenVisible(false)
        super.onPause()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // The TV's focus frame follows closely while the remote is in use.
        if (com.serkantkn.zunelauncher.ui.tv.TvFocus.onKey(window?.decorView?.findFocus(), event)) return true
        // A hub that handles a hardware keyboard (the files hub) gets the keys first.
        if (com.serkantkn.zunelauncher.ui.screens.files.HardwareKeys.handler?.invoke(event) == true) return true
        if (currentVolumeBarStyle == VolumeBarStyle.WINDOWS_PHONE && event.action == android.view.KeyEvent.ACTION_DOWN) {
            // A key the controller declines — while the phone is ringing, say — goes on to the
            // system, which knows how to silence a ringer.
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP ->
                    if (com.serkantkn.zunelauncher.data.service.VolumeController.handleVolumeKey(this, isUp = true)) return true
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN ->
                    if (com.serkantkn.zunelauncher.data.service.VolumeController.handleVolumeKey(this, isUp = false)) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * A phone is held upright; a tablet turns as it likes. A television is neither: it is lying
     * on its side already, and a phone-sized one (960 × 540 dp at 1080p) would otherwise be
     * turned on its end.
     */
    private fun lockOrientation(smallestWidthDp: Int) {
        requestedOrientation = when {
            com.serkantkn.zunelauncher.ui.theme.isTelevision(this) -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            smallestWidthDp < 600 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        lockOrientation(newConfig.smallestScreenWidthDp)
    }

    private fun setLauncherContent() {
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val accentColor by settingsViewModel.accentColor.collectAsState()
            val dynamicThemeColor by settingsViewModel.dynamicThemeColor.collectAsState()
            val customThemeColor by settingsViewModel.customThemeColor.collectAsState()
            val solidBackgroundEnabled by settingsViewModel.solidBackgroundEnabled.collectAsState()
            val fontScale by settingsViewModel.fontScale.collectAsState()
            val animationsEnabled by settingsViewModel.animationsEnabled.collectAsState()
            val homeScreenLayout by settingsViewModel.homeScreenLayout.collectAsState()

            ZuneLauncherTheme(
                themeMode = themeMode,
                accentColor = accentColor,
                dynamicThemeColor = dynamicThemeColor,
                customThemeColor = customThemeColor,
                solidBackgroundEnabled = solidBackgroundEnabled,
                fontScale = fontScale,
                animationsEnabled = animationsEnabled
            ) {
                // The Zune layout moves the Zune HD's way everywhere, not only on its list.
                // The Zune layout's main hubs take the touched name with them as their title; the
                // word flies over the whole launcher, so it is drawn here, above everything.
                val hubFlight = com.serkantkn.zunelauncher.ui.animation.rememberZuneHubFlight()
                androidx.compose.runtime.CompositionLocalProvider(
                    com.serkantkn.zunelauncher.ui.animation.LocalZuneMotion provides
                        (homeScreenLayout == com.serkantkn.zunelauncher.data.model.HomeScreenLayout.ZUNE),
                    com.serkantkn.zunelauncher.ui.animation.LocalZuneHubFlight provides hubFlight
                ) {
                    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        LauncherScreen()
                        com.serkantkn.zunelauncher.ui.animation.ZuneHubFlightOverlay(hubFlight)
                    }
                }
            }
        }
    }
}
