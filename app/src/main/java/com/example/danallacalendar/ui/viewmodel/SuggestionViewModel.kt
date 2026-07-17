package com.example.danallacalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.Suggestion
import com.example.danallacalendar.data.SuggestionComment
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
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("SuggestionViewModel", "Error listening to suggestions", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.toObjects(Suggestion::class.java)
                    _suggestions.value = list
                }
            }
    }

    fun observeComments(suggestionId: String) {
        commentsListenerRegistration?.remove()
        commentsListenerRegistration = firestore.collection("suggestions")
            .document(suggestionId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("SuggestionViewModel", "Error listening to comments", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    _comments.value = snapshot.toObjects(SuggestionComment::class.java)
                }
            }
    }

    fun stopObservingComments() {
        commentsListenerRegistration?.remove()
        commentsListenerRegistration = null
        _comments.value = emptyList()
    }

    fun addSuggestion(title: String, content: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val ref = firestore.collection("suggestions").document()
                val suggestion = Suggestion(
                    id = ref.id,
                    title = title,
                    content = content,
                    authorId = userPreferences.getDeviceUUID(),
                    authorNickname = userPreferences.getNickname().ifBlank { "익명" },
                    createdAt = System.currentTimeMillis()
                )
                ref.set(suggestion).await()
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to add suggestion", e)
            }
        }
    }

    fun addComment(suggestionId: String, content: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val ref = firestore.collection("suggestions")
                    .document(suggestionId)
                    .collection("comments")
                    .document()
                val comment = SuggestionComment(
                    id = ref.id,
                    content = content,
                    authorId = userPreferences.getDeviceUUID(),
                    authorNickname = userPreferences.getNickname().ifBlank { "익명" },
                    createdAt = System.currentTimeMillis()
                )
                ref.set(comment).await()
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("SuggestionViewModel", "Failed to add comment", e)
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

    fun getCurrentUserUUID(): String {
        return userPreferences.getDeviceUUID()
    }

    override fun onCleared() {
        super.onCleared()
        stopObservingComments()
    }
}
