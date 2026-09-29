package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

/**
 * Everything one tile has been told to do differently from the rest of the board.
 *
 * Every field is a departure from the board-wide setting, and a null (or the default) means "as
 * everywhere else" — so a tile with nothing set is [isDefault] and is simply not stored. The set
 * is keyed by the tile's id, the same "hub:", "app:", "web:"… ids the board is stored under, so a
 * look follows its tile through reorders, resizes and folders.
 */
data class TileLook(
    /** ARGB, or null for the accent colour (or whatever colour the tile brings of its own). */
    val color: Int? = null,
    /** What the corner says instead of the tile's own name; null keeps the name. */
    val name: String? = null,
    /** A glyph, a pack drawable or a picture standing in for the tile's own icon. */
    val icon: TileIcon = TileIcon.Default,
    val showLabel: Boolean = true,
    /** A picture filling the whole tile, copied into the launcher's own files. */
    val picture: String? = null,
    /** How large the icon is drawn against its usual size; 1f is the usual size. */
    val iconScale: Float = 1f,
    /** How this tile turns, or null to turn the way the setting says. */
    val animation: TileAnimation? = null,
    /** Off: no count in the corner and no notification face for this tile. */
    val notifications: Boolean = true,
    /** 0..100, or null for the board-wide transparency. */
    val opacity: Int? = null,
    /** Black or white writing, or null for the board-wide choice. */
    val ink: TileInk? = null
) {
    val isDefault: Boolean
        get() = this == DEFAULT

    fun toJson(): JSONObject = JSONObject().apply {
        color?.let { put(KEY_COLOR, it) }
        name?.takeIf { it.isNotBlank() }?.let { put(KEY_NAME, it) }
        icon.store().takeIf { it.isNotEmpty() }?.let { put(KEY_ICON, it) }
        if (!showLabel) put(KEY_SHOW_LABEL, false)
        picture?.takeIf { it.isNotBlank() }?.let { put(KEY_PICTURE, it) }
        if (iconScale != 1f) put(KEY_ICON_SCALE, iconScale.toDouble())
        animation?.let { put(KEY_ANIMATION, it.name) }
        if (!notifications) put(KEY_NOTIFICATIONS, false)
        opacity?.let { put(KEY_OPACITY, it) }
        ink?.let { put(KEY_INK, it.name) }
    }

    companion object {
        val DEFAULT = TileLook()

        const val MIN_ICON_SCALE = 0.5f
        const val MAX_ICON_SCALE = 1.6f

        private const val KEY_COLOR = "c"
        private const val KEY_NAME = "n"
        private const val KEY_ICON = "i"
        private const val KEY_SHOW_LABEL = "l"
        private const val KEY_PICTURE = "p"
        private const val KEY_ICON_SCALE = "s"
        private const val KEY_ANIMATION = "a"
        private const val KEY_NOTIFICATIONS = "b"
        private const val KEY_OPACITY = "o"
        private const val KEY_INK = "k"

        fun fromJson(obj: JSONObject): TileLook = TileLook(
            color = if (obj.has(KEY_COLOR)) obj.optInt(KEY_COLOR) else null,
            name = obj.optString(KEY_NAME).takeIf { it.isNotBlank() },
            icon = TileIcon.parse(obj.optString(KEY_ICON)),
            showLabel = obj.optBoolean(KEY_SHOW_LABEL, true),
            picture = obj.optString(KEY_PICTURE).takeIf { it.isNotBlank() },
            iconScale = obj.optDouble(KEY_ICON_SCALE, 1.0).toFloat()
                .coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE),
            animation = obj.optString(KEY_ANIMATION).takeIf { it.isNotBlank() }
                ?.let { runCatching { TileAnimation.valueOf(it) }.getOrNull() },
            notifications = obj.optBoolean(KEY_NOTIFICATIONS, true),
            opacity = if (obj.has(KEY_OPACITY)) obj.optInt(KEY_OPACITY).coerceIn(0, 100) else null,
            ink = obj.optString(KEY_INK).takeIf { it.isNotBlank() }
                ?.let { runCatching { TileInk.valueOf(it) }.getOrNull() }
        )

        /** The whole set, by tile id, as it is written to the preference. Default looks are left out. */
        fun mapToJson(looks: Map<String, TileLook>): String {
            val obj = JSONObject()
            looks.forEach { (id, look) -> if (!look.isDefault) obj.put(id, look.toJson()) }
            return obj.toString()
        }

        fun mapFromJson(raw: String?): Map<String, TileLook> {
            if (raw.isNullOrBlank()) return emptyMap()
            return try {
                val obj = JSONObject(raw)
                buildMap {
                    obj.keys().forEach { id ->
                        val entry = obj.optJSONObject(id) ?: return@forEach
                        val look = fromJson(entry)
                        if (!look.isDefault) put(id, look)
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }
}
