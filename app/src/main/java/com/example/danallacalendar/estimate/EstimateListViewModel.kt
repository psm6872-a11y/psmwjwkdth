package com.example.danallacalendar.estimate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.EstimatePdf
import com.example.danallacalendar.data.EstimatePdfDao
import com.example.danallacalendar.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val estimatePdfDao: EstimatePdfDao
) : ViewModel() {

    private val _isShareEnabled = MutableStateFlow(userPreferences.isShareEnabled())
    val isShareEnabled: StateFlow<Boolean> = _isShareEnabled.asStateFlow()

    fun toggleShareEnabled(enabled: Boolean) {
        userPreferences.setShareEnabled(enabled)
        _isShareEnabled.value = enabled
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
}
