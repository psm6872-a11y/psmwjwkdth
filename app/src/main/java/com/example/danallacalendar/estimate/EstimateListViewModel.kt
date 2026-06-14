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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    }

    private val _googleAccount = MutableStateFlow<GoogleSignInAccount?>(GoogleDriveHelper.getSignedInAccount(context))
    val googleAccount: StateFlow<GoogleSignInAccount?> = _googleAccount.asStateFlow()

    fun updateGoogleAccount(account: GoogleSignInAccount?) {
        _googleAccount.value = account
        if (account == null) {
            toggleGoogleDriveSaveEnabled(false)
        }
    }

    val estimateList: StateFlow<List<Estimate>> = combine(
        repository.getEstimatesFlow(),
        estimatePdfDao.getAllPdfs()
    ) { remoteList, localList ->
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
        
        mergedMap.values.sortedByDescending { it.createdAt }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val processingIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            estimateList.collect { list ->
                if (userPreferences.isAutoDriveSyncEnabled() && GoogleDriveHelper.getSignedInAccount(context) != null) {
                    val targets = list.filter { it.isSynced && it.localFilePath.isNullOrEmpty() && !processingIds.contains(it.id) }
                    if (targets.isNotEmpty()) {
                        targets.forEach { target ->
                            autoBackupToGoogleDrive(target)
                        }
                    }
                }
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

    private fun autoBackupToGoogleDrive(estimate: Estimate) {
        if (!processingIds.add(estimate.id)) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val account = GoogleDriveHelper.getSignedInAccount(context)
                if (account == null) {
                    processingIds.remove(estimate.id)
                    return@launch
                }
                val htmlContent = EstimateHtmlGenerator.generateEstimateHtml(context, estimate)
                val jpgPath = EstimatePrintHelper.renderHtmlToJpg(context, htmlContent, estimate)
                if (jpgPath != null) {
                    val jpgFile = java.io.File(jpgPath)
                    val fileName = jpgFile.name
                    val fileId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        GoogleDriveHelper.uploadEstimateJpg(
                            context,
                            account,
                            jpgFile,
                            fileName,
                            estimate.estimateDate
                        )
                    }
                    if (fileId != null) {
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
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EstimateListViewModel", "Auto backup failed for estimate: ${estimate.id}", e)
            } finally {
                processingIds.remove(estimate.id)
            }
        }
    }
}
