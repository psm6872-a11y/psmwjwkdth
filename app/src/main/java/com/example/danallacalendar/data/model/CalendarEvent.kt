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
    val updatedAt: Timestamp? = null
)
