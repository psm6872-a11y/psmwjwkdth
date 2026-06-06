package com.example.danallacalendar.estimate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
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
    var currentStep by remember { mutableStateOf(1) }

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
            onBack = onNavigateBack
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = "이사 견적서 작성 (${currentStep}/4)", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 18.sp
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentStep > 1) {
                                currentStep--
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
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
                                onSelectMoveDate = { showDatePicker(moveDate) { viewModel.moveDate.value = it } },
                                startTime = startTime,
                                onSelectStartTime = { showTimePicker { viewModel.startTime.value = it } },
                                amount = amount,
                                onAmountChange = { viewModel.amount.value = it; viewModel.autoSaveToGoogleSheets() },
                                memo = memo,
                                onMemoChange = { viewModel.memo.value = it; viewModel.autoSaveToGoogleSheets() },
                                googleSheetsUrl = googleSheetsUrl,
                                onSheetsUrlChange = { viewModel.googleSheetsUrl.value = it },
                                estimateDate = estimateDate,
                                onSelectEstimateDate = { showDatePicker(estimateDate) { viewModel.estimateDate.value = it } }
                            )
                            4 -> Step4PreviewAndActions(
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
                                onPrint = {
                                    printEstimate(context, customerName, phoneNumber, moveDate, startTime, moveType, departure, destination, amount, viewModel.formatRoomItemsSummary(), memo)
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

                    // Bottom Navigation Row for Navigation (Next / Back buttons)
                    if (currentStep > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = { currentStep-- },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("이전")
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            if (currentStep < 4) {
                                Button(
                                    onClick = { currentStep++ },
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

@Composable
fun Step1StartScreen(
    onCategorySelected: (String) -> Unit,
    onBack: () -> Unit
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
    onUpdateCount: (space: String, item: String, count: Int) -> Unit
) {
    val spaces = listOf("방1", "방2", "방3", "방4", "거실", "주방", "그외")
    var selectedTabIndex by remember { mutableStateOf(0) }
    val currentSpace = spaces[selectedTabIndex]

    val predefinedItems = remember {
        mapOf(
            "방1" to listOf("침대", "옷장", "책상", "서랍장", "행거"),
            "방2" to listOf("침대", "옷장", "책상", "서랍장", "행거"),
            "방3" to listOf("침대", "옷장", "책상", "서랍장", "행거"),
            "방4" to listOf("침대", "옷장", "책상", "서랍장", "행거"),
            "거실" to listOf("소파", "TV", "에어컨", "장식장", "피아노"),
            "주방" to listOf("냉장고", "김치냉장고", "식탁", "전자레인지", "정수기"),
            "그외" to listOf("세탁기", "건조기", "자전거", "운동기구", "박스")
        )
    }

    var customItemName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            spaces.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${currentSpace}의 짐 목록 입력",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Custom Item Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customItemName,
                    onValueChange = { customItemName = it },
                    label = { Text("추가 짐 항목 이름") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (customItemName.isNotBlank()) {
                            onUpdateCount(currentSpace, customItemName.trim(), 1)
                            customItemName = ""
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add custom item", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Predefined and added items list
            val itemsToDisplay = remember(currentSpace, roomItems) {
                val list = predefinedItems[currentSpace]?.toMutableList() ?: mutableListOf()
                roomItems[currentSpace]?.keys?.forEach { item ->
                    if (!list.contains(item)) {
                        list.add(item)
                    }
                }
                list
            }

            itemsToDisplay.forEach { item ->
                val currentCount = roomItems[currentSpace]?.get(item) ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onUpdateCount(currentSpace, item, currentCount - 1) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "$currentCount",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { onUpdateCount(currentSpace, item, currentCount + 1) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

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
    memo: String
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
