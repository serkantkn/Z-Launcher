package com.serkantkn.zeta.util

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * What the phone's connections are doing, for the "connections" page: Wi-Fi, Bluetooth, the
 * mobile network, airplane mode, location, NFC, VPN and data saver.
 *
 * Android lets no app switch these itself any more, so the page shows each as it is and opens
 * Android's own panel or page for it - a panel slides up over the launcher where Android has one.
 */
object Connections {

    data class State(
        val wifiOn: Boolean = false,
        val wifiConnected: Boolean = false,
        /** The network's name; null without precise location, or with location off. */
        val wifiName: String? = null,
        val bluetoothOn: Boolean = false,
        /** The devices connected now; null without the nearby devices permission. */
        val bluetoothDevices: List<String>? = null,
        val hasTelephony: Boolean = false,
        val simPresent: Boolean = false,
        val mobileDataOn: Boolean = false,
        val onMobileData: Boolean = false,
        val operator: String = "",
        val roaming: Boolean = false,
        val dataRoamingOn: Boolean = false,
        val airplane: Boolean = false,
        val locationOn: Boolean = false,
        val hasNfc: Boolean = false,
        val nfcOn: Boolean = false,
        val vpnActive: Boolean = false,
        val dataSaverOn: Boolean = false,
        val online: Boolean = false,
        val hasPrivateDnsPage: Boolean = false
    )

