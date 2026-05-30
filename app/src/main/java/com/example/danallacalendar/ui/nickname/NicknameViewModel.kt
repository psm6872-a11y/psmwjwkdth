package com.example.danallacalendar.ui.nickname

import androidx.lifecycle.ViewModel
import com.example.danallacalendar.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class NicknameViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _nickname = MutableStateFlow(userPreferences.getNickname())
    val nickname = _nickname.asStateFlow()

    fun saveNickname(newNickname: String, onSuccess: () -> Unit) {
        if (newNickname.isNotBlank()) {
            userPreferences.setNickname(newNickname.trim())
            _nickname.value = newNickname.trim()
            onSuccess()
        }
    }
}
