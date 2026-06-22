package com.example.danallacalendar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashItem): Long

    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun getAllTrashItemsFlow(): Flow<List<TrashItem>>

    @Delete
    suspend fun deleteTrashItem(item: TrashItem)

    @Query("DELETE FROM trash_items WHERE deletedAt <= :threshold")
    suspend fun deleteExpiredItems(threshold: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clearAllTrashItems()
}
