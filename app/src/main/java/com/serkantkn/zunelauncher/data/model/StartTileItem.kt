package com.serkantkn.zunelauncher.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One tile on the Start screen. [id] is prefixed with "hub:", "app:", "note:", "web:", "person:",
 * "sms:", "album:" or "folder:".
 * Persisted by SettingsDataStore as a JSON array of [toJson] objects.
 *
 * A folder holds the ids of the tiles inside it in [children]; those tiles are not listed on the
 * board themselves. Folders never nest — a folder's children are always plain tiles.
 */
data class StartTileItem(
    val id: String,
    val span: Int = 2,
    /** Folders only: the tiles inside, in the order they are shown. */
    val children: List<String> = emptyList(),
    /** What the user called it: a folder's name, or a pinned site's title. */
    val name: String = ""
) {
    val isHub: Boolean get() = id.startsWith("hub:")
    val isApp: Boolean get() = id.startsWith("app:")
    val isNote: Boolean get() = id.startsWith("note:") && !isQuickNote
    val isFolder: Boolean get() = id.startsWith("folder:")

    /** A website pinned to Start, the way Windows Phone let any page become a tile. */
    val isWeb: Boolean get() = id.startsWith(WEB_PREFIX)

    val webUrl: String? get() = if (isWeb) id.removePrefix(WEB_PREFIX) else null

    /** A record pinned to Start: its cover, and it starts playing when tapped. */
    val isMusicAlbum: Boolean get() = id.startsWith(MUSIC_ALBUM_PREFIX)

    val musicAlbumId: Long? get() =
        if (isMusicAlbum) id.removePrefix(MUSIC_ALBUM_PREFIX).toLongOrNull() else null

    /** A picture album pinned to Start, cycling its own pictures the way the hub tile does. */
    val isAlbum: Boolean get() = id.startsWith(ALBUM_PREFIX)

    val albumBucketId: Long? get() = if (isAlbum) id.removePrefix(ALBUM_PREFIX).toLongOrNull() else null

    /** A conversation pinned to Start, so somebody's messages are one tap from the home screen. */
    val isThread: Boolean get() = id.startsWith(SMS_PREFIX)

    val smsThreadId: Long? get() = if (isThread) id.removePrefix(SMS_PREFIX).toLongOrNull() else null

    /** A person pinned to Start — the tile Windows Phone was best known for. */
    val isPerson: Boolean get() = id.startsWith(PERSON_PREFIX)

    val contactId: String? get() = if (isPerson) id.removePrefix(PERSON_PREFIX) else null

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
        .apply {
            if (isFolder) {
                put(KEY_CHILDREN, JSONArray().apply { children.forEach { put(it) } })
            }
            if (name.isNotBlank()) put(KEY_NAME, name)
        }

    companion object {
        fun fromHub(hubType: HubType, span: Int = 2): StartTileItem =
            StartTileItem("hub:${hubType.name}", span)

        fun fromApp(packageName: String, span: Int = 2): StartTileItem =
            StartTileItem("app:$packageName", span)

        fun fromNote(noteId: String, span: Int = 2): StartTileItem =
            StartTileItem("note:$noteId", span)

        /** A website as a tile. The address is the id, so the same page is never pinned twice. */
        fun fromWeb(url: String, title: String, span: Int = 2): StartTileItem =
            StartTileItem("$WEB_PREFIX$url", span, name = title.trim())

        /** An album as a tile, named so it still reads if the album is emptied or renamed. */
        fun fromAlbum(bucketId: Long, name: String, span: Int = 2): StartTileItem =
            StartTileItem("$ALBUM_PREFIX$bucketId", span, name = name.trim())

        /** A record as a tile, named so it still reads before the library has been read. */
        fun fromMusicAlbum(albumId: Long, name: String, span: Int = 2): StartTileItem =
            StartTileItem("$MUSIC_ALBUM_PREFIX$albumId", span, name = name.trim())

        /** A person as a tile, named so the tile still reads if the contact goes missing. */
        fun fromPerson(contactId: String, name: String, span: Int = 2): StartTileItem =
            StartTileItem("$PERSON_PREFIX$contactId", span, name = name.trim())

        /** A conversation as a tile, named so it still reads if the address book changes. */
        fun fromThread(threadId: Long, title: String, span: Int = 2): StartTileItem =
            StartTileItem("$SMS_PREFIX$threadId", span, name = title.trim())

        /** A new, empty folder. The id is generated once and then travels with the folder. */
        fun newFolder(children: List<String>, span: Int = 2, name: String = ""): StartTileItem =
            StartTileItem("$FOLDER_PREFIX${UUID.randomUUID()}", span, children, name)

        const val QUICK_NOTE_ID = "note:new"
        const val DEFAULT_SPAN = 2
        const val FOLDER_PREFIX = "folder:"
        const val WEB_PREFIX = "web:"
        const val PERSON_PREFIX = "person:"
        const val SMS_PREFIX = "sms:"
        const val ALBUM_PREFIX = "album:"
        const val MUSIC_ALBUM_PREFIX = "record:"
        private const val KEY_ID = "id"
        private const val KEY_SPAN = "span"
        private const val KEY_CHILDREN = "children"
        private const val KEY_NAME = "name"

        /** Throws JSONException when [KEY_ID] is missing. */
        fun fromJson(obj: JSONObject): StartTileItem {
            val id = obj.getString(KEY_ID)
            val childrenArray = obj.optJSONArray(KEY_CHILDREN)
            val children = if (childrenArray == null) emptyList() else {
                (0 until childrenArray.length()).mapNotNull { index ->
                    childrenArray.optString(index).takeIf { it.isNotBlank() }
                }
            }
            return StartTileItem(
                id = id,
                span = obj.optInt(KEY_SPAN, DEFAULT_SPAN),
                children = children,
                name = obj.optString(KEY_NAME, "")
            )
        }

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

// ════════════════════════════════════════════════════════════
// FOLDER EDITS (pure list maths, unit tested)
// ════════════════════════════════════════════════════════════

/**
 * Every change the Start board can make to a folder, kept as plain list functions so the rules —
 * a folder never nests, never holds fewer than two tiles, and gives its tiles back to the board
 * where it stood — hold everywhere and can be tested without a screen.
 */
object StartFolders {

    /** A folder with one tile left is not a folder; this is when it gives up. */
    const val MINIMUM_CHILDREN = 2

    /**
     * Drops [sourceId] onto [targetId]. If the target is already a folder the tile joins it,
     * otherwise the two become a new folder standing where the target stood.
     */
    fun merge(tiles: List<StartTileItem>, sourceId: String, targetId: String): List<StartTileItem> {
        if (sourceId == targetId) return tiles
        val source = tiles.firstOrNull { it.id == sourceId } ?: return tiles
        val target = tiles.firstOrNull { it.id == targetId } ?: return tiles
        // Folders do not nest: a folder dragged onto something else stays where it is.
        if (source.isFolder) return tiles

        val result = tiles.toMutableList()
        val targetIndex = result.indexOfFirst { it.id == targetId }
        if (target.isFolder) {
            result[targetIndex] = target.copy(children = target.children + sourceId)
        } else {
            result[targetIndex] = StartTileItem.newFolder(
                children = listOf(targetId, sourceId),
                span = target.span
            )
        }
        result.removeAll { it.id == sourceId }
        return result
    }

    /** Takes [childId] out of its folder and puts it back on the board, right after the folder. */
    fun extract(tiles: List<StartTileItem>, folderId: String, childId: String, span: Int): List<StartTileItem> {
        val folderIndex = tiles.indexOfFirst { it.id == folderId }
        if (folderIndex < 0) return tiles
        val folder = tiles[folderIndex]
        if (childId !in folder.children) return tiles

        val result = tiles.toMutableList()
        result[folderIndex] = folder.copy(children = folder.children - childId)
        result.add(folderIndex + 1, StartTileItem(childId, span))
        return collapseThinFolders(result)
    }

    /** Empties a folder onto the board in its place. */
    fun dissolve(tiles: List<StartTileItem>, folderId: String, spanOf: (String) -> Int): List<StartTileItem> {
        val folderIndex = tiles.indexOfFirst { it.id == folderId }
        if (folderIndex < 0) return tiles
        val folder = tiles[folderIndex]
        val result = tiles.toMutableList()
        result.removeAt(folderIndex)
        folder.children.forEachIndexed { offset, childId ->
            result.add(folderIndex + offset, StartTileItem(childId, spanOf(childId)))
        }
        return result
    }

    /** Reorders the tiles inside a folder. */
    fun reorderChildren(tiles: List<StartTileItem>, folderId: String, from: Int, to: Int): List<StartTileItem> {
        val folderIndex = tiles.indexOfFirst { it.id == folderId }
        if (folderIndex < 0) return tiles
        val folder = tiles[folderIndex]
        if (from !in folder.children.indices || to !in folder.children.indices) return tiles
        val children = folder.children.toMutableList()
        children.add(to, children.removeAt(from))
        return tiles.toMutableList().also { it[folderIndex] = folder.copy(children = children) }
    }

    fun rename(tiles: List<StartTileItem>, folderId: String, name: String): List<StartTileItem> {
        val folderIndex = tiles.indexOfFirst { it.id == folderId }
        if (folderIndex < 0) return tiles
        return tiles.toMutableList().also {
            it[folderIndex] = it[folderIndex].copy(name = name.trim())
        }
    }

    /** Drops a tile wherever it lives — on the board or inside a folder. */
    fun removeTile(tiles: List<StartTileItem>, tileId: String): List<StartTileItem> {
        val result = tiles
            .filterNot { it.id == tileId }
            .map { if (it.isFolder) it.copy(children = it.children - tileId) else it }
        return collapseThinFolders(result)
    }

    /**
     * A folder that has been reduced to one tile hands it back and disappears; an empty one just
     * disappears. Called after every edit that can take a tile out.
     */
    fun collapseThinFolders(tiles: List<StartTileItem>): List<StartTileItem> {
        if (tiles.none { it.isFolder && it.children.size < MINIMUM_CHILDREN }) return tiles
        val result = mutableListOf<StartTileItem>()
        for (item in tiles) {
            when {
                !item.isFolder -> result += item
                item.children.size >= MINIMUM_CHILDREN -> result += item
                item.children.size == 1 -> result += StartTileItem(item.children.first(), item.span)
                // Empty: nothing to hand back.
            }
        }
        return result
    }

    /** Every id on the board, folders' children included. */
    fun allTileIds(tiles: List<StartTileItem>): Set<String> =
        buildSet {
            tiles.forEach { item ->
                if (item.isFolder) addAll(item.children) else add(item.id)
            }
        }
}
