package com.example.danallacalendar.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danallacalendar.data.Suggestion
import com.example.danallacalendar.data.SuggestionComment
import com.example.danallacalendar.ui.viewmodel.SuggestionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText

sealed interface SuggestionScreenState {
    object List : SuggestionScreenState
    object Write : SuggestionScreenState
    data class Edit(val suggestion: Suggestion) : SuggestionScreenState
    data class Detail(val suggestion: Suggestion) : SuggestionScreenState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionScreen(
    viewModel: SuggestionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var screenState by remember { mutableStateOf<SuggestionScreenState>(SuggestionScreenState.List) }
    
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val myUUID = remember { viewModel.getCurrentUserUUID() }
    
    var showGuidelinesDialog by remember { mutableStateOf(false) }
    var reportTargetUser by remember { mutableStateOf<Pair<String, String>?>(null) } // (userId, nickname)
    var isGuideBannerExpanded by remember { mutableStateOf(false) }

    var isAdmin by remember { mutableStateOf(viewModel.isAdmin()) }
    var showAdminAuthDialog by remember { mutableStateOf(false) }
    var adminPasscodeInput by remember { mutableStateOf("") }

    if (showGuidelinesDialog) {
        CommunityGuidelinesDialog(onDismissRequest = { showGuidelinesDialog = false })
    }

    if (showAdminAuthDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminAuthDialog = false
                adminPasscodeInput = ""
            },
            title = { Text(if (isAdmin) "👑 관리자 모드 (활성화됨)" else "🔑 관리자 인증") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isAdmin) {
                        Text("현재 건의함 최고 관리자 권한이 활성화되어 있습니다.\n\n[관리자 혜택]\n• 모든 건의글 및 댓글 삭제 가능\n• 글/댓글 작성 시 👑 관리자 뱃지 표시")
                    } else {
                        Text("건의함 관리자 암호를 입력하여 최고 관리자 권한을 활성화하세요.")
                        OutlinedTextField(
                            value = adminPasscodeInput,
                            onValueChange = { adminPasscodeInput = it },
                            label = { Text("관리자 암호 입력") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isAdmin) {
                            viewModel.setAdmin(false)
                            isAdmin = false
                            showAdminAuthDialog = false
                            adminPasscodeInput = ""
                            Toast.makeText(context, "관리자 권한이 해제되었습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            val trimmed = adminPasscodeInput.trim()
                            if (trimmed == "6872" || trimmed == "danalla6872" || trimmed == "7777" || trimmed == "danalla") {
                                viewModel.setAdmin(true)
                                isAdmin = true
                                showAdminAuthDialog = false
                                adminPasscodeInput = ""
                                Toast.makeText(context, "👑 최고 관리자 권한이 활성화되었습니다!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "관리자 암호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text(if (isAdmin) "권한 해제" else "인증하기")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAdminAuthDialog = false
                        adminPasscodeInput = ""
                    }
                ) {
                    Text("닫기")
                }
            }
        )
    }

    if (reportTargetUser != null) {
        AlertDialog(
            onDismissRequest = { reportTargetUser = null },
            title = { Text("사용자 신고") },
            text = { Text("${reportTargetUser?.second}님을 불량 사용자로 신고하시겠습니까?\n신고 시 신고 내용이 운영자에게 전송되어 검토 및 제재가 진행되며, 해당 사용자는 내 화면에서 즉시 차단 처리됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        reportTargetUser?.let { (id, nickname) ->
                            viewModel.reportUser(id, nickname, "부적절한 닉네임 및 악성 게시글 도배")
                        }
                        reportTargetUser = null
                        Toast.makeText(context, "신고 및 차단 처리되었습니다.", Toast.LENGTH_SHORT).show()
                        viewModel.stopObservingComments()
                        screenState = SuggestionScreenState.List
                    }
                ) {
                    Text("신고", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTargetUser = null }) {
                    Text("취소")
                }
            }
        )
    }

