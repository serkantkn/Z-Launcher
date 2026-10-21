package com.serkantkn.zeta

import com.serkantkn.zeta.data.service.ShadeDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeDetectorTest {

    @Test
    fun `the shade as Android 16 reports it is caught`() {
        assertTrue(
            ShadeDetector.isShade(
                "android.widget.FrameLayout",
                listOf("Notification shade.", "No notifications", "10:28"),
                null
            )
        )
        assertTrue(ShadeDetector.isShade("android.widget.FrameLayout", listOf("Notification shade."), null))
    }

    @Test
    fun `the shade in Turkish is caught`() {
        assertTrue(ShadeDetector.isShade("android.widget.FrameLayout", listOf("Bildirim gölgesi."), null))
        assertTrue(ShadeDetector.isShade(null, emptyList(), "Hızlı ayarlar"))
    }

    @Test
    fun `other SystemUI windows are left alone`() {
        assertFalse(ShadeDetector.isShade("com.android.systemui.globalactions.GlobalActionsDialogLite", listOf("Power off"), null))
        assertFalse(ShadeDetector.isShade("android.widget.FrameLayout", listOf("Screenshot saved"), null))
        assertFalse(ShadeDetector.isShade("android.app.Dialog", listOf("Volume"), null))
        assertFalse(ShadeDetector.isShade(null, null, null))
    }

    @Test
    fun `only a shade that reports what is in it counts as opening`() {
        // What Samsung's SystemUI reported as the lock screen went away on unlocking.
        assertFalse(ShadeDetector.hasContent(listOf("Bildirim gölgesi.")))
        assertFalse(ShadeDetector.hasContent(listOf("Notification shade.", " ")))
        assertFalse(ShadeDetector.hasContent(null))
        // A shade really coming down, as the tablet reported it.
        assertTrue(ShadeDetector.hasContent(listOf("Bildirim gölgesi.", "09:53", "11 Ekim Pzr", "Ayarlar")))
        assertTrue(ShadeDetector.hasContent(listOf("Notification shade.", "No notifications", "10:28")))
    }
}
