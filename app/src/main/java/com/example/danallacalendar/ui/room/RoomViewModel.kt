package com.example.danallacalendar.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoomViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _nickname = MutableStateFlow(userPreferences.getNickname())
    val nickname = _nickname.asStateFlow()

    private val _roomCode = MutableStateFlow(userPreferences.getLastRoomCode())
    val roomCode = _roomCode.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow("")
    val error = _error.asStateFlow()

    fun createRoom(onSuccess: (String) -> Unit) {
        _loading.value = true
        _error.value = ""
        viewModelScope.launch {
            try {
                val code = repository.createRoomSuspended()
                userPreferences.setLastRoomCode(code)
                _roomCode.value = code
                onSuccess(code)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "방 생성 실패"
            } finally {
                _loading.value = false
            }
        }
    }

    fun joinRoom(code: String, onSuccess: () -> Unit) {
        // Normalize code input (trim space and replace dashes if any)
        val formattedCode = code.trim().replace(" ", "")
        if (formattedCode.length != 7 || !formattedCode.contains("-")) {
            _error.value = "올바른 형식(예: 483-291)으로 코드를 입력해 주세요."
            return
        }

        _loading.value = true
        _error.value = ""
        viewModelScope.launch {
            try {
                repository.joinRoomSuspended(formattedCode)
                userPreferences.setLastRoomCode(formattedCode)
                _roomCode.value = formattedCode
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "방 확인 실패"
            } finally {
                _loading.value = false
            }
        }
    }

    fun resetPreferences() {
        userPreferences.clearAll()
        _nickname.value = ""
        _roomCode.value = ""
    }
}
