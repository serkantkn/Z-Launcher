package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

/**
 * How an app's icon is drawn on a tile.
 *
 * Windows Phone tiles never carried an app's own artwork: every tile was the accent colour with a
 * single white glyph on it. [WINDOWS_PHONE] recreates that — whatever picture the icon starts as,
 * the tile shows its silhouette in the tile's own ink, with nothing behind it. [ORIGINAL] leaves
 * the icon exactly as the app drew it.
 */
enum class TileIconStyle { WINDOWS_PHONE, ORIGINAL }

/**
 * Which picture one app's tile uses. Stored as a single short string per app so the whole set
 * fits in one preference:
 *
 *  - ""                        the launcher decides (icon pack, then the app's own icon)
 *  - "glyph:phone"             one of the bundled Windows Phone glyphs
 *  - "pack:<package>/<name>"   a named drawable out of an installed icon pack
 *  - "file:<path>"             a picture the user chose, copied into the launcher's own files
 */
sealed interface TileIcon {

    data object Default : TileIcon

    data class Glyph(val name: String) : TileIcon

    data class Pack(val pack: String, val drawable: String) : TileIcon

    data class Picture(val path: String) : TileIcon

    fun store(): String = when (this) {
        is Default -> ""
        is Glyph -> "$GLYPH$name"
        is Pack -> "$PACK$pack/$drawable"
        is Picture -> "$FILE$path"
    }

    companion object {
        private const val GLYPH = "glyph:"
        private const val PACK = "pack:"
        private const val FILE = "file:"

        fun parse(value: String?): TileIcon {
            val raw = value?.trim().orEmpty()
            return when {
                raw.startsWith(GLYPH) -> raw.removePrefix(GLYPH)
                    .takeIf { it.isNotBlank() }?.let { Glyph(it) } ?: Default

                raw.startsWith(PACK) -> {
                    val body = raw.removePrefix(PACK)
                    val slash = body.lastIndexOf('/')
                    if (slash <= 0 || slash == body.lastIndex) Default
                    else Pack(body.substring(0, slash), body.substring(slash + 1))
                }

                raw.startsWith(FILE) -> raw.removePrefix(FILE)
                    .takeIf { it.isNotBlank() }?.let { Picture(it) } ?: Default

                else -> Default
            }
        }

        /** The whole per-app map, as it is written to the preference. */
        fun mapToJson(overrides: Map<String, TileIcon>): String {
            val obj = JSONObject()
            overrides.forEach { (packageName, icon) ->
                val stored = icon.store()
                if (stored.isNotEmpty()) obj.put(packageName, stored)
            }
            return obj.toString()
        }

        fun mapFromJson(raw: String?): Map<String, TileIcon> {
            if (raw.isNullOrBlank()) return emptyMap()
            return try {
                val obj = JSONObject(raw)
                buildMap {
                    obj.keys().forEach { key ->
                        val icon = parse(obj.optString(key))
                        if (icon != Default) put(key, icon)
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }
}

/**
 * The Windows Phone glyphs that ship with the launcher: the 24dp filled cut of Microsoft's Fluent
 * UI System Icons (MIT), recoloured white. Each name is both the drawable's suffix —
 * `ic_wp_<name>` — and what gets stored in a [TileIcon.Glyph].
 *
 * Grouped only so the picker has something to break the grid up with.
 */
object WpGlyphs {

    data class Group(val id: String, val names: List<String>)

    val GROUPS: List<Group> = listOf(
        Group(
            "communication",
            listOf(
                "phone", "call", "message", "messages", "mail", "mail_boxes", "inbox",
                "contacts", "person", "emoji", "mic", "video_call", "share", "link"
            )
        ),
        Group(
            "media",
            listOf(
                "music", "headphones", "speaker", "play", "video", "clip", "film", "tv",
                "album", "photo", "camera", "camera_flip", "gallery", "radio", "ticket",
                "brush", "palette", "game", "trophy"
            )
        ),
        Group(
            "places",
            listOf(
                "globe", "map", "compass", "city", "earth", "location", "airplane", "car",
                "bus", "truck", "rocket", "home", "building", "bank"
            )
        ),
        Group(
            "work",
            listOf(
                "calendar", "clock", "timer", "note", "notes", "folder", "folder_open",
                "document", "pdf", "table", "list", "slides", "archive", "briefcase",
                "book", "news", "translate", "print", "scan", "qr", "keyboard", "search",
                "download", "upload", "sync", "bookmark", "tag", "flag", "chart"
            )
        ),
        Group(
            "system",
            listOf(
                "settings", "calculator", "apps", "grid", "layers", "font", "code",
                "terminal", "bug", "key", "lock", "shield", "cloud", "cloud_up", "server",
                "database", "wifi", "bluetooth", "battery", "flash", "lightbulb", "power",
                "laptop", "desktop", "tablet", "watch", "phone_laptop", "tools", "wrench",
                "wand", "accessibility", "alert", "eye", "circle", "square"
            )
        ),
        Group(
            "life",
            listOf(
                "wallet", "money", "cart", "bag", "gift", "star", "heart", "hand", "run",
                "dumbbell", "bed", "pill", "pulse", "stethoscope", "food", "coffee", "wine",
                "dog", "leaf", "sun", "moon", "rain", "umbrella", "fire", "drop", "ribbon"
            )
        )
    )

    val ALL: List<String> = GROUPS.flatMap { it.names }

    fun resourceName(glyph: String): String = "ic_wp_$glyph"
}
