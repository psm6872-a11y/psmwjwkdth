package com.example.danallacalendar.estimate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.danallacalendar.data.EstimatePdf
import com.example.danallacalendar.data.EstimatePdfDao
import com.example.danallacalendar.data.local.UserPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import java.io.File

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
    private val savedStateHandle: SavedStateHandle,
    private val userPreferences: UserPreferences,
    private val calendarRepository: com.example.danallacalendar.data.repository.CalendarRepository
) : ViewModel() {

    var isSaved = false

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

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    // 구글 드라이브 업로드 진행 중 여부 (나가기 버튼 로딩 표시용)
    private val _isDriveUploading = MutableStateFlow(false)
    val isDriveUploading = _isDriveUploading.asStateFlow()

    private var estimateId: String = ""

    val copyFromEstimateJson: String?
        get() {
            val json = savedStateHandle.get<String>("copyFromEstimateJson")
            return if (json.isNullOrBlank() || json == "null") null else json
        }

    val isCopyMode: Boolean
        get() = copyFromEstimateJson != null

    init {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        estimateDate.value = today

        // 캘린더 일정 연동 인자 파싱 및 바인딩
        try {
            val argMoveDate = savedStateHandle.get<String>("moveDate")
            val argDeparture = savedStateHandle.get<String>("departure")
            val argDestination = savedStateHandle.get<String>("destination")
            val argPhone = savedStateHandle.get<String>("phone")

            val decodeParam = { value: String? ->
                if (value == null) ""
                else {
                    try {
                        java.net.URLDecoder.decode(value, "UTF-8")
                    } catch (e: Exception) {
                        value
                    }
                }
            }

            if (!argMoveDate.isNullOrBlank()) {
                moveDate.value = decodeParam(argMoveDate)
            }
            if (!argDeparture.isNullOrBlank()) {
                departure.value = decodeParam(argDeparture)
            }
            if (!argDestination.isNullOrBlank()) {
                destination.value = decodeParam(argDestination)
            }
            if (!argPhone.isNullOrBlank()) {
                phoneNumber.value = decodeParam(argPhone)
            }
        } catch (e: Exception) {
            android.util.Log.e("EstimateViewModel", "Error decoding navigation arguments", e)
        }

        // 기존 견적서 복사 처리
        val copyFromEstimateJson = savedStateHandle.get<String>("copyFromEstimateJson")
        android.util.Log.d("EstimateViewModel", "[COPY] copyFromEstimateJson parameter received: ${copyFromEstimateJson?.length ?: 0} chars")
        if (!copyFromEstimateJson.isNullOrBlank()) {
            loadAndCopyEstimateFromJson(copyFromEstimateJson)
        }
    }

    private fun loadAndCopyEstimateFromJson(estimateJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("EstimateViewModel", "[COPY] Decoding and parsing estimateJson directly...")
                val decodedJson = try {
                    java.net.URLDecoder.decode(estimateJson, "UTF-8")
                } catch (e: Exception) {
                    estimateJson
                }

                val gson = com.google.gson.Gson()
                val originalEstimate = gson.fromJson(decodedJson, Estimate::class.java)
                android.util.Log.d("EstimateViewModel", "[COPY] Deserialized originalEstimate: ${originalEstimate.customerName}, phone: ${originalEstimate.phoneNumber}")
                
                withContext(Dispatchers.Main) {
                    // UI 필드 복사 (ID는 신규 생성을 위해 의도적으로 빈 값 "" 세팅)
                    estimateId = "" 
                    customerName.value = originalEstimate.customerName
                    phoneNumber.value = originalEstimate.phoneNumber
                    departure.value = originalEstimate.departure
                    destination.value = originalEstimate.destination
                    moveDate.value = originalEstimate.moveDate
                    moveType.value = originalEstimate.moveType
                    cargoSize.value = originalEstimate.cargoSize
                    amount.value = if (originalEstimate.amount > 0) originalEstimate.amount.toString() else ""
                    memo.value = originalEstimate.memo
                    estimateDate.value = originalEstimate.estimateDate
                    startTime.value = originalEstimate.startTime
                    visitDate.value = originalEstimate.visitDate
                    moveInfo.value = originalEstimate.moveInfo
                    totalVolume.value = originalEstimate.totalVolume
                    workersM.value = originalEstimate.workersM
                    workersF.value = originalEstimate.workersF
                    laddersStartFloor.value = originalEstimate.laddersStartFloor
                    laddersStartCost.value = originalEstimate.laddersStartCost
                    laddersEndFloor.value = originalEstimate.laddersEndFloor
                    laddersEndCost.value = originalEstimate.laddersEndCost
                    extraTruck.value = originalEstimate.extraTruck
                    moveCost.value = originalEstimate.moveCost
                    totalCost.value = originalEstimate.totalCost
                    deposit.value = originalEstimate.deposit
                    balance.value = originalEstimate.balance
                    optionCost.value = originalEstimate.optionCost

                    // roomItems 복사 (Long -> Int 변환)
                    val convertedItems = originalEstimate.roomItems.mapValues { (_, innerMap) ->
                        innerMap.mapValues { (_, value) -> value.toInt() }
                    }
                    _roomItems.value = convertedItems
                    android.util.Log.d("EstimateViewModel", "[COPY] All fields assigned on Main thread from JSON successfully.")
                }

                // 복제본 생성 시점에 Firestore에 바로 자동 저장하여 새로운 ID 획득
                autoSaveToFirestore()
            } catch (e: Exception) {
                android.util.Log.e("EstimateViewModel", "[COPY] Failed to parse and copy estimate from JSON", e)
            }
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
        autoSaveToFirestore()
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

    private fun convertRoomItemsToLong(map: Map<String, Map<String, Int>>): Map<String, Map<String, Long>> {
        return map.mapValues { (_, innerMap) ->
            innerMap.mapValues { (_, value) -> value.toLong() }
        }
    }

    /** 현재 ViewModel 상태를 Estimate 객체로 빌드합니다. (인쇄/재생성 용도) */
    fun buildCurrentEstimate(): Estimate {
        return Estimate(
            id = estimateId,
            customerName = customerName.value,
            phoneNumber = phoneNumber.value,
            departure = departure.value,
            destination = destination.value,
            moveDate = moveDate.value,
            moveType = moveType.value,
            cargoSize = cargoSize.value,
            amount = amount.value.toLongOrNull() ?: 0L,
            memo = memo.value.trim(),
            estimateDate = estimateDate.value,
            startTime = startTime.value,
            visitDate = visitDate.value,
            moveInfo = moveInfo.value,
            totalVolume = totalVolume.value,
            workersM = workersM.value,
            workersF = workersF.value,
            laddersStartFloor = laddersStartFloor.value,
            laddersStartCost = laddersStartCost.value,
            laddersEndFloor = laddersEndFloor.value,
            laddersEndCost = laddersEndCost.value,
            extraTruck = extraTruck.value,
            moveCost = moveCost.value,
            totalCost = totalCost.value,
            deposit = deposit.value,
            balance = balance.value,
            optionCost = optionCost.value,
            roomItems = convertRoomItemsToLong(roomItems.value)
        )
    }

    fun autoSaveToFirestore() {
        if (!userPreferences.isShareEnabled()) {
            android.util.Log.d("EstimateViewModel", "Auto save to Firestore skipped since sharing is disabled.")
            return
        }

        val amt = amount.value.toLongOrNull() ?: 0L
        val actualMemo = memo.value.trim()

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
            memo = actualMemo,
            estimateDate = estimateDate.value,
            startTime = startTime.value,
            visitDate = visitDate.value,
            moveInfo = moveInfo.value,
            totalVolume = totalVolume.value,
            workersM = workersM.value,
            workersF = workersF.value,
            laddersStartFloor = laddersStartFloor.value,
            laddersStartCost = laddersStartCost.value,
            laddersEndFloor = laddersEndFloor.value,
            laddersEndCost = laddersEndCost.value,
            extraTruck = extraTruck.value,
            moveCost = moveCost.value,
            totalCost = totalCost.value,
            deposit = deposit.value,
            balance = balance.value,
            optionCost = optionCost.value,
            roomItems = convertRoomItemsToLong(roomItems.value)
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
        val actualMemo = memo.value.trim()

        val scheduleIdParam = savedStateHandle.get<String>("scheduleId")

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
            memo = actualMemo,
            estimateDate = estimateDate.value,
            startTime = startTime.value,
            visitDate = visitDate.value,
            moveInfo = moveInfo.value,
            totalVolume = totalVolume.value,
            workersM = workersM.value,
            workersF = workersF.value,
            laddersStartFloor = laddersStartFloor.value,
            laddersStartCost = laddersStartCost.value,
            laddersEndFloor = laddersEndFloor.value,
            laddersEndCost = laddersEndCost.value,
            extraTruck = extraTruck.value,
            moveCost = moveCost.value,
            totalCost = totalCost.value,
            deposit = deposit.value,
            balance = balance.value,
            optionCost = optionCost.value,
            roomItems = convertRoomItemsToLong(roomItems.value),
            scheduleId = scheduleIdParam
        )

        _saveState.value = SaveState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalEstimate: Estimate
                if (userPreferences.isShareEnabled()) {
                    android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] Step 1: Saving to Firestore starting...")
                    // 1. Save to Firestore under room collection path
                    val savedId = repository.saveToFirestore(estimate)
                    android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] Step 1: Saving to Firestore success, ID = $savedId")
                    finalEstimate = estimate.copy(id = savedId)
                    estimateId = savedId
                } else {
                    android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] Step 1: Saving to Firestore skipped (sharing disabled).")
                    val tempId = estimate.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    finalEstimate = estimate.copy(id = tempId)
                    estimateId = tempId
                }

                android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] Step 2: Generating HTML template starting...")
                // 2. Generate populated HTML template locally
                val htmlContent = EstimateHtmlGenerator.generateEstimateHtml(context, finalEstimate)
                android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] Step 2: Generating HTML template success (size: ${htmlContent.length} chars)")

                android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] Step 3: Rendering HTML to JPG starting...")
                // 3. Render HTML to PDF and convert to JPG programmatically on main thread
                val jpgPath = EstimatePrintHelper.renderHtmlToJpg(context, htmlContent, finalEstimate)
                android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] Step 3: Rendering HTML to JPG success, path = $jpgPath")

                // 4. Save metadata to Room database (legacy support)
                if (jpgPath != null) {
                    try {
                        android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] Step 4: Caching metadata to Room starting...")
                        val jpgFile = File(jpgPath)
                        val monthDay = if (finalEstimate.estimateDate.length >= 10) {
                            finalEstimate.estimateDate.substring(5, 10)
                        } else {
                            "00-00"
                        }
                        val fileName = jpgFile.name

                        val gson = com.google.gson.Gson()
                        val estimateJson = gson.toJson(finalEstimate)

                        val estimatePdf = EstimatePdf(
                            date = monthDay,
                            fileName = fileName,
                            filePath = jpgPath,
                            estimateId = finalEstimate.id,
                            customerName = finalEstimate.customerName,
                            phoneNumber = finalEstimate.phoneNumber,
                            moveDate = finalEstimate.moveDate,
                            departure = finalEstimate.departure,
                            estimateJson = estimateJson,
                            isSynced = userPreferences.isShareEnabled()
                        )
                        estimatePdfDao.insertPdf(estimatePdf)
                        android.util.Log.d("EstimateViewModel", "JPG Cached locally at: ${jpgFile.absolutePath}")

                    } catch (pdfEx: Throwable) {
                        android.util.Log.e("EstimateViewModel", "Local DB cache failed", pdfEx)
                    }
                }

                try {
                    val scheduleIdStr = savedStateHandle.get<String>("scheduleId")
                    val scheduleId = scheduleIdStr?.toIntOrNull()
                    if (scheduleId != null) {
                        val event = calendarRepository.getEventById(scheduleId)
                        if (event != null) {
                            val updatedEvent = event.copy(linkedEstimateId = finalEstimate.id)
                            calendarRepository.updateEvent(updatedEvent)
                            android.util.Log.d("EstimateViewModel", "Schedule linked success: eventId=$scheduleId, estimateId=${finalEstimate.id}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EstimateViewModel", "Failed to link schedule with estimate", e)
                }

                isSaved = true
                _saveState.value = SaveState.Success
                android.util.Log.d("EstimateViewModel", "[LOG] [THREAD: ${Thread.currentThread().name}] SaveState updated to Success")

                val smsBody = "위와 같이 견적 합니다. 검토해 보시고 연락주세요. 감사합니다."

                viewModelScope.launch(Dispatchers.Main) {
                    onCompleted(smsBody, jpgPath)
                }
            } catch (e: Throwable) {
                android.util.Log.e("EstimateViewModel", "Exception occurred during saveEstimate", e)
                _saveState.value = SaveState.Error(e.stackTraceToString())
            }
        }
    }


    /**
     * 구글 드라이브 업로드 후 onDone 콜백을 호출합니다.
     * - 드라이브가 비활성화되어 있거나 권한이 없으면 즉시 onDone()을 호출합니다.
     * - 활성화된 경우 업로드 완료(성공/실패) 후 onDone()을 호출합니다.
     * - isDriveUploading 상태로 UI에 로딩 표시를 할 수 있습니다.
     */
    fun uploadToGoogleDrive(context: android.content.Context, jpgPath: String, onDone: () -> Unit = {}) {
        // 드라이브 비활성화 또는 권한 없음 → 즉시 완료
        if (!userPreferences.isGoogleDriveSaveEnabled() && !userPreferences.isAutoDriveSyncEnabled()) {
            android.util.Log.d("EstimateViewModel", "Google Drive save/sync is disabled in preferences.")
            onDone()
            return
        }
        if (!GoogleDriveHelper.hasDrivePermission(context)) {
            android.util.Log.w("EstimateViewModel", "Google Drive upload skipped: Drive permission not granted.")
            onDone()
            return
        }

        val appContext = context.applicationContext
        _isDriveUploading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jpgFile = File(jpgPath)
                if (!jpgFile.exists()) {
                    android.util.Log.e("EstimateViewModel", "Upload failed: File does not exist at $jpgPath")
                    withContext(Dispatchers.Main) {
                        _isDriveUploading.value = false
                        onDone()
                    }
                    return@launch
                }
                val fileName = jpgFile.name
                val account = GoogleDriveHelper.getSignedInAccount(appContext)
                if (account == null) {
                    android.util.Log.w("EstimateViewModel", "Google Drive upload skipped: No signed-in account")
                    withContext(Dispatchers.Main) {
                        _isDriveUploading.value = false
                        onDone()
                    }
                    return@launch
                }

                val dateVal = estimateDate.value
                android.util.Log.d("EstimateViewModel", "Google Drive upload starting: $fileName, date=$dateVal")

                val result = GoogleDriveHelper.uploadEstimateJpgWithResult(appContext, account, jpgFile, fileName, dateVal)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is GoogleDriveHelper.UploadResult.Success -> {
                            android.util.Log.d("EstimateViewModel", "Drive upload success: ${result.fileId}")
                            android.widget.Toast.makeText(appContext, "구글 드라이브 업로드 완료", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is GoogleDriveHelper.UploadResult.NoPermission -> {
                            android.util.Log.w("EstimateViewModel", "Drive upload skipped: No Drive permission")
                            android.widget.Toast.makeText(appContext, "구글 드라이브 권한이 없습니다. 설정에서 다시 연결해 주세요.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        is GoogleDriveHelper.UploadResult.UserRecoverable -> {
                            android.util.Log.w("EstimateViewModel", "Drive upload failed: User recoverable auth error")
                            android.widget.Toast.makeText(appContext, "구글 드라이브 로그인이 만료되었습니다.\n견적 목록 설정에서 다시 연결해 주세요.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        is GoogleDriveHelper.UploadResult.Failure -> {
                            android.util.Log.e("EstimateViewModel", "Drive upload failed: ${result.error}")
                            android.widget.Toast.makeText(appContext, "드라이브 업로드 실패. 다시 시도해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    _isDriveUploading.value = false
                    onDone()
                }
            } catch (e: Throwable) {
                android.util.Log.e("EstimateViewModel", "Background Google Drive upload failed", e)
                withContext(Dispatchers.Main) {
                    _isDriveUploading.value = false
                    onDone()
                }
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
