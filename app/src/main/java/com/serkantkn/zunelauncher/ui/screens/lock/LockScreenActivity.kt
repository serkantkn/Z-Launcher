package com.serkantkn.zunelauncher.ui.screens.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.LockScreenMode
import com.serkantkn.zunelauncher.data.model.LockScreenState
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class LockScreenActivity : FragmentActivity() {

    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsDataStore = SettingsDataStore(this)
        val mode = runBlocking { settingsDataStore.lockScreenMode.firstOrNull() } ?: LockScreenMode.DISABLED
        
        if (mode == LockScreenMode.DISABLED) {
            LockScreenState.isLocked = false
            finish()
            return
        }

        LockScreenState.isLocked = true

        // Make activity show over lockscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Hide navigation bar and status bar to make it truly full screen
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            ZuneLauncherTheme {
                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                    LockScreenContent(
                        mode = mode,
                        onUnlockRequested = {
                            if (mode == LockScreenMode.SAFE_MODE) {
                                LockScreenState.isLocked = false
                                dismissKeyguardAndFinish()
                            } else {
                                LockScreenState.isLocked = false
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun dismissKeyguardAndFinish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    LockScreenState.isLocked = false
                    finish()
                }
                
                override fun onDismissCancelled() {
                    super.onDismissCancelled()
                    // If user cancels system pin, we shouldn't necessarily close our lock screen.
                    // But for now, returning them to our lock screen state is handled by not finishing.
                }

                override fun onDismissError() {
                    super.onDismissError()
                }
            })
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            LockScreenState.isLocked = false
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Ensure state is cleared if activity is destroyed for any reason when unlocked
        if (!isFinishing) {
           // We're just recreating, don't change lock state
        } else if (LockScreenState.isLocked) {
           // Activity is finishing, but was not gracefully unlocked via request? 
           // We keep isLocked=true to ensure MainActivity resurrects it,
           // OR we reset it if we consider destruction as unlock.
           // Actually, we should only unlock when explicitly unlocked.
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing to prevent user from exiting the lock screen via back button
    }
}
