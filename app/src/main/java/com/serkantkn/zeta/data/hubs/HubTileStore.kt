package com.serkantkn.zeta.data.hubs

import android.content.Context
import android.content.SharedPreferences
import com.serkantkn.zeta.hubkit.protocol.HubNode
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Prop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** What another app's hub says on its tile and under its name in Aura. */
data class HubTile(
    val count: Int = 0,
    val line: String? = null,
    val title: String? = null
)

/**
 * The live tiles of other apps' hubs (PLATFORM.md, "Canlı karo"): what each last said, whether
 * while it was open (its description's live tile) or since (written through [HubTileProvider]).
 * Kept on disk, so the list and the board read the same after a restart.
 */
object HubTileStore {
    private const val PREFS = "hub_tiles"

    private val _tiles = MutableStateFlow<Map<String, HubTile>>(emptyMap())
    val tiles: StateFlow<Map<String, HubTile>> = _tiles.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun start(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _tiles.value = p.all.mapNotNull { (key, raw) ->
            val json = runCatching { JSONObject(raw as String) }.getOrNull() ?: return@mapNotNull null
            key to HubTile(
                count = json.optInt("count"),
                line = json.optString("line").takeIf { it.isNotBlank() },
                title = json.optString("title").takeIf { it.isNotBlank() }
            )
        }.toMap()
    }

    /** What a hub's description says about its tile, if it says anything. */
    fun fromSpec(spec: HubNode): HubTile? = spec.childOf(com.serkantkn.zeta.hubkit.protocol.HubProtocol.Node.LIVE_TILE)?.let {
        HubTile(
            count = it.int(Prop.COUNT).coerceIn(0, MAX_COUNT),
            line = it.string(Prop.LINE)?.take(MAX_TEXT),
            title = it.string(Prop.TITLE)?.take(MAX_TEXT)
        )
    }

    fun put(key: String, tile: HubTile) {
        if (_tiles.value[key] == tile) return
        _tiles.value = _tiles.value + (key to tile)
        prefs?.edit()?.putString(key, JSONObject().apply {
            put("count", tile.count)
            tile.line?.let { put("line", it) }
            tile.title?.let { put("title", it) }
        }.toString())?.apply()
    }

    /** Forgets a hub's tile: it was taken off, or its app is gone. */
    fun drop(key: String) {
        if (key !in _tiles.value) return
        _tiles.value = _tiles.value - key
        prefs?.edit()?.remove(key)?.apply()
    }

    const val MAX_TEXT = 200
    const val MAX_COUNT = 9999
}
