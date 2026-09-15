package com.serkantkn.zunelauncher

import android.provider.CallLog
import android.telecom.Call
import com.serkantkn.zunelauncher.data.model.CallLogModel
import com.serkantkn.zunelauncher.data.model.SpeedDialEntry
import com.serkantkn.zunelauncher.data.service.CallManager
import com.serkantkn.zunelauncher.data.service.CallStatus
import com.serkantkn.zunelauncher.util.PhoneNumbers
import com.serkantkn.zunelauncher.util.decodeLineKey
import com.serkantkn.zunelauncher.util.encodeLineKey
import com.serkantkn.zunelauncher.util.groupCalls
import com.serkantkn.zunelauncher.util.isMissedType
import com.serkantkn.zunelauncher.util.missedCalls
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numbers, the history the phone hub shows, and what Telecom's states mean on screen. */
class PhoneTest {

    // ── Numbers ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun aWrittenNumberIsStrippedDownToWhatCanBeDialled() {
        assertEquals("+905551234567", PhoneNumbers.toDialable("+90 (555) 123 45 67"))
        assertEquals("*10125551234", PhoneNumbers.toDialable("*101#".dropLast(1) + "25551234"))
        assertEquals("5551234,123", PhoneNumbers.toDialable("555-1234,123"))
    }

    @Test
    fun theSameLineWrittenAnyWayIsTheSameLine() {
        assertTrue(PhoneNumbers.sameNumber("+90 555 123 45 67", "0555 123 45 67"))
        assertTrue(PhoneNumbers.sameNumber("(555) 123-4567", "5551234567"))
        assertTrue(PhoneNumbers.sameNumber("905551234567", "+90 555 123 45 67"))
    }

    @Test
    fun twoDifferentPeopleAreNotTheSameLine() {
        assertFalse(PhoneNumbers.sameNumber("+90 555 123 45 67", "+90 555 123 45 68"))
        assertFalse(PhoneNumbers.sameNumber("5551234567", "5559876543"))
    }

    @Test
    fun shortNumbersAreComparedWholeRatherThanByTheirTail() {
        assertTrue(PhoneNumbers.sameNumber("112", "112"))
        assertFalse(PhoneNumbers.sameNumber("112", "155"))
    }

    @Test
    fun somethingWithNoDigitsInItFallsBackToPlainComparison() {
        assertTrue(PhoneNumbers.sameNumber("unknown", "unknown "))
        assertFalse(PhoneNumbers.sameNumber("unknown", "private"))
        assertEquals("unknown", PhoneNumbers.matchKey(" unknown "))
    }

    @Test
    fun twoNumbersThatMatchShareAKey() {
        assertEquals(
            PhoneNumbers.matchKey("+90 555 123 45 67"),
            PhoneNumbers.matchKey("0555 123 45 67")
        )
    }

    // ── The history ───────────────────────────────────────────────────────────────────────────

    private var nextId = 0

    private fun call(
        number: String,
        type: Int = CallLog.Calls.OUTGOING_TYPE,
        name: String? = null,
        at: Long = 1_000L
    ) = CallLogModel(
        id = "c${nextId++}",
        name = name,
        number = number,
        dateMillis = at,
        durationSeconds = 0,
        type = type
    )

    @Test
    fun ringingSomeoneThreeTimesIsOneRow() {
        val groups = groupCalls(
            listOf(
                call("5551234567", at = 300),
                call("5551234567", at = 200),
                call("5551234567", at = 100)
            )
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.first().count)
        assertEquals("the newest call dates the row", 300L, groups.first().latestMillis)
    }

    @Test
    fun theSamePersonOnEitherSideOfSomebodyElseStaysTwoRows() {
        val groups = groupCalls(
            listOf(
                call("5551234567", at = 300),
                call("5559876543", at = 200),
                call("5551234567", at = 100)
            )
        )

        assertEquals(3, groups.size)
        assertTrue(groups.all { it.count == 1 })
    }

    @Test
    fun aNumberWrittenTwoWaysIsStillOneRun() {
        val groups = groupCalls(listOf(call("+90 555 123 45 67"), call("0555 123 45 67")))

        assertEquals(1, groups.size)
        assertEquals(2, groups.first().count)
    }

    @Test
    fun aRowTakesTheNameFromWhicheverCallHadOne() {
        val groups = groupCalls(
            listOf(call("5551234567", name = null), call("5551234567", name = "Ahmet"))
        )

        assertEquals("Ahmet", groups.first().name)
        assertEquals("Ahmet", groups.first().displayName)
    }

    @Test
    fun aRowWithNoNameShowsTheNumber() {
        assertEquals("5551234567", groupCalls(listOf(call("5551234567"))).first().displayName)
    }

    @Test
    fun aRunWithAnUnansweredCallInItIsMarked() {
        val groups = groupCalls(
            listOf(
                call("5551234567", type = CallLog.Calls.OUTGOING_TYPE),
                call("5551234567", type = CallLog.Calls.MISSED_TYPE)
            )
        )

        assertTrue(groups.first().hasMissed)
        assertEquals("the arrow follows the newest call", CallLog.Calls.OUTGOING_TYPE, groups.first().type)
    }

    @Test
    fun anEmptyHistoryGroupsIntoNothing() {
        assertEquals(emptyList<Any>(), groupCalls(emptyList()))
    }

    @Test
    fun aRefusedCallCountsAsUnanswered() {
        assertTrue(isMissedType(CallLog.Calls.MISSED_TYPE))
        assertTrue(isMissedType(CallLog.Calls.REJECTED_TYPE))
        assertFalse(isMissedType(CallLog.Calls.INCOMING_TYPE))
        assertFalse(isMissedType(CallLog.Calls.OUTGOING_TYPE))
    }

    @Test
    fun theMissedFilterKeepsOnlyWhatWentUnanswered() {
        val calls = listOf(
            call("1111111", type = CallLog.Calls.MISSED_TYPE),
            call("2222222", type = CallLog.Calls.OUTGOING_TYPE),
            call("3333333", type = CallLog.Calls.REJECTED_TYPE)
        )

        assertEquals(listOf("1111111", "3333333"), missedCalls(calls).map { it.number })
    }

    // ── Telecom's states on screen ────────────────────────────────────────────────────────────

    @Test
    fun everyCallStateMeansSomethingOnScreen() {
        assertEquals(CallStatus.INCOMING, CallManager.statusOf(Call.STATE_RINGING))
        assertEquals(CallStatus.OUTGOING, CallManager.statusOf(Call.STATE_DIALING))
        assertEquals(CallStatus.OUTGOING, CallManager.statusOf(Call.STATE_CONNECTING))
        assertEquals(CallStatus.ACTIVE, CallManager.statusOf(Call.STATE_ACTIVE))
        assertEquals("a parked call is still a call", CallStatus.ACTIVE, CallManager.statusOf(Call.STATE_HOLDING))
    }

    @Test
    fun aCallThatIsOverHasNothingToShow() {
        assertNull(CallManager.statusOf(Call.STATE_DISCONNECTED))
        assertNull(CallManager.statusOf(Call.STATE_DISCONNECTING))
    }

    @Test
    fun aCallIsTimedFromWhenItConnected() {
        assertEquals("00:00", CallManager.formatDuration(0))
        assertEquals("00:09", CallManager.formatDuration(9))
        assertEquals("01:05", CallManager.formatDuration(65))
        assertEquals("75:00", CallManager.formatDuration(4500))
    }

    // ── Which SIM a call goes out on ──────────────────────────────────────────────────────────

    @Test
    fun aChosenLineSurvivesBeingWrittenDownAndReadBack() {
        val key = encodeLineKey("com.android.phone/.TelephonyConnectionService", "1")

        assertEquals(
            "com.android.phone/.TelephonyConnectionService" to "1",
            decodeLineKey(key)
        )
    }

    @Test
    fun aLineWhoseIdContainsTheSeparatorStillReadsBackWhole() {
        // A flattened component name never contains a bar, but an account id may, so the split
        // belongs at the first one rather than the last.
        val key = encodeLineKey("com.carrier/.Service", "sub|2")

        assertEquals("com.carrier/.Service" to "sub|2", decodeLineKey(key))
    }

    @Test
    fun somethingThatIsNotALineKeyDecodesToNothing() {
        assertNull(decodeLineKey(""))
        assertNull(decodeLineKey("no-separator-here"))
        assertNull(decodeLineKey("|missing-the-component"))
    }

    // ── Speed dial ────────────────────────────────────────────────────────────────────────────

    @Test
    fun aSpeedDialEntrySurvivesBeingWrittenAndReadBack() {
        val entry = SpeedDialEntry("+90 555 123 45 67", "Ahmet", "content://photo/1")

        val restored = SpeedDialEntry.fromJson(JSONObject(entry.toJson().toString()))

        assertEquals(entry, restored)
    }

    @Test
    fun anEntryWithNoPictureStoresNoneAndReadsBackNone() {
        val stored = SpeedDialEntry("5551234567", "Ayşe").toJson()

        assertFalse(stored.has("photo"))
        assertNull(SpeedDialEntry.fromJson(JSONObject(stored.toString())).photoUri)
    }
}
