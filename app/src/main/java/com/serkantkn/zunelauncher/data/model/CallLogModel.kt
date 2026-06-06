package com.serkantkn.zunelauncher.data.model

data class CallLogModel(
    val id: String,
    val name: String?,
    val number: String,
    val dateMillis: Long,
    val durationSeconds: Long,
    val type: Int // CallLog.Calls.INCOMING_TYPE, OUTGOING_TYPE, MISSED_TYPE, REJECTED_TYPE
)
