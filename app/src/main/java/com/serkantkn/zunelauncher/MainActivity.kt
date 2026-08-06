package com.serkantkn.zunelauncher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.LockScreenState
import com.serkantkn.zunelauncher.ui.screens.LauncherScreen
import com.serkantkn.zunelauncher.ui.screens.lock.LockScreenActivity
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsViewModel
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import kotlinx.coroutines.flow.MutableStateFlow

object MainActivityEvents {
    val triggerActionCenter = MutableStateFlow(false)
}

class MainActivity : ComponentActivity() {

    private var touchStartY = 0f
    private var isInterceptingStatusBar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

        setLauncherContent()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ev.rawY < 180f) { // Status Bar top region
                        touchStartY = ev.rawY
                        isInterceptingStatusBar = true
                    } else {
                        isInterceptingStatusBar = false
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isInterceptingStatusBar) {
                        val deltaY = ev.rawY - touchStartY
                        if (deltaY > 15f) {
                            isInterceptingStatusBar = false
                            MainActivityEvents.triggerActionCenter.value = true
                            return true // CONSUME event! Completely blocks Android system status bar expansion!
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isInterceptingStatusBar = false
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        if (LockScreenState.isLocked) {
            val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(lockIntent)
            overridePendingTransition(0, 0)
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
