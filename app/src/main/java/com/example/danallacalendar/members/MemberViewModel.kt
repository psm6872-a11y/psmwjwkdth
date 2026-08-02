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

    private val _creatorUUID = MutableStateFlow<String?>(null)
    val creatorUUID: StateFlow<String?> = _creatorUUID.asStateFlow()

    private val _hasWritePermission = MutableStateFlow(userPreferences.getLastRoomCode().isEmpty() || userPreferences.hasWritePermission())
    val hasWritePermission: StateFlow<Boolean> = _hasWritePermission.asStateFlow()

    private val _kickedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val kickedEvent = _kickedEvent.asSharedFlow()

    private var hasLoadedMembers = false

    var currentRoomCode: String = ""
        private set

    fun initializeRoom(roomCode: String) {
        if (roomCode.isBlank()) {
            _isCreator.value = true
            _hasWritePermission.value = true
            return
        }
        currentRoomCode = roomCode
        
        val localIsCreator = userPreferences.isRoomCreator(roomCode)
        _isCreator.value = localIsCreator
        _hasWritePermission.value = if (localIsCreator) true else userPreferences.hasWritePermission()
        if (localIsCreator) {
            userPreferences.setWritePermission(true)
        }

        registerCurrentUser()
        observeCreator(roomCode)   // 방장 여부를 먼저 확인한 후
        observeMembers(roomCode)   // 멤버 목록 관찰 (isCreator가 이미 설정된 상태)
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
                
                val isMeCreator = _isCreator.value || userPreferences.isRoomCreator(roomCode) || (_creatorUUID.value != null && _creatorUUID.value == deviceUUID)
                if (isMeCreator) {
                    _isCreator.value = true
                    userPreferences.markAsRoomCreator(roomCode)
                    userPreferences.setWritePermission(true)
                    _hasWritePermission.value = true
                } else {
                    val me = memberList.firstOrNull { it.deviceUUID == deviceUUID }
                    if (me != null) {
                        userPreferences.setWritePermission(me.hasWritePermission)
                        _hasWritePermission.value = me.hasWritePermission
                    }
                }
                
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
                _creatorUUID.value = creatorUUID
                val isMeCreator = userPreferences.isRoomCreator(roomCode) || (creatorUUID != null && creatorUUID == deviceUUID)
                _isCreator.value = isMeCreator
                if (isMeCreator) {
                    userPreferences.markAsRoomCreator(roomCode)
                    userPreferences.setWritePermission(true)
                    _hasWritePermission.value = true
                }
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
                    userPreferences.removeRoomCreator(currentRoomCode)
                    memberRepository.transferHost(currentRoomCode, newHostUUID)
                } catch (e: Exception) {
                    android.util.Log.e("MemberViewModel", "Failed to transfer host privilege", e)
                }
            }
        }
    }

    fun updateWritePermission(targetDeviceUUID: String, hasWrite: Boolean) {
        if (currentRoomCode.isNotEmpty() && targetDeviceUUID.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    memberRepository.updateWritePermission(currentRoomCode, targetDeviceUUID, hasWrite)
                } catch (e: Exception) {
                    android.util.Log.e("MemberViewModel", "Failed to update write permission", e)
                }
            }
        }
    }

    fun leaveRoom(newHostUUID: String? = null, onComplete: () -> Unit) {
        if (currentRoomCode.isBlank() || deviceUUID.isBlank()) {
            onComplete()
            return
        }
        viewModelScope.launch {
            try {
                if (!newHostUUID.isNullOrBlank()) {
                    memberRepository.transferHost(currentRoomCode, newHostUUID)
                    memberRepository.updateWritePermission(currentRoomCode, newHostUUID, true)
                }
                memberRepository.removeMember(currentRoomCode, deviceUUID)
            } catch (e: Exception) {
                android.util.Log.e("MemberViewModel", "Failed to leave room", e)
            } finally {
                onComplete()
            }
        }
    }
}
