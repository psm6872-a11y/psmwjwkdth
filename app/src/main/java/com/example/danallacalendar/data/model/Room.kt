package com.example.danallacalendar.data.model

import com.google.firebase.Timestamp

data class Room(
    val createdAt: Timestamp? = null,
    val createdBy: String = "" // Host device UUID
)
