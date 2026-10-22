package com.serkantkn.zeta.data.hubs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.serkantkn.zeta.data.model.ExternalHub
import com.serkantkn.zeta.data.model.HubId
import com.serkantkn.zeta.hubkit.protocol.HubProtocol
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.security.MessageDigest

/** Another app's hub as it is installed now. */
data class InstalledHub(
    val hub: ExternalHub,
    /** The hub's name, lowercase: the service's label. */
    val label: String,
    /** The app it comes from. */
    val appLabel: String,
    /** The protocol it was written for. */
    val protocol: Int,
    /** The SHA-256 of the app's signing certificate. */
    val certificate: String?
) {
    /** Whether this Zeta can speak to it. */
    val speakable: Boolean get() = protocol in 1..HubProtocol.VERSION
}

/**
 * The hubs other apps offer Zeta (PLATFORM.md, "Bulma"): every exported service that answers
 * [HubProtocol.ACTION_HUB], kept up to date as apps come and go. Which of them the person has
 * added is kept in [com.serkantkn.zeta.data.datastore.SettingsDataStore.approvedHubs].
 */
object HubDirectory {
    private const val TAG = "HubDirectory"

    private val _installed = MutableStateFlow<List<InstalledHub>>(emptyList())
    val installed: StateFlow<List<InstalledHub>> = _installed.asStateFlow()

    @Volatile
    private var started = false

    /** Starts watching; safe to call again. */
    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh(context)
        }, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh(app)
    }

    /** Reads the installed hubs again. */
    fun refresh(context: Context) {
        val pm = context.packageManager
        val found = runCatching {
            pm.queryIntentServices(Intent(HubProtocol.ACTION_HUB), PackageManager.GET_META_DATA)
        }.getOrElse {
            ZuneLog.w(TAG, "could not look for hubs", it)
            emptyList()
        }
        _installed.value = found.mapNotNull { resolve ->
            val service = resolve.serviceInfo ?: return@mapNotNull null
            if (!service.exported || service.packageName == context.packageName) return@mapNotNull null
            val hub = ExternalHub(service.packageName, service.name)
            InstalledHub(
                hub = hub,
                label = service.loadLabel(pm).toString().lowercase(java.util.Locale.getDefault()),
                appLabel = service.applicationInfo.loadLabel(pm).toString(),
                protocol = service.metaData?.getInt(HubProtocol.META_PROTOCOL, 0) ?: 0,
                certificate = certificateOf(context, service.packageName)
            )
        }.sortedBy { it.label }
    }

    /** The hub as installed now, if it is. */
    fun find(hub: ExternalHub): InstalledHub? = _installed.value.firstOrNull { it.hub == hub }

    /**
     * The hubs the person has added that are still installed, still signed by whoever was
     * allowed, and spoken by this Zeta.
     */
    fun approved(approvals: Flow<Map<String, String>>): Flow<List<InstalledHub>> =
        combine(installed, approvals) { all, allowed ->
            all.filter { it.speakable && it.certificate != null && allowed[it.hub.key] == it.certificate }
        }

    /** The hubs installed but not added: what "add a hub" offers. */
    fun notAdded(approvals: Flow<Map<String, String>>): Flow<List<InstalledHub>> =
        combine(installed, approvals) { all, allowed ->
            all.filter { it.speakable && it.certificate != null && allowed[it.hub.key] != it.certificate }
        }

    /**
     * [order] with the added external hubs in it: one no longer added (or installed) is left out,
     * a newly added one goes at the end.
     */
    fun withAdded(order: List<HubId>, added: List<InstalledHub>): List<HubId> {
        val hubs = added.map { it.hub }
        return order.filter { it !is ExternalHub || it in hubs } + hubs.filter { it !in order }
    }

    /** The SHA-256 of [packageName]'s signing certificate, lowercase hex. */
    fun certificateOf(context: Context, packageName: String): String? = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return null
        val signer = if (signing.hasMultipleSigners()) signing.apkContentsSigners.first()
        else signing.signingCertificateHistory.last()
        MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
