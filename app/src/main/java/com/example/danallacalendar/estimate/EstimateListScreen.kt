package com.example.danallacalendar.estimate

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.ui.screens.dataStore
import com.example.danallacalendar.ui.screens.CONTRACT_MESSAGE_TEMPLATE_KEY
import com.example.danallacalendar.ui.screens.DEFAULT_CONTRACT_MESSAGE_TEMPLATE
import com.example.danallacalendar.ui.screens.TEAM_COUNT_KEY
import com.example.danallacalendar.ui.screens.TeamConfigs
import com.example.danallacalendar.ui.screens.settingsDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.example.danallacalendar.ui.screens.VISIT_MESSAGE_TEMPLATE_KEY
import com.example.danallacalendar.ui.screens.DEFAULT_VISIT_MESSAGE_TEMPLATE
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEstimateCopy: (String) -> Unit,
    highlightId: String? = null,
    viewModel: EstimateListViewModel = hiltViewModel()
) {
    val estimateList by viewModel.estimateList.collectAsStateWithLifecycle()
    val isShareEnabled by viewModel.isShareEnabled.collectAsStateWithLifecycle()
    val isGoogleDriveSaveEnabled by viewModel.isGoogleDriveSaveEnabled.collectAsStateWithLifecycle()
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    val linkedEvents by viewModel.linkedEvents.collectAsStateWithLifecycle()
    var selectedEstimate by remember { mutableStateOf<Estimate?>(null) }

    LaunchedEffect(highlightId, estimateList) {
        if (!highlightId.isNullOrBlank() && estimateList.isNotEmpty()) {
            val found = estimateList.find { it.id == highlightId }
            if (found != null) {
                selectedEstimate = found
            }
        }
    }

    var showInfoDialog by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("전체") }
    val context = LocalContext.current

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

    var showConfirmConfirmDialog by remember { mutableStateOf(false) }
    var selectedTeamId by remember { mutableStateOf<Int?>(null) }
    var isAmSelected by remember { mutableStateOf(false) }
    var isPmSelected by remember { mutableStateOf(false) }
    var confirmContractEstimate by remember { mutableStateOf<Estimate?>(null) }
    var existingEventsForMoveDate by remember { mutableStateOf<List<Event>>(emptyList()) }
    var isLoadingExistingEvents by remember { mutableStateOf(false) }
    var moveDateStr by remember { mutableStateOf("") }

    var showConflictConfirmDialog by remember { mutableStateOf(false) }
    var conflictTargetEstimate by remember { mutableStateOf<Estimate?>(null) }
    var conflictTeamId by remember { mutableStateOf<Int?>(null) }
    var conflictSlotPos by remember { mutableStateOf<String?>(null) }
    var conflictMessage by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteEstimateTarget by remember { mutableStateOf<Estimate?>(null) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val rowHeight = screenHeight * 0.040f

    var pendingSignInAction by remember { mutableStateOf<String?>(null) } // "save" or "auto"

    val scope = rememberCoroutineScope()
    LaunchedEffect(showConfirmConfirmDialog) {
        if (showConfirmConfirmDialog) {
            existingEventsForMoveDate = emptyList()
            moveDateStr = ""
            val est = confirmContractEstimate
            if (est != null && est.moveDate.isNotBlank()) {
                moveDateStr = est.moveDate
                isLoadingExistingEvents = true
                scope.launch {
                    try {
                        existingEventsForMoveDate = viewModel.getEventsForDateString(est.moveDate)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoadingExistingEvents = false
                    }
                }
            }
        }
    }
    var showEditTemplateDialog by remember { mutableStateOf(false) }
    var templateText by remember { mutableStateOf("") }
    var editingTemplateType by remember { mutableStateOf("contract") }

    LaunchedEffect(showEditTemplateDialog) {
        if (showEditTemplateDialog) {
            val key = CONTRACT_MESSAGE_TEMPLATE_KEY
            val saved = context.dataStore.data.map { prefs ->
                prefs[key]
            }.first() ?: DEFAULT_CONTRACT_MESSAGE_TEMPLATE
            templateText = saved
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.updateGoogleAccount(account)
            if (pendingSignInAction == "auto") {
                viewModel.toggleAutoDriveSyncEnabled(true)
            } else {
                viewModel.toggleGoogleDriveSaveEnabled(true)
            }
        } catch (e: ApiException) {
            Log.e("EstimateListScreen", "Google Sign-In failed", e)
            viewModel.updateGoogleAccount(null)
            viewModel.toggleGoogleDriveSaveEnabled(false)
            viewModel.toggleAutoDriveSyncEnabled(false)
            Toast.makeText(context, "구글 로그인 실패 (코드: ${e.statusCode})", Toast.LENGTH_LONG).show()
        } finally {
            pendingSignInAction = null
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F0825)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F0825))
        ) {
            // 2단 커스텀 상단 헤더 영역 (배경색: Color(0xFF1E1045)) - 반응형 중앙 정렬 적용
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1045))
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 450.dp)
                ) {
                    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                    var isSearchMode by remember { mutableStateOf(false) }
                    val focusRequester = remember { FocusRequester() }

                    // 1단: 뒤로가기 버튼(시작점 정렬) + "견적서 목록" 타이틀 또는 검색창
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isSearchMode) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    tint = Color.White,
                                    contentDescription = "뒤로가기"
                                )
                            }
                            Text(
                                text = "견적서 목록",
                                fontSize = 33.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            IconButton(
                                onClick = { isSearchMode = true },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    tint = Color.White,
                                    contentDescription = "검색"
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = {
                                        Text(
                                            text = "고객명, 전화번호, 주소 검색...",
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "검색 아이콘",
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                if (searchQuery.isNotEmpty()) {
                                                    viewModel.setSearchQuery("")
                                                } else {
                                                    isSearchMode = false
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "닫기",
                                                tint = Color.White
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color(0xFFE040FB),
                                        focusedIndicatorColor = Color(0xFFE040FB),
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                )
                            }
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        }
                    }

                    // 1단: "참여한 멤버와 공유" 텍스트 + 스위치 토글 (우측 정렬, 반응형 높이)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "참여한 멤버와 공유",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showInfoDialog = "share" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                tint = Color.White.copy(alpha = 0.7f),
                                contentDescription = "정보",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isShareEnabled,
                            onCheckedChange = { viewModel.toggleShareEnabled(it) },
                            modifier = Modifier.scale(0.70f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE040FB),
                                checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2단: "내 구글드라이브에 저장" 텍스트 + 스위치 토글 (우측 정렬, 반응형 높이)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "내 구글드라이브에 저장",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showInfoDialog = "save" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                tint = Color.White.copy(alpha = 0.7f),
                                contentDescription = "정보",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isGoogleDriveSaveEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val hasPerm = GoogleDriveHelper.hasDrivePermission(context)
                                    if (googleAccount == null || !hasPerm) {
                                        pendingSignInAction = "save"
                                        googleSignInLauncher.launch(GoogleDriveHelper.getGoogleSignInClient(context).signInIntent)
                                    } else {
                                        viewModel.toggleGoogleDriveSaveEnabled(true)
                                    }
                                } else {
                                    viewModel.toggleGoogleDriveSaveEnabled(false)
                                }
                            },
                            modifier = Modifier.scale(0.70f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE040FB),
                                checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3단: "공유받은 견적서 자동 저장" 텍스트 + 스위치 토글 (우측 정렬, 반응형 높이)
                    val isAutoDriveSyncEnabled by viewModel.isAutoDriveSyncEnabled.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "공유받은 견적서 자동 저장",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showInfoDialog = "auto" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                tint = Color.White.copy(alpha = 0.7f),
                                contentDescription = "정보",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isAutoDriveSyncEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val hasPerm = GoogleDriveHelper.hasDrivePermission(context)
                                    if (googleAccount == null || !hasPerm) {
                                        pendingSignInAction = "auto"
                                        googleSignInLauncher.launch(GoogleDriveHelper.getGoogleSignInClient(context).signInIntent)
                                    } else {
                                        viewModel.toggleAutoDriveSyncEnabled(true)
                                    }
                                } else {
                                    viewModel.toggleAutoDriveSyncEnabled(false)
                                }
                            },
                            modifier = Modifier.scale(0.70f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE040FB),
                                checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    // 4단: 구글 드라이브 로그인된 경우 계정 정보 표시 및 로그아웃 버튼
                    googleAccount?.let { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "연동 계정: ${account.email ?: ""}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "로그아웃",
                                color = Color(0xFFE040FB),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    GoogleDriveHelper.getGoogleSignInClient(context).signOut().addOnCompleteListener {
                                        viewModel.updateGoogleAccount(null)
                                        viewModel.toggleGoogleDriveSaveEnabled(false)
                                        viewModel.toggleAutoDriveSyncEnabled(false)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 하단 리스트 영역 - 반응형 중앙 정렬 적용
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 450.dp)
                ) {
                    val filteredList = remember(estimateList, selectedFilter, linkedEvents) {
                        when (selectedFilter) {
                            "계약완료" -> {
                                estimateList.filter { est ->
                                    val estimateEvents = linkedEvents.filter { it.linkedEstimateId == est.id }
                                    estimateEvents.isNotEmpty() && estimateEvents.any { it.isAllDay }
                                }
                            }
                            "방문완료" -> {
                                estimateList.filter { est ->
                                    val estimateEvents = linkedEvents.filter { it.linkedEstimateId == est.id }
                                    estimateEvents.isNotEmpty() && estimateEvents.none { it.isAllDay }
                                }
                            }
                            else -> estimateList
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // 3칸의 필터 헤더
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("방문완료", "계약완료", "전체").forEach { filter ->
                                val isSelected = selectedFilter == filter
                                val backgroundColor = if (isSelected) Color(0xFFE040FB) else Color(0xFF1E1045)
                                val contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                                val borderStroke = if (isSelected) BorderStroke(1.dp, Color(0xFFE040FB)) else BorderStroke(1.dp, Color(0xFF311B92).copy(alpha = 0.5f))

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(backgroundColor)
                                        .border(borderStroke, RoundedCornerShape(8.dp))
                                        .clickable { selectedFilter = filter },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter,
                                        color = contentColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (filteredList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (selectedFilter) {
                                        "계약완료" -> "계약완료된 견적서가 없습니다."
                                        "방문완료" -> "방문완료된 견적서가 없습니다."
                                        else -> "저장된 공유 견적서가 없습니다."
                                    },
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredList, key = { it.id }) { estimate ->
                                    val estimateEvents = remember(estimate.id, linkedEvents) {
                                        linkedEvents.filter { it.linkedEstimateId == estimate.id }
                                    }
                                    val badgeStatus = remember(estimateEvents) {
                                        when {
                                            estimateEvents.isEmpty() -> null
                                            estimateEvents.any { it.isAllDay } -> "계약완료"
                                            else -> "방문완료"
                                        }
                                    }
                                    EstimateItemCard(
                                        estimate = estimate,
                                        badgeStatus = badgeStatus,
                                        onItemClick = { selectedEstimate = estimate },
                                        onSyncClick = { viewModel.syncEstimate(estimate) },
                                        onDeleteClick = {
                                            deleteEstimateTarget = estimate
                                            showDeleteConfirmDialog = true
                                        },
                                        onConfirmContractClick = {
                                            confirmContractEstimate = estimate
                                            selectedTeamId = null
                                            isAmSelected = false
                                            isPmSelected = false
                                            showConfirmConfirmDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                    selectedEstimate?.let { estimate ->
                        LocalEstimateViewerDialog(
                            estimate = estimate,
                            onDismiss = { selectedEstimate = null },
                            onEditClick = {
                                selectedEstimate = null
                                onNavigateToEstimateCopy(com.google.gson.Gson().toJson(estimate))
                            }
                        )
                    }

                    // 계약확정 다이얼로그
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
                                                        .clickable { isAmSelected = !isAmSelected }
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
                                                        .clickable { isPmSelected = !isPmSelected }
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
                                                    confirmContractEstimate?.let { est ->
                                                        scope.launch {
                                                            val template = context.dataStore.data.map { prefs ->
                                                                prefs[CONTRACT_MESSAGE_TEMPLATE_KEY]
                                                            }.first() ?: DEFAULT_CONTRACT_MESSAGE_TEMPLATE
                                                            
                                                            val body = template
                                                                .replace("{이사날짜}", est.moveDate)
                                                                .replace("{시작시간}", est.startTime)
                                                                
                                                            val phone = est.phoneNumber
                                                                
                                                            try {
                                                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                                                    data = android.net.Uri.parse("smsto:$phone")
                                                                    putExtra("sms_body", body)
                                                                }
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "SMS 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                                            }
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
                                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "문구수정",
                                                    tint = MaterialTheme.colorScheme.onSurface
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
                                                    confirmContractEstimate = null
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
                                                        confirmContractEstimate?.let { est ->
                                                            scope.launch {
                                                                val conflicts = viewModel.checkContractConflict(
                                                                    dateStr = est.moveDate,
                                                                    teamId = teamId,
                                                                    slotPos = finalSlotPosition
                                                                )
                                                                if (conflicts.isNotEmpty()) {
                                                                    val firstConf = conflicts.first()
                                                                    val confTitle = firstConf.title.split("\n").firstOrNull() ?: ""
                                                                    conflictTargetEstimate = est
                                                                    conflictTeamId = teamId
                                                                    conflictSlotPos = finalSlotPosition
                                                                    conflictMessage = "주의: 선택하신 날짜와 팀/시간대에 이미 확정된 일정이 있습니다.\n(기존 일정: $confTitle)\n\n그래도 중복으로 배정하시겠습니까?"
                                                                    showConflictConfirmDialog = true
                                                                    showConfirmConfirmDialog = false
                                                                } else {
                                                                    viewModel.confirmContract(
                                                                        estimate = est,
                                                                        teamId = teamId,
                                                                        slotPos = finalSlotPosition,
                                                                        onSuccess = {
                                                                            Toast.makeText(context, "계약이 확정되었습니다.", Toast.LENGTH_SHORT).show()
                                                                        },
                                                                        onError = { e ->
                                                                            Toast.makeText(context, "오류: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                                        }
                                                                    )
                                                                    showConfirmConfirmDialog = false
                                                                    confirmContractEstimate = null
                                                                }
                                                            }
                                                        }
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

                    // 중복 확인 다이얼로그 (Soft Alert)
                    if (showConflictConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showConflictConfirmDialog = false
                                conflictTargetEstimate = null
                                conflictTeamId = null
                                conflictSlotPos = null
                            },
                            title = { Text("중복 배정 경고", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = { Text(conflictMessage, color = Color.White.copy(alpha = 0.8f)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val est = conflictTargetEstimate
                                        val teamId = conflictTeamId
                                        val slotPos = conflictSlotPos
                                        if (est != null && teamId != null && slotPos != null) {
                                            viewModel.confirmContract(
                                                estimate = est,
                                                teamId = teamId,
                                                slotPos = slotPos,
                                                onSuccess = {
                                                    Toast.makeText(context, "계약이 확정되었습니다.", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { e ->
                                                    Toast.makeText(context, "오류: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                        showConflictConfirmDialog = false
                                        conflictTargetEstimate = null
                                        conflictTeamId = null
                                        conflictSlotPos = null
                                    }
                                ) {
                                    Text("중복 배정", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showConflictConfirmDialog = false
                                        conflictTargetEstimate = null
                                        conflictTeamId = null
                                        conflictSlotPos = null
                                    }
                                ) {
                                    Text("취소", color = Color.Gray)
                                }
                            },
                            containerColor = Color(0xFF1E1045),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    // 삭제 확인 다이얼로그
                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showDeleteConfirmDialog = false
                                deleteEstimateTarget = null
                            },
                            title = { Text("견적서 삭제", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = { Text("이 견적서를 정말로 삭제하시겠습니까?", color = Color.White.copy(alpha = 0.8f)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        deleteEstimateTarget?.let { est ->
                                            viewModel.deleteEstimate(est)
                                        }
                                        showDeleteConfirmDialog = false
                                        deleteEstimateTarget = null
                                    }
                                ) {
                                    Text("삭제", color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirmDialog = false
                                        deleteEstimateTarget = null
                                    }
                                ) {
                                    Text("취소", color = Color.Gray)
                                }
                            },
                            containerColor = Color(0xFF1E1045),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    showInfoDialog?.let { type ->
                        val title = when (type) {
                            "share" -> "참여한 멤버와 공유"
                            "save" -> "내 구글드라이브에 저장"
                            "auto" -> "공유받은 견적서 자동 저장"
                            else -> ""
                        }
                        val message = when (type) {
                            "share" -> "내가 작성한 견적서는 내 전화기에서만 사용 할 수 있습니다.\n내가 작성한 견적서를 같은 방에 있는 팀원들에게 서로 공유 하기 위해서는 스위치를 켜세요."
                            "save" -> "내가 견적서를 작성하면 내 전화기에 저장이 됩니다. \n스위치를 키게 되면 내가 견적서를 작성하고 완료할 때, 내구글 드라이브에 자동으로 저장합니다.\n구글 로그인 필수.\n로그인 된 계정의 구글드라이브에 \"월/일_견적서상 전화번호 뒷4자리\" 형식으로 자동 저장됩니다.\n견적서를 보관해야 하거나, 백업을 해야 할 시 스위치를 켜세요."
                            "auto" -> "다른 멤버가 공유하여 내 목록에 새로 추가된 견적서를 내 구글 드라이브에 자동으로 저장합니다.\n구글 로그인 필수.\n견적사원, 직원 등 다른 멤버가 견적서를 작성시 견적목록에 자동 추가된 견적서를 현재 로그인 된 계정의 구글드라이브에 \"월/일_견적서상 전화번호 뒷4자리\" 형식으로 자동 저장됩니다.\n주로 모든 견적서를 관리해야 하시는 분은 참여한 다른 멤버들이 \"참여한 멤버와 공유\" 스위치를 켠 상태에서 공유받은 견적서 자동 저장 스위치를 켜세요."
                            else -> ""
                        }

                        AlertDialog(
                            onDismissRequest = { showInfoDialog = null },
                            title = {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                            },
                            text = {
                                Text(
                                    text = message,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { showInfoDialog = null }) {
                                    Text("확인", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                                }
                            },
                            containerColor = Color(0xFF1E1045),
                            shape = RoundedCornerShape(16.dp)
                        )
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
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EstimateItemCard(
    estimate: Estimate,
    badgeStatus: String?,
    onItemClick: () -> Unit,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmContractClick: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth
        val density = LocalDensity.current
        var cardHeight by remember { mutableStateOf(0.dp) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    cardHeight = with(density) { coordinates.size.height.toDp() }
                }
                .combinedClickable(
                    onClick = onItemClick,
                    onLongClick = { showContextMenu = true }
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1045).copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${estimate.customerName} 고객님",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val badgeColor = if (estimate.isSynced) Color(0xFFE040FB) else Color(0xFFFF9800)
                                val badgeText = if (estimate.isSynced) "공유 완료 ☁️" else "로컬 저장"
                                Surface(
                                    color = badgeColor.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .width(80.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    )
                                }
                                
                                if (badgeStatus != null) {
                                    val statusColor = if (badgeStatus == "계약완료") Color(0xFF4CAF50) else Color(0xFF2196F3)
                                    Surface(
                                        color = statusColor.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .width(80.dp)
                                    ) {
                                        Text(
                                            text = "$badgeStatus 📅",
                                            color = statusColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val createdTime = remember(estimate.createdAt) {
                            if (estimate.createdAt > 0L) {
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(estimate.createdAt))
                            } else ""
                        }
                        Text(
                            text = if (createdTime.isNotBlank())
                                "${estimate.estimateDate.ifBlank { "정보 없음" }} [$createdTime]"
                            else
                                estimate.estimateDate.ifBlank { "정보 없음" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (estimate.moveDate.isBlank()) "정보 없음" else "${estimate.moveDate} (${estimate.startTime})",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = estimate.departure.ifBlank { "정보 없음" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = estimate.phoneNumber.ifBlank { "정보 없음" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!estimate.isSynced) {
                        IconButton(onClick = onSyncClick) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "파이어베이스 동기화",
                                tint = Color(0xFFFF9800)
                            )
                        }
                    }
                }
            }
        }

        if (showContextMenu) {
            Popup(
                alignment = Alignment.Center,
                onDismissRequest = {
                    showContextMenu = false
                },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier.width(cardWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 계약확정 (상)
                    BubbleButton(
                        text = "계약확정",
                        onClick = {
                            showContextMenu = false
                            onConfirmContractClick()
                        },
                        containerColor = Color(0xFF81C784),
                        contentColor = Color(0xFF36221A),
                        arrowOnLeft = false,
                        arrowOnTop = false,
                        arrowPositionCenter = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 삭제 (하)
                    BubbleButton(
                        text = "삭제",
                        onClick = {
                            showContextMenu = false
                            onDeleteClick()
                        },
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
        }
    }
}

@Composable
fun LocalEstimateViewerDialog(
    estimate: Estimate,
    onDismiss: () -> Unit,
    onEditClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    var showOverwriteConfirmDialog by remember { mutableStateOf(false) }
    var nextFileNameToUpload by remember { mutableStateOf<String?>(null) }

    val htmlContent = remember(estimate) {
        EstimateHtmlGenerator.generateEstimateHtml(context, estimate)
    }

    fun performUpload(targetFileName: String?) {
        isUploading = true
        coroutineScope.launch {
            try {
                val account = GoogleDriveHelper.getSignedInAccount(context) ?: return@launch
                val jpgPath = EstimatePrintHelper.renderHtmlToJpg(context, htmlContent, estimate)
                if (jpgPath != null) {
                    val jpgFile = java.io.File(jpgPath)
                    val dateStr = estimate.estimateDate.ifBlank { estimate.moveDate }
                    val dateParts = dateStr.split("-")
                    val monthDay = if (dateParts.size >= 3) "${dateParts[1]}-${dateParts[2]}" else "00-00"
                    val rawPhone = estimate.phoneNumber.replace(Regex("[^0-9]"), "")
                    val last4 = if (rawPhone.length >= 4) rawPhone.takeLast(4) else "0000"
                    val originalFileName = "${monthDay}_$last4.jpg"
                    val finalFileName = targetFileName ?: originalFileName

                    val result = GoogleDriveHelper.uploadEstimateJpgWithResult(
                        context,
                        account,
                        jpgFile,
                        finalFileName,
                        estimate.estimateDate
                    )
                    when (result) {
                        is GoogleDriveHelper.UploadResult.Success ->
                            Toast.makeText(context, "구글 드라이브 백업 완료", Toast.LENGTH_SHORT).show()
                        is GoogleDriveHelper.UploadResult.UserRecoverable ->
                            Toast.makeText(context, "구글 드라이브 로그인이 만료되었습니다.\n설정에서 다시 연결해 주세요.", Toast.LENGTH_LONG).show()
                        is GoogleDriveHelper.UploadResult.NoPermission ->
                            Toast.makeText(context, "구글 드라이브 권한이 없습니다.\n설정에서 다시 연결해 주세요.", Toast.LENGTH_LONG).show()
                        is GoogleDriveHelper.UploadResult.Failure -> {
                            android.util.Log.e("EstimateListScreen", "Drive upload failure: ${result.error}")
                            Toast.makeText(context, "백업 실패: 네트워크를 확인해 주세요.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "견적서 이미지 렌더링 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("EstimateListScreen", "Direct Google Drive upload failed", e)
                Toast.makeText(context, "백업 중 오류 발생: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isUploading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F0825)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 상단바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0xFF1E1045))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            tint = Color.White,
                            contentDescription = "닫기",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "견적서 상세 보기",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (onEditClick != null) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onEditClick()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                tint = Color(0xFFE040FB),
                                contentDescription = "수정",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "수정",
                                color = Color(0xFFE040FB),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 상단 액션 버튼 행 (아이콘 + 텍스트 정렬 개선 및 높이 축소)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1045))
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 구글 드라이브 백업
                    if (isUploading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFE040FB),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("백업 중...", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                val account = GoogleDriveHelper.getSignedInAccount(context)
                                if (account == null) {
                                    Toast.makeText(context, "설정 화면에서 구글 드라이브 로그인을 먼저 진행해주세요.", Toast.LENGTH_LONG).show()
                                } else {
                                    isUploading = true
                                    coroutineScope.launch {
                                        try {
                                            if (!GoogleDriveHelper.hasDrivePermission(context)) {
                                                Toast.makeText(context, "구글 드라이브 권한이 없습니다.\n설정에서 다시 연결해 주세요.", Toast.LENGTH_LONG).show()
                                                isUploading = false
                                                return@launch
                                            }
                                            val dateStr = estimate.estimateDate.ifBlank { estimate.moveDate }
                                            val dateParts = dateStr.split("-")
                                            val monthDay = if (dateParts.size >= 3) "${dateParts[1]}-${dateParts[2]}" else "00-00"
                                            val rawPhone = estimate.phoneNumber.replace(Regex("[^0-9]"), "")
                                            val last4 = if (rawPhone.length >= 4) rawPhone.takeLast(4) else "0000"
                                            val fileName = "${monthDay}_$last4.jpg"

                                            val existingId = GoogleDriveHelper.findExistingFileId(
                                                context, account, fileName, estimate.estimateDate
                                            )
                                            if (existingId != null) {
                                                val nextName = GoogleDriveHelper.findNextAvailableFileName(
                                                    context, account, fileName, estimate.estimateDate
                                                )
                                                if (nextName != null) {
                                                    nextFileNameToUpload = nextName
                                                    isUploading = false
                                                    showOverwriteConfirmDialog = true
                                                } else {
                                                    performUpload(null)
                                                }
                                            } else {
                                                performUpload(null)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("EstimateListScreen", "Drive check failed", e)
                                            isUploading = false
                                            performUpload(null)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AddToDrive,
                                    tint = Color.White,
                                    contentDescription = "구글 드라이브 백업",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("구글 백업", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    // 2. 인쇄 / PDF 저장
                    TextButton(
                        onClick = {
                            EstimatePrintHelper.printEstimate(context, htmlContent, estimate)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                tint = Color.White,
                                contentDescription = "인쇄/PDF저장",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("인쇄/PDF", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    // 3. 문자 발송 (MMS)
                    TextButton(
                        onClick = {
                            EstimatePrintHelper.shareEstimateAsJpg(context, htmlContent, estimate)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                tint = Color.White,
                                contentDescription = "문자 발송(MMS)",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("문자 발송", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    // 4. 카카오톡 공유
                    TextButton(
                        onClick = {
                            Toast.makeText(context, "카카오톡 공유용 이미지 생성 중...", Toast.LENGTH_SHORT).show()
                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                            scope.launch {
                                val jpgPath = EstimatePrintHelper.renderHtmlToJpg(context, htmlContent, estimate)
                                if (jpgPath != null) {
                                    val jpgFile = java.io.File(jpgPath)
                                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        jpgFile
                                    )
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                                        setPackage("com.kakao.talk")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "이미지 생성 실패", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                tint = Color(0xFFFFE000),
                                contentDescription = "카카오톡 공유",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("카톡", color = Color(0xFFFFE000), fontSize = 11.sp)
                        }
                    }
                }

                // 구분선
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )

                // WebView
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.textZoom = 100
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                            clearCache(true)
                            loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        webView.clearCache(true)
                        webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (showOverwriteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showOverwriteConfirmDialog = false
                            nextFileNameToUpload = null
                        },
                        title = { Text("이미 저장된 견적서 입니다.", color = Color.White) },
                        text = { Text("다시 저장 할까요?", color = Color.White) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showOverwriteConfirmDialog = false
                                    performUpload(nextFileNameToUpload)
                                    nextFileNameToUpload = null
                                }
                            ) {
                                Text("예", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showOverwriteConfirmDialog = false
                                    nextFileNameToUpload = null
                                }
                            ) {
                                Text("아니오", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF1E1045),
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
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
