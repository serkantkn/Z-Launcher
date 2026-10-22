package com.serkantkn.zeta

import com.serkantkn.zeta.data.model.ExternalHub
import com.serkantkn.zeta.data.model.HubId
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.model.StartTileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Hub keys are stored everywhere hubs are kept; they must read back exactly. */
class HubIdTest {

    @Test
    fun `a Zeta hub's key is its old enum name`() {
        HubType.entries.forEach { hub ->
            assertEquals(hub.name, hub.key)
            assertEquals(hub, HubId.fromKey(hub.key))
        }
    }

    @Test
    fun `an external hub's key reads back`() {
        val hub = ExternalHub("com.example.parcels", "com.example.parcels.ParcelHub")
        assertEquals("ext:com.example.parcels/com.example.parcels.ParcelHub", hub.key)
        assertEquals(hub, HubId.fromKey(hub.key))
    }

    @Test
    fun `unknown and broken keys read as nothing`() {
        assertNull(HubId.fromKey("GAMES"))
        assertNull(HubId.fromKey("ext:"))
        assertNull(HubId.fromKey("ext:com.example"))
        assertNull(HubId.fromKey("ext:/Cls"))
        assertNull(HubId.fromKey("ext:com.example/"))
    }

    @Test
    fun `tiles name hubs of both kinds`() {
        val external = ExternalHub("com.example.parcels", ".ParcelHub")
        assertEquals(HubType.NOTES, StartTileItem.fromHub(HubType.NOTES).hubId)
        assertEquals(HubType.NOTES, StartTileItem.fromHub(HubType.NOTES).hubType)
        assertEquals("hub:NOTES", StartTileItem.fromHub(HubType.NOTES).id)
        val tile = StartTileItem.fromHub(external)
        assertEquals(external, tile.hubId)
        assertNull(tile.hubType)
    }

    @Test
    fun `external hubs are not offered on a television`() {
        assertFalse(ExternalHub("a.b", "a.b.C").onTelevision)
    }
}
