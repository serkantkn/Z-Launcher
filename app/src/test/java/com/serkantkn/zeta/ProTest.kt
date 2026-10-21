package com.serkantkn.zeta

import com.serkantkn.zeta.data.datastore.freeChange
import com.serkantkn.zeta.data.datastore.freeLooks
import com.serkantkn.zeta.data.model.TileAnimation
import com.serkantkn.zeta.data.model.TileLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the free build makes of a tile's own look, which is Pro's but for its notifications. */
class ProTest {

    private val dressed = TileLook(color = 0xFF00FF00.toInt(), name = "ev", animation = TileAnimation.FLIP, notifications = false)

    @Test
    fun theFreeBuildSeesOnlyWhetherATileShowsNotifications() {
        val seen = freeLooks(mapOf("app:a" to dressed, "app:b" to TileLook(color = 1)))
        assertEquals(TileLook(notifications = false), seen["app:a"])
        // A look that is all Pro is, to the free build, no look at all.
        assertFalse(seen.containsKey("app:b"))
    }

    @Test
    fun aChangeInTheFreeBuildKeepsWhatWasStoredForPro() {
        val changed = freeChange(dressed, TileLook(notifications = true, color = 5, name = "x"))
        assertEquals(dressed.copy(notifications = true), changed)
    }

    @Test
    fun aResetInTheFreeBuildOnlyTurnsNotificationsBackOn() {
        val reset = freeChange(dressed, TileLook.DEFAULT)
        assertTrue(reset.notifications)
        assertEquals(dressed.color, reset.color)
        assertEquals(dressed.name, reset.name)
    }
}
