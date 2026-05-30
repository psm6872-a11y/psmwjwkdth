package com.example.danallacalendar.ui.room

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RoomScreen(
    viewModel: RoomViewModel,
    onNavigateToCalendar: () -> Unit,
    onNavigateToNickname: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val roomCode by viewModel.roomCode.collectAsStateWithLifecycle()

    var inputCode by remember { mutableStateOf("") }
    var isJoinMode by remember { mutableStateOf(false) }

    fun copyToClipboard(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Room Code", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "공유 코드가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "환영합니다, ${nickname}님!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (loading) {
                    CircularProgressIndicator(color = Color(0xFF6366F1))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (error.isNotEmpty()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (!isJoinMode) {
                    // Host Room Mode
                    Button(
                        onClick = {
                            viewModel.createRoom { code ->
                                // Optional auto copy or direct navigation
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White
                        )
                    ) {
                        Text("새로운 공유방 만들기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    if (roomCode.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "생성된 공유 코드",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = roomCode,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(onClick = { copyToClipboard(roomCode) }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "복사",
                                    tint = Color(0xFF6366F1)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToCalendar,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981), // Green 500
                                contentColor = Color.White
                            )
                        ) {
                            Text("캘린더 입장하기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(onClick = { isJoinMode = true }) {
                        Text("기존 공유방 참여하기", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Join Room Mode
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = {
                            // Auto-insert dash if length is 6 without it
                            var formatted = it.trim()
                            if (formatted.length == 6 && !formatted.contains("-")) {
                                formatted = "${formatted.substring(0, 3)}-${formatted.substring(3)}"
                            }
                            inputCode = formatted
                        },
                        label = { Text("6자리 공유 코드 (예: 483-291)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF6366F1),
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.joinRoom(inputCode) {
                                onNavigateToCalendar()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White
                        )
                    ) {
                        Text("참여하기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { isJoinMode = false }) {
                        Text("이전으로", color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        viewModel.resetPreferences()
                        onNavigateToNickname()
                    }
                ) {
                    Text("닉네임 변경 및 초기화", color = Color.Gray.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
    }
}
