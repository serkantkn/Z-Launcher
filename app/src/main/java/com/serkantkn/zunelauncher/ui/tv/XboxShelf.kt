package com.serkantkn.zunelauncher.ui.tv

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Something the Xbox design can open, pin and remember: a TV app, one of the launcher's hubs, or
 * the film left part way. Its [key] says which ("app:<package>", "hub:<place>", "film:continue").
 */
internal class XboxItem(
    val key: String,
    val label: String,
    val line: String?,
    val icon: ImageVector?,
    val image: ImageBitmap?,
    /** An app's own banner, which carries its name. */
    val banner: Boolean,
    val kind: Kind,
    val packageName: String? = null,
    val film: TvContinue? = null,
    val progress: Float? = null,
    private val run: () -> Unit
) {
    /** [MEDIA] is something found by search - a song, an album, a video - opened but not kept. */
    enum class Kind { APP, HUB, FILM, MEDIA }

    /** Opens it, and remembers it among the recent - but only an app or a hub, which can be found again. */
    fun open() {
        if (pinnable) XboxShelf.used(key)
        run()
    }

    val pinnable: Boolean get() = kind == Kind.APP || kind == Kind.HUB
}

/** The pages of cards the dashboard keeps: every app, the pinned, the recent. */
internal enum class XboxCards { APPS, PINS, RECENT, SEARCH }

/**
 * What the Xbox design keeps: the things pinned to home ("my pins", in the order the user put them)
 * and the things opened lately ("recent", newest first). Plain SharedPreferences: small, and read
 * as Start is drawn.
 */
internal object XboxShelf {
    private var prefs: SharedPreferences? = null

    private val _pins = MutableStateFlow<List<String>>(emptyList())
    val pins: StateFlow<List<String>> = _pins.asStateFlow()

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    /** Everything that can be opened now, by key: what the guide's recent list is read against. */
    val items = MutableStateFlow<Map<String, XboxItem>>(emptyMap())

    /** A page of cards asked for from outside Start (the guide); Start opens it and clears this. */
    private val _pendingPage = MutableStateFlow<XboxCards?>(null)
    val pendingPage: StateFlow<XboxCards?> = _pendingPage.asStateFlow()

    @Synchronized
    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _pins.value = p.read(KEY_PINS)
        _recent.value = p.read(KEY_RECENT)
    }

    fun isPinned(key: String) = key in _pins.value

    fun pin(key: String) {
        if (key in _pins.value) return
        _pins.value = _pins.value + key
        save()
    }

    fun unpin(key: String) {
        _pins.value = _pins.value - key
        save()
    }

    fun moveToFront(key: String) {
        if (key !in _pins.value) return
        _pins.value = listOf(key) + (_pins.value - key)
        save()
    }

    fun used(key: String) {
        _recent.value = (listOf(key) + (_recent.value - key)).take(MAX_RECENT)
        save()
    }

    fun request(page: XboxCards) {
        _pendingPage.value = page
    }

    fun consumePage() {
        _pendingPage.value = null
    }

    private fun save() {
        prefs?.edit()
            ?.putString(KEY_PINS, _pins.value.joinToString("\n"))
            ?.putString(KEY_RECENT, _recent.value.joinToString("\n"))
            ?.apply()
    }

    private fun SharedPreferences.read(key: String): List<String> =
        getString(key, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private const val PREFS = "xbox_shelf"
    private const val KEY_PINS = "pins"
    private const val KEY_RECENT = "recent"
    private const val MAX_RECENT = 20
}

/** The Xbox guide: open or not, from anywhere in the launcher. */
internal object XboxGuide {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    fun show() {
        _open.value = true
    }

    fun hide() {
        _open.value = false
    }

    fun toggle() {
        _open.value = !_open.value
    }
}
