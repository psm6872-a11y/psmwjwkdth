package com.example.danallacalendar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlacklistItem): Long

    @Query("SELECT * FROM blacklist_items ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<BlacklistItem>>

    @Query("SELECT * FROM blacklist_items ORDER BY createdAt DESC")
    suspend fun getAllList(): List<BlacklistItem>

    @Delete
    suspend fun delete(item: BlacklistItem)
}
