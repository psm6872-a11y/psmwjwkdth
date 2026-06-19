package com.example.danallacalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "calendar_categories")
@Serializable
data class CalendarCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String,
    val accountName: String,
    val isVisible: Boolean = true
)

@Entity(tableName = "events")
@Serializable
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val location: String = "",
    val notes: String = "",
    val repeatType: String = "NONE", // NONE, DAILY, WEEKLY, MONTHLY, YEARLY
    val reminderMinutes: Int = -1,  // -1 for none, 0, 10, 60, 1440, etc.
    val calendarId: Int,
    val syncId: String? = null,
    val isSynced: Boolean = false,
    val colorHex: String? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val linkedEstimateId: String? = null,
    val teamId: Int? = null,
    val slotPosition: String? = null
)

@Entity(tableName = "deadline_dates")
data class DeadlineDate(
    @PrimaryKey val dateMillis: Long  // 자정 기준으로 정규화된 날짜 millis
)
