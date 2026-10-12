package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.ui.screens.video.VideoPlayer
import com.serkantkn.zunelauncher.ui.screens.video.formatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerTest {

    @Test
    fun takesVideosAndNothingElse() {
        assertTrue(VideoPlayer.handles("video/mp4"))
        assertTrue(VideoPlayer.handles("VIDEO/Quicktime"))
        // An MMS part's type can carry parameters after it.
        assertTrue(VideoPlayer.handles("video/3gpp; name=clip.3gp"))
        assertFalse(VideoPlayer.handles("audio/mpeg"))
        assertFalse(VideoPlayer.handles("image/jpeg"))
        assertFalse(VideoPlayer.handles(""))
        assertFalse(VideoPlayer.handles(null))
    }

    @Test
    fun timesReadAsAClockDoes() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:09", formatTime(9_999))
        assertEquals("4:05", formatTime(245_000))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("0:00", formatTime(-5))
    }
}
