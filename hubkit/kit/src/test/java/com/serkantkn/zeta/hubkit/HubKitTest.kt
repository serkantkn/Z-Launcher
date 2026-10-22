package com.serkantkn.zeta.hubkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HubKitTest {

    class ListHub : ZetaHubService() {
        var items = listOf("milk", "bread", "milk")
        override fun hub(): HubSpec = zetaHub(title = "shopping") {
            page("list") {
                section("today")
                list(items, key = { it }) { item(it, opens = { subPage(it) { text(it) } }) }
                // Two lines without keys of their own: still told apart.
                item("first")
                item("second")
            }
            bar {
                action("add", HubIcon.ADD) {}
                menu("clear") {}
            }
            liveTile { count = items.size }
        }
    }

    private fun hub() = Robolectric.buildService(ListHub::class.java).create().get()

    @Test
    fun `a sound hub validates, even with an element twice in a keyed list`() {
        // "milk" twice gets two keys of its own, so touches still go to the right line.
        assertEquals(emptyList<Any>(), hub().validate())
    }

    @Test
    fun `a picture that is not shared is caught`() {
        val h = object : ZetaHubService() {
            override fun hub() = zetaHub("x") {
                page("p") { item("a", image = android.net.Uri.parse("file:///sdcard/a.png")) }
            }
        }
        val service = Robolectric.buildService(h.javaClass).create().get()
        assertTrue(service.validate().single().error)
    }
}
