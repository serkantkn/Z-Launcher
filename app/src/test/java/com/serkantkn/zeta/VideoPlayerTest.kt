package com.serkantkn.zeta

import com.serkantkn.zeta.ui.screens.video.NaturalOrder
import com.serkantkn.zeta.ui.screens.video.VideoPlayer
import com.serkantkn.zeta.ui.screens.video.formatTime
import com.serkantkn.zeta.ui.screens.video.VideoQueue
import com.serkantkn.zeta.ui.screens.video.sidecarSubtitles
import com.serkantkn.zeta.ui.screens.video.speedLabel
import com.serkantkn.zeta.ui.screens.video.subtitleLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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

    @Test
    fun episodesFollowInTheOrderTheyAreNumbered() {
        val names = listOf("Bölüm 10.mp4", "bölüm 2.mp4", "Bölüm 1.mp4", "bölüm 02b.mp4", "Ara.mp4")
        assertEquals(
            listOf("Ara.mp4", "Bölüm 1.mp4", "bölüm 2.mp4", "bölüm 02b.mp4", "Bölüm 10.mp4"),
            names.sortedWith(NaturalOrder)
        )
    }

    @Test
    fun seasonsAndEpisodesCompareNumberByNumber() {
        val names = listOf("Dizi S02E01.mkv", "Dizi S01E10.mkv", "Dizi S01E09.mkv")
        assertEquals(listOf("Dizi S01E09.mkv", "Dizi S01E10.mkv", "Dizi S02E01.mkv"), names.sortedWith(NaturalOrder))
    }

    @Test
    fun onlyVideosJoinTheQueue() {
        assertTrue(VideoQueue.isVideoName("tatil.MP4"))
        assertTrue(VideoQueue.isVideoName("film.mkv"))
        assertFalse(VideoQueue.isVideoName("film.srt"))
        assertFalse(VideoQueue.isVideoName("kapak.jpg"))
    }

    @Test
    fun subtitlesBesideAFilmAreFoundByItsName() {
        val folder = listOf("film.mp4", "film.srt", "film.en.srt", "film.tr.srt", "baska.srt", "film2.srt", "film.nfo")
        assertEquals(listOf("film.tr.srt", "film.en.srt", "film.srt"), sidecarSubtitles("film.mp4", folder, language = "tr"))
        assertEquals(listOf("film.en.srt", "film.srt", "film.tr.srt"), sidecarSubtitles("film.mp4", folder, language = "en"))
    }

    @Test
    fun aSubtitleNameCanSayItsLanguage() {
        assertEquals("tr", subtitleLanguage("film.mp4", "film.tr.srt"))
        assertEquals("eng", subtitleLanguage("film.mp4", "film.ENG.srt"))
        assertNull(subtitleLanguage("film.mp4", "film.srt"))
        assertNull(subtitleLanguage("film.mp4", "film.forced.srt"))
    }

    @Test
    fun speedsAreWrittenTheWayTheLanguageWritesDecimals() {
        assertEquals("1×", speedLabel(1f, Locale.ENGLISH))
        assertEquals("1.25×", speedLabel(1.25f, Locale.ENGLISH))
        assertEquals("0,5×", speedLabel(0.5f, Locale.forLanguageTag("tr")))
    }
}
