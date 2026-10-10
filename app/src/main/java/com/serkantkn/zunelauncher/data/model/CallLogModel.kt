package com.serkantkn.zunelauncher.data.model

import android.provider.CallLog

data class CallLogModel(
    val id: String,
    val name: String?,
    /** Empty, never null: a withheld number has no digits, and the log says so in [presentation]. */
    val number: String,
    val dateMillis: Long,
    val durationSeconds: Long,
    val type: Int, // CallLog.Calls.INCOMING_TYPE, OUTGOING_TYPE, MISSED_TYPE, REJECTED_TYPE
    /** Whether the caller let their number be seen: CallLog.Calls.PRESENTATION_*. */
    val presentation: Int = CallLog.Calls.PRESENTATION_ALLOWED
) {
    /** A caller who withheld their number, or one the network never had. */
    val isPrivate: Boolean
        get() = presentation != CallLog.Calls.PRESENTATION_ALLOWED || number.isBlank()
}
