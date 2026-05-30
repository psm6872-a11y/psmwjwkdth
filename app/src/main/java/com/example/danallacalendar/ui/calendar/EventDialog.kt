package com.example.danallacalendar.ui.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danallacalendar.data.model.CalendarEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun EventDialog(
    event: CalendarEvent?, // null for Add Mode
    selectedDateStr: String, // Format: "yyyy-MM-dd"
    deviceUUID: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, date: String, time: String, description: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isEditMode = event != null
    val isOwner = !isEditMode || event?.createdBy == deviceUUID

    var title by remember { mutableStateOf(event?.title ?: "") }
    var date by remember { mutableStateOf(event?.date ?: selectedDateStr) }
    var time by remember { mutableStateOf(event?.time ?: "12:00") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var titleError by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance()

    // Date Picker Dialog
    val datePickerDialog = remember {
        val parts = date.split("-")
        val year = parts.getOrNull(0)?.toInt() ?: calendar.get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toInt() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
        val day = parts.getOrNull(2)?.toInt() ?: calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, y, m, d ->
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.set(y, m, d)
            date = format.format(cal.time)
        }, year, month, day)
    }

    // Time Picker Dialog
    val timePickerDialog = remember {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toInt() ?: 12
        val minute = parts.getOrNull(1)?.toInt() ?: 0

        TimePickerDialog(context, { _, h, m ->
            time = String.format(Locale.getDefault(), "%02d:%02d", h, m)
        }, hour, minute, true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (!isEditMode) "일정 추가" else if (isOwner) "일정 편집" else "일정 상세 정보",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isEditMode && event != null) {
                    Text(
                        text = "작성자: ${if (event.createdBy == deviceUUID) "나" else event.createdByName}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    label = { Text("일정 제목") },
                    singleLine = true,
                    enabled = isOwner,
                    isError = titleError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (titleError) {
                    Text(
                        text = "일정 제목을 입력해 주세요.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { datePickerDialog.show() },
                        enabled = isOwner,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(date)
                    }

                    Button(
                        onClick = { timePickerDialog.show() },
                        enabled = isOwner,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(time)
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("메모") },
                    maxLines = 4,
                    enabled = isOwner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditMode && isOwner && onDelete != null) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("삭제")
                    }
                }

                if (isOwner) {
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                            } else {
                                onConfirm(title, date, time, description)
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("저장")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E293B),
        shape = RoundedCornerShape(16.dp)
    )
}
