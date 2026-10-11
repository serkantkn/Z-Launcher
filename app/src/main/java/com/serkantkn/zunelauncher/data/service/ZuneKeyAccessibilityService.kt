package com.serkantkn.zunelauncher.data.service

import com.serkantkn.zunelauncher.BuildConfig
import android.os.Build
import com.serkantkn.zunelauncher.util.ZuneLog
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.cancel
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
    private var shieldJob: Job? = null
    @Volatile
    private var actionCenterOn = false
    private var lastShadeCaught = 0L
    private var screenReceiver: BroadcastReceiver? = null

    @Volatile
    private var volumeBarStyle: VolumeBarStyle = VolumeBarStyle.WINDOWS_PHONE

    override fun onServiceConnected() {
        super.onServiceConnected()
        ZuneLog.d(TAG, "connected; flags=${serviceInfo?.flags}")
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (e: Exception) {
            ZuneLog.e("ZuneKeyAccessibilityService", "onServiceConnected failed", e)
        }
        current = this
        watchScreen()
        shieldJob?.cancel()
        shieldJob = scope.launch {
            try {
                applicationContext.appContainer.settingsDataStore.wpActionCenter.collect { on ->
                    actionCenterOn = on
                    if (on) StatusBarShield.attach(this@ZuneKeyAccessibilityService) else StatusBarShield.detach()
                }
            } catch (e: Exception) {
                ZuneLog.e(TAG, "action center setting could not be read", e)
            }
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

    /**
     * The strip steps aside while a full-screen app has the status bar hidden, and while the
     * phone is locked: the action center would lay every notification and quick setting open to
     * whoever picks the phone up, where Android's own shade on the lock screen keeps to the
     * user's lock-screen privacy settings.
     */
    private fun followStatusBar() {
        if (!StatusBarShield.isAttached) return
        if (isLocked()) {
            StatusBarShield.setTouchable(false)
            return
        }
        val list = runCatching { windows }.getOrNull() ?: return
        val width = resources.displayMetrics.widthPixels
        StatusBarShield.setTouchable(StatusBarShield.isStatusBarShown(list, width))
    }

    private fun isLocked(): Boolean =
        (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true

    /** The lock screen comes and goes with the screen; the window list alone does not always say. */
    private fun watchScreen() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    ActionCenterOverlay.close()
                    QuickReplyOverlay.hide()
                }
                if (actionCenterOn) followStatusBar()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            screenReceiver = receiver
        }.onFailure { ZuneLog.e(TAG, "screen receiver could not be registered", it) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // A turn of the screen changes the bar's height, and the cutout may move with it.
        StatusBarShield.remeasure(this)
    }

    /**
     * The safety net. The strip stops a pull on the status bar, but the system shade has other
     * doors — the lock screen, a quick-settings shortcut, an app that opens it. When SystemUI
     * puts the shade on screen anyway, it is closed again at once and the action center comes
     * down in its place.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!actionCenterOn) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            followStatusBar()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != SYSTEM_UI) return
        if (BuildConfig.DEBUG) {
            ZuneLog.d(TAG, "systemui window: class=${event.className} text=${event.text} desc=${event.contentDescription}")
        }
        if (!ShadeDetector.isShade(event.className, event.text, event.contentDescription)) return
        // On the lock screen the shade is Android's, for the same reason the strip steps aside.
        if (isLocked()) return
        ZuneLog.d(TAG, "system shade caught; closing it")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        // SystemUI reports the shade again as it folds away; only the first report of a burst
        // brings the action center down, so one the user has just put away stays away.
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastShadeCaught > SHADE_BURST_MILLIS) ActionCenterOverlay.open(this)
        lastShadeCaught = now
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Back puts the action center away, whatever app is underneath it.
        if (event.keyCode == KeyEvent.KEYCODE_BACK && ActionCenterOverlay.isOpen) {
            if (event.action == KeyEvent.ACTION_UP) ActionCenterOverlay.close()
            return true
        }
        val isUp = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return false
        }
        // The launcher's own activity handles keys while it is on screen.
        if (SocialRepository.isLauncherForeground) return false
        if (volumeBarStyle != VolumeBarStyle.WINDOWS_PHONE) return false
        if (!WpToastOverlay.isAvailable(this)) return false

        // A ringing phone is the system's to quieten: the controller declines the key and so do we.
        if (CallManager.callStatus.value == CallStatus.INCOMING) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (!VolumeController.handleVolumeKey(this, isUp)) return false
            WpVolumeOverlay.show(this)
        }
        // Consume both DOWN and UP so the system volume panel never appears.
        return true
    }

    override fun onDestroy() {
        if (current === this) current = null
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        shieldJob?.cancel()
        StatusBarShield.detach()
        styleJob?.cancel()
        // The scope is the service's; a collector left running would outlive it.
        scope.cancel()
        WpVolumeOverlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ZuneKeyService"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val SHADE_BURST_MILLIS = 1_500L

        /**
         * The running service, if any. Overlays that must sit above the status bar can only be
         * added through it.
         */
        @Volatile
        var current: ZuneKeyAccessibilityService? = null
            private set

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
