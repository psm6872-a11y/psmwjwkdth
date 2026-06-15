package com.example.danallacalendar.estimate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.EstimatePdf
import com.example.danallacalendar.data.EstimatePdfDao
import com.example.danallacalendar.data.local.UserPreferences
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EstimateListViewModel @Inject constructor(
    private val repository: EstimateRepository,
    private val userPreferences: UserPreferences,
    private val estimatePdfDao: EstimatePdfDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val processingIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val failedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _isShareEnabled = MutableStateFlow(userPreferences.isShareEnabled())
    val isShareEnabled: StateFlow<Boolean> = _isShareEnabled.asStateFlow()

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

            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val htmlContent = EstimateHtmlGenerator.generateEstimateHtml(context, estimate)
                val jpgPath = EstimatePrintHelper.renderHtmlToJpg(context, htmlContent, estimate)
                if (jpgPath != null) {
                    val jpgFile = java.io.File(jpgPath)
                    val fileName = jpgFile.name
                    val uploadResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        GoogleDriveHelper.uploadEstimateJpgWithResult(
                            context,
                            account,
                            jpgFile,
                            fileName,
                            estimate.estimateDate
                        )
                    }
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
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            estimatePdfDao.insertPdf(pdfEntity)
                        }
                        android.util.Log.d("EstimateListViewModel", "Auto backup success for estimate: ${estimate.id}")
                        true
                    } else {
                        android.util.Log.w("EstimateListViewModel", "Auto backup upload result: $uploadResult for estimate: ${estimate.id}")
                        false
                    }
                } else {
                    android.util.Log.e("EstimateListViewModel", "Failed to render HTML to JPG for ${estimate.id}")
                    false
                }
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
}
