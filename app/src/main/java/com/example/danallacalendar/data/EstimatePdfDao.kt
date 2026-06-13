package com.example.danallacalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EstimatePdfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: EstimatePdf)

    @Query("SELECT * FROM estimate_pdfs ORDER BY createdAt DESC")
    fun getAllPdfs(): Flow<List<EstimatePdf>>

    @Query("DELETE FROM estimate_pdfs WHERE id = :id")
    suspend fun deletePdf(id: Int)

    @Query("UPDATE estimate_pdfs SET isSynced = :isSynced, estimateId = :newId WHERE estimateId = :oldId")
    suspend fun updateSyncStatus(oldId: String, newId: String, isSynced: Boolean)

    @Query("DELETE FROM estimate_pdfs WHERE estimateId = :estimateId")
    suspend fun deleteByEstimateId(estimateId: String)
}
