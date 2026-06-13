package com.example.danallacalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "estimate_pdfs")
data class EstimatePdf(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,          // MM-dd 형식의 날짜
    val fileName: String,      // 예: 06-10_0018.jpg
    val filePath: String,      // 내부 저장 절대 경로
    val createdAt: Long = System.currentTimeMillis(),
    
    // 추가 필드 (오프라인 수동 공유 및 목록 표시용)
    val estimateId: String = "",
    val customerName: String = "",
    val phoneNumber: String = "",
    val moveDate: String = "",
    val departure: String = "",
    val estimateJson: String = "", // 전체 Estimate 직렬화 JSON
    val isSynced: Boolean = false  // 파이어베이스 동기화 완료 여부
)
