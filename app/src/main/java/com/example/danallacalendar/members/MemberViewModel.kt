package com.example.danallacalendar.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val deviceUUID: String
        get() = userPreferences.getDeviceUUID()
    
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _isCreator = MutableStateFlow(false)
    val isCreator: StateFlow<Boolean> = _isCreator.asStateFlow()

    private val _kickedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val kickedEvent = _kickedEvent.asSharedFlow()

    private var hasLoadedMembers = false

    var currentRoomCode: String = ""
        private set

    fun initializeRoom(roomCode: String) {
        if (roomCode.isBlank()) return
        currentRoomCode = roomCode
        registerCurrentUser()
        observeMembers(roomCode)
        observeCreator(roomCode)
    }

    fun registerCurrentUser() {
        val nickname = userPreferences.getNickname()
        if (currentRoomCode.isNotEmpty() && deviceUUID.isNotEmpty() && nickname.isNotEmpty()) {
            memberRepository.registerOrUpdateMember(currentRoomCode, deviceUUID, nickname)
        }
    }

    private fun observeMembers(roomCode: String) {
        hasLoadedMembers = false
        viewModelScope.launch {
            memberRepository.getMembersFlow(roomCode).collect { memberList ->
                _members.value = memberList
                
                val nickname = userPreferences.getNickname()
                if (nickname.isNotEmpty()) {
                    if (memberList.isNotEmpty()) {
                        hasLoadedMembers = true
                        val isMePresent = memberList.any { it.deviceUUID == deviceUUID }
                        if (!isMePresent) {
                            handleKicked()
                        }
                    } else if (hasLoadedMembers) {
                        handleKicked()
                    }
                }
            }
        }
    }

    private fun handleKicked() {
        viewModelScope.launch {
            try {
                memberRepository.removeMember(currentRoomCode, deviceUUID)
            } catch (e: Exception) {
                // Ignore
            }
            userPreferences.clearAll()
            _kickedEvent.emit(Unit)
        }
    }

    private fun observeCreator(roomCode: String) {
        viewModelScope.launch {
            memberRepository.getRoomCreatorFlow(roomCode).collect { creatorUUID ->
                _isCreator.value = (creatorUUID != null && creatorUUID == deviceUUID)
            }
        }
    }

    fun removeMember(targetDeviceUUID: String) {
        if (currentRoomCode.isNotEmpty() && targetDeviceUUID.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    memberRepository.removeMember(currentRoomCode, targetDeviceUUID)
                } catch (e: Exception) {
                    android.util.Log.e("MemberViewModel", "Failed to remove member", e)
                }
            }
        }
    }

    fun transferHost(newHostUUID: String) {
        if (currentRoomCode.isNotEmpty() && newHostUUID.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    memberRepository.transferHost(currentRoomCode, newHostUUID)
                } catch (e: Exception) {
                    android.util.Log.e("MemberViewModel", "Failed to transfer host privilege", e)
                }
            }
        }
    }
}
