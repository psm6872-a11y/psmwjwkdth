package com.example.danallacalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.EstimatePdfDao
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.data.repository.CalendarRepository
import com.example.danallacalendar.estimate.Estimate
import com.example.danallacalendar.estimate.EstimateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val estimateRepository: EstimateRepository,
    private val estimatePdfDao: EstimatePdfDao,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    // 1. All events from local Room DB in real-time
    val allEvents: StateFlow<List<Event>> = calendarRepository.eventDao.getAllEventsFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    // 2. All estimates (merged from local and remote) in real-time
    val allEstimates: StateFlow<List<Estimate>> = combine(
        estimateRepository.getEstimatesFlow(),
        estimatePdfDao.getAllPdfs()
    ) { remoteList, localList ->
        val mergedMap = mutableMapOf<String, Estimate>()
        val gson = com.google.gson.Gson()
        
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
        
        remoteList.forEach { remoteEst ->
            val localMatch = mergedMap[remoteEst.id]
            mergedMap[remoteEst.id] = remoteEst.copy(
                isSynced = true,
                localFilePath = localMatch?.localFilePath
            )
        }
        
        mergedMap.values.toList()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
}
