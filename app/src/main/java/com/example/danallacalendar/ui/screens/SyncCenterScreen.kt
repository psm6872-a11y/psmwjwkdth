package com.example.danallacalendar.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danallacalendar.ui.components.Contact
import com.example.danallacalendar.ui.components.ContactPickerDialog
import com.example.danallacalendar.ui.sync.SyncPermission
import com.example.danallacalendar.ui.sync.SyncRole
import com.example.danallacalendar.ui.viewmodel.CalendarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(
    onNavigateBack: () -> Unit,
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val syncRole by viewModel.syncManager.role.collectAsStateWithLifecycle()
    val roomCode by viewModel.syncManager.inviteCode.collectAsStateWithLifecycle()
    val isConnected by viewModel.syncManager.isConnected.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncManager.syncLogs.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.syncManager.connectedPeers.collectAsStateWithLifecycle()

    var inputRoomCode by remember { mutableStateOf("") }
    var joinDeviceName by remember { mutableStateOf("친구 기기") }
    var selectedPermission by remember { mutableStateOf(SyncPermission.READ_ONLY) }
    
    // Contact picker state
    var showContactPicker by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var phoneInput by remember { mutableStateOf("") }
    var showSmsConfirm by remember { mutableStateOf(false) }

    // Helper: copy to clipboard
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Room Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "공유 코드가 복사되었습니다: $text", Toast.LENGTH_SHORT).show()
    }

    // SMS send helper
    fun sendInviteSms(phone: String, code: String) {
        val permKorean = if (selectedPermission == SyncPermission.READ_ONLY) "읽기 전용" else "모든 권한"
        val body = "[다날라 캘린더] 캘린더 공유 초대장\n공유방 번호: $code\n부여된 권한: $permKorean\n앱의 '캘린더 공유' 메뉴에서 코드를 입력해 동기화해 보세요."
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

    fun sendKakaoInvite(code: String) {
        val inviteLink = "intent://join?code=$code&perm=${selectedPermission.name}#Intent;scheme=danallacalendar;package=com.example.danallacalendar;end"
        val body = "[다날라 캘린더] 친구와 공유 초대장\n\n친구로부터 캘린더 공유 그룹에 초대받았습니다.\n아래 링크를 누르면 공유 캘린더에 자동으로 참여합니다:\n\n$inviteLink\n\n(링크 클릭이 되지 않는 경우, 앱의 [친구와 공유] 메뉴에서 공유 코드 '$code'를 직접 입력하여 참여해 주세요.)"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "카카오톡 / 친구 초대하기"))
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
                                Text("공유방 코드: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(roomCode, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Row {
                                Text("부여 권한: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    text = if (selectedPermission == SyncPermission.READ_ONLY) "읽기 전용" else "모든 권한",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sendInviteSms(phoneInput, roomCode)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("친구와 공유", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "친구와 공유 설정", 
                fontWeight = FontWeight.Bold, 
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "친구를 초대하여 일정을 함께 관리하고 실시간으로 공유할 수 있습니다. 공유 코드를 이용해 간편하게 연결하세요.",
                fontSize = 13.sp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (syncRole == SyncRole.NONE) {
                // ── Option A: Create Room ──────────────────────────────────────
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("공유 캘린더 만들기", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "새로운 공유 캘린더 그룹을 개설하고 친구를 멤버로 초대하여 일정을 공유합니다.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Permission selection row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("참여 멤버 권한:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedPermission == SyncPermission.READ_ONLY,
                                    onClick = { selectedPermission = SyncPermission.READ_ONLY }
                                )
                                Text("읽기 전용", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedPermission == SyncPermission.FULL_ACCESS,
                                    onClick = { selectedPermission = SyncPermission.FULL_ACCESS }
                                )
                                Text("모든 권한", fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = { viewModel.syncManager.startHosting(selectedPermission) }, 
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("공유 캘린더 그룹 개설", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Option B: Join Room ──────────────────────────────────────
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("초대받은 캘린더에 참여하기", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "친구에게 전달받은 6자리 공유 코드를 입력하여 일정을 실시간 연동합니다.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = joinDeviceName,
                            onValueChange = { joinDeviceName = it },
                            label = { Text("내 기기 이름") },
                            placeholder = { Text("예: 내 휴대폰, 패드 등") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        OutlinedTextField(
                            value = inputRoomCode,
                            onValueChange = { inputRoomCode = it.take(6) },
                            label = { Text("공유 코드 (6자리)") },
                            placeholder = { Text("예: 123456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Button(
                            onClick = { viewModel.syncManager.joinHost("", inputRoomCode, joinDeviceName) },
                            enabled = inputRoomCode.length == 6 && joinDeviceName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("공유 캘린더 참여", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // ── Connected Active Session ──────────────────────────────────
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle, 
                                contentDescription = null, 
                                tint = Color(0xFF34C759), 
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (syncRole == SyncRole.HOST) "공유 캘린더 생성됨 (방장)" else "공유 캘린더 연결됨 (참여자)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("공유 코드", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer {
                                    Text(roomCode, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            IconButton(onClick = { copyToClipboard(roomCode) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "공유 코드 복사")
                            }
                        }

                        // Display list of connected/participating members
                        Text("참여 중인 멤버 목록", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (connectedPeers.isEmpty()) {
                            Text("멤버 불러오는 중...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                connectedPeers.forEach { peer ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (peer.name.isNotEmpty()) peer.name.first().toString() else "?",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Text(peer.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        }
                                        if (syncRole == SyncRole.HOST && peer.name != "방장 (나)") {
                                            var showMenu by remember { mutableStateOf(false) }
                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (peer.permission == SyncPermission.READ_ONLY) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.clickable { showMenu = true }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = if (peer.permission == SyncPermission.READ_ONLY) "읽기 전용" else "모든 권한",
                                                            fontSize = 12.sp,
                                                            color = if (peer.permission == SyncPermission.READ_ONLY) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowDropDown,
                                                            contentDescription = "권한 변경",
                                                            tint = if (peer.permission == SyncPermission.READ_ONLY) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = showMenu,
                                                    onDismissRequest = { showMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("읽기 전용") },
                                                        onClick = {
                                                            viewModel.syncManager.updateMemberPermission(peer.name, SyncPermission.READ_ONLY)
                                                            showMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("모든 권한") },
                                                        onClick = {
                                                            viewModel.syncManager.updateMemberPermission(peer.name, SyncPermission.FULL_ACCESS)
                                                            showMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = if (peer.permission == SyncPermission.READ_ONLY) "읽기 전용" else if (peer.name.startsWith("방장")) "모든 권한 (방장)" else "모든 권한 (편집 가능)",
                                                fontSize = 12.sp,
                                                color = if (peer.permission == SyncPermission.READ_ONLY) Color.Gray else MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // SMS Invitation Card inside Active Session for Host
                        if (syncRole == SyncRole.HOST) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Text("전화번호로 친구 초대하기", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
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
                                FilledTonalButton(
                                    onClick = { showContactPicker = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(56.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.Contacts, contentDescription = "연락처", modifier = Modifier.size(20.dp))
                                }
                            }

                            Button(
                                onClick = {
                                    if (phoneInput.isBlank()) {
                                        Toast.makeText(context, "전화번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showSmsConfirm = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("초대 문자(SMS) 보내기", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    sendKakaoInvite(roomCode)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFEE500),
                                    contentColor = Color(0xFF3C1E1E)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("카카오톡으로 초대 보내기", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { viewModel.syncManager.stopAll() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("공유 종료", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Sync Log Console
            Text("실시간 동기화 상태 로그", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(12.dp),
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
