package com.serkantkn.zunelauncher

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.ui.screens.LauncherScreen
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsViewModel
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme

class MainActivity : ComponentActivity() {
    private companion object {
        const val WALLPAPER_READ_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent back button from closing the launcher
        // Hub-level back is handled by BackHandler in LauncherScreen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consumed — launcher should never close via back button
            }
        })

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (requestWallpaperReadPermissionIfNeeded()) {
            return
        }

        setLauncherContent()
    }

    private fun setLauncherContent() {
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val accentColor by settingsViewModel.accentColor.collectAsState()
            val dynamicThemeColor by settingsViewModel.dynamicThemeColor.collectAsState()
            val fontScale by settingsViewModel.fontScale.collectAsState()

            ZuneLauncherTheme(
                themeMode = themeMode,
                accentColor = accentColor,
                dynamicThemeColor = dynamicThemeColor,
                fontScale = fontScale
            ) {
                LauncherScreen()
            }
        }
    }

    private fun requestWallpaperReadPermissionIfNeeded(): Boolean {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val isGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (isGranted) return false

        ActivityCompat.requestPermissions(
            this,
            permissions,
            WALLPAPER_READ_PERMISSION_REQUEST
        )
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == WALLPAPER_READ_PERMISSION_REQUEST) {
            setLauncherContent()
        }
    }
}
