package com.serkantkn.zunelauncher.ui.screens.settings

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.findActivity
import com.serkantkn.zunelauncher.util.Connections
import com.serkantkn.zunelauncher.util.Connections.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "connections": Wi-Fi, Bluetooth, the mobile network, airplane mode and the rest, each as it is
 * right now, as Windows Phone's settings listed them. Android lets no app switch them itself, so
 * a tap opens Android's own switch - as a panel over the launcher where Android has one.
 *
 * The page reads them again whenever one changes, so it is right when the user comes back.
 */
@Composable
internal fun ConnectivitySettingsPage() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(Connections.State()) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    var bluetoothDevices by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(tick) {
        state = withContext(Dispatchers.IO) { Connections.read(context) }
        bluetoothDevices = withContext(Dispatchers.IO) { Connections.bluetoothDevices(context) }
    }
    ConnectionChanges { tick++ }
    val mayReadWifiName = remember(tick) { Connections.mayReadWifiName(context) }
    val mayReadBluetooth = remember(tick) { Connections.mayReadBluetoothDevices(context) }
    // From Android 12 the name comes only with the network's details, asked for by name.
    val liveWifiName = rememberWifiName(enabled = mayReadWifiName)
    val wifiName = liveWifiName ?: state.wifiName

    val askWifiName = rememberPermissionAsk(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ) { tick++ }
    val askBluetooth = rememberPermissionAsk(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    ) { tick++ }

    val on = stringResource(R.string.settings_conn_on)
    val off = stringResource(R.string.settings_conn_off)
    fun onOff(value: Boolean) = if (value) on else off
    val open = { page: Page -> Connections.open(context, page) }

    SettingsLazyColumn {
        // The phone link: on a TV, the phones that tell it of calls and messages; on a phone,
        // the TVs it tells (data/link).
        item(key = "link") {
            if (com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current) TvLinkGroup() else PhoneLinkGroup()
        }
        item(key = "wireless") {
            SettingGroup(title = stringResource(R.string.settings_wireless)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        title = stringResource(R.string.settings_airplane),
                        subtitle = if (state.airplane) stringResource(R.string.settings_airplane_sub_on) else off,
                        active = state.airplane,
                        onClick = { open(Page.AIRPLANE) }
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_wifi),
                        subtitle = when {
                            !state.wifiOn -> off
                            state.wifiConnected && wifiName != null -> stringResource(R.string.settings_wifi_with_name, wifiName)
                            state.wifiConnected && mayReadWifiName && !state.locationOn ->
                                stringResource(R.string.settings_wifi_name_needs_location)
                            state.wifiConnected -> stringResource(R.string.settings_wifi_connected)
                            else -> stringResource(R.string.settings_wifi_not_connected)
                        },
                        active = state.wifiOn,
                        onClick = { open(Page.WIFI) }
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_bluetooth),
                        subtitle = bluetoothText(state.bluetoothOn, bluetoothDevices, off),
                        active = state.bluetoothOn,
                        onClick = { open(Page.BLUETOOTH) }
                    )
                }
            }
        }

        if (state.hasTelephony) {
            item(key = "cellular") {
                SettingGroup(title = stringResource(R.string.settings_cellular)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val inUse = stringResource(R.string.settings_mobile_data_in_use)
                        val noSim = stringResource(R.string.settings_no_sim)
                        SettingRow(
                            title = stringResource(R.string.settings_mobile_data),
                            subtitle = if (!state.simPresent) noSim else listOfNotNull(
                                onOff(state.mobileDataOn),
                                state.operator.takeIf { it.isNotBlank() },
                                inUse.takeIf { state.onMobileData }
                            ).joinToString(" · "),
                            active = state.simPresent && state.mobileDataOn,
                            onClick = { open(Page.MOBILE) }
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_data_roaming),
                            subtitle = if (state.roaming) {
                                stringResource(R.string.settings_conn_with, onOff(state.dataRoamingOn), stringResource(R.string.settings_roaming_now))
                            } else onOff(state.dataRoamingOn),
                            active = state.dataRoamingOn,
                            onClick = { open(Page.ROAMING) }
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_data_usage),
                            subtitle = stringResource(if (state.dataSaverOn) R.string.settings_data_saver_on else R.string.settings_data_saver_off),
                            active = state.dataSaverOn,
                            onClick = { open(Page.DATA_USAGE) }
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_hotspot),
                            subtitle = stringResource(R.string.settings_hotspot_sub),
                            onClick = { open(Page.HOTSPOT) }
                        )
                    }
                }
            }
        }

        item(key = "other") {
            SettingGroup(title = stringResource(R.string.settings_other_connections)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        title = stringResource(R.string.settings_location),
                        subtitle = onOff(state.locationOn),
                        active = state.locationOn,
                        onClick = { open(Page.LOCATION) }
                    )
                    if (state.hasNfc) {
                        SettingRow(
                            title = stringResource(R.string.settings_nfc),
                            subtitle = stringResource(R.string.settings_conn_with, onOff(state.nfcOn), stringResource(R.string.settings_nfc_sub)),
                            active = state.nfcOn,
                            onClick = { open(Page.NFC) }
                        )
                    }
                    SettingRow(
                        title = stringResource(R.string.settings_vpn),
                        subtitle = stringResource(if (state.vpnActive) R.string.settings_vpn_connected else R.string.settings_vpn_not_connected),
                        active = state.vpnActive,
                        onClick = { open(Page.VPN) }
                    )
                    if (state.hasPrivateDnsPage) {
                        SettingRow(
                            title = stringResource(R.string.settings_private_dns),
                            subtitle = stringResource(R.string.settings_private_dns_sub),
                            onClick = { open(Page.PRIVATE_DNS) }
                        )
                    }
                    SettingRow(
                        title = stringResource(R.string.settings_cast),
                        subtitle = stringResource(R.string.settings_cast_sub),
                        onClick = { open(Page.CAST) }
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_more_connections),
                        subtitle = stringResource(R.string.settings_more_connections_sub),
                        onClick = { open(Page.MORE) }
                    )
                    Text(
                        text = stringResource(R.string.settings_connections_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textDim,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        }

        item(key = "permissions") {
            SettingGroup(title = stringResource(R.string.settings_conn_permissions)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PermissionRow(
                        title = stringResource(R.string.settings_perm_wifi_name),
                        what = stringResource(R.string.settings_perm_wifi_name_sub),
                        why = stringResource(R.string.settings_perm_wifi_name_why),
                        granted = mayReadWifiName,
                        onAsk = askWifiName
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PermissionRow(
                            title = stringResource(R.string.settings_perm_bt_devices),
                            what = stringResource(R.string.settings_perm_bt_devices_sub),
                            why = stringResource(R.string.settings_perm_bt_devices_why),
                            granted = mayReadBluetooth,
                            onAsk = askBluetooth
                        )
                    }
                }
            }
        }
    }
}