    fun read(context: Context): State {
        val app = context.applicationContext
        val pm = app.packageManager
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val telephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val tm = app.getSystemService(TelephonyManager::class.java)
        val nfc = runCatching { NfcAdapter.getDefaultAdapter(app) }.getOrNull()
        return State(
            wifiOn = runCatching { app.getSystemService(WifiManager::class.java)?.isWifiEnabled == true }.getOrDefault(false),
            wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            wifiName = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) wifiName(app) else null,
            bluetoothOn = runCatching { app.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false),
            hasTelephony = telephony,
            simPresent = telephony && runCatching {
                tm?.simState.let { it != null && it != TelephonyManager.SIM_STATE_ABSENT && it != TelephonyManager.SIM_STATE_UNKNOWN }
            }.getOrDefault(false),
            mobileDataOn = telephony && runCatching { tm?.isDataEnabled == true }.getOrDefault(false),
            onMobileData = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            operator = if (telephony) runCatching { tm?.networkOperatorName.orEmpty() }.getOrDefault("") else "",
            roaming = telephony && runCatching { tm?.isNetworkRoaming == true }.getOrDefault(false),
            dataRoamingOn = runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.DATA_ROAMING, 0) == 1 }.getOrDefault(false),
            airplane = runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false),
            locationOn = runCatching { app.getSystemService(LocationManager::class.java)?.isLocationEnabled == true }.getOrDefault(false),
            hasNfc = nfc != null,
            nfcOn = runCatching { nfc?.isEnabled == true }.getOrDefault(false),
            vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            dataSaverOn = runCatching {
                cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            }.getOrDefault(false),
            online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            // Some phones (Samsung's) keep private DNS inside their own connections page.
            hasPrivateDnsPage = runCatching { Intent(PRIVATE_DNS_SETTINGS).resolveActivity(pm) != null }.getOrDefault(false)
        )
    }

    /** Whether Android will tell the launcher the Wi-Fi network's name. */
    fun mayReadWifiName(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** Whether Android will tell the launcher which Bluetooth devices are connected. */
    fun mayReadBluetoothDevices(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** The network's name without its quotes; null when Android keeps it back. */
    @Suppress("DEPRECATION")
    private fun wifiName(context: Context): String? {
        if (!mayReadWifiName(context)) return null
        val info = runCatching { context.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull()
        return cleanSsid(info?.ssid)
    }

    /** "\"Home\"" is Home; Android's placeholder for a name it keeps back is no name. */
    fun cleanSsid(raw: String?): String? {
        val name = raw?.trim()?.removeSurrounding("\"") ?: return null
        return name.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" && it != "0x" }
    }

    /** The name in a Wi-Fi network's details, which carry it from Android 10 on when asked for. */
    fun ssidOf(caps: NetworkCapabilities): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cleanSsid((caps.transportInfo as? WifiInfo)?.ssid) else null

    /**
     * The Bluetooth devices connected now - headphones, a car, a watch - by name, read from each
     * kind of connection Android keeps. Null without the nearby devices permission or with
     * Bluetooth off.
     */
    suspend fun bluetoothDevices(context: Context): List<String>? {
        if (!mayReadBluetoothDevices(context)) return null
        val app = context.applicationContext
        val manager = app.getSystemService(BluetoothManager::class.java) ?: return null
        val adapter = manager.adapter?.takeIf { runCatching { it.isEnabled }.getOrDefault(false) } ?: return null
        val devices = mutableMapOf<String, BluetoothDevice>()
        runCatching { manager.getConnectedDevices(BluetoothProfile.GATT) }.getOrNull()?.forEach { devices[it.address] = it }
        for (profile in AUDIO_PROFILES) {
            val connected = withTimeoutOrNull(PROFILE_WAIT_MS) {
                suspendCancellableCoroutine<List<BluetoothDevice>> { done ->
                    val asked = runCatching {
                        adapter.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
                            override fun onServiceConnected(kind: Int, proxy: BluetoothProfile) {
                                val list = runCatching { proxy.connectedDevices }.getOrDefault(emptyList())
                                runCatching { adapter.closeProfileProxy(kind, proxy) }
                                if (done.isActive) done.resume(list)
                            }
                            override fun onServiceDisconnected(kind: Int) {
                                if (done.isActive) done.resume(emptyList())
                            }
                        }, profile)
                    }.getOrDefault(false)
                    if (!asked && done.isActive) done.resume(emptyList())
                }
            }
            connected?.forEach { devices[it.address] = it }
        }
        return devices.values
            .mapNotNull { device -> runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias ?: device.name else device.name }.getOrNull()?.takeIf { it.isNotBlank() } }
            .distinct()
            .sorted()
    }

    /** Headsets and speakers, hands-free (a car), hearing aids and LE audio. */
    private val AUDIO_PROFILES = buildList {
        add(BluetoothProfile.A2DP)
        add(BluetoothProfile.HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(BluetoothProfile.HEARING_AID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(BluetoothProfile.LE_AUDIO)
    }

    private const val PROFILE_WAIT_MS = 1_500L

    /** The places the page opens, each with somewhere to fall back to. */
    enum class Page { WIFI, BLUETOOTH, MOBILE, ROAMING, DATA_USAGE, HOTSPOT, AIRPLANE, LOCATION, NFC, VPN, CAST, PRIVATE_DNS, MORE }

    fun open(context: Context, page: Page) {
        val tries = when (page) {
            Page.WIFI -> panel(Settings.Panel.ACTION_WIFI) + Intent(Settings.ACTION_WIFI_SETTINGS)
            Page.BLUETOOTH -> listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            Page.MOBILE -> panel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) +
                Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS) + Intent(Settings.ACTION_WIRELESS_SETTINGS)
            Page.ROAMING -> listOf(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            Page.DATA_USAGE -> listOf(Intent(Settings.ACTION_DATA_USAGE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            // Hotspot and tethering have no public page; Android's own and Samsung's are tried by
            // name, then the general connections page.
            Page.HOTSPOT -> listOf(
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")),
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
            Page.AIRPLANE -> listOf(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            Page.LOCATION -> listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            Page.NFC -> panel(Settings.Panel.ACTION_NFC) + Intent(Settings.ACTION_NFC_SETTINGS) + Intent(Settings.ACTION_WIRELESS_SETTINGS)
            Page.VPN -> listOf(Intent(Settings.ACTION_VPN_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            Page.CAST -> listOf(Intent(Settings.ACTION_CAST_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            Page.PRIVATE_DNS -> listOf(Intent(PRIVATE_DNS_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            Page.MORE -> listOf(Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in tries) {
            val opened = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (opened) return
        }
        ZuneLog.w(TAG, "nothing would open for $page")
    }

    /** Android's panels, which slide up over the launcher, came with Android 10. */
    private fun panel(action: String): List<Intent> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(Intent(action)) else emptyList()

    /** Android's private DNS page; not public, but present since Android 9. */
    private const val PRIVATE_DNS_SETTINGS = "android.settings.PRIVATE_DNS_SETTINGS"

    private const val TAG = "Connections"
}
