package com.example.danallacalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import com.example.danallacalendar.ui.components.RecentCallsPickerDialog
import com.example.danallacalendar.data.CalendarCategory
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.ui.viewmodel.CalendarViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

val calendarColors = listOf(
    "#ff3b30", // Red
    "#ff9500", // Orange
    "#ffcc00", // Yellow
    "#34c759", // Green
    "#5ac8fa", // Light Blue
    "#007aff", // Blue
    "#5856d6", // Indigo
    "#af52de", // Purple
    "#ff2d55", // Pink
    "#a2845e", // Brown
    "#8e8e93", // Gray
    "#008080", // Teal
    "#e67e22", // Pumpkin
    "#1abc9c"  // Turquoise
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddEditEventScreen(
    eventId: Int?,
    onNavigateBack: () -> Unit,
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs: SharedPreferences = remember { context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE) }

    // Form States
    var title by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(false) }
    var startMillis by remember { mutableStateOf(selectedDate) }
    var endMillis by remember { mutableStateOf(selectedDate + 60 * 60 * 1000L) } // Default +1 hour
    var location by remember { mutableStateOf("") }   // 위치 1 상단
    var location1b by remember { mutableStateOf("") } // 위치 1 하단
    var location2 by remember { mutableStateOf("") }  // 위치 2 상단
    var location2b by remember { mutableStateOf("") } // 위치 2 하단
    var notes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CalendarCategory?>(null) }
    var selectedColorHex by remember { mutableStateOf("#ff3b30") }
    var repeatType by remember { mutableStateOf("NONE") }
    var reminderMinutes by remember { mutableStateOf(60) }
    var syncId by remember { mutableStateOf<String?>(null) }
    var isSynced by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }

    val isReadOnly = false

    // Dialog control states
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecentCallsDialog by remember { mutableStateOf(false) }
    var showTitleDatePicker by remember { mutableStateOf(false) }

    // Dropdown control states
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showTitleCategoryDropdown by remember { mutableStateOf(false) }
    var showRepeatDropdown by remember { mutableStateOf(false) }
    var showReminderDropdown by remember { mutableStateOf(false) }

    // Set default category and color when categories load
    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            val lastUsedId = prefs.getInt("last_used_category_id", -1)
            val defaultCat = categories.find { it.id == lastUsedId }
                ?: categories.find { it.name == "공유 캘린더" }
                ?: categories.find { it.name == "내 캘린더" }
                ?: categories.first()
            selectedCategory = defaultCat
        }
    }

    var isTimeInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(selectedDate) {
        if (eventId == null && !isTimeInitialized) {
            val calStart = Calendar.getInstance().apply { timeInMillis = selectedDate }
            val now = Calendar.getInstance()
            calStart.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY) + 1)
            calStart.set(Calendar.MINUTE, 0)
            calStart.set(Calendar.SECOND, 0)
            calStart.set(Calendar.MILLISECOND, 0)
            
            startMillis = calStart.timeInMillis
            endMillis = calStart.timeInMillis + 60 * 60 * 1000L
            isTimeInitialized = true
        }
    }

    // Load event if editing
    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(eventId, isLoaded, viewModel.monthlyEvents.value) {
        if (eventId != null && !isLoaded) {
            val event = viewModel.monthlyEvents.value.find { it.id == eventId }
            if (event != null) {
                title = event.title
                isAllDay = event.isAllDay
                startMillis = event.startMillis
                endMillis = event.endMillis
                val locationParts = event.location.split("|||")
                location  = locationParts.getOrNull(0) ?: ""
                location1b = locationParts.getOrNull(1) ?: ""
                location2  = locationParts.getOrNull(2) ?: ""
                location2b = locationParts.getOrNull(3) ?: ""
                notes = event.notes
                repeatType = event.repeatType
                reminderMinutes = event.reminderMinutes
                val cat = categories.find { cat -> cat.id == event.calendarId } ?: categories.firstOrNull()
                selectedCategory = cat
                selectedColorHex = event.colorHex ?: cat?.colorHex ?: "#ff3b30"
                syncId = event.syncId
                isSynced = event.isSynced
                isCompleted = event.isCompleted
                isLoaded = true
            }
        }
    }

    val onSaveClick = {
        if (title.isNotBlank() && selectedCategory != null) {
            val event = Event(
                id = eventId ?: 0,
                title = title,
                startMillis = startMillis,
                endMillis = endMillis,
                isAllDay = isAllDay,
                location = listOf(location, location1b, location2, location2b)
                    .joinToString("|||"),
                notes = notes,
                repeatType = repeatType,
                reminderMinutes = reminderMinutes,
                calendarId = selectedCategory!!.id,
                syncId = syncId,
                isSynced = isSynced,
                colorHex = selectedColorHex,
                isCompleted = isCompleted
            )
            if (eventId == null) {
                viewModel.addEvent(event)
            } else {
                viewModel.updateEvent(event)
            }
            prefs.edit().putInt("last_used_category_id", selectedCategory!!.id).apply()
            onNavigateBack()
        }
    }

    val isImeVisible = WindowInsets.isImeVisible

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (eventId == null) "일정 추가" else "일정 편집", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "취소")
                    }
                },
                actions = {
                    Button(
                        onClick = onSaveClick,
                        enabled = title.isNotBlank() && !isReadOnly,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp).height(40.dp)
                    ) {
                        Text("저장", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },

        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isReadOnly) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "잠금",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "읽기 전용 권한입니다. 이 일정은 수정할 수 없습니다.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Title Input
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("제목", fontSize = 18.sp) },
                        singleLine = true,
                        enabled = !isReadOnly,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { if (!isReadOnly) showTitleDatePicker = true },
                        enabled = !isReadOnly,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "날짜 선택",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    val displayColorHex = selectedColorHex
                    Box(
                        modifier = Modifier.padding(end = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(displayColorHex)))
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable(enabled = !isReadOnly) { showTitleCategoryDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showTitleCategoryDropdown,
                            onDismissRequest = { showTitleCategoryDropdown = false }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                calendarColors.chunked(7).forEach { chunk ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        chunk.forEach { colorOption ->
                                            val isSelected = selectedColorHex.equals(colorOption, ignoreCase = true)
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(android.graphics.Color.parseColor(colorOption)))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 0.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        selectedColorHex = colorOption
                                                        showTitleCategoryDropdown = false
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Memo & Location Card (위치 위, 전화번호 아래)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {


                    // ── 위치 1 상단 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (location.isEmpty()) {
                                Text(
                                    text = "출발지 주소",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            BasicTextField(
                                value = location,
                                onValueChange = { location = it },
                                singleLine = false,
                                enabled = !isReadOnly,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    color = if (isReadOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 28.dp)
                                    .padding(vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (location.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nmap://search?query=${Uri.encode(location)}&appname=com.example.danallacalendar"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.map.naver.com/search2/search.naver?query=${Uri.encode(location)}"))
                                        context.startActivity(webIntent)
                                    }
                                } else {
                                    Toast.makeText(context, "위치를 입력해주세요.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("지도", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // ── 위치 1 하단 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (location1b.isEmpty()) {
                                Text(
                                    text = "동/호수",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            BasicTextField(
                                value = location1b,
                                onValueChange = { location1b = it },
                                singleLine = true,
                                enabled = !isReadOnly,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    color = if (isReadOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 28.dp)
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    // ── 위치 2 그룹 구분선 + 헤더 ──
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.5.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))



                    // ── 위치 2 상단 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (location2.isEmpty()) {
                                Text(
                                    text = "도착지 주소",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            BasicTextField(
                                value = location2,
                                onValueChange = { location2 = it },
                                singleLine = false,
                                enabled = !isReadOnly,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    color = if (isReadOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 28.dp)
                                    .padding(vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (location2.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nmap://search?query=${Uri.encode(location2)}&appname=com.example.danallacalendar"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.map.naver.com/search2/search.naver?query=${Uri.encode(location2)}"))
                                        context.startActivity(webIntent)
                                    }
                                } else {
                                    Toast.makeText(context, "위치 2 (상)을 입력해주세요.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("지도", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // ── 위치 2 하단 ──

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (location2b.isEmpty()) {
                                Text(
                                    text = "동/호수",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            BasicTextField(
                                value = location2b,
                                onValueChange = { location2b = it },
                                singleLine = true,
                                enabled = !isReadOnly,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    color = if (isReadOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 28.dp)
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Phone Number Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (notes.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${notes.trim()}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "전화 기능을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "전화 걸기",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (notes.isEmpty()) {
                                Text("전화번호", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            BasicTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                singleLine = true,
                                enabled = !isReadOnly,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    color = if (isReadOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        IconButton(
                            onClick = { if (!isReadOnly) showRecentCallsDialog = true },
                            enabled = !isReadOnly,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "최근 통화",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Period Selectors Card (Samsung Style Date-Time layout)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // All Day switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("하루 종일", fontSize = 16.sp)
                        }
                        Switch(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it },
                            enabled = !isReadOnly
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Start Time Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "시작", 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(60.dp)
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Start Date Button
                            DateButton(
                                millis = startMillis,
                                modifier = Modifier.weight(1f)
                            ) { if (!isReadOnly) showStartDatePicker = true }
                            // Start Time Button
                            if (!isAllDay) {
                                TimeButton(
                                    millis = startMillis,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (!isReadOnly) {
                                        showStartTimePicker = !showStartTimePicker
                                        if (showStartTimePicker) showEndTimePicker = false
                                    }
                                }
                            }
                        }
                    }

                    // Start Time Picker Inline
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showStartTimePicker && !isAllDay
                    ) {
                        if (showStartTimePicker) {
                            val calendar = remember(startMillis) { Calendar.getInstance().apply { timeInMillis = startMillis } }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                WheelTimePicker(
                                    initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                                    initialMinute = calendar.get(Calendar.MINUTE),
                                    onTimeChanged = { hour, minute ->
                                        val cal = Calendar.getInstance().apply {
                                            timeInMillis = startMillis
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        startMillis = cal.timeInMillis
                                        if (startMillis >= endMillis) {
                                            endMillis = startMillis + 60 * 60 * 1000L
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // End Time Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "종료", 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(60.dp)
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // End Date Button
                            DateButton(
                                millis = endMillis,
                                modifier = Modifier.weight(1f)
                            ) { if (!isReadOnly) showEndDatePicker = true }
                            // End Time Button
                            if (!isAllDay) {
                                TimeButton(
                                    millis = endMillis,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (!isReadOnly) {
                                        showEndTimePicker = !showEndTimePicker
                                        if (showEndTimePicker) showStartTimePicker = false
                                    }
                                }
                            }
                        }
                    }

                    // End Time Picker Inline
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showEndTimePicker && !isAllDay
                    ) {
                        if (showEndTimePicker) {
                            val calendar = remember(endMillis) { Calendar.getInstance().apply { timeInMillis = endMillis } }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                WheelTimePicker(
                                    initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                                    initialMinute = calendar.get(Calendar.MINUTE),
                                    onTimeChanged = { hour, minute ->
                                        val cal = Calendar.getInstance().apply {
                                            timeInMillis = endMillis
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        endMillis = cal.timeInMillis
                                        if (endMillis <= startMillis) {
                                            endMillis = startMillis + 60 * 60 * 1000L
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Category & Repeat & Alerts Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Calendar Category Selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isReadOnly) { showCategoryDropdown = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("캘린더", fontSize = 16.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            selectedCategory?.let {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(it.colorHex)))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(it.name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Category Dropdown
                    Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopEnd)) {
                        DropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false }
                        ) {
                            categories.filter { !it.name.endsWith("색 캘린더") }.forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(android.graphics.Color.parseColor(category.colorHex)))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(category.name)
                                        }
                                    },
                                    onClick = {
                                        selectedCategory = category
                                        prefs.edit().putInt("last_used_category_id", category.id).apply()
                                        showCategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // 2. Repeat Settings
                    val repeatLabels = mapOf("NONE" to "반복 안 함", "DAILY" to "매일", "WEEKLY" to "매주", "MONTHLY" to "매월", "YEARLY" to "매년")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isReadOnly) { showRepeatDropdown = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("반복", fontSize = 16.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(repeatLabels[repeatType] ?: "반복 안 함", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Repeat Dropdown
                    Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopEnd)) {
                        DropdownMenu(
                            expanded = showRepeatDropdown,
                            onDismissRequest = { showRepeatDropdown = false }
                        ) {
                            repeatLabels.forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        repeatType = type
                                        showRepeatDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // 3. Reminder / Alerts
                    val reminderLabels = mapOf(-1 to "알림 없음", 0 to "정각", 10 to "10분 전", 60 to "1시간 전", 1440 to "1일 전")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isReadOnly) { showReminderDropdown = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("알림", fontSize = 16.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(reminderLabels[reminderMinutes] ?: "알림 없음", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Reminder Dropdown
                    Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopEnd)) {
                        DropdownMenu(
                            expanded = showReminderDropdown,
                            onDismissRequest = { showReminderDropdown = false }
                        ) {
                            reminderLabels.forEach { (mins, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        reminderMinutes = mins
                                        showReminderDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

        }
    }

    // --- DIALOGS ---
    
    // Title Date Picker Dialog
    if (showTitleDatePicker) {
        val initialDialogMillis = remember(title) {
            val fallbackMillis = System.currentTimeMillis()
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
                sdf.parse(title)?.time ?: fallbackMillis
            } catch (e: Exception) {
                try {
                    val sdf2 = SimpleDateFormat("MM-dd", Locale.KOREAN)
                    val calParsed = Calendar.getInstance().apply {
                        time = sdf2.parse(title) ?: Date(fallbackMillis)
                        set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                    }
                    calParsed.timeInMillis
                } catch (e2: Exception) {
                    fallbackMillis
                }
            }
        }
        MyDatePickerDialog(
            initialMillis = initialDialogMillis,
            onDismiss = { showTitleDatePicker = false },
            onDateSelected = { selectedDateMillis ->
                val formatter = SimpleDateFormat("MM-dd", Locale.KOREAN)
                title = formatter.format(Date(selectedDateMillis))
                showTitleDatePicker = false
            }
        )
    }

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        MyDatePickerDialog(
            initialMillis = startMillis,
            onDismiss = { showStartDatePicker = false },
            onDateSelected = { selectedDateMillis ->
                // Adjust start date, keeping time
                val currentCal = Calendar.getInstance().apply { timeInMillis = startMillis }
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMillis
                    set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startMillis = newCal.timeInMillis
                // Adjust end date if start date is after end date
                if (startMillis >= endMillis) {
                    endMillis = startMillis + 60 * 60 * 1000L
                }
                showStartDatePicker = false
            }
        )
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        MyDatePickerDialog(
            initialMillis = endMillis,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { selectedDateMillis ->
                val currentCal = Calendar.getInstance().apply { timeInMillis = endMillis }
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMillis
                    set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                endMillis = newCal.timeInMillis
                if (endMillis <= startMillis) {
                    endMillis = startMillis + 60 * 60 * 1000L
                }
                showEndDatePicker = false
            }
        )
    }

    // Start/End Time Pickers are now inline, no dialog triggers needed.

    // Recent Calls Picker Dialog
    if (showRecentCallsDialog) {
        RecentCallsPickerDialog(
            onCallSelected = { number ->
                notes = number
                showRecentCallsDialog = false
            },
            onDismiss = { showRecentCallsDialog = false }
        )
    }
}

@Composable
fun DateButton(millis: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val formatter = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)
    val label = formatter.format(Date(millis))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label, 
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun TimeButton(millis: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val formatter = SimpleDateFormat("HH:mm", Locale.KOREAN)
    val label = formatter.format(Date(millis))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label, 
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun MyDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val koreanDayNames = listOf("일", "월", "화", "수", "목", "금", "토")
    val today = remember { Calendar.getInstance() }

    val initialCal = remember { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    var selectedYear by remember { mutableStateOf(initialCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(initialCal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(initialCal.get(Calendar.DAY_OF_MONTH)) }

    // 기준 페이지: 1200 = 현재 달. 각 페이지는 기준에서 +/- 월 수
    val baseYear = initialCal.get(Calendar.YEAR)
    val baseMonth = initialCal.get(Calendar.MONTH)
    val basePage = 1200
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = basePage,
        pageCount = { 2400 }
    )
    val coroutineScope = rememberCoroutineScope()

    // 현재 표시 중인 페이지에서 년/월 계산
    val currentPage = pagerState.currentPage
    val offsetMonths = currentPage - basePage
    val displayCal = remember(currentPage) {
        Calendar.getInstance().apply {
            set(baseYear, baseMonth, 1)
            add(Calendar.MONTH, offsetMonths)
        }
    }
    val displayYear = displayCal.get(Calendar.YEAR)
    val displayMonth = displayCal.get(Calendar.MONTH)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onDateSelected(cal.timeInMillis)
            }) { Text("선택", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        title = null,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── 월 네비게이션 헤더 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
                    }
                    Text(
                        text = "${displayYear}년 ${displayMonth + 1}월",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── 요일 헤더 ──
                Row(modifier = Modifier.fillMaxWidth()) {
                    koreanDayNames.forEachIndexed { idx, day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (idx) {
                                0 -> Color(0xFFE53935)
                                6 -> Color(0xFF1E88E5)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── 스와이프 달력 그리드 ──
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) { page ->
                    val offsetM = page - basePage
                    val pageCal = remember(page) {
                        Calendar.getInstance().apply {
                            set(baseYear, baseMonth, 1)
                            add(Calendar.MONTH, offsetM)
                        }
                    }
                    val pYear = pageCal.get(Calendar.YEAR)
                    val pMonth = pageCal.get(Calendar.MONTH)
                    val firstDayOfWeek = (pageCal.get(Calendar.DAY_OF_WEEK) - 1)
                    val daysInMonth = pageCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val totalCells = firstDayOfWeek + daysInMonth
                    val rows = (totalCells + 6) / 7

                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (row in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val dayNum = row * 7 + col - firstDayOfWeek + 1
                                    if (dayNum < 1 || dayNum > daysInMonth) {
                                        Box(modifier = Modifier.weight(1f).height(38.dp))
                                    } else {
                                        val isSelected = dayNum == selectedDay &&
                                                pMonth == selectedMonth &&
                                                pYear == selectedYear
                                        val isToday = dayNum == today.get(Calendar.DAY_OF_MONTH) &&
                                                pMonth == today.get(Calendar.MONTH) &&
                                                pYear == today.get(Calendar.YEAR)

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable {
                                                    selectedDay = dayNum
                                                    selectedMonth = pMonth
                                                    selectedYear = pYear
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isToday -> MaterialTheme.colorScheme.primary
                                                    col == 0 -> Color(0xFFE53935)
                                                    col == 6 -> Color(0xFF1E88E5)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

// MyTimePickerDialog has been replaced by inline WheelTimePicker inside Period Selectors Card.

@Composable
fun WheelTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    // 24시간제: 0~23
    val hourList = (0..23).map { String.format("%02d", it) }
    val minuteList = listOf("00", "10", "20", "30", "40", "50")

    val initialHourIndex = initialHour.coerceIn(0, 23)

    // Map initialMinute to the nearest 10 minutes step (00, 10, 20, 30, 40, 50)
    val roundedMinute = ((initialMinute + 5) / 10 * 10) % 60
    val initialMinuteIndex = roundedMinute / 10

    var selectedHourIndex by remember { mutableStateOf(initialHourIndex) }
    var selectedMinuteIndex by remember { mutableStateOf(initialMinuteIndex) }

    LaunchedEffect(selectedHourIndex, selectedMinuteIndex) {
        val hour = selectedHourIndex
        val minute = minuteList[selectedMinuteIndex].toInt()
        onTimeChanged(hour, minute)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        Text(
            text = ":",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
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
    itemHeight: androidx.compose.ui.unit.Dp = 42.dp
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState)

    val selectedIndex by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex
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
                        fontSize = 16.sp,
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
