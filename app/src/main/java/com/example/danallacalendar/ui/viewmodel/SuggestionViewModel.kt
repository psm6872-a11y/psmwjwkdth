package com.example.danallacalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.Suggestion
import com.example.danallacalendar.data.SuggestionComment
import com.example.danallacalendar.data.UserReport
import com.example.danallacalendar.data.local.UserPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SuggestionViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    private val _comments = MutableStateFlow<List<SuggestionComment>>(emptyList())
    val comments: StateFlow<List<SuggestionComment>> = _comments.asStateFlow()

    private val _blockedUsers = MutableStateFlow<Set<String>>(emptySet())
    val blockedUsers: StateFlow<Set<String>> = _blockedUsers.asStateFlow()

    private var commentsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        loadBlockedUsers()
        observeSuggestions()
    }

    fun loadBlockedUsers() {
        _blockedUsers.value = userPreferences.getBlockedUserIds()
    }

    private fun observeSuggestions() {
        firestore.collection("suggestions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("SuggestionViewModel", "Error listening to suggestions", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        parseSuggestion(doc)
                    }.sortedWith(compareByDescending<Suggestion> { it.isPinned }.thenByDescending { it.createdAt })
                    _suggestions.value = list
                }
            }
    }

    private fun parseSuggestion(doc: com.google.firebase.firestore.DocumentSnapshot): Suggestion? {
        return try {
            val s = doc.toObject(Suggestion::class.java)
            if (s != null && s.title.isNotBlank()) {
                if (s.id.isBlank()) s.id = doc.id
                s
            } else {
                parseSuggestionFallback(doc)
            }
        } catch (e: Exception) {
            android.util.Log.e("SuggestionViewModel", "Failed to parse suggestion doc ${doc.id}, using fallback", e)
            parseSuggestionFallback(doc)
        }
    }

    private fun parseSuggestionFallback(doc: com.google.firebase.firestore.DocumentSnapshot): Suggestion? {
        return try {
            val title = doc.getString("title") ?: ""
            val content = doc.getString("content") ?: ""
            val authorId = doc.getString("authorId") ?: ""
            val authorNickname = doc.getString("authorNickname") ?: "익명"
            val createdAt = doc.getLong("createdAt") ?: (doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis())
            val isReported = doc.getBoolean("isReported") ?: doc.getBoolean("reported") ?: false
            val isAdmin = doc.getBoolean("isAdmin") ?: doc.getBoolean("admin") ?: false
            val isPinned = doc.getBoolean("isPinned") ?: doc.getBoolean("pinned") ?: false
            val reportedByUserIds = (doc.get("reportedByUserIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            Suggestion(
                id = doc.id,
                title = title,
                content = content,
                authorId = authorId,
                authorNickname = authorNickname,
                createdAt = createdAt,
                reportedByUserIds = reportedByUserIds,
                isReported = isReported,
                isAdmin = isAdmin,
                isPinned = isPinned
            )
        } catch (e: Exception) {
            android.util.Log.e("SuggestionViewModel", "Fallback parse failed for doc ${doc.id}", e)
            null
        }
    }

    fun observeComments(suggestionId: String) {
        commentsListenerRegistration?.remove()
        commentsListenerRegistration = firestore.collection("suggestions")
            .document(suggestionId)
            .collection("comments")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("SuggestionViewModel", "Error listening to comments", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        parseComment(doc)
                    }.sortedBy { it.createdAt }
                    _comments.value = list
                }
            }
    }

    private fun parseComment(doc: com.google.firebase.firestore.DocumentSnapshot): SuggestionComment? {
        return try {
            val c = doc.toObject(SuggestionComment::class.java)
            if (c != null && c.content.isNotBlank()) {
                if (c.id.isBlank()) c.id = doc.id
                c
            } else {
                parseCommentFallback(doc)
            }
        } catch (e: Exception) {
            android.util.Log.e("SuggestionViewModel", "Failed to parse comment doc ${doc.id}, using fallback", e)
            parseCommentFallback(doc)
        }
    }

    private fun parseCommentFallback(doc: com.google.firebase.firestore.DocumentSnapshot): SuggestionComment? {
        return try {
            val id = doc.getString("id")?.ifBlank { doc.id } ?: doc.id
            val content = doc.getString("content") ?: ""
            val authorId = doc.getString("authorId") ?: ""
            val authorNickname = doc.getString("authorNickname") ?: "익명"
            val createdAt = doc.getLong("createdAt") ?: (doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis())
            val isReported = doc.getBoolean("isReported") ?: doc.getBoolean("reported") ?: false
            val isAdmin = doc.getBoolean("isAdmin") ?: doc.getBoolean("admin") ?: false
            val reportedByUserIds = (doc.get("reportedByUserIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            SuggestionComment(
                id = id,
                content = content,
                authorId = authorId,
                authorNickname = authorNickname,
                createdAt = createdAt,
                reportedByUserIds = reportedByUserIds,
                isReported = isReported,
                isAdmin = isAdmin
            )
        } catch (e: Exception) {
            android.util.Log.e("SuggestionViewModel", "Fallback parse failed for comment doc ${doc.id}", e)
            null
        }
    }

    fun stopObservingComments() {
        commentsListenerRegistration?.remove()
        commentsListenerRegistration = null
        _comments.value = emptyList()
    }

    fun addSuggestion(title: String, content: String, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ref = firestore.collection("suggestions").document()
                val isAdmin = userPreferences.isAdmin()
                val authorNick = userPreferences.getNickname().ifBlank { if (isAdmin) "관리자" else "익명" }
                val suggestion = Suggestion(
                    id = ref.id,
                    title = title,
                    content = content,
                    authorId = userPreferences.getDeviceUUID(),
                    authorNickname = authorNick,
                    createdAt = System.currentTimeMillis(),
                    isAdmin = isAdmin
                )
                ref.set(suggestion).await()

                // 로컬 리스트에 즉시 추가 (네트워크 지연이나 렌더링 타이밍 이슈 방지)
                val currentList = _suggestions.value.toMutableList()
                currentList.removeAll { it.id == suggestion.id }
                currentList.add(0, suggestion)
                _suggestions.value = currentList

                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to add suggestion", e)
                onError(e.message ?: "건의사항 등록에 실패했습니다.")
            }
        }
    }

    fun addComment(suggestionId: String, content: String, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ref = firestore.collection("suggestions")
                    .document(suggestionId)
                    .collection("comments")
                    .document()
                val isAdmin = userPreferences.isAdmin()
                val authorNick = userPreferences.getNickname().ifBlank { if (isAdmin) "관리자" else "익명" }
                val comment = SuggestionComment(
                    id = ref.id,
                    content = content,
                    authorId = userPreferences.getDeviceUUID(),
                    authorNickname = authorNick,
                    createdAt = System.currentTimeMillis(),
                    isAdmin = isAdmin
                )
                ref.set(comment).await()

                // 로컬 댓글 리스트에 즉시 추가
                val currentComments = _comments.value.toMutableList()
                currentComments.removeAll { it.id == comment.id }
                currentComments.add(comment)
                _comments.value = currentComments

                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to add comment", e)
                onError(e.message ?: "댓글 등록에 실패했습니다.")
            }
        }
    }

    fun deleteSuggestion(suggestionId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                firestore.collection("suggestions").document(suggestionId).delete().await()
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to delete suggestion", e)
            }
        }
    }

    fun deleteComment(suggestionId: String, commentId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("suggestions")
                    .document(suggestionId)
                    .collection("comments")
                    .document(commentId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to delete comment", e)
            }
        }
    }

    fun reportSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            try {
                val myId = userPreferences.getDeviceUUID()
                val updatedReportedByUserIds = suggestion.reportedByUserIds.toMutableList()
                if (!updatedReportedByUserIds.contains(myId)) {
                    updatedReportedByUserIds.add(myId)
                }
                firestore.collection("suggestions")
                    .document(suggestion.id)
                    .update(
                        "reportedByUserIds", updatedReportedByUserIds,
                        "isReported", true
                    )
                    .await()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to report suggestion", e)
            }
        }
    }

    fun reportComment(suggestionId: String, comment: SuggestionComment) {
        viewModelScope.launch {
            try {
                val myId = userPreferences.getDeviceUUID()
                val updatedReportedByUserIds = comment.reportedByUserIds.toMutableList()
                if (!updatedReportedByUserIds.contains(myId)) {
                    updatedReportedByUserIds.add(myId)
                }
                firestore.collection("suggestions")
                    .document(suggestionId)
                    .collection("comments")
                    .document(comment.id)
                    .update(
                        "reportedByUserIds", updatedReportedByUserIds,
                        "isReported", true
                    )
                    .await()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to report comment", e)
            }
        }
    }

    fun blockUser(userId: String) {
        userPreferences.blockUser(userId)
        loadBlockedUsers()
    }

    fun reportUser(reportedUserId: String, reportedUserNickname: String, reason: String) {
        viewModelScope.launch {
            try {
                val ref = firestore.collection("user_reports").document()
                val report = UserReport(
                    id = ref.id,
                    reportedUserId = reportedUserId,
                    reportedUserNickname = reportedUserNickname,
                    reportedBy = userPreferences.getDeviceUUID(),
                    createdAt = System.currentTimeMillis(),
                    reason = reason
                )
                ref.set(report).await()
                // Automatically block the user locally
                blockUser(reportedUserId)
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to report user", e)
            }
        }
    }

    fun togglePinSuggestion(suggestionId: String, currentPinnedState: Boolean) {
        viewModelScope.launch {
            try {
                val newPinnedState = !currentPinnedState
                firestore.collection("suggestions")
                    .document(suggestionId)
                    .update("isPinned", newPinnedState)
                    .await()
                
                val currentList = _suggestions.value.map { item ->
                    if (item.id == suggestionId) item.copy(isPinned = newPinnedState) else item
                }.sortedWith(compareByDescending<Suggestion> { it.isPinned }.thenByDescending { it.createdAt })
                _suggestions.value = currentList
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to toggle pin for suggestion", e)
            }
        }
    }

    fun unreportSuggestion(suggestionId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("suggestions")
                    .document(suggestionId)
                    .update(
                        "isReported", false,
                        "reportedByUserIds", emptyList<String>()
                    )
                    .await()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to unreport suggestion", e)
            }
        }
    }

    fun getCurrentUserUUID(): String {
        return userPreferences.getDeviceUUID()
    }

    fun isAdmin(): Boolean {
        return userPreferences.isAdmin()
    }

    fun setAdmin(isAdmin: Boolean) {
        userPreferences.setAdmin(isAdmin)
    }

    override fun onCleared() {
        super.onCleared()
        stopObservingComments()
    }
}
