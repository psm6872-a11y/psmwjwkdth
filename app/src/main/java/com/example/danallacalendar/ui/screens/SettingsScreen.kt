package com.example.danallacalendar.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import java.io.File
import java.io.FileOutputStream
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.example.danallacalendar.data.local.UserPreferences
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay

// Define the DataStore in SettingsScreen.kt
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "team_settings")
val TEAM_COUNT_KEY = intPreferencesKey("team_count")

fun uriToBase64(context: Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun updateCompanyInfo(roomCode: String, field: String, value: String) {
    if (roomCode.isEmpty()) return
    val firestore = FirebaseFirestore.getInstance()
    val docRef = firestore.collection("company_info").document(roomCode)
    val data = mapOf(field to value)
    docRef.set(data, SetOptions.merge())
        .addOnFailureListener { e ->
            e.printStackTrace()
        }
}

val TEAM_1_NAME_KEY = stringPreferencesKey("team_1_name")
val TEAM_1_COLOR_KEY = longPreferencesKey("team_1_color")
val TEAM_2_NAME_KEY = stringPreferencesKey("team_2_name")
val TEAM_2_COLOR_KEY = longPreferencesKey("team_2_color")
val TEAM_3_NAME_KEY = stringPreferencesKey("team_3_name")
val TEAM_3_COLOR_KEY = longPreferencesKey("team_3_color")
val TEAM_4_NAME_KEY = stringPreferencesKey("team_4_name")
val TEAM_4_COLOR_KEY = longPreferencesKey("team_4_color")
val TEAM_5_NAME_KEY = stringPreferencesKey("team_5_name")
val TEAM_5_COLOR_KEY = longPreferencesKey("team_5_color")

val VISIT_COLOR_DEFAULT_KEY = longPreferencesKey("visit_color_default")
val VISIT_COLOR_ACTIVE_KEY = longPreferencesKey("visit_color_active")
val VISIT_COLOR_DONE_KEY = longPreferencesKey("visit_color_done")
val VISIT_SLOT_COUNT_KEY = intPreferencesKey("visit_slot_count")

data class TeamConfig(
    val nameKey: Preferences.Key<String>,
    val colorKey: Preferences.Key<Long>,
    val defaultName: String,
    val defaultColor: Long
)

val TeamConfigs = listOf(
    TeamConfig(TEAM_1_NAME_KEY, TEAM_1_COLOR_KEY, "백호", 0xFF4CAF50L),
    TeamConfig(TEAM_2_NAME_KEY, TEAM_2_COLOR_KEY, "봉황", 0xFFFFEB3BL),
    TeamConfig(TEAM_3_NAME_KEY, TEAM_3_COLOR_KEY, "청룡", 0xFF9C27B0L),
    TeamConfig(TEAM_4_NAME_KEY, TEAM_4_COLOR_KEY, "황룡", 0xFF2196F3L),
    TeamConfig(TEAM_5_NAME_KEY, TEAM_5_COLOR_KEY, "금룡", 0xFF795548L)
)

val ColorOptions = listOf(
    0xFF4CAF50L, // 초록
    0xFFFFEB3BL, // 노랑
    0xFF9C27B0L, // 보라
    0xFFFF9800L, // 주황
    0xFFFFD700L, // 금색
    0xFFF44336L, // 빨강
    0xFF2196F3L, // 파랑
    0xFF00BCD4L, // 청록
    0xFFE91E63L, // 핑크
    0xFF795548L, // 갈색
    0xFF9E9E9EL, // 회색
    0xFF29B6F6L  // 하늘색
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Read team count from dataStore
    val teamCountFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            preferences[TEAM_COUNT_KEY] ?: 1 // Default is 1
        }
    }
    val teamCount by teamCountFlow.collectAsState(initial = 1)
    var isCompanySettingsExpanded by remember { mutableStateOf(false) }
    var isTeamSettingsExpanded by remember { mutableStateOf(false) }
    var isVisitSettingsExpanded by remember { mutableStateOf(false) }
       val firestore = remember { FirebaseFirestore.getInstance() }
    val userPreferences = remember { UserPreferences(context) }
    val roomCode = remember { userPreferences.getLastRoomCode() }

    var companyName by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var ceoNickname by remember { mutableStateOf("") }
    var companyPhone by remember { mutableStateOf("") }
    var ceoName by remember { mutableStateOf("") }
    var bizNumber by remember { mutableStateOf("") }
    var bankAccount by remember { mutableStateOf("") }
    var logoBase64 by remember { mutableStateOf("") }
    var stampBase64 by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }

    // 진입 시 단 1회 Firestore에서 데이터 로드
    LaunchedEffect(roomCode) {
        if (roomCode.isNotEmpty()) {
            isLoading = true
            firestore.collection("company_info").document(roomCode)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        companyName = snapshot.getString("companyName") ?: ""
                        licenseNumber = snapshot.getString("licenseNumber") ?: ""
                        ceoNickname = snapshot.getString("ceoNickname") ?: ""
                        companyPhone = snapshot.getString("companyPhone") ?: ""
                        ceoName = snapshot.getString("ceoName") ?: ""
                        bizNumber = snapshot.getString("bizNumber") ?: ""
                        bankAccount = snapshot.getString("bankAccount") ?: ""
                        logoBase64 = snapshot.getString("logoBase64") ?: ""
                        stampBase64 = snapshot.getString("stampBase64") ?: ""
                        android.util.Log.d("SettingsScreen", "Firestore 업체 정보 로드 완료 (상호명: $companyName)")
                    } else {
                        android.util.Log.d("SettingsScreen", "Firestore에 업체 정보 문서가 존재하지 않습니다. 신규 작성이 필요합니다. (방코드: $roomCode)")
                    }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("SettingsScreen", "Firestore 업체 정보 로드 중 예외 발생", e)
                    android.widget.Toast.makeText(context, "업체 정보를 불러오지 못했습니다: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
        } else {
            android.util.Log.w("SettingsScreen", "방코드(roomCode)가 설정되지 않아 Firestore에서 업체 정보를 로드할 수 없습니다.")
        }
    }

    // 갤러리 이미지 로드 후 로컬 상태만 변경 (백그라운드 스레드 처리로 UI 프리징 방지)
    val logoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val base64Str = uriToBase64(context, it)
                if (base64Str != null) {
                    val mimeType = context.contentResolver.getType(it) ?: "image/png"
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        logoBase64 = "data:$mimeType;base64,$base64Str"
                    }
                }
            }
        }
    }

    val stampLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val base64Str = uriToBase64(context, it)
                if (base64Str != null) {
                    val mimeType = context.contentResolver.getType(it) ?: "image/png"
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        stampBase64 = "data:$mimeType;base64,$base64Str"
                    }
                }
            }
        }
    }

    // 일괄 저장 헬퍼 함수
    val saveCompanyInfo = {
        if (roomCode.isNotEmpty()) {
            val data = mapOf(
                "companyName" to companyName,
                "licenseNumber" to licenseNumber,
                "ceoNickname" to ceoNickname,
                "companyPhone" to companyPhone,
                "ceoName" to ceoName,
                "bizNumber" to bizNumber,
                "bankAccount" to bankAccount,
                "logoBase64" to logoBase64,
                "stampBase64" to stampBase64
            )
            firestore.collection("company_info").document(roomCode)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    android.widget.Toast.makeText(context, "업체 정보가 저장되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    android.widget.Toast.makeText(context, "저장에 실패했습니다: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
        } else {
            android.widget.Toast.makeText(context, "방코드가 설정되지 않았습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }


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

    val visitSlotCountFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            preferences[VISIT_SLOT_COUNT_KEY] ?: 1
        }
    }
    val visitSlotCount by visitSlotCountFlow.collectAsState(initial = 1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "설정",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
            // Category: 업체 정보 설정
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "━━ 업체 정보 설정 ━━",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCompanySettingsExpanded = !isCompanySettingsExpanded }
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "견적서 업체 정보 설정 ${if (isCompanySettingsExpanded) "▲" else "▼"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isCompanySettingsExpanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsInputField(
                                label = "상호명",
                                value = companyName,
                                onValueChange = { companyName = it }
                            )

                            SettingsInputField(
                                label = "관허 번호",
                                value = licenseNumber,
                                onValueChange = { licenseNumber = it }
                            )

                            SettingsInputField(
                                label = "대표자 닉네임",
                                value = ceoNickname,
                                onValueChange = { ceoNickname = it }
                            )

                            SettingsInputField(
                                label = "전화번호",
                                value = companyPhone,
                                onValueChange = { companyPhone = it }
                            )

                            SettingsInputField(
                                label = "대표자명",
                                value = ceoName,
                                onValueChange = { ceoName = it }
                            )

                            SettingsInputField(
                                label = "사업자번호",
                                value = bizNumber,
                                onValueChange = { bizNumber = it }
                            )

                            SettingsInputField(
                                label = "계좌번호",
                                value = bankAccount,
                                onValueChange = { bankAccount = it }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                            SettingsImageField(
                                label = "로고 이미지",
                                base64Str = logoBase64,
                                onSelectClick = {
                                    logoLauncher.launch("image/*")
                                },
                                onClearClick = {
                                    logoBase64 = ""
                                }
                            )

                            SettingsImageField(
                                label = "도장 이미지",
                                base64Str = stampBase64,
                                onSelectClick = {
                                    stampLauncher.launch("image/*")
                                },
                                onClearClick = {
                                    stampBase64 = ""
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { saveCompanyInfo() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "설정 저장",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Category: 화면 설정
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "━━ 캘린더 하단바 설정 ━━",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTeamSettingsExpanded = !isTeamSettingsExpanded }
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "계약슬롯 설정 ${if (isTeamSettingsExpanded) "▲" else "▼"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isTeamSettingsExpanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "계약슬롯 칸 수 설정",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "캘린더 화면에 날짜(하루) 하단의 색상막대의 갯수를 분할 합니다.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..5).forEach { count ->
                                    val isSelected = teamCount == count
                                    val backgroundColor by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        label = "tabBg"
                                    )
                                    val textColor by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        label = "tabText"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(backgroundColor)
                                            .clickable {
                                                scope.launch {
                                                    context.settingsDataStore.edit { preferences ->
                                                        preferences[TEAM_COUNT_KEY] = count
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${count}칸",
                                            color = textColor,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                            (0 until teamCount).forEach { index ->
                                val config = TeamConfigs[index]
                                val currentPref = teamPrefsList.getOrNull(index) ?: (config.defaultName to config.defaultColor)
                                val dbName = currentPref.first
                                val dbColor = currentPref.second

                                var localName by remember(dbName) { mutableStateOf(dbName) }
                                var showColorDropdown by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}팀",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.width(36.dp)
                                    )

                                    var isFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = localName,
                                            onValueChange = { newVal ->
                                                localName = newVal
                                                scope.launch {
                                                    context.settingsDataStore.edit { preferences ->
                                                        preferences[config.nameKey] = newVal
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged {
                                                    isFocused = it.isFocused
                                                }
                                        )
                                    }

                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .clickable { showColorDropdown = true }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(dbColor))
                                                    .border(1.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
                                            )
                                            Text(
                                                text = "▼",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showColorDropdown,
                                            onDismissRequest = { showColorDropdown = false },
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ColorOptions.chunked(4).forEach { rowColors ->
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        rowColors.forEach { colorVal ->
                                                            val isColorSelected = dbColor == colorVal
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(colorVal))
                                                                    .border(
                                                                        width = if (isColorSelected) 3.dp else 1.dp,
                                                                        color = if (isColorSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                                                        shape = CircleShape
                                                                    )
                                                                    .clickable {
                                                                        scope.launch {
                                                                            context.settingsDataStore.edit { preferences ->
                                                                                preferences[config.colorKey] = colorVal
                                                                            }
                                                                        }
                                                                        showColorDropdown = false
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
                    }
                }
            }

            // Category: 방문 설정
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isVisitSettingsExpanded = !isVisitSettingsExpanded }
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "방문슬롯 설정 ${if (isVisitSettingsExpanded) "▲" else "▼"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isVisitSettingsExpanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "방문 칸 수 설정",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "캘린더 화면에 날짜(하루) 하단의 방문 색상막대 갯수를 분할 합니다.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..5).forEach { count ->
                                    val isSelected = visitSlotCount == count
                                    val backgroundColor by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        label = "visitTabBg"
                                    )
                                    val textColor by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        label = "visitTabText"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(backgroundColor)
                                            .clickable {
                                                scope.launch {
                                                    context.settingsDataStore.edit { preferences ->
                                                        preferences[VISIT_SLOT_COUNT_KEY] = count
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${count}칸",
                                            color = textColor,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                            val visitItems = listOf(
                                Triple("일정없음", VISIT_COLOR_DEFAULT_KEY, visitColors.first),
                                Triple("일정있음", VISIT_COLOR_ACTIVE_KEY, visitColors.second),
                                Triple("일정완료", VISIT_COLOR_DONE_KEY, visitColors.third)
                            )

                            visitItems.forEach { (label, key, currentColor) ->
                                var showColorDropdown by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .clickable { showColorDropdown = true }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(currentColor))
                                                    .border(1.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
                                            )
                                            Text(
                                                text = "▼",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showColorDropdown,
                                            onDismissRequest = { showColorDropdown = false },
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ColorOptions.chunked(4).forEach { rowColors ->
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        rowColors.forEach { colorVal ->
                                                            val isColorSelected = currentColor == colorVal
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(colorVal))
                                                                    .border(
                                                                        width = if (isColorSelected) 3.dp else 1.dp,
                                                                        color = if (isColorSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                                                        shape = CircleShape
                                                                    )
                                                                    .clickable {
                                                                        scope.launch {
                                                                            context.settingsDataStore.edit { preferences ->
                                                                                preferences[key] = colorVal
                                                                            }
                                                                        }
                                                                        showColorDropdown = false
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
                    }
                }
            }

            // Category: 시스템 및 정보 (Placeholder for extensibility)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "시스템 설정",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "알림 설정",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "공유 캘린더 변경 알림 수신 (준비 중)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = false,
                                onCheckedChange = null,
                                enabled = false
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "자동 동기화",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "백그라운드 백업 주기 설정 (준비 중)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "사용 안 함",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Base64Thumbnail(base64Str: String, modifier: Modifier = Modifier) {
    val bitmap = remember(base64Str) {
        if (base64Str.isNotEmpty()) {
            try {
                val cleanBase64 = if (base64Str.contains(",")) {
                    base64Str.substringAfter(",")
                } else {
                    base64Str
                }
                val decodedString = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Thumbnail",
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "이미지 없음",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(90.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        isFocused = it.isFocused
                    }
            )
        }
    }
}

@Composable
fun SettingsImageField(
    label: String,
    base64Str: String,
    onSelectClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(90.dp)
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Base64Thumbnail(
                base64Str = base64Str,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .clickable { onSelectClick() }
            )

            Button(
                onClick = onSelectClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("선택", fontSize = 12.sp)
            }

            if (base64Str.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("삭제", fontSize = 12.sp)
                }
            }
        }
    }
}

