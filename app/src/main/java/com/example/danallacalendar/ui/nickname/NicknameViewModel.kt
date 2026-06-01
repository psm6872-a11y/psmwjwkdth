package com.example.danallacalendar.ui.nickname

import androidx.lifecycle.ViewModel
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class NicknameViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val repository: CalendarRepository
) : ViewModel() {

    private val _nickname = MutableStateFlow(userPreferences.getNickname())
    val nickname = _nickname.asStateFlow()

    fun saveNickname(newNickname: String, onSuccess: () -> Unit) {
        if (newNickname.isNotBlank()) {
            val trimmed = newNickname.trim()
            userPreferences.setNickname(trimmed)
            _nickname.value = trimmed
            
            val roomCode = userPreferences.getLastRoomCode()
            if (roomCode.isNotEmpty()) {
                repository.registerMemberInFirestore(roomCode)
            }
            onSuccess()
        }
    }
}
