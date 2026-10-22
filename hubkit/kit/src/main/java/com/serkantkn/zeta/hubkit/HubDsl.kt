package com.serkantkn.zeta.hubkit

import android.net.Uri
import android.os.Bundle
import com.serkantkn.zeta.hubkit.protocol.HubNode
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Node
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Prop
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Style

@DslMarker
annotation class HubDsl

/** A hub as [ZetaHubService.hub] describes it; written with [zetaHub]. */
class HubSpec internal constructor(
    internal val title: String?,
    internal val content: HubScope.() -> Unit
)

/**
 * Describes a hub. [title] defaults to the service's label in the manifest. The block runs again
 * whenever the hub changes ([ZetaHubService.invalidate], [HubState]), so it reads the hub's state
 * as it is now.
 */
fun zetaHub(title: String? = null, content: HubScope.() -> Unit): HubSpec = HubSpec(title, content)

/** A page opened over the hub; written with [subPage] and opened by [PageScope.item]'s `opens`. */
class SubPage internal constructor(
    internal val title: String,
    internal val key: String?,
    internal val content: PageScope.() -> Unit
)

/** A page opened over the hub, with its own [title]. Give it a [key] when more than one may be open. */
fun subPage(title: String, key: String? = null, content: PageScope.() -> Unit): SubPage =
    SubPage(title, key, content)

/** The text styles a [PageScope.text] may take. */
enum class TextStyle(internal val value: String) {
    TITLE(Style.TITLE), BODY(Style.BODY), CAPTION(Style.CAPTION)
}

/** The glyphs a bar command may have. Zeta draws them in its own set. */
enum class HubIcon(internal val value: String) {
    ADD("add"), REFRESH("refresh"), SEARCH("search"), EDIT("edit"), DELETE("delete"),
    SHARE("share"), SETTINGS("settings"), CHECK("check"), CLOSE("close"), STAR("star"),
    PLAY("play"), PAUSE("pause"), NEXT("next"), PREVIOUS("previous"), SEND("send"),
    FILTER("filter"), SORT("sort"), PIN("pin"), CALENDAR("calendar"), MAP("map")
}

/** What touching things does: filled while a description is built, read when an event comes. */
internal class Handlers {
    val clicks = HashMap<String, () -> Unit>()
    val longClicks = HashMap<String, () -> Unit>()
    val actions = HashMap<String, () -> Unit>()
    val toggles = HashMap<String, (Boolean) -> Unit>()
    val opens = HashMap<String, () -> SubPage>()
}

/** Builds the children of one node, keying each by its place unless it is given a key. */
@HubDsl
open class NodeScope internal constructor(
    internal val parentKey: String,
    internal val handlers: Handlers
) {
    internal val nodes = ArrayList<HubNode>()
    private val usedKeys = HashSet<String>()

    internal fun keyFor(given: Any?, kind: String): String {
        val base = given?.let { "$parentKey/k:$it" } ?: "$parentKey/${nodes.size}:$kind"
        var key = base
        var n = 1
        while (!usedKeys.add(key)) key = "$base#${n++}"
        return key
    }

    internal fun add(type: String, key: String, props: Bundle, children: List<HubNode> = emptyList()): String {
        nodes.add(HubNode(type, key, props, children))
        return key
    }
}

/** The hub itself: its pages, its bar, its tile. */
@HubDsl
class HubScope internal constructor(handlers: Handlers) : NodeScope("hub", handlers) {

    /** A page under the heading [heading]. Pages come in the order they are written. */
    fun page(heading: String, key: Any? = null, content: PageScope.() -> Unit) {
        val k = keyFor(key ?: heading, Node.PAGE)
        val page = PageScope(k, handlers).apply(content)
        add(Node.PAGE, k, Bundle().apply { putString(Prop.TITLE, heading) }, page.nodes)
    }

