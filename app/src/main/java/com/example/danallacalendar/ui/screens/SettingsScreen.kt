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

// Define the DataStore in SettingsScreen.kt
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "team_settings")
val TEAM_COUNT_KEY = intPreferencesKey("team_count")

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
    var isTeamSettingsExpanded by remember { mutableStateOf(false) }
    var isVisitSettingsExpanded by remember { mutableStateOf(false) }

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
