package com.example.danallacalendar.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.ui.components.Contact
import com.example.danallacalendar.ui.components.ContactPickerDialog
import com.example.danallacalendar.ui.sync.SyncPermission
import com.example.danallacalendar.ui.sync.SyncPeer
import com.example.danallacalendar.ui.sync.SyncRole
import com.example.danallacalendar.ui.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.util.Collections
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType

// ─────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(
    onNavigateBack: () -> Unit,
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val syncRole by viewModel.syncManager.role.collectAsStateWithLifecycle()
    val syncPermission by viewModel.syncManager.permission.collectAsStateWithLifecycle()
    val inviteCode by viewModel.syncManager.inviteCode.collectAsStateWithLifecycle()
    val isConnected by viewModel.syncManager.isConnected.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.syncManager.connectedPeers.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncManager.syncLogs.collectAsStateWithLifecycle()

    val simConnected by viewModel.syncManager.simGuestConnected.collectAsStateWithLifecycle()
    val simEvents by viewModel.syncManager.simGuestEvents.collectAsStateWithLifecycle()
    val simPermission by viewModel.syncManager.simGuestPermission.collectAsStateWithLifecycle()
    val simInviteCode by viewModel.syncManager.simGuestInviteCode.collectAsStateWithLifecycle()

    var hostIpInput by remember { mutableStateOf("10.0.2.2") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var deviceNameInput by remember { mutableStateOf("My Phone") }
    var selectedPermission by remember { mutableStateOf(SyncPermission.READ_ONLY) }
    var localIpAddress by remember { mutableStateOf("확인 불가 (Wi-Fi 연결 확인)") }

    LaunchedEffect(Unit) {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4 && sAddr.startsWith("192.168.")) {
                            localIpAddress = sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("캘린더 공유 및 동기화", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
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
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("동기화 시뮬레이터", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("로컬 Wi-Fi 동기화", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                )
            }

            if (selectedTab == 0) {
                SyncSimulatorLayout(
                    syncRole = syncRole,
                    inviteCode = simInviteCode,
                    simConnected = simConnected,
                    simEvents = simEvents,
                    simPermission = simPermission,
                    syncLogs = syncLogs,
                    onStartSim = { perm -> viewModel.syncManager.startSimulation(perm) },
                    onConnectSim = { code, name ->
                        val ok = viewModel.syncManager.simulateGuestConnect(code, name)
                        if (ok) {
                            Toast.makeText(context, "시뮬레이션 연결 완료!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "잘못된 초대 코드입니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDisconnectSim = { viewModel.syncManager.simulateGuestDisconnect() },
                    onAddSimEvent = { title, isAllDay ->
                        scope.launch {
                            val calendarId = viewModel.getOrCreateSharedCategory()
                            viewModel.syncManager.addSimulatedEventFromGuest(
                                title = title,
                                startMillis = System.currentTimeMillis(),
                                endMillis = System.currentTimeMillis() + 60 * 60 * 1000L,
                                isAllDay = isAllDay,
                                calendarId = calendarId
                            )
                        }
                    },
                    onDeleteSimEvent = { syncId ->
                        viewModel.syncManager.deleteSimulatedEventFromGuest(syncId)
                    },
                    onResetAll = { viewModel.syncManager.stopAll() }
                )
            } else {
                WifiP2PSyncLayout(
                    syncRole = syncRole,
                    permission = syncPermission,
                    inviteCode = inviteCode,
                    isConnected = isConnected,
                    connectedPeers = connectedPeers,
                    syncLogs = syncLogs,
                    localIpAddress = localIpAddress,
                    hostIpInput = hostIpInput,
                    inviteCodeInput = inviteCodeInput,
                    deviceNameInput = deviceNameInput,
                    selectedPermission = selectedPermission,
                    onHostIpChange = { hostIpInput = it },
                    onInviteCodeChange = { inviteCodeInput = it },
                    onDeviceNameChange = { deviceNameInput = it },
                    onPermissionChange = { selectedPermission = it },
                    onStartHost = { viewModel.syncManager.startHosting(selectedPermission) },
                    onJoinHost = { viewModel.syncManager.joinHost(hostIpInput, inviteCodeInput, deviceNameInput) },
                    onStop = { viewModel.syncManager.stopAll() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TAB 1: Simulator UI
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncSimulatorLayout(
    syncRole: SyncRole,
    inviteCode: String,
    simConnected: Boolean,
    simEvents: List<Event>,
    simPermission: SyncPermission,
    syncLogs: List<String>,
    onStartSim: (SyncPermission) -> Unit,
    onConnectSim: (String, String) -> Unit,
    onDisconnectSim: () -> Unit,
    onAddSimEvent: (String, Boolean) -> Unit,
    onDeleteSimEvent: (String) -> Unit,
    onResetAll: () -> Unit
) {
    var simCodeInput by remember { mutableStateOf("") }
    var guestNameInput by remember { mutableStateOf("친구의 갤럭시") }
    var newEventTitleInput by remember { mutableStateOf("") }
    var newEventAllDay by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "이 시뮬레이터는 한 기기 안에서 두 개의 스마트폰(방장 폰과 친구 폰)을 가상으로 연결하여 실시간 동기화와 읽기/쓰기 권한 분기 처리를 즉시 테스트할 수 있는 기능입니다.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 450.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Host Panel
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (syncRole == SyncRole.SIMULATION)
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("나의 스마트폰 (방장)", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    if (syncRole != SyncRole.SIMULATION) {
                        Text(
                            "시뮬레이션을 시작하여 친구를 초대하고 권한 설정을 테스트하세요.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        var simPerm by remember { mutableStateOf(SyncPermission.READ_ONLY) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("권한 설정:", fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = simPerm == SyncPermission.READ_ONLY,
                                    onClick = { simPerm = SyncPermission.READ_ONLY }
                                )
                                Text("읽기 전용", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = simPerm == SyncPermission.FULL_ACCESS,
                                    onClick = { simPerm = SyncPermission.FULL_ACCESS }
                                )
                                Text("모든 권한", fontSize = 13.sp)
                            }
                        }
                        Button(
                            onClick = { onStartSim(simPerm) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("방 만들기 & 초대코드 생성")
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "방 상태: 공유중 (Host)",
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("초대 코드: ", fontSize = 13.sp)
                                    SelectionContainer {
                                        Text(
                                            inviteCode, fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Text(
                                    text = if (simPermission == SyncPermission.READ_ONLY)
                                        "친구 권한: 읽기 전용" else "친구 권한: 쓰기/편집 가능",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onResetAll,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("시뮬레이션 종료")
                        }

                        Text("실시간 동기화 로그:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(syncLogs) { logMsg ->
                                    Text(logMsg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }
                    }
                }
            }

            // Right: Guest Phone Simulator
            Card(
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(3.dp, Color(0xFF2C2C2C))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("친구의 스마트폰 📱", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    if (!simConnected) {
                        Text("공유 캘린더 참가하기", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "방장에게 전달받은 초대코드를 입력하여 실시간 동기화 방에 접속해 보세요.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextField(
                            value = guestNameInput, onValueChange = { guestNameInput = it },
                            placeholder = { Text("기기 이름") }, singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = simCodeInput, onValueChange = { simCodeInput = it },
                            placeholder = { Text("초대 코드 (ROOM-XXXX-XXXX)") }, singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onConnectSim(simCodeInput, guestNameInput) },
                            enabled = syncRole == SyncRole.SIMULATION && simCodeInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("연결하기") }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("공유 캘린더 (연결됨)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = if (simPermission == SyncPermission.READ_ONLY)
                                        "권한: 읽기 전용 🔒" else "권한: 쓰기/편집 가능 ✏️",
                                    fontSize = 11.sp,
                                    color = if (simPermission == SyncPermission.READ_ONLY)
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onDisconnectSim) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "연결 끊기",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Text("동기화된 일정 목록:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        ) {
                            if (simEvents.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("동기화된 일정이 없습니다.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(simEvents) { event ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.background)
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(event.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    if (event.isAllDay) "하루 종일" else "시간제 일정",
                                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (simPermission == SyncPermission.FULL_ACCESS) {
                                                IconButton(
                                                    onClick = { event.syncId?.let { onDeleteSimEvent(it) } },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete, contentDescription = "삭제",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (simPermission == SyncPermission.READ_ONLY) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("읽기 전용 모드이므로 일정을 추가할 수 없습니다.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextField(
                                    value = newEventTitleInput, onValueChange = { newEventTitleInput = it },
                                    placeholder = { Text("친구 폰에서 추가할 일정 제목") }, singleLine = true,
                                    textStyle = TextStyle(fontSize = 12.sp), modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (newEventTitleInput.isNotBlank()) {
                                            onAddSimEvent(newEventTitleInput, newEventAllDay)
                                            newEventTitleInput = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) { Text("일정 추가 및 실시간 동기화") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TAB 2: Real Wi-Fi Sync Controls (with Phone Invitation)
// ─────────────────────────────────────────────────────────────
@Composable
fun WifiP2PSyncLayout(
    syncRole: SyncRole,
    permission: SyncPermission,
    inviteCode: String,
    isConnected: Boolean,
    connectedPeers: List<SyncPeer>,
    syncLogs: List<String>,
    localIpAddress: String,
    hostIpInput: String,
    inviteCodeInput: String,
    deviceNameInput: String,
    selectedPermission: SyncPermission,
    onHostIpChange: (String) -> Unit,
    onInviteCodeChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onPermissionChange: (SyncPermission) -> Unit,
    onStartHost: () -> Unit,
    onJoinHost: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current

    // Contact picker state
    var showContactPicker by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var phoneInput by remember { mutableStateOf("") }
    var pendingInviteCode by remember { mutableStateOf("") }
    var showSmsConfirm by remember { mutableStateOf(false) }

    // When inviteCode changes (host started), capture it for SMS sending
    LaunchedEffect(inviteCode) {
        if (inviteCode.isNotEmpty()) pendingInviteCode = inviteCode
    }

    // SMS send helper
    fun sendInviteSms(phone: String, code: String, ip: String) {
        val body = "[다날라 캘린더] 캘린더 공유 초대\n초대 코드: $code\n방장 IP: $ip\n앱에서 '로컬 Wi-Fi 동기화' 탭을 열어 코드를 입력하세요."
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phone")
            putExtra("sms_body", body)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "SMS 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showContactPicker) {
        ContactPickerDialog(
            onContactSelected = { contact ->
                selectedContact = contact
                phoneInput = contact.phoneNumber
                showContactPicker = false
            },
            onDismiss = { showContactPicker = false }
        )
    }

    if (showSmsConfirm) {
        AlertDialog(
            onDismissRequest = { showSmsConfirm = false },
            icon = { Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("초대 문자 발송", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("아래 정보로 초대 SMS를 발송합니다.")
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text("수신자: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(selectedContact?.name ?: phoneInput, fontSize = 13.sp)
                            }
                            Row {
                                Text("번호: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(phoneInput, fontSize = 13.sp)
                            }
                            Row {
                                Text("초대 코드: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(pendingInviteCode, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sendInviteSms(phoneInput, pendingInviteCode, localIpAddress)
                        showSmsConfirm = false
                    }
                ) { Text("문자 보내기") }
            },
            dismissButton = {
                TextButton(onClick = { showSmsConfirm = false }) { Text("취소") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (syncRole == SyncRole.NONE) {
            Text("로컬 Wi-Fi 네트워크 동기화 설정", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "동일한 Wi-Fi 네트워크에 있는 두 대의 단말기가 소켓 통신을 통해 실시간으로 일정을 공유합니다. (포트 9090 사용)",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Block A: Host Setup ──────────────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("1단계: 방장(Host)으로 시작하기", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "이 기기에서 캘린더 동기화 서버를 구동하고 친구의 접속 코드를 생성합니다.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("내 Wi-Fi IP 주소: ", fontSize = 13.sp)
                        Text(localIpAddress, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("접속 권한 부여:", fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedPermission == SyncPermission.READ_ONLY,
                                onClick = { onPermissionChange(SyncPermission.READ_ONLY) }
                            )
                            Text("읽기 전용", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedPermission == SyncPermission.FULL_ACCESS,
                                onClick = { onPermissionChange(SyncPermission.FULL_ACCESS) }
                            )
                            Text("모든 권한", fontSize = 13.sp)
                        }
                    }

                    Button(onClick = onStartHost, modifier = Modifier.fillMaxWidth()) {
                        Text("서버 구동 및 초대 코드 생성")
                    }
                }
            }

            // ── Block B: Phone Invitation ────────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "전화번호로 친구 초대하기",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        "초대 코드를 생성한 후 친구의 전화번호를 입력하거나 연락처에서 선택하여 SMS로 초대장을 보낼 수 있습니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Selected contact chip (if chosen)
                    AnimatedVisibility(visible = selectedContact != null) {
                        selectedContact?.let { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            contact.initial.toString(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Column {
                                        Text(contact.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(contact.phoneNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { selectedContact = null; phoneInput = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "선택 해제", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Phone number input row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = {
                                phoneInput = it
                                if (it != selectedContact?.phoneNumber) selectedContact = null
                            },
                            label = { Text("전화번호") },
                            placeholder = { Text("010-0000-0000") },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        // Contacts picker button
                        FilledTonalButton(
                            onClick = { showContactPicker = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(56.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Contacts, contentDescription = "연락처", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("연락처", fontSize = 13.sp)
                        }
                    }

                    // SMS send button
                    Button(
                        onClick = {
                            when {
                                phoneInput.isBlank() -> Toast.makeText(context, "전화번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                                pendingInviteCode.isEmpty() -> Toast.makeText(context, "먼저 서버를 구동하여 초대 코드를 생성해 주세요.", Toast.LENGTH_LONG).show()
                                else -> showSmsConfirm = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("초대 문자(SMS) 보내기", fontWeight = FontWeight.Bold)
                    }

                    // Hint if invite code not generated yet
                    if (pendingInviteCode.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                            Column {
                                Text("초대 코드 준비됨", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                SelectionContainer {
                                    Text(pendingInviteCode, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // ── Block C: Client Join ─────────────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("2단계: 친구(Client)로 접속하기", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "방장의 IP 주소와 초대 코드를 입력하여 동기화를 연동합니다.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = deviceNameInput, onValueChange = onDeviceNameChange,
                        label = { Text("내 기기 이름") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = hostIpInput, onValueChange = onHostIpChange,
                        label = { Text("방장의 IP 주소 (예: 192.168.0.x)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = inviteCodeInput, onValueChange = onInviteCodeChange,
                        label = { Text("초대 코드 (ROOM-XXXX-XXXX)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )

                    Button(
                        onClick = onJoinHost,
                        enabled = hostIpInput.isNotBlank() && inviteCodeInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("방장 캘린더에 연결")
                    }
                }
            }
        } else {
            // Active session view
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (syncRole == SyncRole.HOST) "방장 모드 (Hosting)" else "친구 모드 (Connected Client)",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (syncRole == SyncRole.HOST) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("서버 IP: ", fontSize = 14.sp)
                            Text(localIpAddress, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("활성 초대 코드: ", fontSize = 14.sp)
                            SelectionContainer {
                                Text(inviteCode, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        // Quick SMS invite from active session
                        if (phoneInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = { showSmsConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${selectedContact?.name ?: phoneInput} 에게 초대 문자 보내기")
                            }
                        }

                        Text("접속된 친구 단말기 목록:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (connectedPeers.isEmpty()) {
                            Text("대기 중... 연결된 기기가 없습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            connectedPeers.forEach { peer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(peer.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        text = if (peer.permission == SyncPermission.READ_ONLY) "읽기 전용" else "편집 권한 보유",
                                        color = if (peer.permission == SyncPermission.READ_ONLY) Color.Gray else MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else if (syncRole == SyncRole.CLIENT) {
                        Text("연결 여부: 동기화 동작 중", fontSize = 14.sp)
                        Text(
                            text = if (permission == SyncPermission.READ_ONLY) "내 권한: 읽기 전용 (수정 불가능)" else "내 권한: 모든 권한 (수정 가능)",
                            fontWeight = FontWeight.Bold,
                            color = if (permission == SyncPermission.READ_ONLY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("연결 종료 및 서버 정지")
                    }
                }
            }

            // Sync log
            Text("네트워크 패킷/동기화 로그:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().height(200.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(syncLogs) { logMsg ->
                        Text(logMsg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
