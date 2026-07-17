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

data class UserReport(
    val id: String = "",
    val reportedUserId: String = "",
    val reportedUserNickname: String = "",
    val reportedBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reason: String = "부적절한 닉네임 및 악성 게시물 도배"
)
