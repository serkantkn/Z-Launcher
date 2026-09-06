package com.serkantkn.zunelauncher.data.service

import com.serkantkn.zunelauncher.util.ZuneLog
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.serkantkn.zunelauncher.data.model.VolumeBarStyle
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import com.serkantkn.zunelauncher.di.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hardware volume keys reach an app only while it is in the foreground. To show the Windows
 * Phone volume bar while WhatsApp or any other app is open, this accessibility service filters
 * the key events system-wide: it adjusts the volume itself (no system UI) and shows the WP bar
 * in an overlay window.
 *
 * It does nothing unless the user enabled it in Ayarlar → Erişilebilirlik, the volume bar style
 * is WINDOWS_PHONE, and the overlay permission is granted. While the launcher is in front the
 * keys are left alone so MainActivity keeps handling them exactly as before.
 */
class ZuneKeyAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var styleJob: Job? = null

    @Volatile
    private var volumeBarStyle: VolumeBarStyle = VolumeBarStyle.WINDOWS_PHONE

    override fun onServiceConnected() {
        super.onServiceConnected()
        ZuneLog.d(TAG, "connected; flags=${serviceInfo?.flags}")
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
        } catch (e: Exception) {
            ZuneLog.e("ZuneKeyAccessibilityService", "onServiceConnected failed", e)
        }
        styleJob?.cancel()
        styleJob = scope.launch {
            try {
                applicationContext.appContainer.settingsDataStore.volumeBarStyle.collect { volumeBarStyle = it }
            } catch (e: Exception) {
                ZuneLog.e("ZuneKeyAccessibilityService", "volumeBarStyle collect failed", e)
            }
        }
    }

    override fun onDestroy() {
        styleJob?.cancel()
        WpVolumeOverlay.hide()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used: this service exists only for key filtering.
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isUp = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return false
        }
        // The launcher's own activity handles keys while it is on screen.
        if (SocialRepository.isLauncherForeground) return false
        if (volumeBarStyle != VolumeBarStyle.WINDOWS_PHONE) return false
        if (!WpToastOverlay.isAvailable(this)) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            VolumeController.handleVolumeKey(this, isUp)
            WpVolumeOverlay.show(this)
        }
        // Consume both DOWN and UP so the system volume panel never appears.
        return true
    }

    companion object {
        private const val TAG = "ZuneKeyService"

        /** True when the user enabled this service in the system accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ZuneKeyAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { entry ->
                val name = ComponentName.unflattenFromString(entry) ?: return@any false
                name == expected
            }
        }
    }
}
