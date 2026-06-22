package com.example.danallacalendar.estimate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.EstimatePdf
import com.example.danallacalendar.data.EstimatePdfDao
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.repository.CalendarRepository
import com.example.danallacalendar.ui.screens.settingsDataStore
import com.example.danallacalendar.ui.screens.TeamConfigs
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EstimateListViewModel @Inject constructor(
    private val repository: EstimateRepository,
    private val userPreferences: UserPreferences,
    private val estimatePdfDao: EstimatePdfDao,
    private val calendarRepository: CalendarRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val processingIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val failedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _isShareEnabled = MutableStateFlow(userPreferences.isShareEnabled())
    val isShareEnabled: StateFlow<Boolean> = _isShareEnabled.asStateFlow()

    val linkedEvents: StateFlow<List<Event>> = calendarRepository.eventDao.getLinkedEventsFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun toggleShareEnabled(enabled: Boolean) {
        userPreferences.setShareEnabled(enabled)
        _isShareEnabled.value = enabled
    }

    private val _isGoogleDriveSaveEnabled = MutableStateFlow(userPreferences.isGoogleDriveSaveEnabled())
    val isGoogleDriveSaveEnabled: StateFlow<Boolean> = _isGoogleDriveSaveEnabled.asStateFlow()

    fun toggleGoogleDriveSaveEnabled(enabled: Boolean) {
        userPreferences.setGoogleDriveSaveEnabled(enabled)
        _isGoogleDriveSaveEnabled.value = enabled
    }

    private val _isAutoDriveSyncEnabled = MutableStateFlow(userPreferences.isAutoDriveSyncEnabled())
    val isAutoDriveSyncEnabled: StateFlow<Boolean> = _isAutoDriveSyncEnabled.asStateFlow()

    fun toggleAutoDriveSyncEnabled(enabled: Boolean) {
        userPreferences.setAutoDriveSyncEnabled(enabled)
        _isAutoDriveSyncEnabled.value = enabled
        if (enabled) {
            failedIds.clear()
        }
    }

    private val _googleAccount = MutableStateFlow<GoogleSignInAccount?>(GoogleDriveHelper.getSignedInAccount(context))
    val googleAccount: StateFlow<GoogleSignInAccount?> = _googleAccount.asStateFlow()

    fun updateGoogleAccount(account: GoogleSignInAccount?) {
        _googleAccount.value = account
        if (account == null) {
            toggleGoogleDriveSaveEnabled(false)
        } else {
            failedIds.clear()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val estimateList: StateFlow<List<Estimate>> = combine(
        repository.getEstimatesFlow(),
        estimatePdfDao.getAllPdfs(),
        searchQuery
    ) { remoteList, localList, query ->
        val mergedMap = mutableMapOf<String, Estimate>()
        val gson = com.google.gson.Gson()
        
        // 1. 로컬 캐시 항목들을 머지 맵에 로드
        localList.forEach { pdf ->
            val localEst = try {
                gson.fromJson(pdf.estimateJson, Estimate::class.java).copy(
                    isSynced = pdf.isSynced,
                    localFilePath = pdf.filePath
                )
            } catch (e: Exception) {
                Estimate(
                    id = pdf.estimateId.ifBlank { pdf.id.toString() },
                    estimateDate = pdf.date,
                    customerName = pdf.customerName,
                    phoneNumber = pdf.phoneNumber,
                    departure = pdf.departure,
                    isSynced = pdf.isSynced,
                    localFilePath = pdf.filePath,
                    createdAt = pdf.createdAt
                )
            }
            mergedMap[localEst.id] = localEst
        }
        
        // 2. 원격 Firestore 항목으로 덮어씌움 (동기화 완료 상태)
        remoteList.forEach { remoteEst ->
            val localMatch = mergedMap[remoteEst.id]
            mergedMap[remoteEst.id] = remoteEst.copy(
                isSynced = true,
                localFilePath = localMatch?.localFilePath
            )
        }
        
        val sortedList = mergedMap.values.sortedByDescending { it.createdAt }
        if (query.isBlank()) {
            sortedList
        } else {
            val trimmed = query.trim().lowercase(java.util.Locale.getDefault())
            sortedList.filter { est ->
                est.customerName.lowercase(java.util.Locale.getDefault()).contains(trimmed) ||
                est.phoneNumber.lowercase(java.util.Locale.getDefault()).contains(trimmed) ||
                est.departure.lowercase(java.util.Locale.getDefault()).contains(trimmed)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            // 앱 시작 직후 Firestore flow가 즉시 방출되면 WebView 초기화 전에 충돌이 발생할 수 있음
            // 3초 지연 후 자동 백업을 시작하여 안정성 확보
            delay(3000L)
            try {
                combine(
                    repository.getEstimatesFlow(),
                    estimatePdfDao.getAllPdfs(),
                    isAutoDriveSyncEnabled,
                    googleAccount
                ) { remoteList, localList, autoSync, account ->
                    if (autoSync && account != null) {
                        val localMap = localList.associateBy { it.estimateId.ifBlank { it.id.toString() } }
                        remoteList.filter { remoteEst ->
                            val localPdf = localMap[remoteEst.id]
                            localPdf?.filePath.isNullOrBlank()
                        }
                    } else {
                        emptyList()
                    }
                }
                .distinctUntilChanged() // 동일한 목록이 재방출될 때 중복 처리 방지
                .collect { targets ->
                    val pendingTargets = targets.filter {
                        !processingIds.contains(it.id) && !failedIds.contains(it.id)
                    }
                    if (pendingTargets.isNotEmpty()) {
                        android.util.Log.d("EstimateListViewModel", "Auto backup: ${pendingTargets.size} items pending")
                    }
                    for (target in pendingTargets) {
                        // 각 항목 사이에 짧은 지연을 두어 메인 스레드 과부하 방지
                        delay(500L)
                        autoBackupToGoogleDriveSequentially(target)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EstimateListViewModel", "Auto backup flow error", e)
            }
        }
    }

    fun syncEstimate(estimate: Estimate) {
        viewModelScope.launch {
            try {
                // 1. Firestore에 동기화 업로드
                val savedId = repository.saveToFirestore(estimate.copy(isSynced = true))
                // 2. 로컬 DB 동기화 완료 처리
                estimatePdfDao.updateSyncStatus(estimate.id, savedId, true)
            } catch (e: Exception) {
                android.util.Log.e("EstimateListViewModel", "Failed to sync estimate: ${estimate.id}", e)
            }
        }
    }

    fun deleteEstimate(estimate: Estimate) {
        viewModelScope.launch {
            try {
                repository.deleteFromFirestore(estimate.id)
            } catch (e: Exception) {
                android.util.Log.e("EstimateListViewModel", "Failed to delete remote estimate: ${estimate.id}", e)
            }
            try {
                estimatePdfDao.deleteByEstimateId(estimate.id)
            } catch (e: Exception) {
                android.util.Log.e("EstimateListViewModel", "Failed to delete local estimate: ${estimate.id}", e)
            }
        }
    }

    private suspend fun autoBackupToGoogleDriveSequentially(estimate: Estimate) {
        if (!processingIds.add(estimate.id)) return
        try {
            val account = GoogleDriveHelper.getSignedInAccount(context) ?: throw Exception("No signed in account")
            if (!GoogleDriveHelper.hasDrivePermission(context)) {
                android.util.Log.w("EstimateListViewModel", "Auto backup skipped: Drive permission not granted for ${estimate.id}")
                failedIds.add(estimate.id)
                return
            }

            // 1. HTML generation on Dispatchers.Default
            val htmlContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                EstimateHtmlGenerator.generateEstimateHtml(context, estimate)
            }

            // 2. HTML to JPG rendering (WebView needs Main thread)
            val jpgPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                EstimatePrintHelper.renderHtmlToJpg(context, htmlContent, estimate)
            }

            // 3. Upload & DB Caching on Dispatchers.IO
            val result = if (jpgPath != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val jpgFile = java.io.File(jpgPath)
                    val fileName = jpgFile.name
                    val uploadResult = GoogleDriveHelper.uploadEstimateJpgWithResult(
                        context,
                        account,
                        jpgFile,
                        fileName,
                        estimate.estimateDate
                    )
                    if (uploadResult is GoogleDriveHelper.UploadResult.Success) {
                        val gson = com.google.gson.Gson()
                        val dateParts = estimate.estimateDate.split("-")
                        val monthDay = if (dateParts.size >= 3) "${dateParts[1]}-${dateParts[2]}" else "00-00"
                        val pdfEntity = com.example.danallacalendar.data.EstimatePdf(
                            date = monthDay,
                            fileName = fileName,
                            filePath = jpgPath,
                            estimateId = estimate.id,
                            customerName = estimate.customerName,
                            phoneNumber = estimate.phoneNumber,
                            moveDate = estimate.moveDate,
                            departure = estimate.departure,
                            estimateJson = gson.toJson(estimate),
                            isSynced = true
                        )
                        estimatePdfDao.insertPdf(pdfEntity)
                        android.util.Log.d("EstimateListViewModel", "Auto backup success for estimate: ${estimate.id}")
                        true
                    } else {
                        android.util.Log.w("EstimateListViewModel", "Auto backup upload result: $uploadResult for estimate: ${estimate.id}")
                        false
                    }
                }
            } else {
                android.util.Log.e("EstimateListViewModel", "Failed to render HTML to JPG for ${estimate.id}")
                false
            }

            if (!result) {
                failedIds.add(estimate.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("EstimateListViewModel", "Auto backup failed for estimate: ${estimate.id}", e)
            failedIds.add(estimate.id)
        } finally {
            processingIds.remove(estimate.id)
        }
    }

    suspend fun getOrCreateSharedCategory(calendarRepository: CalendarRepository): Int {
        val list = calendarRepository.eventDao.getAllCategoriesList()
        val found = list.find { it.name == "공유 캘린더" }
        return if (found != null) {
            found.id
        } else {
            calendarRepository.eventDao.insertCategory(
                com.example.danallacalendar.data.CalendarCategory(
                    name = "공유 캘린더",
                    colorHex = "#34c759", // Green color for synced calendar
                    accountName = "공유 계정",
                    isVisible = true
                )
            ).toInt()
        }
    }

    fun confirmContract(
        estimate: Estimate,
        teamId: Int,
        slotPos: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val sharedCategoryId = getOrCreateSharedCategory(calendarRepository)
                
                // date 파싱 (yyyy-MM-dd)
                val dateCal = java.util.Calendar.getInstance()
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREAN)
                try {
                    val parsedDate = dateFormat.parse(estimate.moveDate)
                    if (parsedDate != null) {
                        dateCal.time = parsedDate
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EstimateListViewModel", "Error parsing moveDate: ${estimate.moveDate}", e)
                }
                
                dateCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                dateCal.set(java.util.Calendar.MINUTE, 0)
                dateCal.set(java.util.Calendar.SECOND, 0)
                dateCal.set(java.util.Calendar.MILLISECOND, 0)
                val startMillis = dateCal.timeInMillis
                
                dateCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                dateCal.set(java.util.Calendar.MINUTE, 59)
                dateCal.set(java.util.Calendar.SECOND, 59)
                dateCal.set(java.util.Calendar.MILLISECOND, 999)
                val endMillis = dateCal.timeInMillis
                
                // settingsDataStore에서 팀 설정 읽기
                val preferences = context.settingsDataStore.data.first()
                val teamConfigs = TeamConfigs.map { config ->
                    val name = preferences[config.nameKey] ?: config.defaultName
                    val color = preferences[config.colorKey] ?: config.defaultColor
                    name to color
                }
                
                val teamPref = teamConfigs.getOrNull(teamId - 1) ?: ("" to 0xFF4CAF50L)
                val teamName = teamPref.first
                val teamColorLong = teamPref.second
                
                val resolvedVolume = if (estimate.totalVolume.isNotBlank()) {
                    if (estimate.totalVolume.contains("톤")) estimate.totalVolume else "${estimate.totalVolume}톤"
                } else {
                    "-"
                }

                val resolvedStartTime = if (estimate.startTime.isNotBlank()) {
                    estimate.startTime
                } else {
                    "-"
                }

                val departureFirstWord = run {
                    val mainPart = estimate.departure.split("|").firstOrNull() ?: ""
                    val firstWord = mainPart.trim().split(" ").firstOrNull() ?: ""
                    if (firstWord.isNotBlank()) firstWord else "-"
                }

                val destinationFirstWord = run {
                    val mainPart = estimate.destination.split("|").firstOrNull() ?: ""
                    val firstWord = mainPart.trim().split(" ").firstOrNull() ?: ""
                    if (firstWord.isNotBlank()) firstWord else "-"
                }

                val departureFloor = if (estimate.departureFloorType.isNotBlank()) estimate.departureFloorType else "-"
                val destinationFloor = if (estimate.destinationFloorType.isNotBlank()) estimate.destinationFloorType else "-"

                val titleText = "$teamName. $resolvedStartTime. $resolvedVolume\n$departureFirstWord. $destinationFirstWord. $departureFloor/$destinationFloor"
                
                val departureAddr: String
                val departureDetail: String
                if (estimate.departure.contains("|")) {
                    val parts = estimate.departure.split("|")
                    departureAddr = parts.getOrNull(0) ?: ""
                    departureDetail = parts.getOrNull(1) ?: ""
                } else {
                    departureAddr = estimate.departure
                    departureDetail = ""
                }
                
                val destinationAddr: String
                val destinationDetail: String
                if (estimate.destination.contains("|")) {
                    val parts = estimate.destination.split("|")
                    destinationAddr = parts.getOrNull(0) ?: ""
                    destinationDetail = parts.getOrNull(1) ?: ""
                } else {
                    destinationAddr = estimate.destination
                    destinationDetail = ""
                }
                
                val locationField = "$departureAddr|||$departureDetail|||$destinationAddr|||$destinationDetail"
                
                val notesField = estimate.phoneNumber
                val colorHexField = String.format("#%08X", teamColorLong)
                
                val newEvent = com.example.danallacalendar.data.Event(
                    title = titleText,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    isAllDay = true,
                    location = locationField,
                    notes = notesField,
                    colorHex = colorHexField,
                    calendarId = sharedCategoryId,
                    syncId = java.util.UUID.randomUUID().toString(),
                    isSynced = true,
                    linkedEstimateId = estimate.id,
                    teamId = teamId,
                    slotPosition = slotPos,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                calendarRepository.insertEvent(newEvent)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("EstimateListViewModel", "Failed to confirm contract from estimate list", e)
                onError(e)
            }
        }
    }
}
