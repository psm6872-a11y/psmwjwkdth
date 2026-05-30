package com.example.danallacalendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.model.CalendarEvent
import com.example.danallacalendar.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val deviceUUID = userPreferences.getDeviceUUID()
    val nickname = userPreferences.getNickname()
    val roomCode = userPreferences.getLastRoomCode()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events = _events.asStateFlow()

    private val _syncStatus = MutableStateFlow("연결중")
    val syncStatus = _syncStatus.asStateFlow()

    private val _currentDate = MutableStateFlow(Calendar.getInstance())
    val currentDate = _currentDate.asStateFlow()

    private val _error = MutableStateFlow("")
    val error = _error.asStateFlow()

    init {
        observeEvents()
    }

    private fun observeEvents() {
        if (roomCode.isEmpty()) return
        viewModelScope.launch {
            _syncStatus.value = "연결중"
            repository.getEventsFlow(roomCode)
                .catch { e ->
                    _error.value = e.localizedMessage ?: "동기화 오류"
                    _syncStatus.value = "오프라인"
                }
                .collect { eventList ->
                    _events.value = eventList
                    _syncStatus.value = "동기화됨"
                }
        }
    }

    fun nextMonth() {
        val cal = _currentDate.value.clone() as Calendar
        cal.add(Calendar.MONTH, 1)
        _currentDate.value = cal
    }

    fun prevMonth() {
        val cal = _currentDate.value.clone() as Calendar
        cal.add(Calendar.MONTH, -1)
        _currentDate.value = cal
    }

    fun addEvent(title: String, date: String, time: String, description: String) {
        _error.value = ""
        repository.addEvent(
            roomCode = roomCode,
            title = title,
            date = date,
            time = time,
            description = description,
            onSuccess = { /* Handle success locally if needed */ },
            onFailure = { e ->
                _error.value = e.localizedMessage ?: "일정 추가 실패"
            }
        )
    }

    fun updateEvent(event: CalendarEvent) {
        _error.value = ""
        repository.updateEvent(
            roomCode = roomCode,
            event = event,
            onSuccess = { /* Handle success */ },
            onFailure = { e ->
                _error.value = e.localizedMessage ?: "일정 수정 실패"
            }
        )
    }

    fun deleteEvent(event: CalendarEvent) {
        _error.value = ""
        repository.deleteEvent(
            roomCode = roomCode,
            eventId = event.id,
            createdBy = event.createdBy,
            onSuccess = { /* Handle success */ },
            onFailure = { err ->
                _error.value = err
            }
        )
    }
}
