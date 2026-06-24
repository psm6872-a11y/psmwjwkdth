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

    @Query("SELECT * FROM blacklist_items WHERE syncId = :syncId LIMIT 1")
    suspend fun getBlacklistItemsBySyncId(syncId: String): List<BlacklistItem>

    @Query("SELECT * FROM blacklist_items WHERE isSynced = 1")
    suspend fun getSyncedBlacklistItems(): List<BlacklistItem>

    @Query("DELETE FROM blacklist_items WHERE isSynced = 1")
    suspend fun deleteSyncedItems()

    @Query("DELETE FROM blacklist_items WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String)
}
