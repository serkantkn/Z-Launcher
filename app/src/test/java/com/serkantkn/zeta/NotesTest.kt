package com.serkantkn.zeta

import com.serkantkn.zeta.data.datastore.splitNotesJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notes store's rules that hold without a phone: what one bad entry may and may not cost. */
class NotesTest {

    @Test
    fun anEmptyStoreIsEmptyAndNotCorrupt() {
        val split = splitNotesJson(null)

        assertTrue(split.entries.isEmpty())
        assertFalse(split.corrupt)
        assertFalse(splitNotesJson("   ").corrupt)
    }

    @Test
    fun oneElementThatIsNotANoteDoesNotTakeTheOthersWithIt() {
        val split = splitNotesJson("""[{"id":"a","title":"one"}, 42, {"id":"b","title":"two"}]""")

        assertEquals(listOf("a", "b"), split.entries.map { it.getString("id") })
        assertEquals(listOf("42"), split.broken)
        assertFalse(split.corrupt)
    }

    @Test
    fun textThatIsNotAnArrayIsReportedRatherThanEmptied() {
        val split = splitNotesJson("""{"id":"a"}""")

        assertTrue(split.corrupt)
        assertTrue(split.entries.isEmpty())
    }
}
