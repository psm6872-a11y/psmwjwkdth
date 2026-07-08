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
import java.text.SimpleDateFormat
import java.util.Locale
import android.provider.CalendarContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.tasks.await
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import android.app.Activity

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
    private val estimatePdfDao: com.example.danallacalendar.data.EstimatePdfDao,
    private val estimateRepository: com.example.danallacalendar.estimate.EstimateRepository,
    @ApplicationContext private val context: Context,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _isCheckingForUpdate = MutableStateFlow(false)
    val isCheckingForUpdate: StateFlow<Boolean> = _isCheckingForUpdate.asStateFlow()

    private val _isUpdateDownloaded = MutableStateFlow(false)
    val isUpdateDownloaded: StateFlow<Boolean> = _isUpdateDownloaded.asStateFlow()

    fun setUpdateDownloaded(downloaded: Boolean) {
        _isUpdateDownloaded.value = downloaded
    }

    fun checkForUpdates(activity: Activity) {
        if (_isCheckingForUpdate.value) return
        _isCheckingForUpdate.value = true

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            _isCheckingForUpdate.value = false
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.FLEXIBLE,
                        activity,
                        5001
                    )
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "업데이트 시작 실패: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                android.widget.Toast.makeText(context, "최신 버전입니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            _isCheckingForUpdate.value = false
            android.widget.Toast.makeText(context, "업데이트 확인 실패: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }


    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }



    val deviceUUID = userPreferences.getDeviceUUID()

    private val _roomCodeState = MutableStateFlow(userPreferences.getLastRoomCode())
    val roomCode: String
        get() = _roomCodeState.value

    private val _isLoggedIn = MutableStateFlow(userPreferences.getLastRoomCode().isNotEmpty())
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _loginType = MutableStateFlow("공유 코드")
    val loginType = _loginType.asStateFlow()

    private val _userName = MutableStateFlow(userPreferences.getNickname())
    val userName = _userName.asStateFlow()

    private val _pendingChangeNotification = MutableStateFlow<Pair<String, String>?>(null)
    val pendingChangeNotification = _pendingChangeNotification.asStateFlow()

    private val _highlightedEventSyncId = MutableStateFlow<String?>(null)
    val highlightedEventSyncId = _highlightedEventSyncId.asStateFlow()

    private val _showChangeNotificationDialog = MutableStateFlow(false)
    val showChangeNotificationDialog = _showChangeNotificationDialog.asStateFlow()

    fun setPendingChangeNotification(title: String, body: String) {
        _pendingChangeNotification.value = Pair(title, body)
    }

    fun clearPendingChangeNotification() {
        _pendingChangeNotification.value = null
    }

    fun setHighlightedEventSyncId(syncId: String?) {
        _highlightedEventSyncId.value = syncId
    }

    fun clearHighlightedEventSyncId() {
        _highlightedEventSyncId.value = null
    }

    fun setShowChangeNotificationDialog(show: Boolean) {
        _showChangeNotificationDialog.value = show
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    private fun startSync(code: String) {
        syncJob?.cancel()
        if (code.isEmpty()) return
        
        syncJob = viewModelScope.launch {
            val sharedCatId = getOrCreateSharedCategory()
            repository.registerMemberInFirestore(code)
            
            // 1. 이벤트 실시간 동기화
            launch {
                repository.startRealtimeSync(code, sharedCatId)
                    .catch { e -> android.util.Log.e("SyncError", "Sync failure", e) }
                    .collect()
            }

            // 2. 마감도장 실시간 동기화
            launch {
                repository.startDeadlineRealtimeSync(code)
                    .catch { e -> android.util.Log.e("SyncError", "Deadline sync failure", e) }
                    .collect()
            }

            // 3. 블랙리스트 실시간 동기화
            launch {
                repository.startBlacklistRealtimeSync(code)
                    .catch { e -> android.util.Log.e("SyncError", "Blacklist sync failure", e) }
                    .collect()
            }
        }
    }

    fun loginToRoom(code: String) {
        _roomCodeState.value = code
        _isLoggedIn.value = true
        _userName.value = userPreferences.getNickname()
        startSync(code)
    }

    fun logout() {
        userPreferences.setLastRoomCode("")
        _roomCodeState.value = ""
        _isLoggedIn.value = false
        syncJob?.cancel()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.blacklistDao.deleteSyncedItems()
        }
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

            // 2. Ensure only the three main default categories exist
            val updatedList = repository.eventDao.getAllCategoriesList()
            val defaultCategories = listOf(
                CalendarCategory(name = "내 캘린더", colorHex = "#1c62f2", accountName = "내 전화기", isVisible = true),
                CalendarCategory(name = "공휴일", colorHex = "#ff3b30", accountName = "기타", isVisible = true),
                CalendarCategory(name = "공유 캘린더", colorHex = "#34c759", accountName = "공유 계정", isVisible = true)
            )
            
            // Insert missing main categories if they don't exist
            for (defaultCat in defaultCategories) {
                val exists = updatedList.any { it.name == defaultCat.name }
                if (!exists) {
                    repository.eventDao.insertCategory(defaultCat)
                }
            }

            // Delete any categories that are NOT in the default categories list
            val finalCats = repository.eventDao.getAllCategoriesList()
            val toDelete = finalCats.filter { cat ->
                cat.name != "내 캘린더" && cat.name != "공유 캘린더" && cat.name != "공휴일"
            }
            if (toDelete.isNotEmpty()) {
                val toDeleteIds = toDelete.map { it.id }
                // Find target default category ID to re-map events (use "내 캘린더")
                val myCal = finalCats.find { it.name == "내 캘린더" } 
                    ?: repository.eventDao.getAllCategoriesList().find { it.name == "내 캘린더" }
                if (myCal != null) {
                    repository.eventDao.updateEventsCalendarId(toDeleteIds, myCal.id)
                }
                repository.eventDao.deleteCategories(toDelete)
            }

            // Start real-time Firestore sync
            val initialRoomCode = _roomCodeState.value
            if (initialRoomCode.isNotEmpty()) {
                startSync(initialRoomCode)
            }
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

    private fun expandEventsForRange(events: List<Event>, rangeStart: Long, rangeEnd: Long): List<Event> {
        val expandedList = mutableListOf<Event>()
        for (event in events) {
            if (event.repeatType == "NONE") {
                expandedList.add(event)
                continue
            }

            val calStart = Calendar.getInstance().apply { timeInMillis = event.startMillis }
            val duration = event.endMillis - event.startMillis
            val limitCal = Calendar.getInstance().apply { timeInMillis = rangeEnd }
            
            val currentCal = Calendar.getInstance().apply { 
                timeInMillis = event.startMillis 
            }
            
            // Fast forward DAILY/WEEKLY repeating events if they start way in the past
            if (currentCal.timeInMillis < rangeStart) {
                when (event.repeatType) {
                    "DAILY" -> {
                        val diffDays = (rangeStart - event.startMillis) / (24 * 60 * 60 * 1000L)
                        if (diffDays > 1) {
                            currentCal.add(Calendar.DAY_OF_YEAR, diffDays.toInt() - 1)
                        }
                    }
                    "WEEKLY" -> {
                        val diffWeeks = (rangeStart - event.startMillis) / (7 * 24 * 60 * 60 * 1000L)
                        if (diffWeeks > 1) {
                            currentCal.add(Calendar.WEEK_OF_YEAR, diffWeeks.toInt() - 1)
                        }
                    }
                }
            }
            
            var iterations = 0
            while (currentCal.timeInMillis <= limitCal.timeInMillis && iterations < 1000) {
                val instStart = currentCal.timeInMillis
                val instEnd = instStart + duration
                
                val overlaps = (instStart >= rangeStart && instStart <= rangeEnd) ||
                               (instEnd >= rangeStart && instEnd <= rangeEnd) ||
                               (instStart <= rangeStart && instEnd >= rangeEnd)
                
                if (overlaps) {
                    expandedList.add(
                        event.copy(
                            startMillis = instStart,
                            endMillis = instEnd
                        )
                    )
                }
                
                when (event.repeatType) {
                    "DAILY" -> currentCal.add(Calendar.DAY_OF_YEAR, 1)
                    "WEEKLY" -> currentCal.add(Calendar.WEEK_OF_YEAR, 1)
                    "MONTHLY" -> currentCal.add(Calendar.MONTH, 1)
                    "YEARLY" -> currentCal.add(Calendar.YEAR, 1)
                    else -> break
                }
                iterations++
            }
        }
        return expandedList
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyEvents: StateFlow<List<Event>> = combine(
        _currentMonth.flatMapLatest { cal ->
            val start = cal.timeInMillis - (45 * 24 * 60 * 60 * 1000L) // -45 days
            val end = cal.timeInMillis + (75 * 24 * 60 * 60 * 1000L) // +75 days
            repository.getEventsInRange(start, end).map { list ->
                expandEventsForRange(list, start, end)
            }
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
            compareBy<Event>(
                { if (it.isAllDay) 0 else 1 },
                { if (it.isAllDay) (it.teamId ?: 999) else 0 },
                {
                    if (it.isAllDay) {
                        when (it.slotPosition) {
                            "top" -> 0
                            "bottom" -> 1
                            "both" -> 2
                            else -> 3
                        }
                    } else {
                        0
                    }
                },
                { it.startMillis }
            )
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
            repository.getEventsInRange(start, end).map { list ->
                expandEventsForRange(list, start, end)
            }
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
            compareBy<Event>(
                { if (it.isAllDay) 0 else 1 },
                { if (it.isAllDay) (it.teamId ?: 999) else 0 },
                {
                    if (it.isAllDay) {
                        when (it.slotPosition) {
                            "top" -> 0
                            "bottom" -> 1
                            "both" -> 2
                            else -> 3
                        }
                    } else {
                        0
                    }
                },
                { it.startMillis }
            )
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
            compareBy<Event>(
                { if (it.isAllDay) 0 else 1 },
                { if (it.isAllDay) (it.teamId ?: 999) else 0 },
                {
                    if (it.isAllDay) {
                        when (it.slotPosition) {
                            "top" -> 0
                            "bottom" -> 1
                            "both" -> 2
                            else -> 3
                        }
                    } else {
                        0
                    }
                },
                { it.startMillis }
            )
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

    suspend fun getEventById(id: Int): Event? = repository.getEventById(id)

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
            repository.moveToTrash(event)
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

    suspend fun getEstimateById(estimateId: String): com.example.danallacalendar.estimate.Estimate? {
        android.util.Log.d("TEST", "getEstimateById 호출됨 id=$estimateId")
        android.util.Log.d("CalendarViewModel", "[getEstimateById] 조회 시작 - linkedEstimateId: $estimateId")

        // 1. linkedEstimateId로 원본 견적서 조회 (Room DB 우선, 없으면 Firestore)
        val pdf = estimatePdfDao.getPdfByEstimateId(estimateId)
        val originalEstimate = if (pdf != null) {
            try {
                com.google.gson.Gson().fromJson(pdf.estimateJson, com.example.danallacalendar.estimate.Estimate::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            estimateRepository.getEstimateFromFirestore(estimateId)
        }

        if (originalEstimate == null) {
            android.util.Log.d("CalendarViewModel", "[getEstimateById] 원본 견적서 조회 실패 - null 반환")
            return null
        }

        android.util.Log.d("CalendarViewModel", "[getEstimateById] 원본 견적서 조회 성공 - estimateId=${originalEstimate.id}, phoneNumber=${originalEstimate.phoneNumber}, createdAt=${originalEstimate.createdAt}")

        // 2. phoneNumber가 비어있으면 원본 그대로 반환
        val phoneNumber = originalEstimate.phoneNumber
        if (phoneNumber.isBlank()) {
            android.util.Log.d("CalendarViewModel", "[getEstimateById] phoneNumber 없음 - 원본 견적서 반환")
            return originalEstimate
        }

        // 3. Firestore에서 phoneNumber가 같은 견적서 전체 조회
        val roomCode = userPreferences.getLastRoomCode()
        val collectionRef = if (roomCode.isNotEmpty()) {
            firestore.collection("rooms").document(roomCode).collection("estimates")
        } else {
            firestore.collection("estimates")
        }

        val remoteEstimates = try {
            val snapshot = collectionRef
                .whereEqualTo("phoneNumber", phoneNumber)
                .get().await()
            snapshot.documents.mapNotNull { it.toObject(com.example.danallacalendar.estimate.Estimate::class.java) }
        } catch (e: Exception) {
            android.util.Log.e("CalendarViewModel", "[getEstimateById] Firestore phoneNumber 조회 실패", e)
            emptyList()
        }

        android.util.Log.d("CalendarViewModel", "[getEstimateById] Firestore 조회 결과: ${remoteEstimates.size}개")
        remoteEstimates.forEachIndexed { i, est ->
            android.util.Log.d("CalendarViewModel", "[getEstimateById]   Firestore[$i] estimateId=${est.id}, phoneNumber=${est.phoneNumber}, createdAt=${est.createdAt}")
        }

        // 4. createdAt 기준 가장 최신 견적서 반환
        val latest = (remoteEstimates + originalEstimate).maxByOrNull { it.createdAt }

        android.util.Log.d("CalendarViewModel", "[getEstimateById] 최종 선택 → estimateId=${latest?.id}, createdAt=${latest?.createdAt}")

        // 5. 없으면 원본 그대로 반환
        return latest ?: originalEstimate
    }


    suspend fun getEstimateByScheduleId(scheduleId: String): com.example.danallacalendar.estimate.Estimate? {
        android.util.Log.d("CalendarViewModel", "[getEstimateByScheduleId] 조회 시작 - scheduleId: $scheduleId")

        val remoteLatest = estimateRepository.getEstimateByScheduleId(scheduleId)
        android.util.Log.d("CalendarViewModel", "[getEstimateByScheduleId] Firestore 결과: estimateId=${remoteLatest?.id}, createdAt=${remoteLatest?.createdAt}")
        
        val allPdfs = estimatePdfDao.getAllPdfs().first()
        val localLatest = allPdfs.mapNotNull { p ->
            try {
                com.google.gson.Gson().fromJson(p.estimateJson, com.example.danallacalendar.estimate.Estimate::class.java)
            } catch (e: Exception) {
                null
            }
        }.filter { it.scheduleId == scheduleId }
         .maxByOrNull { it.createdAt }

        android.util.Log.d("CalendarViewModel", "[getEstimateByScheduleId] Room DB 결과: estimateId=${localLatest?.id}, createdAt=${localLatest?.createdAt}")

        val result = when {
            localLatest != null && remoteLatest != null ->
                if (remoteLatest.createdAt >= localLatest.createdAt) remoteLatest else localLatest
            remoteLatest != null -> remoteLatest
            localLatest != null -> localLatest
            else -> null
        }

        android.util.Log.d("CalendarViewModel", "[getEstimateByScheduleId] 최종 선택 → estimateId=${result?.id}, createdAt=${result?.createdAt}")
        return result
     }

    suspend fun findEstimateByPhoneNumber(phone: String): com.example.danallacalendar.estimate.Estimate? {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        if (cleanPhone.isEmpty()) return null

        // 1. Local Room DB search
        try {
            val localPdfs = estimatePdfDao.getAllPdfsList()
            for (pdf in localPdfs) {
                val est = com.google.gson.Gson().fromJson(pdf.estimateJson, com.example.danallacalendar.estimate.Estimate::class.java)
                val estCleanPhone = est.phoneNumber.replace(Regex("[^0-9]"), "")
                if (estCleanPhone.isNotEmpty() && estCleanPhone == cleanPhone) {
                    return est
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarViewModel", "Error searching local estimates by phone", e)
        }

        // 2. Remote Firestore search
        val roomCode = userPreferences.getLastRoomCode()
        if (roomCode.isNotEmpty()) {
            try {
                val snapshot = firestore.collection("rooms")
                    .document(roomCode)
                    .collection("estimates")
                    .get().await()
                for (doc in snapshot.documents) {
                    val est = doc.toObject(com.example.danallacalendar.estimate.Estimate::class.java)
                    if (est != null) {
                        val estCleanPhone = est.phoneNumber.replace(Regex("[^0-9]"), "")
                        if (estCleanPhone.isNotEmpty() && estCleanPhone == cleanPhone) {
                            return est
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Error searching remote estimates by phone", e)
            }
        }
        return null
    }

    suspend fun checkContractConflict(
        dateStr: String,
        teamId: Int,
        slotPos: String,
        excludeEventId: Int? = null
    ): List<Event> {
        return try {
            val dateCal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
            val parsedDate = dateFormat.parse(dateStr)
            if (parsedDate != null) {
                dateCal.time = parsedDate
            }
            
            dateCal.set(Calendar.HOUR_OF_DAY, 0)
            dateCal.set(Calendar.MINUTE, 0)
            dateCal.set(Calendar.SECOND, 0)
            dateCal.set(Calendar.MILLISECOND, 0)
            val startMillis = dateCal.timeInMillis
            
            dateCal.set(Calendar.HOUR_OF_DAY, 23)
            dateCal.set(Calendar.MINUTE, 59)
            dateCal.set(Calendar.SECOND, 59)
            dateCal.set(Calendar.MILLISECOND, 999)
            val endMillis = dateCal.timeInMillis

            val dayEvents = repository.eventDao.getEventsInRangeList(startMillis, endMillis)
            dayEvents.filter { event ->
                event.id != excludeEventId && event.teamId == teamId && when {
                    slotPos == "both" || event.slotPosition == "both" -> true
                    slotPos == "top" && event.slotPosition == "top" -> true
                    slotPos == "bottom" && event.slotPosition == "bottom" -> true
                    else -> false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarViewModel", "Error checking contract conflict", e)
            emptyList()
        }
    }

    suspend fun getEventsForDateString(dateStr: String): List<Event> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        return try {
            val parsedDate = dateFormat.parse(dateStr) ?: return emptyList()
            val cal = Calendar.getInstance()
            cal.time = parsedDate
            
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            
            repository.eventDao.getEventsInRangeList(start, end)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun updateEventTitleAndAssignment(
        event: Event,
        newTeamId: Int,
        newSlotPos: String,
        newTitle: String
    ) {
        viewModelScope.launch {
            try {
                val updatedColorHex = when (newTeamId) {
                    1 -> "#FF4CAF50"
                    2 -> "#FFFFEB3B"
                    else -> event.colorHex
                }
                val updatedEvent = event.copy(
                    teamId = newTeamId,
                    slotPosition = newSlotPos,
                    title = newTitle,
                    colorHex = updatedColorHex,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateEvent(updatedEvent)
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Failed to update event assignment", e)
            }
        }
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