    /** Shown instead of the pages while it is written: a sign-in, an explanation, a question. */
    fun takeover(content: PageScope.() -> Unit) {
        val k = keyFor("takeover", Node.TAKEOVER)
        add(Node.TAKEOVER, k, Bundle(), PageScope(k, handlers).apply(content).nodes)
    }

    /** The commands at the bottom of the hub. */
    fun bar(content: BarScope.() -> Unit) {
        val k = keyFor("bar", Node.BAR)
        add(Node.BAR, k, Bundle(), BarScope(k, handlers).apply(content).nodes)
    }

    /** What the hub's tile and its line in Zeta's list say. */
    fun liveTile(content: LiveTileScope.() -> Unit) {
        val tile = LiveTileScope().apply(content)
        add(Node.LIVE_TILE, keyFor("tile", Node.LIVE_TILE), Bundle().apply {
            putInt(Prop.COUNT, tile.count)
            tile.line?.let { putString(Prop.LINE, it) }
            tile.title?.let { putString(Prop.TITLE, it) }
        })
    }
}

/** The commands at the bottom: at most four with glyphs, any number in the menu. */
@HubDsl
class BarScope internal constructor(parentKey: String, handlers: Handlers) : NodeScope(parentKey, handlers) {
    private var actionCount = 0

    /** A command with a glyph. Only the first four are shown. */
    fun action(label: String, icon: HubIcon, key: Any? = null, onClick: () -> Unit) {
        if (actionCount++ >= MAX_ACTIONS) return
        val k = keyFor(key ?: label, Node.ACTION)
        handlers.clicks[k] = onClick
        add(Node.ACTION, k, Bundle().apply {
            putString(Prop.LABEL, label)
            putString(Prop.ICON, icon.value)
        })
    }

    /** A command in the menu the bar's "…" opens. */
    fun menu(label: String, key: Any? = null, onClick: () -> Unit) {
        val k = keyFor(key ?: label, Node.MENU_ITEM)
        handlers.clicks[k] = onClick
        add(Node.MENU_ITEM, k, Bundle().apply { putString(Prop.LABEL, label) })
    }

    private companion object {
        const val MAX_ACTIONS = 4
    }
}

/** What the hub's tile says. */
@HubDsl
class LiveTileScope internal constructor() {
    /** The number in the tile's corner and beside the hub's name in Aura; 0 shows none. */
    var count: Int = 0
    /** A line about what is new: under the hub's name in Aura, on the tile's back. */
    var line: String? = null
    /** A heading for the tile's wide face. */
    var title: String? = null
}

/** What a page, a sub-page or a takeover holds, top to bottom. */
@HubDsl
class PageScope internal constructor(parentKey: String, handlers: Handlers) : NodeScope(parentKey, handlers) {

    /** A heading over the part of the page that follows, with an optional action at its end. */
    fun section(title: String, actionLabel: String? = null, key: Any? = null, onAction: (() -> Unit)? = null) {
        val k = keyFor(key ?: title, Node.SECTION)
        if (onAction != null) handlers.actions[k] = onAction
        add(Node.SECTION, k, Bundle().apply {
            putString(Prop.TITLE, title)
            if (actionLabel != null && onAction != null) putString(Prop.ACTION_LABEL, actionLabel)
        })
    }

