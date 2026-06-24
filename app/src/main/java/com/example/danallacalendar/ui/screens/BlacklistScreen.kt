package com.example.danallacalendar.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Edit
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.danallacalendar.ui.components.RecentCallsPickerDialog
import com.example.danallacalendar.ui.components.formatPhoneNumberForDetail
import com.example.danallacalendar.ui.viewmodel.BlacklistViewModel
import com.example.danallacalendar.data.BlacklistItem
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(
    onNavigateBack: () -> Unit,
    viewModel: BlacklistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val blacklistItems by viewModel.blacklistItems.collectAsStateWithLifecycle()

    var phoneInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var showRecentCallsDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BlacklistItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("B/L 관리", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "블랙리스트 추가",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Row 1: 전화번호 (좌측) + 최근통화목록 버튼 (우측)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactOutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = "전화번호",
                            placeholder = "010-0000-0000",
                            modifier = Modifier.weight(1f)
                        )

                        // 최근통화목록 버튼 (Far Right)
                        Button(
                            onClick = { showRecentCallsDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp) // Align height with text field
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "최근 통화",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("최근통화", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Row 2: 사유 (전체 너비)
                    CompactOutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = "사유",
                        placeholder = "사유 입력",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )

                    // Add Button
                    Button(
                        onClick = {
                            viewModel.addBlacklist(
                                phoneNumber = phoneInput,
                                reason = reasonInput,
                                onSuccess = {
                                    phoneInput = ""
                                    reasonInput = ""
                                    Toast.makeText(context, "블랙리스트에 추가되었습니다.", Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("추가하기", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // List Title
            Text(
                text = "등록된 블랙리스트 목록 (${blacklistItems.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Scrollable List
            if (blacklistItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "등록된 블랙리스트가 없습니다.",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(blacklistItems, key = { it.id }) { item ->
                        val formattedPhone = formatPhoneNumberForDetail(item.phoneNumber.replace(Regex("[^0-9]"), ""))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formattedPhone,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = item.reason,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { editingItem = item },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "수정",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteBlacklist(item) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "삭제",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
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

    if (showRecentCallsDialog) {
        RecentCallsPickerDialog(
            onCallSelected = { selectedNumber ->
                phoneInput = selectedNumber
                showRecentCallsDialog = false
            },
            onDismiss = {
                showRecentCallsDialog = false
            }
        )
    }

    val currentItem = editingItem
    if (currentItem != null) {
        EditBlacklistDialog(
            item = currentItem,
            onDismiss = { editingItem = null },
            onConfirm = { updatedPhone, updatedReason ->
                viewModel.updateBlacklist(
                    item = currentItem,
                    phoneNumber = updatedPhone,
                    reason = updatedReason,
                    onSuccess = {
                        editingItem = null
                        Toast.makeText(context, "수정되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    val interactionSource = remember { MutableInteractionSource() }
    val heightModifier = if (singleLine) {
        modifier.height(46.dp)
    } else {
        modifier.heightIn(min = 46.dp, max = 120.dp)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        interactionSource = interactionSource,
        modifier = heightModifier,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        ),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(label, fontSize = 11.sp) },
                placeholder = { Text(placeholder, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (singleLine) 4.dp else 10.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = OutlinedTextFieldDefaults.shape
                    )
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBlacklistDialog(
    item: BlacklistItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var phoneInput by remember { mutableStateOf(item.phoneNumber) }
    var reasonInput by remember { mutableStateOf(item.reason) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "블랙리스트 수정",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactOutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = "전화번호",
                    placeholder = "010-0000-0000",
                    modifier = Modifier.fillMaxWidth()
                )

                CompactOutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = "사유",
                    placeholder = "사유 입력",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(phoneInput, reasonInput) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("저장", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
