package com.serkantkn.zeta.data.link

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A device on the other end of a pairing: a phone, as the TV keeps it, or a TV, as the phone does. */
internal data class LinkPeer(
    val id: String,
    val name: String,
    /** Wrapped by the Keystore; see [LinkCrypto.wrap]. */
    val wrappedKey: String,
    /** Where a TV was last found; empty for a phone. */
    val host: String = "",
    val port: Int = 0,
    /** The newest notice taken from this phone, so an old one sent again is not shown. */
    val lastAt: Long = 0
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("key", wrappedKey)
        .put("host", host).put("port", port).put("lastAt", lastAt)

    companion object {
        fun fromJson(json: JSONObject) = LinkPeer(
            json.getString("id"), json.optString("name"), json.getString("key"),
            json.optString("host"), json.optInt("port"), json.optLong("lastAt")
        )
    }
}

/**
 * What the phone link keeps: this device's own id, the devices it is paired with, and the
 * switches. Small and read often, from receivers as much as from screens, so it is plain
 * SharedPreferences rather than the settings' DataStore.
 */
internal object LinkStore {
    private var prefs: SharedPreferences? = null

    private val _peers = MutableStateFlow<List<LinkPeer>>(emptyList())
    val peers: StateFlow<List<LinkPeer>> = _peers.asStateFlow()

    /** On a TV: show the notices that come in. */
    private val _showNotices = MutableStateFlow(true)
    val showNotices: StateFlow<Boolean> = _showNotices.asStateFlow()

    /** On a phone: what is sent at all, and what of it. */
    private val _send = MutableStateFlow(SendSettings())
    val send: StateFlow<SendSettings> = _send.asStateFlow()

    data class SendSettings(
        val enabled: Boolean = true,
        val calls: Boolean = true,
        val sms: Boolean = true,
        val apps: Boolean = true,
        /** The message itself: off unless the user turns it on - the TV is in a shared room. */
        val text: Boolean = false
    )

    @Synchronized
    fun attach(context: Context): LinkStore {
        if (prefs != null) return this
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _peers.value = readPeers(p)
        _showNotices.value = p.getBoolean(KEY_SHOW, true)
        _send.value = SendSettings(
            enabled = p.getBoolean(KEY_SEND, true),
            calls = p.getBoolean(KEY_CALLS, true),
            sms = p.getBoolean(KEY_SMS, true),
            apps = p.getBoolean(KEY_APPS, true),
            text = p.getBoolean(KEY_TEXT, false)
        )
        return this
    }

    /** This device as the other side knows it: an id made once, and its name. */
    fun identity(context: Context): LinkIdentity {
        val p = attach(context).prefs!!
        val id = p.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also { p.edit().putString(KEY_ID, it).apply() }
        return LinkIdentity(id, deviceName(context))
    }

    fun deviceName(context: Context): String =
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    fun peer(id: String): LinkPeer? = _peers.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(peer: LinkPeer) {
        _peers.value = _peers.value.filter { it.id != peer.id } + peer
        writePeers()
    }

    @Synchronized
    fun remove(id: String) {
        _peers.value = _peers.value.filter { it.id != id }
        writePeers()
    }

    fun setShowNotices(on: Boolean) {
        _showNotices.value = on
        prefs?.edit()?.putBoolean(KEY_SHOW, on)?.apply()
    }

    fun setSend(settings: SendSettings) {
        _send.value = settings
        prefs?.edit()
            ?.putBoolean(KEY_SEND, settings.enabled)
            ?.putBoolean(KEY_CALLS, settings.calls)
            ?.putBoolean(KEY_SMS, settings.sms)
            ?.putBoolean(KEY_APPS, settings.apps)
            ?.putBoolean(KEY_TEXT, settings.text)
            ?.apply()
    }

    private fun readPeers(p: SharedPreferences): List<LinkPeer> = runCatching {
        val array = JSONArray(p.getString(KEY_PEERS, "[]"))
        (0 until array.length()).map { LinkPeer.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun writePeers() {
        val array = JSONArray().apply { _peers.value.forEach { put(it.toJson()) } }
        prefs?.edit()?.putString(KEY_PEERS, array.toString())?.apply()
    }

    private const val PREFS = "phone_link"
    private const val KEY_ID = "own_id"
    private const val KEY_PEERS = "peers"
    private const val KEY_SHOW = "show_notices"
    private const val KEY_SEND = "send"
    private const val KEY_CALLS = "send_calls"
    private const val KEY_SMS = "send_sms"
    private const val KEY_APPS = "send_apps"
    private const val KEY_TEXT = "send_text"
}
