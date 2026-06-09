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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

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
    val startTime = MutableStateFlow("07시 00분")
    val visitDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(java.util.Date()))
    val moveInfo = MutableStateFlow("포장이사")
    val totalVolume = MutableStateFlow("")
    val workersM = MutableStateFlow("4")
    val workersF = MutableStateFlow("1")
    val laddersStartFloor = MutableStateFlow("")
    val laddersStartCost = MutableStateFlow("")
    val laddersEndFloor = MutableStateFlow("")
    val laddersEndCost = MutableStateFlow("")
    val extraTruck = MutableStateFlow("")
    val moveCost = MutableStateFlow("")
    val totalCost = MutableStateFlow("")
    val deposit = MutableStateFlow("")
    val balance = MutableStateFlow("")
    val optionCost = MutableStateFlow("")

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

    // 이사비용 또는 옵션비용 변경 시 총비용 자동계산
    fun onMoveCostOrOptionChanged() {
        val move = moveCost.value.toLongOrNull() ?: 0L
        val option = optionCost.value.toLongOrNull() ?: 0L
        val total = move + option
        totalCost.value = if (total > 0) total.toString() else ""
        // 잔금도 자동계산
        val depositVal = deposit.value.toLongOrNull() ?: 0L
        balance.value = if (total > 0) (total - depositVal).toString() else ""
    }

    // 계약금 변경 시 잔금 자동계산
    fun onDepositChanged() {
        val total = totalCost.value.toLongOrNull() ?: 0L
        val dep = deposit.value.toLongOrNull() ?: 0L
        balance.value = if (total > 0) (total - dep).toString() else ""
    }

    // 출발지사다리 금액 또는 도착지사다리 금액 변경 시 옵션비용 자동계산
    fun onLadderCostChanged() {
        val start = laddersStartCost.value.toLongOrNull() ?: 0L
        val end = laddersEndCost.value.toLongOrNull() ?: 0L
        val option = start + end
        optionCost.value = if (option > 0) option.toString() else ""
        // 옵션비용 변경됐으니 총비용/잔금도 연쇄 자동계산
        onMoveCostOrOptionChanged()
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

                // 2. Apps Script POST payload formatting
                val roomItemsFormatted = roomItems.value.mapValues { (_, items) ->
                    items.entries.joinToString("\n") { (item, count) ->
                        if (count > 1) "$item x$count" else item
                    }
                }
                
                val payload = JSONObject().apply {
                    put("customerName", customerName.value)
                    put("phoneNumber", phoneNumber.value)
                    put("departure", departure.value)
                    put("destination", destination.value)
                    put("moveDate", moveDate.value)
                    put("startTime", startTime.value)
                    put("moveType", moveType.value)
                    put("estimateDate", estimateDate.value)
                    put("totalVolume", totalVolume.value)
                    put("workersM", workersM.value)
                    put("workersF", workersF.value)
                    put("laddersStartFloor", laddersStartFloor.value)
                    put("laddersStartCost", laddersStartCost.value)
                    put("laddersEndFloor", laddersEndFloor.value)
                    put("laddersEndCost", laddersEndCost.value)
                    put("extraTruck", extraTruck.value)
                    put("moveCost", moveCost.value)
                    put("optionCost", optionCost.value)
                    put("totalCost", totalCost.value)
                    put("deposit", deposit.value)
                    put("balance", balance.value)
                    put("memo", memo.value)
                    put("roomItems", JSONObject(roomItemsFormatted))
                }

                // OkHttp POST request for Apps Script
                val url = BuildConfig.SPREADSHEET_WEB_APP_URL
                if (url.isNotBlank()) {
                    val client = okhttp3.OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = payload.toString().toRequestBody(mediaType)
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .post(body)
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            throw java.io.IOException("스프레드시트 전송 실패: ${response.code}, body: $errorBody")
                        }
                    }
                }

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
                android.util.Log.e("EstimateViewModel", "saveEstimate failed with exception details", e)
                _saveState.value = SaveState.Error(e.toString())
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
