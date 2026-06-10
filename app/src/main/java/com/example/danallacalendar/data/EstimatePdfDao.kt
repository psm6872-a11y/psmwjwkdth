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
}
