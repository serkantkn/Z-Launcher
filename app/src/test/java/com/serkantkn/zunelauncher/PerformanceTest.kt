package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.WallpaperImages
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind the launcher's footprint: how much of a wallpaper is worth decoding.
 */
class PerformanceTest {

    @Test
    fun `a wallpaper the size of the screen is read whole`() {
        assertEquals(1, WallpaperImages.sampleSizeFor(1080, 2400, 1080, 2400))
    }

    @Test
    fun `a wallpaper twice the screen is read at half`() {
        assertEquals(2, WallpaperImages.sampleSizeFor(2160, 4800, 1080, 2400))
    }

    @Test
    fun `a picture far larger than the screen still covers it`() {
        val sample = WallpaperImages.sampleSizeFor(4320, 9600, 1080, 2400)
        assertEquals(4, sample)
        // What is left is never smaller than the screen it has to fill.
        assertEquals(1080, 4320 / sample)
    }

    @Test
    fun `a wallpaper wider than the screen but no taller is read whole`() {
        // Sliding wallpapers are wide, not tall; halving would leave the height short.
        assertEquals(1, WallpaperImages.sampleSizeFor(2160, 2400, 1080, 2400))
    }

    @Test
    fun `nonsense sizes ask for no scaling rather than dividing by zero`() {
        assertEquals(1, WallpaperImages.sampleSizeFor(0, 0, 1080, 2400))
        assertEquals(1, WallpaperImages.sampleSizeFor(1080, 2400, 0, 0))
    }
}
