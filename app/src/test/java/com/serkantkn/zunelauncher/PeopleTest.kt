package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.util.ContactTouch
import com.serkantkn.zunelauncher.ui.screens.people.cropRectFor
import com.serkantkn.zunelauncher.ui.screens.people.formatBirthday
import com.serkantkn.zunelauncher.util.recentContacts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Who has been in touch lately, and people pinned to the start board. */
class PeopleTest {

    private fun person(id: String, name: String) =
        ContactModel(id, name, null, isFavorite = false, lastTimeContacted = 0, hasPhoneNumber = true)

    private val ayse = person("1", "Ayşe")
    private val mehmet = person("2", "Mehmet")
    private val zeynep = person("3", "Zeynep")
    private val everyone = listOf(ayse, mehmet, zeynep)

    private val numbers = mapOf(
        "1" to listOf("+90 555 111 22 33"),
        "2" to listOf("0532 444 55 66", "+90 216 700 80 90"),
        "3" to listOf("5559998877")
    )

    // ── Recently in touch ─────────────────────────────────────────────────────────────────────

    @Test
    fun theMostRecentlyInTouchComesFirst() {
        val recent = recentContacts(
            everyone,
            numbers,
            listOf(
                ContactTouch("+90 555 111 22 33", 100),
                ContactTouch("0532 444 55 66", 300),
                ContactTouch("5559998877", 200)
            )
        )

        assertEquals(listOf("Mehmet", "Zeynep", "Ayşe"), recent.map { it.name })
    }

    @Test
    fun aNumberWrittenAnotherWayStillFindsItsOwner() {
        // The call log keeps whatever was dialled; the phone book keeps it written out in full.
        val recent = recentContacts(everyone, numbers, listOf(ContactTouch("05551112233", 100)))

        assertEquals(listOf("Ayşe"), recent.map { it.name })
    }

    @Test
    fun anyOfSomebodysNumbersCountsAsTheirs() {
        val recent = recentContacts(everyone, numbers, listOf(ContactTouch("+90 216 700 80 90", 500)))

        assertEquals(listOf("Mehmet"), recent.map { it.name })
    }

    @Test
    fun somebodyReachedTwiceIsListedOnceByTheirLatest() {
        val recent = recentContacts(
            everyone,
            numbers,
            listOf(
                ContactTouch("0532 444 55 66", 100),
                ContactTouch("+90 216 700 80 90", 900),
                ContactTouch("5559998877", 500)
            )
        )

        assertEquals(listOf("Mehmet", "Zeynep"), recent.map { it.name })
    }

    @Test
    fun aNumberNobodyOwnsIsNotSomebody() {
        val recent = recentContacts(everyone, numbers, listOf(ContactTouch("+90 212 000 00 00", 100)))

        assertTrue(recent.isEmpty())
    }

    @Test
    fun nothingInTheCallLogMeansNobodyRecent() {
        assertEquals(emptyList<ContactModel>(), recentContacts(everyone, numbers, emptyList()))
        assertEquals(emptyList<ContactModel>(), recentContacts(emptyList(), numbers, listOf(ContactTouch("1", 1))))
    }

    @Test
    fun theListIsCappedSoTheRecentPageStaysRecent() {
        val many = (1..40).map { person(it.toString(), "Kişi $it") }
        val manyNumbers = (1..40).associate { it.toString() to listOf("555000${"%04d".format(it)}") }
        val touches = (1..40).map { ContactTouch("555000${"%04d".format(it)}", it.toLong()) }

        assertEquals(20, recentContacts(many, manyNumbers, touches).size)
        assertEquals(5, recentContacts(many, manyNumbers, touches, limit = 5).size)
    }

    // ── Framing a contact photo ───────────────────────────────────────────────────────────────

    @Test
    fun anUntouchedWideePictureIsCroppedToItsMiddleSquare() {
        val rect = cropRectFor(1000, 500, windowPx = 400, scale = 1f, offsetX = 0f, offsetY = 0f)

        assertEquals("the full height is used", 500, rect.height)
        assertEquals("and a square of it", 500, rect.width)
        assertEquals("taken from the middle", 250, rect.left)
        assertEquals(0, rect.top)
    }

