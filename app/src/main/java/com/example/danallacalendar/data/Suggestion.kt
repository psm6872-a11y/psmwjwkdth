package com.example.danallacalendar.data

data class Suggestion(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorId: String = "",
    val authorNickname: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reportedByUserIds: List<String> = emptyList(),
    val isReported: Boolean = false
)

data class SuggestionComment(
    val id: String = "",
    val content: String = "",
    val authorId: String = "",
    val authorNickname: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reportedByUserIds: List<String> = emptyList(),
    val isReported: Boolean = false
)
