package com.example.danallacalendar.estimate

import com.example.danallacalendar.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

sealed class SaveState {
    object Idle : SaveState()
    object Loading : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

@HiltViewModel
class EstimateViewModel @Inject constructor(
    private val repository: EstimateRepository
) : ViewModel() {

    // Form fields
    val customerName = MutableStateFlow("")
    val phoneNumber = MutableStateFlow("")
    val departure = MutableStateFlow("")
    val destination = MutableStateFlow("")
    val moveDate = MutableStateFlow("")
    val moveType = MutableStateFlow("가정이사")
    val cargoSize = MutableStateFlow("중")
    val amount = MutableStateFlow("")
    val memo = MutableStateFlow("")
    val estimateDate = MutableStateFlow("")
    val startTime = MutableStateFlow("")

    // Room-specific cargo items: Map<SpaceName, Map<ItemName, Count>>
    private val _roomItems = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    val roomItems = _roomItems.asStateFlow()

    // Google Sheets WebApp URL configuration
    val googleSheetsUrl = MutableStateFlow("")

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    init {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        estimateDate.value = today
        moveDate.value = today
        googleSheetsUrl.value = BuildConfig.SPREADSHEET_WEB_APP_URL
    }

    fun updateItemCount(space: String, item: String, count: Int) {
        val current = _roomItems.value.toMutableMap()
        val spaceMap = current[space]?.toMutableMap() ?: mutableMapOf()
        if (count <= 0) {
            spaceMap.remove(item)
        } else {
            spaceMap[item] = count
        }
        if (spaceMap.isEmpty()) {
            current.remove(space)
        } else {
            current[space] = spaceMap
        }
        _roomItems.value = current
        autoSaveToGoogleSheets()
    }

    fun formatRoomItemsSummary(): String {
        val sb = StringBuilder()
        _roomItems.value.forEach { (space, items) ->
            if (items.isNotEmpty()) {
                sb.append("[$space] ")
                val itemsStr = items.map { "${it.key}:${it.value}개" }.joinToString(", ")
                sb.append(itemsStr).append("\n")
            }
        }
        return sb.toString().trim()
    }

    fun autoSaveToGoogleSheets() {
        val url = googleSheetsUrl.value
        if (url.isBlank()) return

        val amt = amount.value.toLongOrNull() ?: 0L
        val formattedCargo = formatRoomItemsSummary()
        val combinedMemo = if (formattedCargo.isNotBlank()) {
            if (memo.value.isNotBlank()) "$formattedCargo\n\n[메모]\n${memo.value}" else formattedCargo
        } else {
            memo.value
        }

        val estimate = Estimate(
            customerName = customerName.value.ifBlank { "임시 고객" },
            phoneNumber = phoneNumber.value,
            departure = departure.value,
            destination = destination.value,
            moveDate = moveDate.value,
            moveType = moveType.value,
            cargoSize = cargoSize.value,
            amount = amt,
            memo = combinedMemo,
            estimateDate = estimateDate.value,
            startTime = startTime.value
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveToGoogleSheets(url, estimate)
            } catch (e: Exception) {
                android.util.Log.e("EstimateViewModel", "Auto save to Google Sheets failed", e)
            }
        }
    }

    fun saveEstimate(onCompleted: (smsBody: String) -> Unit) {
        val amt = amount.value.toLongOrNull() ?: 0L
        val formattedCargo = formatRoomItemsSummary()
        val combinedMemo = if (formattedCargo.isNotBlank()) {
            if (memo.value.isNotBlank()) "$formattedCargo\n\n[메모]\n${memo.value}" else formattedCargo
        } else {
            memo.value
        }

        val estimate = Estimate(
            customerName = customerName.value,
            phoneNumber = phoneNumber.value,
            departure = departure.value,
            destination = destination.value,
            moveDate = moveDate.value,
            moveType = moveType.value,
            cargoSize = cargoSize.value,
            amount = amt,
            memo = combinedMemo,
            estimateDate = estimateDate.value,
            startTime = startTime.value
        )

        _saveState.value = SaveState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Save to Firestore
                val savedId = repository.saveToFirestore(estimate)
                val finalEstimate = estimate.copy(id = savedId)

                // 2. Save to Google Sheets
                autoSaveToGoogleSheets()

                _saveState.value = SaveState.Success
                
                // Formulate SMS message
                val formattedAmount = NumberFormat.getNumberInstance(Locale.KOREA).format(amt)
                val smsBody = """
                    [이사 견적서]
                    고객명: ${finalEstimate.customerName}
                    연락처: ${finalEstimate.phoneNumber}
                    이사일: ${finalEstimate.moveDate} ${if (finalEstimate.startTime.isNotBlank()) "(${finalEstimate.startTime})" else ""}
                    이사종류: ${finalEstimate.moveType}
                    출발지: ${finalEstimate.departure}
                    도착지: ${finalEstimate.destination}
                    견적금액: ${formattedAmount}원
                    
                    [이사 화물 정보]
                    ${if (formattedCargo.isNotBlank()) formattedCargo else "기본 정보"}
                    
                    ${if (finalEstimate.memo.isNotBlank() && finalEstimate.memo != formattedCargo) "[메모]\n${memo.value}" else ""}
                    견적일: ${finalEstimate.estimateDate}
                """.trimIndent()

                viewModelScope.launch(Dispatchers.Main) {
                    onCompleted(smsBody)
                }
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
