package com.example.danallacalendar.data

import com.google.firebase.firestore.PropertyName

data class Suggestion(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("content") @set:PropertyName("content") var content: String = "",
    @get:PropertyName("authorId") @set:PropertyName("authorId") var authorId: String = "",
    @get:PropertyName("authorNickname") @set:PropertyName("authorNickname") var authorNickname: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("reportedByUserIds") @set:PropertyName("reportedByUserIds") var reportedByUserIds: List<String> = emptyList(),
    @get:PropertyName("isReported") @set:PropertyName("isReported") var isReported: Boolean = false,
    @get:PropertyName("isAdmin") @set:PropertyName("isAdmin") var isAdmin: Boolean = false,
    @get:PropertyName("isPinned") @set:PropertyName("isPinned") var isPinned: Boolean = false
)

data class SuggestionComment(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("content") @set:PropertyName("content") var content: String = "",
    @get:PropertyName("authorId") @set:PropertyName("authorId") var authorId: String = "",
    @get:PropertyName("authorNickname") @set:PropertyName("authorNickname") var authorNickname: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("reportedByUserIds") @set:PropertyName("reportedByUserIds") var reportedByUserIds: List<String> = emptyList(),
    @get:PropertyName("isReported") @set:PropertyName("isReported") var isReported: Boolean = false,
    @get:PropertyName("isAdmin") @set:PropertyName("isAdmin") var isAdmin: Boolean = false
)

data class UserReport(
    val id: String = "",
    val reportedUserId: String = "",
    val reportedUserNickname: String = "",
    val reportedBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reason: String = "부적절한 닉네임 및 악성 게시물 도배"
)