    @Test
    fun anUntouchedTallPictureIsCroppedToItsMiddleSquare() {
        val rect = cropRectFor(500, 1000, windowPx = 400, scale = 1f, offsetX = 0f, offsetY = 0f)

        assertEquals(500, rect.width)
        assertEquals(500, rect.height)
        assertEquals(0, rect.left)
        assertEquals(250, rect.top)
    }

    @Test
    fun pinchingInTakesLessOfThePicture() {
        val whole = cropRectFor(1000, 1000, 400, scale = 1f, offsetX = 0f, offsetY = 0f)
        val closer = cropRectFor(1000, 1000, 400, scale = 2f, offsetX = 0f, offsetY = 0f)

        assertEquals(1000, whole.width)
        assertEquals("twice the zoom, half the picture", 500, closer.width)
        assertEquals("still centred", 250, closer.left)
    }

    @Test
    fun draggingMovesWhichPartIsTaken() {
        val centred = cropRectFor(1000, 1000, 400, scale = 2f, offsetX = 0f, offsetY = 0f)
        val dragged = cropRectFor(1000, 1000, 400, scale = 2f, offsetX = 100f, offsetY = 0f)

        // Dragging the picture right shows what was to its left.
        assertTrue(dragged.left < centred.left)
        assertEquals(centred.width, dragged.width)
    }

    @Test
    fun draggingPastTheEdgeStopsAtThePicture() {
        val rect = cropRectFor(1000, 1000, 400, scale = 2f, offsetX = 100000f, offsetY = 100000f)

        assertTrue("never before the first pixel", rect.left >= 0)
        assertTrue("never past the last", rect.left + rect.width <= 1000)
        assertTrue("and never empty", rect.width > 0 && rect.height > 0)
    }

    @Test
    fun aPictureWithNoSizeIsNotDividedByZero() {
        val rect = cropRectFor(0, 0, 400, 1f, 0f, 0f)

        assertTrue(rect.width > 0 && rect.height > 0)
    }

    // ── Birthdays ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun aBirthdayIsWrittenOutTheWayPeopleSayIt() {
        assertEquals("3 April 1986", formatBirthday("1986-04-03", Locale.UK))
    }

    @Test
    fun aBirthdayWithNoYearLosesTheYearRatherThanInventingOne() {
        // Contacts really does store "--04-03" for somebody whose year nobody knows.
        assertEquals("3 April", formatBirthday("--04-03", Locale.UK))
    }

    @Test
    fun somethingThatIsNotADateIsLeftAloneRatherThanMangled() {
        assertEquals("her zaman nisanda", formatBirthday("her zaman nisanda", Locale.UK))
        assertEquals("1986-13-45", formatBirthday("1986-13-45", Locale.UK))
        assertEquals("", formatBirthday("", Locale.UK))
    }

    // ── A person pinned to Start ──────────────────────────────────────────────────────────────

    @Test
    fun aPinnedPersonKeepsTheirIdAndTheirName() {
        val tile = StartTileItem.fromPerson("42", "Ayşe Yılmaz")

        assertTrue(tile.isPerson)
        assertEquals("42", tile.contactId)
        assertEquals("Ayşe Yılmaz", tile.name)
    }

    @Test
    fun aPinnedPersonSurvivesBeingWrittenAndReadBack() {
        val tile = StartTileItem.fromPerson("42", "Ayşe Yılmaz", span = 4)

        assertEquals(tile, StartTileItem.fromJson(JSONObject(tile.toJson().toString())))
    }

    @Test
    fun aPinnedPersonIsNotAPinnedWebsite() {
        val person = StartTileItem.fromPerson("42", "Ayşe")
        val site = StartTileItem.fromWeb("https://example.com", "Example")

        assertFalse(person.isWeb)
        assertNull(person.webUrl)
        assertFalse(site.isPerson)
        assertNull(site.contactId)
    }
}
