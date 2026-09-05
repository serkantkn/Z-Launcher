package com.serkantkn.zunelauncher.data.model

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

    companion object {
        fun fromHub(hubType: HubType, span: Int = 2): StartTileItem =
            StartTileItem("hub:${hubType.name}", span)

        fun fromApp(packageName: String, span: Int = 2): StartTileItem =
            StartTileItem("app:$packageName", span)

        fun fromNote(noteId: String, span: Int = 2): StartTileItem =
            StartTileItem("note:$noteId", span)

        const val QUICK_NOTE_ID = "note:new"
    }
}
