package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

/**
 * One tile on the Start screen. [id] is prefixed with "hub:", "app:" or "note:".
 * Persisted by SettingsDataStore as a JSON array of [toJson] objects.
 */
data class StartTileItem(
    val id: String,
    val span: Int = 2
) {
    val isHub: Boolean get() = id.startsWith("hub:")
    val isApp: Boolean get() = id.startsWith("app:")
    val isNote: Boolean get() = id.startsWith("note:") && !isQuickNote
    /** The "hızlı not" tile: opens a blank editor. */
    val isQuickNote: Boolean get() = id == QUICK_NOTE_ID

    val noteId: String? get() = if (isNote) id.removePrefix("note:") else null

    val hubType: HubType? get() {
        if (!isHub) return null
        val name = id.removePrefix("hub:")
        return try {
            HubType.valueOf(name)
        } catch (_: Exception) {
            null
        }
    }

    val packageName: String? get() {
        if (!isApp) return null
        return id.removePrefix("app:")
    }

    fun toJson(): JSONObject = JSONObject()
        .put(KEY_ID, id)
        .put(KEY_SPAN, span)

    companion object {
        fun fromHub(hubType: HubType, span: Int = 2): StartTileItem =
            StartTileItem("hub:${hubType.name}", span)

        fun fromApp(packageName: String, span: Int = 2): StartTileItem =
            StartTileItem("app:$packageName", span)

        fun fromNote(noteId: String, span: Int = 2): StartTileItem =
            StartTileItem("note:$noteId", span)

        const val QUICK_NOTE_ID = "note:new"
        const val DEFAULT_SPAN = 2
        private const val KEY_ID = "id"
        private const val KEY_SPAN = "span"

        /** Throws JSONException when [KEY_ID] is missing. */
        fun fromJson(obj: JSONObject): StartTileItem = StartTileItem(
            id = obj.getString(KEY_ID),
            span = obj.optInt(KEY_SPAN, DEFAULT_SPAN)
        )

        /**
         * Parses one element of the pre-JSON comma-delimited list. Supports both legacy
         * shapes: "id#span" and the older "type:id:span". Returns null when malformed.
         */
        fun fromLegacyString(itemStr: String): StartTileItem? {
            val hashParts = itemStr.split("#")
            if (hashParts.size == 2) {
                return StartTileItem(hashParts[0], hashParts[1].toIntOrNull() ?: DEFAULT_SPAN)
            }
            val parts = itemStr.split(":")
            if (parts.size >= 3) {
                return StartTileItem("${parts[0]}:${parts[1]}", parts[2].toIntOrNull() ?: DEFAULT_SPAN)
            }
            return null
        }
    }
}
