package com.example.danallacalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "estimate_pdfs")
data class EstimatePdf(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,          // MM-dd 형식의 날짜
    val fileName: String,      // 예: 06-10_0018.pdf
    val filePath: String,      // 내부 저장 절대 경로
    val createdAt: Long = System.currentTimeMillis()
)
