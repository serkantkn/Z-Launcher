package com.serkantkn.zeta.util

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
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
enum class QuickAction(@StringRes val labelRes: Int) {
    WIFI(R.string.quick_wifi),
    BLUETOOTH(R.string.quick_bluetooth),
    FLASHLIGHT(R.string.quick_flashlight),
    ROTATION_LOCK(R.string.quick_rotation_lock),
    MOBILE_DATA(R.string.quick_mobile_data),
    AIRPLANE(R.string.quick_airplane),
    LOCATION(R.string.quick_location),
    QUIET_HOURS(R.string.quick_quiet_hours);

    companion object {
        /** The first row; the rest come out when the row is expanded, as on Windows 10 Mobile. */
        val FIRST_ROW = listOf(WIFI, BLUETOOTH, FLASHLIGHT, ROTATION_LOCK)
        val SECOND_ROW = listOf(MOBILE_DATA, AIRPLANE, LOCATION, QUIET_HOURS)
    }
}

/** What happened when a square was pressed. */
enum class QuickOutcome {
    /** Switched here; the panel stays open. */
    SWITCHED,

    /** Android's sheet or a settings page was opened; the panel should get out of the way. */
    OPENED_ELSEWHERE,

    /** Nothing could be done. */
    FAILED
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
    }

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
