package com.example.danallacalendar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

import androidx.room.SkipQueryVerification

@Dao
@SkipQueryVerification
interface EventDao {
    // Categories
    @Query("SELECT * FROM calendar_categories")
    fun getAllCategories(): Flow<List<CalendarCategory>>

    @Query("SELECT * FROM calendar_categories")
    suspend fun getAllCategoriesList(): List<CalendarCategory>

    @Query("SELECT COUNT(*) FROM events WHERE calendarId = :categoryId")
    suspend fun getEventCountForCategory(categoryId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CalendarCategory): Long

    @Update
    suspend fun updateCategory(category: CalendarCategory)

    @Query("SELECT * FROM calendar_categories WHERE id = :id")
    suspend fun getCategoryById(id: Int): CalendarCategory?

    // Events
    @Query("""
        SELECT events.* FROM events 
        INNER JOIN calendar_categories ON events.calendarId = calendar_categories.id 
        WHERE calendar_categories.isVisible = 1 
        AND (
            (events.startMillis >= :start AND events.startMillis <= :end) OR 
            (events.endMillis >= :start AND events.endMillis <= :end) OR
            (events.startMillis <= :start AND events.endMillis >= :end)
        )
        ORDER BY events.startMillis ASC
    """)
    fun getEventsInRange(start: Long, end: Long): Flow<List<Event>>

    @Query("""
        SELECT events.* FROM events 
        INNER JOIN calendar_categories ON events.calendarId = calendar_categories.id 
        WHERE calendar_categories.isVisible = 1 
        AND (events.title LIKE :query OR events.notes LIKE :query OR events.location LIKE :query) 
        ORDER BY events.startMillis ASC
    """)
    fun searchEvents(query: String): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: Int): Event?

    @Query("SELECT * FROM events WHERE syncId = :syncId")
    suspend fun getEventBySyncId(syncId: String): Event?

    @Query("SELECT * FROM events WHERE isSynced = 1")
    suspend fun getSyncedEvents(): List<Event>

    @Query("DELETE FROM events WHERE syncId = :syncId")
    suspend fun deleteEventBySyncId(syncId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)

    @Delete
    suspend fun deleteCategories(categories: List<CalendarCategory>)

    @Query("UPDATE events SET calendarId = :newCalendarId WHERE calendarId IN (:oldCalendarIds)")
    suspend fun updateEventsCalendarId(oldCalendarIds: List<Int>, newCalendarId: Int)

    // DeadlineDates
    @Query("SELECT * FROM deadline_dates")
    fun getAllDeadlineDates(): Flow<List<DeadlineDate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeadlineDate(deadlineDate: DeadlineDate)

    @Query("DELETE FROM deadline_dates WHERE dateMillis = :dateMillis")
    suspend fun deleteDeadlineDate(dateMillis: Long)
}