    // Handle system back press
    BackHandler {
        when (screenState) {
            is SuggestionScreenState.List -> onNavigateBack()
            is SuggestionScreenState.Write -> screenState = SuggestionScreenState.List
            is SuggestionScreenState.Edit -> screenState = SuggestionScreenState.Detail((screenState as SuggestionScreenState.Edit).suggestion)
            is SuggestionScreenState.Detail -> {
                viewModel.stopObservingComments()
                screenState = SuggestionScreenState.List
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (screenState) {
                            is SuggestionScreenState.List -> "건의함"
                            is SuggestionScreenState.Write -> "건의 등록"
                            is SuggestionScreenState.Edit -> "건의 수정"
                            is SuggestionScreenState.Detail -> "건의 상세"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (screenState) {
                                is SuggestionScreenState.List -> onNavigateBack()
                                is SuggestionScreenState.Write -> screenState = SuggestionScreenState.List
                                is SuggestionScreenState.Edit -> screenState = SuggestionScreenState.Detail((screenState as SuggestionScreenState.Edit).suggestion)
                                is SuggestionScreenState.Detail -> {
                                    viewModel.stopObservingComments()
                                    screenState = SuggestionScreenState.List
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdminAuthDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "관리자 설정",
                            tint = if (isAdmin) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = screenState) {
                is SuggestionScreenState.List -> {
                    val filteredSuggestions = remember(suggestions, blockedUsers, myUUID, isAdmin) {
                        suggestions.sortedWith(compareByDescending<Suggestion> { it.isPinned }.thenByDescending { it.createdAt }).filter { suggestion ->
                            val notReportedByMe = !suggestion.reportedByUserIds.contains(myUUID)
                            val notBlocked = !blockedUsers.contains(suggestion.authorId)
                            val reportCheck = if (isAdmin) true else !suggestion.isReported
                            reportCheck && notReportedByMe && notBlocked
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Collapsible Guide Banner
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isGuideBannerExpanded = !isGuideBannerExpanded }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "경고",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "건의함 이용안내 및 가이드라인",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isGuideBannerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isGuideBannerExpanded) "접기" else "펼치기",
                                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isGuideBannerExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                    ) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Text(
                                            text = "깨끗한 소통 공간을 위해 비방, 욕설, 허위사실 등 부적절한 내용은 예고 없이 삭제될 수 있습니다. 부적절한 글 발견 시 신고/차단 기능을 적극 활용해 주세요.",
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "👉 이용규칙(가이드라인) 전문 보기",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { showGuidelinesDialog = true }
                                        )
                                    }
                                }
                            }
                        }

                        if (filteredSuggestions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "등록된 건의사항이 없습니다.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(filteredSuggestions, key = { _, item -> item.id }) { index, item ->
                                    val postNumber = filteredSuggestions.size - index
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.observeComments(item.id)
                                                screenState = SuggestionScreenState.Detail(item)
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (item.isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                                        ),
                                        border = if (item.isPinned) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300)) else null,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.isPinned) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = "상단 고정",
                                                    tint = Color(0xFFE65100),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = "$postNumber. ",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (item.isPinned) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = item.title,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Action Button
                    FloatingActionButton(
                        onClick = { screenState = SuggestionScreenState.Write },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "추가")
                    }
                }
                is SuggestionScreenState.Write -> {
                    var title by remember { mutableStateOf("") }
                    var content by remember { mutableStateOf("") }
                    var agreeRules by remember { mutableStateOf(false) }
                    var isSubmitting by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("제목") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("내용") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        // Rule agreement checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = agreeRules,
                                onCheckedChange = { agreeRules = it }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "[필수] 비방, 욕설, 광고글 등 부적절한 게시글 작성 시 제재를 받을 수 있음에 동의합니다.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { agreeRules = !agreeRules }
                                )
                                Text(
                                    text = "이용규칙 전문 보기",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showGuidelinesDialog = true }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isSubmitting) return@Button
                                if (title.isBlank() || content.isBlank()) {
                                    Toast.makeText(context, "제목과 내용을 모두 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!agreeRules) {
                                    Toast.makeText(context, "이용규칙 동의 항목에 체크해 주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSubmitting = true
                                viewModel.addSuggestion(title, content,
                                    onSuccess = {
                                        isSubmitting = false
                                        Toast.makeText(context, "건의사항이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                                        screenState = SuggestionScreenState.List
                                    },
                                    onError = { errorMsg ->
                                        isSubmitting = false
                                        Toast.makeText(context, "등록 실패: $errorMsg", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("등록 중...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Text("등록하기", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is SuggestionScreenState.Edit -> {
                    val item = state.suggestion
                    var title by remember { mutableStateOf(item.title) }
                    var content by remember { mutableStateOf(item.content) }
                    var isSubmitting by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("제목") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("내용") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Button(
                            onClick = {
                                if (isSubmitting) return@Button
                                if (title.isBlank() || content.isBlank()) {
                                    Toast.makeText(context, "제목과 내용을 모두 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSubmitting = true
                                viewModel.updateSuggestion(
                                    suggestionId = item.id,
                                    newTitle = title,
                                    newContent = content,
                                    onSuccess = { updatedItem ->
                                        isSubmitting = false
                                        Toast.makeText(context, "게시글이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                                        viewModel.observeComments(updatedItem.id)
                                        screenState = SuggestionScreenState.Detail(updatedItem)
                                    },
                                    onError = { errorMsg ->
                                        isSubmitting = false
                                        Toast.makeText(context, "수정 실패: $errorMsg", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("수정 중...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Text("수정 완료", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is SuggestionScreenState.Detail -> {
                    val item = state.suggestion
                    val filteredComments = remember(comments, blockedUsers, myUUID) {
                        comments.filter {
                            !it.isReported && 
                            !it.reportedByUserIds.contains(myUUID) && 
                            !blockedUsers.contains(it.authorId)
                        }
                    }

                    var commentText by remember { mutableStateOf("") }
                    var showDetailMenu by remember { mutableStateOf(false) }
                    
                    var reportTargetPost by remember { mutableStateOf<Suggestion?>(null) }
                    var reportTargetComment by remember { mutableStateOf<SuggestionComment?>(null) }
                    var blockTargetUser by remember { mutableStateOf<Pair<String, String>?>(null) } // pair of (userId, nickname)
                    var deleteConfirmPost by remember { mutableStateOf(false) }

                    // Detail post menus dialogs
                    if (reportTargetPost != null) {
                        AlertDialog(
                            onDismissRequest = { reportTargetPost = null },
                            title = { Text("게시글 신고") },
                            text = { Text("이 게시글을 신고하시겠습니까?\n신고 시 해당 글은 본인 화면에서 즉시 숨겨지며, 누적 신고 시 비공개 처리됩니다.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        reportTargetPost?.let { viewModel.reportSuggestion(it) }
                                        reportTargetPost = null
                                        Toast.makeText(context, "신고 처리되었습니다.", Toast.LENGTH_SHORT).show()
                                        viewModel.stopObservingComments()
                                        screenState = SuggestionScreenState.List
                                    }
                                ) {
                                    Text("신고", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { reportTargetPost = null }) {
                                    Text("취소")
                                }
                            }
                        )
                    }

                    if (deleteConfirmPost) {
                        AlertDialog(
                            onDismissRequest = { deleteConfirmPost = false },
                            title = { Text("게시글 삭제") },
                            text = { Text("이 게시글을 정말로 삭제하시겠습니까?") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteSuggestion(item.id) {
                                            Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                            viewModel.stopObservingComments()
                                            screenState = SuggestionScreenState.List
                                        }
                                        deleteConfirmPost = false
                                    }
                                ) {
                                    Text("삭제", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { deleteConfirmPost = false }) {
                                    Text("취소")
                                }
                            }
                        )
                    }

                    if (reportTargetComment != null) {
                        AlertDialog(
                            onDismissRequest = { reportTargetComment = null },
                            title = { Text("댓글 신고") },
                            text = { Text("이 댓글을 신고하시겠습니까?\n신고 시 해당 댓글은 본인 화면에서 즉시 숨김 처리됩니다.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        reportTargetComment?.let { viewModel.reportComment(item.id, it) }
                                        reportTargetComment = null
                                        Toast.makeText(context, "신고 처리되었습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("신고", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { reportTargetComment = null }) {
                                    Text("취소")
                                }
                            }
                        )
                    }

                    if (blockTargetUser != null) {
                        AlertDialog(
                            onDismissRequest = { blockTargetUser = null },
                            title = { Text("사용자 차단") },
                            text = { Text("${blockTargetUser?.second}님을 차단하시겠습니까?\n차단 이후 이 사용자가 쓴 모든 건의글과 댓글이 화면에서 차단되어 보이지 않습니다.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        blockTargetUser?.first?.let { viewModel.blockUser(it) }
                                        blockTargetUser = null
                                        Toast.makeText(context, "차단 처리되었습니다.", Toast.LENGTH_SHORT).show()
                                        viewModel.stopObservingComments()
                                        screenState = SuggestionScreenState.List
                                    }
                                ) {
                                    Text("차단", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { blockTargetUser = null }) {
                                    Text("취소")
                                }
                            }
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Core Post Detail Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box {
                                        IconButton(onClick = { showDetailMenu = true }) {
                                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "더보기")
                                        }
                                        DropdownMenu(
                                            expanded = showDetailMenu,
                                            onDismissRequest = { showDetailMenu = false }
                                        ) {
                                            if (isAdmin) {
                                                if (item.isReported) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = "신고 해제 (게시글 복구)",
                                                                color = Color(0xFF2E7D32),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        },
                                                        onClick = {
                                                            showDetailMenu = false
                                                            viewModel.unreportSuggestion(item.id)
                                                            Toast.makeText(context, "게시글 신고가 해제되어 복구되었습니다.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PushPin,
                                                                contentDescription = "상단 고정",
                                                                tint = Color(0xFFE65100),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Text(
                                                                text = if (item.isPinned) "상단 고정 해제" else "상단 고정하기",
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFFE65100)
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        showDetailMenu = false
                                                        viewModel.togglePinSuggestion(item.id, item.isPinned)
                                                        Toast.makeText(context, if (item.isPinned) "상단 고정이 해제되었습니다." else "게시글이 상단에 고정되었습니다.", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                            if (item.authorId == myUUID || isAdmin) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = if (isAdmin && item.authorId != myUUID) "삭제하기 (관리자)" else "삭제하기",
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    },
                                                    onClick = {
                                                        showDetailMenu = false
                                                        deleteConfirmPost = true
                                                    }
                                                )
                                            } else {
                                                DropdownMenuItem(
                                                    text = { Text("신고하기", color = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        showDetailMenu = false
                                                        reportTargetPost = item
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("사용자 신고", color = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        showDetailMenu = false
                                                        reportTargetUser = item.authorId to item.authorNickname
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("작성자 차단") },
                                                    onClick = {
                                                        showDetailMenu = false
                                                        blockTargetUser = item.authorId to item.authorNickname
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.authorNickname,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (item.isAdmin || item.authorNickname.contains("관리자")) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFFF8E1)
                                        ) {
                                            Text(
                                                text = "👑 관리자",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    val formattedDate = remember(item.createdAt) {
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.createdAt))
                                    }
                                    Text(
                                        text = formattedDate,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                )

                                LinkifiedText(
                                    text = item.content,
                                    style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Comments Section
                        Text(
                            text = "댓글 (${filteredComments.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        // Comments List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredComments, key = { it.id }) { comment ->
                                var showCommentMenu by remember { mutableStateOf(false) }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = comment.authorNickname,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (comment.isAdmin || comment.authorNickname.contains("관리자")) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFFFF8E1)
                                                    ) {
                                                        Text(
                                                            text = "👑 관리자",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFE65100),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                val commentDate = remember(comment.createdAt) {
                                                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(comment.createdAt))
                                                }
                                                Text(
                                                    text = commentDate,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                            
                                            // Comment more options
                                            Box {
                                                IconButton(
                                                    onClick = { showCommentMenu = true },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "댓글 메뉴", modifier = Modifier.size(16.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = showCommentMenu,
                                                    onDismissRequest = { showCommentMenu = false }
                                                ) {
                                                    if (comment.authorId == myUUID) {
                                                        DropdownMenuItem(
                                                            text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                                                            onClick = {
                                                                showCommentMenu = false
                                                                viewModel.deleteComment(item.id, comment.id)
                                                                Toast.makeText(context, "댓글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    } else {
                                                        DropdownMenuItem(
                                                            text = { Text("신고", color = MaterialTheme.colorScheme.error) },
                                                            onClick = {
                                                                showCommentMenu = false
                                                                reportTargetComment = comment
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("사용자 신고", color = MaterialTheme.colorScheme.error) },
                                                            onClick = {
                                                                showCommentMenu = false
                                                                reportTargetUser = comment.authorId to comment.authorNickname
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("작성자 차단") },
                                                            onClick = {
                                                                showCommentMenu = false
                                                                blockTargetUser = comment.authorId to comment.authorNickname
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinkifiedText(
                                            text = comment.content,
                                            style = TextStyle(fontSize = 13.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Comment Input Bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    placeholder = { Text("따뜻한 한마디를 남겨주세요", fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 3,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        disabledBorderColor = Color.Transparent
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (commentText.isBlank()) return@IconButton
                                        viewModel.addComment(item.id, commentText,
                                            onSuccess = {
                                                commentText = ""
                                                Toast.makeText(context, "댓글이 작성되었습니다.", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { errorMsg ->
                                                Toast.makeText(context, "댓글 등록 실패: $errorMsg", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Send, contentDescription = "댓글 작성")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityGuidelinesDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "이용약관 및 커뮤니티 가이드라인",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "본 가이드라인은 안전하고 유익한 건의함 이용을 위해 모든 사용자가 준수해야 할 규칙을 명시합니다.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "1. 금지 행위 및 콘텐츠 종류\n" +
                            "- 욕설, 비방, 인신공격 및 타인을 비하하는 조롱성 표현\n" +
                            "- 음란성 콘텐츠, 혐오 표현, 성적 수치심을 유발하는 표현\n" +
                            "- 상업적 광고, 스팸, 동일 내용의 무단 도배성 글\n" +
                            "- 허위 사실 유포, 사칭 및 타인의 명예를 훼손하는 콘텐츠\n" +
                            "- 타인의 전화번호, 개인정보를 무단 기재 및 노출하는 행위",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "2. 위반 시 조치 및 제재 방침\n" +
                            "- 금지된 내용이 포함된 글이나 댓글은 다른 사용자의 신고 또는 관리자 모니터링을 통해 사전 예고 없이 즉시 삭제 조치됩니다.\n" +
                            "- 부적절한 게시물을 지속적으로 게재하거나 규칙을 반복하여 위반할 경우, 해당 계정(디바이스)의 글쓰기 권한이 영구 제한될 수 있습니다.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "3. 사용자 신고 및 차단 기능 안내\n" +
                            "- 모든 사용자는 부적절한 글이나 댓글 우측 상단의 메뉴를 통해 즉시 신고(운영자 전송) 및 해당 유저 차단(내 화면에서 영구 필터링)을 수행할 수 있습니다.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("확인", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    val uriHandler = LocalUriHandler.current

    val annotatedString = remember(text, linkColor) {
        buildAnnotatedString {
            append(text)
            val matcher = android.util.Patterns.WEB_URL.matcher(text)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val rawUrl = matcher.group()
                val validUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                    "https://$rawUrl"
                } else {
                    rawUrl
                }
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = start,
                    end = end
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = validUrl,
                    start = start,
                    end = end
                )
            }
        }
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = if (color != Color.Unspecified) color else style.color),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        // ignore malformed URLs
                    }
                }
        }
    )
}

