package com.serkantkn.zeta.hubkit.protocol

import android.net.Uri
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Node
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Prop

/**
 * The rules a hub's description keeps (PLATFORM.md). Zeta draws what breaks them as well as it
 * can, or leaves it out; this says what will go wrong before it does. ZetaHubKit runs it in a
 * hub's own tests, Zeta's developer mode on every description that arrives.
 */
object HubSpecValidator {

    /** One thing wrong: where ([key]) and what. An [error] is not drawn; the rest only looks off. */
    data class Problem(val key: String, val message: String, val error: Boolean)

    /** More than any hub needs to describe at once. */
    const val MAX_NODES = 4000

    /** More words than any line of a hub can show. */
    const val MAX_TEXT = 2000

    /** The bar shows at most this many commands with glyphs. */
    const val MAX_ACTIONS = 4

    private val ICONS = HubProtocol.Icon::class.java.declaredFields
        .filter { it.type == String::class.java }
        .mapNotNull { runCatching { it.get(null) as String }.getOrNull() }
        .toSet()

    fun check(root: HubNode): List<Problem> {
        val problems = ArrayList<Problem>()
        fun error(node: HubNode, message: String) = problems.add(Problem(node.key, message, true))
        fun warn(node: HubNode, message: String) = problems.add(Problem(node.key, message, false))

        if (root.type != Node.HUB) {
            error(root, "the description must start with a hub, not \"${root.type}\"")
            return problems
        }
        if (root.string(Prop.TITLE).isNullOrBlank()) warn(root, "the hub has no title; Zeta uses the app's label")
        val pages = root.childrenOf(Node.PAGE)
        if (pages.isEmpty() && root.childOf(Node.TAKEOVER) == null) {
            warn(root, "the hub has no pages and no takeover; Zeta says it is empty")
        }
        root.childrenOf(Node.BAR).drop(1).forEach { warn(it, "only the first bar is shown") }
        root.childrenOf(Node.LIVE_TILE).drop(1).forEach { warn(it, "only the first live tile is used") }

        val keys = HashMap<String, Int>()
        var count = 0
        fun walk(node: HubNode, depth: Int) {
            count++
            keys[node.key] = (keys[node.key] ?: 0) + 1
            if (depth > HubNode.MAX_DEPTH) {
                error(node, "nested deeper than ${HubNode.MAX_DEPTH}; what is under it is dropped")
                return
            }
            if (node.type !in Node.V1) warn(node, "\"${node.type}\" is not a kind this protocol knows; Zeta leaves it out")
            when (node.type) {
                Node.PAGE, Node.SUB_PAGE -> if (node.string(Prop.TITLE).isNullOrBlank()) warn(node, "a ${node.type} without a title")
                Node.ITEM -> {
                    if (node.string(Prop.TITLE).isNullOrBlank()) warn(node, "an item without a title")
                    node.string(Prop.IMAGE)?.let { image ->
                        val scheme = runCatching { Uri.parse(image).scheme }.getOrNull()
                        if (scheme != "content" && scheme != "https") {
                            error(node, "an image must be content:// (shared with Zeta) or https://, not \"$scheme\"")
                        }
                    }
                }
                Node.BAR -> if (node.childrenOf(Node.ACTION).size > MAX_ACTIONS) {
                    warn(node, "more than $MAX_ACTIONS bar actions; the rest are not shown")
                }
                Node.ACTION -> {
                    if (node.string(Prop.LABEL).isNullOrBlank()) warn(node, "a bar action without a label")
                    val icon = node.string(Prop.ICON)
                    if (icon == null || icon !in ICONS) warn(node, "\"$icon\" is not one of the glyphs; Zeta draws a plain one")
                }
                Node.TEXT -> if ((node.string(Prop.TEXT)?.length ?: 0) > MAX_TEXT) warn(node, "more than $MAX_TEXT characters of text")
            }
            node.children.forEach { walk(it, depth + 1) }
        }
        walk(root, 0)

        if (count > MAX_NODES) error(root, "$count parts, more than $MAX_NODES: Zeta does not draw it")
        keys.filterValues { it > 1 }.keys.forEach { key ->
            problems.add(Problem(key, "the key is used ${keys[key]} times; touches may go to the wrong one", false))
        }
        return problems
    }
}
