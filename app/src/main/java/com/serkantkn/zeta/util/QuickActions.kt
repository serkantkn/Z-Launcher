package com.serkantkn.zeta.util

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.annotation.StringRes
import com.serkantkn.zeta.R

/**
 * The squares across the top of the action center.
 *
 * Since Android 10 an app may no longer switch Wi‑Fi, mobile data, Bluetooth or airplane mode
 * itself. Those squares show the state and open Android's own small settings sheet (or the
 * settings page where there is no sheet); the rest — the torch, the rotation lock, quiet hours —
 * are switched here directly, given the permission each needs.
 */
enum class QuickAction(
    @StringRes val labelRes: Int,
    /** Pressing it opens Android's own sheet or settings page rather than switching it here. */
    val opensSettings: Boolean = false,
    /** It is on or off; one that only does something (a screenshot) has no state. */
    val hasState: Boolean = true
) {
    WIFI(R.string.quick_wifi, opensSettings = true),
    BLUETOOTH(R.string.quick_bluetooth, opensSettings = true),
    FLASHLIGHT(R.string.quick_flashlight),
    ROTATION_LOCK(R.string.quick_rotation_lock),
    MOBILE_DATA(R.string.quick_mobile_data, opensSettings = true),
    AIRPLANE(R.string.quick_airplane, opensSettings = true),
    LOCATION(R.string.quick_location, opensSettings = true),
    QUIET_HOURS(R.string.quick_quiet_hours),
    RINGER(R.string.quick_ringer),
    VOLUME(R.string.quick_volume, opensSettings = true, hasState = false),
    BATTERY_SAVER(R.string.quick_battery_saver, opensSettings = true),
    NFC(R.string.quick_nfc, opensSettings = true),
    CAST(R.string.quick_cast, opensSettings = true, hasState = false),
    SCREENSHOT(R.string.quick_screenshot, hasState = false),
    LOCK_SCREEN(R.string.quick_lock_screen, hasState = false);

    companion object {
        /** As the panel comes the first time: the first row, and the rest when it is expanded. */
        val DEFAULT = listOf(
            WIFI, BLUETOOTH, FLASHLIGHT, ROTATION_LOCK,
            MOBILE_DATA, AIRPLANE, LOCATION, QUIET_HOURS
        )

        /** How many squares make a row. */
        const val ROW = 4
    }
}

/** What happened when a square was pressed. */
enum class QuickOutcome {
    /** Switched here; the panel stays open. */
    SWITCHED,

    /** Android's sheet or a settings page was opened; the panel should get out of the way. */
    OPENED_ELSEWHERE,

    /** Nothing could be done. */
    FAILED,

    /** It is done once the panel has gone (a screenshot of what is under it); the panel goes away. */
    AFTER_CLOSING
}

object QuickActions {

    private const val TAG = "QuickActions"

