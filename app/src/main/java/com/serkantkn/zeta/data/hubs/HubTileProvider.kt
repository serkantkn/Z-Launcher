package com.serkantkn.zeta.data.hubs

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.SystemClock
import com.serkantkn.zeta.data.model.ExternalHub
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.hubkit.protocol.HubProtocol
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Where a hub app writes its live tile while the hub is closed (ZetaHubKit's `ZetaTiles`):
 * `call("tile", <service class>, {count, line, title})`. Only for a hub the person has added, only
 * by the app that owns it, and at most [MAX_PER_MINUTE] times a minute.
 */
class HubTileProvider : ContentProvider() {

    private val recent = HashMap<String, ArrayDeque<Long>>()

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != HubProtocol.Tile.METHOD || arg.isNullOrBlank() || extras == null) return null
        val context = context ?: return null
        val uid = Binder.getCallingUid()
        val packages = context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        val hub = packages.asSequence().map { ExternalHub(it, arg) }.firstOrNull { candidate ->
            HubDirectory.find(candidate) != null
        } ?: return refuse("not a hub of the caller's: $arg")
        val identity = Binder.clearCallingIdentity()
        try {
            val store = context.appContainer.settingsDataStore
            val allowed = runBlocking { store.approvedHubs.first() }[hub.key]
            if (allowed == null || allowed != HubDirectory.find(hub)?.certificate) return refuse("${hub.key} is not added")
            if (!withinRate(hub.key)) return refuse("${hub.key} writes its tile too often")
            HubTileStore.start(context)
            HubTileStore.put(hub.key, HubTile(
                count = extras.getInt(HubProtocol.Tile.COUNT).coerceIn(0, HubTileStore.MAX_COUNT),
                line = extras.getString(HubProtocol.Tile.LINE)?.take(HubTileStore.MAX_TEXT),
                title = extras.getString(HubProtocol.Tile.TITLE)?.take(HubTileStore.MAX_TEXT)
            ))
            ZuneLog.d(TAG, "tile of ${hub.key} written while closed")
            return Bundle().apply { putBoolean(HubProtocol.Tile.OK, true) }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @Synchronized
    private fun withinRate(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val times = recent.getOrPut(key) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.first() > 60_000) times.removeFirst()
        if (times.size >= MAX_PER_MINUTE) return false
        times.addLast(now)
        return true
    }

    private fun refuse(why: String): Bundle {
        ZuneLog.w(TAG, why)
        return Bundle().apply { putBoolean(HubProtocol.Tile.OK, false) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "HubTileProvider"
        const val MAX_PER_MINUTE = 4
    }
}
