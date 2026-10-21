package com.serkantkn.zeta.util

import android.provider.CallLog
import com.serkantkn.zeta.data.model.CallLogModel

/**
 * A run of calls with the same person, collapsed into one line.
 *
 * Ringing someone three times in a row is one thing that happened, not three, and Windows Phone's
 * history said so: one row, the name, and how many times. [count] is that number.
 */
data class CallGroup(
    val id: String,
    val name: String?,
    val number: String,
    val latestMillis: Long,
    val count: Int,
    /** The newest call's direction; what the row's arrow shows. */
    val type: Int,
    /** True when any call in the run went unanswered, which is what colours the row. */
    val hasMissed: Boolean,
    /** The caller withheld their number: the row has nothing to dial and says "private number". */
    val isPrivate: Boolean = false
) {
    /** The name, or the number; a private number has neither and the screen labels it itself. */
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: number
}

/** True for the call types that mean nobody picked up. */
fun isMissedType(type: Int): Boolean =
    type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE

/**
 * Collapses consecutive calls with the same line into single rows.
 *
 * Only *consecutive* calls are collapsed: a call to the same person yesterday and again today,
 * with somebody else in between, stays two rows — otherwise the history stops being a history and
 * becomes a list of people.
 */
fun groupCalls(calls: List<CallLogModel>): List<CallGroup> {
    val groups = mutableListOf<CallGroup>()
    var runKey: String? = null
    var runCalls = mutableListOf<CallLogModel>()

    fun closeRun() {
        val newest = runCalls.firstOrNull() ?: return
        groups += CallGroup(
            id = newest.id,
            name = runCalls.firstNotNullOfOrNull { it.name?.takeIf { name -> name.isNotBlank() } },
            number = newest.number,
            latestMillis = newest.dateMillis,
            count = runCalls.size,
            type = newest.type,
            hasMissed = runCalls.any { isMissedType(it.type) },
            isPrivate = newest.isPrivate
        )
        runCalls = mutableListOf()
    }

    calls.forEach { call ->
        // Withheld numbers cannot be told apart, so a run of them is one row, as it was on WP.
        val key = if (call.isPrivate) PRIVATE_KEY else PhoneNumbers.matchKey(call.number)
        if (key != runKey) {
            closeRun()
            runKey = key
        }
        runCalls += call
    }
    closeRun()
    return groups
}

/** The unanswered calls, newest first: what the hub's missed filter and the tile's badge count. */
fun missedCalls(calls: List<CallLogModel>): List<CallLogModel> = calls.filter { isMissedType(it.type) }

/** The match key shared by every call whose number was withheld. */
private const val PRIVATE_KEY = "private"
