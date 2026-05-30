package com.example.danallacalendar.ui.room

import androidx.lifecycle.ViewModel
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        repository.createRoom(
            onSuccess = { code ->
                _loading.value = false
                _roomCode.value = code
                onSuccess(code)
            },
            onFailure = { e ->
                _loading.value = false
                _error.value = e.localizedMessage ?: "방 생성 실패"
            }
        )
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
        repository.joinRoom(
            roomCode = formattedCode,
            onSuccess = {
                _loading.value = false
                _roomCode.value = formattedCode
                onSuccess()
            },
            onFailure = { err ->
                _loading.value = false
                _error.value = err
            }
        )
    }

    fun resetPreferences() {
        userPreferences.clearAll()
        _nickname.value = ""
        _roomCode.value = ""
    }
}
