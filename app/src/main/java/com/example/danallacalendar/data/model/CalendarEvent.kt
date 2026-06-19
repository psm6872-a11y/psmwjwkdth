package com.example.danallacalendar.data.model

import com.google.firebase.Timestamp

data class CalendarEvent(
    val id: String = "",
    val title: String = "",
    val date: String = "",         // Format: "2026-05-30"
    val time: String = "",         // Format: "14:00"
    val description: String = "",
    val createdBy: String = "",    // Device UUID
    val createdByName: String = "",// Nickname
    val updatedAt: Timestamp? = null,
    val teamId: Int? = null,        // 팀 번호 (1~5, null이면 미배정)
    val slotPosition: String? = null  // "top"(오전) / "bottom"(오후) / "both"(오전+오후)
)
