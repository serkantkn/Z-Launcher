package com.serkantkn.zeta

import com.serkantkn.zeta.util.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class DeviceInfoTest {

    @Test
    fun theMakerIsWrittenOnceBeforeTheModel() {
        assertEquals("Samsung SM-S721B", DeviceInfo.modelName("samsung", "SM-S721B"))
        assertEquals("Google Pixel 9", DeviceInfo.modelName("Google", "Pixel 9"))
        assertEquals("Xiaomi 14", DeviceInfo.modelName("Xiaomi", "Xiaomi 14"))
    }

    @Test
    fun theRadioOfEachSlotIsListedOnce() {
        assertEquals("S721BXXSEDZI1", DeviceInfo.radioVersion("S721BXXSEDZI1,S721BXXSEDZI1"))
        assertEquals("A1, B2", DeviceInfo.radioVersion("A1, B2"))
        assertNull(DeviceInfo.radioVersion(" , "))
    }

    @Test
    fun theSecurityUpdateIsADateInTheReadersLanguage() {
        assertEquals("5 Eylül 2026", DeviceInfo.patchDate("2026-09-05", Locale.forLanguageTag("tr-TR")))
        assertEquals("September 5, 2026", DeviceInfo.patchDate("2026-09-05", Locale.US))
        assertEquals("not a date", DeviceInfo.patchDate("not a date", Locale.US))
        assertNull(DeviceInfo.patchDate("", Locale.US))
    }
}
