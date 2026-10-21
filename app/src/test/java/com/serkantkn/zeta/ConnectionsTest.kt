package com.serkantkn.zeta

import com.serkantkn.zeta.data.repository.roundToApproximate
import com.serkantkn.zeta.util.Connections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionsTest {

    @Test
    fun aNetworksNameLosesItsQuotes() {
        assertEquals("Home", Connections.cleanSsid("\"Home\""))
        assertEquals("Ev Ağı 5G", Connections.cleanSsid("\"Ev Ağı 5G\""))
    }

    @Test
    fun aNameAndroidKeepsBackIsNoName() {
        assertNull(Connections.cleanSsid("<unknown ssid>"))
        assertNull(Connections.cleanSsid("\"\""))
        assertNull(Connections.cleanSsid(null))
    }

    @Test
    fun theWeatherIsGivenAnApproximatePositionOnly() {
        assertEquals(41.03, roundToApproximate(41.034567), 1e-9)
        assertEquals(28.68, roundToApproximate(28.676543), 1e-9)
        assertEquals(-12.35, roundToApproximate(-12.3456), 1e-9)
    }
}
