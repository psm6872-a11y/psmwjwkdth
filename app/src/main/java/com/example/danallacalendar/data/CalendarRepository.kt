package com.example.danallacalendar.data

import kotlinx.coroutines.flow.Flow

class CalendarRepository(val eventDao: EventDao) {
    fun getAllCategories(): Flow<List<CalendarCategory>> = eventDao.getAllCategories()
    
    suspend fun insertCategory(category: CalendarCategory) = eventDao.insertCategory(category)
    
    suspend fun updateCategory(category: CalendarCategory) = eventDao.updateCategory(category)
    
    fun getEventsInRange(start: Long, end: Long): Flow<List<Event>> = eventDao.getEventsInRange(start, end)
    
    fun searchEvents(query: String): Flow<List<Event>> = eventDao.searchEvents("%$query%")
    
    suspend fun getEventById(id: Int): Event? = eventDao.getEventById(id)
    
    suspend fun insertEvent(event: Event) = eventDao.insertEvent(event)
    
    suspend fun updateEvent(event: Event) = eventDao.updateEvent(event)
    
    suspend fun deleteEvent(event: Event) = eventDao.deleteEvent(event)
}
