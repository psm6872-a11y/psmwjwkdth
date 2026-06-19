package com.example.danallacalendar.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.example.danallacalendar.R
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danallacalendar.data.CalendarCategory
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.ui.components.DrawerContent
import com.example.danallacalendar.ui.viewmodel.CalendarViewModel
import com.example.danallacalendar.ui.viewmodel.CalendarViewMode
import com.example.danallacalendar.ui.viewmodel.EventFilter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.example.danallacalendar.members.MemberViewModel
import com.example.danallacalendar.members.MemberPanel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset


data class CalendarDay(
    val dateInMillis: Long,
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val dayOfWeek: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarMainScreen(
    onNavigateToAddEditEvent: (Int?) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToEstimate: () -> Unit,
    onNavigateToEstimateList: () -> Unit,
    onExitRoom: () -> Unit,
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    var showPermissionGuideDialog by remember { mutableStateOf(false) }

    val memberViewModel: MemberViewModel = hiltViewModel()
    val members by memberViewModel.members.collectAsStateWithLifecycle()
    val isCreator by memberViewModel.isCreator.collectAsStateWithLifecycle()

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            onExitRoom()
        }
    }

    LaunchedEffect(viewModel.roomCode) {
        memberViewModel.initializeRoom(viewModel.roomCode)
    }

    LaunchedEffect(Unit) {
        memberViewModel.kickedEvent.collect {
            viewModel.logout()
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                showPermissionGuideDialog = true
            }
        }
    }




    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val monthlyEvents by viewModel.monthlyEvents.collectAsStateWithLifecycle()
    val selectedDateEvents by viewModel.selectedDateEvents.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val eventFilter by viewModel.eventFilter.collectAsStateWithLifecycle()

    // 마감 날짜 Set - DB에서 영구 저장
    val deadlineDates by viewModel.deadlineDates.collectAsStateWithLifecycle()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val jsonString = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                if (jsonString != null) {
                    val defaultCatId = categories.firstOrNull()?.id ?: 1
                    viewModel.importEventsFromJson(
                        jsonString = jsonString,
                        targetCalendarId = defaultCatId,
                        onSuccess = { count ->
                            Toast.makeText(context, "${count}개의 일정을 성공적으로 가져왔습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, "가져오기 실패: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "파일 열기 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }



    if (showPermissionGuideDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionGuideDialog = false },
            title = { Text(text = "알림 권한 안내") },
            text = { Text(text = "공유 멤버가 일정을 추가, 수정, 삭제할 때 알림을 받으려면 알림 권한이 필요합니다. 설정 화면에서 알림을 허용해 주세요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionGuideDialog = false
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text(text = "설정으로 이동", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionGuideDialog = false }) {
                    Text(text = "닫기")
                }
            }
        )
    }



    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !drawerState.isClosed,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    categories = categories,
                    isLoggedIn = isLoggedIn,
                    userName = userName,
                    roomCode = viewModel.roomCode,
                    members = members,
                    currentDeviceUUID = memberViewModel.deviceUUID,
                    isCreator = isCreator,
                    onRemoveMember = { memberViewModel.removeMember(it) },
                    onTransferHost = { memberViewModel.transferHost(it) },
                    onLogoutClick = { viewModel.logout() },
                    onToggleCategory = { viewModel.toggleCategoryVisibility(it) },
                    onImportClick = {
                        scope.launch { drawerState.close() }
                        filePickerLauncher.launch("*/*")
                    },
                    onShareAppClick = {
                        scope.launch { drawerState.close() }
                        try {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "다날라 캘린더 앱을 설치해보세요!\n다운로드: https://github.com/psm6872-a11y/psmwjwkdth/releases/latest")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "공유하기"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "앱 공유 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onEstimateListClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToEstimateList()
                    },

                    onCloseClick = {
                        scope.launch { drawerState.close() }
                    },
                    onBackupClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToBackup()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                MainTopAppBar(
                    currentMonth = currentMonth,
                    viewMode = viewMode,
                    selectedFilter = eventFilter,
                    onFilterSelected = { viewModel.setEventFilter(it) },
                    onToggleDrawer = { scope.launch { drawerState.open() } },
                    onToggleViewMode = { viewModel.toggleViewMode() },
                    onNavigateToSearch = { onNavigateToSearch() },
                    onNavigateToEstimate = onNavigateToEstimate,
                    onGoToToday = { viewModel.selectDate(System.currentTimeMillis()) }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onNavigateToAddEditEvent(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "일정 추가")
                }
            },
            modifier = modifier
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Calendar Grid Component
                CalendarGridSection(
                    selectedDate = selectedDate,
                    currentMonth = currentMonth,
                    viewMode = viewMode,
                    monthlyEvents = monthlyEvents,
                    categories = categories,
                    deadlineDates = deadlineDates,
                    onDaySelected = { viewModel.selectDate(it) },
                    onMonthChanged = { viewModel.selectDate(it.timeInMillis) },
                    onWeekSelected = { viewModel.selectDate(it) },
                    onCollapseToggle = { viewModel.toggleViewMode() }
                )

                // Bottom Panel: Selected Date Header and Event List
                EventListSection(
                    selectedDate = selectedDate,
                    events = monthlyEvents,
                    categories = categories,
                    onEventClick = { onNavigateToAddEditEvent(it.id) },
                    onDeleteEvent = { viewModel.deleteEvent(it) },
                    onToggleComplete = { viewModel.updateEvent(it.copy(isCompleted = !it.isCompleted)) },
                    onUpdateEvent = { viewModel.updateEvent(it) },
                    isDeadlineSet = deadlineDates.any { isSameDay(it, selectedDate) },
                    onDeadlineToggle = { dateMillis ->
                        viewModel.toggleDeadlineDate(dateMillis)
                    },
                    viewMode = viewMode,
                    onSwipeDownAtTop = { viewModel.setViewMode(CalendarViewMode.MONTH) },
                    onSwipeUp = { viewModel.setViewMode(CalendarViewMode.WEEK) },
                    onDateSelected = { viewModel.selectDate(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }



    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    currentMonth: Calendar,
    viewMode: CalendarViewMode,
    selectedFilter: EventFilter,
    onFilterSelected: (EventFilter) -> Unit,
    onToggleDrawer: () -> Unit,
    onToggleViewMode: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToEstimate: () -> Unit,
    onGoToToday: () -> Unit
) {
    val monthFormat = SimpleDateFormat("M월", Locale.KOREAN)
    val monthStr = monthFormat.format(currentMonth.time)

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onGoToToday() }
                ) {
                    Text(
                        text = monthStr,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .offset(x = (-8).dp, y = 2.dp)
                ) {
                    FilterButton(
                        text = "방문",
                        isSelected = selectedFilter == EventFilter.ESTIMATE,
                        onClick = { onFilterSelected(EventFilter.ESTIMATE) }
                    )
                    FilterButton(
                        text = "계약",
                        isSelected = selectedFilter == EventFilter.CONTRACT,
                        onClick = { onFilterSelected(EventFilter.CONTRACT) }
                    )
                    FilterButton(
                        text = "전체",
                        isSelected = selectedFilter == EventFilter.ALL,
                        onClick = { onFilterSelected(EventFilter.ALL) }
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onToggleDrawer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "메뉴",
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF3E5F5))
                        .border(1.dp, Color(0xFFAB47BC), RoundedCornerShape(6.dp))
                        .clickable { onNavigateToEstimate() }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "견적내기",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E24AA),
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
                // 검색(돋보기) 버튼
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { onNavigateToSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "검색",
                        modifier = Modifier.size(20.dp)
                    )
                }
                // 오늘날짜 이동 버튼
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onGoToToday() },
                    contentAlignment = Alignment.Center
                ) {
                    val dayStr = SimpleDateFormat("d", Locale.getDefault()).format(Date())
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp))
                    ) {
                        Text(
                            text = dayStr,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            lineHeight = 9.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                                )
                            ),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun FilterButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    
    Box(
        modifier = Modifier
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(containerColor)
            .clickable { onClick() }
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarGridSection(
    selectedDate: Long,
    currentMonth: Calendar,
    viewMode: CalendarViewMode,
    monthlyEvents: List<Event>,
    categories: List<CalendarCategory>,
    deadlineDates: Set<Long> = emptySet(),
    onDaySelected: (Long) -> Unit,
    onMonthChanged: (Calendar) -> Unit,
    onWeekSelected: (Long) -> Unit,
    onCollapseToggle: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    LaunchedEffect(monthlyEvents) {
        android.util.Log.d("CalendarDebug", "monthlyEvents size: ${monthlyEvents.size}")
        monthlyEvents.forEach { event ->
            android.util.Log.d("CalendarDebug", "event: ${event.title}, start: ${event.startMillis}, calendarId: ${event.calendarId}")
        }
    }

    LaunchedEffect(categories) {
        android.util.Log.d("CalendarDebug", "categories size: ${categories.size}")
        categories.forEach { cat ->
            android.util.Log.d("CalendarDebug", "category: ${cat.name}, id: ${cat.id}, isVisible: ${cat.isVisible}")
        }
    }

    // base calculation pages
    fun getMonthPage(cal: Calendar): Int {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        return (year - 2000) * 12 + month
    }

    fun getCalendarFromMonthPage(page: Int): Calendar {
        val year = 2000 + page / 12
        val month = page % 12
        return Calendar.getInstance().apply {
            clearTimeToZero()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    fun getWeekPage(millis: Long): Int {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            clearTimeToZero()
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            add(Calendar.DAY_OF_YEAR, -(dayOfWeek - 1))
        }
        val baseCal = Calendar.getInstance().apply {
            set(2000, 0, 2, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = cal.timeInMillis - baseCal.timeInMillis
        val diffWeeks = diffMillis / (7 * 24 * 60 * 60 * 1000L)
        return diffWeeks.toInt()
    }

    fun getCalendarFromWeekPage(page: Int): Calendar {
        val baseCal = Calendar.getInstance().apply {
            set(2000, 0, 2, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        baseCal.add(Calendar.WEEK_OF_YEAR, page)
        return baseCal
    }

    val monthPagerState = rememberPagerState(
        initialPage = getMonthPage(currentMonth)
    ) { 5000 }

    val weekPagerState = rememberPagerState(
        initialPage = getWeekPage(selectedDate)
    ) { 5000 }

    // Sync Month PagerState with ViewModel currentMonth
    LaunchedEffect(currentMonth) {
        val targetPage = getMonthPage(currentMonth)
        if (monthPagerState.currentPage != targetPage) {
            monthPagerState.scrollToPage(targetPage)
        }
    }

    // Sync Week PagerState with ViewModel selectedDate
    LaunchedEffect(selectedDate) {
        val targetPage = getWeekPage(selectedDate)
        if (weekPagerState.currentPage != targetPage) {
            weekPagerState.scrollToPage(targetPage)
        }
    }

    // Sync when viewMode toggles
    LaunchedEffect(viewMode) {
        if (viewMode == CalendarViewMode.WEEK) {
            weekPagerState.scrollToPage(getWeekPage(selectedDate))
        } else {
            monthPagerState.scrollToPage(getMonthPage(currentMonth))
        }
    }

    // Sync from Month PagerState to ViewModel when settled
    LaunchedEffect(monthPagerState.settledPage) {
        if (viewMode == CalendarViewMode.MONTH) {
            val targetMonth = getCalendarFromMonthPage(monthPagerState.settledPage)
            if (getMonthPage(currentMonth) != monthPagerState.settledPage) {
                onMonthChanged(targetMonth)
            }
        }
    }

    // Sync from Week PagerState to ViewModel when settled
    LaunchedEffect(weekPagerState.settledPage) {
        if (viewMode == CalendarViewMode.WEEK) {
            val targetWeekSunday = getCalendarFromWeekPage(weekPagerState.settledPage)
            val currentSelectedCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
            val currentDayOfWeek = currentSelectedCal.get(Calendar.DAY_OF_WEEK)
            
            val targetDateCal = targetWeekSunday.clone() as Calendar
            targetDateCal.add(Calendar.DAY_OF_YEAR, currentDayOfWeek - 1)
            
            if (getWeekPage(selectedDate) != weekPagerState.settledPage) {
                onWeekSelected(targetDateCal.timeInMillis)
            }
        }
    }

    var totalDragY = 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                start = minOf(screenWidth, 400.dp) * 0.025f,
                end = minOf(screenWidth, 400.dp) * 0.025f,
                bottom = screenHeight * 0.01f
            )
            .pointerInput(viewMode) {
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -50f) {
                            if (viewMode == CalendarViewMode.MONTH) {
                                onCollapseToggle()
                            }
                        } else if (totalDragY > 50f) {
                            if (viewMode == CalendarViewMode.WEEK) {
                                onCollapseToggle()
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount
                    }
                )
            }
    ) {
        // Weekday labels (S M T W T F S)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = screenHeight * 0.005f)) {
            val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
            daysOfWeek.forEachIndexed { index, day ->
                val textColor = when (index) {
                    0 -> Color(0xFFFF4A4A) // Sunday
                    6 -> Color(0xFF2F80ED) // Saturday
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = (minOf(screenWidth, 400.dp).value * 0.035f).sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }

        // Animated Smooth Pager Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
        ) {
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                    fadeOut(animationSpec = tween(90))
                },
                label = "calendar_pager_transition"
            ) { targetMode ->
                if (targetMode == CalendarViewMode.MONTH) {
                    HorizontalPager(
                        state = monthPagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val pageMonthCal = getCalendarFromMonthPage(page)
                        val pageDays = getGridDays(pageMonthCal)
                        Column {
                            val rowsCount = pageDays.size / 7
                            for (r in 0 until rowsCount) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(screenHeight * 0.068f)
                                ) {
                                    for (c in 0 until 7) {
                                        val dayIndex = r * 7 + c
                                        if (dayIndex < pageDays.size) {
                                            val day = pageDays[dayIndex]
                                            val dayEvents = getDayEvents(day.dateInMillis, monthlyEvents)
                                            CalendarDayCell(
                                                day = day,
                                                isSelected = isSameDay(day.dateInMillis, selectedDate),
                                                isDeadline = deadlineDates.any { isSameDay(it, day.dateInMillis) },
                                                dayEvents = dayEvents,
                                                categories = categories,
                                                onClick = { onDaySelected(day.dateInMillis) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    HorizontalPager(
                        state = weekPagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val pageWeekCal = getCalendarFromWeekPage(page)
                        val pageDays = getWeekDays(pageWeekCal.timeInMillis)
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(screenHeight * 0.068f)
                            ) {
                                for (c in 0 until 7) {
                                    val day = pageDays[c]
                                    val dayEvents = getDayEvents(day.dateInMillis, monthlyEvents)
                                    CalendarDayCell(
                                        day = day,
                                        isSelected = isSameDay(day.dateInMillis, selectedDate),
                                        isDeadline = deadlineDates.any { isSameDay(it, day.dateInMillis) },
                                        dayEvents = dayEvents,
                                        categories = categories,
                                        onClick = { onDaySelected(day.dateInMillis) },
                                        modifier = Modifier.weight(1f)
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

@Composable
fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isDeadline: Boolean = false,
    dayEvents: List<Event>,
    categories: List<CalendarCategory>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val calendar = Calendar.getInstance().apply { timeInMillis = day.dateInMillis }
    val isSunday = day.dayOfWeek == Calendar.SUNDAY
    val isSaturday = day.dayOfWeek == Calendar.SATURDAY
    val isHoliday = getKoreanHolidayName(day.dateInMillis) != null
    val showSonEopNeunMark = isSonEopNeunDay(day.dateInMillis)

    val textAlpha = if (day.isCurrentMonth) 1.0f else 0.35f
    val dayTextColor = when {
        isHoliday || isSunday -> Color(0xFFFF4A4A).copy(alpha = textAlpha)
        isSaturday -> Color(0xFF2F80ED).copy(alpha = textAlpha)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onClick() }
    ) {
        // Date circle highlight wrapper Box for SonEopNeunMark alignment (centered in day cell)
        Box(
            modifier = Modifier
                .size(minOf(screenWidth, 400.dp) * 0.08f)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            day.isToday -> Color(0xFF757575)
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.dayOfMonth.toString(),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        day.isToday -> Color.White
                        isDeadline -> dayTextColor.copy(alpha = 0.2f)
                        else -> dayTextColor
                    },
                    fontSize = (minOf(screenWidth, 400.dp).value * 0.038f).sp,
                    fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        // Small indicator lines/dots for events (aligned to bottom)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(
                    start = minOf(screenWidth, 400.dp) * 0.005f,
                    top = 0.dp,
                    end = minOf(screenWidth, 400.dp) * 0.005f,
                    bottom = 0.dp
                )
                .height(screenHeight * 0.015f),
            verticalArrangement = Arrangement.spacedBy(screenHeight * 0.0015f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display up to 2 items
            val displayEvents = dayEvents.take(2)
            val barAlpha = if (day.isCurrentMonth) 1.0f else 0.3f
            displayEvents.forEach { event ->
                val category = categories.find { it.id == event.calendarId }
                val colorHex = event.colorHex ?: category?.colorHex ?: "#1c62f2"
                val catColor = Color(android.graphics.Color.parseColor(colorHex))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.004f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(catColor.copy(alpha = barAlpha))
                )
            }
            if (dayEvents.size > 2) {
                // Small indicator dot at the center for overflow
                Box(
                    modifier = Modifier
                        .size(screenHeight * 0.004f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = barAlpha))
                )
            }
        }

        if (showSonEopNeunMark) {
            val infiniteTransition = rememberInfiniteTransition(label = "blink")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            val badgeAlpha = if (day.isCurrentMonth) alpha else alpha * 0.3f

            Image(
                painter = painterResource(id = R.drawable.ic_moving_day),
                contentDescription = "이삿날",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF9C27B0)),
                modifier = Modifier
                    .size(minOf(screenWidth, 400.dp) * 0.065f)
                    .align(Alignment.TopEnd)
                    .offset(
                        x = minOf(screenWidth, 400.dp) * 0.005f,
                        y = minOf(screenHeight, 800.dp) * -0.004f
                    )
                    .alpha(badgeAlpha)
            )
        }

        // 마감 도장 오버레이 (투명 PNG 사용)
        if (isDeadline) {
            val stampAlpha = if (day.isCurrentMonth) 0.85f else 0.3f
            Image(
                painter = painterResource(id = R.drawable.ic_magam_stamp),
                contentDescription = "마감",
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = 0.6f,
                        scaleY = 0.6f
                    )
                    .alpha(stampAlpha)
            )
        }
    }
}

private fun getDaysFromBase(millis: Long): Int {
    val baseCal = Calendar.getInstance().apply {
        set(2000, 0, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val targetCal = Calendar.getInstance().apply {
        timeInMillis = millis
        clearTimeToZero()
    }
    val diffMillis = targetCal.timeInMillis - baseCal.timeInMillis
    return (diffMillis / (24 * 60 * 60 * 1000L)).toInt()
}

private fun getCalendarFromDays(days: Int): Calendar {
    val baseCal = Calendar.getInstance().apply {
        set(2000, 0, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    baseCal.add(Calendar.DAY_OF_YEAR, days)
    return baseCal
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventListSection(
    selectedDate: Long,
    events: List<Event>,
    categories: List<CalendarCategory>,
    onEventClick: (Event) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onToggleComplete: (Event) -> Unit,
    onUpdateEvent: (Event) -> Unit,
    isDeadlineSet: Boolean = false,
    onDeadlineToggle: (Long) -> Unit = {},
    viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    onSwipeDownAtTop: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onDateSelected: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val dateFormat = SimpleDateFormat("d일 EEEE", Locale.KOREAN)
    val lunarStr = getKoreanLunarDateString(selectedDate)
    val dateHeaderStr = "${dateFormat.format(Date(selectedDate))} ($lunarStr)"

    val lazyListState = rememberLazyListState()
    val nestedScrollConnection = remember(lazyListState, viewMode) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0.5f && viewMode == CalendarViewMode.WEEK) {
                    val isAtTop = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
                    if (isAtTop) {
                        onSwipeDownAtTop()
                        return Offset(0f, available.y)
                    }
                }
                if (available.y < -0.5f && viewMode == CalendarViewMode.MONTH) {
                    onSwipeUp()
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = getDaysFromBase(selectedDate)
    ) { 100000 }

    // Sync selectedDate (from outside) -> pagerState (inside)
    LaunchedEffect(selectedDate) {
        val targetPage = getDaysFromBase(selectedDate)
        if (pagerState.currentPage != targetPage) {
            val diff = kotlin.math.abs(pagerState.currentPage - targetPage)
            if (diff <= 2) {
                pagerState.animateScrollToPage(targetPage)
            } else {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    // Sync pagerState (inside) -> selectedDate (outside)
    LaunchedEffect(pagerState.settledPage) {
        val targetCal = getCalendarFromDays(pagerState.settledPage)
        if (getDaysFromBase(selectedDate) != pagerState.settledPage) {
            onDateSelected(targetCal.timeInMillis)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(nestedScrollConnection)
    ) {
        // Date Header Row with Deadline button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = minOf(screenWidth, 400.dp) * 0.04f,
                    vertical = screenHeight * 0.01f
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dateHeaderStr,
                fontSize = (minOf(screenWidth, 400.dp).value * 0.046f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(minOf(screenWidth, 400.dp) * 0.02f))
            if (isDeadlineSet) {
                Button(
                    onClick = { onDeadlineToggle(selectedDate) },
                    contentPadding = PaddingValues(horizontal = minOf(screenWidth, 400.dp) * 0.015f, vertical = 0.dp),
                    shape = RoundedCornerShape(minOf(screenWidth, 400.dp) * 0.015f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp).height(screenHeight * 0.032f)
                ) {
                    Text(
                        text = "마감✓",
                        fontSize = (minOf(screenWidth, 400.dp).value * 0.03f).sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onDeadlineToggle(selectedDate) },
                    contentPadding = PaddingValues(horizontal = minOf(screenWidth, 400.dp) * 0.015f, vertical = 0.dp),
                    shape = RoundedCornerShape(minOf(screenWidth, 400.dp) * 0.015f),
                    border = androidx.compose.foundation.BorderStroke(
                        minOf(screenWidth, 400.dp) * 0.0025f,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp).height(screenHeight * 0.032f)
                ) {
                    Text(
                        text = "마감",
                        fontSize = (minOf(screenWidth, 400.dp).value * 0.03f).sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val pageCal = getCalendarFromDays(page)
            val pageDateMillis = pageCal.timeInMillis

            val dayEvents = remember(events, pageDateMillis) {
                val pageStart = pageDateMillis
                val pageEnd = pageDateMillis + 24 * 60 * 60 * 1000L - 1
                events.filter { event ->
                    event.startMillis <= pageEnd && event.endMillis >= pageStart
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (dayEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(viewMode) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    if (dragAmount > 0.5f && viewMode == CalendarViewMode.WEEK) {
                                        change.consume()
                                        onSwipeDownAtTop()
                                    } else if (dragAmount < -0.5f && viewMode == CalendarViewMode.MONTH) {
                                        change.consume()
                                        onSwipeUp()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(minOf(screenWidth, 400.dp) * 0.16f)
                            )
                            Spacer(modifier = Modifier.height(screenHeight * 0.015f))
                            Text(
                                text = "일정이 없습니다",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = (minOf(screenWidth, 400.dp).value * 0.042f).sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = if (page == getDaysFromBase(selectedDate)) lazyListState else rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = minOf(screenWidth, 400.dp) * 0.04f,
                            end = minOf(screenWidth, 400.dp) * 0.04f,
                            top = screenHeight * 0.01f,
                            bottom = screenHeight * 0.3f
                        ),
                        verticalArrangement = Arrangement.spacedBy(screenHeight * 0.012f)
                    ) {
                        items(dayEvents) { event ->
                            val category = categories.find { it.id == event.calendarId }
                            EventItemCard(
                                event = event,
                                category = category,
                                onClick = { onEventClick(event) },
                                onDelete = { onDeleteEvent(event) },
                                onToggleComplete = { onToggleComplete(event) },
                                onUpdate = onUpdateEvent
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventItemCard(
    event: Event,
    category: CalendarCategory?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit,
    onUpdate: (Event) -> Unit = {}
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val colorHex = event.colorHex ?: category?.colorHex ?: "#1c62f2"
    val catColor = Color(android.graphics.Color.parseColor(colorHex))

    // 24시간제 포맷: HH:mm (예: 13:30)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREAN)
    val startStr = timeFormat.format(Date(event.startMillis))

    // 하루종일 일정 여부에 따른 카드 스타일 결정 (배경색을 부드럽게 연하게 변경)
    val cardBgColor = if (event.isAllDay) catColor.copy(alpha = 0.15f) else Color.Transparent
    
    // 완료 여부에 따라 내용 색상의 투명도 조절
    val contentColor = if (event.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
    val subContentColor = if (event.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant

    var showContextMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth
        var cardHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    cardHeight = with(density) { coordinates.size.height.toDp() }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBgColor
            ),
            border = null,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = minOf(screenWidth, 400.dp) * 0.025f, 
                        end = minOf(screenWidth, 400.dp) * 0.015f, 
                        top = if (event.isAllDay) (screenHeight * 0.026f) else (screenHeight * 0.012f), 
                        bottom = if (event.isAllDay) (screenHeight * 0.026f) else (screenHeight * 0.012f)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!event.isAllDay) {
                    // 시간 설정 일정의 경우: 제일 좌측에 24시간제 시작 시간 표시
                    Box(
                        modifier = Modifier.width(minOf(screenWidth, 400.dp) * 0.15f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = startStr,
                            fontSize = (minOf(screenWidth, 400.dp).value * 0.038f).sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(minOf(screenWidth, 400.dp) * 0.01f))
                    
                    // Category Color Bar indicator
                    Box(
                        modifier = Modifier
                            .width(minOf(screenWidth, 400.dp) * 0.01f)
                            .height(screenHeight * 0.035f)
                            .clip(RoundedCornerShape(1.dp))
                            .background(catColor.copy(alpha = if (event.isCompleted) 0.5f else 1f))
                    )
                    Spacer(modifier = Modifier.width(minOf(screenWidth, 400.dp) * 0.025f))
                }

                // 일정 제목 Column (우측 남은 가로 공간을 채움)
                Column(modifier = Modifier.weight(1f)) {
                    val titleFirstLine = event.title.lineSequence().firstOrNull()?.trim() ?: ""
                    val startLocation = event.location.split("|||").getOrNull(0)?.trim() ?: ""
                    val displayText = if (startLocation.isNotEmpty()) {
                        "$titleFirstLine $startLocation"
                    } else {
                        titleFirstLine
                    }
                    Text(
                        text = displayText,
                        fontSize = (minOf(screenWidth, 400.dp).value * 0.04f).sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(
                            textDecoration = if (event.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                        )
                    )
                }

                // 견적서 연결 아이콘
                if (!event.linkedEstimateId.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(minOf(screenWidth, 400.dp) * 0.015f))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFBEAF0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "견적서 연결됨",
                            tint = Color(0xFF993556),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(minOf(screenWidth, 400.dp) * 0.02f))

                // 완료 토글 버튼
                if (event.isCompleted) {
                    OutlinedButton(
                        onClick = onToggleComplete,
                        contentPadding = PaddingValues(horizontal = minOf(screenWidth, 400.dp) * 0.015f, vertical = 0.dp),
                        shape = RoundedCornerShape(minOf(screenWidth, 400.dp) * 0.012f),
                        border = androidx.compose.foundation.BorderStroke(minOf(screenWidth, 400.dp) * 0.0025f, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.height(screenHeight * 0.032f).defaultMinSize(minWidth = 1.dp)
                    ) {
                        Text(
                            text = "완료",
                            fontSize = (minOf(screenWidth, 400.dp).value * 0.03f).sp,
                            fontWeight = FontWeight.Medium,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                } else {
                    Button(
                        onClick = onToggleComplete,
                        contentPadding = PaddingValues(horizontal = minOf(screenWidth, 400.dp) * 0.015f, vertical = 0.dp),
                        shape = RoundedCornerShape(minOf(screenWidth, 400.dp) * 0.012f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(screenHeight * 0.032f).defaultMinSize(minWidth = 1.dp)
                    ) {
                        Text(
                            text = "완료",
                            fontSize = (minOf(screenWidth, 400.dp).value * 0.03f).sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                }
            }
        }

        if (showContextMenu) {
            val popupOffset = with(density) {
                // bubble height (40.dp) + gap (8.dp) = 48.dp
                -48.dp.roundToPx()
            }

            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { showContextMenu = false },
                offset = IntOffset(0, popupOffset),
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier.width(cardWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 상단 말풍선 버튼 2개
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            BubbleButton(
                                text = "방문 💬",
                                onClick = {
                                    showContextMenu = false
                                    onUpdate(event.copy(isAllDay = false))
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            BubbleButton(
                                text = "계약 💬",
                                onClick = {
                                    showContextMenu = false
                                    onUpdate(event.copy(isAllDay = true))
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // 카드 높이 만큼의 공간 확보
                    Spacer(modifier = Modifier.height(cardHeight + 8.dp))

                    // 하단 삭제 버튼 (방문 버튼과 동일한 X축 위치)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onDelete()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "삭제",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("삭제", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

class SpeechBubbleShape(
    private val cornerRadius: Dp = 12.dp,
    private val arrowWidth: Dp = 12.dp,
    private val arrowHeight: Dp = 8.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val r = with(density) { cornerRadius.toPx() }
            val arrowW = with(density) { arrowWidth.toPx() }
            val arrowH = with(density) { arrowHeight.toPx() }
            
            val rectHeight = size.height - arrowH
            
            // Top-left round corner
            moveTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            
            // Top edge to top-right corner
            lineTo(size.width - r, 0f)
            quadraticTo(size.width, 0f, size.width, r)
            
            // Right edge to bottom-right corner
            lineTo(size.width, rectHeight - r)
            quadraticTo(size.width, rectHeight, size.width - r, rectHeight)
            
            // Bottom edge from right corner to arrow start
            val arrowStart = size.width / 2f + arrowW / 2f
            val arrowEnd = size.width / 2f - arrowW / 2f
            val arrowPeak = size.width / 2f
            
            lineTo(arrowStart, rectHeight)
            // Arrow tail to the peak
            lineTo(arrowPeak, size.height)
            // Arrow tail back to bottom edge
            lineTo(arrowEnd, rectHeight)
            
            // Bottom edge to bottom-left corner
            lineTo(r, rectHeight)
            quadraticTo(0f, rectHeight, 0f, rectHeight - r)
            
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun BubbleButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .shadow(elevation = 6.dp, shape = SpeechBubbleShape())
            .background(color = containerColor, shape = SpeechBubbleShape())
            .clickable { onClick() }
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + 8.dp), // extra 8.dp for arrow tail
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Helpers
fun getGridDays(monthCal: Calendar): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val cal = monthCal.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.clearTimeToZero()

    // Back up to Sunday of the first week
    val startDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DAY_OF_YEAR, -(startDayOfWeek - 1))

    val today = Calendar.getInstance().apply { clearTimeToZero() }
    val currentMonthVal = monthCal.get(Calendar.MONTH)
    val currentYearVal = monthCal.get(Calendar.YEAR)

    // Calculate how many days we need: 35 (5 weeks) or 42 (6 weeks)
    val firstDayOfMonth = monthCal.clone() as Calendar
    firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
    val startDayOffset = firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - 1 // 0 for Sunday, 6 for Saturday
    val maxDaysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val neededDays = if (startDayOffset + maxDaysInMonth <= 35) 35 else 42

    for (i in 0 until neededDays) {
        val isCurrentMonth = cal.get(Calendar.MONTH) == currentMonthVal && cal.get(Calendar.YEAR) == currentYearVal
        val isToday = cal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH) &&
                cal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)

        days.add(
            CalendarDay(
                dateInMillis = cal.timeInMillis,
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = isCurrentMonth,
                isToday = isToday,
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            )
        )
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return days
}

fun getWeekDays(selectedDateMillis: Long): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val cal = Calendar.getInstance().apply {
        timeInMillis = selectedDateMillis
        clearTimeToZero()
    }

    // Back up to Sunday of the current week
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DAY_OF_YEAR, -(dayOfWeek - 1))

    val today = Calendar.getInstance().apply { clearTimeToZero() }
    val currentMonthVal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }.get(Calendar.MONTH)
    val currentYearVal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }.get(Calendar.YEAR)

    for (i in 0 until 7) {
        val isCurrentMonth = cal.get(Calendar.MONTH) == currentMonthVal && cal.get(Calendar.YEAR) == currentYearVal
        val isToday = cal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH) &&
                cal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)

        days.add(
            CalendarDay(
                dateInMillis = cal.timeInMillis,
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = isCurrentMonth,
                isToday = isToday,
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            )
        )
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return days
}

fun getDayEvents(dateMillis: Long, events: List<Event>): List<Event> {
    val dayCal = Calendar.getInstance().apply {
        timeInMillis = dateMillis
        clearTimeToZero()
    }
    val start = dayCal.timeInMillis
    dayCal.add(Calendar.DAY_OF_YEAR, 1)
    val end = dayCal.timeInMillis

    return events.filter { event ->
        (event.startMillis >= start && event.startMillis < end) ||
                (event.endMillis >= start && event.endMillis < end) ||
                (event.startMillis <= start && event.endMillis >= end)
    }
}

fun isSameDay(millis1: Long, millis2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun Calendar.clearTimeToZero() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

fun getKoreanHolidayName(dateInMillis: Long): String? {
    val cal = Calendar.getInstance().apply { timeInMillis = dateInMillis }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1 // 1-indexed for convenience
    val day = cal.get(Calendar.DAY_OF_MONTH)
    
    // Solar fixed holidays
    when {
        month == 1 && day == 1 -> return "신정"
        month == 3 && day == 1 -> return "삼일절"
        month == 5 && day == 5 -> return "어린이날"
        month == 6 && day == 6 -> return "현충일"
        month == 8 && day == 15 -> return "광복절"
        month == 10 && day == 3 -> return "개천절"
        month == 10 && day == 9 -> return "한글날"
        month == 12 && day == 25 -> return "성탄절"
    }
    
    // Year-specific lunar/alt holidays
    when (year) {
        2025 -> {
            if (month == 1 && day == 28) return "설날 연휴"
            if (month == 1 && day == 29) return "설날"
            if (month == 1 && day == 30) return "설날 연휴"
            if (month == 1 && day == 31) return "대체공휴일(설날)"
            
            if (month == 5 && day == 6) return "대체공휴일(부처님오신날)"
            
            if (month == 10 && day == 5) return "추석 연휴"
            if (month == 10 && day == 6) return "추석"
            if (month == 10 && day == 7) return "추석 연휴"
            if (month == 10 && day == 8) return "대체공휴일(추석)"
        }
        2026 -> {
            if (month == 2 && day == 16) return "설날 연휴"
            if (month == 2 && day == 17) return "설날"
            if (month == 2 && day == 18) return "설날 연휴"
            if (month == 3 && day == 2) return "대체공휴일(삼일절)"
            
            if (month == 5 && day == 24) return "부처님오신날"
            if (month == 5 && day == 25) return "대체공휴일(부처님오신날)"
            
            if (month == 8 && day == 17) return "대체공휴일(광복절)"
            
            if (month == 9 && day == 24) return "추석 연휴"
            if (month == 9 && day == 25) return "추석"
            if (month == 9 && day == 26) return "추석 연휴"
            if (month == 9 && day == 28) return "대체공휴일(추석)"
            
            if (month == 10 && day == 5) return "대체공휴일(개천절)"
        }
        2027 -> {
            if (month == 2 && day == 6) return "설날 연휴"
            if (month == 2 && day == 7) return "설날"
            if (month == 2 && day == 8) return "설날 연휴"
            if (month == 2 && day == 9) return "대체공휴일(설날)"
            
            if (month == 5 && day == 13) return "부처님오신날"
            
            if (month == 8 && day == 16) return "대체공휴일(광복절)"
            
            if (month == 9 && day == 14) return "추석 연휴"
            if (month == 9 && day == 15) return "추석"
            if (month == 9 && day == 16) return "추석 연휴"
            
            if (month == 10 && day == 4) return "대체공휴일(개천절)"
            if (month == 10 && day == 11) return "대체공휴일(한글날)"
            if (month == 12 && day == 27) return "대체공휴일(성탄절)"
        }
        2028 -> {
            if (month == 1 && day == 26) return "설날 연휴"
            if (month == 1 && day == 27) return "설날"
            if (month == 1 && day == 28) return "설날 연휴"
            if (month == 5 && day == 2) return "부처님오신날"
            if (month == 10 && day == 2) return "추석 연휴"
            if (month == 10 && day == 3) return "추석/개천절"
            if (month == 10 && day == 4) return "추석 연휴"
            if (month == 10 && day == 5) return "대체공휴일"
        }
    }
    
    return null
}

fun getKoreanLunarDateString(timeInMillis: Long): String {
    val cc = android.icu.util.ChineseCalendar()
    cc.timeInMillis = timeInMillis
    val month = cc.get(android.icu.util.ChineseCalendar.MONTH) + 1
    val day = cc.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH)
    val isLeap = cc.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1
    return "음력 ${if (isLeap) "윤" else ""}${month}월 ${day}일"
}

fun isSonEopNeunDay(timeInMillis: Long): Boolean {
    val cc = android.icu.util.ChineseCalendar()
    cc.timeInMillis = timeInMillis
    val lunarDay = cc.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH)
    return lunarDay == 9 || lunarDay == 10 || lunarDay == 19 || lunarDay == 20 || lunarDay == 29 || lunarDay == 30
}

