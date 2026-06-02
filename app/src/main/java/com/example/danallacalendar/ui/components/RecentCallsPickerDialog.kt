package com.example.danallacalendar.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class RecentCall(
    val id: String,
    val name: String?,
    val number: String,
    val date: Long,
    val type: Int
)

fun formatPhoneNumber(number: String): String {
    val clean = number.replace(Regex("[^0-9+]"), "")
    var formatted = clean
    if (formatted.startsWith("+82")) {
        formatted = "0" + formatted.substring(3)
    } else if (formatted.startsWith("82")) {
        formatted = "0" + formatted.substring(2)
    }
    
    val cleanDigits = formatted.replace(Regex("[^0-9]"), "")
    return when (cleanDigits.length) {
        8 -> "${cleanDigits.substring(0, 4)}-${cleanDigits.substring(4)}"
        9 -> "${cleanDigits.substring(0, 2)}-${cleanDigits.substring(2, 5)}-${cleanDigits.substring(5)}"
        10 -> {
            if (cleanDigits.startsWith("02")) {
                "${cleanDigits.substring(0, 2)}-${cleanDigits.substring(2, 6)}-${cleanDigits.substring(6)}"
            } else {
                "${cleanDigits.substring(0, 3)}-${cleanDigits.substring(3, 6)}-${cleanDigits.substring(6)}"
            }
        }
        11 -> "${cleanDigits.substring(0, 3)}-${cleanDigits.substring(3, 7)}-${cleanDigits.substring(7)}"
        else -> number
    }
}

fun formatPhoneNumberForDetail(number: String): String {
    return when {
        number.length == 11 ->
            "${number.substring(0, 3)}-" +
            "${number.substring(3, 7)}-" +
            "${number.substring(7)}"
        number.length == 10 ->
            "${number.substring(0, 3)}-" +
            "${number.substring(3, 6)}-" +
            "${number.substring(6)}"
        else -> number
    }
}

fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat(
        "yyyy-MM-dd HH:mm",
        Locale.getDefault()
    )
    return sdf.format(Date(timestamp))
}

@Composable
fun RecentCallDetailDialog(
    call: RecentCall,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val formattedNumber = remember(call.number) {
        val cleanNumber = call.number.replace(Regex("[^0-9]"), "")
        formatPhoneNumberForDetail(cleanNumber)
    }
    val formattedDate = remember(call.date) {
        formatDateTime(call.date)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1행: 이름
                if (!call.name.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "이름",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = call.name,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 2행: 전화번호
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "전화번호",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = formattedNumber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 3행: 날짜 및 시간
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "통화일시",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // [닫기] 버튼
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("닫기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

suspend fun loadRecentCalls(context: Context): List<RecentCall> = withContext(Dispatchers.IO) {
    val calls = mutableListOf<RecentCall>()
    val contentResolver = context.contentResolver

    val projection = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.DATE,
        CallLog.Calls.TYPE
    )

    try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            val seenNumbers = mutableSetOf<String>()

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                while (it.moveToNext() && calls.size < 5) {
                    val id = it.getString(idIdx) ?: ""
                    val rawNumber = it.getString(numberIdx) ?: ""
                    
                    // 중복 방지용 정규화 번호
                    val cleanDigits = rawNumber.replace(Regex("[^0-9]"), "")
                    val normalized = if (cleanDigits.startsWith("82")) {
                        "0" + cleanDigits.substring(2)
                    } else {
                        cleanDigits
                    }

                    if (normalized.isNotEmpty()) {
                        if (seenNumbers.contains(normalized)) {
                            continue
                        }
                        seenNumbers.add(normalized)
                    }

                    val number = formatPhoneNumber(rawNumber)
                    val name = it.getString(nameIdx)
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    calls.add(RecentCall(id, name, number, date, type))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    calls
}

@Composable
fun RecentCallsPickerDialog(
    onCallSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recentCalls by remember { mutableStateOf<List<RecentCall>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var selectedCallForDetail by remember { mutableStateOf<RecentCall?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        isLoading = true
        scope.launch {
            recentCalls = loadRecentCalls(context)
            isLoading = false
        }
    }

    LaunchedEffect(hasPermission) {
        isLoading = true
        if (hasPermission) {
            recentCalls = loadRecentCalls(context)
            isLoading = false
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "최근 통화 목록",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "전화번호를 입력할 최근 통화를 선택하세요",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "통화 목록 불러오는 중...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (recentCalls.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "통화 내역이 없습니다.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(recentCalls, key = { it.id }) { call ->
                            RecentCallItem(
                                call = call,
                                onClick = { selectedCallForDetail = call }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedCallForDetail != null) {
        RecentCallDetailDialog(
            call = selectedCallForDetail!!,
            onConfirm = {
                onCallSelected(selectedCallForDetail!!.number)
                selectedCallForDetail = null
            },
            onDismiss = {
                selectedCallForDetail = null
            }
        )
    }
}

@Composable
private fun RecentCallItem(
    call: RecentCall,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("M월 d일 a h:mm", Locale.KOREAN) }
    val dateStr = formatter.format(Date(call.date))

    val (typeIcon, typeColor) = when (call.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived to Color(0xFF34C759)
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade to Color(0xFF1C62F2)
        CallLog.Calls.MISSED_TYPE -> Icons.Default.CallMissed to Color(0xFFFF3B30)
        else -> Icons.Default.Call to Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon showing call type
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(typeColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = typeIcon,
                contentDescription = null,
                tint = typeColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // Caller name/number and date info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1행: 저장된 이름/메모
            if (!call.name.isNullOrBlank()) {
                Text(
                    text = call.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // 2행: 전화번호
            Text(
                text = call.number,
                fontSize = 14.sp,
                fontWeight = if (call.name.isNullOrBlank()) FontWeight.SemiBold else FontWeight.Normal,
                color = if (call.name.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 3행: 날짜, 시간
            Text(
                text = dateStr,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
