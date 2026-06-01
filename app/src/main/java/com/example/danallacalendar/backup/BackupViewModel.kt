package com.example.danallacalendar.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.data.EventDao
import com.example.danallacalendar.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Loading : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val userPreferences: UserPreferences,
    private val eventDao: EventDao
) : ViewModel() {

    private val _backupList = MutableStateFlow<List<BackupEntry>>(emptyList())
    val backupList: StateFlow<List<BackupEntry>> = _backupList.asStateFlow()

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _localEventCount = MutableStateFlow(0)
    val localEventCount: StateFlow<Int> = _localEventCount.asStateFlow()

    val roomCode: String get() = userPreferences.getLastRoomCode()

    init {
        loadBackupList()
        loadLocalEventCount()
    }

    fun loadLocalEventCount() {
        viewModelScope.launch {
            try {
                _localEventCount.value = eventDao.getAllEventsList().size
            } catch (e: Exception) {
                _localEventCount.value = 0
            }
        }
    }

    fun loadBackupList() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = backupRepository.getBackupList(roomCode)
            result.onSuccess { list ->
                _backupList.value = list
            }.onFailure { e ->
                _uiState.value = BackupUiState.Error(e.message ?: "목록을 불러올 수 없습니다.")
            }
            _isLoading.value = false
        }
    }

    // 수동 백업
    fun performManualBackup() {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            try {
                val allEvents = eventDao.getAllEventsList()
                if (allEvents.isEmpty()) {
                    _uiState.value = BackupUiState.Error("백업할 일정이 없습니다.")
                    return@launch
                }
                val result = backupRepository.saveBackup(roomCode, allEvents)
                result.onSuccess { dateKey ->
                    backupRepository.deleteOldBackups(roomCode)
                    _uiState.value = BackupUiState.Success("${allEvents.size}개 일정이 백업되었습니다. ($dateKey)")
                    loadBackupList()
                    loadLocalEventCount()
                }.onFailure { e ->
                    _uiState.value = BackupUiState.Error(e.message ?: "백업 실패")
                }
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(e.message ?: "백업 실패")
            }
        }
    }

    // 복원 - 현재 일정 전부 교체
    fun restoreBackup(backupId: String, targetCalendarId: Int) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            try {
                // 0. 복원 전 현재 상태를 임시 백업 (복원 실수 방지)
                val currentEvents = eventDao.getAllEventsList()
                if (currentEvents.isNotEmpty()) {
                    val tempBackupId = "restore_backup_before_${System.currentTimeMillis()}"
                    backupRepository.saveBackup(roomCode, currentEvents, tempBackupId)
                }

                // 1. 복원할 데이터 가져오기
                val result = backupRepository.restoreBackup(roomCode, backupId, targetCalendarId)
                result.onSuccess { events ->
                    // 2. 현재 일정 모두 삭제
                    eventDao.deleteAllEvents()
                    // 3. 복원한 이벤트 삽입
                    events.forEach { event ->
                        eventDao.insertEvent(event.copy(id = 0))
                    }
                    _uiState.value = BackupUiState.Success("${events.size}개의 일정이 복원되었습니다.")
                    loadLocalEventCount()
                }.onFailure { e ->
                    _uiState.value = BackupUiState.Error(e.message ?: "복원 실패")
                }
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(e.message ?: "복원 실패")
            }
        }
    }

    fun clearUiState() {
        _uiState.value = BackupUiState.Idle
    }
}
