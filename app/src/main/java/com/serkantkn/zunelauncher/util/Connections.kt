package com.serkantkn.zunelauncher.util

import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager

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
        val bluetoothOn: Boolean = false,
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
