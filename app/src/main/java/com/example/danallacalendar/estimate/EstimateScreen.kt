package com.example.danallacalendar.estimate

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PageRange
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.danallacalendar.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.example.danallacalendar.data.local.UserPreferences
import android.util.Log
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateScreen(
    viewModel: EstimateViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val activity = context.findActivity()
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }
    var currentStep by remember { mutableStateOf(if (viewModel.copyFromEstimateJson != null) 2 else 1) }
    var hasSaved by remember { mutableStateOf(false) }
    var activeSpaceForCargoInput by remember { mutableStateOf<String?>(null) }
    var savedSmsBody by remember { mutableStateOf("") }
    var savedJpgPath by remember { mutableStateOf<String?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            savedJpgPath?.let { path ->
                viewModel.uploadToGoogleDrive(context, path) {
                    onNavigateBack()
                }
            } ?: onNavigateBack()
        } catch (e: ApiException) {
            Log.e("EstimateScreen", "Google Sign-In failed during save", e)
            Toast.makeText(context, "구글 로그인 및 권한 획득에 실패했습니다. (코드: ${e.statusCode})", Toast.LENGTH_LONG).show()
        }
    }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDetailMessage by remember { mutableStateOf("") }
    var completedSpaces by remember { mutableStateOf(setOf<String>()) }
    val isDriveUploading by viewModel.isDriveUploading.collectAsStateWithLifecycle()
    var spaceExpectedVolumes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val totalVol = spaceExpectedVolumes.values.mapNotNull { it.toDoubleOrNull() }.sum()
    val totalExpectedVolumeStr = if (totalVol > 0.0) {
        if (totalVol % 1.0 == 0.0) totalVol.toInt().toString() else String.format(Locale.US, "%.2f", totalVol).trimEnd('0').trimEnd('.')
    } else ""

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var isTtsEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
                .getBoolean("tts_enabled", true)
        )
    }
    var showTtsSettings by remember { mutableStateOf(false) }
    var isTtsReady by remember { mutableStateOf(false) }

    fun speak(text: String) {
        if (!isTtsEnabled) return
        tts?.let { ttsInstance ->
            ttsInstance.speak(text, TextToSpeech.QUEUE_ADD, null, "EstimateScreenTTS")
        }
    }

    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val safeTts = ttsInstance ?: return@TextToSpeech
                val result = safeTts.setLanguage(Locale.KOREAN)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    val allVoices = safeTts.voices
                    if (allVoices != null) {
                        val koVoices = allVoices.filterIsInstance<Voice>().filter { it.locale.language == "ko" }
                        ttsVoices = koVoices
                        
                        val sharedPref = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
                        val savedVoiceName = sharedPref.getString("selected_voice", null)
                        if (savedVoiceName != null) {
                            val matchingVoice = koVoices.find { it.name == savedVoiceName }
                            if (matchingVoice != null) {
                                safeTts.voice = matchingVoice
                                selectedVoice = matchingVoice
                            }
                        } else if (koVoices.isNotEmpty()) {
                            selectedVoice = safeTts.voice
                        }
                    }
                }
                isTtsReady = true
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
        }
    }

    LaunchedEffect(currentStep, isTtsReady) {
        if (!isTtsReady) return@LaunchedEffect
        delay(500)
        val speakText = when (currentStep) {
            1 -> "견적을 시작합니다"
            2 -> "물품을 확인합니다. 확인할 공간을 선택해 주세요."
            3 -> "이사정보를 확인합니다."
            4 -> "견적을 완료합니다."

            else -> ""
        }
        if (speakText.isNotEmpty()) {
            speak(speakText)
        }
    }

    val customerName by viewModel.customerName.collectAsStateWithLifecycle()
    val phoneNumber by viewModel.phoneNumber.collectAsStateWithLifecycle()
    val departure by viewModel.departure.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val moveDate by viewModel.moveDate.collectAsStateWithLifecycle()
    val moveType by viewModel.moveType.collectAsStateWithLifecycle()
    val cargoSize by viewModel.cargoSize.collectAsStateWithLifecycle()
    val amount by viewModel.amount.collectAsStateWithLifecycle()
    val memo by viewModel.memo.collectAsStateWithLifecycle()
    val estimateDate by viewModel.estimateDate.collectAsStateWithLifecycle()
    val startTime by viewModel.startTime.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val roomItems by viewModel.roomItems.collectAsStateWithLifecycle()
    val visitDate by viewModel.visitDate.collectAsStateWithLifecycle()
    val moveInfo by viewModel.moveInfo.collectAsStateWithLifecycle()
    val totalVolume by viewModel.totalVolume.collectAsStateWithLifecycle()
    val workersM by viewModel.workersM.collectAsStateWithLifecycle()
    val workersF by viewModel.workersF.collectAsStateWithLifecycle()
    val laddersStartFloor by viewModel.laddersStartFloor.collectAsStateWithLifecycle()
    val laddersStartCost by viewModel.laddersStartCost.collectAsStateWithLifecycle()
    val laddersEndFloor by viewModel.laddersEndFloor.collectAsStateWithLifecycle()
    val laddersEndCost by viewModel.laddersEndCost.collectAsStateWithLifecycle()
    val extraTruck by viewModel.extraTruck.collectAsStateWithLifecycle()
    val moveCost by viewModel.moveCost.collectAsStateWithLifecycle()
    val totalCost by viewModel.totalCost.collectAsStateWithLifecycle()
    val deposit by viewModel.deposit.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val optionCost by viewModel.optionCost.collectAsStateWithLifecycle()

    val calendar = Calendar.getInstance()

    // Helper functions for pickers
    fun showDatePicker(currentDateStr: String, onDateSelected: (String) -> Unit) {
        val parts = currentDateStr.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
        val month = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: calendar.get(Calendar.MONTH)
        val day = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, y, m, d ->
            val formatted = String.format(Locale.US, "%d-%02d-%02d", y, m + 1, d)
            onDateSelected(formatted)
            viewModel.autoSaveToFirestore()
        }, year, month, day).show()
    }

    fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(context, { _, h, m ->
            val ampm = if (h < 12) "오전" else "오후"
            val displayHour = if (h == 0) 12 else if (h > 12) h - 12 else h
            val formatted = String.format(Locale.KOREA, "%s %d:%02d", ampm, displayHour, m)
            onTimeSelected(formatted)
            viewModel.autoSaveToFirestore()
        }, hour, minute, false).show()
    }

    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Success -> {
                viewModel.resetSaveState()
                hasSaved = false
                if (currentStep == 3) {
                    currentStep = 4
                } else {
                    onNavigateBack()
                }
            }
            is SaveState.Error -> {
                errorDetailMessage = (saveState as SaveState.Error).message
                showErrorDialog = true
                viewModel.resetSaveState()
                hasSaved = false
            }
            else -> {}
        }
    }

    val onSaveEstimate = {
        if (customerName.isBlank()) {
            Toast.makeText(context, "고객명을 입력해주세요.", Toast.LENGTH_SHORT).show()
            currentStep = 3
        } else {
            viewModel.saveEstimate(context) { smsBody, pdfPath ->
                savedSmsBody = smsBody
                savedJpgPath = pdfPath
            }
        }
    }

    if (currentStep == 1) {
        Step1StartScreen(
            onCategorySelected = { type ->
                viewModel.moveType.value = type
                viewModel.autoSaveToFirestore()
                currentStep = 2
            },
            onBack = {
                onNavigateBack()
            },
            onSettingClick = {
                showTtsSettings = true
            }
        )
    } else {
        val gradientBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2D1B69),
                Color(0xFF4A148C)
            )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (currentStep != 2 && currentStep != 3 && currentStep != 4) {
                        TopAppBar(
                            title = { 
                                if (currentStep != 4) {
                                    Text(
                                        text = if (currentStep == 2 && activeSpaceForCargoInput != null) {
                                            "${activeSpaceForCargoInput} 짐 선택"
                                        } else {
                                            "이사 견적서 작성 (${currentStep}/4)"
                                        }, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 18.sp,
                                        color = Color.White
                                    ) 
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (currentStep == 2 && activeSpaceForCargoInput != null) {
                                        activeSpaceForCargoInput = null
                                    } else if (currentStep > 1) {
                                        currentStep--
                                    } else {
                                        onNavigateBack()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack, 
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    showTtsSettings = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "TTS 설정",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Main Content depending on currentStep
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentStep) {
                            2 -> Step2CargoInput(
                                roomItems = roomItems,
                                onUpdateCount = { space, item, count ->
                                    viewModel.updateItemCount(space, item, count)
                                },
                                activeSpace = activeSpaceForCargoInput,
                                onActiveSpaceChange = { activeSpaceForCargoInput = it },
                                completedSpaces = completedSpaces,
                                onCompletedSpacesChange = { completedSpaces = it },
                                spaceExpectedVolumes = spaceExpectedVolumes,
                                onUpdateExpectedVolume = { space, volume ->
                                    spaceExpectedVolumes = spaceExpectedVolumes + (space to volume)
                                },
                                onNavigateNext = {
                                    currentStep = 3
                                },
                                onSpaceClick = { space ->
                                    speak("${space} 선택")
                                    activeSpaceForCargoInput = space
                                },
                                onUpdateCountTts = { text ->
                                    // Removed speak
                                }
                            )
                            3 -> Step3CustomerInfo(
                                customerName = customerName,
                                onCustomerNameChange = { viewModel.customerName.value = it; viewModel.autoSaveToFirestore() },
                                phoneNumber = phoneNumber,
                                onPhoneNumberChange = { viewModel.phoneNumber.value = it; viewModel.autoSaveToFirestore() },
                                departure = departure,
                                onDepartureChange = { viewModel.departure.value = it; viewModel.autoSaveToFirestore() },
                                destination = destination,
                                onDestinationChange = { viewModel.destination.value = it; viewModel.autoSaveToFirestore() },
                                moveDate = moveDate,
                                onSelectMoveDate = {
                                    showDatePicker(moveDate) {
                                        viewModel.moveDate.value = it
                                        viewModel.autoSaveToFirestore()
                                    }
                                },
                                startTime = startTime,
                                onSelectStartTime = { timeStr ->
                                    viewModel.startTime.value = timeStr
                                    viewModel.autoSaveToFirestore()
                                },
                                amount = amount,
                                onAmountChange = { viewModel.amount.value = it; viewModel.autoSaveToFirestore() },
                                memo = memo,
                                onMemoChange = { viewModel.memo.value = it; viewModel.autoSaveToFirestore() },
                                estimateDate = estimateDate,
                                onSelectEstimateDate = {
                                    showDatePicker(estimateDate) {
                                        viewModel.estimateDate.value = it
                                    }
                                },
                                visitDate = visitDate,
                                onSelectVisitDate = {
                                    showDatePicker(visitDate) {
                                        viewModel.visitDate.value = it
                                        viewModel.autoSaveToFirestore()
                                    }
                                },
                                moveInfo = moveInfo,
                                onMoveInfoChange = { viewModel.moveInfo.value = it; viewModel.autoSaveToFirestore() },
                                totalVolume = totalVolume,
                                onTotalVolumeChange = { viewModel.totalVolume.value = it; viewModel.autoSaveToFirestore() },
                                workersM = workersM,
                                onWorkersMChange = { viewModel.workersM.value = it; viewModel.autoSaveToFirestore() },
                                workersF = workersF,
                                onWorkersFChange = { viewModel.workersF.value = it; viewModel.autoSaveToFirestore() },
                                laddersStartFloor = laddersStartFloor,
                                onLaddersStartFloorChange = { viewModel.laddersStartFloor.value = it; viewModel.autoSaveToFirestore() },
                                laddersStartCost = laddersStartCost,
                                onLaddersStartCostChange = { viewModel.laddersStartCost.value = it; viewModel.onLadderCostChanged(); viewModel.autoSaveToFirestore() },
                                laddersEndFloor = laddersEndFloor,
                                onLaddersEndFloorChange = { viewModel.laddersEndFloor.value = it; viewModel.autoSaveToFirestore() },
                                laddersEndCost = laddersEndCost,
                                onLaddersEndCostChange = { viewModel.laddersEndCost.value = it; viewModel.onLadderCostChanged(); viewModel.autoSaveToFirestore() },
                                extraTruck = extraTruck,
                                onExtraTruckChange = { viewModel.extraTruck.value = it; viewModel.autoSaveToFirestore() },
                                moveCost = moveCost,
                                onMoveCostChange = { viewModel.moveCost.value = it; viewModel.onMoveCostOrOptionChanged(); viewModel.autoSaveToFirestore() },
                                totalCost = totalCost,
                                onTotalCostChange = { viewModel.totalCost.value = it; viewModel.autoSaveToFirestore() },
                                deposit = deposit,
                                onDepositChange = { viewModel.deposit.value = it; viewModel.onDepositChanged(); viewModel.autoSaveToFirestore() },
                                balance = balance,
                                onBalanceChange = { viewModel.balance.value = it; viewModel.autoSaveToFirestore() },
                                optionCost = optionCost,
                                onOptionCostChange = { viewModel.optionCost.value = it; viewModel.onMoveCostOrOptionChanged(); viewModel.autoSaveToFirestore() },
                                totalExpectedVolume = totalExpectedVolumeStr
                            )
                            4 -> {
                                val totalVol = spaceExpectedVolumes.values.mapNotNull { it.toDoubleOrNull() }.sum()
                                val totalExpectedVolumeStr = if (totalVol > 0.0) {
                                    if (totalVol % 1.0 == 0.0) totalVol.toInt().toString() else String.format(Locale.US, "%.2f", totalVol).trimEnd('0').trimEnd('.')
                                } else ""
                                Step4PreviewAndActions(
                                    customerName = customerName,
                                    phoneNumber = phoneNumber,
                                    departure = departure,
                                    destination = destination,
                                    moveDate = moveDate,
                                    moveType = moveType,
                                    startTime = startTime,
                                    amount = amount,
                                    totalCost = totalCost,
                                    memo = memo,
                                    roomItemsSummary = viewModel.formatRoomItemsSummary(),
                                    saveState = saveState,
                                    totalExpectedVolume = totalExpectedVolumeStr,
                                    onPrint = {
                                        // WebView createPrintDocumentAdapter 방식: HTML을 직접 PrintManager에 전달
                                        // 이 방식은 Android가 A4 크기에 맞게 렌더링하므로 이미지 축소 문제 없음
                                        val estimate = viewModel.buildCurrentEstimate()
                                        val html = com.example.danallacalendar.estimate.EstimateHtmlGenerator.generateEstimateHtml(context, estimate)
                                        EstimatePrintHelper.printEstimate(context, html, estimate)
                                    },
                                    onSendSms = { smsText ->
                                        if (savedJpgPath != null) {
                                            val jpgFile = java.io.File(savedJpgPath!!)
                                            if (jpgFile.exists()) {
                                                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    jpgFile
                                                )
                                                val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/jpeg"
                                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                                    putExtra(Intent.EXTRA_TEXT, smsText)
                                                    putExtra("sms_body", smsText)
                                                    putExtra("address", phoneNumber)
                                                    putExtra("recipient", phoneNumber)
                                                    putExtra(Intent.EXTRA_PHONE_NUMBER, phoneNumber)
                                                    if (!defaultSmsPackage.isNullOrBlank()) {
                                                        setPackage(defaultSmsPackage)
                                                    }
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    if (!defaultSmsPackage.isNullOrBlank()) {
                                                        context.startActivity(intent)
                                                    } else {
                                                        context.startActivity(Intent.createChooser(intent, "견적서 전송"))
                                                    }
                                                } catch (e: Exception) {
                                                    try {
                                                        intent.setPackage(null)
                                                        context.startActivity(Intent.createChooser(intent, "견적서 전송"))
                                                    } catch (ex: Exception) {
                                                        Toast.makeText(context, "앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "견적서 이미지가 없습니다. 먼저 저장해 주세요.", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "저장된 견적서가 없습니다. 먼저 저장해 주세요.", Toast.LENGTH_SHORT).show()
                                        }
                                    }


                                )
                            }
                        }
                    }

                    // Bottom Navigation Row for Navigation (Next / Back buttons)
                    if (currentStep > 1 && (currentStep != 2 || activeSpaceForCargoInput == null)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = {
                                    currentStep--
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Text("이전")
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            if (currentStep < 3) {
                                Button(
                                    onClick = {
                                        currentStep++
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("다음")
                                }
                            } else if (currentStep == 3) {
                                Button(
                                    onClick = {
                                        if (customerName.isNotBlank()) {
                                            hasSaved = true
                                        }
                                        onSaveEstimate()
                                    },
                                    enabled = !hasSaved && saveState !is SaveState.Loading && saveState !is SaveState.Success,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE040FB)
                                    )
                                ) {
                                    Text(if (hasSaved || saveState is SaveState.Loading) "저장 중입니다" else "저장 및 다음")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (isDriveUploading) return@Button
                                        // 업로드 완료 후 화면 이탈 (업로드 없으면 즉시 이탈)
                                        savedJpgPath?.let { path ->
                                            val userPrefs = UserPreferences(context)
                                            val isDriveSyncOn = userPrefs.isGoogleDriveSaveEnabled() || userPrefs.isAutoDriveSyncEnabled()
                                            val hasPerm = GoogleDriveHelper.hasDrivePermission(context)
                                            val account = GoogleDriveHelper.getSignedInAccount(context)
                                            
                                            if (isDriveSyncOn && (account == null || !hasPerm)) {
                                                googleSignInLauncher.launch(GoogleDriveHelper.getGoogleSignInClient(context).signInIntent)
                                            } else {
                                                viewModel.uploadToGoogleDrive(context, path) {
                                                    onNavigateBack()
                                                }
                                            }
                                        } ?: onNavigateBack()
                                    },
                                    enabled = !isDriveUploading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE040FB)
                                    )
                                ) {
                                    if (isDriveUploading) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("나가기")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (saveState is SaveState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color(0xFFE040FB)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "저장 중입니다.",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }



    if (showTtsSettings) {
        var tempSelectedVoice by remember { mutableStateOf(selectedVoice) }
        val sharedPref = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)

        AlertDialog(
            onDismissRequest = { showTtsSettings = false },
            title = { Text("TTS 음성 설정", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TTS 읽어주기 활성화", color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = isTtsEnabled,
                            onCheckedChange = {
                                isTtsEnabled = it
                                sharedPref.edit().putBoolean("tts_enabled", it).apply()
                            }
                        )
                    }

                    if (isTtsEnabled) {
                        Text("목소리 선택", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                        if (ttsVoices.isEmpty()) {
                            Text("사용 가능한 한국어 목소리가 없습니다.", color = Color.Gray)
                        } else {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(tempSelectedVoice?.name ?: "기본 목소리", color = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ttsVoices.forEach { voice ->
                                        DropdownMenuItem(
                                            text = { Text(voice.name) },
                                            onClick = {
                                                tempSelectedVoice = voice
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val originalVoice = tts?.voice
                                    tts?.voice = tempSelectedVoice
                                    tts?.speak("안녕하세요. 미리듣기 안내 음성입니다.", TextToSpeech.QUEUE_FLUSH, null, "PreviewTTS")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("미리듣기")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedVoice = tempSelectedVoice
                        tempSelectedVoice?.let { voice ->
                            tts?.voice = voice
                            sharedPref.edit().putString("selected_voice", voice.name).apply()
                        }
                        showTtsSettings = false
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTtsSettings = false }) {
                    Text("취소")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("견적 저장 오류 발생", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("오류 원인 스택 트레이스 (제미나이 전달용):", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(Color.Black.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = errorDetailMessage,
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("danalla_error", errorDetailMessage)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "오류 로그가 복사되었습니다.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {}
                        showErrorDialog = false
                    }
                ) {
                    Text("오류 복사 및 닫기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

@Composable
fun Step1StartScreen(
    onCategorySelected: (String) -> Unit,
    onBack: () -> Unit,
    onSettingClick: () -> Unit
) {
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val screenWidth = screenWidthDp.dp
    val screenHeight = screenHeightDp.dp
    val baseWidth = minOf(screenWidthDp, 400)

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2D1B69),
            Color(0xFF4A148C)
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(horizontal = (screenWidthDp * 0.06f).dp)
    ) {
        // 뒤로가기 버튼 및 캘린더 버튼 추가 (상단 배치)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = screenHeight * 0.04f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = Color.White
                )
            }
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(start = 0.dp, end = 8.dp)
            ) {
                Text(
                    text = "캘린더",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 설정 버튼 추가 (상단 배치)
        IconButton(
            onClick = onSettingClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = screenHeight * 0.04f, end = (screenWidthDp * 0.02f).dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "TTS 설정",
                tint = Color.White
            )
        }

        // 전체 공간을 비례하여 차지하는 Column 레이아웃
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = screenHeight * 0.10f, bottom = screenHeight * 0.02f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val letters = listOf(
                "견", "적", "을", "시", "작", "합", "니", "다", "."
            )
            val infiniteTransition = rememberInfiniteTransition(label = "BlinkText")

            // 상단 부분: 미니카 이미지 및 텍스트 타이틀 (전체 높이의 55% 비중)
            Column(
                modifier = Modifier.weight(1.0f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // 미니카 이미지 (너비 비율에 반응)
                Image(
                    painter = painterResource(R.drawable.mini_car_final),
                    contentDescription = "Mini Car",
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .aspectRatio(1.2f)
                )

                // Directed by 텍스트 (반응형 음수 오프셋 적용하여 위로 배치)
                Text(
                    text = "Directed by 다날라 익스프레스",
                    fontSize = (baseWidth * 0.042f).sp,
                    color = Color.White,
                    modifier = Modifier.offset(y = -minOf(screenHeightDp * 0.045f, 36f).dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(screenHeight * 0.045f))

                // 점멸 애니메이션 타이틀
                Row(
                    modifier = Modifier
                        .rotate(-4f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    letters.forEachIndexed { index, char ->
                        val hue by infiniteTransition.animateFloat(
                            initialValue = index * 40f,
                            targetValue = index * 40f + 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 3000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "hue_$index"
                        )

                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800, delayMillis = index * 50, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha_$index"
                        )

                        // screenHeight 비례 대신 고정 dp 계단식 오프셋
                        val yOffset = -(index * 2).dp
                        val animatedColor = Color.hsv(hue % 360f, 0.8f, 1.0f)
                        val animTextSize = (baseWidth * 0.105f).sp

                        Text(
                            text = char,
                            fontSize = animTextSize,
                            fontWeight = FontWeight.Bold,
                            color = animatedColor,
                            modifier = Modifier
                                .offset(y = yOffset)
                                .alpha(alpha)
                        )

                        if (char == "을") {
                            Spacer(modifier = Modifier.width((screenWidthDp * 0.035f).dp))
                        }
                    }
                }
            }

            // 하단 부분: 카테고리 버튼들 (전체 높이의 45% 비중)
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .padding(top = (screenHeightDp * 0.02f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                val categories = listOf(
                    Triple("포장이사", Brush.horizontalGradient(listOf(Color(0xFFFF5252), Color(0xFFFF1744))), Color.White),
                    Triple("보관이사", Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B0FF))), Color.White),
                    Triple("사무실이사", Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00C853))), Color.White)
                )

                categories.forEach { (category, brush, textColor) ->
                    Box(
                        modifier = Modifier
                            .widthIn(max = 320.dp) // 태블릿 가로 폭 상한 지정
                            .fillMaxWidth(0.65f)
                            .padding(vertical = (screenHeightDp * 0.01f).dp)
                            .heightIn(max = 60.dp) // 태블릿 세로 높이 상한 지정
                            .height((screenHeightDp * 0.09f).dp)
                            .background(brush, shape = RoundedCornerShape(16.dp))
                            .clickable { onCategorySelected(category) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category,
                            fontSize = (baseWidth * 0.055f).sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Step2CargoInput(
    roomItems: Map<String, Map<String, Int>>,
    onUpdateCount: (space: String, item: String, count: Int) -> Unit,
    activeSpace: String?,
    onActiveSpaceChange: (String?) -> Unit,
    completedSpaces: Set<String>,
    onCompletedSpacesChange: (Set<String>) -> Unit,
    spaceExpectedVolumes: Map<String, String>,
    onUpdateExpectedVolume: (String, String) -> Unit,
    onNavigateNext: () -> Unit,
    onSpaceClick: (String) -> Unit,
    onUpdateCountTts: (String) -> Unit
) {
    if (activeSpace == null) {
        Step2SpaceSelection(
            roomItems = roomItems,
            completedSpaces = completedSpaces,
            spaceExpectedVolumes = spaceExpectedVolumes,
            onSpaceClick = onSpaceClick,
            onNavigateNext = onNavigateNext
        )
    } else {
        Step2ItemSelection(
            spaceName = activeSpace,
            roomItems = roomItems,
            onUpdateCount = onUpdateCount,
            expectedVolume = spaceExpectedVolumes[activeSpace] ?: "",
            onUpdateExpectedVolume = { volume -> onUpdateExpectedVolume(activeSpace, volume) },
            onComplete = {
                onCompletedSpacesChange(completedSpaces + activeSpace)
                onActiveSpaceChange(null)
            },
            onUpdateCountTts = onUpdateCountTts
        )
    }
}

@Composable
fun Step2SpaceSelection(
    roomItems: Map<String, Map<String, Int>>,
    completedSpaces: Set<String>,
    spaceExpectedVolumes: Map<String, String>,
    onSpaceClick: (String) -> Unit,
    onNavigateNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Animated Text "물품을 확인합니다."
        val letters = listOf("물", "품", "을", "확", "인", "합", "니", "다", ".")
        val infiniteTransition = rememberInfiniteTransition(label = "BlinkText")

        Row(
            modifier = Modifier
                .rotate(-4f)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            letters.forEachIndexed { index, char ->
                val hue by infiniteTransition.animateFloat(
                    initialValue = index * 40f,
                    targetValue = index * 40f + 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "hue_$index"
                )

                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, delayMillis = index * 50, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha_$index"
                )

                val yOffset = -(index * 2).dp
                val animatedColor = Color.hsv(hue % 360f, 0.8f, 1.0f)

                val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                val baseWidth = minOf(screenWidth, 400)
                val animTextSize = (baseWidth * 0.10f).sp
                Text(
                    text = char,
                    fontSize = animTextSize,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor,
                    modifier = Modifier
                        .offset(y = yOffset)
                        .alpha(alpha)
                )

                if (char == "을") {
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "물품을 확인할 공간을 선택해주세요.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val spacePairs = listOf(
                    Pair("안방", "작은방1"),
                    Pair("작은방2", "입구방"),
                    Pair("거실", "주방"),
                    Pair("그외", null)
                )

                spacePairs.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SpaceCard(
                            space = pair.first,
                            roomItems = roomItems,
                            completedSpaces = completedSpaces,
                            expectedVolume = spaceExpectedVolumes[pair.first],
                            onClick = onSpaceClick,
                            modifier = Modifier.weight(1f)
                        )
                        if (pair.second != null) {
                            SpaceCard(
                                space = pair.second!!,
                                roomItems = roomItems,
                                completedSpaces = completedSpaces,
                                expectedVolume = spaceExpectedVolumes[pair.second!!],
                                onClick = onSpaceClick,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpaceCard(
    space: String,
    roomItems: Map<String, Map<String, Int>>,
    completedSpaces: Set<String>,
    expectedVolume: String?,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val itemCount = roomItems[space]?.values?.sum() ?: 0
    val isCompleted = completedSpaces.contains(space) || itemCount > 0



    Card(
        modifier = modifier
            .height(screenHeight * 0.13f)
            .clickable { onClick(space) },
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                Color(0xFFAB47BC).copy(alpha = 0.6f) // 완료 시 더 밝은 보라색
            } else {
                Color(0xFF8E24AA).copy(alpha = 0.15f) // 미완료 시 반투명 보라색 배경
            }
        ),
        shape = RoundedCornerShape(18.dp), // 모서리 더 둥글게 (18.dp)
        border = BorderStroke(
            width = if (isCompleted) 1.5.dp else 1.dp,
            color = if (isCompleted) Color(0xFFE040FB) else Color(0xFFCE93D8).copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Text Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text(
                        text = space,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!expectedVolume.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(1.dp)) // 행간 간격 2.dp -> 1.dp로 축소
                        Text(
                            text = "예상: ${expectedVolume}t",
                            fontSize = 12.sp,
                            color = Color(0xFFE040FB),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Spacer(modifier = Modifier.height(18.dp)) // 예상물량 텍스트 높이와 동일한 높이의 Spacer 플레이스홀더
                    }
                }

                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                Color(0xFF4CAF50), // 완료된 공간은 초록색 체크 표시
                                shape = RoundedCornerShape(50)
                            )
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Divider Line


            // Right Emoji Area
            Box(
                modifier = Modifier
                    .width(screenWidth * 0.15f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (space) {
                        "안방" -> {
                            Text(text = "🌙", fontSize = 28.sp)
                            Text(text = "💤", fontSize = 28.sp)
                        }
                        "작은방1" -> {
                            Text(text = "📚", fontSize = 28.sp)
                            Text(text = "✏️", fontSize = 28.sp)
                        }
                        "작은방2" -> {
                            Text(text = "📚", fontSize = 28.sp)
                            Text(text = "✏️", fontSize = 28.sp)
                        }
                        "입구방" -> {
                            Text(text = "🚪", fontSize = 36.sp)
                        }
                        "거실" -> {
                            Text(text = "🛋️", fontSize = 36.sp)
                        }
                        "주방" -> {
                            Text(text = "🍳", fontSize = 36.sp)
                        }
                        "그외" -> {
                            Text(text = "📦", fontSize = 36.sp)
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun Step2ItemSelection(
    spaceName: String,
    roomItems: Map<String, Map<String, Int>>,
    onUpdateCount: (space: String, item: String, count: Int) -> Unit,
    expectedVolume: String,
    onUpdateExpectedVolume: (String) -> Unit,
    onComplete: () -> Unit,
    onUpdateCountTts: (String) -> Unit
) {
    val predefinedItems = spaceItemsMap[spaceName] ?: emptyList()
    val chunkedItems = predefinedItems.chunked(3)
    var itemPendingOptions by remember { mutableStateOf<PredefinedItem?>(null) }
    var isSecondBubble by remember { mutableStateOf(false) }
    var isAirconBrandBubble by remember { mutableStateOf(false) }
    var isAirconInstallBubble by remember { mutableStateOf(false) }
    var isTvSizeBubble by remember { mutableStateOf(false) }
    var isTvInstallBubble by remember { mutableStateOf(false) }
    var selectedFirstOption by remember { mutableStateOf<String?>(null) }
    var selectedSecondOption by remember { mutableStateOf<String?>(null) }
    var showDirectInputDialog by remember { mutableStateOf(false) }
    var directInputText by remember { mutableStateOf("") }
    var clickedItemPosition by remember { mutableStateOf(Offset.Zero) }
    var clickedItemSize by remember { mutableStateOf(IntSize.Zero) }
    var clickedItemCol by remember { mutableStateOf(0) }
    var rootCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var bottomOverlayHeightPx by remember { mutableStateOf(0) }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastVisibleText by remember { mutableStateOf("") }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            toastVisibleText = toastMessage!!
            delay(1500)
            toastMessage = null
        }
    }

    val toastAlpha by animateFloatAsState(
        targetValue = if (toastMessage != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "toastAlpha"
    )

    var isBottomSheetExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val flyingParticles = remember { mutableStateListOf<FlyingParticle>() }

    fun spawnFlyingParticle(item: PredefinedItem) {
        val itemIndex = predefinedItems.indexOf(item)
        val col = if (itemIndex >= 0) itemIndex % 3 else 1
        val row = if (itemIndex >= 0) itemIndex / 3 else 1
        val startX = (col - 1) * 110f
        val startY = row * 100f + 120f
        flyingParticles.add(
            FlyingParticle(
                id = System.nanoTime(),
                name = item.name,
                iconRes = item.iconRes,
                startX = startX,
                startY = startY
            )
        )
    }

    val selectedItems = remember(roomItems, spaceName) {
        val baseItems = roomItems[spaceName] ?: emptyMap()
        val extraItems = mutableMapOf<String, Int>()
        if (spaceName == "거실") {
            roomItems["에어컨"]?.let { extraItems.putAll(it) }
            roomItems["TV"]?.let { extraItems.putAll(it) }
        } else if (spaceName == "안방") {
            roomItems["TV"]?.let { extraItems.putAll(it) }
        }
        if (extraItems.isEmpty()) baseItems else baseItems + extraItems
    }
    val totalCount = selectedItems.values.sum()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoordinates = it }
    ) {
        val density = LocalDensity.current
        val bottomOverlayHeightDp = with(density) { bottomOverlayHeightPx.toDp() }

        // Main Content (Grid of items + Header)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (bottomOverlayHeightDp > 0.dp) bottomOverlayHeightDp else 64.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Animated Text "물품을 선택하세요."
            val letters = listOf("물", "품", "을", "선", "택", "하", "세", "요", ".")
            val infiniteTransition = rememberInfiniteTransition(label = "BlinkText")

            Row(
                modifier = Modifier
                    .rotate(-4f)
                    .padding(vertical = 16.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                letters.forEachIndexed { index, char ->
                    val hue by infiniteTransition.animateFloat(
                        initialValue = index * 40f,
                        targetValue = index * 40f + 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 3000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "hue_$index"
                    )

                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, delayMillis = index * 50, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha_$index"
                    )

                    val yOffset = -(index * 2).dp
                    val animatedColor = Color.hsv(hue % 360f, 0.8f, 1.0f)

                    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                    val baseWidth = minOf(screenWidth, 400)
                    val animTextSize = (baseWidth * 0.12f).sp
                    Text(
                        text = char,
                        fontSize = animTextSize,
                        fontWeight = FontWeight.Bold,
                        color = animatedColor,
                        modifier = Modifier
                            .offset(y = yOffset)
                            .alpha(alpha)
                    )

                    if (char == "을") {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }

            // Predefined items grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${spaceName}의 물품 선택 (두개는 두번선택)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                chunkedItems.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { item ->
                            val itemIndex = predefinedItems.indexOf(item)
                            ItemSelectCard(
                                item = item,
                                onClick = { cardCoords ->
                                    val root = rootCoordinates
                                    if (root != null && cardCoords.isAttached) {
                                        clickedItemPosition = root.localPositionOf(cardCoords, Offset.Zero)
                                        clickedItemSize = cardCoords.size
                                    } else {
                                        clickedItemPosition = Offset.Zero
                                        clickedItemSize = IntSize.Zero
                                    }
                                    clickedItemCol = if (itemIndex >= 0) itemIndex % 3 else 1

                                    if (item.name == "직접입력") {
                                        onUpdateCountTts("물품 직접 입력")
                                        directInputText = ""
                                        showDirectInputDialog = true
                                    } else {
                                        onUpdateCountTts("${item.name} 선택")
                                        itemPendingOptions = item
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size < 3) {
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Bottom Sheet & Complete Button fixed at the bottom overlay
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val maxExpandedHeight = screenHeight * 0.75f

        val preferredHeight = if (selectedItems.isEmpty()) {
            160.dp
        } else {
            88.dp + (selectedItems.size * 56).dp
        }

        val targetExpandedHeight = minOf(preferredHeight, maxExpandedHeight)

        val sheetHeight by animateDpAsState(
            targetValue = if (isBottomSheetExpanded) targetExpandedHeight else 64.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
            label = "sheetHeight"
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    bottomOverlayHeightPx = coords.size.height
                }
        ) {
            // Bottom Sheet Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (delta < -10) {
                                isBottomSheetExpanded = true
                            } else if (delta > 10) {
                                isBottomSheetExpanded = false
                            }
                        }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E0F3D).copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Drag Handle & Header Row
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isBottomSheetExpanded = !isBottomSheetExpanded }
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(50))
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "선택된 물품 목록 ($totalCount)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Icon(
                                imageVector = if (isBottomSheetExpanded) {
                                    Icons.Default.KeyboardArrowDown
                                } else {
                                    Icons.Default.KeyboardArrowUp
                                },
                                contentDescription = "Toggle Sheet",
                                tint = Color.White
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Scrollable list (visible when expanded)
                    if (isBottomSheetExpanded) {
                        if (selectedItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedItems.forEach { (itemWithOption, count) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = itemWithOption,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val targetSpace = when {
                                                        (itemWithOption.contains("에어컨") &&
                                                         !itemWithOption.contains("(폐기)") &&
                                                         !itemWithOption.contains("(제자리)") &&
                                                         !itemWithOption.contains("(1층)")) -> "에어컨"
                                                        (itemWithOption.contains("TV") &&
                                                         itemWithOption.contains("벽걸이") &&
                                                         !itemWithOption.contains("(폐기)") &&
                                                         !itemWithOption.contains("(제자리)") &&
                                                         !itemWithOption.contains("(1층)")) -> "TV"
                                                        else -> spaceName
                                                    }
                                                    onUpdateCount(targetSpace, itemWithOption, count - 1)
                                                    onUpdateCountTts("${itemWithOption} 감소. 현재 ${count - 1}개")
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(32.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = "$count",
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Button(
                                                onClick = {
                                                    val targetSpace = when {
                                                        (itemWithOption.contains("에어컨") &&
                                                         !itemWithOption.contains("(폐기)") &&
                                                         !itemWithOption.contains("(제자리)") &&
                                                         !itemWithOption.contains("(1층)")) -> "에어컨"
                                                        (itemWithOption.contains("TV") &&
                                                         itemWithOption.contains("벽걸이") &&
                                                         !itemWithOption.contains("(폐기)") &&
                                                         !itemWithOption.contains("(제자리)") &&
                                                         !itemWithOption.contains("(1층)")) -> "TV"
                                                        else -> spaceName
                                                    }
                                                    onUpdateCount(targetSpace, itemWithOption, count + 1)
                                                    onUpdateCountTts("${itemWithOption} 증가. 현재 ${count + 1}개")
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(32.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFE040FB),
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "추가된 물품이 없습니다.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Fixed Expected Volume Input & Complete Button below Bottom Sheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D1B69))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = isBottomSheetExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = expectedVolume,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                onUpdateExpectedVolume(input)
                            }
                        },
                        label = { Text("예상물량 (t)", color = Color.White.copy(alpha = 0.6f)) },
                        placeholder = { Text("0.0", color = Color.White.copy(alpha = 0.3f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE040FB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color(0xFFE040FB),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            cursorColor = Color(0xFFE040FB)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        onUpdateCountTts("완료")
                        if (!isBottomSheetExpanded && selectedItems.isNotEmpty()) {
                            isBottomSheetExpanded = true
                        } else {
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFAB47BC),
                        contentColor = Color.White
                    )
                ) {
                    Text("완료", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        // Flying Particles Overlay
        flyingParticles.forEach { particle ->
            key(particle.id) {
                FlyingIcon(
                    particle = particle,
                    onAnimationEnd = {
                        flyingParticles.remove(particle)
                    }
                )
            }
        }
        // Custom Toast Overlay (1/3 from top)
        if (toastAlpha > 0f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(toastAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = Color(0xFF1E0F3D).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFFE040FB).copy(alpha = 0.4f)),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = toastVisibleText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    // Popup Dialog (Speech Bubble Overlay)
    if (itemPendingOptions != null) {
        val density = LocalDensity.current
        val bubbleWidth = 280.dp
        var bubbleHeightPx by remember { mutableStateOf(0f) }

        val tailOffsetXFraction = when (clickedItemCol) {
            0 -> 0.2f
            2 -> 0.8f
            else -> 0.5f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    itemPendingOptions = null
                    isSecondBubble = false
                    isAirconBrandBubble = false
                    isAirconInstallBubble = false
                    isTvSizeBubble = false
                    isTvInstallBubble = false
                    selectedFirstOption = null
                    selectedSecondOption = null
                }
        ) {
            val bubbleTopPx = clickedItemPosition.y - bubbleHeightPx - 8f
            val minTopPx = with(density) { 10.dp.toPx() }
            val finalTopPx = maxOf(minTopPx, bubbleTopPx)
            val finalTopDp = with(density) { finalTopPx.toDp() }

            val bubbleLeftPx = clickedItemPosition.x + (clickedItemSize.width / 2f) - (with(density) { bubbleWidth.toPx() } * tailOffsetXFraction)
            val finalLeftDp = with(density) { bubbleLeftPx.toDp() }

            val bubbleAlpha by animateFloatAsState(
                targetValue = if (bubbleHeightPx > 0f) 1f else 0f,
                animationSpec = tween(durationMillis = 150),
                label = "bubbleAlpha"
            )

            Surface(
                shape = BubbleShape(
                    tailWidthPx = with(density) { 16.dp.toPx() },
                    tailHeightPx = with(density) { 10.dp.toPx() },
                    tailOffsetXFraction = tailOffsetXFraction,
                    cornerRadiusPx = with(density) { 16.dp.toPx() }
                ),
                color = Color(0xFF1E0F3D),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .offset(x = finalLeftDp, y = finalTopDp)
                    .width(bubbleWidth)
                    .alpha(bubbleAlpha)
                    .onGloballyPositioned { coords ->
                        if (bubbleHeightPx == 0f) {
                            bubbleHeightPx = coords.size.height.toFloat()
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Prevent click propagation
                    }
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = itemPendingOptions!!.iconRes),
                            contentDescription = itemPendingOptions!!.name,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isAirconBrandBubble -> "${itemPendingOptions!!.name} 제조사 선택"
                                isAirconInstallBubble -> "${itemPendingOptions!!.name} 설치 옵션 선택"
                                isTvSizeBubble -> "${itemPendingOptions!!.name} 크기 선택"
                                isTvInstallBubble -> "${itemPendingOptions!!.name} 설치 옵션 선택"
                                isSecondBubble -> "${itemPendingOptions!!.name} 제외 옵션"
                                else -> "${itemPendingOptions!!.name} 선택"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isSecondBubble && !isAirconBrandBubble && !isAirconInstallBubble && !isTvSizeBubble && !isTvInstallBubble) {
                        // 1차 말풍선
                        val hasOptions = itemPendingOptions!!.options.isNotEmpty()
                        if (hasOptions) {
                            // 기존에 옵션 있던 물품 (침대, 소파 등): 기존 옵션들 + 제외
                            itemPendingOptions!!.options.forEach { option ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            if (itemPendingOptions!!.name == "에어컨") {
                                                onUpdateCountTts("${option} 선택")
                                                selectedFirstOption = option
                                                isAirconBrandBubble = true
                                                bubbleHeightPx = 0f // 높이 재계산 유도
                                            } else if (itemPendingOptions!!.name == "TV") {
                                                onUpdateCountTts("${option} 선택")
                                                selectedFirstOption = option
                                                isTvSizeBubble = true
                                                bubbleHeightPx = 0f // 높이 재계산 유도
                                            } else {
                                                val displayName = "${itemPendingOptions!!.name} ($option)"
                                                val currentCount = roomItems[spaceName]?.get(displayName) ?: 0
                                                onUpdateCount(spaceName, displayName, currentCount + 1)
                                                onUpdateCountTts("${displayName} 추가")
                                                toastMessage = "${displayName}이 추가되었습니다."
                                                spawnFlyingParticle(itemPendingOptions!!)
                                                itemPendingOptions = null
                                                isSecondBubble = false
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = option,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            // 기존에 옵션 없던 물품 (서랍장, 화장대 등): 추가 + 제외
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        val displayName = itemPendingOptions!!.name
                                        val currentCount = roomItems[spaceName]?.get(displayName) ?: 0
                                        onUpdateCount(spaceName, displayName, currentCount + 1)
                                        onUpdateCountTts("${displayName} 추가")
                                        toastMessage = "${displayName}이 추가되었습니다."
                                        spawnFlyingParticle(itemPendingOptions!!)
                                        itemPendingOptions = null
                                        isSecondBubble = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "추가",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 공통 '제외' 카드
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    onUpdateCountTts("제외 옵션 선택")
                                    isSecondBubble = true
                                    bubbleHeightPx = 0f // 높이 재계산 유도
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "제외",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    } else if (isAirconBrandBubble) {
                        // 에어컨 브랜드 2차 말풍선: 삼성 / LG / 캐리어 / 위니아
                        val brands = listOf("삼성", "LG", "캐리어", "위니아")
                        brands.forEach { brand ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        onUpdateCountTts("${brand} 선택")
                                        selectedSecondOption = brand
                                        isAirconBrandBubble = false
                                        isAirconInstallBubble = true
                                        bubbleHeightPx = 0f // 높이 재계산 유도
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = brand,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else if (isAirconInstallBubble) {
                        // 에어컨 설치 3차 말풍선: 협력업체 / A/S센터 / 탈착,이동 / 미정
                        val options3 = listOf("협력업체", "A/S센터", "탈착,이동", "미정")
                        options3.forEach { option ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        val displayName = "${itemPendingOptions!!.name} (${selectedFirstOption!!}_${selectedSecondOption!!}_$option)"
                                        val currentCount = roomItems["에어컨"]?.get(displayName) ?: 0
                                        onUpdateCount("에어컨", displayName, currentCount + 1)
                                        onUpdateCountTts("${displayName} 추가")
                                        toastMessage = "${displayName}이 추가되었습니다."
                                        spawnFlyingParticle(itemPendingOptions!!)
                                        itemPendingOptions = null
                                        isAirconInstallBubble = false
                                        selectedFirstOption = null
                                        selectedSecondOption = null
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else if (isTvSizeBubble) {
                        // TV 크기 2차 말풍선: 65"이하 / 75" / 85"이상
                        val sizes = listOf("65\"이하", "75\"", "85\"이상")
                        sizes.forEach { size ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        if (selectedFirstOption == "벽걸이") {
                                            onUpdateCountTts("${size} 선택")
                                            selectedSecondOption = size
                                            isTvSizeBubble = false
                                            isTvInstallBubble = true
                                            bubbleHeightPx = 0f // 높이 재계산 유도
                                        } else {
                                            val displayName = "${itemPendingOptions!!.name} (${selectedFirstOption!!}-$size)"
                                            val currentCount = roomItems[spaceName]?.get(displayName) ?: 0
                                            onUpdateCount(spaceName, displayName, currentCount + 1)
                                            onUpdateCountTts("${displayName} 추가")
                                            toastMessage = "${displayName}이 추가되었습니다."
                                            spawnFlyingParticle(itemPendingOptions!!)
                                            itemPendingOptions = null
                                            isTvSizeBubble = false
                                            selectedFirstOption = null
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = size,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else if (isTvInstallBubble) {
                        // TV 설치 3차 말풍선: 협력업체 / A/S센터 / 탈착,이동 / 미정
                        val options3 = listOf("협력업체", "A/S센터", "탈착,이동", "미정")
                        options3.forEach { option ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        val displayName = "${itemPendingOptions!!.name} (${selectedFirstOption!!}_${selectedSecondOption!!}_$option)"
                                        val currentCount = roomItems["TV"]?.get(displayName) ?: 0
                                        onUpdateCount("TV", displayName, currentCount + 1)
                                        onUpdateCountTts("${displayName} 추가")
                                        toastMessage = "${displayName}이 추가되었습니다."
                                        spawnFlyingParticle(itemPendingOptions!!)
                                        itemPendingOptions = null
                                        isTvInstallBubble = false
                                        selectedFirstOption = null
                                        selectedSecondOption = null
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // 2차 말풍선: 폐기 / 제자리 / 1층
                        val excludeOptions = listOf("폐기", "제자리", "1층")
                        excludeOptions.forEach { option ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        val displayName = "${itemPendingOptions!!.name} ($option)"
                                        val currentCount = roomItems[spaceName]?.get(displayName) ?: 0
                                        onUpdateCount(spaceName, displayName, currentCount + 1)
                                        onUpdateCountTts("${displayName} 추가")
                                        toastMessage = "${displayName}이 추가되었습니다."
                                        spawnFlyingParticle(itemPendingOptions!!)
                                        itemPendingOptions = null
                                        isSecondBubble = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            onUpdateCountTts("취소")
                            itemPendingOptions = null
                            isSecondBubble = false
                            isAirconBrandBubble = false
                            isAirconInstallBubble = false
                            isTvSizeBubble = false
                            isTvInstallBubble = false
                            selectedFirstOption = null
                            selectedSecondOption = null
                        },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("취소", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDirectInputDialog) {
        Dialog(onDismissRequest = { showDirectInputDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "물품 직접 입력",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = directInputText,
                        onValueChange = { directInputText = it },
                        placeholder = { Text("예: 스타일러, 식기세척기 등") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            onUpdateCountTts("취소")
                            showDirectInputDialog = false
                        }) {
                            Text("취소", color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = directInputText.trim()
                                if (trimmed.isNotEmpty()) {
                                    val currentCount = roomItems[spaceName]?.get(trimmed) ?: 0
                                    onUpdateCount(spaceName, trimmed, currentCount + 1)
                                    onUpdateCountTts("${trimmed} 추가")
                                    toastMessage = "${trimmed}이 추가되었습니다."
                                    val directItem = predefinedItems.find { it.name == "직접입력" }
                                    if (directItem != null) {
                                        spawnFlyingParticle(directItem)
                                    }
                                    showDirectInputDialog = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("추가")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemSelectCard(
    item: PredefinedItem,
    onClick: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    var coordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    Column(
        modifier = modifier
            .height(screenHeight * 0.13f)
            .onGloballyPositioned { coordinates = it }
            .clickable {
                val coords = coordinates
                if (coords != null && coords.isAttached) {
                    onClick(coords)
                }
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.name,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class FlyingParticle(
    val id: Long,
    val name: String,
    val iconRes: Int,
    val startX: Float,
    val startY: Float
)

@Composable
fun FlyingIcon(
    particle: FlyingParticle,
    onAnimationEnd: () -> Unit
) {
    val animX = remember { Animatable(particle.startX) }
    val animY = remember { Animatable(particle.startY) }
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val duration = 800
        launch {
            animX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            animY.animateTo(
                targetValue = 620f,
                animationSpec = keyframes {
                    durationMillis = duration
                    (particle.startY - 60f) at 200 using FastOutSlowInEasing
                    620f at duration using FastOutLinearInEasing
                }
            )
        }
        launch {
            scale.animateTo(
                targetValue = 0.2f,
                animationSpec = keyframes {
                    durationMillis = duration
                    1.0f at 200
                    0.6f at 500
                    0.2f at duration
                }
            )
        }
        alpha.animateTo(
            targetValue = 0.0f,
            animationSpec = keyframes {
                durationMillis = duration
                1.0f at 400
                0.5f at 650
                0.0f at duration
            }
        )
        onAnimationEnd()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .offset(x = animX.value.dp, y = animY.value.dp)
                .size(44.dp)
                .scale(scale.value)
                .alpha(alpha.value)
                .background(Color(0xFFE040FB), shape = RoundedCornerShape(50))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = particle.iconRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

data class PredefinedItem(
    val name: String,
    val iconRes: Int,
    val options: List<String> = emptyList()
)

private val spaceItemsMap = mapOf(
    "안방" to listOf(
        PredefinedItem("침대", R.drawable.ic_bed, listOf("싱글", "더블", "킹")),
        PredefinedItem("화장대", R.drawable.ic_dressing_table),
        PredefinedItem("서랍장", R.drawable.ic_drawers),
        PredefinedItem("행거", R.drawable.ic_hanger, listOf("1칸", "2칸", "3칸", "4칸")),
        PredefinedItem("시스템행거", R.drawable.ic_hanger, listOf("2칸", "3칸", "L형")),
        PredefinedItem("책장", R.drawable.ic_bookshelf, listOf("3x3", "3x5")),
        PredefinedItem("책상", R.drawable.ic_desk, listOf("일반형", "책장형")),
        PredefinedItem("장농", R.drawable.ic_wardrobe, listOf("1칸", "2칸", "3칸", "분해형")),
        PredefinedItem("TV", R.drawable.ic_tv, listOf("스탠드", "벽걸이")),
        PredefinedItem("직접입력", R.drawable.ic_add)
    ),
    "작은방1" to listOf(
        PredefinedItem("침대", R.drawable.ic_bed, listOf("싱글", "더블", "킹")),
        PredefinedItem("화장대", R.drawable.ic_dressing_table),
        PredefinedItem("서랍장", R.drawable.ic_drawers),
        PredefinedItem("행거", R.drawable.ic_hanger, listOf("1칸", "2칸", "3칸", "4칸")),
        PredefinedItem("시스템행거", R.drawable.ic_hanger, listOf("2칸", "3칸", "L형")),
        PredefinedItem("책장", R.drawable.ic_bookshelf, listOf("3x3", "3x5")),
        PredefinedItem("책상", R.drawable.ic_desk, listOf("일반형", "책장형")),
        PredefinedItem("장농", R.drawable.ic_wardrobe, listOf("1칸", "2칸", "3칸", "분해형")),
        PredefinedItem("직접입력", R.drawable.ic_add)
    ),
    "작은방2" to listOf(
        PredefinedItem("침대", R.drawable.ic_bed, listOf("싱글", "더블", "킹")),
        PredefinedItem("화장대", R.drawable.ic_dressing_table),
        PredefinedItem("서랍장", R.drawable.ic_drawers),
        PredefinedItem("행거", R.drawable.ic_hanger, listOf("1칸", "2칸", "3칸", "4칸")),
        PredefinedItem("시스템행거", R.drawable.ic_hanger, listOf("2칸", "3칸", "L형")),
        PredefinedItem("책장", R.drawable.ic_bookshelf, listOf("3x3", "3x5")),
        PredefinedItem("책상", R.drawable.ic_desk, listOf("일반형", "책장형")),
        PredefinedItem("장농", R.drawable.ic_wardrobe, listOf("1칸", "2칸", "3칸", "분해형")),
        PredefinedItem("직접입력", R.drawable.ic_add)
    ),
    "입구방" to listOf(
        PredefinedItem("침대", R.drawable.ic_bed, listOf("싱글", "더블", "킹")),
        PredefinedItem("화장대", R.drawable.ic_dressing_table),
        PredefinedItem("서랍장", R.drawable.ic_drawers),
        PredefinedItem("행거", R.drawable.ic_hanger, listOf("1칸", "2칸", "3칸", "4칸")),
        PredefinedItem("시스템행거", R.drawable.ic_hanger, listOf("2칸", "3칸", "L형")),
        PredefinedItem("책장", R.drawable.ic_bookshelf, listOf("3x3", "3x5")),
        PredefinedItem("책상", R.drawable.ic_desk, listOf("일반형", "책장형")),
        PredefinedItem("장농", R.drawable.ic_wardrobe, listOf("1칸", "2칸", "3칸", "분해형")),
        PredefinedItem("직접입력", R.drawable.ic_add)
    ),
    "거실" to listOf(
        PredefinedItem("소파", R.drawable.ic_sofa, listOf("2인", "3인", "L형")),
        PredefinedItem("TV", R.drawable.ic_tv, listOf("스탠드", "벽걸이")),
        PredefinedItem("TV장", R.drawable.ic_tv_cabinet),
        PredefinedItem("에어컨", R.drawable.ic_air_conditioner, listOf("2in1", "스탠드", "벽걸이")),
        PredefinedItem("장식장", R.drawable.ic_cabinet),
        PredefinedItem("테이블", R.drawable.ic_table),
        PredefinedItem("안마의자", R.drawable.ic_chair),
        PredefinedItem("화분", R.drawable.ic_plant),
        PredefinedItem("항아리", R.drawable.ic_jangdok),
        PredefinedItem("직접입력", R.drawable.ic_add)
    ),
    "주방" to listOf(
        PredefinedItem("냉장고", R.drawable.ic_refrigerator, listOf("일반", "양문형", "4도어")),
        PredefinedItem("김치냉장고", R.drawable.ic_dishwasher, listOf("일반형", "스탠드형")),
        PredefinedItem("식탁", R.drawable.ic_dining_table, listOf("4인", "6인")),
        PredefinedItem("정수기", R.drawable.ic_water_purifier),
        PredefinedItem("세탁기", R.drawable.ic_washing_machine, listOf("통돌이", "드럼형", "일체형")),
        PredefinedItem("건조기", R.drawable.ic_washing_machine, listOf("단독형", "일체형")),
        PredefinedItem("식기세척기", R.drawable.ic_shelf, listOf("노출형", "매립형")),
        PredefinedItem("선반", R.drawable.ic_shelf),
        PredefinedItem("항아리", R.drawable.ic_jangdok),
        PredefinedItem("직접입력", R.drawable.ic_add)
    ),
    "그외" to listOf(
        PredefinedItem("직접입력", R.drawable.ic_add)
    )
)

@Composable
fun TableCell(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isEmpty: Boolean = false,
    suffix: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    textAlign: TextAlign = TextAlign.End,
    textFieldModifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentHeight()
            .border(0.5.dp, Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        if (!isEmpty) {
            Column {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = Color(0xFFCE93D8),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!prefix.isNullOrEmpty()) {
                        Text(
                            text = prefix,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = textAlign
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        singleLine = true,
                        modifier = if (textFieldModifier != Modifier) {
                            textFieldModifier
                        } else {
                            if (suffix.isNullOrEmpty() && prefix.isNullOrEmpty()) Modifier.fillMaxWidth() else Modifier.weight(1f)
                        },
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (value.isEmpty()) {
                                    Text(
                                        text = "0",
                                        color = Color.White.copy(alpha = 0.2f),
                                        fontSize = 15.sp,
                                        textAlign = textAlign,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (!suffix.isNullOrEmpty()) {
                        Text(
                            text = suffix,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step3CustomerInfo(
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    departure: String,
    onDepartureChange: (String) -> Unit,
    destination: String,
    onDestinationChange: (String) -> Unit,
    moveDate: String,
    onSelectMoveDate: () -> Unit,
    startTime: String,
    onSelectStartTime: (String) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    memo: String,
    onMemoChange: (String) -> Unit,
    estimateDate: String,
    onSelectEstimateDate: () -> Unit,
    visitDate: String,
    onSelectVisitDate: () -> Unit,
    moveInfo: String,
    onMoveInfoChange: (String) -> Unit,
    totalVolume: String,
    onTotalVolumeChange: (String) -> Unit,
    workersM: String,
    onWorkersMChange: (String) -> Unit,
    workersF: String,
    onWorkersFChange: (String) -> Unit,
    laddersStartFloor: String,
    onLaddersStartFloorChange: (String) -> Unit,
    laddersStartCost: String,
    onLaddersStartCostChange: (String) -> Unit,
    laddersEndFloor: String,
    onLaddersEndFloorChange: (String) -> Unit,
    laddersEndCost: String,
    onLaddersEndCostChange: (String) -> Unit,
    extraTruck: String,
    onExtraTruckChange: (String) -> Unit,
    moveCost: String,
    onMoveCostChange: (String) -> Unit,
    totalCost: String,
    onTotalCostChange: (String) -> Unit,
    deposit: String,
    onDepositChange: (String) -> Unit,
    balance: String,
    onBalanceChange: (String) -> Unit,
    optionCost: String,
    onOptionCostChange: (String) -> Unit,
    totalExpectedVolume: String
) {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val amountFieldWidth by remember(moveCost, optionCost, totalCost, deposit, balance) {
        derivedStateOf {
            val baseWidth = minOf(screenWidth, 400)
            (listOf(moveCost, optionCost, totalCost, deposit, balance)
                .maxOf { it.length.coerceAtLeast(4) } * (baseWidth * 0.025f)).dp
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFE040FB),
        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        focusedLabelColor = Color(0xFFE040FB),
        unfocusedLabelColor = Color(0xFFCE93D8),
        cursorColor = Color(0xFFE040FB),
        disabledTextColor = Color.White,
        disabledBorderColor = Color.White.copy(alpha = 0.3f),
        disabledLabelColor = Color(0xFFCE93D8),
        disabledTrailingIconColor = Color(0xFFE040FB)
    )

    LaunchedEffect(totalExpectedVolume) {
        if (totalVolume.isEmpty() && totalExpectedVolume.isNotEmpty()) {
            onTotalVolumeChange(totalExpectedVolume)
        }
    }

    var showTimeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Animated Text "이사정보를 확인합니다."
        val letters = listOf("이", "사", "정", "보", "를", "확", "인", "합", "니", "다", ".")
        val infiniteTransition = rememberInfiniteTransition(label = "BlinkTextStep3")

        Row(
            modifier = Modifier
                .rotate(-4f)
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            letters.forEachIndexed { index, char ->
                val hue by infiniteTransition.animateFloat(
                    initialValue = index * 40f,
                    targetValue = index * 40f + 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "hue_step3_$index"
                )

                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, delayMillis = index * 50, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha_step3_$index"
                )

                val yOffset = -(index * 2).dp
                val animatedColor = Color.hsv(hue % 360f, 0.8f, 1.0f)

                val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                val baseWidth = minOf(screenWidth, 400)
                val animTextSize = (baseWidth * 0.09f).sp
                Text(
                    text = char,
                    fontSize = animTextSize,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor,
                    modifier = Modifier
                        .offset(y = yOffset)
                        .alpha(alpha)
                )

                if (char == "를") {
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }

        // Move Logistics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E0F3D).copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📅 이사 일정 및 장소",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE040FB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = visitDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("방문 날짜", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Visit Date", modifier = Modifier.clickable { onSelectVisitDate() })
                    },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = moveDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("이사 날짜", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date", modifier = Modifier.clickable { onSelectMoveDate() })
                    },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("시작 시간", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("선택 안 됨", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Time")
                        },
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showTimeDialog = true }
                    )
                }
                var moveInfoExpanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = moveInfo,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("이사 종류", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFFE040FB)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors,
                        enabled = false  // enabled = false 로 해야 Box clickable이 동작함
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { moveInfoExpanded = true }
                    )
                    DropdownMenu(
                        expanded = moveInfoExpanded,
                        onDismissRequest = { moveInfoExpanded = false },
                        modifier = Modifier
                            .widthIn(min = 180.dp)
                            .fillMaxWidth(0.5f)
                            .background(
                                color = Color(0xFF2D1B69),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, Color(0xFFE040FB).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        offset = DpOffset(x = 180.dp, y = 0.dp)
                    ) {
                        listOf("포장이사", "반포장이사", "일반이사").forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    onMoveInfoChange(option)
                                    moveInfoExpanded = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (option == moveInfo)
                                            Color(0xFFE040FB).copy(alpha = 0.2f)
                                        else
                                            Color.Transparent
                                    )
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = departure,
                    onValueChange = onDepartureChange,
                    label = { Text("출발지", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = onDestinationChange,
                    label = { Text("도착지", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Amount & Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E0F3D).copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "💰 견적 정보",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE040FB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E0F3D), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    // Row 1: 총견적물량 / 이사비용
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TableCell(
                            label = "총견적물량",
                            value = totalVolume,
                            onValueChange = onTotalVolumeChange,
                            modifier = Modifier.weight(1f),
                            suffix = " 톤"
                        )
                        TableCell(
                            label = "이사비용",
                            value = moveCost,
                            onValueChange = onMoveCostChange,
                            modifier = Modifier.weight(1f),
                            prefix = "₩ ",
                            suffix = " 만원",
                            textFieldModifier = Modifier.width(amountFieldWidth)
                        )
                    }
                    // Row 2: 작업인원 / 옵션비용
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .wrapContentHeight()
                                .border(0.5.dp, Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    "작업인원",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCE93D8),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text("남", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    BasicTextField(
                                        value = workersM,
                                        onValueChange = onWorkersMChange,
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.End),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width((screenWidth * 0.05f).dp)
                                    )
                                    Text("명   ", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("여", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    BasicTextField(
                                        value = workersF,
                                        onValueChange = onWorkersFChange,
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.End),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width((screenWidth * 0.05f).dp)
                                    )
                                    Text("명", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        TableCell(
                            label = if ((optionCost.toLongOrNull() ?: 0L) < 0) "할인" else "옵션비용",
                            value = optionCost,
                            onValueChange = onOptionCostChange,
                            modifier = Modifier.weight(1f),
                            prefix = "₩ ",
                            suffix = " 만원",
                            textFieldModifier = Modifier.width(amountFieldWidth)
                        )
                    }
                    // Row 3: 출발지사다리 / 총비용
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .wrapContentHeight()
                                .border(0.5.dp, Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "출발지사다리",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCE93D8),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicTextField(
                                        value = laddersStartFloor,
                                        onValueChange = onLaddersStartFloorChange,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.End),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        decorationBox = { inner ->
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (laddersStartFloor.isEmpty()) {
                                                    Text(
                                                        text = "0",
                                                        color = Color.White.copy(alpha = 0.2f),
                                                        fontSize = 15.sp,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                    Text(" 층", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                    Spacer(Modifier.width(8.dp))
                                    BasicTextField(
                                        value = laddersStartCost,
                                        onValueChange = onLaddersStartCostChange,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.End),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        decorationBox = { inner ->
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (laddersStartCost.isEmpty()) {
                                                    Text(
                                                        text = "0",
                                                        color = Color.White.copy(alpha = 0.2f),
                                                        fontSize = 15.sp,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                    Text(" 만원", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                            }
                        }
                        TableCell(
                            label = "총비용",
                            value = totalCost,
                            onValueChange = onTotalCostChange,
                            modifier = Modifier.weight(1f),
                            prefix = "₩ ",
                            suffix = " 만원",
                            textFieldModifier = Modifier.width(amountFieldWidth)
                        )
                    }
                    // Row 4: 도착지사다리 / 계약금
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .wrapContentHeight()
                                .border(0.5.dp, Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "도착지사다리",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCE93D8),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicTextField(
                                        value = laddersEndFloor,
                                        onValueChange = onLaddersEndFloorChange,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.End),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        decorationBox = { inner ->
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (laddersEndFloor.isEmpty()) {
                                                    Text(
                                                        text = "0",
                                                        color = Color.White.copy(alpha = 0.2f),
                                                        fontSize = 15.sp,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                    Text(" 층", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                    Spacer(Modifier.width(8.dp))
                                    BasicTextField(
                                        value = laddersEndCost,
                                        onValueChange = onLaddersEndCostChange,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.End),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        decorationBox = { inner ->
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (laddersEndCost.isEmpty()) {
                                                    Text(
                                                        text = "0",
                                                        color = Color.White.copy(alpha = 0.2f),
                                                        fontSize = 15.sp,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                    Text(" 만원", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                            }
                        }
                        TableCell(
                            label = "계약금",
                            value = deposit,
                            onValueChange = onDepositChange,
                            modifier = Modifier.weight(1f),
                            prefix = "₩ ",
                            suffix = " 만원",
                            textFieldModifier = Modifier.width(amountFieldWidth)
                        )
                    }
                    // Row 5: 1T 추가시 / 잔금
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TableCell(
                            label = "1T 추가시",
                            value = extraTruck,
                            onValueChange = onExtraTruckChange,
                            modifier = Modifier.weight(1f),
                            suffix = " 만원"
                        )
                        TableCell(
                            label = "잔금",
                            value = balance,
                            onValueChange = onBalanceChange,
                            modifier = Modifier.weight(1f),
                            prefix = "₩ ",
                            suffix = " 만원",
                            textFieldModifier = Modifier.width(amountFieldWidth)
                        )
                    }
                }
            }
        }

        // Customer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E0F3D).copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "👤 고객 연락처",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE040FB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    label = { Text("전화번호", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = customerName,
                    onValueChange = onCustomerNameChange,
                    label = { Text("성명 또는 회사명(계약금 입금자명)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { onMemoChange(it) },
                    label = { Text("고객 요청 및 특이사항") },
                    minLines = 3,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
            }
        }

        if (showTimeDialog) {
            val parts = startTime.split("시", "분").map { it.trim() }
            val currentHour = parts.getOrNull(0)?.toIntOrNull() ?: 7
            val currentMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            var selectedHour by remember { mutableStateOf(currentHour) }
            var selectedMinute by remember { mutableStateOf(currentMinute) }

            AlertDialog(
                onDismissRequest = { showTimeDialog = false },

                confirmButton = {
                    TextButton(onClick = {
                        val formatted = String.format(Locale.KOREA, "%02d시 %02d분", selectedHour, selectedMinute)
                        onSelectStartTime(formatted)
                        showTimeDialog = false
                    }) {
                        Text("선택", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimeDialog = false }) {
                        Text("취소", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                title = { Text("시작 시간 선택", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WheelTimePicker(
                            initialHour = currentHour,
                            initialMinute = currentMinute,
                            modifier = Modifier.width((screenWidth * 0.5f).dp),
                            onTimeChanged = { hour, minute ->
                                selectedHour = hour
                                selectedMinute = minute
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun Step4PreviewAndActions(
    customerName: String,
    phoneNumber: String,
    departure: String,
    destination: String,
    moveDate: String,
    moveType: String,
    startTime: String,
    amount: String,
    totalCost: String,
    memo: String,
    roomItemsSummary: String,
    saveState: SaveState,
    totalExpectedVolume: String,
    onPrint: () -> Unit,
    onSendSms: (String) -> Unit
) {
    val formattedAmount = remember(amount) {
        val amt = amount.toLongOrNull() ?: 0L
        NumberFormat.getNumberInstance(Locale.KOREA).format(amt)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val userPreferences = remember { com.example.danallacalendar.data.local.UserPreferences(context) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val buttonHeight = (screenHeightDp * 0.07f).coerceIn(48f, 60f).dp

    var smsText by remember { mutableStateOf(userPreferences.getSmsBodyTemplate()) }
    var showSmsDialog by remember { mutableStateOf(false) }

    if (showSmsDialog) {
        AlertDialog(
            onDismissRequest = { 
                smsText = userPreferences.getSmsBodyTemplate()
                showSmsDialog = false 
            },
            title = { Text("문자 메시지 본문 수정", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = smsText,
                    onValueChange = { smsText = it },
                    label = { Text("메시지 내용") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                userPreferences.setSmsBodyTemplate(smsText)
                            }
                        },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = Color(0xFFE040FB),
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                        focusedLabelColor = Color(0xFFE040FB),
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = Color(0xFFE040FB)
                    ),
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        userPreferences.setSmsBodyTemplate(smsText)
                        showSmsDialog = false 
                    }
                ) {
                    Text("확인", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        smsText = userPreferences.getSmsBodyTemplate()
                        showSmsDialog = false 
                    }
                ) {
                    Text("취소", color = Color.Gray)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Animated Text "견적을 완료합니다."
        val letters = listOf("견", "적", "을", "완", "료", "합", "니", "다", ".")
        val infiniteTransition = rememberInfiniteTransition(label = "BlinkTextStep4")

        Row(
            modifier = Modifier
                .rotate(-4f)
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            letters.forEachIndexed { index, char ->
                val hue by infiniteTransition.animateFloat(
                    initialValue = index * 40f,
                    targetValue = index * 40f + 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "hue_step4_$index"
                )

                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, delayMillis = index * 50, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha_step4_$index"
                )

                val yOffset = -(index * 2).dp
                val animatedColor = Color.hsv(hue % 360f, 0.8f, 1.0f)

                val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                val baseWidth = minOf(screenWidth, 400)
                val animTextSize = (baseWidth * 0.11f).sp
                Text(
                    text = char,
                    fontSize = animTextSize,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor,
                    modifier = Modifier
                        .offset(y = yOffset)
                        .alpha(alpha)
                )

                if (char == "을") {
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.9f)
                .align(Alignment.CenterHorizontally),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E0F3D).copy(alpha = 0.85f)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("견적 요약", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE040FB))
                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

                PreviewRow(label = "이사 종류", value = moveType)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                PreviewRow(label = "이사 날짜", value = moveDate)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                PreviewRow(label = "시작 시간", value = startTime)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                PreviewRow(label = "이사 물량", value = if (totalExpectedVolume.isNotBlank()) "${totalExpectedVolume}t" else "")
                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                PreviewRow(label = "이사 비용", value = if (totalCost.isNotBlank()) "${totalCost}만원" else "", valueColor = Color(0xFFE040FB), isBold = true)

                if (memo.isNotBlank()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Text("메모", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Text(
                        text = memo,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Text(
            text = " 상세 견적서는 별도로 첨부해 드립니다.",
            fontSize = 17.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.9f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.9f)
                .align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                color = MaterialTheme.colorScheme.tertiary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onPrint() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("프린터 출력", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                color = MaterialTheme.colorScheme.tertiary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSendSms(smsText) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("견적서 문자 발송", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Vertical Divider between text and edit button
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.5f)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    IconButton(
                        onClick = { showSmsDialog = true },
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "문구 수정",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(
            text = value.ifBlank { "입력 안 됨" },
            color = if (value.isBlank()) MaterialTheme.colorScheme.error else valueColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

class BubbleShape(
    val tailWidthPx: Float,
    val tailHeightPx: Float,
    val tailOffsetXFraction: Float = 0.5f,
    val cornerRadiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val rectHeight = size.height - tailHeightPx
            val rectWidth = size.width

            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = rectWidth,
                    bottom = rectHeight,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx),
                    topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx),
                    bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx),
                    bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
                )
            )

            val tailStart = (rectWidth * tailOffsetXFraction) - (tailWidthPx / 2)
            moveTo(tailStart, rectHeight)
            lineTo(tailStart + (tailWidthPx / 2), size.height)
            lineTo(tailStart + tailWidthPx, rectHeight)
            close()
        }
        return Outline.Generic(path)
    }
}

// Printer support helper
// Printer support helper for PDF files
fun printPdfFile(context: Context, file: File, documentName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "${documentName}_Job"
    val printAdapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(documentName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback?.onLayoutFinished(info, newAttributes != oldAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            var input: FileInputStream? = null
            var output: FileOutputStream? = null
            try {
                input = FileInputStream(file)
                output = FileOutputStream(destination?.fileDescriptor)
                val buf = ByteArray(16384)
                var size: Int
                while (input.read(buf).also { size = it } >= 0 && cancellationSignal?.isCanceled != true) {
                    output.write(buf, 0, size)
                }
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                } else {
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            } finally {
                try {
                    input?.close()
                    output?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun WheelTimePicker(
    initialHour: Int,
    initialMinute: Int,
    modifier: Modifier = Modifier,
    onTimeChanged: (Int, Int) -> Unit
) {
    // 24시간제: 0~23
    val hourList = (0..23).map { String.format(Locale.US, "%02d시", it) }
    val minuteList = listOf("00분", "10분", "20분", "30분", "40분", "50분")

    val initialHourIndex = initialHour.coerceIn(0, 23)

    // Map initialMinute to the nearest 10 minutes step (00, 10, 20, 30, 40, 50)
    val roundedMinute = ((initialMinute + 5) / 10 * 10) % 60
    val initialMinuteIndex = (roundedMinute / 10).coerceIn(0, 5)

    var selectedHourIndex by remember { mutableStateOf(initialHourIndex) }
    var selectedMinuteIndex by remember { mutableStateOf(initialMinuteIndex) }

    LaunchedEffect(selectedHourIndex, selectedMinuteIndex) {
        val hour = selectedHourIndex
        val minute = minuteList[selectedMinuteIndex].replace("분", "").toInt()
        onTimeChanged(hour, minute)
    }

    Row(
        modifier = modifier
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPicker(
            items = hourList,
            initialIndex = initialHourIndex,
            onIndexSelected = { selectedHourIndex = it },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        WheelPicker(
            items = minuteList,
            initialIndex = initialMinuteIndex,
            onIndexSelected = { selectedMinuteIndex = it },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 5,
    itemHeight: androidx.compose.ui.unit.Dp = 48.dp
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState)

    val selectedIndex by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex
        }
    }

    LaunchedEffect(initialIndex) {
        if (initialIndex in items.indices && initialIndex != lazyListState.firstVisibleItemIndex) {
            lazyListState.scrollToItem(initialIndex)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            onIndexSelected(selectedIndex)
        }
    }

    val verticalPadding = itemHeight * ((visibleItemsCount - 1) / 2)

    Box(
        modifier = modifier.height(itemHeight * visibleItemsCount),
        contentAlignment = Alignment.Center
    ) {
        // Selection capsule styling (Samsung One UI style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp)
                )
        )

        LazyColumn(
            state = lazyListState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = verticalPadding),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                val indexDiff = abs(index - selectedIndex)
                val alpha = when (indexDiff) {
                    0 -> 1.0f
                    1 -> 0.5f
                    2 -> 0.2f
                    else -> 0.0f
                }
                val scale = when (indexDiff) {
                    0 -> 1.15f
                    1 -> 1.0f
                    2 -> 0.85f
                    else -> 0.7f
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        fontSize = 20.sp,
                        fontWeight = if (indexDiff == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (indexDiff == 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.graphicsLayer {
                            this.alpha = alpha
                            this.scaleX = scale
                            this.scaleY = scale
                        }
                    )
                }
            }
        }
    }
}
