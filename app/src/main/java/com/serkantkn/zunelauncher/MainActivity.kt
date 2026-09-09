package com.serkantkn.zunelauncher

import android.content.Context
import android.content.Intent
import com.serkantkn.zunelauncher.data.repository.EmailBridge
import com.serkantkn.zunelauncher.data.model.NoteFallbackTitles
import com.serkantkn.zunelauncher.util.AppLocale
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
    }

    private var currentVolumeBarStyle = VolumeBarStyle.WINDOWS_PHONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsDataStore = appContainer.settingsDataStore
        lifecycleScope.launch {
            settingsDataStore.volumeBarStyle.collect { style ->
                currentVolumeBarStyle = style
            }
        }
        
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }

        // Prevent back button from closing the launcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consumed — launcher should never close via back button
            }
        })

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Force maximum display refresh rate (120Hz) for liquid smooth animations
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { maxMode ->
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = maxMode.modeId
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            @Suppress("DEPRECATION")
            val defaultDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            @Suppress("DEPRECATION")
            val maxRate = defaultDisplay?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 120f
            window.attributes = window.attributes.apply {
                @Suppress("DEPRECATION")
                preferredRefreshRate = maxRate
            }
        }

        handleEmailIntent(intent)
        handleSettingsIntent(intent)
        setLauncherContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEmailIntent(intent)
        handleSettingsIntent(intent)
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
        com.serkantkn.zunelauncher.data.service.WpToastOverlay.hide()
        com.serkantkn.zunelauncher.data.service.WpVolumeOverlay.hide()
    }

    override fun onPause() {
        com.serkantkn.zunelauncher.data.repository.SocialRepository.isLauncherForeground = false
        super.onPause()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (currentVolumeBarStyle == VolumeBarStyle.WINDOWS_PHONE && event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    com.serkantkn.zunelauncher.data.service.VolumeController.handleVolumeKey(this, isUp = true)
                    return true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    com.serkantkn.zunelauncher.data.service.VolumeController.handleVolumeKey(this, isUp = false)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
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

            ZuneLauncherTheme(
                themeMode = themeMode,
                accentColor = accentColor,
                dynamicThemeColor = dynamicThemeColor,
                customThemeColor = customThemeColor,
                solidBackgroundEnabled = solidBackgroundEnabled,
                fontScale = fontScale
            ) {
                LauncherScreen()
            }
        }
    }
}
