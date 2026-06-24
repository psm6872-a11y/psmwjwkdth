package com.example.danallacalendar.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.estimate.Estimate
import com.example.danallacalendar.ui.viewmodel.StatisticsViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    isCreator: Boolean = false,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val allEvents by viewModel.allEvents.collectAsStateWithLifecycle()
    val allEstimates by viewModel.allEstimates.collectAsStateWithLifecycle()

    val currentCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(currentCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(currentCal.get(Calendar.MONTH)) } // 0-11

    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = remember(isCreator) {
        if (isCreator) {
            listOf("견적", "계약", "운영/짐", "거리/지역", "연간 매출")
        } else {
            listOf("견적", "계약", "운영/짐", "거리/지역")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("통계 대시보드", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedMonth == 0) {
                                    selectedMonth = 11
                                    selectedYear -= 1
                                } else {
                                    selectedMonth -= 1
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "이전 달"
                            )
                        }
                        Text(
                            text = "${selectedYear}년 ${selectedMonth + 1}월",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(
                            onClick = {
                                if (selectedMonth == 11) {
                                    selectedMonth = 0
                                    selectedYear += 1
                                } else {
                                    selectedMonth += 1
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "다음 달"
                            )
                        }
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
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (allEstimates.isEmpty() && allEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator()
                            Text("데이터 로드 중...", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> EstimateTabContent(allEstimates, allEvents, selectedYear, selectedMonth)
                        1 -> ContractTabContent(allEstimates, allEvents, selectedYear, selectedMonth)
                        2 -> OperationsCargoTabContent(allEstimates, allEvents, selectedYear, selectedMonth)
                        3 -> DistanceRegionTabContent(allEstimates, selectedYear, selectedMonth)
                        4 -> {
                            if (isCreator) {
                                AnnualRevenueTabContent(allEstimates, allEvents, selectedYear)
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("해당 화면을 볼 수 있는 권한이 없습니다.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------
// Tabs Contents
// ----------------------------------------

@Composable
fun EstimateTabContent(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int) {
    val stats = remember(estimates, events, year, month) {
        computeEstimateContractStats(estimates, events, year, month)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Conversion Rate Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("견적 대비 계약 전환율", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format("%.1f", stats.conversionRate)}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        DonutChart(
                            percentage = stats.conversionRate.toFloat(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        item {
            // Stats Row: Total Requests & Average Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("총 견적 요청 수", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${stats.totalEstimates}건", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("평균 견적 금액", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(formatManwon(stats.averageEstimateAmount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            // Chart: Monthly Request Trend (Split into Jan-Jun / Jul-Dec)
            val monthlyData = stats.monthlyRequests.entries
                .filter { it.key.startsWith(year.toString()) }
                .sortedBy { it.key }
            
            val firstHalf = monthlyData.take(6).map { it.value.toFloat() to it.key.substringAfter("-") + "월" }
            val secondHalf = monthlyData.drop(6).take(6).map { it.value.toFloat() to it.key.substringAfter("-") + "월" }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${year}년 월별 견적 요청 추이", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (firstHalf.isNotEmpty()) {
                        BarChart(
                            data = firstHalf,
                            barColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                        )
                    } else {
                        Text("표시할 차트 데이터가 없습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (secondHalf.isNotEmpty()) {
                        BarChart(
                            data = secondHalf,
                            barColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                        )
                    } else {
                        Text("표시할 차트 데이터가 없습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        item {
            // Weekly Inquiries for the selected month
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("주별 견적 요청 추이 (선택된 월)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val weekOrder = listOf("1주차", "2주차", "3주차", "4주차", "5주차")
                    val chartData = stats.weeklyRequests.entries
                        .sortedBy { weekOrder.indexOf(it.key) }
                        .map { it.value.toFloat() to it.key }

                    if (chartData.isNotEmpty()) {
                        BarChart(
                            data = chartData,
                            barColor = Color(0xFFFF9800),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    } else {
                        Text("표시할 차트 데이터가 없습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        item {
            // Day of Week Requests for selected year/month
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("요일별 견적 요청 추이", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val dayLabels = listOf("일", "월", "화", "수", "목", "금", "토")
                    val chartData = dayLabels.mapIndexed { index, label ->
                        val count = stats.dayOfWeekRequests[index + 1] ?: 0
                        count.toFloat() to label
                    }

                    BarChart(
                        data = chartData,
                        barColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }
        }

        item {
            // Hourly Heatmap
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("시간대별 문의 현황", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val timeRanges = listOf(
                        "00-06시" to stats.hourlyRequests.filter { it.key in 0..5 }.values.sum(),
                        "06-12시" to stats.hourlyRequests.filter { it.key in 6..11 }.values.sum(),
                        "12-18시" to stats.hourlyRequests.filter { it.key in 12..17 }.values.sum(),
                        "18-24시" to stats.hourlyRequests.filter { it.key in 18..23 }.values.sum()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        timeRanges.forEach { range ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(range.first, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    val pct = if (stats.totalEstimates > 0) range.second.toFloat() / stats.totalEstimates else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("${range.second}건", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun ContractTabContent(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int) {
    val stats = remember(estimates, events, year, month) {
        computeEstimateContractStats(estimates, events, year, month)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Conversion Rate Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("견적 대비 계약 전환율", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format("%.1f", stats.conversionRate)}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        DonutChart(
                            percentage = stats.conversionRate.toFloat(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        item {
            // Stats Row: Total Requests & Average Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("총 견적 요청 수", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${stats.totalEstimates}건", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("평균 견적 금액", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(formatManwon(stats.averageEstimateAmount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            // Team Contract Counts
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("팀별 계약 건수", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    val maxTeamVal = (stats.teamMoveCounts.values.maxOrNull() ?: 1).toFloat()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (teamId in 1..5) {
                            val count = stats.teamMoveCounts[teamId] ?: 0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${teamId}팀", fontSize = 13.sp, modifier = Modifier.width(50.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    val pct = if (maxTeamVal > 0) count.toFloat() / maxTeamVal else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct)
                                            .background(Color(0xFF4CAF50))
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("${count}건", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            // Avg Price by Tonnage
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("톤수별 평균 이사 단가", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (ton in 1..6) {
                            val avgPrice = stats.tonnageAvgPrices[ton] ?: 0L
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${ton}톤", fontSize = 14.sp)
                                Text(
                                    text = if (avgPrice > 0L) formatCurrency(avgPrice) else "데이터 없음",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (avgPrice > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun OperationsCargoTabContent(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int) {
    val stats = remember(estimates, events, year, month) {
        computeOperationsCargoStats(estimates, events, year, month)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Stats Row: Holidays & Growth
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("주말/공휴일 이사 비율", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("${String.format("%.1f", stats.holidayWeekendRatio)}%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("전년 동기 대비 성장률", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(6.dp))
                        val sign = if (stats.yoyGrowthRate >= 0) "+" else ""
                        Text("$sign${String.format("%.1f", stats.yoyGrowthRate)}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (stats.yoyGrowthRate >= 0) Color(0xFF4CAF50) else Color(0xFFF44336))
                    }
                }
            }
        }

        item {
            // Day of week moves chart
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("요일별 이사 건수", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val dayLabels = listOf("일", "월", "화", "수", "목", "금", "토")
                    val chartData = dayLabels.mapIndexed { index, label ->
                        val count = stats.dayOfWeekMoveCounts[index + 1] ?: 0
                        count.toFloat() to label
                    }

                    BarChart(
                        data = chartData,
                        barColor = Color(0xFF9C27B0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }
        }

        item {
            // Month moves chart (Operations)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("월별 이사 건수 (최근 6개월)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val chartData = stats.monthlyMoveCounts.entries
                        .sortedBy { it.key }
                        .takeLast(6)
                        .map { it.value.toFloat() to it.key.substringAfter("-") + "월" }

                    if (chartData.isNotEmpty()) {
                        BarChart(
                            data = chartData,
                            barColor = Color(0xFF3F51B5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    } else {
                        Text("표시할 차트 데이터가 없습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        item {
            // Cargo Size Ratio (Donut Chart representation)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("짐 규모별 비율", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    val total = stats.cargoTonnageCounts.values.sum().toFloat()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                var startAngle = -90f
                                val slices = listOf(
                                    Triple(stats.cargoTonnageCounts["1-2톤"] ?: 0, Color(0xFF2196F3), "1-2톤"),
                                    Triple(stats.cargoTonnageCounts["3-4톤"] ?: 0, Color(0xFFFF9800), "3-4톤"),
                                    Triple(stats.cargoTonnageCounts["5-6톤"] ?: 0, Color(0xFF4CAF50), "5-6톤"),
                                    Triple(stats.cargoTonnageCounts["기타"] ?: 0, Color(0xFF9E9E9E), "기타")
                                )
                                slices.forEach { slice ->
                                    val sweep = if (total > 0f) (slice.first.toFloat() / total) * 360f else 0f
                                    if (sweep > 0f) {
                                        drawArc(
                                            color = slice.second,
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt)
                                        )
                                        startAngle += sweep
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val items = listOf(
                                Triple("1-2톤 (소형)", stats.cargoTonnageCounts["1-2톤"] ?: 0, Color(0xFF2196F3)),
                                Triple("3-4톤 (중형)", stats.cargoTonnageCounts["3-4톤"] ?: 0, Color(0xFFFF9800)),
                                Triple("5-6톤 (대형)", stats.cargoTonnageCounts["5-6톤"] ?: 0, Color(0xFF4CAF50)),
                                Triple("기타", stats.cargoTonnageCounts["기타"] ?: 0, Color(0xFF9E9E9E))
                            )
                            items.forEach { item ->
                                val pct = if (total > 0f) (item.second.toFloat() / total) * 100 else 0f
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(item.third))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${item.first}: ${String.format("%.1f", pct)}% (${item.second}건)", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun DistanceRegionTabContent(estimates: List<Estimate>, year: Int, month: Int) {
    val stats = remember(estimates, year, month) {
        computeDistanceRegionStats(estimates, year, month)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Distance card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("평균 이사 거리 (추정치)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.1f", stats.averageDistance)} km",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            // Region transfer flow
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("지역간 이사 현황", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (stats.regionFlows.isEmpty()) {
                        Text("이사 이동 데이터가 부족합니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            stats.regionFlows.take(10).forEach { flow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(flow.first.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(" → ", color = MaterialTheme.colorScheme.outline)
                                        Text(flow.first.second, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text("${flow.second}건", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun AnnualRevenueTabContent(estimates: List<Estimate>, events: List<Event>, year: Int) {
    val stats = remember(estimates, events, year) {
        computeAnnualRevenueStats(estimates, events, year)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Revenue Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("연간 누적 매출 (계약 완료 기준)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatCurrency(stats.annualTotalRevenue),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            // Monthly Revenue List
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("월별 누적 매출 현황", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
                    val monthlyList = stats.monthlyRevenues.entries
                        .filter { it.key.startsWith(currentYear) }
                        .sortedBy { it.key }

                    if (monthlyList.isEmpty()) {
                        Text("올해 매출 데이터가 존재하지 않습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            monthlyList.forEach { entry ->
                                val monthStr = entry.key.substringAfter("-") + "월"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(monthStr, fontSize = 14.sp)
                                    Text(formatCurrency(entry.value), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// ----------------------------------------
// Custom Canvas Charts
// ----------------------------------------

@Composable
fun DonutChart(
    percentage: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Draw track
        drawCircle(
            color = color.copy(alpha = 0.1f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Draw fill arc
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = (percentage / 100f) * 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun BarChart(
    data: List<Pair<Float, String>>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxVal = data.maxOf { it.first }
        val spacing = 20.dp.toPx()
        val textHeight = 20.dp.toPx()
        val topPadding = 16.dp.toPx()
        val chartHeight = size.height - textHeight - topPadding - 8.dp.toPx()
        val barWidth = (size.width - (spacing * (data.size + 1))) / data.size

        data.forEachIndexed { index, pair ->
            val left = spacing + index * (barWidth + spacing)
            val pct = if (maxVal > 0f) pair.first / maxVal else 0f
            val height = chartHeight * pct
            val top = chartHeight - height + topPadding

            // Draw Bar
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Draw count above bar
            val valuePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            drawContext.canvas.nativeCanvas.drawText(
                pair.first.toInt().toString(),
                left + barWidth / 2,
                top - 4.dp.toPx(),
                valuePaint
            )

            // Draw text label
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                pair.second,
                left + barWidth / 2,
                size.height - 4.dp.toPx(),
                paint
            )
        }
    }
}

// ----------------------------------------
// Data Calculations Helpers & Stats Models
// ----------------------------------------

fun formatCurrency(amount: Long): String {
    val df = DecimalFormat("#,###")
    return "${df.format(amount)}원"
}

fun formatManwon(amount: Long): String {
    val manwon = amount / 10000.0
    val df = if (amount % 10000 == 0L) {
        DecimalFormat("#,###")
    } else {
        DecimalFormat("#,###.#")
    }
    return "${df.format(manwon)}만원"
}

// Stats models
data class EstimateContractStats(
    val totalEstimates: Int,
    val conversionRate: Double,
    val averageEstimateAmount: Long,
    val dayOfWeekRequests: Map<Int, Int>,
    val weeklyRequests: Map<String, Int>,
    val monthlyRequests: Map<String, Int>,
    val hourlyRequests: Map<Int, Int>,
    val teamMoveCounts: Map<Int, Int>,
    val tonnageAvgPrices: Map<Int, Long>
)

data class OperationsCargoStats(
    val holidayWeekendRatio: Double,
    val yoyGrowthRate: Double,
    val dayOfWeekMoveCounts: Map<Int, Int>,
    val monthlyMoveCounts: Map<String, Int>,
    val cargoTonnageCounts: Map<String, Int>
)

data class DistanceRegionStats(
    val averageDistance: Double,
    val regionFlows: List<Pair<Pair<String, String>, Int>>
)

data class AnnualRevenueStats(
    val annualTotalRevenue: Long,
    val monthlyRevenues: Map<String, Long>
)

// Helper region parsers
fun getRegionName(address: String): String {
    val cleanAddress = address.split("|").firstOrNull()?.trim() ?: ""
    if (cleanAddress.isEmpty()) return "미지정"
    val firstWord = cleanAddress.split(" ").firstOrNull() ?: "미지정"
    return when {
        firstWord.startsWith("서울") -> "서울"
        firstWord.startsWith("경기") -> "경기"
        firstWord.startsWith("인천") -> "인천"
        firstWord.startsWith("강원") -> "강원"
        firstWord.startsWith("충북") || firstWord.startsWith("충청북") -> "충북"
        firstWord.startsWith("충남") || firstWord.startsWith("충청남") -> "충남"
        firstWord.startsWith("대전") -> "대전"
        firstWord.startsWith("경북") || firstWord.startsWith("경상북") -> "경북"
        firstWord.startsWith("경남") || firstWord.startsWith("경상남") -> "경남"
        firstWord.startsWith("부산") -> "부산"
        firstWord.startsWith("울산") -> "울산"
        firstWord.startsWith("대구") -> "대구"
        firstWord.startsWith("전북") || firstWord.startsWith("전라북") -> "전북"
        firstWord.startsWith("전남") || firstWord.startsWith("전라남") -> "전남"
        firstWord.startsWith("광주") -> "광주"
        firstWord.startsWith("세종") -> "세종"
        firstWord.startsWith("제주") -> "제주"
        else -> firstWord.take(2)
    }
}

fun estimateDistance(fromRegion: String, toRegion: String): Double {
    if (fromRegion == "미지정" || toRegion == "미지정") return 0.0
    if (fromRegion == toRegion) {
        return when (fromRegion) {
            "서울" -> 12.0
            "경기" -> 25.0
            "인천" -> 15.0
            else -> 18.0
        }
    }
    val key1 = fromRegion
    val key2 = toRegion
    val matches = mapOf(
        setOf("서울", "경기") to 35.0,
        setOf("서울", "인천") to 40.0,
        setOf("경기", "인천") to 45.0,
        setOf("서울", "부산") to 390.0,
        setOf("서울", "대구") to 290.0,
        setOf("서울", "대전") to 160.0,
        setOf("서울", "광주") to 270.0,
        setOf("경기", "부산") to 370.0,
        setOf("경기", "대구") to 270.0,
        setOf("경기", "대전") to 140.0,
        setOf("경기", "광주") to 250.0
    )
    val match = matches.entries.find { it.key.contains(key1) && it.key.contains(key2) }
    return match?.value ?: 200.0
}

fun parseTonnage(volume: String): Double {
    val clean = volume.replace(Regex("[^0-9.]"), "")
    return clean.toDoubleOrNull() ?: 0.0
}

// ----------------------------------------
// Computation Functions
// ----------------------------------------

fun computeEstimateContractStats(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int): EstimateContractStats {
    val targetCal = Calendar.getInstance()
    val filteredEstimates = estimates.filter { est ->
        targetCal.timeInMillis = est.createdAt
        targetCal.get(Calendar.YEAR) == year && targetCal.get(Calendar.MONTH) == month
    }
    
    val total = filteredEstimates.size
    val contractedEstimateIds = events.mapNotNull { it.linkedEstimateId }.filter { it.isNotBlank() }.toSet()
    val contractedCount = filteredEstimates.count { it.id in contractedEstimateIds }
    
    val conversion = if (total > 0) (contractedCount.toDouble() / total) * 100 else 0.0
    
    val eventsByEstimate = events.groupBy { it.linkedEstimateId }
    val visitCompletedEstimates = filteredEstimates.filter { est ->
        val estEvents = eventsByEstimate[est.id] ?: emptyList()
        estEvents.isNotEmpty() && !estEvents.any { it.isAllDay }
    }
    val totalVisitCompletedCost = visitCompletedEstimates.sumOf { est ->
        val costStr = est.totalCost.replace(Regex("[^0-9]"), "")
        costStr.toLongOrNull() ?: 0L
    }
    val avgAmount = if (visitCompletedEstimates.isNotEmpty()) {
        totalVisitCompletedCost / visitCompletedEstimates.size
    } else {
        0L
    }

    // Inquiries dayOfWeek/monthly/weekly
    val dayOfWeekRequests = mutableMapOf<Int, Int>()
    for (d in 1..7) {
        dayOfWeekRequests[d] = 0
    }
    val monthly = mutableMapOf<String, Int>()
    val weekly = mutableMapOf<String, Int>()
    val hourly = mutableMapOf<Int, Int>()

    val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.KOREAN)
    
    // Initialize weekly map keys
    weekly["1주차"] = 0
    weekly["2주차"] = 0
    weekly["3주차"] = 0
    weekly["4주차"] = 0
    val maxDays = targetCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    if (maxDays > 28) {
        weekly["5주차"] = 0
    }

    filteredEstimates.forEach { est ->
        val date = Date(est.createdAt)
        val cal = Calendar.getInstance().apply { time = date }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        dayOfWeekRequests[dayOfWeek] = (dayOfWeekRequests[dayOfWeek] ?: 0) + 1
        
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        hourly[hour] = (hourly[hour] ?: 0) + 1

        val day = cal.get(Calendar.DAY_OF_MONTH)
        val weekKey = when {
            day <= 7 -> "1주차"
            day <= 14 -> "2주차"
            day <= 21 -> "3주차"
            day <= 28 -> "4주차"
            else -> "5주차"
        }
        weekly[weekKey] = (weekly[weekKey] ?: 0) + 1
    }

    // Monthly requests for the selected year (Jan - Dec)
    val yearStr = year.toString()
    for (m in 1..12) {
        val mKey = "$yearStr-${String.format("%02d", m)}"
        monthly[mKey] = 0
    }
    
    estimates.filter { est ->
        val cal = Calendar.getInstance().apply { timeInMillis = est.createdAt }
        cal.get(Calendar.YEAR) == year
    }.forEach { est ->
        val monthStr = sdfMonth.format(Date(est.createdAt))
        monthly[monthStr] = (monthly[monthStr] ?: 0) + 1
    }

    // Team move counts in selected year/month
    val filteredEvents = events.filter { evt ->
        targetCal.timeInMillis = evt.startMillis
        targetCal.get(Calendar.YEAR) == year && targetCal.get(Calendar.MONTH) == month
    }
    val teamCounts = mutableMapOf<Int, Int>()
    filteredEvents.forEach { evt ->
        if (evt.teamId != null) {
            teamCounts[evt.teamId] = (teamCounts[evt.teamId] ?: 0) + 1
        }
    }

    // Tonnage average prices
    val tonnagePrices = mutableMapOf<Int, MutableList<Long>>()
    filteredEstimates.filter { it.id in contractedEstimateIds }.forEach { est ->
        val ton = parseTonnage(est.totalVolume).toInt()
        if (ton in 1..6) {
            if (tonnagePrices[ton] == null) tonnagePrices[ton] = mutableListOf()
            tonnagePrices[ton]?.add(est.amount)
        }
    }
    val tonnageAvg = tonnagePrices.mapValues { entry ->
        if (entry.value.isNotEmpty()) entry.value.average().toLong() else 0L
    }

    return EstimateContractStats(
        totalEstimates = total,
        conversionRate = conversion,
        averageEstimateAmount = avgAmount,
        dayOfWeekRequests = dayOfWeekRequests,
        weeklyRequests = weekly,
        monthlyRequests = monthly,
        hourlyRequests = hourly,
        teamMoveCounts = teamCounts,
        tonnageAvgPrices = tonnageAvg
    )
}

fun computeOperationsCargoStats(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int): OperationsCargoStats {
    val targetCal = Calendar.getInstance()
    
    val confirmedEvents = events.filter { evt ->
        evt.isAllDay && evt.teamId != null &&
        targetCal.apply { timeInMillis = evt.startMillis }.get(Calendar.YEAR) == year &&
        targetCal.get(Calendar.MONTH) == month
    }
    val totalMoves = confirmedEvents.size

    var holidayWeekendCount = 0
    val dayOfWeekCounts = mutableMapOf<Int, Int>()
    val monthlyMoveCounts = mutableMapOf<String, Int>()

    val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.KOREAN)
    val cal = Calendar.getInstance()

    confirmedEvents.forEach { evt ->
        cal.timeInMillis = evt.startMillis
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        dayOfWeekCounts[dow] = (dayOfWeekCounts[dow] ?: 0) + 1

        val isHoliday = getKoreanHolidayName(evt.startMillis) != null
        val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        if (isHoliday || isWeekend) {
            holidayWeekendCount++
        }
    }

    val holidayWeekendRatio = if (totalMoves > 0) (holidayWeekendCount.toDouble() / totalMoves) * 100 else 0.0

    // Monthly moves for 6 months ending in selected month
    val endCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
    }
    val startCal = Calendar.getInstance().apply {
        timeInMillis = endCal.timeInMillis
        add(Calendar.MONTH, -5)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
    }
    events.filter { it.isAllDay && it.teamId != null && it.startMillis in startCal.timeInMillis..endCal.timeInMillis }.forEach { evt ->
        val monthStr = sdfMonth.format(Date(evt.startMillis))
        monthlyMoveCounts[monthStr] = (monthlyMoveCounts[monthStr] ?: 0) + 1
    }
    val fillCal = Calendar.getInstance().apply { timeInMillis = startCal.timeInMillis }
    for (i in 0..5) {
        val key = sdfMonth.format(fillCal.time)
        if (!monthlyMoveCounts.containsKey(key)) {
            monthlyMoveCounts[key] = 0
        }
        fillCal.add(Calendar.MONTH, 1)
    }

    // YoY Growth calculation
    val allConfirmedEvents = events.filter { it.isAllDay && it.teamId != null }
    val currentMonthCount = allConfirmedEvents.count {
        cal.timeInMillis = it.startMillis
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }
    val lastYearMonthCount = allConfirmedEvents.count {
        cal.timeInMillis = it.startMillis
        cal.get(Calendar.YEAR) == (year - 1) && cal.get(Calendar.MONTH) == month
    }

    val yoyGrowth = if (lastYearMonthCount > 0) {
        ((currentMonthCount.toDouble() - lastYearMonthCount) / lastYearMonthCount) * 100
    } else {
        0.0
    }

    // Cargo size ratios
    val cargoCounts = mutableMapOf(
        "1-2톤" to 0,
        "3-4톤" to 0,
        "5-6톤" to 0,
        "기타" to 0
    )
    val filteredEstimates = estimates.filter { est ->
        cal.timeInMillis = est.createdAt
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }
    filteredEstimates.forEach { est ->
        val ton = parseTonnage(est.totalVolume)
        when {
            ton <= 0.0 -> cargoCounts["기타"] = (cargoCounts["기타"] ?: 0) + 1
            ton <= 2.0 -> cargoCounts["1-2톤"] = (cargoCounts["1-2톤"] ?: 0) + 1
            ton <= 4.0 -> cargoCounts["3-4톤"] = (cargoCounts["3-4톤"] ?: 0) + 1
            ton <= 6.0 -> cargoCounts["5-6톤"] = (cargoCounts["5-6톤"] ?: 0) + 1
            else -> cargoCounts["기타"] = (cargoCounts["기타"] ?: 0) + 1
        }
    }

    return OperationsCargoStats(
        holidayWeekendRatio = holidayWeekendRatio,
        yoyGrowthRate = yoyGrowth,
        dayOfWeekMoveCounts = dayOfWeekCounts,
        monthlyMoveCounts = monthlyMoveCounts,
        cargoTonnageCounts = cargoCounts
    )
}

fun computeDistanceRegionStats(estimates: List<Estimate>, year: Int, month: Int): DistanceRegionStats {
    var totalDistance = 0.0
    var count = 0
    val flows = mutableMapOf<Pair<String, String>, Int>()
    val cal = Calendar.getInstance()

    estimates.filter { est ->
        cal.timeInMillis = est.createdAt
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }.forEach { est ->
        if (est.departure.isNotBlank() && est.destination.isNotBlank()) {
            val depReg = getRegionName(est.departure)
            val destReg = getRegionName(est.destination)
            if (depReg != "미지정" && destReg != "미지정") {
                val dist = estimateDistance(depReg, destReg)
                totalDistance += dist
                count++

                val pair = depReg to destReg
                flows[pair] = (flows[pair] ?: 0) + 1
            }
        }
    }

    val avgDist = if (count > 0) totalDistance / count else 0.0
    val sortedFlows = flows.entries.sortedByDescending { it.value }.map { it.key to it.value }

    return DistanceRegionStats(
        averageDistance = avgDist,
        regionFlows = sortedFlows
    )
}

fun computeAnnualRevenueStats(estimates: List<Estimate>, events: List<Event>, year: Int): AnnualRevenueStats {
    val yearStr = year.toString()
    val contractedEstimateIds = events.mapNotNull { it.linkedEstimateId }.filter { it.isNotBlank() }.toSet()
    
    val yearContractedEstimates = estimates.filter {
        it.id in contractedEstimateIds && it.moveDate.startsWith(yearStr)
    }

    val annualTotal = yearContractedEstimates.sumOf { it.amount }

    val monthlyRevenues = mutableMapOf<String, Long>()

    estimates.filter { it.id in contractedEstimateIds && it.moveDate.startsWith(yearStr) }.forEach { est ->
        try {
            val dateParts = est.moveDate.split("-")
            if (dateParts.size >= 2) {
                val monthKey = "${dateParts[0]}-${dateParts[1]}"
                monthlyRevenues[monthKey] = (monthlyRevenues[monthKey] ?: 0L) + est.amount
            }
        } catch (e: Exception) {
            // Ignore format errors
        }
    }
    
    // Populate all 12 months
    for (m in 1..12) {
        val monthKey = "$yearStr-${String.format("%02d", m)}"
        if (!monthlyRevenues.containsKey(monthKey)) {
            monthlyRevenues[monthKey] = 0L
        }
    }

    return AnnualRevenueStats(
        annualTotalRevenue = annualTotal,
        monthlyRevenues = monthlyRevenues
    )
}
