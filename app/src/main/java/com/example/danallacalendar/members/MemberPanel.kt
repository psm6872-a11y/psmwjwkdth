package com.example.danallacalendar.members

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// HSL Premium color mapper for avatars
private fun getAvatarColor(nickname: String): Color {
    val colors = listOf(
        Color(0xFFEF4444), // Red
        Color(0xFFF97316), // Orange
        Color(0xFFF59E0B), // Amber
        Color(0xFF10B981), // Emerald
        Color(0xFF06B6D4), // Cyan
        Color(0xFF3B82F6), // Blue
        Color(0xFF6366F1), // Indigo
        Color(0xFF8B5CF6), // Violet
        Color(0xFFEC4899), // Pink
        Color(0xFF14B8A6)  // Teal
    )
    val hash = nickname.hashCode()
    val index = abs(hash) % colors.size
    return colors[index]
}

@Composable
fun MemberPanel(
    visible: Boolean,
    members: List<Member>,
    currentDeviceUUID: String,
    isCreator: Boolean,
    onRemoveMember: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var memberToRemove by remember { mutableStateOf<Member?>(null) }

    if (memberToRemove != null) {
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("멤버 내보내기") },
            text = { Text("${memberToRemove?.nickname}님을 내보내시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        memberToRemove?.let { onRemoveMember(it.deviceUUID) }
                        memberToRemove = null
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("취소")
                }
            }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Consume clicks to prevent dismissing */ }
                            .padding(vertical = 24.dp, horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "참여 멤버 ${members.size}명",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))

                            // Member List
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(members) { member ->
                                    val isMe = member.deviceUUID == currentDeviceUUID
                                    val displayName = if (isMe) "${member.nickname} (나)" else member.nickname
                                    val firstChar = member.nickname.firstOrNull()?.toString() ?: "?"
                                    val avatarColor = getAvatarColor(member.nickname)

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Avatar
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(avatarColor)
                                            ) {
                                                Text(
                                                    text = firstChar,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Info
                                            Column {
                                                Text(
                                                    text = displayName,
                                                    fontSize = 15.sp,
                                                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "참여중",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF34C759)) // Green dot
                                                    )
                                                }
                                            }
                                        }

                                        if (isCreator && !isMe) {
                                            IconButton(
                                                onClick = { memberToRemove = member }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "내보내기",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Close Button
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = "닫기",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
