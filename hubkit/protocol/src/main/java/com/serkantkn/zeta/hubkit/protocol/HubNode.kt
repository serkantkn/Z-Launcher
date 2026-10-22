package com.serkantkn.zeta.hubkit.protocol

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

/**
 * One piece of a hub as a hub app describes it: what it is ([type], one of [HubProtocol.Node]),
 * a [key] that stays the same while it means the same thing (events come back by it), what it
 * says ([props], names in [HubProtocol.Prop]) and what it holds ([children]).
 *
 * Nothing in it runs: it is words, numbers and addresses of pictures. Zeta decides how each kind
 * looks on a phone, a tablet and in each of its designs.
 */
class HubNode(
    val type: String,
    val key: String,
    val props: Bundle = Bundle.EMPTY,
    val children: List<HubNode> = emptyList()
) : Parcelable {

    fun string(name: String): String? = props.getString(name)
    fun int(name: String, default: Int = 0): Int = props.getInt(name, default)
    fun boolean(name: String, default: Boolean = false): Boolean = props.getBoolean(name, default)

    /** The children of one kind, in order. */
    fun childrenOf(type: String): List<HubNode> = children.filter { it.type == type }

    /** The first child of one kind. */
    fun childOf(type: String): HubNode? = children.firstOrNull { it.type == type }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(type)
        dest.writeString(key)
        dest.writeBundle(props)
        dest.writeInt(children.size)
        children.forEach { it.writeToParcel(dest, flags) }
    }

    override fun toString(): String = "HubNode($type, $key, ${children.size} children)"

    companion object {
        /** The deepest a tree may go; deeper is cut, so a mistake cannot exhaust Zeta's stack. */
        const val MAX_DEPTH = 32

        @JvmField
        val CREATOR: Parcelable.Creator<HubNode> = object : Parcelable.Creator<HubNode> {
            override fun createFromParcel(source: Parcel): HubNode = read(source, 0)
            override fun newArray(size: Int): Array<HubNode?> = arrayOfNulls(size)
        }

        private fun read(source: Parcel, depth: Int): HubNode {
            val type = source.readString().orEmpty()
            val key = source.readString().orEmpty()
            val props = source.readBundle(HubNode::class.java.classLoader) ?: Bundle.EMPTY
            val count = source.readInt().coerceAtLeast(0)
            val children = ArrayList<HubNode>(count.coerceAtMost(1024))
            repeat(count) { children.add(read(source, depth + 1)) }
            return HubNode(type, key, props, if (depth >= MAX_DEPTH) emptyList() else children)
        }
    }
}
