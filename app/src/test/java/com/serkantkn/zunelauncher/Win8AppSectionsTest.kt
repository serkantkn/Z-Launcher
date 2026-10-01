package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.ui.screens.apps.Win8AppsSort
import com.serkantkn.zunelauncher.ui.screens.apps.Win8DateBucket
import com.serkantkn.zunelauncher.ui.screens.apps.buildWin8AppSections
import com.serkantkn.zunelauncher.ui.screens.apps.columnsOf
import com.serkantkn.zunelauncher.ui.screens.apps.dateBucketOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Win8AppSectionsTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100L * day
    private val startOfToday = now - 6L * 60 * 60 * 1000  // six hours into the day

    private fun app(name: String, installed: Long = 0L) = AppInfo(name.lowercase(), name, "", installed)

    private val grouped = mapOf(
        'A' to listOf(app("Ahmet", now - 2 * day), app("Ayşe", now - 1 * 60 * 60 * 1000)),
        'B' to listOf(app("Bora", now - 20 * day)),
        'C' to listOf(app("Cem"))
    )

    @Test
    fun `by name keeps the alphabetical groups as they are`() {
        val sections = buildWin8AppSections(Win8AppsSort.BY_NAME, grouped, emptyMap(), now, startOfToday)
        assertEquals(listOf('A', 'B', 'C'), sections.map { it.letter })
        assertEquals(listOf("Ahmet", "Ayşe"), sections[0].apps.map { it.label })
        assertNull(sections[0].bucket)
    }

    @Test
    fun `by date buckets installs and puts the newest first in each`() {
        val sections = buildWin8AppSections(Win8AppsSort.BY_DATE, grouped, emptyMap(), now, startOfToday)
        assertEquals(
            listOf(Win8DateBucket.TODAY, Win8DateBucket.THIS_WEEK, Win8DateBucket.THIS_MONTH, Win8DateBucket.EARLIER),
            sections.map { it.bucket }
        )
        assertEquals(listOf("Ayşe"), sections[0].apps.map { it.label })
        assertEquals(listOf("Ahmet"), sections[1].apps.map { it.label })
        assertEquals(listOf("Bora"), sections[2].apps.map { it.label })
        // An app the phone will not date is simply old.
        assertEquals(listOf("Cem"), sections[3].apps.map { it.label })
    }

    @Test
    fun `by date leaves out a bucket nothing falls in`() {
        val onlyOld = mapOf('C' to listOf(app("Cem")))
        val sections = buildWin8AppSections(Win8AppsSort.BY_DATE, onlyOld, emptyMap(), now, startOfToday)
        assertEquals(listOf(Win8DateBucket.EARLIER), sections.map { it.bucket })
    }

    @Test
    fun `by usage puts the opened apps first, most used at the top, and the rest after`() {
        val usage = mapOf("bora" to 50L, "ayşe" to 900L)
        val sections = buildWin8AppSections(Win8AppsSort.BY_USAGE, grouped, usage, now, startOfToday)
        assertEquals(listOf(true, false), sections.map { it.usage })
        assertEquals(listOf("Ayşe", "Bora"), sections[0].apps.map { it.label })
        assertEquals(listOf("Ahmet", "Cem"), sections[1].apps.map { it.label })
    }

    @Test
    fun `by usage with nothing known is one section of everything`() {
        val sections = buildWin8AppSections(Win8AppsSort.BY_USAGE, grouped, emptyMap(), now, startOfToday)
        assertEquals(1, sections.size)
        assertEquals(false, sections[0].usage)
        assertEquals(4, sections[0].apps.size)
    }

    @Test
    fun `date buckets have the edges Windows drew`() {
        assertEquals(Win8DateBucket.TODAY, dateBucketOf(startOfToday, now, startOfToday))
        assertEquals(Win8DateBucket.THIS_WEEK, dateBucketOf(startOfToday - 1, now, startOfToday))
        assertEquals(Win8DateBucket.THIS_WEEK, dateBucketOf(now - 7 * day, now, startOfToday))
        assertEquals(Win8DateBucket.THIS_MONTH, dateBucketOf(now - 7 * day - 1, now, startOfToday))
        assertEquals(Win8DateBucket.THIS_MONTH, dateBucketOf(now - 30 * day, now, startOfToday))
        assertEquals(Win8DateBucket.EARLIER, dateBucketOf(now - 30 * day - 1, now, startOfToday))
        assertEquals(Win8DateBucket.EARLIER, dateBucketOf(0L, now, startOfToday))
    }

    @Test
    fun `columns read down and then across`() {
        val columns = columnsOf((1..7).toList(), rows = 3)
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7)), columns)
        assertTrue(columnsOf(emptyList<Int>(), rows = 3).isEmpty())
        // A silly row count still stands everything in one column rather than failing.
        assertEquals(listOf(listOf(1), listOf(2)), columnsOf(listOf(1, 2), rows = 0))
    }
}
