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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateListScreen(
    onNavigateBack: () -> Unit,
    viewModel: EstimateListViewModel = hiltViewModel()
) {
    val estimateList by viewModel.estimateList.collectAsStateWithLifecycle()
    val isShareEnabled by viewModel.isShareEnabled.collectAsStateWithLifecycle()
    var selectedEstimate by remember { mutableStateOf<Estimate?>(null) }

    Scaffold(
        containerColor = Color(0xFF0F0825)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F0825))
        ) {
            // 2단 커스텀 상단 헤더 영역 (배경색: Color(0xFF1E1045))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1045))
                    .padding(bottom = 12.dp)
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

                // 2단: "참여 멤버와 공유" 텍스트 (18.sp로 변경) + 스위치 토글 (우측 정렬)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "참여 멤버와 공유",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isShareEnabled,
                        onCheckedChange = { viewModel.toggleShareEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFE040FB),
                            checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
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

@Composable
fun EstimateItemCard(
    estimate: Estimate,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val amountFormatted = java.text.NumberFormat.getNumberInstance(Locale.KOREA).format(estimate.amount)
    val totalCostFormatted = if (estimate.totalCost.isNotEmpty()) {
        val clean = estimate.totalCost.replace("₩", "").replace("만원", "").trim()
        "₩ $clean 만원"
    } else {
        "₩ ${amountFormatted}원"
    }

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
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "PDF 아이콘",
                    tint = Color(0xFFE040FB),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "${estimate.customerName} 고객님",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "이사일: ${estimate.moveDate} (${estimate.startTime})",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "종류: ${estimate.moveInfo.ifBlank { estimate.moveType }} | 비용: $totalCostFormatted",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
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

@Composable
fun LocalEstimateViewerDialog(
    estimate: Estimate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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

                    Row {
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
