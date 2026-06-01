package com.example.danallacalendar.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val deviceUUID = userPreferences.getDeviceUUID()
    
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    var currentRoomCode: String = ""
        private set

    fun initializeRoom(roomCode: String) {
        if (roomCode.isBlank()) return
        currentRoomCode = roomCode
        registerCurrentUser()
        observeMembers(roomCode)
    }

    fun registerCurrentUser() {
        val nickname = userPreferences.getNickname()
        if (currentRoomCode.isNotEmpty() && deviceUUID.isNotEmpty() && nickname.isNotEmpty()) {
            memberRepository.registerOrUpdateMember(currentRoomCode, deviceUUID, nickname)
        }
    }

    private fun observeMembers(roomCode: String) {
        viewModelScope.launch {
            memberRepository.getMembersFlow(roomCode).collect { memberList ->
                _members.value = memberList
            }
        }
    }
}
