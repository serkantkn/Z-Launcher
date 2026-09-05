package com.serkantkn.zunelauncher.data.model

import java.util.UUID

data class CalendarEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val timestamp: Long,
    val hour: Int = 10,
    val minute: Int = 0,
    val category: String = "Genel",
    val colorHex: String = "#E0007A",
    val linkedNoteId: String? = null
) {
    val formattedTime: String
        get() = String.format("%02d:%02d", hour, minute)
}
