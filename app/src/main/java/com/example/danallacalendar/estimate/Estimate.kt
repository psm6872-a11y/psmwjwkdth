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
    val visitDate: String = "",
    val moveInfo: String = "",
    val totalVolume: String = "",
    val workersM: String = "",
    val workersF: String = "",
    val laddersStartFloor: String = "",
    val laddersStartCost: String = "",
    val laddersEndFloor: String = "",
    val laddersEndCost: String = "",
    val extraTruck: String = "",
    val moveCost: String = "",
    val totalCost: String = "",
    val deposit: String = "",
    val balance: String = "",
    val optionCost: String = "",
    val roomItems: Map<String, Map<String, Long>> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)
