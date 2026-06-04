package com.example.danallacalendar.estimate

data class Estimate(
    val id: String = "",
    val customerName: String = "",
    val phoneNumber: String = "",
    val departure: String = "",
    val destination: String = "",
    val moveDate: String = "",
    val moveType: String = "가정이사", // 가정이사, 사무실이사, 원룸이사
    val cargoSize: String = "중", // 소, 중, 대
    val amount: Long = 0L,
    val memo: String = "",
    val estimateDate: String = "",
    val startTime: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
