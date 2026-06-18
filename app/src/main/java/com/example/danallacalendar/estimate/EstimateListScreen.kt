package com.example.danallacalendar.estimate

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEstimateCopy: (String) -> Unit,
    viewModel: EstimateListViewModel = hiltViewModel()
) {
    val estimateList by viewModel.estimateList.collectAsStateWithLifecycle()
    val isShareEnabled by viewModel.isShareEnabled.collectAsStateWithLifecycle()
    val isGoogleDriveSaveEnabled by viewModel.isGoogleDriveSaveEnabled.collectAsStateWithLifecycle()
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    var selectedEstimate by remember { mutableStateOf<Estimate?>(null) }
    var showInfoDialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val rowHeight = screenHeight * 0.040f

    var pendingSignInAction by remember { mutableStateOf<String?>(null) } // "save" or "auto"

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.updateGoogleAccount(account)
            if (pendingSignInAction == "auto") {
                viewModel.toggleAutoDriveSyncEnabled(true)
            } else {
                viewModel.toggleGoogleDriveSaveEnabled(true)
            }
        } catch (e: ApiException) {
            Log.e("EstimateListScreen", "Google Sign-In failed", e)
            viewModel.updateGoogleAccount(null)
            viewModel.toggleGoogleDriveSaveEnabled(false)
            viewModel.toggleAutoDriveSyncEnabled(false)
            Toast.makeText(context, "구글 로그인 실패 (코드: ${e.statusCode})", Toast.LENGTH_LONG).show()
        } finally {
            pendingSignInAction = null
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F0825)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F0825))
        ) {
            // 2단 커스텀 상단 헤더 영역 (배경색: Color(0xFF1E1045)) - 반응형 중앙 정렬 적용
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1045))
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 450.dp)
                ) {
                    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                    var isSearchMode by remember { mutableStateOf(false) }
                    val focusRequester = remember { FocusRequester() }

                    // 1단: 뒤로가기 버튼(시작점 정렬) + "견적서 목록" 타이틀 또는 검색창
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isSearchMode) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    tint = Color.White,
                                    contentDescription = "뒤로가기"
                                )
                            }
                            Text(
                                text = "견적서 목록",
                                fontSize = 33.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            IconButton(
                                onClick = { isSearchMode = true },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    tint = Color.White,
                                    contentDescription = "검색"
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = {
                                        Text(
                                            text = "고객명, 전화번호, 주소 검색...",
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "검색 아이콘",
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                if (searchQuery.isNotEmpty()) {
                                                    viewModel.setSearchQuery("")
                                                } else {
                                                    isSearchMode = false
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "닫기",
                                                tint = Color.White
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color(0xFFE040FB),
                                        focusedIndicatorColor = Color(0xFFE040FB),
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                )
                            }
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        }
                    }

                    // 1단: "참여한 멤버와 공유" 텍스트 + 스위치 토글 (우측 정렬, 반응형 높이)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "참여한 멤버와 공유",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showInfoDialog = "share" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                tint = Color.White.copy(alpha = 0.7f),
                                contentDescription = "정보",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isShareEnabled,
                            onCheckedChange = { viewModel.toggleShareEnabled(it) },
                            modifier = Modifier.scale(0.70f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE040FB),
                                checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2단: "내 구글드라이브에 저장" 텍스트 + 스위치 토글 (우측 정렬, 반응형 높이)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "내 구글드라이브에 저장",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showInfoDialog = "save" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                tint = Color.White.copy(alpha = 0.7f),
                                contentDescription = "정보",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isGoogleDriveSaveEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val hasPerm = GoogleDriveHelper.hasDrivePermission(context)
                                    if (googleAccount == null || !hasPerm) {
                                        pendingSignInAction = "save"
                                        googleSignInLauncher.launch(GoogleDriveHelper.getGoogleSignInClient(context).signInIntent)
                                    } else {
                                        viewModel.toggleGoogleDriveSaveEnabled(true)
                                    }
                                } else {
                                    viewModel.toggleGoogleDriveSaveEnabled(false)
                                }
                            },
                            modifier = Modifier.scale(0.70f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE040FB),
                                checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3단: "공유받은 견적서 자동 저장" 텍스트 + 스위치 토글 (우측 정렬, 반응형 높이)
                    val isAutoDriveSyncEnabled by viewModel.isAutoDriveSyncEnabled.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "공유받은 견적서 자동 저장",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showInfoDialog = "auto" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                tint = Color.White.copy(alpha = 0.7f),
                                contentDescription = "정보",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isAutoDriveSyncEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val hasPerm = GoogleDriveHelper.hasDrivePermission(context)
                                    if (googleAccount == null || !hasPerm) {
                                        pendingSignInAction = "auto"
                                        googleSignInLauncher.launch(GoogleDriveHelper.getGoogleSignInClient(context).signInIntent)
                                    } else {
                                        viewModel.toggleAutoDriveSyncEnabled(true)
                                    }
                                } else {
                                    viewModel.toggleAutoDriveSyncEnabled(false)
                                }
                            },
                            modifier = Modifier.scale(0.70f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE040FB),
                                checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    // 4단: 구글 드라이브 로그인된 경우 계정 정보 표시 및 로그아웃 버튼
                    googleAccount?.let { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "연동 계정: ${account.email ?: ""}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "로그아웃",
                                color = Color(0xFFE040FB),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    GoogleDriveHelper.getGoogleSignInClient(context).signOut().addOnCompleteListener {
                                        viewModel.updateGoogleAccount(null)
                                        viewModel.toggleGoogleDriveSaveEnabled(false)
                                        viewModel.toggleAutoDriveSyncEnabled(false)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 하단 리스트 영역 - 반응형 중앙 정렬 적용
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 450.dp)
                ) {
                    if (estimateList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "저장된 공유 견적서가 없습니다.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(estimateList, key = { it.id }) { estimate ->
                                EstimateItemCard(
                                    estimate = estimate,
                                    onItemClick = { selectedEstimate = estimate },
                                    onSyncClick = { viewModel.syncEstimate(estimate) },
                                    onDeleteClick = { viewModel.deleteEstimate(estimate) },
                                    onCopyClick = { pdf ->
                                        onNavigateToEstimateCopy(pdf.estimateJson)
                                    }
                                )
                            }
                        }
                    }
                    selectedEstimate?.let { estimate ->
                        LocalEstimateViewerDialog(
                            estimate = estimate,
                            onDismiss = { selectedEstimate = null }
                        )
                    }

                    showInfoDialog?.let { type ->
                        val title = when (type) {
                            "share" -> "참여한 멤버와 공유"
                            "save" -> "내 구글드라이브에 저장"
                            "auto" -> "공유받은 견적서 자동 저장"
                            else -> ""
                        }
                        val message = when (type) {
                            "share" -> "내가 작성한 견적서는 내 전화기에서만 사용 할 수 있습니다.\n내가 작성한 견적서를 같은 방에 있는 팀원들에게 서로 공유 하기 위해서는 스위치를 켜세요."
                            "save" -> "내가 견적서를 작성하면 내 전화기에 저장이 됩니다. \n스위치를 키게 되면 내가 견적서를 작성하고 완료할 때, 내구글 드라이브에 자동으로 저장합니다.\n구글 로그인 필수.\n로그인 된 계정의 구글드라이브에 \"월/일_견적서상 전화번호 뒷4자리\" 형식으로 자동 저장됩니다.\n견적서를 보관해야 하거나, 백업을 해야 할 시 스위치를 켜세요."
                            "auto" -> "다른 멤버가 공유하여 내 목록에 새로 추가된 견적서를 내 구글 드라이브에 자동으로 저장합니다.\n구글 로그인 필수.\n견적사원, 직원 등 다른 멤버가 견적서를 작성시 견적목록에 자동 추가된 견적서를 현재 로그인 된 계정의 구글드라이브에 \"월/일_견적서상 전화번호 뒷4자리\" 형식으로 자동 저장됩니다.\n주로 모든 견적서를 관리해야 하시는 분은 참여한 다른 멤버들이 \"참여한 멤버와 공유\" 스위치를 켠 상태에서 공유받은 견적서 자동 저장 스위치를 켜세요."
                            else -> ""
                        }

                        AlertDialog(
                            onDismissRequest = { showInfoDialog = null },
                            title = {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                            },
                            text = {
                                Text(
                                    text = message,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { showInfoDialog = null }) {
                                    Text("확인", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                                }
                            },
                            containerColor = Color(0xFF1E1045),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EstimateItemCard(
    estimate: Estimate,
    onItemClick: () -> Unit,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyClick: (com.example.danallacalendar.data.EstimatePdf) -> Unit
) {

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onItemClick() }
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1045).copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${estimate.customerName} 고객님",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val badgeColor = if (estimate.isSynced) Color(0xFFE040FB) else Color(0xFFFF9800)
                            val badgeText = if (estimate.isSynced) "공유 완료 ☁️" else "로컬 저장"
                            Surface(
                                color = badgeColor.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val createdTime = remember(estimate.createdAt) {
                            if (estimate.createdAt > 0L) {
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(estimate.createdAt))
                            } else ""
                        }
                        Text(
                            text = if (createdTime.isNotBlank())
                                "${estimate.estimateDate.ifBlank { "정보 없음" }} [$createdTime]"
                            else
                                estimate.estimateDate.ifBlank { "정보 없음" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (estimate.moveDate.isBlank()) "정보 없음" else "${estimate.moveDate} (${estimate.startTime})",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = estimate.departure.ifBlank { "정보 없음" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = estimate.phoneNumber.ifBlank { "정보 없음" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!estimate.isSynced) {
                        IconButton(onClick = onSyncClick) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "파이어베이스 동기화",
                                tint = Color(0xFFFF9800)
                            )
                        }
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = Color.Red.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun LocalEstimateViewerDialog(
    estimate: Estimate,
    onDismiss: () -> Unit,
    onEditClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    var showOverwriteConfirmDialog by remember { mutableStateOf(false) }
    var nextFileNameToUpload by remember { mutableStateOf<String?>(null) }

    val htmlContent = remember(estimate) {
        EstimateHtmlGenerator.generateEstimateHtml(context, estimate)
    }

    fun performUpload(targetFileName: String?) {
        isUploading = true
        coroutineScope.launch {
            try {
                val account = GoogleDriveHelper.getSignedInAccount(context) ?: return@launch
                val jpgPath = EstimatePrintHelper.renderHtmlToJpg(context, htmlContent, estimate)
                if (jpgPath != null) {
                    val jpgFile = java.io.File(jpgPath)
                    val dateStr = estimate.estimateDate.ifBlank { estimate.moveDate }
                    val dateParts = dateStr.split("-")
                    val monthDay = if (dateParts.size >= 3) "${dateParts[1]}-${dateParts[2]}" else "00-00"
                    val rawPhone = estimate.phoneNumber.replace(Regex("[^0-9]"), "")
                    val last4 = if (rawPhone.length >= 4) rawPhone.takeLast(4) else "0000"
                    val originalFileName = "${monthDay}_$last4.jpg"
                    val finalFileName = targetFileName ?: originalFileName

                    val result = GoogleDriveHelper.uploadEstimateJpgWithResult(
                        context,
                        account,
                        jpgFile,
                        finalFileName,
                        estimate.estimateDate
                    )
                    when (result) {
                        is GoogleDriveHelper.UploadResult.Success ->
                            Toast.makeText(context, "구글 드라이브 백업 완료", Toast.LENGTH_SHORT).show()
                        is GoogleDriveHelper.UploadResult.UserRecoverable ->
                            Toast.makeText(context, "구글 드라이브 로그인이 만료되었습니다.\n설정에서 다시 연결해 주세요.", Toast.LENGTH_LONG).show()
                        is GoogleDriveHelper.UploadResult.NoPermission ->
                            Toast.makeText(context, "구글 드라이브 권한이 없습니다.\n설정에서 다시 연결해 주세요.", Toast.LENGTH_LONG).show()
                        is GoogleDriveHelper.UploadResult.Failure -> {
                            android.util.Log.e("EstimateListScreen", "Drive upload failure: ${result.error}")
                            Toast.makeText(context, "백업 실패: 네트워크를 확인해 주세요.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "견적서 이미지 렌더링 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("EstimateListScreen", "Direct Google Drive upload failed", e)
                Toast.makeText(context, "백업 중 오류 발생: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isUploading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F0825)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 상단바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0xFF1E1045))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            tint = Color.White,
                            contentDescription = "닫기",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "견적서 상세 보기",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (onEditClick != null) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onEditClick()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                tint = Color(0xFFE040FB),
                                contentDescription = "수정",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "수정",
                                color = Color(0xFFE040FB),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 상단 액션 버튼 행 (아이콘 + 텍스트 정렬 개선 및 높이 축소)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1045))
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 구글 드라이브 백업
                    if (isUploading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFE040FB),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("백업 중...", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                val account = GoogleDriveHelper.getSignedInAccount(context)
                                if (account == null) {
                                    Toast.makeText(context, "설정 화면에서 구글 드라이브 로그인을 먼저 진행해주세요.", Toast.LENGTH_LONG).show()
                                } else {
                                    isUploading = true
                                    coroutineScope.launch {
                                        try {
                                            if (!GoogleDriveHelper.hasDrivePermission(context)) {
                                                Toast.makeText(context, "구글 드라이브 권한이 없습니다.\n설정에서 다시 연결해 주세요.", Toast.LENGTH_LONG).show()
                                                isUploading = false
                                                return@launch
                                            }
                                            val dateStr = estimate.estimateDate.ifBlank { estimate.moveDate }
                                            val dateParts = dateStr.split("-")
                                            val monthDay = if (dateParts.size >= 3) "${dateParts[1]}-${dateParts[2]}" else "00-00"
                                            val rawPhone = estimate.phoneNumber.replace(Regex("[^0-9]"), "")
                                            val last4 = if (rawPhone.length >= 4) rawPhone.takeLast(4) else "0000"
                                            val fileName = "${monthDay}_$last4.jpg"

                                            val existingId = GoogleDriveHelper.findExistingFileId(
                                                context, account, fileName, estimate.estimateDate
                                            )
                                            if (existingId != null) {
                                                val nextName = GoogleDriveHelper.findNextAvailableFileName(
                                                    context, account, fileName, estimate.estimateDate
                                                )
                                                if (nextName != null) {
                                                    nextFileNameToUpload = nextName
                                                    isUploading = false
                                                    showOverwriteConfirmDialog = true
                                                } else {
                                                    performUpload(null)
                                                }
                                            } else {
                                                performUpload(null)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("EstimateListScreen", "Drive check failed", e)
                                            isUploading = false
                                            performUpload(null)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AddToDrive,
                                    tint = Color.White,
                                    contentDescription = "구글 드라이브 백업",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("구글 백업", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    // 2. 인쇄 / PDF 저장
                    TextButton(
                        onClick = {
                            EstimatePrintHelper.printEstimate(context, htmlContent, estimate)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                tint = Color.White,
                                contentDescription = "인쇄/PDF저장",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("인쇄/PDF", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    // 3. 문자 발송 (MMS)
                    TextButton(
                        onClick = {
                            EstimatePrintHelper.shareEstimateAsJpg(context, htmlContent, estimate)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                tint = Color.White,
                                contentDescription = "문자 발송(MMS)",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("문자 발송", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }

                // 구분선
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )

                // WebView
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.textZoom = 100
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                            clearCache(true)
                            loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        webView.clearCache(true)
                        webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (showOverwriteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showOverwriteConfirmDialog = false
                            nextFileNameToUpload = null
                        },
                        title = { Text("이미 저장된 견적서 입니다.", color = Color.White) },
                        text = { Text("다시 저장 할까요?", color = Color.White) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showOverwriteConfirmDialog = false
                                    performUpload(nextFileNameToUpload)
                                    nextFileNameToUpload = null
                                }
                            ) {
                                Text("예", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showOverwriteConfirmDialog = false
                                    nextFileNameToUpload = null
                                }
                            ) {
                                Text("아니오", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF1E1045),
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
                }
            }
        }
    }
}
