package com.serkantkn.zeta.hubkit.protocol

/**
 * The names Zeta and a hub app agree on. Version [VERSION] of the protocol; a name is never given
 * a new meaning, only added (PLATFORM.md, "Sürümleme").
 */
object HubProtocol {
    /** The protocol this build speaks. */
    const val VERSION = 1

    /** The action a hub app's service answers. */
    const val ACTION_HUB = "com.serkantkn.zeta.action.HUB"

    /** Service meta-data: the protocol version the hub was written for. */
    const val META_PROTOCOL = "com.serkantkn.zeta.hub.protocol"

    /** Service meta-data: whether the hub is made for a television too (not used before TV support). */
    const val META_TELEVISION = "com.serkantkn.zeta.hub.television"

    /** The kinds of [HubNode]. */
    object Node {
        /** The root: [Prop.TITLE]; children are pages, a bar, a live tile, a takeover and open sub-pages. */
        const val HUB = "hub"
        /** One heading's page: [Prop.TITLE] is the heading. */
        const val PAGE = "page"
        /** What stands in for the pages while it is there (a sign-in, an explanation). */
        const val TAKEOVER = "takeover"
        /** A page opened over the hub, last on top: [Prop.TITLE]. Back sends [Event.BACK] with its key. */
        const val SUB_PAGE = "subpage"
        /** The commands at the bottom: actions and menu items. */
        const val BAR = "bar"
        /** A command with a glyph: [Prop.LABEL], [Prop.ICON]. */
        const val ACTION = "action"
        /** A command in the bar's menu: [Prop.LABEL]. */
        const val MENU_ITEM = "menu"
        /** What the hub's tile and its line in the list say: [Prop.COUNT], [Prop.LINE], [Prop.TITLE]. */
        const val LIVE_TILE = "tile"

        /** A heading over a part of a page: [Prop.TITLE], optionally [Prop.ACTION_LABEL]. */
        const val SECTION = "section"
        /** A line of a list: [Prop.TITLE], [Prop.SUBTITLE], [Prop.META], [Prop.IMAGE], [Prop.COUNT]. */
        const val ITEM = "item"
        /** Words: [Prop.TEXT] in [Prop.STYLE]. */
        const val TEXT = "text"
        /** A button: [Prop.LABEL]. */
        const val BUTTON = "button"
        /** Nothing here yet: [Prop.MESSAGE], optionally [Prop.ACTION_LABEL]. */
        const val EMPTY = "empty"
        /** A word to the person, in a line: [Prop.MESSAGE], optionally [Prop.ACTION_LABEL]. */
        const val NOTICE = "notice"
        /** Still coming. */
        const val LOADING = "loading"
        /** A switch: [Prop.TITLE], [Prop.SUBTITLE], [Prop.CHECKED]. */
        const val SWITCH = "switch"
        /** A setting and its value: [Prop.TITLE], [Prop.VALUE]. */
        const val VALUE = "value"

        /** The kinds this version knows, with their own versions: what Zeta announces in [Host.COMPONENTS]. */
        val V1: Map<String, Int> = listOf(
            HUB, PAGE, TAKEOVER, SUB_PAGE, BAR, ACTION, MENU_ITEM, LIVE_TILE,
            SECTION, ITEM, TEXT, BUTTON, EMPTY, NOTICE, LOADING, SWITCH, VALUE
        ).associateWith { 1 }
    }

    /** The names of a node's [HubNode.props]. */
    object Prop {
        const val TITLE = "title"
        const val SUBTITLE = "subtitle"
        const val META = "meta"
        /** A picture: a content:// address the hub app has let Zeta read, or https://. */
        const val IMAGE = "image"
        const val COUNT = "count"
        const val TEXT = "text"
        /** [Style] of a [Node.TEXT]. */
        const val STYLE = "style"
        const val LABEL = "label"
        /** One of [Icon]. */
        const val ICON = "icon"
        const val MESSAGE = "message"
        const val ACTION_LABEL = "actionLabel"
        const val CHECKED = "checked"
        const val VALUE = "value"
        /** The live tile's line under the hub's name in Aura, and its tile's back. */
        const val LINE = "line"
        /** The node answers a touch. */
        const val CLICKABLE = "clickable"
        /** The node answers a long press. */
        const val LONG_CLICKABLE = "longClickable"
        /** Touching it opens a sub-page: in Aura its words fly up to become the page's title. */
        const val OPENS = "opens"
        /** The image is a person or a round thing. */
        const val ROUND = "round"
    }

