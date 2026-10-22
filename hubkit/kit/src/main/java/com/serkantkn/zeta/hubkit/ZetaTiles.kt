package com.serkantkn.zeta.hubkit

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.serkantkn.zeta.hubkit.protocol.HubProtocol

/**
 * Writes a hub's live tile while the hub is closed - from a WorkManager job, a push message, an
 * alarm. Zeta takes it only for a hub the person has added, and at most four times a minute.
 * While the hub is open, its `liveTile { }` is what counts and this is not needed.
 */
object ZetaTiles {

    /**
     * Sets what the tile of [hub] (your [ZetaHubService] subclass) says. Returns whether a Zeta
     * took it. Call it off the main thread if you can: it is a call into another app.
     */
    fun update(context: Context, hub: Class<out ZetaHubService>, tile: LiveTileScope.() -> Unit): Boolean {
        val values = LiveTileScope().apply(tile)
        val extras = Bundle().apply {
            putInt(HubProtocol.Tile.COUNT, values.count)
            values.line?.let { putString(HubProtocol.Tile.LINE, it) }
            values.title?.let { putString(HubProtocol.Tile.TITLE, it) }
        }
        var taken = false
        ZetaIdentity.PACKAGES.forEach { zeta ->
            val authority = zeta + HubProtocol.Tile.AUTHORITY_SUFFIX
            val provider = context.packageManager.resolveContentProvider(authority, 0) ?: return@forEach
            if (provider.packageName != zeta || !ZetaIdentity.isZeta(context, zeta)) return@forEach
            val answer = runCatching {
                context.contentResolver.call(Uri.parse("content://$authority"), HubProtocol.Tile.METHOD, hub.name, extras)
            }.onFailure { Log.w("ZetaHubKit", "$zeta did not take the tile", it) }.getOrNull()
            if (answer?.getBoolean(HubProtocol.Tile.OK) == true) taken = true
        }
        return taken
    }
}
