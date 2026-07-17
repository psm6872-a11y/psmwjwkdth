package com.example.danallacalendar.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.danallacalendar.estimate.LocalEstimateViewerDialog
import com.example.danallacalendar.estimate.Estimate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.content.Context

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val VISIT_MESSAGE_TEMPLATE_KEY = stringPreferencesKey("visit_message_template")
val DEFAULT_VISIT_MESSAGE_TEMPLATE = """
[방문예약]
다날라 익스프레스
{시작시간}
방문 예정입니다.
30분~1시간 전 미리 전화 드리고 방문 하겠습니다.
감사합니다.
""".trimIndent()

val CONTRACT_MESSAGE_TEMPLATE_KEY = stringPreferencesKey("contract_message_template")
val DEFAULT_CONTRACT_MESSAGE_TEMPLATE = """
[계약확정]
다날라 익스프레스
{이사날짜} {시작시간}
이사 계약이 확정되었습니다.
이사전날 확인전화 드립니다.
감사합니다.
""".trimIndent()


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
    onNavigateToTrash: () -> Unit,
    onNavigateToBlacklist: () -> Unit,
    onNavigateToStatistics: (Boolean) -> Unit,
    onNavigateToSuggestions: () -> Unit,
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
    var isContextMenuOpen by remember { mutableStateOf(false) }

    val teamCountFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            preferences[TEAM_COUNT_KEY] ?: 2
        }
    }
    val slotCount by teamCountFlow.collectAsState(initial = 2)

    val teamConfigsFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            TeamConfigs.map { config ->
                val name = preferences[config.nameKey] ?: config.defaultName
                val color = preferences[config.colorKey] ?: config.defaultColor
                name to color
            }
        }
    }
    val teamPrefsList by teamConfigsFlow.collectAsState(
        initial = TeamConfigs.map { it.defaultName to it.defaultColor }
    )

    val visitSlotCountFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            preferences[VISIT_SLOT_COUNT_KEY] ?: 3
        }
    }
    val visitSlotCount by visitSlotCountFlow.collectAsState(initial = 3)

    val visitColorsFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            Triple(
                preferences[VISIT_COLOR_DEFAULT_KEY] ?: 0xFF9E9E9EL,
                preferences[VISIT_COLOR_ACTIVE_KEY] ?: 0xFF29B6F6L,
                preferences[VISIT_COLOR_DONE_KEY] ?: 0xFFFF9800L
            )
        }
    }
    val visitColors by visitColorsFlow.collectAsState(
        initial = Triple(0xFF9E9E9EL, 0xFF29B6F6L, 0xFFFF9800L)
    )

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
    val pendingChangeNotification by viewModel.pendingChangeNotification.collectAsStateWithLifecycle()
    val showChangeNotificationDialog by viewModel.showChangeNotificationDialog.collectAsStateWithLifecycle()

    val isCheckingForUpdate by viewModel.isCheckingForUpdate.collectAsStateWithLifecycle()
    val isUpdateDownloaded by viewModel.isUpdateDownloaded.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isUpdateDownloaded) {
        if (isUpdateDownloaded) {
            val result = snackbarHostState.showSnackbar(
                message = "업데이트가 준비되었습니다. 재시작하시겠습니까?",
                actionLabel = "재시작",
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.completeUpdate()
            }
        }
    }

    var isMonthViewExpanded by remember { mutableStateOf(false) }
    var showDayEventsDialogDate by remember { mutableStateOf<Long?>(null) }
    var autoInputToastMessage by remember { mutableStateOf<String?>(null) }

    val dismissedContractSyncIds by viewModel.dismissedContractSyncIds.collectAsStateWithLifecycle()
    val activeRecentContracts by viewModel.activeRecentContracts.collectAsStateWithLifecycle()
    var expandedBannerId by remember { mutableStateOf<Int?>(null) }
    var showEstimateDetailDialog by remember { mutableStateOf(false) }
    var selectedEstimateForDetail by remember { mutableStateOf<com.example.danallacalendar.estimate.Estimate?>(null) }


    LaunchedEffect(autoInputToastMessage) {
        if (autoInputToastMessage != null) {
            kotlinx.coroutines.delay(2000L)
            autoInputToastMessage = null
        }
    }

    LaunchedEffect(viewMode) {
        if (viewMode == CalendarViewMode.WEEK) {
            isMonthViewExpanded = false
        }
    }

    // 마감 날짜 Set - DB에서 영구 저장
    val deadlineDates by viewModel.deadlineDates.collectAsStateWithLifecycle()

    var showConflictConfirmDialog by remember { mutableStateOf(false) }
    var conflictMessage by remember { mutableStateOf("") }
    var pendingNewEvent by remember { mutableStateOf<com.example.danallacalendar.data.Event?>(null) }

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
            modifier = Modifier
                .fillMaxSize()
                .blur(if (isContextMenuOpen) 8.dp else 0.dp),
            drawerState = drawerState,
            gesturesEnabled = !drawerState.isClosed && !isContextMenuOpen,
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
                    onEstimateClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToEstimate()
                    },
                    onEstimateListClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToEstimateList()
                    },
                    onStatisticsClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToStatistics(isCreator)
                    },
                    onSuggestionsClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSuggestions()
                    },
                    onCloseClick = {
                        scope.launch { drawerState.close() }
                    },
                    onBackupClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToBackup()
                    },
                    onBlacklistClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToBlacklist()
                    },
                    onTrashClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToTrash()
                    },
                    isCheckingForUpdate = isCheckingForUpdate,
                    onCheckForUpdateClick = {
                        var act: android.app.Activity? = null
                        var ctx = context
                        while (ctx is android.content.ContextWrapper) {
                            if (ctx is android.app.Activity) {
                                act = ctx
                                break
                            }
                            ctx = ctx.baseContext
                        }
                        if (act != null) {
                            viewModel.checkForUpdates(act)
                        } else {
                            android.widget.Toast.makeText(context, "Activity를 찾을 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                MainTopAppBar(
                    currentMonth = currentMonth,
                    viewMode = viewMode,
                    selectedFilter = eventFilter,
                    onFilterSelected = { viewModel.setEventFilter(it) },
                    onToggleDrawer = { scope.launch { drawerState.open() } },
                    onToggleViewMode = { viewModel.toggleViewMode() },
                    onNavigateToSearch = { onNavigateToSearch() },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Calendar Grid Component
                Box(
                    modifier = if (isMonthViewExpanded) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    CalendarGridSection(
                        selectedDate = selectedDate,
                        currentMonth = currentMonth,
                        viewMode = viewMode,
                        monthlyEvents = monthlyEvents,
                        categories = categories,
                        deadlineDates = deadlineDates,
                        slotCount = slotCount,
                        teamPrefsList = teamPrefsList,
                        eventFilter = eventFilter,
                        visitSlotCount = visitSlotCount,
                        visitColors = visitColors,
                        onDaySelected = { dateMillis ->
                            viewModel.selectDate(dateMillis)
                            if (isMonthViewExpanded) {
                                showDayEventsDialogDate = dateMillis
                            }
                        },
                        onMonthChanged = { viewModel.selectDate(it.timeInMillis) },
                        onWeekSelected = { viewModel.selectDate(it) },
                        onCollapseToggle = { viewModel.toggleViewMode() },
                        isMonthViewExpanded = isMonthViewExpanded,
                        onMonthViewExpandedChanged = { isMonthViewExpanded = it }
                    )
                }

                if (!isMonthViewExpanded) {
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    val localScreenHeight = config.screenHeightDp.dp
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(localScreenHeight * 0.03f)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    EventListSection(
                    selectedDate = selectedDate,
                    events = monthlyEvents,
                    categories = categories,
                    onEventClick = { onNavigateToAddEditEvent(it.id) },
                    onDeleteEvent = { viewModel.deleteEvent(it) },
                    onToggleComplete = { viewModel.updateEvent(it.copy(isCompleted = !it.isCompleted)) },
                    onUpdateEvent = { viewModel.updateEvent(it) },
                    onConfirmContract = { evt, teamId, slotPos ->
                        val estimateId = evt.linkedEstimateId
                        if (!estimateId.isNullOrBlank()) {
                            scope.launch {
                                try {
                                    val estimate = viewModel.getEstimateById(estimateId)
                                    if (estimate != null) {
                                        val sharedCategoryId = viewModel.getOrCreateSharedCategory()
                                        
                                        // date 파싱 (yyyy-MM-dd)
                                        val dateCal = Calendar.getInstance()
                                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
                                        try {
                                            val parsedDate = dateFormat.parse(estimate.moveDate)
                                            if (parsedDate != null) {
                                                dateCal.time = parsedDate
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("CalendarMainScreen", "Error parsing moveDate: ${estimate.moveDate}", e)
                                        }
                                        
                                        dateCal.set(Calendar.HOUR_OF_DAY, 0)
                                        dateCal.set(Calendar.MINUTE, 0)
                                        dateCal.set(Calendar.SECOND, 0)
                                        dateCal.set(Calendar.MILLISECOND, 0)
                                        val startMillis = dateCal.timeInMillis
                                        
                                        dateCal.set(Calendar.HOUR_OF_DAY, 23)
                                        dateCal.set(Calendar.MINUTE, 59)
                                        dateCal.set(Calendar.SECOND, 59)
                                        dateCal.set(Calendar.MILLISECOND, 999)
                                        val endMillis = dateCal.timeInMillis
                                        
                                        // 선택한 팀 이름 및 색상
                                        val teamPref = teamPrefsList.getOrNull(teamId - 1) ?: (TeamConfigs.getOrNull(teamId - 1)?.let { it.defaultName to it.defaultColor } ?: ("" to 0xFF4CAF50L))
                                        val teamName = teamPref.first
                                        val teamColorLong = teamPref.second
                                        
                                        val resolvedVolume = if (estimate.totalVolume.isNotBlank()) {
                                            if (estimate.totalVolume.contains("톤")) estimate.totalVolume else "${estimate.totalVolume}톤"
                                        } else {
                                            "-"
                                        }

                                        val resolvedStartTime = if (estimate.startTime.isNotBlank()) {
                                            estimate.startTime
                                        } else {
                                            "-"
                                        }

                                        val departureFirstWord = run {
                                            val mainPart = estimate.departure.split("|").firstOrNull() ?: ""
                                            val firstWord = mainPart.trim().split(" ").firstOrNull() ?: ""
                                            if (firstWord.isNotBlank()) firstWord else "-"
                                        }

                                        val destinationFirstWord = run {
                                            val mainPart = estimate.destination.split("|").firstOrNull() ?: ""
                                            val firstWord = mainPart.trim().split(" ").firstOrNull() ?: ""
                                            if (firstWord.isNotBlank()) firstWord else "-"
                                        }

                                        val departureFloor = if (estimate.departureFloorType.isNotBlank()) estimate.departureFloorType else "-"
                                        val destinationFloor = if (estimate.destinationFloorType.isNotBlank()) estimate.destinationFloorType else "-"

                                        val titleText = "$teamName. $resolvedStartTime. $resolvedVolume\n$departureFirstWord. $destinationFirstWord. $departureFloor/$destinationFloor"
                                        
                                        // 출발지/도착지 및 동호수 처리 -> ||| 로 결합
                                        val departureAddr: String
                                        val departureDetail: String
                                        if (estimate.departure.contains("|")) {
                                            val parts = estimate.departure.split("|")
                                            departureAddr = parts.getOrNull(0) ?: ""
                                            departureDetail = parts.getOrNull(1) ?: ""
                                        } else {
                                            departureAddr = estimate.departure
                                            departureDetail = ""
                                        }
                                        
                                        val destinationAddr: String
                                        val destinationDetail: String
                                        if (estimate.destination.contains("|")) {
                                            val parts = estimate.destination.split("|")
                                            destinationAddr = parts.getOrNull(0) ?: ""
                                            destinationDetail = parts.getOrNull(1) ?: ""
                                        } else {
                                            destinationAddr = estimate.destination
                                            destinationDetail = ""
                                        }
                                        
                                        val locationField = "$departureAddr|||$departureDetail|||$destinationAddr|||$destinationDetail"
                                        
                                        val notesField = estimate.phoneNumber
                                        val colorHexField = when (teamId) {
                                            1 -> "#FF4CAF50"
                                            2 -> "#FFFFEB3B"
                                            else -> String.format("#%08X", teamColorLong)
                                        }
                                        
                                        val syncId = java.util.UUID.randomUUID().toString()
                                        val newEvent = com.example.danallacalendar.data.Event(
                                            title = titleText,
                                            startMillis = startMillis,
                                            endMillis = endMillis,
                                            isAllDay = true,
                                            location = locationField,
                                            notes = notesField,
                                            colorHex = colorHexField,
                                            calendarId = sharedCategoryId,
                                            syncId = syncId,
                                            isSynced = true,
                                            linkedEstimateId = estimateId,
                                            teamId = teamId,
                                            slotPosition = slotPos,
                                            createdAt = System.currentTimeMillis(),
                                            updatedAt = System.currentTimeMillis(),
                                            createdBy = userName,
                                            updatedBy = userName
                                        )

                                        // 중복 일정 체크
                                        val conflicts = viewModel.checkContractConflict(
                                            dateStr = estimate.moveDate,
                                            teamId = teamId,
                                            slotPos = slotPos
                                        )

                                        if (conflicts.isNotEmpty()) {
                                            val firstConf = conflicts.first()
                                            val confTitle = firstConf.title.split("\n").firstOrNull() ?: ""
                                            conflictMessage = "주의: 선택하신 날짜와 팀/시간대에 이미 확정된 일정이 있습니다.\n(기존 일정: $confTitle)\n\n그래도 중복으로 배정하시겠습니까?"
                                            pendingNewEvent = newEvent
                                            showConflictConfirmDialog = true
                                        } else {
                                            viewModel.dismissContract(syncId)
                                            viewModel.addEvent(newEvent)
                                            val formattedDate = try {
                                                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).parse(estimate.moveDate)
                                                if (parsed != null) {
                                                    SimpleDateFormat("M월 d일", Locale.KOREAN).format(parsed)
                                                } else {
                                                    estimate.moveDate
                                                }
                                            } catch (e: Exception) {
                                                estimate.moveDate
                                            }
                                            autoInputToastMessage = "${formattedDate}에 일정이 자동 입력되었습니다."
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("CalendarMainScreen", "Failed to auto-create move schedule", e)
                                }
                            }
                        }
                    },
                    isDeadlineSet = deadlineDates.any { isSameDay(it, selectedDate) },
                    onDeadlineToggle = { dateMillis ->
                        viewModel.toggleDeadlineDate(dateMillis)
                    },
                    viewMode = viewMode,
                    onSwipeDownAtTop = { viewModel.setViewMode(CalendarViewMode.MONTH) },
                    onSwipeUp = { viewModel.setViewMode(CalendarViewMode.WEEK) },
                    onDateSelected = { viewModel.selectDate(it) },
                    modifier = Modifier.weight(1f),
                    viewModel = viewModel,
                    isContextMenuOpen = isContextMenuOpen,
                    onShowContextMenuChanged = { isContextMenuOpen = it }
                )
                }
            }

            if (activeRecentContracts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeRecentContracts.forEach { evt ->
                        val isExpanded = expandedBannerId == evt.id
                        val contractBannerText = remember(evt, teamPrefsList) {
                            val cal = Calendar.getInstance().apply { timeInMillis = evt.startMillis }
                            val m = cal.get(Calendar.MONTH) + 1
                            val d = cal.get(Calendar.DAY_OF_MONTH)
                            val teamName = teamPrefsList.getOrNull((evt.teamId ?: 1) - 1)?.first ?: "1팀"
                            "${m}월 ${d}일 ${teamName} 계약확정"
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // The Banner Button
                            Card(
                                onClick = {
                                    expandedBannerId = if (isExpanded) null else evt.id
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFEF5350).copy(alpha = 0.95f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = contractBannerText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // The Action Bubble / Pop-up
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .wrapContentWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left button: 견적서보기
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    val est = viewModel.getEstimateById(evt.linkedEstimateId!!)
                                                    if (est != null) {
                                                        selectedEstimateForDetail = est
                                                        showEstimateDetailDialog = true
                                                    } else {
                                                        Toast.makeText(context, "견적서 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Text("견적서보기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        // Divider
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(16.dp)
                                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                        )

                                        // Right button: 확인
                                        TextButton(
                                            onClick = {
                                                evt.syncId?.let { viewModel.dismissContract(it) }
                                                if (expandedBannerId == evt.id) {
                                                    expandedBannerId = null
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Text("확인", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            autoInputToastMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF323232).copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                        .wrapContentSize()
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        }

        if (isContextMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            isContextMenuOpen = false
                        }
                    }
            )
        }
    }



        if (showConflictConfirmDialog) {
            AlertDialog(
                onDismissRequest = {
                    showConflictConfirmDialog = false
                    pendingNewEvent = null
                },
                title = { Text("일정 중복 경고", fontWeight = FontWeight.Bold) },
                text = { Text(conflictMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingNewEvent?.let {
                                it.syncId?.let { syncId -> viewModel.dismissContract(syncId) }
                                viewModel.addEvent(it)
                                val formattedDate = try {
                                    SimpleDateFormat("M월 d일", Locale.KOREAN).format(Date(it.startMillis))
                                } catch (e: Exception) {
                                    ""
                                }
                                autoInputToastMessage = "${formattedDate}에 일정이 자동 입력되었습니다."
                            }
                            showConflictConfirmDialog = false
                            pendingNewEvent = null
                        }
                    ) {
                        Text("확인", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showConflictConfirmDialog = false
                            pendingNewEvent = null
                        }
                    ) {
                        Text("취소")
                    }
                }
            )
        }

        if (showDayEventsDialogDate != null) {
            DayEventsDialog(
                dateMillis = showDayEventsDialogDate!!,
                events = monthlyEvents,
                categories = categories,
                onDismissRequest = { showDayEventsDialogDate = null },
                onEventClick = { event ->
                    showDayEventsDialogDate = null
                    onNavigateToAddEditEvent(event.id)
                },
                onDeleteEvent = { viewModel.deleteEvent(it) },
                onToggleComplete = { viewModel.updateEvent(it.copy(isCompleted = !it.isCompleted)) },
                onUpdateEvent = { viewModel.updateEvent(it) },
                viewModel = viewModel
            )
        }

        if (showEstimateDetailDialog && selectedEstimateForDetail != null) {
            LocalEstimateViewerDialog(
                estimate = selectedEstimateForDetail!!,
                onDismiss = { showEstimateDetailDialog = false },
                onEditClick = null
            )
        }

        if (showChangeNotificationDialog && pendingChangeNotification != null) {
            val pair = pendingChangeNotification!!
            AlertDialog(
                onDismissRequest = {
                    viewModel.setShowChangeNotificationDialog(false)
                    viewModel.clearPendingChangeNotification()
                    viewModel.clearHighlightedEventSyncId()
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = pair.first,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Text(
                        text = pair.second,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setShowChangeNotificationDialog(false)
                            viewModel.clearPendingChangeNotification()
                            viewModel.clearHighlightedEventSyncId()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("확인", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            )
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
    slotCount: Int,
    teamPrefsList: List<Pair<String, Long>>,
    eventFilter: EventFilter,
    visitSlotCount: Int,
    visitColors: Triple<Long, Long, Long>,
    onDaySelected: (Long) -> Unit,
    onMonthChanged: (Calendar) -> Unit,
    onWeekSelected: (Long) -> Unit,
    onCollapseToggle: () -> Unit,
    isMonthViewExpanded: Boolean,
    onMonthViewExpandedChanged: (Boolean) -> Unit
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
            .let { if (isMonthViewExpanded) it.fillMaxHeight() else it }
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                start = minOf(screenWidth, 400.dp) * 0.025f,
                end = minOf(screenWidth, 400.dp) * 0.025f,
                bottom = screenHeight * 0.01f
            )
            .pointerInput(viewMode, isMonthViewExpanded) {
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -50f) {
                            if (isMonthViewExpanded) {
                                onMonthViewExpandedChanged(false)
                            } else if (viewMode == CalendarViewMode.MONTH) {
                                onCollapseToggle()
                            }
                        } else if (totalDragY > 50f) {
                            if (viewMode == CalendarViewMode.MONTH && !isMonthViewExpanded) {
                                onMonthViewExpandedChanged(true)
                            } else if (viewMode == CalendarViewMode.WEEK) {
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
                .let { if (isMonthViewExpanded) it.fillMaxHeight() else it }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { if (isMonthViewExpanded) it.fillMaxHeight() else it }
                    ) { page ->
                        val pageMonthCal = getCalendarFromMonthPage(page)
                        val pageDays = getGridDays(pageMonthCal)
                        Column(
                            modifier = if (isMonthViewExpanded) Modifier.fillMaxHeight() else Modifier.wrapContentHeight()
                        ) {
                            val rowsCount = pageDays.size / 7
                            for (r in 0 until rowsCount) {
                                Row(
                                    modifier = if (isMonthViewExpanded) {
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    } else {
                                        Modifier
                                            .fillMaxWidth()
                                            .height(screenHeight * 0.068f)
                                    }
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
                                                slotCount = slotCount,
                                                teamPrefsList = teamPrefsList,
                                                eventFilter = eventFilter,
                                                visitSlotCount = visitSlotCount,
                                                visitColors = visitColors,
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
                                        slotCount = slotCount,
                                        teamPrefsList = teamPrefsList,
                                        eventFilter = eventFilter,
                                        visitSlotCount = visitSlotCount,
                                        visitColors = visitColors,
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
    slotCount: Int,
    teamPrefsList: List<Pair<String, Long>>,
    eventFilter: EventFilter,
    visitSlotCount: Int,
    visitColors: Triple<Long, Long, Long>,
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
                    bottom = 2.dp
                )
                .height(screenHeight * 0.012f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val barAlpha = if (day.isCurrentMonth) 1.0f else 0.35f

            if (eventFilter == EventFilter.ESTIMATE) {
                val visitEvents = dayEvents.filter { !it.isAllDay }
                val defaultColorVal = visitColors.first
                val activeColorVal = visitColors.second
                val doneColorVal = visitColors.third

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    repeat(visitSlotCount) { index ->
                        val topColorVal = if (index < visitEvents.size) {
                            if (visitEvents[index].isCompleted) doneColorVal else activeColorVal
                        } else {
                            defaultColorVal
                        }

                        val bottomIndex = index + visitSlotCount
                        val bottomColorVal = if (bottomIndex < visitEvents.size) {
                            if (visitEvents[bottomIndex].isCompleted) doneColorVal else activeColorVal
                        } else {
                            defaultColorVal
                        }

                        val topColor = Color(topColorVal).copy(alpha = barAlpha)
                        val bottomColor = Color(bottomColorVal).copy(alpha = barAlpha)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(topColor)
                            )
                            Spacer(modifier = Modifier.height(0.5.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(bottomColor)
                            )
                        }
                    }
                }
            } else {
                val confirmEvents = dayEvents.filter { it.isAllDay && it.teamId != null }
                val defaultGray = Color(0xFFE0E0E0).copy(alpha = barAlpha)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    repeat(slotCount) { index ->
                        val currentTeamId = index + 1
                        val teamEvents = confirmEvents.filter { it.teamId == currentTeamId }

                        val isBoth = teamEvents.any { it.slotPosition == "both" || it.slotPosition == null }
                        val hasTop = teamEvents.any { it.slotPosition == "top" || it.slotPosition == "both" || it.slotPosition == null }
                        val hasBottom = teamEvents.any { it.slotPosition == "bottom" || it.slotPosition == "both" || it.slotPosition == null }

                        val teamPref = teamPrefsList.getOrNull(index) ?: (TeamConfigs.getOrNull(index)?.let { it.defaultName to it.defaultColor } ?: ("" to 0xFF4CAF50L))
                        val teamColor = Color(teamPref.second).copy(alpha = barAlpha)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.dp))
                        ) {
                            if (isBoth) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(teamColor)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(if (hasTop) teamColor else defaultGray)
                                )
                                Spacer(modifier = Modifier.height(0.5.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(if (hasBottom) teamColor else defaultGray)
                                )
                            }
                        }
                    }
                }
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
    onConfirmContract: (Event, Int, String) -> Unit = { _, _, _ -> },
    isDeadlineSet: Boolean = false,
    onDeadlineToggle: (Long) -> Unit = {},
    viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    onSwipeDownAtTop: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onDateSelected: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel,
    isContextMenuOpen: Boolean = false,
    onShowContextMenuChanged: (Boolean) -> Unit = {}
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
                        items(dayEvents, key = { "${it.id}_${it.startMillis}" }) { event ->
                            val category = categories.find { it.id == event.calendarId }
                            EventItemCard(
                                event = event,
                                category = category,
                                onClick = { onEventClick(event) },
                                onDelete = { onDeleteEvent(event) },
                                onToggleComplete = { onToggleComplete(event) },
                                onUpdate = onUpdateEvent,
                                onConfirmContract = onConfirmContract,
                                viewModel = viewModel,
                                isContextMenuOpen = isContextMenuOpen,
                                onShowContextMenuChanged = onShowContextMenuChanged
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
    onUpdate: (Event) -> Unit = {},
    onConfirmContract: (Event, Int, String) -> Unit = { _, _, _ -> },
    viewModel: CalendarViewModel,
    isContextMenuOpen: Boolean = false,
    onShowContextMenuChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val highlightedSyncId by viewModel.highlightedEventSyncId.collectAsStateWithLifecycle()
    val isHighlighted = remember(highlightedSyncId, event.syncId) {
        !highlightedSyncId.isNullOrBlank() && event.syncId == highlightedSyncId
    }

    val teamCountFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            preferences[TEAM_COUNT_KEY] ?: 1
        }
    }
    val slotCount by teamCountFlow.collectAsState(initial = 1)

    val teamConfigsFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            TeamConfigs.map { config ->
                val name = preferences[config.nameKey] ?: config.defaultName
                val color = preferences[config.colorKey] ?: config.defaultColor
                name to color
            }
        }
    }
    val teamPrefsList by teamConfigsFlow.collectAsState(
        initial = TeamConfigs.map { it.defaultName to it.defaultColor }
    )

    var selectedTeamId by remember(event.teamId) { mutableStateOf(event.teamId) }
    var isAmSelected by remember(event.slotPosition) {
        mutableStateOf(event.slotPosition == "top" || event.slotPosition == "both" || event.slotPosition == null)
    }
    var isPmSelected by remember(event.slotPosition) {
        mutableStateOf(event.slotPosition == "bottom" || event.slotPosition == "both" || event.slotPosition == null)
    }

    var moveDateStr by remember { mutableStateOf("") }
    var existingEventsForMoveDate by remember { mutableStateOf<List<Event>>(emptyList()) }
    var isLoadingExistingEvents by remember { mutableStateOf(false) }

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
    var showSecondBubble by remember { mutableStateOf(false) }
    LaunchedEffect(showContextMenu) {
        onShowContextMenuChanged(showContextMenu)
    }
    LaunchedEffect(isContextMenuOpen) {
        if (!isContextMenuOpen) {
            showContextMenu = false
            showSecondBubble = false
        }
    }
    var showConfirmConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var visitButtonWidth by remember { mutableStateOf(90.dp) }
    var secondBubbleHeight by remember { mutableStateOf(54.dp) }
    var contractButtonHeight by remember { mutableStateOf(0.dp) }

    var showEditTemplateDialog by remember { mutableStateOf(false) }
    var templateText by remember { mutableStateOf("") }
    var editingTemplateType by remember { mutableStateOf("visit") }

    LaunchedEffect(showEditTemplateDialog) {
        if (showEditTemplateDialog) {
            val key = if (editingTemplateType == "contract") CONTRACT_MESSAGE_TEMPLATE_KEY else VISIT_MESSAGE_TEMPLATE_KEY
            val defaultTemplate = if (editingTemplateType == "contract") DEFAULT_CONTRACT_MESSAGE_TEMPLATE else DEFAULT_VISIT_MESSAGE_TEMPLATE
            val saved = context.dataStore.data.map { prefs ->
                prefs[key]
            }.first() ?: defaultTemplate
            templateText = saved
        }
    }

    LaunchedEffect(showConfirmConfirmDialog) {
        if (showConfirmConfirmDialog) {
            selectedTeamId = null
            isAmSelected = false
            isPmSelected = false
            moveDateStr = ""
            existingEventsForMoveDate = emptyList()
            
            val estimateId = event.linkedEstimateId
            if (!estimateId.isNullOrBlank()) {
                isLoadingExistingEvents = true
                scope.launch {
                    try {
                        val estimate = viewModel.getEstimateById(estimateId)
                        if (estimate != null && !estimate.moveDate.isNullOrBlank()) {
                            moveDateStr = estimate.moveDate
                            existingEventsForMoveDate = viewModel.getEventsForDateString(estimate.moveDate)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoadingExistingEvents = false
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth
        var cardHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
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
            val isContractConfirmed = event.isAllDay && event.teamId != null
            val titleFirstLine = event.title.lineSequence().firstOrNull()?.trim() ?: ""
            val startLocation = event.location.split("|||").getOrNull(0)?.trim() ?: ""
            val displayText = if (isContractConfirmed) {
                event.title
            } else {
                if (startLocation.isNotEmpty()) {
                    "$titleFirstLine $startLocation"
                } else {
                    titleFirstLine
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = minOf(screenWidth, 400.dp) * 0.025f, 
                        end = minOf(screenWidth, 400.dp) * 0.015f, 
                        top = if (event.isAllDay && !displayText.contains("\n")) (screenHeight * 0.026f) else (screenHeight * 0.012f), 
                        bottom = if (event.isAllDay && !displayText.contains("\n")) (screenHeight * 0.026f) else (screenHeight * 0.012f)
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
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(minOf(screenWidth, 400.dp) * 0.01f)
                                .height(screenHeight * 0.035f)
                                .clip(RoundedCornerShape(1.dp))
                                .background(catColor.copy(alpha = if (event.isCompleted) 0.5f else 1f))
                        )
                        if (event.repeatType != "NONE") {
                            Box(
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "반복",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(minOf(screenWidth, 400.dp) * 0.025f))
                }

                // 일정 제목 Column (우측 남은 가로 공간을 채움)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayText,
                        fontSize = (minOf(screenWidth, 400.dp).value * 0.04f).sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = if (isContractConfirmed) 2 else 1,
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
                -(108.dp + 8.dp).roundToPx()
            }

            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = {
                    showContextMenu = false
                    showSecondBubble = false
                },
                offset = IntOffset(0, popupOffset),
                properties = PopupProperties(focusable = true)
            ) {
                // 전체를 카드 너비만큼의 Row로 구성:
                // 좌측 절반: 1차 말풍선(계약확정/방문예약) + 삭제
                // 우측 절반: 2차 말풍선(문자발송/문구수정)
                Row(
                    modifier = Modifier.width(cardWidth)
                ) {
                    // ── 좌측 절반 ──
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        // 계약확정 (상)
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                contractButtonHeight = with(density) { coords.size.height.toDp() }
                            }
                        ) {
                            BubbleButton(
                                text = "계약확정",
                                onClick = { showConfirmConfirmDialog = true },
                                containerColor = Color(0xFF81C784),
                                contentColor = Color(0xFF36221A),
                                arrowOnLeft = false,
                                arrowOnTop = false,
                                arrowPositionCenter = true
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 방문예약 (하)
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                visitButtonWidth = with(density) { coords.size.width.toDp() }
                            }
                        ) {
                            BubbleButton(
                                text = "방문예약",
                                onClick = { showSecondBubble = !showSecondBubble },
                                containerColor = Color(0xFF64B5F6),
                                contentColor = Color(0xFF36221A),
                                arrowOnLeft = false,
                                arrowOnTop = false,
                                arrowPositionCenter = true
                            )
                        }

                        // 카드 높이만큼 공간 확보
                        Spacer(modifier = Modifier.height(cardHeight + 8.dp))

                        // 삭제 버튼 (하단)
                        Box {
                            BubbleButton(
                                text = "삭제",
                                onClick = { showDeleteConfirmDialog = true },
                                containerColor = Color(0xFFE57373),
                                contentColor = Color(0xFF36221A),
                                arrowOnLeft = false,
                                arrowOnTop = true,
                                arrowPositionCenter = true,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "삭제",
                                        tint = Color(0xFF36221A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    // ── 우측 절반: 2차 말풍선 ──
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // 계약확정 높이 + 간격 만큼 내려서 방문예약과 동일 Y위치에 문자발송 정렬
                        Spacer(modifier = Modifier.height(contractButtonHeight + 8.dp))
                        if (showSecondBubble) {
                            val animVisible = remember { MutableTransitionState(false) }.apply {
                                targetState = true
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visibleState = animVisible,
                                enter = slideInHorizontally(
                                    animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
                                    initialOffsetX = { fullWidth -> -fullWidth }
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    // 문자발송 (상)
                                    BubbleButton(
                                        text = "문자발송",
                                        onClick = {
                                            showSecondBubble = false
                                            showContextMenu = false
                                            scope.launch {
                                                val template = context.dataStore.data.map { prefs ->
                                                    prefs[VISIT_MESSAGE_TEMPLATE_KEY]
                                                }.first() ?: DEFAULT_VISIT_MESSAGE_TEMPLATE

                                                val dateFormat = SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREAN)
                                                val timeStr = dateFormat.format(Date(event.startMillis))
                                                val body = template.replace("{시작시간}", timeStr)

                                                val phone = extractPhoneNumber(event.title)
                                                    ?: extractPhoneNumber(event.location)
                                                    ?: extractPhoneNumber(event.notes)
                                                    ?: ""
                                                try {
                                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                        data = Uri.parse("smsto:$phone")
                                                        putExtra("sms_body", body)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "SMS 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        containerColor = Color(0xFF80DEEA),
                                        contentColor = Color(0xFF36221A),
                                        arrowOnLeft = true,
                                        arrowPositionCenter = true
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 문구수정 (하)
                                    BubbleButton(
                                        text = "문구수정",
                                        onClick = {
                                            showSecondBubble = false
                                            showContextMenu = false
                                            showEditTemplateDialog = true
                                        },
                                        containerColor = Color(0xFFFFF176),
                                        contentColor = Color(0xFF36221A),
                                        arrowOnLeft = true,
                                        arrowPositionTop = true,
                                        arrowPositionCenter = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isHighlighted) {
            val badgeStartOffset = (minOf(screenWidth, 400.dp) * 0.19f) - 17.dp
            Box(
                modifier = Modifier
                    .padding(start = badgeStartOffset)
                    .size(34.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        viewModel.setShowChangeNotificationDialog(true)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "수정됨",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

    if (showConfirmConfirmDialog) {
        Dialog(onDismissRequest = { showConfirmConfirmDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Title
                        Text(
                            text = "계약을 확정하시겠습니까?",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        // Existing events list on the target date
                        if (moveDateStr.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📅 이사 예정일: $moveDateStr",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isLoadingExistingEvents) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                val confirmedEvents = existingEventsForMoveDate.filter { it.teamId != null }

                                if (confirmedEvents.isEmpty() && !isLoadingExistingEvents) {
                                    Text(
                                        text = "해당 날짜에 배정된 팀이 없습니다.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (confirmedEvents.isNotEmpty()) {
                                    Text(
                                        text = "배정된 팀 현황:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    confirmedEvents.sortedWith(compareBy({ it.teamId }, { it.slotPosition })).forEach { conf ->
                                        val tPref = teamPrefsList.getOrNull((conf.teamId ?: 1) - 1) 
                                            ?: (TeamConfigs.getOrNull((conf.teamId ?: 1) - 1)?.let { it.defaultName to it.defaultColor } ?: ("" to 0xFF4CAF50L))
                                        val tName = tPref.first
                                        val slotText = when (conf.slotPosition) {
                                            "top" -> "오전"
                                            "bottom" -> "오후"
                                            else -> "하루종일"
                                        }
                                        val titleClean = conf.title.lineSequence().firstOrNull()?.replace("$tName.", "")?.trim() ?: ""
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(tPref.second))
                                            )
                                            Text(
                                                text = "[$tName / $slotText] $titleClean",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Team Selection
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "팀 선택",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            val teamChunks = (0 until slotCount).chunked(3)
                            teamChunks.forEach { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chunk.forEach { index ->
                                        val teamIdVal = index + 1
                                        val teamPref = teamPrefsList.getOrNull(index) ?: (TeamConfigs[index].defaultName to TeamConfigs[index].defaultColor)
                                        val teamName = teamPref.first
                                        val teamColor = teamPref.second
                                        val isSelected = selectedTeamId == teamIdVal

                                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(containerColor)
                                                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                                                .clickable { selectedTeamId = teamIdVal }
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(teamColor))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = teamName,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (chunk.size < 3) {
                                        repeat(3 - chunk.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // AM/PM Selection
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "오전/오후 선택",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val amContainerColor = if (isAmSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                val amBorderColor = if (isAmSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(amContainerColor)
                                        .border(1.5.dp, amBorderColor, RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (isAmSelected) {
                                                if (isPmSelected) isAmSelected = false
                                            } else {
                                                isAmSelected = true
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "오전",
                                        fontSize = 13.sp,
                                        fontWeight = if (isAmSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isAmSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val pmContainerColor = if (isPmSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                val pmBorderColor = if (isPmSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(pmContainerColor)
                                        .border(1.5.dp, pmBorderColor, RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (isPmSelected) {
                                                if (isAmSelected) isPmSelected = false
                                            } else {
                                                isPmSelected = true
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "오후",
                                        fontSize = 13.sp,
                                        fontWeight = if (isPmSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isPmSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // SMS Message Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val estimateId = event.linkedEstimateId
                                        var moveDateStr = ""
                                        var startTimeStr = ""
                                        if (!estimateId.isNullOrBlank()) {
                                            try {
                                                val estimate = viewModel.getEstimateById(estimateId)
                                                if (estimate != null) {
                                                    moveDateStr = estimate.moveDate ?: ""
                                                    startTimeStr = estimate.startTime ?: ""
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("CalendarMainScreen", "Error loading estimate for sms", e)
                                            }
                                        }
                                        
                                        val template = context.dataStore.data.map { prefs ->
                                            prefs[CONTRACT_MESSAGE_TEMPLATE_KEY]
                                        }.first() ?: DEFAULT_CONTRACT_MESSAGE_TEMPLATE
                                        
                                        val body = template
                                            .replace("{이사날짜}", moveDateStr)
                                            .replace("{시작시간}", startTimeStr)
                                            
                                        val phone = extractPhoneNumber(event.notes)
                                            ?: extractPhoneNumber(event.title)
                                            ?: extractPhoneNumber(event.location)
                                            ?: ""
                                            
                                        try {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:$phone")
                                                putExtra("sms_body", body)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "SMS 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF80DEEA),
                                    contentColor = Color(0xFF36221A)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("확정문자발송", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            
                            IconButton(
                                onClick = {
                                    editingTemplateType = "contract"
                                    showEditTemplateDialog = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFFFF176), shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "문구수정",
                                    tint = Color(0xFF36221A)
                                )
                            }
                        }
                    }

                    // Bottom Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    showConfirmConfirmDialog = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "취소",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (selectedTeamId == null || (!isAmSelected && !isPmSelected)) {
                                        Toast.makeText(context, "팀과 시간대를 선택해주세요", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val finalSlotPosition = when {
                                            isAmSelected && isPmSelected -> "both"
                                            isAmSelected -> "top"
                                            isPmSelected -> "bottom"
                                            else -> "both"
                                        }
                                        val teamId = selectedTeamId ?: 1
                                        val updatedColorHex = when (teamId) {
                                            1 -> "#FF4CAF50"
                                            2 -> "#FFFFEB3B"
                                            else -> event.colorHex
                                        }
                                        onUpdate(
                                            event.copy(
                                                teamId = teamId,
                                                slotPosition = finalSlotPosition,
                                                colorHex = updatedColorHex
                                            )
                                        )
                                        onConfirmContract(event, teamId, finalSlotPosition)
                                        showConfirmConfirmDialog = false
                                        showContextMenu = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "확인",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        Dialog(onDismissRequest = { showDeleteConfirmDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "삭제하시겠습니까?",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    showDeleteConfirmDialog = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "취소",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.error)
                                .clickable {
                                    showDeleteConfirmDialog = false
                                    showContextMenu = false
                                    onDelete()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "확인",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditTemplateDialog) {
        Dialog(onDismissRequest = { showEditTemplateDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (editingTemplateType == "contract") "계약확정 문자 문구 수정" else "방문예약 문자 문구 수정",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = templateText,
                        onValueChange = { templateText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        placeholder = { Text("문구를 입력하세요") }
                    )
                    
                    Text(
                        text = if (editingTemplateType == "contract") {
                            "※ '{이사날짜}', '{시작시간}' 입력 시 발송 시점의 일정 데이터로 자동 치환됩니다."
                        } else {
                            "※ '{시작시간}' 입력 시 발송 시점의 일정 시간(예: 6월 19일 (금) 14:00)으로 자동 치환됩니다."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditTemplateDialog = false }) {
                            Text("취소", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val key = if (editingTemplateType == "contract") CONTRACT_MESSAGE_TEMPLATE_KEY else VISIT_MESSAGE_TEMPLATE_KEY
                                    context.dataStore.edit { prefs ->
                                        prefs[key] = templateText
                                    }
                                    showEditTemplateDialog = false
                                }
                            }
                        ) {
                            Text("저장", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

class SpeechBubbleShape(
    private val cornerRadius: Dp = 12.dp,
    private val arrowWidth: Dp = 20.dp,
    private val arrowHeight: Dp = 16.dp,
    private val arrowOnTop: Boolean = false,
    private val arrowPositionLeft: Boolean = true,
    private val arrowOnLeft: Boolean = false,
    private val arrowPositionTop: Boolean = true,
    private val arrowPositionCenter: Boolean = false
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val r = with(density) { cornerRadius.toPx() }
            val arrowW = minOf(
                with(density) { arrowWidth.toPx() },
                maxOf(0f, if (arrowOnLeft) size.height - 2 * r else size.width - 2 * r)
            )
            val arrowH = with(density) { arrowHeight.toPx() }

            if (arrowOnLeft) {
                val rectLeft = arrowH
                
                // Start drawing from top-left corner of the content rectangle
                moveTo(rectLeft + r, 0f)
                
                // Top edge to top-right corner
                lineTo(size.width - r, 0f)
                quadraticTo(size.width, 0f, size.width, r)
                
                // Right edge to bottom-right corner
                lineTo(size.width, size.height - r)
                quadraticTo(size.width, size.height, size.width - r, size.height)
                
                // Bottom edge to bottom-left corner
                lineTo(rectLeft + r, size.height)
                quadraticTo(rectLeft, size.height, rectLeft, size.height - r)
                
                // Left edge with curved cartoon tail pointing left
                if (arrowPositionCenter) {
                    val arrowStart = (size.height - arrowW) / 2f
                    val arrowPeakY = size.height / 2f
                    val arrowEnd = (size.height + arrowW) / 2f
                    
                    lineTo(rectLeft, arrowEnd)
                    quadraticTo(rectLeft - arrowH * 0.5f, arrowEnd - arrowW * 0.2f, 0f, arrowPeakY)
                    quadraticTo(rectLeft - arrowH * 0.5f, arrowStart + arrowW * 0.2f, rectLeft, arrowStart)
                } else if (arrowPositionTop) {
                    val arrowStart = r
                    val arrowPeakY = r + arrowW * 0.5f
                    val arrowEnd = r + arrowW
                    
                    lineTo(rectLeft, arrowEnd)
                    quadraticTo(rectLeft - arrowH * 0.7f, r + arrowW * 0.8f, 0f, arrowPeakY)
                    quadraticTo(rectLeft - arrowH * 0.4f, r + arrowW * 0.1f, rectLeft, arrowStart)
                } else {
                    val arrowStart = size.height - r - arrowW
                    val arrowPeakY = size.height - r - arrowW * 0.5f
                    val arrowEnd = size.height - r
                    
                    lineTo(rectLeft, arrowEnd)
                    quadraticTo(rectLeft - arrowH * 0.4f, size.height - r - arrowW * 0.1f, 0f, arrowPeakY)
                    quadraticTo(rectLeft - arrowH * 0.7f, size.height - r - arrowW * 0.8f, rectLeft, arrowStart)
                }
                
                // Left edge up to top-left corner
                lineTo(rectLeft, r)
                quadraticTo(rectLeft, 0f, rectLeft + r, 0f)
                
                close()
            } else if (arrowOnTop) {
                val rectTop = arrowH
                
                // Start drawing from left edge below top-left corner
                moveTo(0f, rectTop + r)
                
                // Top-left round corner
                quadraticTo(0f, rectTop, r, rectTop)
                
                // Top edge with curved cartoon tail
                if (arrowPositionCenter) {
                    val arrowStart = (size.width - arrowW) / 2f
                    val arrowPeakX = size.width / 2f
                    val arrowEnd = (size.width + arrowW) / 2f
                    
                    lineTo(arrowStart, rectTop)
                    quadraticTo(arrowStart + arrowW * 0.2f, rectTop - arrowH * 0.5f, arrowPeakX, 0f)
                    quadraticTo(arrowEnd - arrowW * 0.2f, rectTop - arrowH * 0.5f, arrowEnd, rectTop)
                } else if (arrowPositionLeft) {
                    val arrowStart = r
                    val arrowPeakX = r - arrowW * 0.15f
                    val arrowEnd = r + arrowW
                    
                    lineTo(arrowStart, rectTop)
                    // Curved left side of the tail (concave, curving inwards)
                    quadraticTo(r - arrowW * 0.2f, rectTop - arrowH * 0.4f, arrowPeakX, 0f)
                    // Curved right side of the tail (concave, curving inwards)
                    quadraticTo(r + arrowW * 0.5f, rectTop - arrowH * 0.7f, arrowEnd, rectTop)
                } else {
                    val arrowStart = size.width - r - arrowW
                    val arrowPeakX = size.width - r + arrowW * 0.15f
                    val arrowEnd = size.width - r
                    
                    lineTo(arrowStart, rectTop)
                    // Curved left side of the tail (concave, curving inwards)
                    quadraticTo(size.width - r - arrowW * 0.5f, rectTop - arrowH * 0.7f, arrowPeakX, 0f)
                    // Curved right side of the tail (concave, curving inwards)
                    quadraticTo(size.width - r + arrowW * 0.2f, rectTop - arrowH * 0.4f, arrowEnd, rectTop)
                }
                
                // Top edge to top-right corner
                lineTo(size.width - r, rectTop)
                quadraticTo(size.width, rectTop, size.width, rectTop + r)
                
                // Right edge to bottom-right corner
                lineTo(size.width, size.height - r)
                quadraticTo(size.width, size.height, size.width - r, size.height)
                
                // Bottom edge to bottom-left corner
                lineTo(r, size.height)
                quadraticTo(0f, size.height, 0f, size.height - r)
                
                close()
            } else {
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
                
                // Bottom edge with curved cartoon tail
                if (arrowPositionCenter) {
                    val arrowStart = (size.width - arrowW) / 2f
                    val arrowPeakX = size.width / 2f
                    val arrowEnd = (size.width + arrowW) / 2f
                    
                    lineTo(arrowEnd, rectHeight)
                    quadraticTo(arrowEnd - arrowW * 0.2f, rectHeight + arrowH * 0.5f, arrowPeakX, size.height)
                    quadraticTo(arrowStart + arrowW * 0.2f, rectHeight + arrowH * 0.5f, arrowStart, rectHeight)
                } else if (arrowPositionLeft) {
                    val arrowStart = r
                    val arrowPeakX = r - arrowW * 0.15f
                    val arrowEnd = r + arrowW
                    
                    lineTo(arrowEnd, rectHeight)
                    // Curved right side of the tail (concave, curving inwards)
                    quadraticTo(r + arrowW * 0.5f, rectHeight + arrowH * 0.7f, arrowPeakX, size.height)
                    // Curved left side of the tail (concave, curving inwards)
                    quadraticTo(r - arrowW * 0.2f, rectHeight + arrowH * 0.4f, arrowStart, rectHeight)
                } else {
                    val arrowStart = size.width - r - arrowW
                    val arrowPeakX = size.width - r + arrowW * 0.15f
                    val arrowEnd = size.width - r
                    
                    lineTo(arrowEnd, rectHeight)
                    // Curved right side of the tail (concave, curving inwards)
                    quadraticTo(size.width - r + arrowW * 0.2f, rectHeight + arrowH * 0.4f, arrowPeakX, size.height)
                    // Curved left side of the tail (concave, curving inwards)
                    quadraticTo(size.width - r - arrowW * 0.5f, rectHeight + arrowH * 0.7f, arrowStart, rectHeight)
                }
                
                // Bottom edge to bottom-left corner
                lineTo(r, rectHeight)
                quadraticTo(0f, rectHeight, 0f, rectHeight - r)
                
                close()
            }
        }
        return Outline.Generic(path)
    }
}

@Composable
fun BubbleButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    arrowOnTop: Boolean = false,
    arrowPositionLeft: Boolean = true,
    arrowOnLeft: Boolean = false,
    arrowPositionTop: Boolean = true,
    arrowPositionCenter: Boolean = false,
    icon: @Composable (() -> Unit)? = null
) {
    val shape = remember(arrowOnTop, arrowPositionLeft, arrowOnLeft, arrowPositionTop, arrowPositionCenter) { 
        SpeechBubbleShape(
            arrowOnTop = arrowOnTop,
            arrowPositionLeft = arrowPositionLeft,
            arrowOnLeft = arrowOnLeft,
            arrowPositionTop = arrowPositionTop,
            arrowPositionCenter = arrowPositionCenter
        ) 
    }
    val borderColor = Color(0xFF36221A) // Kawaii-style dark brown border
    
    val topPadding = if (arrowOnTop) 10.dp + 16.dp else 10.dp
    val bottomPadding = if (arrowOnLeft) 10.dp else (if (arrowOnTop) 10.dp else 10.dp + 16.dp)
    val startPadding = if (arrowOnLeft) 16.dp + 16.dp else 16.dp
    val endPadding = 16.dp

    Box(
        modifier = Modifier
            .shadow(elevation = 6.dp, shape = shape)
            .background(color = containerColor, shape = shape)
            .border(width = 2.dp, color = borderColor, shape = shape)
            .clickable { onClick() }
            .padding(
                start = startPadding,
                end = endPadding,
                top = topPadding,
                bottom = bottomPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
        month == 5 && day == 1 -> return "노동절"
        month == 5 && day == 5 -> return "어린이날"
        month == 6 && day == 6 -> return "현충일"
        month == 7 && day == 17 -> return "제헌절"
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
            
            if (month == 5 && day == 3) return "대체공휴일(노동절)"
            if (month == 5 && day == 13) return "부처님오신날"
            
            if (month == 7 && day == 19) return "대체공휴일(제헌절)"
            
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

fun extractPhoneNumber(text: String): String? {
    val regex = Regex("""01[0-9][- ]?[0-9]{3,4}[- ]?[0-9]{4}""")
    return regex.find(text)?.value?.replace(Regex("""[- ]"""), "")
}

@Composable
fun DayEventsDialog(
    dateMillis: Long,
    events: List<Event>,
    categories: List<CalendarCategory>,
    onDismissRequest: () -> Unit,
    onEventClick: (Event) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onToggleComplete: (Event) -> Unit,
    onUpdateEvent: (Event) -> Unit,
    viewModel: CalendarViewModel
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val dateFormat = SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN)
    val lunarStr = getKoreanLunarDateString(dateMillis)
    val titleStr = "${dateFormat.format(Date(dateMillis))} ($lunarStr)"

    val dayEvents = remember(events, dateMillis) {
        val pageStart = dateMillis
        val pageEnd = dateMillis + 24 * 60 * 60 * 1000L - 1
        events.filter { event ->
            event.startMillis <= pageEnd && event.endMillis >= pageStart
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.75f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = titleStr,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List
                if (dayEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "일정이 없습니다.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(dayEvents, key = { "${it.id}_${it.startMillis}" }) { event ->
                            val category = categories.find { it.id == event.calendarId }
                            EventItemCard(
                                event = event,
                                category = category,
                                onClick = { onEventClick(event) },
                                onDelete = { onDeleteEvent(event) },
                                onToggleComplete = { onToggleComplete(event) },
                                onUpdate = onUpdateEvent,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