/** "on · Galaxy Buds2, Car +1", "on · nothing connected", or just on/off without the permission. */
@Composable
private fun bluetoothText(on: Boolean, devices: List<String>?, off: String): String = when {
    !on -> off
    devices == null -> stringResource(R.string.settings_conn_on)
    devices.isEmpty() -> stringResource(R.string.settings_bt_none)
    devices.size <= 2 -> stringResource(R.string.settings_bt_with, devices.joinToString(", "))
    else -> stringResource(
        R.string.settings_bt_with,
        stringResource(R.string.settings_bt_and_more, devices.take(2).joinToString(", "), devices.size - 2)
    )
}

/**
 * A permission with what it is and, under it, exactly what the launcher does with it. Switched
 * on, it asks Android for it; once given, a tap opens the launcher's page in Android's settings,
 * the only place it can be taken back.
 */
@Composable
internal fun PermissionRow(title: String, what: String, why: String, granted: Boolean, onAsk: () -> Unit) {
    val context = LocalContext.current
    Column {
        SettingSwitchRow(
            title = title,
            subtitle = if (granted) stringResource(R.string.settings_perm_granted_tap) else what,
            checked = granted,
            onCheckedChange = { if (granted) openAppDetails(context) else onAsk() }
        )
        Text(
            text = why,
            style = MaterialTheme.typography.bodySmall,
            color = LocalZuneColors.current.textDim,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
        )
    }
}

/**
 * Asks Android for [permissions], then calls [onDone]. Once Android has stopped asking - the user
 * said no and not to ask again - Android shows nothing, so the launcher's page in Android's
 * settings opens instead, where it can still be given.
 */
@Composable
internal fun rememberPermissionAsk(permissions: Array<String>, onDone: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { done() }
    return remember(launcher, permissions.joinToString()) {
        ask@{
            if (permissions.isEmpty()) return@ask
            val prefs = context.getSharedPreferences(PERMISSION_ASKS, Context.MODE_PRIVATE)
            val key = permissions.first()
            val activity = context.findActivity()
            val mayExplain = activity != null &&
                permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            if (prefs.getBoolean(key, false) && !mayExplain) {
                openAppDetails(context)
            } else {
                prefs.edit().putBoolean(key, true).apply()
                runCatching { launcher.launch(permissions) }.onFailure { openAppDetails(context) }
            }
        }
    }
}

internal fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Which permissions the launcher has asked for before, to tell "never asked" from "don't ask". */
private const val PERMISSION_ASKS = "permission_asks"

/**
 * The name of the Wi-Fi network the phone is on, from Android 12 on, where it comes only with
 * the network's details and only when asked for with the location in them.
 */
@Composable
private fun rememberWifiName(enabled: Boolean): String? {
    val context = LocalContext.current
    var name by remember { mutableStateOf<String?>(null) }
    DisposableEffect(context, enabled) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            name = null
            return@DisposableEffect onDispose { }
        }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val main = ContextCompat.getMainExecutor(context)
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ssid = Connections.ssidOf(caps)
                main.execute { name = ssid }
            }
            override fun onLost(network: Network) = main.execute { name = null }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val watching = runCatching { cm?.registerNetworkCallback(request, callback) }.isSuccess
        onDispose { if (watching) runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return name
}

@Composable
private fun SettingRow(title: String, subtitle: String, active: Boolean = false, onClick: () -> Unit) =
    SystemSettingRow(title = title, subtitle = subtitle, isActive = active, onClick = onClick)

/** Calls [onChange] whenever a connection changes, while the page is on screen. */
@Composable
private fun ConnectionChanges(onChange: () -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onChange)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = latest()
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Joining or leaving a network, or a VPN coming up, comes through the default network.
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = post()
            override fun onLost(network: Network) = post()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = post()
            private fun post() = ContextCompat.getMainExecutor(context).execute { latest() }
        }
        val watching = runCatching { cm?.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            if (watching) runCatching { cm?.unregisterNetworkCallback(callback) }
        }
    }
}
