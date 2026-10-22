package com.serkantkn.zeta.hubkit.protocol

import android.os.Bundle
import android.os.Parcel
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Node
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Prop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HubSpecValidatorTest {

    private fun props(vararg pairs: Pair<String, Any>) = Bundle().apply {
        pairs.forEach { (k, v) ->
            when (v) {
                is String -> putString(k, v)
                is Int -> putInt(k, v)
                is Boolean -> putBoolean(k, v)
            }
        }
    }

    private fun hub(vararg children: HubNode) =
        HubNode(Node.HUB, "hub", props(Prop.TITLE to "to-do"), children.toList())

    private fun page(key: String, vararg children: HubNode) =
        HubNode(Node.PAGE, key, props(Prop.TITLE to key), children.toList())

    @Test
    fun `a sound hub has nothing wrong`() {
        val spec = hub(
            page("open", HubNode(Node.ITEM, "a", props(Prop.TITLE to "milk", Prop.IMAGE to "https://example.com/a.png"))),
            HubNode(Node.BAR, "bar", Bundle(), listOf(HubNode(Node.ACTION, "add", props(Prop.LABEL to "add", Prop.ICON to "add"))))
        )
        assertEquals(emptyList<HubSpecValidator.Problem>(), HubSpecValidator.check(spec))
    }

    @Test
    fun `a picture from a file the app has not shared is an error`() {
        val spec = hub(page("p", HubNode(Node.ITEM, "a", props(Prop.TITLE to "x", Prop.IMAGE to "file:///sdcard/a.png"))))
        val problems = HubSpecValidator.check(spec)
        assertTrue(problems.single().error)
        assertEquals("a", problems.single().key)
    }

    @Test
    fun `the same key twice is a warning`() {
        val spec = hub(page("p", HubNode(Node.ITEM, "a", props(Prop.TITLE to "x")), HubNode(Node.ITEM, "a", props(Prop.TITLE to "y"))))
        assertTrue(HubSpecValidator.check(spec).any { it.key == "a" && !it.error })
    }

    @Test
    fun `an unknown kind and an unknown glyph are warnings`() {
        val spec = hub(
            page("p", HubNode("map", "m")),
            HubNode(Node.BAR, "bar", Bundle(), listOf(HubNode(Node.ACTION, "x", props(Prop.LABEL to "x", Prop.ICON to "rocket"))))
        )
        val problems = HubSpecValidator.check(spec)
        assertTrue(problems.any { it.key == "m" && !it.error })
        assertTrue(problems.any { it.key == "x" && !it.error })
    }

    @Test
    fun `too many parts is an error`() {
        val items = (0..HubSpecValidator.MAX_NODES).map { HubNode(Node.ITEM, "i$it", props(Prop.TITLE to "t")) }
        val spec = hub(page("p", *items.toTypedArray()))
        assertTrue(HubSpecValidator.check(spec).any { it.error && it.key == "hub" })
    }

    @Test
    fun `a description crosses the binder whole`() {
        val spec = hub(page("p", HubNode(Node.ITEM, "a", props(Prop.TITLE to "milk", Prop.COUNT to 3))))
        val parcel = Parcel.obtain()
        try {
            spec.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val back = HubNode.CREATOR.createFromParcel(parcel)
            assertEquals("milk", back.childOf(Node.PAGE)!!.children.single().string(Prop.TITLE))
            assertEquals(3, back.childOf(Node.PAGE)!!.children.single().int(Prop.COUNT))
        } finally {
            parcel.recycle()
        }
    }
}
