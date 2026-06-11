package com.example.danallacalendar.estimate

import com.example.danallacalendar.BuildConfig
import com.example.danallacalendar.data.EstimatePdf
import com.example.danallacalendar.data.EstimatePdfDao
import androidx.lifecycle.SavedStateHandle
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
    private val repository: EstimateRepository,
    private val estimatePdfDao: EstimatePdfDao,
    private val savedStateHandle: SavedStateHandle
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

    private var estimateId: String = ""

    init {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        estimateDate.value = today
        googleSheetsUrl.value = BuildConfig.SPREADSHEET_WEB_APP_URL

        // 캘린더 일정 연동 인자 파싱 및 바인딩
        try {
            val argMoveDate = savedStateHandle.get<String>("moveDate")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val argDeparture = savedStateHandle.get<String>("departure")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val argDestination = savedStateHandle.get<String>("destination")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val argPhone = savedStateHandle.get<String>("phone")?.let { java.net.URLDecoder.decode(it, "UTF-8") }

            if (!argMoveDate.isNullOrBlank()) {
                moveDate.value = argMoveDate
            }
            if (!argDeparture.isNullOrBlank()) {
                departure.value = argDeparture
            }
            if (!argDestination.isNullOrBlank()) {
                destination.value = argDestination
            }
            if (!argPhone.isNullOrBlank()) {
                phoneNumber.value = argPhone
            }
        } catch (e: Exception) {
            android.util.Log.e("EstimateViewModel", "Error decoding navigation arguments", e)
        }
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
        val amt = amount.value.toLongOrNull() ?: 0L
        val formattedCargo = formatRoomItemsSummary()
        val combinedMemo = if (formattedCargo.isNotBlank()) {
            if (memo.value.isNotBlank()) "$formattedCargo\n\n[메모]\n${memo.value}" else formattedCargo
        } else {
            memo.value
        }

        val estimate = Estimate(
            id = estimateId,
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
                val savedId = repository.saveToFirestore(estimate)
                estimateId = savedId
            } catch (e: Exception) {
                android.util.Log.e("EstimateViewModel", "Auto save to Firestore failed", e)
            }
        }
    }

    fun saveEstimate(context: android.content.Context, onCompleted: (smsBody: String, pdfPath: String?) -> Unit) {
        android.util.Log.d("EstimateViewModel", "[LOG] saveEstimate function entered")
        val amt = amount.value.toLongOrNull() ?: 0L
        val formattedCargo = formatRoomItemsSummary()
        val combinedMemo = if (formattedCargo.isNotBlank()) {
            if (memo.value.isNotBlank()) "$formattedCargo\n\n[메모]\n${memo.value}" else formattedCargo
        } else {
            memo.value
        }

        val estimate = Estimate(
            id = estimateId,
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
            var savedPdfPath: String? = null
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
                    put("visitDate", visitDate.value)
                    put("moveDate", moveDate.value)
                    put("startTime", startTime.value)
                    put("moveType", moveType.value)
                    put("moveInfo", moveInfo.value)
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
                val url = googleSheetsUrl.value.ifBlank { BuildConfig.SPREADSHEET_WEB_APP_URL }
                android.util.Log.d("EstimateViewModel", "[LOG] SPREADSHEET_WEB_APP_URL value: '$url'")
                if (url.isNotBlank()) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = payload.toString().toRequestBody(mediaType)
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .post(body)
                        .build()
                    android.util.Log.d("EstimateViewModel", "[LOG] Sending POST request to URL...")
                    client.newCall(request).execute().use { response ->
                        android.util.Log.d("EstimateViewModel", "[LOG] POST response code: ${response.code}")
                        val responseBody = response.body?.string() ?: ""
                        android.util.Log.d("EstimateViewModel", "Response Body: $responseBody")
                        if (!response.isSuccessful) {
                            throw java.io.IOException("스프레드시트 전송 실패: ${response.code}, body: $responseBody")
                        }
                        
                        try {
                            val jsonObj = org.json.JSONObject(responseBody)
                            val pdfBase64 = jsonObj.optString("pdfBase64", "")
                            val pdfFileId = jsonObj.optString("pdfFileId", "")
                            val debugInfo = jsonObj.optJSONObject("debugInfo")
                            if (debugInfo != null) {
                                android.util.Log.d("EstimateViewModel", "Sheet Debug Info (B9 diagnosis): $debugInfo")
                            }
                            if (pdfBase64.isNotBlank()) {
                                val dateParts = moveDate.value.split("-")
                                val monthDay = if (dateParts.size >= 3) "${dateParts[1]}-${dateParts[2]}" else "00-00"
                                val rawPhone = phoneNumber.value.replace("-", "").trim()
                                val last4 = if (rawPhone.length >= 4) rawPhone.takeLast(4) else "0000"
                                val fileName = "${monthDay}_$last4.pdf"

                                // MMS 첨부용 로컬 임시 캐시 디렉터리에 저장
                                val tempDir = java.io.File(context.cacheDir, "danalla_temp")
                                if (!tempDir.exists()) {
                                    tempDir.mkdirs()
                                }
                                try {
                                    tempDir.listFiles()?.forEach { it.delete() }
                                } catch (e: Exception) {
                                    android.util.Log.e("EstimateViewModel", "Failed to clean temp directory", e)
                                }

                                val pdfFile = java.io.File(tempDir, fileName)

                                val pdfBytes = android.util.Base64.decode(pdfBase64, android.util.Base64.DEFAULT)
                                java.io.FileOutputStream(pdfFile).use { fos ->
                                    fos.write(pdfBytes)
                                }

                                // DB에는 구글 드라이브 파일 ID를 저장
                                val estimatePdf = EstimatePdf(
                                    date = monthDay,
                                    fileName = fileName,
                                    filePath = pdfFileId
                                )
                                estimatePdfDao.insertPdf(estimatePdf)
                                savedPdfPath = pdfFile.absolutePath
                                android.util.Log.d("EstimateViewModel", "PDF Cached at: ${pdfFile.absolutePath}, Drive File ID: $pdfFileId")
                            }
                        } catch (pdfEx: Exception) {
                            android.util.Log.e("EstimateViewModel", "PDF Save or DB Insert failed", pdfEx)
                        }
                    }
                } else {
                    android.util.Log.w("EstimateViewModel", "[LOG] URL is blank, skipping POST request")
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
                    onCompleted(smsBody, savedPdfPath)
                }
            } catch (e: Exception) {
                android.util.Log.e("EstimateViewModel", "[LOG] Exception occurred during saveEstimate", e)
                _saveState.value = SaveState.Error(e.toString())
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