    fun isOn(context: Context, action: QuickAction): Boolean = runCatching {
        when (action) {
            QuickAction.WIFI ->
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
            QuickAction.BLUETOOTH ->
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
            QuickAction.FLASHLIGHT -> Torch.isOn
            QuickAction.ROTATION_LOCK ->
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
            QuickAction.MOBILE_DATA ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).isDataEnabled
            QuickAction.AIRPLANE ->
                Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            QuickAction.LOCATION ->
                (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
            QuickAction.QUIET_HOURS ->
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            // Lit while the phone does not ring: on vibrate or silent.
            QuickAction.RINGER ->
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode != AudioManager.RINGER_MODE_NORMAL
            QuickAction.BATTERY_SAVER -> (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
            QuickAction.NFC -> NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
            QuickAction.VOLUME, QuickAction.CAST, QuickAction.SCREENSHOT, QuickAction.LOCK_SCREEN -> false
        }
    }.getOrDefault(false)

    fun press(context: Context, action: QuickAction): QuickOutcome = when (action) {
        QuickAction.WIFI -> openSheet(context, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        QuickAction.MOBILE_DATA ->
            openSheet(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_DATA_ROAMING_SETTINGS)
        QuickAction.BLUETOOTH -> open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        QuickAction.AIRPLANE -> open(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        QuickAction.LOCATION -> open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        QuickAction.FLASHLIGHT -> if (Torch.toggle(context)) QuickOutcome.SWITCHED else QuickOutcome.FAILED
        QuickAction.ROTATION_LOCK -> {
            if (!Settings.System.canWrite(context)) {
                askToWriteSettings(context)
            } else {
                val locked = isOn(context, QuickAction.ROTATION_LOCK)
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 1 else 0)
                QuickOutcome.SWITCHED
            }
        }
        QuickAction.QUIET_HOURS -> {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } else {
                val quiet = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                nm.setInterruptionFilter(
                    if (quiet) NotificationManager.INTERRUPTION_FILTER_ALL
                    else NotificationManager.INTERRUPTION_FILTER_PRIORITY
                )
                QuickOutcome.SWITCHED
            }
        }
        QuickAction.RINGER -> {
            com.serkantkn.zeta.data.service.VolumeController.toggleRingerMode(context)
            QuickOutcome.SWITCHED
        }
        QuickAction.VOLUME -> openSheet(context, Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)
        QuickAction.BATTERY_SAVER -> open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        QuickAction.NFC -> openSheet(context, Settings.Panel.ACTION_NFC, Settings.ACTION_NFC_SETTINGS)
        QuickAction.CAST -> open(context, Intent(Settings.ACTION_CAST_SETTINGS))
        // Both need the launcher's accessibility service, which may take a screenshot and lock the
        // screen; the screenshot waits for the panel to be out of it.
        QuickAction.SCREENSHOT -> globalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, SCREENSHOT_DELAY_MILLIS)
        QuickAction.LOCK_SCREEN -> globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, LOCK_DELAY_MILLIS)
    }

    /** Which squares this phone can offer at all: no NFC square on a phone without NFC. */
    fun available(context: Context, action: QuickAction): Boolean = when (action) {
        QuickAction.NFC -> NfcAdapter.getDefaultAdapter(context) != null
        QuickAction.FLASHLIGHT -> context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        QuickAction.MOBILE_DATA -> context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        else -> true
    }

    private fun globalAction(action: Int, delayMillis: Long): QuickOutcome {
        val service = com.serkantkn.zeta.data.service.ZuneKeyAccessibilityService.current ?: return QuickOutcome.FAILED
        Handler(Looper.getMainLooper()).postDelayed({ service.performGlobalAction(action) }, delayMillis)
        return QuickOutcome.AFTER_CLOSING
    }

    /** Long enough for the panel to have gone up out of the picture. */
    private const val SCREENSHOT_DELAY_MILLIS = 650L
    private const val LOCK_DELAY_MILLIS = 250L

    // ── Brightness ────────────────────────────────────────────────────────────────

    /** 0..1, from the system setting. */
    fun brightness(context: Context): Float =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    fun isAutoBrightness(context: Context): Boolean =
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    /** Whether the launcher may change brightness and rotation itself. */
    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    /** Sets the brightness; dragging the slider turns automatic brightness off, as it does in Android. */
    fun setBrightness(context: Context, value: Float) {
        if (!Settings.System.canWrite(context)) return
        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (value.coerceIn(0.01f, 1f) * 255).toInt())
    }

    fun setAutoBrightness(context: Context, on: Boolean) {
        if (!Settings.System.canWrite(context)) return
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
    }

    fun askToWriteSettings(context: Context): QuickOutcome = open(
        context,
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    // ── Opening things ────────────────────────────────────────────────────────────

    private fun openSheet(context: Context, sheet: String, fallback: String): QuickOutcome =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            open(context, Intent(sheet)).takeIf { it != QuickOutcome.FAILED } ?: open(context, Intent(fallback))
        } else {
            open(context, Intent(fallback))
        }

    private fun open(context: Context, intent: Intent): QuickOutcome = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        QuickOutcome.OPENED_ELSEWHERE
    }.getOrElse {
        ZuneLog.w(TAG, "could not open ${intent.action}", it)
        QuickOutcome.FAILED
    }
}

/**
 * The camera's torch. Its state is learnt from the system's torch callback, so a torch switched
 * on from somewhere else still shows as on here.
 */
object Torch {
    @Volatile
    var isOn: Boolean = false
        private set

    private var registered = false
    private var cameraId: String? = null

    fun watch(context: Context) {
        if (registered) return
        val cm = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        runCatching {
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == idOf(cm)) isOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
            registered = true
        }
    }

    fun toggle(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val id = idOf(cm) ?: return false
        return runCatching {
            cm.setTorchMode(id, !isOn)
            isOn = !isOn
            true
        }.getOrDefault(false)
    }

    private fun idOf(cm: CameraManager): String? = cameraId ?: runCatching {
        cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull().also { cameraId = it }
}
