package com.example.danallacalendar.estimate

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    viewModel: EstimateListViewModel = hiltViewModel()
) {
    val estimateList by viewModel.estimateList.collectAsStateWithLifecycle()
    val isShareEnabled by viewModel.isShareEnabled.collectAsStateWithLifecycle()
    val isGoogleDriveSaveEnabled by viewModel.isGoogleDriveSaveEnabled.collectAsStateWithLifecycle()
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    var selectedEstimate by remember { mutableStateOf<Estimate?>(null) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val rowHeight = screenHeight * 0.040f

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.updateGoogleAccount(account)
            viewModel.toggleGoogleDriveSaveEnabled(true)
        } catch (e: ApiException) {
            Log.e("EstimateListScreen", "Google Sign-In failed", e)
            viewModel.updateGoogleAccount(null)
            viewModel.toggleGoogleDriveSaveEnabled(false)
            Toast.makeText(context, "구글 로그인 실패 (코드: ${e.statusCode})", Toast.LENGTH_LONG).show()
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
                    // 1단: 뒤로가기 버튼(시작점 정렬) + "견적서 목록" 타이틀 (33.sp로 확대, 완벽한 가운데 정렬)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
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
                    }

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
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isGoogleDriveSaveEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (googleAccount == null) {
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

                    // 3단: "참여한 멤버와 공유" 텍스트 + 스위치 토글 (우측 정렬, 반응형 높이)
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

                    // 3.5단: "공유받은 견적서 자동 백업" 텍스트 + 스위치 토글 (우측 정렬, 반응형 높이)
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
                            text = "공유받은 견적서 자동 백업",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isAutoDriveSyncEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (googleAccount == null) {
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
                                    onDeleteClick = { viewModel.deleteEstimate(estimate) }
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
                }
            }
        }
    }
}

@Composable
fun EstimateItemCard(
    estimate: Estimate,
    onItemClick: () -> Unit,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
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
                    Text(
                        text = "이사일: ${estimate.moveDate} (${estimate.startTime})",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "출발지주소: ${estimate.departure.ifBlank { "정보 없음" }}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "전화번호: ${estimate.phoneNumber.ifBlank { "정보 없음" }}",
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

@Composable
fun LocalEstimateViewerDialog(
    estimate: Estimate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    val htmlContent = remember(estimate) {
        EstimateHtmlGenerator.generateEstimateHtml(context, estimate)
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
                        .height(56.dp)
                        .background(Color(0xFF1E1045))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                tint = Color.White,
                                contentDescription = "닫기"
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "견적서 상세 보기",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = {
                                val account = GoogleDriveHelper.getSignedInAccount(context)
                                if (account == null) {
                                    Toast.makeText(context, "설정 화면에서 구글 드라이브 로그인을 먼저 진행해주세요.", Toast.LENGTH_LONG).show()
                                } else {
                                    isUploading = true
                                    coroutineScope.launch {
                                        try {
                                            val jpgPath = EstimatePrintHelper.renderHtmlToJpg(context, htmlContent, estimate)
                                            if (jpgPath != null) {
                                                val jpgFile = java.io.File(jpgPath)
                                                val fileName = jpgFile.name
                                                val fileId = GoogleDriveHelper.uploadEstimateJpg(
                                                    context,
                                                    account,
                                                    jpgFile,
                                                    fileName,
                                                    estimate.estimateDate
                                                )
                                                if (fileId != null) {
                                                    Toast.makeText(context, "구글 드라이브 백업 완료", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "구글 드라이브 백업 실패", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "견적서 이미지 렌더링 실패", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("EstimateListScreen", "Direct Google Drive upload failed", e)
                                            Toast.makeText(context, "백업 중 오류 발생: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isUploading = false
                                        }
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    tint = Color.White,
                                    contentDescription = "구글 드라이브 백업"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {
                            EstimatePrintHelper.printEstimate(context, htmlContent, estimate)
                        }) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                tint = Color.White,
                                contentDescription = "인쇄/PDF저장"
                            )
                        }
                        IconButton(onClick = {
                            EstimatePrintHelper.shareEstimateAsJpg(context, htmlContent, estimate)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                tint = Color.White,
                                contentDescription = "공유"
                            )
                        }
                    }
                }

                // WebView
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
