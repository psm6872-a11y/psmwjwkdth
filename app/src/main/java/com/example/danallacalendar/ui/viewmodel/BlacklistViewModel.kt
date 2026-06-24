package com.example.danallacalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.BlacklistDao
import com.example.danallacalendar.data.BlacklistItem
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val blacklistDao: BlacklistDao,
    private val repository: CalendarRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val blacklistItems: StateFlow<List<BlacklistItem>> = blacklistDao.getAllFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addBlacklist(phoneNumber: String, reason: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val cleanPhone = phoneNumber.trim()
        val cleanReason = reason.trim()

        if (cleanPhone.isBlank()) {
            onError("전화번호를 입력해주세요.")
            return
        }
        if (cleanReason.isBlank()) {
            onError("사유를 입력해주세요.")
            return
        }

        viewModelScope.launch {
            try {
                val roomCode = userPreferences.getLastRoomCode()
                val syncId = if (roomCode.isNotEmpty()) java.util.UUID.randomUUID().toString() else null
                val item = BlacklistItem(
                    phoneNumber = cleanPhone,
                    reason = cleanReason,
                    syncId = syncId,
                    isSynced = false
                )
                val insertedId = blacklistDao.insert(item)
                if (roomCode.isNotEmpty() && syncId != null) {
                    repository.uploadBlacklistItem(roomCode, item.copy(id = insertedId.toInt()))
                }
                onSuccess()
            } catch (e: Exception) {
                onError("추가 실패: ${e.localizedMessage}")
            }
        }
    }

    fun deleteBlacklist(item: BlacklistItem) {
        viewModelScope.launch {
            try {
                blacklistDao.delete(item)
                val roomCode = userPreferences.getLastRoomCode()
                if (roomCode.isNotEmpty() && item.syncId != null) {
                    repository.deleteBlacklistItemFromFirestore(roomCode, item.syncId)
                }
            } catch (e: Exception) {
                android.util.Log.e("BlacklistViewModel", "Failed to delete blacklist item: ${item.id}", e)
            }
        }
    }

    fun updateBlacklist(
        item: BlacklistItem,
        phoneNumber: String,
        reason: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanPhone = phoneNumber.trim()
        val cleanReason = reason.trim()

        if (cleanPhone.isBlank()) {
            onError("전화번호를 입력해주세요.")
            return
        }
        if (cleanReason.isBlank()) {
            onError("사유를 입력해주세요.")
            return
        }

        viewModelScope.launch {
            try {
                val roomCode = userPreferences.getLastRoomCode()
                val updatedItem = item.copy(
                    phoneNumber = cleanPhone,
                    reason = cleanReason,
                    isSynced = false
                )
                blacklistDao.insert(updatedItem)
                if (roomCode.isNotEmpty() && updatedItem.syncId != null) {
                    repository.uploadBlacklistItem(roomCode, updatedItem)
                }
                onSuccess()
            } catch (e: Exception) {
                onError("수정 실패: ${e.localizedMessage}")
            }
        }
    }
}
