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
            val data = doc.data ?: return null
            val title = doc.getString("title") ?: data["title"]?.toString() ?: data["subject"]?.toString() ?: ""
            val content = doc.getString("content") ?: data["content"]?.toString() ?: data["body"]?.toString() ?: ""
            val authorId = doc.getString("authorId") ?: data["authorId"]?.toString() ?: ""
            val authorNickname = doc.getString("authorNickname") ?: data["authorNickname"]?.toString() ?: "익명"
            val createdAt = doc.getLong("createdAt") 
                ?: (data["createdAt"] as? Long) 
                ?: (doc.getTimestamp("createdAt")?.toDate()?.time) 
                ?: System.currentTimeMillis()
            val isReported = doc.getBoolean("isReported") 
                ?: (data["isReported"] as? Boolean) 
                ?: doc.getBoolean("reported") 
                ?: false
            val isAdmin = doc.getBoolean("isAdmin") 
                ?: (data["isAdmin"] as? Boolean) 
                ?: doc.getBoolean("admin") 
                ?: false
            val isPinned = doc.getBoolean("isPinned") 
                ?: (data["isPinned"] as? Boolean) 
                ?: doc.getBoolean("pinned") 
                ?: false
            val reportedByUserIds = (data["reportedByUserIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

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
            android.util.Log.e("SuggestionViewModel", "Parse failed for doc ${doc.id}", e)
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
            val data = doc.data ?: return null
            val content = doc.getString("content") ?: data["content"]?.toString() ?: data["text"]?.toString() ?: ""
            val authorId = doc.getString("authorId") ?: data["authorId"]?.toString() ?: ""
            val authorNickname = doc.getString("authorNickname") ?: data["authorNickname"]?.toString() ?: "익명"
            val createdAt = doc.getLong("createdAt") 
                ?: (data["createdAt"] as? Long) 
                ?: (doc.getTimestamp("createdAt")?.toDate()?.time) 
                ?: System.currentTimeMillis()
            val isReported = doc.getBoolean("isReported") 
                ?: (data["isReported"] as? Boolean) 
                ?: doc.getBoolean("reported") 
                ?: false
            val isAdmin = doc.getBoolean("isAdmin") 
                ?: (data["isAdmin"] as? Boolean) 
                ?: doc.getBoolean("admin") 
                ?: false
            val reportedByUserIds = (data["reportedByUserIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            SuggestionComment(
                id = doc.id,
                content = content,
                authorId = authorId,
                authorNickname = authorNickname,
                createdAt = createdAt,
                reportedByUserIds = reportedByUserIds,
                isReported = isReported,
                isAdmin = isAdmin
            )
        } catch (e: Exception) {
            android.util.Log.e("SuggestionViewModel", "Parse failed for comment doc ${doc.id}", e)
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
                val authorId = userPreferences.getDeviceUUID()
                val now = System.currentTimeMillis()

                val suggestionMap = hashMapOf<String, Any>(
                    "id" to ref.id,
                    "title" to title,
                    "content" to content,
                    "authorId" to authorId,
                    "authorNickname" to authorNick,
                    "createdAt" to now,
                    "reportedByUserIds" to emptyList<String>(),
                    "isReported" to false,
                    "isAdmin" to isAdmin,
                    "isPinned" to false
                )
                ref.set(suggestionMap).await()

                val suggestion = Suggestion(
                    id = ref.id,
                    title = title,
                    content = content,
                    authorId = authorId,
                    authorNickname = authorNick,
                    createdAt = now,
                    reportedByUserIds = emptyList(),
                    isReported = false,
                    isAdmin = isAdmin,
                    isPinned = false
                )

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

    fun updateSuggestion(suggestionId: String, newTitle: String, newContent: String, onSuccess: (Suggestion) -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                if (suggestionId.isBlank() || newTitle.isBlank() || newContent.isBlank()) {
                    onError("제목과 내용을 모두 입력해 주세요.")
                    return@launch
                }
                val updates = hashMapOf<String, Any>(
                    "title" to newTitle,
                    "content" to newContent
                )
                firestore.collection("suggestions")
                    .document(suggestionId)
                    .update(updates)
                    .await()

                var updatedSuggestion: Suggestion? = null
                val currentList = _suggestions.value.map { item ->
                    if (item.id == suggestionId) {
                        val updated = item.copy(title = newTitle, content = newContent)
                        updatedSuggestion = updated
                        updated
                    } else {
                        item
                    }
                }
                _suggestions.value = currentList

                if (updatedSuggestion != null) {
                    onSuccess(updatedSuggestion!!)
                } else {
                    val fallback = Suggestion(id = suggestionId, title = newTitle, content = newContent)
                    onSuccess(fallback)
                }
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to update suggestion", e)
                onError(e.message ?: "게시글 수정에 실패했습니다.")
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
                val authorId = userPreferences.getDeviceUUID()
                val now = System.currentTimeMillis()

                val commentMap = hashMapOf<String, Any>(
                    "id" to ref.id,
                    "content" to content,
                    "authorId" to authorId,
                    "authorNickname" to authorNick,
                    "createdAt" to now,
                    "reportedByUserIds" to emptyList<String>(),
                    "isReported" to false,
                    "isAdmin" to isAdmin
                )
                ref.set(commentMap).await()

                val comment = SuggestionComment(
                    id = ref.id,
                    content = content,
                    authorId = authorId,
                    authorNickname = authorNick,
                    createdAt = now,
                    reportedByUserIds = emptyList(),
                    isReported = false,
                    isAdmin = isAdmin
                )

                val currentList = _comments.value.toMutableList()
                currentList.removeAll { it.id == comment.id }
                currentList.add(comment)
                _comments.value = currentList

                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to add comment", e)
                onError(e.message ?: "댓글 작성에 실패했습니다.")
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
