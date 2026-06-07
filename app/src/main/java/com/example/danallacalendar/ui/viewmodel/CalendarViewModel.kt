package com.example.danallacalendar.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.CalendarCategory
import com.example.danallacalendar.data.DeadlineDate
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import android.provider.CalendarContract
import javax.inject.Inject
import com.example.danallacalendar.update.UpdateChecker
import com.example.danallacalendar.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class CalendarViewMode {
    MONTH, WEEK
}

enum class EventFilter {
    ALL, ESTIMATE, CONTRACT
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun checkUpdateManually() {
        if (_isChecking.value) return
        
        if (!isNetworkAvailable(context)) {
            _updateState.value = UpdateState.NoNetwork
            return
        }

        _isChecking.value = true
        _updateState.value = UpdateState.Checking
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = UpdateChecker.checkForUpdate(context)
                if (info != null) {
                    _updateState.value = UpdateState.UpdateAvailable(
                        version = info.latestVersion,
                        downloadUrl = info.downloadUrl,
                        updateInfo = info
                    )
                } else {
                    _updateState.value = UpdateState.UpToDate
                }
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("UpdateChecker", "오류 발생: ${e.message}", e)
                _updateState.value = UpdateState.NoNetwork
            } catch (e: Exception) {
                android.util.Log.e("UpdateChecker", "오류 발생: ${e.message}", e)
                _updateState.value = UpdateState.Error
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun checkUpdateAutomatically() {
        if (!isNetworkAvailable(context)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = UpdateChecker.checkForUpdate(context)
                if (info != null) {
                    _updateState.value = UpdateState.UpdateAvailable(
                        version = info.latestVersion,
                        downloadUrl = info.downloadUrl,
                        updateInfo = info
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateChecker", "자동 업데이트 확인 실패", e)
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    val deviceUUID = userPreferences.getDeviceUUID()
    val roomCode = userPreferences.getLastRoomCode()

    private val _isLoggedIn = MutableStateFlow(userPreferences.getLastRoomCode().isNotEmpty())
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _loginType = MutableStateFlow("공유 코드")
    val loginType = _loginType.asStateFlow()

    private val _userName = MutableStateFlow(userPreferences.getNickname())
    val userName = _userName.asStateFlow()

    fun logout() {
        userPreferences.setLastRoomCode("")
        _isLoggedIn.value = false
    }

    suspend fun getOrCreateSharedCategory(): Int {
        val list = repository.eventDao.getAllCategoriesList()
        val found = list.find { it.name == "공유 캘린더" }
        return if (found != null) {
            found.id
        } else {
            repository.eventDao.insertCategory(
                CalendarCategory(
                    name = "공유 캘린더",
                    colorHex = "#34c759", // Green color for synced calendar
                    accountName = "공유 계정",
                    isVisible = true
                )
            ).toInt()
        }
    }

    init {
        viewModelScope.launch {
            // 1. Deduplicate existing categories by name
            val currentList = repository.eventDao.getAllCategoriesList()
            val groupedByName = currentList.groupBy { it.name }
            groupedByName.forEach { (name, categories) ->
                if (categories.size > 1) {
                    val keep = categories.first()
                    val toDelete = categories.drop(1)
                    val toDeleteIds = toDelete.map { it.id }
                    
                    // Update events pointing to duplicate categories
                    repository.eventDao.updateEventsCalendarId(toDeleteIds, keep.id)
                    // Delete duplicate categories
                    repository.eventDao.deleteCategories(toDelete)
                }
            }

            // 2. Insert other default categories (excluding the 4 main ones generated on DB creation)
            val updatedList = repository.eventDao.getAllCategoriesList()
            val defaultCategories = listOf(
                CalendarCategory(name = "공유 캘린더", colorHex = "#34c759", accountName = "공유 계정", isVisible = true),
                CalendarCategory(name = "주황색 캘린더", colorHex = "#ff9500", accountName = "기타", isVisible = true),
                CalendarCategory(name = "노란색 캘린더", colorHex = "#ffcc00", accountName = "기타", isVisible = true),
                CalendarCategory(name = "핑크색 캘린더", colorHex = "#ff2d55", accountName = "기타", isVisible = true),
                CalendarCategory(name = "청록색 캘린더", colorHex = "#5ac8fa", accountName = "기타", isVisible = true),
                CalendarCategory(name = "남색 캘린더", colorHex = "#5856d6", accountName = "기타", isVisible = true),
                CalendarCategory(name = "하늘색 캘린더", colorHex = "#00cbd6", accountName = "기타", isVisible = true),
                CalendarCategory(name = "갈색 캘린더", colorHex = "#a2845e", accountName = "기타", isVisible = true),
                CalendarCategory(name = "민트색 캘린더", colorHex = "#63e6be", accountName = "기타", isVisible = true),
                CalendarCategory(name = "라벤더색 캘린더", colorHex = "#bf8bff", accountName = "기타", isVisible = true),
                CalendarCategory(name = "복숭아색 캘린더", colorHex = "#ffb3ba", accountName = "기타", isVisible = true)
            )
            for (defaultCat in defaultCategories) {
                val exists = updatedList.any { it.name == defaultCat.name }
                if (!exists) {
                    repository.eventDao.insertCategory(defaultCat)
                }
            }

            // Start real-time Firestore sync
            val sharedCatId = getOrCreateSharedCategory()
            if (roomCode.isNotEmpty()) {
                repository.registerMemberInFirestore(roomCode)
                
                // 1. 이벤트 실시간 동기화
                launch {
                    repository.startRealtimeSync(roomCode, sharedCatId)
                        .catch { e -> android.util.Log.e("SyncError", "Sync failure", e) }
                        .collect()
                }

                // 2. 마감도장 실시간 동기화
                launch {
                    repository.startDeadlineRealtimeSync(roomCode)
                        .catch { e -> android.util.Log.e("SyncError", "Deadline sync failure", e) }
                        .collect()
                }
            }
        }

        // 3. 24-hour interval automatic update check
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_check_time", 0L)
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        if (now - lastCheck >= oneDayMillis) {
            checkUpdateAutomatically()
            prefs.edit().putLong("last_check_time", now).apply()
        }
    }

    // View States
    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        clearTimeToZero()
    })
    val currentMonth: StateFlow<Calendar> = _currentMonth.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _eventFilter = MutableStateFlow(EventFilter.ALL)
    val eventFilter: StateFlow<EventFilter> = _eventFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Database Flows
    val categories: StateFlow<List<CalendarCategory>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deadlineDates: StateFlow<Set<Long>> = repository.getAllDeadlineDates()
        .map { list -> list.map { it.dateMillis }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyEvents: StateFlow<List<Event>> = combine(
        _currentMonth.flatMapLatest { cal ->
            val start = cal.timeInMillis - (45 * 24 * 60 * 60 * 1000L) // -45 days
            val end = cal.timeInMillis + (75 * 24 * 60 * 60 * 1000L) // +75 days
            repository.getEventsInRange(start, end)
        },
        categories,
        _eventFilter
    ) { events, cats, filter ->
        events.filter { event ->
            val isVisible = cats.find { it.id == event.calendarId }?.isVisible ?: true
            val matchesFilter = when (filter) {
                EventFilter.ALL -> true
                EventFilter.ESTIMATE -> !event.isAllDay
                EventFilter.CONTRACT -> event.isAllDay
            }
            isVisible && matchesFilter
        }.sortedWith(
            compareByDescending<Event> { it.isAllDay }
                .thenBy { it.startMillis }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDateEvents: StateFlow<List<Event>> = combine(
        _selectedDate.flatMapLatest { millis ->
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            cal.clearTimeToZero()
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            repository.getEventsInRange(start, end)
        },
        categories,
        _eventFilter
    ) { events, cats, filter ->
        events.filter { event ->
            val isVisible = cats.find { it.id == event.calendarId }?.isVisible ?: true
            val matchesFilter = when (filter) {
                EventFilter.ALL -> true
                EventFilter.ESTIMATE -> !event.isAllDay
                EventFilter.CONTRACT -> event.isAllDay
            }
            isVisible && matchesFilter
        }.sortedWith(
            compareByDescending<Event> { it.isAllDay }
                .thenBy { it.startMillis }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Event>> = combine(
        _searchQuery
            .debounce(300)
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    repository.searchEvents(query)
                }
            },
        categories
    ) { events, cats ->
        events.filter { event ->
            cats.find { it.id == event.calendarId }?.isVisible ?: true
        }.sortedWith(
            compareByDescending<Event> { it.isAllDay }
                .thenBy { it.startMillis }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Actions
    fun selectDate(millis: Long) {
        _selectedDate.value = millis
        // Update visible month if the selected date moves to another month
        val calSelected = Calendar.getInstance().apply { timeInMillis = millis }
        val calCurrent = _currentMonth.value
        if (calSelected.get(Calendar.YEAR) != calCurrent.get(Calendar.YEAR) ||
            calSelected.get(Calendar.MONTH) != calCurrent.get(Calendar.MONTH)
        ) {
            val newMonth = Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.DAY_OF_MONTH, 1)
                clearTimeToZero()
            }
            _currentMonth.value = newMonth
        }
    }

    fun nextMonth() {
        val cal = _currentMonth.value.clone() as Calendar
        cal.add(Calendar.MONTH, 1)
        _currentMonth.value = cal
        
        // Also select the 1st of the new month as a default experience
        val selectCal = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
            clearTimeToZero()
        }
        _selectedDate.value = selectCal.timeInMillis
    }

    fun prevMonth() {
        val cal = _currentMonth.value.clone() as Calendar
        cal.add(Calendar.MONTH, -1)
        _currentMonth.value = cal

        // Also select the 1st of the new month
        val selectCal = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
            clearTimeToZero()
        }
        _selectedDate.value = selectCal.timeInMillis
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == CalendarViewMode.MONTH) {
            CalendarViewMode.WEEK
        } else {
            CalendarViewMode.MONTH
        }
    }

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setEventFilter(filter: EventFilter) {
        _eventFilter.value = filter
    }

    fun toggleDeadlineDate(dateMillis: Long) {
        viewModelScope.launch {
            // 자정 기준으로 날짜 정규화
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = dateMillis
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val normalized = cal.timeInMillis
            if (deadlineDates.value.any { isSameDay(it, dateMillis) }) {
                repository.deleteDeadlineDate(normalized)
            } else {
                repository.insertDeadlineDate(DeadlineDate(normalized))
            }
        }
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val calA = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val calB = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return calA.get(java.util.Calendar.YEAR) == calB.get(java.util.Calendar.YEAR) &&
               calA.get(java.util.Calendar.DAY_OF_YEAR) == calB.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun toggleCategoryVisibility(category: CalendarCategory) {
        viewModelScope.launch {
            repository.updateCategory(category.copy(isVisible = !category.isVisible))
        }
    }

    fun addEvent(event: Event) {
        viewModelScope.launch {
            val sharedId = getOrCreateSharedCategory()
            val eventToInsert = if (event.calendarId == sharedId) {
                event.copy(
                    isSynced = true,
                    syncId = event.syncId ?: UUID.randomUUID().toString()
                )
            } else {
                event
            }
            repository.insertEvent(eventToInsert)
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch {
            val sharedId = getOrCreateSharedCategory()
            val eventToUpdate = if (event.calendarId == sharedId) {
                event.copy(
                    isSynced = true,
                    syncId = event.syncId ?: UUID.randomUUID().toString()
                )
            } else {
                event
            }
            repository.updateEvent(eventToUpdate)
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    fun clearAllEvents() {
        viewModelScope.launch {
            repository.deleteAllEvents()
        }
    }

    fun importEventsFromJson(
        jsonString: String,
        targetCalendarId: Int,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val jsonParser = Json { ignoreUnknownKeys = true }
                val list = jsonParser.decodeFromString<List<ImportEvent>>(jsonString)
                for (imported in list) {
                    val event = Event(
                        title = imported.title,
                        startMillis = imported.startMillis,
                        endMillis = imported.endMillis,
                        isAllDay = imported.isAllDay,
                        location = imported.location,
                        notes = imported.notes,
                        calendarId = targetCalendarId,
                        colorHex = imported.colorHex
                    )
                    repository.insertEvent(event)
                }
                onSuccess(list.size)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun importEventsFromDevice(
        context: Context,
        targetCalendarId: Int,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                var importedCount = 0
                val contentResolver = context.contentResolver
                val uri = CalendarContract.Events.CONTENT_URI
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION
                )
                val selection = "(${CalendarContract.Events.DELETED} = 0 OR ${CalendarContract.Events.DELETED} IS NULL)"
                
                val cursor = contentResolver.query(uri, projection, selection, null, null)
                cursor?.use { c ->
                    val idIndex = c.getColumnIndex(CalendarContract.Events._ID)
                    val titleIndex = c.getColumnIndex(CalendarContract.Events.TITLE)
                    val dtStartIndex = c.getColumnIndex(CalendarContract.Events.DTSTART)
                    val dtEndIndex = c.getColumnIndex(CalendarContract.Events.DTEND)
                    val allDayIndex = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
                    val locationIndex = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    val descriptionIndex = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)

                    while (c.moveToNext()) {
                        val id = if (idIndex >= 0) c.getLong(idIndex) else continue
                        val title = if (titleIndex >= 0) c.getString(titleIndex) ?: "(제목 없음)" else "(제목 없음)"
                        val start = if (dtStartIndex >= 0 && !c.isNull(dtStartIndex)) c.getLong(dtStartIndex) else continue
                        val allDayVal = if (allDayIndex >= 0) c.getInt(allDayIndex) else 0
                        val isAllDay = allDayVal == 1
                        val end = if (dtEndIndex >= 0 && !c.isNull(dtEndIndex)) {
                            c.getLong(dtEndIndex)
                        } else {
                            if (isAllDay) start + 24 * 60 * 60 * 1000 else start + 60 * 60 * 1000
                        }
                        val location = if (locationIndex >= 0) c.getString(locationIndex) ?: "" else ""
                        val notes = if (descriptionIndex >= 0) c.getString(descriptionIndex) ?: "" else ""

                        val syncId = "device_calendar_event_$id"
                        val existing = repository.eventDao.getEventBySyncId(syncId)
                        if (existing == null) {
                            val event = Event(
                                title = title,
                                startMillis = start,
                                endMillis = end,
                                isAllDay = isAllDay,
                                location = location,
                                notes = notes,
                                calendarId = targetCalendarId,
                                syncId = syncId
                            )
                            repository.insertEvent(event)
                            importedCount++
                        }
                    }
                }
                onSuccess(importedCount)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    private fun Calendar.clearTimeToZero() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

@kotlinx.serialization.Serializable
data class ImportEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean = false,
    val location: String = "",
    val notes: String = "",
    val colorHex: String? = null
)