    /** How a [Node.TEXT] reads. */
    object Style {
        const val TITLE = "title"
        const val BODY = "body"
        const val CAPTION = "caption"
    }

    /** The glyphs a command may have; Zeta draws them in its own set. */
    object Icon {
        const val ADD = "add"
        const val REFRESH = "refresh"
        const val SEARCH = "search"
        const val EDIT = "edit"
        const val DELETE = "delete"
        const val SHARE = "share"
        const val SETTINGS = "settings"
        const val CHECK = "check"
        const val CLOSE = "close"
        const val STAR = "star"
        const val PLAY = "play"
        const val PAUSE = "pause"
        const val NEXT = "next"
        const val PREVIOUS = "previous"
        const val SEND = "send"
        const val FILTER = "filter"
        const val SORT = "sort"
        const val PIN = "pin"
        const val CALENDAR = "calendar"
        const val MAP = "map"
    }

    /** The [android.os.Bundle] Zeta sends with [IZetaHub.hello]. */
    object Host {
        /** Int: the protocol Zeta speaks. */
        const val PROTOCOL = "protocol"
        /** Bundle: node kind to its version, for every kind Zeta can draw. */
        const val COMPONENTS = "components"
        /** String: "phone" or "tablet". */
        const val FORM_FACTOR = "formFactor"
        /** String: "aura", "mosaic" or "panorama" - for the hub to know, not to draw by. */
        const val LAYOUT = "layout"
        /** String: a BCP 47 language tag. */
        const val LOCALE = "locale"
        /** Boolean: the dark theme. */
        const val DARK = "dark"
        /** Int: the accent colour, ARGB. */
        const val ACCENT = "accent"
    }

    /** The [android.os.Bundle] Zeta sends with [IZetaHub.event]. */
    object Event {
        /** String: what happened, one of the kinds below. */
        const val KIND = "kind"
        /** String: the node it happened to. */
        const val KEY = "key"
        /** Boolean: a switch's new state. */
        const val CHECKED = "checked"
        /** Int: the page now in front. */
        const val INDEX = "index"
        /** Boolean: for [VISIBLE]. */
        const val SHOWN = "shown"

        const val CLICK = "click"
        const val LONG_CLICK = "longClick"
        const val TOGGLE = "toggle"
        /** The secondary action of a section, an empty page or a notice. */
        const val ACTION = "action"
        const val PAGE = "page"
        /** Back on an open sub-page ([KEY] is the sub-page's). */
        const val BACK = "back"
        /** The hub came into view or went out of it. */
        const val VISIBLE = "visible"
    }

    /**
     * Writing a live tile while the hub is closed: `ContentResolver.call` on Zeta's provider at
     * [AUTHORITY_SUFFIX] after Zeta's package name, method [METHOD], arg the hub service's class name.
     */
    object Tile {
        const val AUTHORITY_SUFFIX = ".hubtiles"
        const val METHOD = "tile"
        /** Int. */
        const val COUNT = "count"
        /** String. */
        const val LINE = "line"
        /** String. */
        const val TITLE = "title"
        /** Boolean, in the answer: whether Zeta took it. */
        const val OK = "ok"
    }

    /** The [android.os.Bundle] a hub app sends with [IZetaHost.command]. */
    object Command {
        const val KIND = "kind"
        const val TEXT = "text"
        const val URL = "url"

        /** A short note at the top of the screen: [TEXT]. */
        const val TOAST = "toast"
        /** A page on the web, in Zeta's internet hub: [URL], https only. */
        const val OPEN_URL = "openUrl"
        /** The system's share sheet: [TEXT]. */
        const val SHARE = "share"
        /** Close the hub, as Back on its first page would. */
        const val CLOSE_HUB = "closeHub"
    }
}
