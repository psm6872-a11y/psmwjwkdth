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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.danallacalendar.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
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
    var currentStep by remember { mutableStateOf(1) }
    var activeSpaceForCargoInput by remember { mutableStateOf<String?>(null) }
    var completedSpaces by remember { mutableStateOf(setOf<String>()) }
    var spaceExpectedVolumes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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
            2 -> "물품을 확인합니다"
            3 -> "고객 정보를 입력합니다"
            4 -> "최종 견적서를 확인합니다"
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
    val googleSheetsUrl by viewModel.googleSheetsUrl.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val roomItems by viewModel.roomItems.collectAsStateWithLifecycle()

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
            viewModel.autoSaveToGoogleSheets()
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
            viewModel.autoSaveToGoogleSheets()
        }, hour, minute, false).show()
    }

    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Success -> {
                Toast.makeText(context, "견적서가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                viewModel.resetSaveState()
            }
            is SaveState.Error -> {
                Toast.makeText(context, "오류: ${(saveState as SaveState.Error).message}", Toast.LENGTH_LONG).show()
                viewModel.resetSaveState()
            }
            else -> {}
        }
    }

    if (currentStep == 1) {
        Step1StartScreen(
            onCategorySelected = { type ->
                viewModel.moveType.value = type
                viewModel.autoSaveToGoogleSheets()
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
                    if (currentStep != 2) {
                        TopAppBar(
                            title = { 
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
                                    activeSpaceForCargoInput = space
                                },
                                onUpdateCountTts = { text ->
                                    // Removed speak
                                }
                            )
                            3 -> Step3CustomerInfo(
                                customerName = customerName,
                                onCustomerNameChange = { viewModel.customerName.value = it; viewModel.autoSaveToGoogleSheets() },
                                phoneNumber = phoneNumber,
                                onPhoneNumberChange = { viewModel.phoneNumber.value = it; viewModel.autoSaveToGoogleSheets() },
                                departure = departure,
                                onDepartureChange = { viewModel.departure.value = it; viewModel.autoSaveToGoogleSheets() },
                                destination = destination,
                                onDestinationChange = { viewModel.destination.value = it; viewModel.autoSaveToGoogleSheets() },
                                moveDate = moveDate,
                                onSelectMoveDate = {
                                    showDatePicker(moveDate) {
                                        viewModel.moveDate.value = it
                                    }
                                },
                                startTime = startTime,
                                onSelectStartTime = {
                                    showTimePicker {
                                        viewModel.startTime.value = it
                                    }
                                },
                                amount = amount,
                                onAmountChange = { viewModel.amount.value = it; viewModel.autoSaveToGoogleSheets() },
                                memo = memo,
                                onMemoChange = { viewModel.memo.value = it; viewModel.autoSaveToGoogleSheets() },
                                googleSheetsUrl = googleSheetsUrl,
                                onSheetsUrlChange = { viewModel.googleSheetsUrl.value = it },
                                estimateDate = estimateDate,
                                onSelectEstimateDate = {
                                    showDatePicker(estimateDate) {
                                        viewModel.estimateDate.value = it
                                    }
                                }
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
                                    memo = memo,
                                    roomItemsSummary = viewModel.formatRoomItemsSummary(),
                                    saveState = saveState,
                                    totalExpectedVolume = totalExpectedVolumeStr,
                                    onPrint = {
                                        printEstimate(context, customerName, phoneNumber, moveDate, startTime, moveType, departure, destination, amount, viewModel.formatRoomItemsSummary(), memo, totalExpectedVolumeStr)
                                    },
                                    onSave = {
                                        if (customerName.isBlank()) {
                                            Toast.makeText(context, "고객명을 입력해주세요.", Toast.LENGTH_SHORT).show()
                                            currentStep = 3
                                        } else {
                                            viewModel.saveEstimate { smsBody ->
                                                Toast.makeText(context, "구글 시트 및 DB 저장 완료!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onSendSms = {
                                        viewModel.saveEstimate { smsBody ->
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:${phoneNumber}")
                                                putExtra("sms_body", smsBody)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "문자 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                                            }
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

                            if (currentStep < 4) {
                                Button(
                                    onClick = {
                                        currentStep++
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(if (currentStep == 3) "미리보기" else "다음")
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
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
}

@Composable
fun Step1StartScreen(
    onCategorySelected: (String) -> Unit,
    onBack: () -> Unit,
    onSettingClick: () -> Unit
) {
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
            .padding(horizontal = 24.dp)
    ) {
        // 뒤로가기 버튼 및 캘린더 버튼 추가
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp),
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
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 설정 버튼 추가
        IconButton(
            onClick = onSettingClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "TTS 설정",
                tint = Color.White
            )
        }

        // 타이틀은 상단에서 약간 내려서 배치
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 미니카 이미지
            Image(
                painter = painterResource(R.drawable.mini_car_final),
                contentDescription = "Mini Car",
                modifier = Modifier.size(260.dp)
                    .offset(y = 10.dp)
            )

            Spacer(modifier = Modifier.height(0.dp))

            // Directed by 텍스트
            Text(
                text = "Directed by 다날라 익스프레스",
                fontSize = 15.sp,
                color = Color.White,
                        modifier = Modifier.offset(y = (-55).dp)
            )

            Spacer(modifier = Modifier.height(0.dp))

            val letters = listOf(
                "견", "적", "을", "시", "작", "합", "니", "다", "."
            )

            val infiniteTransition = rememberInfiniteTransition(label = "BlinkText")

            Row(
                modifier = Modifier
                    .rotate(-4f)
                    .offset(y = (-8).dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                letters.forEachIndexed { index, char ->
                    // 색상 애니메이션 (hue가 0~360도로 순환)
                    val hue by infiniteTransition.animateFloat(
                        initialValue = index * 40f,
                        targetValue = index * 40f + 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 3000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "hue_$index"
                    )

                    // 점멸 애니메이션 (alpha가 0.4~1.0으로 변화)
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, delayMillis = index * 50, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha_$index"
                    )

                    // 뒤로 갈수록 글자가 위로 올라가는 효과 (y offset 증가)
                    val yOffset = -(index * 2).dp

                    // HSV를 RGB로 변환하여 색상 생성
                    val animatedColor = Color.hsv(hue % 360f, 0.8f, 1.0f)

                    Text(
                        text = char,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = animatedColor,
                        modifier = Modifier
                            .offset(y = yOffset)
                            .alpha(alpha)
                    )

                    // "을" 다음에 공백 삽입
                    if (char == "을") {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }
        }

        // 카테고리 버튼들은 중앙에서 아래로 더 내려서 배치
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val categories = listOf(
                Triple("포장이사", Brush.horizontalGradient(listOf(Color(0xFFFF5252), Color(0xFFFF1744))), Color.White),
                Triple("보관이사", Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B0FF))), Color.White),
                Triple("사무실이사", Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00C853))), Color.White)
            )

            categories.forEach { (category, brush, textColor) ->
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .padding(vertical = 8.dp)
                        .height(60.dp)
                        .background(brush, shape = RoundedCornerShape(16.dp))
                        .clickable { onCategorySelected(category) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
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

                Text(
                    text = char,
                    fontSize = 40.sp,
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
                modifier = Modifier.padding(horizontal = 4.dp)
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
    val itemCount = roomItems[space]?.values?.sum() ?: 0
    val isCompleted = completedSpaces.contains(space) || itemCount > 0

    val emoji = when (space) {
        "안방" -> "🛏️"
        "작은방1" -> "🛏️"
        "작은방2" -> "🛏️"
        "입구방" -> "🚪"
        "거실" -> "🛋️"
        "주방" -> "🍳"
        "그외" -> "📦"
        else -> ""
    }

    Card(
        modifier = modifier
            .height(90.dp)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp) // 세로 패딩을 12.dp -> 8.dp로 축소하여 내부 세로 공간 확보
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = "$space $emoji", // 공간에 이모지 아이콘 추가
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(1.dp)) // 행간 간격 3.dp -> 1.dp로 축소
                Text(
                    text = if (itemCount > 0) "${itemCount}개 선택됨" else "비어 있음",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (!expectedVolume.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(1.dp)) // 행간 간격 2.dp -> 1.dp로 축소
                    Text(
                        text = "예상: ${expectedVolume}t",
                        fontSize = 10.sp,
                        color = Color(0xFFE040FB),
                        fontWeight = FontWeight.Bold
                    )
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
    var selectedFirstOption by remember { mutableStateOf<String?>(null) }
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

    val selectedItems = roomItems[spaceName] ?: emptyMap()
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

                    Text(
                        text = char,
                        fontSize = 36.sp,
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
                    color = Color.White
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
                                color = Color.White
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
                                            color = Color.White
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    onUpdateCount(spaceName, itemWithOption, count - 1)
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
                                                    onUpdateCount(spaceName, itemWithOption, count + 1)
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
                        .height(48.dp),
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
                    selectedFirstOption = null
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
                                isSecondBubble -> "${itemPendingOptions!!.name} 제외 옵션"
                                else -> "${itemPendingOptions!!.name} 선택"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isSecondBubble && !isAirconBrandBubble) {
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
                                        val displayName = "${itemPendingOptions!!.name} (${selectedFirstOption!!}-$brand)"
                                        val currentCount = roomItems[spaceName]?.get(displayName) ?: 0
                                        onUpdateCount(spaceName, displayName, currentCount + 1)
                                        onUpdateCountTts("${displayName} 추가")
                                        toastMessage = "${displayName}이 추가되었습니다."
                                        spawnFlyingParticle(itemPendingOptions!!)
                                        itemPendingOptions = null
                                        isAirconBrandBubble = false
                                        selectedFirstOption = null
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
                    } else {
                        // 2차 말풍선: 폐기 / 제자리 / 1층
                        val excludeOptions = listOf("폐기", "제자리", "1층")
                        excludeOptions.forEach { option ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        val displayName = "${itemPendingOptions!!.name} (제외-$option)"
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
                            selectedFirstOption = null
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
    var coordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    Column(
        modifier = modifier
            .height(90.dp)
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
            color = Color.White
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
        PredefinedItem("TV", R.drawable.ic_tv, listOf("65\"이하", "75\"", "85\"이상")),
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
    onSelectStartTime: () -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    memo: String,
    onMemoChange: (String) -> Unit,
    googleSheetsUrl: String,
    onSheetsUrlChange: (String) -> Unit,
    estimateDate: String,
    onSelectEstimateDate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Customer Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("고객 연락처 정보", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = customerName,
                    onValueChange = onCustomerNameChange,
                    label = { Text("고객명") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    label = { Text("전화번호") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Move Logistics Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("이사 장소 및 일정", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = moveDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("이사 날짜") },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date", modifier = Modifier.clickable { onSelectMoveDate() })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("시작 시간") },
                    placeholder = { Text("선택 안 됨") },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Time", modifier = Modifier.clickable { onSelectStartTime() })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = departure,
                    onValueChange = onDepartureChange,
                    label = { Text("출발지") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = onDestinationChange,
                    label = { Text("도착지") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Amount & Details Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("견적 정보", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text("금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = onMemoChange,
                    label = { Text("메모") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Integration Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("시스템 연동", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = googleSheetsUrl,
                    onValueChange = onSheetsUrlChange,
                    label = { Text("구글 스프레드시트 웹앱 URL") },
                    placeholder = { Text("https://script.google.com/...") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = estimateDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("견적 작성일") },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date", modifier = Modifier.clickable { onSelectEstimateDate() })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
    memo: String,
    roomItemsSummary: String,
    saveState: SaveState,
    totalExpectedVolume: String,
    onPrint: () -> Unit,
    onSave: () -> Unit,
    onSendSms: () -> Unit
) {
    val formattedAmount = remember(amount) {
        val amt = amount.toLongOrNull() ?: 0L
        NumberFormat.getNumberInstance(Locale.KOREA).format(amt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("최종 견적서 요약", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Divider()

                PreviewRow(label = "이사 종류", value = moveType)
                PreviewRow(label = "고객명", value = customerName)
                PreviewRow(label = "전화번호", value = phoneNumber)
                PreviewRow(label = "이사 날짜", value = "$moveDate ${if (startTime.isNotBlank()) "($startTime)" else ""}")
                PreviewRow(label = "출발지", value = departure)
                PreviewRow(label = "도착지", value = destination)
                if (totalExpectedVolume.isNotBlank()) {
                    PreviewRow(label = "총 예상물량", value = "${totalExpectedVolume}t", valueColor = Color(0xFFE040FB), isBold = true)
                }
                PreviewRow(label = "견적 금액", value = "${formattedAmount}원", valueColor = MaterialTheme.colorScheme.primary, isBold = true)

                if (roomItemsSummary.isNotBlank()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("공간별 짐 요약", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = roomItemsSummary,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }

                if (memo.isNotBlank()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("메모", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = memo,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPrint,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("프린터 출력")
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (saveState is SaveState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("저장")
                }
            }
        }

        Button(
            onClick = onSendSms,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("문자 전송", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun PreviewRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
fun printEstimate(
    context: Context,
    customerName: String,
    phoneNumber: String,
    moveDate: String,
    startTime: String,
    moveType: String,
    departure: String,
    destination: String,
    amount: String,
    roomItemsSummary: String,
    memo: String,
    totalExpectedVolume: String
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val formattedAmount = run {
        val amt = amount.toLongOrNull() ?: 0L
        NumberFormat.getNumberInstance(Locale.KOREA).format(amt)
    }

    val htmlDocument = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>이사 견적서 - $customerName 고객님</title>
            <style>
                body { font-family: sans-serif; padding: 20px; line-height: 1.6; }
                h1 { text-align: center; color: #333; margin-bottom: 30px; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
                th { background-color: #f5f5f5; width: 30%; }
                .amount-box { border: 2px solid #0056b3; background-color: #e6f0fa; padding: 15px; text-align: center; font-size: 20px; font-weight: bold; color: #0056b3; margin-top: 20px; }
                .section-title { font-size: 16px; font-weight: bold; margin-top: 20px; margin-bottom: 10px; color: #333; border-bottom: 1px solid #333; padding-bottom: 5px; }
                pre { white-space: pre-wrap; font-family: sans-serif; background-color: #f9f9f9; padding: 15px; border-radius: 5px; }
            </style>
        </head>
        <body>
            <h1>이사 견적서</h1>
            <table>
                <tr>
                    <th>고객명</th>
                    <td>$customerName</td>
                </tr>
                <tr>
                    <th>연락처</th>
                    <td>$phoneNumber</td>
                </tr>
                <tr>
                    <th>이사 종류</th>
                    <td>$moveType</td>
                </tr>
                <tr>
                    <th>이사 날짜</th>
                    <td>$moveDate ${if (startTime.isNotBlank()) "($startTime)" else ""}</td>
                </tr>
                <tr>
                    <th>출발지</th>
                    <td>$departure</td>
                </tr>
                <tr>
                    <th>도착지</th>
                    <td>$destination</td>
                </tr>
                ${if (totalExpectedVolume.isNotBlank()) {
                    "<tr><th>총 예상물량</th><td>${totalExpectedVolume}t</td></tr>"
                } else ""}
            </table>

            ${if (roomItemsSummary.isNotBlank()) {
                "<div class='section-title'>공간별 짐 목록</div><pre>$roomItemsSummary</pre>"
            } else ""}

            ${if (memo.isNotBlank()) {
                "<div class='section-title'>특이사항 및 메모</div><pre>$memo</pre>"
            } else ""}

            <div class="amount-box">
                최종 견적 금액: ${formattedAmount}원
            </div>
        </body>
        </html>
    """.trimIndent()

    val webView = WebView(context).apply {
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter = createPrintDocumentAdapter("이사 견적서 - $customerName")
                val jobName = "이사 견적서_Job"
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            }
        }
        loadDataWithBaseURL(null, htmlDocument, "text/html", "UTF-8", null)
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