    /**
     * A line of a list. [image] is a picture beside it (a content:// address your app lets Zeta
     * read, or https://); [count] a number at its end. Touching it runs [onClick], or opens the
     * sub-page [opens] makes - and in Aura the [title] flies up to become that page's title.
     * Give lines of a changing list a [key] (their id), so Zeta keeps its place as it changes.
     */
    fun item(
        title: String,
        subtitle: String? = null,
        meta: String? = null,
        image: Uri? = null,
        roundImage: Boolean = false,
        count: Int = 0,
        key: Any? = null,
        opens: (() -> SubPage)? = null,
        onLongClick: (() -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        val k = keyFor(key, Node.ITEM)
        if (onClick != null) handlers.clicks[k] = onClick
        if (opens != null) handlers.opens[k] = opens
        if (onLongClick != null) handlers.longClicks[k] = onLongClick
        add(Node.ITEM, k, Bundle().apply {
            putString(Prop.TITLE, title)
            subtitle?.let { putString(Prop.SUBTITLE, it) }
            meta?.let { putString(Prop.META, it) }
            image?.let { putString(Prop.IMAGE, it.toString()) }
            if (roundImage) putBoolean(Prop.ROUND, true)
            if (count > 0) putInt(Prop.COUNT, count)
            putBoolean(Prop.CLICKABLE, onClick != null || opens != null)
            if (onLongClick != null) putBoolean(Prop.LONG_CLICKABLE, true)
            if (opens != null) putBoolean(Prop.OPENS, true)
        })
    }

    /** One [item] (or anything else) per element of [items]; [key] keeps each in its place. */
    fun <T> list(items: Iterable<T>, key: (T) -> Any, row: PageScope.(T) -> Unit) {
        items.forEach { element ->
            val scope = PageScope(keyFor(key(element), "row"), handlers).apply { row(element) }
            nodes.addAll(scope.nodes)
        }
    }

    /** Words, as a heading, body text or a small caption. */
    fun text(text: String, style: TextStyle = TextStyle.BODY, key: Any? = null) {
        add(Node.TEXT, keyFor(key, Node.TEXT), Bundle().apply {
            putString(Prop.TEXT, text)
            putString(Prop.STYLE, style.value)
        })
    }

    /** A button. */
    fun button(label: String, key: Any? = null, onClick: () -> Unit) {
        val k = keyFor(key ?: label, Node.BUTTON)
        handlers.clicks[k] = onClick
        add(Node.BUTTON, k, Bundle().apply { putString(Prop.LABEL, label) })
    }

    /** Nothing here yet, and perhaps something to do about it. */
    fun empty(message: String, actionLabel: String? = null, key: Any? = null, onAction: (() -> Unit)? = null) {
        val k = keyFor(key, Node.EMPTY)
        if (onAction != null) handlers.actions[k] = onAction
        add(Node.EMPTY, k, Bundle().apply {
            putString(Prop.MESSAGE, message)
            if (actionLabel != null && onAction != null) putString(Prop.ACTION_LABEL, actionLabel)
        })
    }

    /** A word to the person in a line of its own, and perhaps something to do about it. */
    fun notice(message: String, actionLabel: String? = null, key: Any? = null, onAction: (() -> Unit)? = null) {
        val k = keyFor(key, Node.NOTICE)
        if (onAction != null) handlers.actions[k] = onAction
        add(Node.NOTICE, k, Bundle().apply {
            putString(Prop.MESSAGE, message)
            if (actionLabel != null && onAction != null) putString(Prop.ACTION_LABEL, actionLabel)
        })
    }

    /** Still coming. */
    fun loading(key: Any? = null) {
        add(Node.LOADING, keyFor(key, Node.LOADING), Bundle())
    }

    /** A switch. [onChange] gets the new state; the hub then describes itself with it. */
    fun switch(title: String, checked: Boolean, subtitle: String? = null, key: Any? = null, onChange: (Boolean) -> Unit) {
        val k = keyFor(key ?: title, Node.SWITCH)
        handlers.toggles[k] = onChange
        add(Node.SWITCH, k, Bundle().apply {
            putString(Prop.TITLE, title)
            putBoolean(Prop.CHECKED, checked)
            subtitle?.let { putString(Prop.SUBTITLE, it) }
        })
    }

    /** A setting and its value; touching it runs [onClick]. */
    fun value(title: String, value: String, key: Any? = null, onClick: () -> Unit) {
        val k = keyFor(key ?: title, Node.VALUE)
        handlers.clicks[k] = onClick
        add(Node.VALUE, k, Bundle().apply {
            putString(Prop.TITLE, title)
            putString(Prop.VALUE, value)
        })
    }
}
