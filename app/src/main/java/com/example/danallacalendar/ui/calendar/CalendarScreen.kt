package com.example.danallacalendar.ui.calendar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danallacalendar.data.model.CalendarEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onExitRoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()

    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedEventForEdit by remember { mutableStateOf<CalendarEvent?>(null) }

    val sdfYearMonth = SimpleDateFormat("yyyy년 M월", Locale.KOREAN)
    val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val selectedDateStr = sdfDay.format(selectedDate.time)

    fun copyToClipboard(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Room Code", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "공유 코드가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("공유 캘린더", fontWeight = FontWeight.Bold, color = Color.White)
                        // Sync Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (syncStatus == "동기화됨") Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(syncStatus, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { copyToClipboard(viewModel.roomCode) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(viewModel.roomCode, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "코드 복사",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onExitRoom) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "방 나가기",
                            tint = Color.Red
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedEventForEdit = null
                    showDialog = true
                },
                containerColor = Color(0xFF6366F1),
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "일정 추가")
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0F172A))
        ) {
            // Month Navigation Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.prevMonth() }) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "이전 달", tint = Color.White)
                }
                Text(
                    text = sdfYearMonth.format(currentDate.time),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "다음 달", tint = Color.White)
                }
            }

            // Calendar Month Grid View
            val daysInMonth = getDaysInMonthList(currentDate)
            MonthGrid(
                days = daysInMonth,
                selectedDate = selectedDate,
                events = events,
                onDayClick = { selectedDate = it }
            )

            Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Selected Day Events Title
            Text(
                text = "${selectedDate.get(Calendar.DAY_OF_MONTH)}일 일정 목록",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Day's Events List
            val dayEvents = events.filter { it.date == selectedDateStr }
            if (dayEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("등록된 일정이 없습니다.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dayEvents, key = { it.id }) { event ->
                        val isMyEvent = event.createdBy == viewModel.deviceUUID
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedEventForEdit = event
                                    showDialog = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMyEvent) Color(0xFF1E293B) else Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (event.description.isNotEmpty()) {
                                        Text(
                                            text = event.description,
                                            fontSize = 13.sp,
                                            color = Color.LightGray,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "작성자: ${if (isMyEvent) "나" else event.createdByName}",
                                        fontSize = 11.sp,
                                        color = if (isMyEvent) Color(0xFF6366F1) else Color(0xFF10B981)
                                    )
                                }
                                Text(
                                    text = event.time,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        EventDialog(
            event = selectedEventForEdit,
            selectedDateStr = selectedDateStr,
            deviceUUID = viewModel.deviceUUID,
            onDismiss = { showDialog = false },
            onConfirm = { title, dateStr, timeStr, desc ->
                showDialog = false
                if (selectedEventForEdit == null) {
                    viewModel.addEvent(title, dateStr, timeStr, desc)
                } else {
                    viewModel.updateEvent(selectedEventForEdit!!.copy(title = title, date = dateStr, time = timeStr, description = desc))
                }
            },
            onDelete = {
                showDialog = false
                selectedEventForEdit?.let { viewModel.deleteEvent(it) }
            }
        )
    }
}

@Composable
fun MonthGrid(
    days: List<Calendar?>,
    selectedDate: Calendar,
    events: List<CalendarEvent>,
    onDayClick: (Calendar) -> Unit
) {
    val dayOfWeekHeaders = listOf("일", "월", "화", "수", "목", "금", "토")
    val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        // Day of Week Headers
        Row(modifier = Modifier.fillMaxWidth()) {
            for (header in dayOfWeekHeaders) {
                Text(
                    text = header,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = when (header) {
                        "일" -> Color(0xFFEF4444) // Red
                        "토" -> Color(0xFF3B82F6) // Blue
                        else -> Color.Gray
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Weeks
        val chunked = days.chunked(7)
        for (week in chunked) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (day in week) {
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        val isSelected = sdfDay.format(day.time) == sdfDay.format(selectedDate.time)
                        val hasEvents = events.any { it.date == sdfDay.format(day.time) }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { onDayClick(day) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = day.get(Calendar.DAY_OF_MONTH).toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = when {
                                    isSelected -> Color(0xFF818CF8)
                                    day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY -> Color(0xFFF87171)
                                    day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY -> Color(0xFF60A5FA)
                                    else -> Color.White
                                }
                            )
                            if (hasEvents) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF6366F1))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Generate Month grid list including offsets
fun getDaysInMonthList(currentDate: Calendar): List<Calendar?> {
    val list = ArrayList<Calendar?>()
    val cal = currentDate.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    val offset = cal.get(Calendar.DAY_OF_WEEK) - 1
    for (i in 0 until offset) {
        list.add(null)
    }

    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (i in 1..maxDays) {
        val dayCal = cal.clone() as Calendar
        dayCal.set(Calendar.DAY_OF_MONTH, i)
        list.add(dayCal)
    }

    // Complete the grid to full weeks
    while (list.size % 7 != 0) {
        list.add(null)
    }

    return list
}
