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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.danallacalendar.ui.screens.TeamConfigs
import com.example.danallacalendar.ui.screens.settingsDataStore
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
    val companyAddress by viewModel.companyAddress.collectAsStateWithLifecycle()
    val activeAreas by viewModel.activeAreas.collectAsStateWithLifecycle()

    val currentCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(currentCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(currentCal.get(Calendar.MONTH)) } // 0-11

    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = remember(isCreator) {
        if (isCreator) {
            listOf("견적", "계약", "성장률", "거리/지역", "연간 매출")
        } else {
            listOf("견적", "계약", "성장률", "거리/지역")
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
                        2 -> GrowthRateTabContent(allEstimates, allEvents, selectedYear, selectedMonth)
                        3 -> DistanceRegionTabContent(allEstimates, companyAddress, activeAreas, selectedYear, selectedMonth)
                        4 -> {
                            if (isCreator) {
                                AnnualRevenueTabContent(allEstimates, allEvents, selectedYear, selectedMonth)
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
                        Text("견적 대비 계약 전환율", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format("%.1f", stats.conversionRate)}%",
                            fontSize = 22.sp,
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
                        Text("총 견적 요청 수", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${stats.totalEstimates}건", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("평균 견적 금액", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(formatManwon(stats.averageEstimateAmount), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            // Day of Week Requests for selected year/month
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("요일별 견적 건수", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                            .height(110.dp)
                    )
                }
            }
        }

        item {
            // Weekly Inquiries for the selected month
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${month + 1}월 주간 견적 건수", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                                .height(110.dp)
                        )
                    } else {
                        Text("표시할 차트 데이터가 없습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
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
                    Text("${year}년 월간 견적 건수", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
            // Hourly Heatmap
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("시간대별 견적 현황", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val timeRanges = listOf(
                        "오전(08~12시)" to stats.hourlyRequests.filter { it.key in 8..11 }.values.sum(),
                        "오후(12~17시)" to stats.hourlyRequests.filter { it.key in 12..16 }.values.sum(),
                        "저녁(17~21시)" to stats.hourlyRequests.filter { it.key in 17..20 }.values.sum()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        timeRanges.forEach { range ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(range.first, fontSize = 13.sp, modifier = Modifier.width(125.dp))
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val teamConfigsFlow = remember(context) {
        context.settingsDataStore.data.map { preferences ->
            TeamConfigs.map { config ->
                val name = preferences[config.nameKey] ?: config.defaultName
                val color = preferences[config.colorKey] ?: config.defaultColor
                name to color
            }
        }
    }
    val teamPrefsList by teamConfigsFlow.collectAsState(
        initial = TeamConfigs.map { it.defaultName to it.defaultColor }
    )

    val stats = remember(estimates, events, year, month) {
        computeEstimateContractStats(estimates, events, year, month)
    }

    val opsStats = remember(estimates, events, year, month) {
        computeOperationsCargoStats(estimates, events, year, month)
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingTeamId by remember { mutableIntStateOf(1) }
    var editingTeamName by remember { mutableStateOf("") }

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
                        Text("견적 대비 계약 전환율", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format("%.1f", stats.conversionRate)}%",
                            fontSize = 22.sp,
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
            // Stats Row: Contracted Count & Average Contract Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("계약된 완료 건수", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${stats.contractedCount}건", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("평균 계약완료 금액", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(formatManwon(stats.averageContractAmount), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            // Team Contract Counts
            Card(modifier = Modifier.fillMaxWidth()) {
                var menuExpanded by remember { mutableStateOf(false) }
                var visibleTeamsCount by remember { mutableIntStateOf(3) }

                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("팀별 계약 건수", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Box {
                            TextButton(
                                onClick = { menuExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("${visibleTeamsCount}개 팀 표시", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "팀 개수 선택"
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                for (num in 1..5) {
                                    DropdownMenuItem(
                                        text = { Text("${num}개 팀", fontSize = 13.sp) },
                                        onClick = {
                                            visibleTeamsCount = num
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val maxTeamVal = (stats.teamMoveCounts.values.maxOrNull() ?: 1).toFloat()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (teamId in 1..visibleTeamsCount) {
                            val count = stats.teamMoveCounts[teamId] ?: 0
                            val teamPref = teamPrefsList.getOrNull(teamId - 1) ?: (TeamConfigs.getOrNull(teamId - 1)?.let { it.defaultName to it.defaultColor } ?: ("" to 0xFF4CAF50L))
                            val teamDisplayName = teamPref.first.ifBlank { "${teamId}팀" }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingTeamId = teamId
                                        editingTeamName = teamPref.first
                                        showEditDialog = true
                                    }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = teamDisplayName,
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(70.dp),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    val pct = if (maxTeamVal > 0) count.toFloat() / maxTeamVal else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct)
                                            .background(Color(teamPref.second))
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
            // Avg Price by Tonnage (Collapsible)
            Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                var isTonnageCardExpanded by remember { mutableStateOf(false) }

                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTonnageCardExpanded = !isTonnageCardExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("계약완료된 평균 금액(톤수별)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Icon(
                            imageVector = if (isTonnageCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isTonnageCardExpanded) "접기" else "펼치기"
                        )
                    }

                    if (isTonnageCardExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (ton in 6 downTo 1) {
                                val avgPrice = stats.tonnageAvgPrices[ton] ?: 0L
                                val count = stats.tonnageCounts[ton] ?: 0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${ton}톤 (${count}건)", fontSize = 14.sp)
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
        }

        item {
            // Day of week moves chart
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("요일별 이사 건수", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val dayLabels = listOf("일", "월", "화", "수", "목", "금", "토")
                    val chartData = dayLabels.mapIndexed { index, label ->
                        val count = opsStats.dayOfWeekMoveCounts[index + 1] ?: 0
                        count.toFloat() to label
                    }

                    BarChart(
                        data = chartData,
                        barColor = Color(0xFF9C27B0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            }
        }

        item {
            // Chart: Monthly Move Trend (Split into Jan-Jun / Jul-Dec)
            val monthlyData = opsStats.monthlyMoveCounts.entries
                .filter { it.key.startsWith(year.toString()) }
                .sortedBy { it.key }
            
            val firstHalf = monthlyData.take(6).map { it.value.toFloat() to it.key.substringAfter("-") + "월" }
            val secondHalf = monthlyData.drop(6).take(6).map { it.value.toFloat() to it.key.substringAfter("-") + "월" }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${year}년 월간 이사 건수", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (firstHalf.isNotEmpty()) {
                        BarChart(
                            data = firstHalf,
                            barColor = Color(0xFF3F51B5),
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
                            barColor = Color(0xFF3F51B5),
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
            // Cargo Size Ratio (Horizontal Progress Bars - Reversed & Compact)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("짐 규모별 비율", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    val total = opsStats.cargoTonnageCounts.values.sum().toFloat()
                    val items = listOf(
                        Triple("7톤이상", opsStats.cargoTonnageCounts["7톤이상"] ?: 0, Color(0xFF9E9E9E)),
                        Triple("5-6톤", opsStats.cargoTonnageCounts["5-6톤"] ?: 0, Color(0xFF4CAF50)),
                        Triple("3-4톤", opsStats.cargoTonnageCounts["3-4톤"] ?: 0, Color(0xFFFF9800)),
                        Triple("1-2톤", opsStats.cargoTonnageCounts["1-2톤"] ?: 0, Color(0xFF2196F3))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items.forEach { item ->
                            val pct = if (total > 0f) (item.second.toFloat() / total) * 100 else 0f
                            val pctFloat = if (total > 0f) item.second.toFloat() / total else 0f
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.first,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(75.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pctFloat)
                                            .background(item.third)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${String.format("%.1f", pct)}% (${item.second}건)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("팀 이름 변경", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text("${editingTeamId}팀의 새로운 이름을 입력하세요.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingTeamName,
                        onValueChange = { editingTeamName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            context.settingsDataStore.edit { preferences ->
                                val config = TeamConfigs[editingTeamId - 1]
                                preferences[config.nameKey] = editingTeamName
                            }
                        }
                        showEditDialog = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun DistanceRegionTabContent(
    estimates: List<Estimate>,
    companyAddress: String,
    activeAreas: String,
    year: Int,
    month: Int
) {
    val stats = remember(estimates, companyAddress, activeAreas, year, month) {
        computeDistanceRegionStats(estimates, companyAddress, activeAreas, year, month)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. 업체 거점 위치 및 평균 거리 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📍 분석 기준 (내 업체 위치)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (companyAddress.isNotBlank()) companyAddress else "설정된 업체 위치가 없습니다. (설정 탭에서 등록 가능)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (companyAddress.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("평균 이사 거리 (추정치)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format("%.1f", stats.averageDistance)} km",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "총 ${stats.totalCount}건 분석",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 2. 이사 구간 비율 통계 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "이사 구간 비율 통계",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (companyAddress.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚠️ 설정 탭에서 업체 위치를 등록하시면\n거점 기준 구간(관내/근거리 등) 분석이 표시됩니다.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (stats.totalCount == 0) {
                        Text(
                            text = "구간을 분석할 수 있는 이사 데이터가 없습니다.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        val total = stats.totalCount.toFloat()
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ZoneRatioItem(
                                label = "관내 (동일 시/군/구)",
                                count = stats.inCityCount,
                                total = total,
                                barColor = Color(0xFF2E7D32)
                            )
                            ZoneRatioItem(
                                label = "근거리 (인접 시/군/구)",
                                count = stats.nearbyCount,
                                total = total,
                                barColor = Color(0xFF1976D2)
                            )
                            ZoneRatioItem(
                                label = "중거리 (약 100km 내외)",
                                count = stats.midDistCount,
                                total = total,
                                barColor = Color(0xFFF57C00)
                            )
                            ZoneRatioItem(
                                label = "장거리 (약 200km 이상)",
                                count = stats.longDistCount,
                                total = total,
                                barColor = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
        }

        // 3. 자주 가는 목적지 TOP 10 (관내제외) 순위 리스트
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "자주 가는 목적지 TOP 10 (관내제외)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (stats.destinationRankings.isEmpty()) {
                        Text("목적지 데이터가 존재하지 않습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            stats.destinationRankings.take(10).forEachIndexed { index, rank ->
                                val rankNum = index + 1
                                val rankColor = when (rankNum) {
                                    1 -> Color(0xFFFFD700) // Gold
                                    2 -> Color(0xFFC0C0C0) // Silver
                                    3 -> Color(0xFFCD7F32) // Bronze
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (rankNum <= 3) rankColor else MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = rankNum.toString(),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (rankNum <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = rank.first,
                                            fontWeight = if (rankNum <= 3) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${rank.second}건",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (index < stats.destinationRankings.take(10).lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(0.5.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. 연간 월별 이사 구간 추이 카드 (관내제외)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "연간 월별 이사 구간 추이 (관내제외)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (stats.monthlyZoneCounts.isEmpty() || stats.monthlyZoneCounts.all { it.nearbyCount + it.midDistCount + it.longDistCount == 0 }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("해당 연도에 집계할 이사 데이터가 없습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        ZoneLineChart(
                            data = stats.monthlyZoneCounts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChartLegendItem("근거리", Color(0xFF1976D2))
                            ChartLegendItem("중거리", Color(0xFFF57C00))
                            ChartLegendItem("장거리", Color(0xFFD32F2F))
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun ZoneRatioItem(
    label: String,
    count: Int,
    total: Float,
    barColor: Color
) {
    val pct = if (total > 0f) (count.toFloat() / total) * 100 else 0f
    val pctFraction = if (total > 0f) count.toFloat() / total else 0f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${String.format("%.1f", pct)}% (${count}건)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pctFraction.coerceIn(0f, 1f))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun AnnualRevenueTabContent(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int) {
    val stats = remember(estimates, events, year) {
        computeAnnualRevenueStats(estimates, events, year)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 2. 연간 누적 매출 카드 (primary 틴트 계열 배경)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${year}년 연간 누적 매출 (계약 완료 기준)", 
                        fontSize = 13.sp, 
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatCurrency(stats.annualTotalRevenue),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 3. 월별 누적 매출 현황 리스트 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${year}년 월별 누적 매출 현황", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val monthlyList = stats.monthlyRevenues.entries
                        .sortedBy { it.key }

                    if (monthlyList.isEmpty()) {
                        Text("올해 매출 데이터가 존재하지 않습니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            monthlyList.forEach { entry ->
                                val monthStr = entry.key.substringAfter("-") + "월"
                                val isCurrentMonth = entry.key.endsWith(String.format("-%02d", month + 1))
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isCurrentMonth) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f) else Color.Transparent)
                                        .padding(vertical = if (isCurrentMonth) 6.dp else 2.dp, horizontal = if (isCurrentMonth) 8.dp else 0.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = monthStr, 
                                        fontSize = 14.sp, 
                                        fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrentMonth) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatCurrency(entry.value), 
                                        fontSize = 14.sp, 
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentMonth) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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

@Composable
fun ZoneLineChart(
    data: List<MonthlyZoneCount>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        // 관외(근거리, 중거리, 장거리) 중 최대값 건수를 구해서 scaleMax 산정
        val maxVal = data.maxOf { maxOf(it.nearbyCount, it.midDistCount, it.longDistCount) }.toFloat()
        val scaleMax = if (maxVal > 0f) maxVal else 1f

        val spacing = 16.dp.toPx()
        val textHeight = 18.dp.toPx()
        val topPadding = 20.dp.toPx()
        val chartHeight = size.height - textHeight - topPadding - 8.dp.toPx()
        
        // 12개월의 x 좌표들
        val xStep = (size.width - (spacing * 2)) / 11

        // 색상 정의
        val nearbyColor = Color(0xFF1976D2)
        val midDistColor = Color(0xFFF57C00)
        val longDistColor = Color(0xFFD32F2F)

        // 각 라인별 포인트 좌표 리스트 생성
        val nearbyPoints = mutableListOf<Offset>()
        val midDistPoints = mutableListOf<Offset>()
        val longDistPoints = mutableListOf<Offset>()

        data.forEachIndexed { index, mc ->
            val x = spacing + index * xStep
            
            val yNearby = chartHeight - (mc.nearbyCount.toFloat() / scaleMax * chartHeight) + topPadding
            val yMidDist = chartHeight - (mc.midDistCount.toFloat() / scaleMax * chartHeight) + topPadding
            val yLongDist = chartHeight - (mc.longDistCount.toFloat() / scaleMax * chartHeight) + topPadding

            nearbyPoints.add(Offset(x, yNearby))
            midDistPoints.add(Offset(x, yMidDist))
            longDistPoints.add(Offset(x, yLongDist))

            // 하단 1~12 라벨 그리기
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 9.5.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val shortLabel = mc.monthLabel.replace("월", "")
            drawContext.canvas.nativeCanvas.drawText(
                shortLabel,
                x,
                size.height - 4.dp.toPx(),
                labelPaint
            )
        }

        // 라인 그리기 함수 헬퍼
        fun drawTrendLine(points: List<Offset>, lineColor: Color, counts: List<Int>) {
            // 1. 선 그리기
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = lineColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2.dp.toPx()
                )
            }
            // 2. 각 점(Dot) 및 수치 텍스트 그리기
            points.forEachIndexed { i, pt ->
                val count = counts[i]
                
                // Dot
                drawCircle(
                    color = lineColor,
                    radius = 3.5.dp.toPx(),
                    center = pt
                )
                // 하얀 속을 파서 더 예쁘게 만듦
                drawCircle(
                    color = Color.White,
                    radius = 1.5.dp.toPx(),
                    center = pt
                )

                // 건수가 0보다 큰 경우에만 숫자 텍스트 표시
                if (count > 0) {
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 8.5.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        count.toString(),
                        pt.x,
                        pt.y - 6.dp.toPx(),
                        textPaint
                    )
                }
            }
        }

        // 3가지 구간의 라인을 각각 그림
        drawTrendLine(nearbyPoints, nearbyColor, data.map { it.nearbyCount })
        drawTrendLine(midDistPoints, midDistColor, data.map { it.midDistCount })
        drawTrendLine(longDistPoints, longDistColor, data.map { it.longDistCount })
    }
}

@Composable
fun ChartLegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
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
    val tonnageAvgPrices: Map<Int, Long>,
    val contractedCount: Int,
    val averageContractAmount: Long,
    val tonnageCounts: Map<Int, Int>
)

data class OperationsCargoStats(
    val holidayWeekendRatio: Double,
    val yoyGrowthRate: Double,
    val dayOfWeekMoveCounts: Map<Int, Int>,
    val monthlyMoveCounts: Map<String, Int>,
    val cargoTonnageCounts: Map<String, Int>
)

data class MonthlyZoneCount(
    val monthLabel: String,
    val inCityCount: Int = 0,
    val nearbyCount: Int = 0,
    val midDistCount: Int = 0,
    val longDistCount: Int = 0
)

data class DistanceRegionStats(
    val averageDistance: Double,
    val regionFlows: List<Pair<Pair<String, String>, Int>>,
    val inCityCount: Int = 0,
    val nearbyCount: Int = 0,
    val midDistCount: Int = 0,
    val longDistCount: Int = 0,
    val totalCount: Int = 0,
    val destinationRankings: List<Pair<String, Int>> = emptyList(),
    val monthlyZoneCounts: List<MonthlyZoneCount> = emptyList()
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

// 인접 도시 사전 정의 맵
val neighborCities = mapOf(
    "수원시" to listOf("화성시", "용인시", "의왕시", "안산시", "오산시", "성남시", "안양시"),
    "성남시" to listOf("용인시", "광주시", "하남시", "서울", "과천시", "의왕시", "수원시"),
    "용인시" to listOf("수원시", "성남시", "화성시", "평택시", "안성시", "이천시", "광주시"),
    "화성시" to listOf("수원시", "용인시", "안산시", "평택시", "오산시"),
    "고양시" to listOf("서울", "파주시", "김포시", "양주시"),
    "부천시" to listOf("서울", "인천", "시흥시", "광명시"),
    "안산시" to listOf("시흥시", "군포시", "의왕시", "수원시", "화성시", "인천"),
    "안양시" to listOf("서울", "과천시", "의왕시", "군포시", "안산시", "시흥시", "광명시"),
    "남양주시" to listOf("서울", "구리시", "의정부시", "포천시", "가평군", "양평군", "광주시", "하남시"),
    "의정부시" to listOf("서울", "양주시", "포천시", "남양주시"),
    "구미시" to listOf("칠곡군", "김천시", "상주시", "의성군", "군위군", "대구", "칠곡")
)

// 주요 시/군/구의 시/도 매핑 맵
val cityToSidoMap = mapOf(
    "수원" to "경기", "수원시" to "경기",
    "성남" to "경기", "성남시" to "경기",
    "용인" to "경기", "용인시" to "경기",
    "화성" to "경기", "화성시" to "경기",
    "고양" to "경기", "고양시" to "경기",
    "부천" to "경기", "부천시" to "경기",
    "안산" to "경기", "안산시" to "경기",
    "안양" to "경기", "안양시" to "경기",
    "남양주" to "경기", "남양주시" to "경기",
    "의정부" to "경기", "의정부시" to "경기",
    "평택" to "경기", "평택시" to "경기",
    "시흥" to "경기", "시흥시" to "경기",
    "파주" to "경기", "파주시" to "경기",
    "김포" to "경기", "김포시" to "경기",
    "광명" to "경기", "광명시" to "경기",
    "군포" to "경기", "군포시" to "경기",
    "광주" to "경기", "광주시" to "경기",
    "이천" to "경기", "이천시" to "경기",
    "양주" to "경기", "양주시" to "경기",
    "오산" to "경기", "오산시" to "경기",
    "안성" to "경기", "안성시" to "경기",
    "구리" to "경기", "구리시" to "경기",
    "포천" to "경기", "포천시" to "경기",
    "의왕" to "경기", "의왕시" to "경기",
    "하남" to "경기", "하남시" to "경기",
    "여주" to "경기", "여주시" to "경기",
    "동두천" to "경기", "동두천시" to "경기",
    "양평" to "경기", "양평군" to "경기",
    "가평" to "경기", "가평군" to "경기",
    "연천" to "경기", "연천군" to "경기",
    
    "구미" to "경북", "구미시" to "경북",
    "포항" to "경북", "포항시" to "경북",
    "경주" to "경북", "경주시" to "경북",
    "김천" to "경북", "김천시" to "경북",
    "안동" to "경북", "안동시" to "경북",
    "영주" to "경북", "영주시" to "경북",
    "영천" to "경북", "영천시" to "경북",
    "상주" to "경북", "상주시" to "경북",
    "문경" to "경북", "문경시" to "경북",
    "경산" to "경북", "경산시" to "경북",
    "군위" to "대구", "군위군" to "대구",
    "의성" to "경북", "의성군" to "경북",
    "청송" to "경북", "청송군" to "경북",
    "영양" to "경북", "영양군" to "경북",
    "영덕" to "경북", "영덕군" to "경북",
    "청도" to "경북", "청도군" to "경북",
    "고령" to "경북", "고령군" to "경북",
    "성주" to "경북", "성주군" to "경북",
    "칠곡" to "경북", "칠곡군" to "경북",
    "예천" to "경북", "예천군" to "경북",
    "봉화" to "경북", "봉화군" to "경북",
    "울진" to "경북", "울진군" to "경북",
    "울릉" to "경북", "울릉군" to "경북"
)

enum class DistanceZone(val displayName: String) {
    IN_CITY("관내"),
    NEARBY("근거리"),
    MID_DIST("중거리"),
    LONG_DIST("장거리"),
    UNKNOWN("미지정")
}

// 시/군/구 명칭의 유효성 검사 (동/호수, 지번, 아파트명 오인식 방지)
fun isValidSigungu(sigungu: String): Boolean {
    val clean = sigungu.trim()
    if (clean.isEmpty() || clean == "미지정") return false
    
    // 숫자가 섞여있거나 번지, 호, 길, 로 등으로 끝나면 시군구가 아님
    if (clean.contains(Regex("[0-9]")) || clean.endsWith("번지") || clean.endsWith("호") || clean.endsWith("길") || clean.endsWith("로") || clean.endsWith("동")) {
        // 단, 안동시, 성동구, 영동군 등 진짜 시군구는 허용
        val isRealSigungu = clean.endsWith("시") || clean.endsWith("군") || clean.endsWith("구")
        val isDongException = clean.endsWith("동") && (clean.contains("성동") || clean.contains("영동") || clean.contains("안동") || clean.contains("창동"))
        if (isRealSigungu && !clean.endsWith("동")) {
            return true
        }
        if (isDongException) {
            return true
        }
        return false
    }
    
    return clean.endsWith("시") || clean.endsWith("군") || clean.endsWith("구") || clean.endsWith("읍") || clean.endsWith("면")
}

// 주소에서 (시/도, 시/군/구) 파싱 (도명 생략 케이스 대응)
fun parseCityAndGu(address: String): Pair<String, String> {
    val clean = address.split("|").firstOrNull()?.trim() ?: ""
    if (clean.isEmpty()) return "미지정" to "미지정"
    val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "미지정" to "미지정"

    val first = parts[0]
    
    // 첫 단어가 시/군/구 이거나, 생략형 시/군/구 매핑에 포함되는 경우 (도 생략 케이스 보정)
    val restoredSido = cityToSidoMap[first] ?: cityToSidoMap[first.replace(Regex("[시군구]$"), "")]
    if (restoredSido != null) {
        val sigungu = if (first.endsWith("시") || first.endsWith("군") || first.endsWith("구")) {
            first
        } else {
            if (cityToSidoMap.containsKey(first + "시")) first + "시"
            else if (cityToSidoMap.containsKey(first + "군")) first + "군"
            else first
        }
        return restoredSido to sigungu
    }

    val rawSido = first
    val sido = when {
        rawSido.startsWith("서울") -> "서울"
        rawSido.startsWith("경기") -> "경기"
        rawSido.startsWith("인천") -> "인천"
        rawSido.startsWith("강원") -> "강원"
        rawSido.startsWith("충북") || rawSido.startsWith("충청북") -> "충북"
        rawSido.startsWith("충남") || rawSido.startsWith("충청남") -> "충남"
        rawSido.startsWith("대전") -> "대전"
        rawSido.startsWith("경북") || rawSido.startsWith("경상북") -> "경북"
        rawSido.startsWith("경남") || rawSido.startsWith("경상남") -> "경남"
        rawSido.startsWith("부산") -> "부산"
        rawSido.startsWith("울산") -> "울산"
        rawSido.startsWith("대구") -> "대구"
        rawSido.startsWith("전북") || rawSido.startsWith("전라북") -> "전북"
        rawSido.startsWith("전남") || rawSido.startsWith("전라남") -> "전남"
        rawSido.startsWith("광주") -> "광주"
        rawSido.startsWith("세종") -> "세종"
        rawSido.startsWith("제주") -> "제주"
        else -> rawSido.take(2)
    }

    if (parts.size < 2) {
        return sido to sido
    }
    
    val second = parts[1]
    return sido to second
}

// 수도권(서울/경기/인천) 내 가깝게 인접한 도시쌍 체크
private fun checkMetropolitanNearby(city1: String, city2: String): Boolean {
    val nearbyPairs = listOf(
        setOf("서울", "성남시"), setOf("서울", "고양시"), setOf("서울", "과천시"), 
        setOf("서울", "안양시"), setOf("서울", "부천시"), setOf("서울", "광명시"), 
        setOf("서울", "구리시"), setOf("서울", "하남시"), setOf("서울", "의정부시"), 
        setOf("인천", "부천시"), setOf("인천", "김포시"), setOf("인천", "시흥시"),
        setOf("수원시", "안산시"), setOf("수원시", "군포시"), setOf("수원시", "의왕시")
    )
    return nearbyPairs.any { it.contains(city1) && it.contains(city2) }
}

fun determineZone(
    mySido: String,
    mySigungu: String,
    depSido: String,
    depSigungu: String,
    activeAreas: String = ""
): DistanceZone {
    val cleanMySigungu = mySigungu.replace(Regex("^[가-힣]+도\\s+"), "").trim()
    val cleanDepSigungu = depSigungu.replace(Regex("^[가-힣]+도\\s+"), "").trim()
    
    val mySigunguKey = cleanMySigungu.split(" ").firstOrNull() ?: ""
    val depSigunguKey = cleanDepSigungu.split(" ").firstOrNull() ?: ""
    
    // 1. 도명이 다르거나 오인식되더라도 시군구가 같으면 가장 우선적으로 관내(IN_CITY)로 판정
    if (mySigunguKey.isNotEmpty() && depSigunguKey.isNotEmpty() && mySigunguKey == depSigunguKey) {
        return DistanceZone.IN_CITY
    }

    // 1.2. 추가 활동 영역(activeAreas)에 출발지 시군구 키가 포함되어 있으면 관내(IN_CITY)로 판정
    if (activeAreas.isNotBlank() && depSigunguKey.isNotEmpty()) {
        val activeKeys = activeAreas.split(",")
            .map { it.trim().replace(Regex("[시군구]$"), "").trim() }
            .filter { it.isNotEmpty() }
        if (activeKeys.contains(depSigunguKey)) {
            return DistanceZone.IN_CITY
        }
    }

    if (mySido.isBlank() || depSido.isBlank() || depSido == "미지정") return DistanceZone.UNKNOWN
    
    if (cleanMySigungu.isNotEmpty() && cleanDepSigungu.isNotEmpty()) {
        // 2. 근거리: neighborCities 매핑 확인
        val neighbors = neighborCities[mySigunguKey] ?: emptyList()
        if (neighbors.any { depSigunguKey.contains(it) || it.contains(depSigunguKey) }) {
            return DistanceZone.NEARBY
        }
        
        // 3. 동일 도(Sido) 내 이동 판정
        if (mySido == depSido) {
            val isMySeoulIncheon = mySido == "서울" || mySido == "인천"
            val isDepSeoulIncheon = depSido == "서울" || depSido == "인천"
            
            // 수도권(서울/경기/인천) 간 이동일 때
            if ((mySido == "경기" && isDepSeoulIncheon) || (isMySeoulIncheon && depSido == "경기") || (isMySeoulIncheon && isDepSeoulIncheon)) {
                val isNearbyMetropolitan = checkMetropolitanNearby(mySigunguKey, depSigunguKey)
                return if (isNearbyMetropolitan) DistanceZone.NEARBY else DistanceZone.MID_DIST
            }
            // 같은 도내(예: 경북)이더라도 인접 도시가 아닌 먼 곳(예: 구미-포항)은 중거리(MID_DIST)로 분류
            return DistanceZone.MID_DIST
        }
    }

    val distanceMatrix = mapOf(
        setOf("서울", "충남") to 100.0, setOf("경기", "충남") to 90.0, setOf("인천", "충남") to 110.0,
        setOf("서울", "충북") to 120.0, setOf("경기", "충북") to 100.0, setOf("인천", "충북") to 130.0,
        setOf("서울", "대전") to 160.0, setOf("경기", "대전") to 140.0, setOf("인천", "대전") to 170.0,
        setOf("서울", "세종") to 130.0, setOf("경기", "세종") to 110.0, setOf("인천", "세종") to 140.0,
        setOf("서울", "강원") to 140.0, setOf("경기", "강원") to 100.0, setOf("인천", "강원") to 160.0,
        setOf("서울", "전북") to 230.0, setOf("경기", "전북") to 210.0, setOf("인천", "전북") to 240.0,
        setOf("서울", "경북") to 260.0, setOf("경기", "경북") to 240.0, setOf("인천", "경북") to 270.0,
        setOf("서울", "대구") to 290.0, setOf("경기", "대구") to 270.0, setOf("인천", "대구") to 300.0,
        setOf("서울", "광주") to 290.0, setOf("경기", "광주") to 270.0, setOf("인천", "광주") to 300.0,
        setOf("서울", "부산") to 390.0, setOf("경기", "부산") to 370.0, setOf("인천", "부산") to 400.0,
        setOf("서울", "울산") to 370.0, setOf("경기", "울산") to 350.0, setOf("인천", "울산") to 380.0,
        setOf("서울", "경남") to 350.0, setOf("경기", "경남") to 330.0, setOf("인천", "경남") to 360.0,
        setOf("서울", "전남") to 350.0, setOf("경기", "전남") to 330.0, setOf("인천", "전남") to 360.0,
        
        setOf("대전", "세종") to 30.0, setOf("대전", "충남") to 50.0, setOf("대전", "충북") to 60.0,
        setOf("대전", "전북") to 90.0, setOf("대전", "경북") to 140.0, setOf("대전", "대구") to 140.0,
        setOf("대전", "광주") to 170.0, setOf("대전", "서울") to 160.0, setOf("대전", "경기") to 140.0,
        setOf("대전", "부산") to 260.0, setOf("대전", "경남") to 220.0, setOf("대전", "전남") to 230.0,
        
        setOf("부산", "울산") to 50.0, setOf("부산", "경남") to 60.0, setOf("부산", "대구") to 100.0,
        setOf("부산", "경북") to 150.0, setOf("부산", "전남") to 190.0, setOf("부산", "광주") to 260.0,
        setOf("부산", "전북") to 250.0, setOf("대구", "경북") to 60.0, setOf("대구", "경남") to 100.0,
        setOf("대구", "울산") to 90.0, setOf("대구", "광주") to 180.0, setOf("대구", "전북") to 170.0
    )
    
    val pair = setOf(mySido, depSido)
    val distance = distanceMatrix[pair]
    
    return when {
        distance == null -> {
            if (mySido != depSido) DistanceZone.LONG_DIST else DistanceZone.NEARBY
        }
        distance < 80.0 -> DistanceZone.NEARBY
        distance <= 160.0 -> DistanceZone.MID_DIST
        else -> DistanceZone.LONG_DIST
    }
}

fun parseTonnage(volume: String): Double {
    val clean = volume.replace(Regex("[^0-9.]"), "")
    return clean.toDoubleOrNull() ?: 0.0
}

// ----------------------------------------
// Computation Functions
// ----------------------------------------

fun computeEstimateContractStats(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int): EstimateContractStats {
    // 전화번호(phoneNumber) 기준 중복 제거 (숫자만 남겨 비교, 비어있지 않은 번호들에 대해 최신 createdAt 기준 1개 선택)
    val uniqueEstimates = estimates
        .groupBy { it.phoneNumber.replace(Regex("[^0-9]"), "") }
        .flatMap { (phone, estList) ->
            if (phone.isEmpty()) {
                estList
            } else {
                listOf(estList.maxByOrNull { it.createdAt } ?: estList.first())
            }
        }

    android.util.Log.d("STATS_DEBUG", "=== START STATS ===")
    android.util.Log.d("STATS_DEBUG", "estimates size: ${estimates.size}, uniqueEstimates size: ${uniqueEstimates.size}")
    uniqueEstimates.take(15).forEach { est ->
        android.util.Log.d("STATS_DEBUG", "Est -> ID: '${est.id}', Name: '${est.customerName}', Phone: '${est.phoneNumber}', totalCost: '${est.totalCost}', amount: ${est.amount}")
    }
    events.filter { !it.linkedEstimateId.isNullOrBlank() }.take(20).forEach { evt ->
        android.util.Log.d("STATS_DEBUG", "Evt -> Title: '${evt.title}', LinkedID: '${evt.linkedEstimateId}', isAllDay: ${evt.isAllDay}, startMillis: ${evt.startMillis}")
    }

    val filteredEstimates = uniqueEstimates.filter { est ->
        val cal = Calendar.getInstance().apply { timeInMillis = est.createdAt }
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }
    
    val total = filteredEstimates.size
    val contractedEstimateIds = uniqueEstimates.filter { est ->
        val estEvents = events.filter { it.linkedEstimateId == est.id }
        estEvents.any { it.isAllDay }
    }.map { it.id }.toSet()
    val contractedCount = filteredEstimates.count { it.id in contractedEstimateIds }
    
    val conversion = if (total > 0) (contractedCount.toDouble() / total) * 100 else 0.0
    
    val selectedYearMonthStr = String.format("%04d-%02d", year, month + 1)
    val visitCompletedEstimates = filteredEstimates.filter { est ->
        val estEvents = events.filter { it.linkedEstimateId == est.id }
        estEvents.isNotEmpty()
    }
    
    android.util.Log.d("STATS_DEBUG", "selectedYearMonthStr: $selectedYearMonthStr, visitCompletedEstimates size: ${visitCompletedEstimates.size}")
    visitCompletedEstimates.forEach { est ->
        android.util.Log.d("STATS_DEBUG", "Matched Est -> Name: '${est.customerName}', visitDate: '${est.visitDate}', moveDate: '${est.moveDate}', totalCost: '${est.totalCost}', amount: ${est.amount}")
    }

    val totalVisitCompletedCost = visitCompletedEstimates.sumOf { est ->
        val costStr = est.totalCost.replace(Regex("[^0-9]"), "")
        val cost = costStr.toLongOrNull() ?: 0L
        val rawCost = if (cost > 0L) cost else est.amount
        if (rawCost > 0L && rawCost < 10000L) rawCost * 10000L else rawCost
    }
    val avgAmount = if (visitCompletedEstimates.isNotEmpty()) {
        totalVisitCompletedCost / visitCompletedEstimates.size
    } else {
        0L
    }

    val contractedEstimatesInMonth = uniqueEstimates.filter { est ->
        val estEvents = events.filter { it.linkedEstimateId == est.id }
        val hasAllDayInMonth = estEvents.any { evt ->
            evt.isAllDay && Calendar.getInstance().apply { timeInMillis = evt.startMillis }.let {
                it.get(Calendar.YEAR) == year && it.get(Calendar.MONTH) == month
            }
        }
        val hasMoveDateInMonth = est.moveDate.startsWith(selectedYearMonthStr) && est.moveDate.isNotBlank() && estEvents.any { it.isAllDay }
        hasAllDayInMonth || hasMoveDateInMonth
    }
    val actualContractedCount = contractedEstimatesInMonth.size
    val totalContractedCost = contractedEstimatesInMonth.sumOf { est ->
        val costStr = est.totalCost.replace(Regex("[^0-9]"), "")
        val cost = costStr.toLongOrNull() ?: 0L
        val rawCost = if (cost > 0L) cost else est.amount
        if (rawCost > 0L && rawCost < 10000L) rawCost * 10000L else rawCost
    }
    val avgContractAmount = if (contractedEstimatesInMonth.isNotEmpty()) {
        totalContractedCost / contractedEstimatesInMonth.size
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
    val maxDays = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    if (maxDays > 28) {
        weekly["5주차"] = 0
    }

    filteredEstimates.forEach { est ->
        val cal = Calendar.getInstance().apply { timeInMillis = est.createdAt }
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
        val cal = Calendar.getInstance().apply { timeInMillis = evt.startMillis }
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }
    val teamCounts = mutableMapOf<Int, Int>()
    filteredEvents.forEach { evt ->
        if (evt.teamId != null) {
            teamCounts[evt.teamId] = (teamCounts[evt.teamId] ?: 0) + 1
        }
    }

    // Tonnage average prices
    val tonnagePrices = mutableMapOf<Int, MutableList<Long>>()
    contractedEstimatesInMonth.forEach { est ->
        val ton = parseTonnage(est.totalVolume).toInt()
        if (ton in 1..6) {
            if (tonnagePrices[ton] == null) tonnagePrices[ton] = mutableListOf()
            val costStr = est.totalCost.replace(Regex("[^0-9]"), "")
            val cost = costStr.toLongOrNull() ?: 0L
            val rawCost = if (cost > 0L) cost else est.amount
            val normalizedCost = if (rawCost > 0L && rawCost < 10000L) rawCost * 10000L else rawCost
            tonnagePrices[ton]?.add(normalizedCost)
        }
    }
    val tonnageAvg = tonnagePrices.mapValues { entry ->
        if (entry.value.isNotEmpty()) entry.value.average().toLong() else 0L
    }
    val tonnageCounts = tonnagePrices.mapValues { entry ->
        entry.value.size
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
        tonnageAvgPrices = tonnageAvg,
        contractedCount = actualContractedCount,
        averageContractAmount = avgContractAmount,
        tonnageCounts = tonnageCounts
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

    // Monthly moves for the selected year (Jan - Dec)
    val yearStr = year.toString()
    for (m in 1..12) {
        val mKey = "$yearStr-${String.format("%02d", m)}"
        monthlyMoveCounts[mKey] = 0
    }
    events.filter { evt ->
        evt.isAllDay && evt.teamId != null &&
        cal.apply { timeInMillis = evt.startMillis }.get(Calendar.YEAR) == year
    }.forEach { evt ->
        val monthStr = sdfMonth.format(Date(evt.startMillis))
        monthlyMoveCounts[monthStr] = (monthlyMoveCounts[monthStr] ?: 0) + 1
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

    // Unique estimates by phone number (same logic as computeEstimateContractStats)
    val uniqueEstimates = estimates
        .groupBy { it.phoneNumber.replace(Regex("[^0-9]"), "") }
        .flatMap { (phone, estList) ->
            if (phone.isEmpty()) {
                estList
            } else {
                listOf(estList.maxByOrNull { it.createdAt } ?: estList.first())
            }
        }

    val selectedYearMonthStr = String.format("%04d-%02d", year, month + 1)
    val contractedEstimatesInMonth = uniqueEstimates.filter { est ->
        val estEvents = events.filter { it.linkedEstimateId == est.id }
        val hasAllDayInMonth = estEvents.any { evt ->
            evt.isAllDay && Calendar.getInstance().apply { timeInMillis = evt.startMillis }.let {
                it.get(Calendar.YEAR) == year && it.get(Calendar.MONTH) == month
            }
        }
        val hasMoveDateInMonth = est.moveDate.startsWith(selectedYearMonthStr) && est.moveDate.isNotBlank() && estEvents.any { it.isAllDay }
        hasAllDayInMonth || hasMoveDateInMonth
    }

    // Cargo size ratios
    val cargoCounts = mutableMapOf(
        "1-2톤" to 0,
        "3-4톤" to 0,
        "5-6톤" to 0,
        "7톤이상" to 0
    )
    
    contractedEstimatesInMonth.forEach { est ->
        val ton = parseTonnage(est.totalVolume)
        when {
            ton <= 0.0 -> cargoCounts["7톤이상"] = (cargoCounts["7톤이상"] ?: 0) + 1
            ton <= 2.0 -> cargoCounts["1-2톤"] = (cargoCounts["1-2톤"] ?: 0) + 1
            ton <= 4.0 -> cargoCounts["3-4톤"] = (cargoCounts["3-4톤"] ?: 0) + 1
            ton <= 6.0 -> cargoCounts["5-6톤"] = (cargoCounts["5-6톤"] ?: 0) + 1
            else -> cargoCounts["7톤이상"] = (cargoCounts["7톤이상"] ?: 0) + 1
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

fun computeDistanceRegionStats(estimates: List<Estimate>, companyAddress: String, activeAreas: String, year: Int, month: Int): DistanceRegionStats {
    // 전화번호(phoneNumber) 기준 중복 제거 (최신 견적서 1개만 선택)
    val uniqueEstimates = estimates
        .groupBy { it.phoneNumber.replace(Regex("[^0-9]"), "") }
        .flatMap { (phone, estList) ->
            if (phone.isEmpty()) {
                estList
            } else {
                listOf(estList.maxByOrNull { it.createdAt } ?: estList.first())
            }
        }

    var totalDistance = 0.0
    var count = 0
    val flows = mutableMapOf<Pair<String, String>, Int>()
    val cal = Calendar.getInstance()

    // 내 업체 주소 파싱
    val (mySido, mySigungu) = if (companyAddress.isNotBlank()) {
        parseCityAndGu(companyAddress)
    } else {
        "" to ""
    }
    val cleanMySigungu = mySigungu.replace(Regex("^[가-힣]+도\\s+"), "").trim()
    val mySigunguKey = cleanMySigungu.split(" ").firstOrNull() ?: ""

    var inCityCount = 0
    var nearbyCount = 0
    var midDistCount = 0
    var longDistCount = 0
    var totalCount = 0

    val destinationMap = mutableMapOf<String, Int>()

    uniqueEstimates.filter { est ->
        cal.timeInMillis = est.createdAt
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }.forEach { est ->
        if (est.departure.isNotBlank() && est.destination.isNotBlank()) {
            val validSidos = setOf("서울", "경기", "인천", "강원", "충북", "충남", "대전", "경북", "경남", "부산", "울산", "대구", "전북", "전남", "광주", "세종", "제주")

            var depReg = getRegionName(est.departure)
            var destReg = getRegionName(est.destination)

            // 출발지 주소의 시/도 판단이 힘든 경우, 내 업체 위치를 출발 지역명 기본값으로 적용
            if ((depReg == "미지정" || !validSidos.contains(depReg)) && mySido.isNotBlank()) {
                depReg = mySido
            }

            // 도착지 주소의 시/도 판단이 힘든 경우, 내 업체 위치를 도착 지역명 기본값으로 적용
            if ((destReg == "미지정" || !validSidos.contains(destReg)) && mySido.isNotBlank()) {
                destReg = mySido
            }
            
            // 기존 평균 거리 계산용 로직 유지
            if (depReg != "미지정" && destReg != "미지정") {
                val dist = estimateDistance(depReg, destReg)
                totalDistance += dist
                count++

                val pair = depReg to destReg
                flows[pair] = (flows[pair] ?: 0) + 1
            }

            // 새로운 구간 및 목적지 빈도 분석 로직
            var (depSido, depSigungu) = parseCityAndGu(est.departure)
            var (destSido, destSigungu) = parseCityAndGu(est.destination)

            // 출발지 주소가 도/시 생략 등으로 판단이 힘든 모호한 값인 경우 내 업체 위치의 Sido/Sigungu를 출발지로 자동 보정
            if ((depSido == "미지정" || !validSidos.contains(depSido)) && mySido.isNotBlank()) {
                depSido = mySido
                depSigungu = mySigungu
            }

            // 도착지 주소가 도/시 생략 등으로 판단이 힘든 모호한 값인 경우 내 업체 위치의 Sido/Sigungu를 도착지로 자동 보정
            if ((destSido == "미지정" || !validSidos.contains(destSido)) && mySido.isNotBlank()) {
                destSido = mySido
                destSigungu = mySigungu
            }

            if (depSido != "미지정" && mySido.isNotBlank()) {
                val zone = determineZone(mySido, mySigungu, depSido, depSigungu, activeAreas)
                when (zone) {
                    DistanceZone.IN_CITY -> inCityCount++
                    DistanceZone.NEARBY -> nearbyCount++
                    DistanceZone.MID_DIST -> midDistCount++
                    DistanceZone.LONG_DIST -> longDistCount++
                    else -> {}
                }
                if (zone != DistanceZone.UNKNOWN) {
                    totalCount++
                }
            }

            val cleanDestSigungu = destSigungu.replace(Regex("^[가-힣]+도\\s+"), "").trim()
            val destSigunguKey = cleanDestSigungu.split(" ").firstOrNull() ?: ""

            val activeKeys = if (activeAreas.isNotBlank()) {
                activeAreas.split(",")
                    .map { it.trim().replace(Regex("[시군구]$"), "").trim() }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            val isLocalDestination = (mySigunguKey.isNotEmpty() && destSigunguKey == mySigunguKey) || 
                                     activeKeys.contains(destSigunguKey)

            val isValidDest = validSidos.contains(destSido) && isValidSigungu(destSigungu)

            if (isValidDest && !isLocalDestination) {
                val displayDest = "$destSido $cleanDestSigungu"
                destinationMap[displayDest] = (destinationMap[displayDest] ?: 0) + 1
            }
        }
    }

    val avgDist = if (count > 0) totalDistance / count else 0.0
    val sortedFlows = flows.entries.sortedByDescending { it.value }.map { it.key to it.value }
    val sortedDestinations = destinationMap.entries.sortedByDescending { it.value }.map { it.key to it.value }

    // 연간 월별 구간 건수 집계 (1월 ~ 12월)
    val monthlyZoneCountsList = (0..11).map { m ->
        val monthLabel = "${m + 1}월"
        var inCity = 0
        var nearby = 0
        var midDist = 0
        var longDist = 0

        uniqueEstimates.filter { est ->
            cal.timeInMillis = est.createdAt
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == m
        }.forEach { est ->
            if (est.departure.isNotBlank() && est.destination.isNotBlank()) {
                val validSidos = setOf("서울", "경기", "인천", "강원", "충북", "충남", "대전", "경북", "경남", "부산", "울산", "대구", "전북", "전남", "광주", "세종", "제주")
                var (depSido, depSigungu) = parseCityAndGu(est.departure)
                var (destSido, destSigungu) = parseCityAndGu(est.destination)

                // 출발지 보정
                if ((depSido == "미지정" || !validSidos.contains(depSido)) && mySido.isNotBlank()) {
                    depSido = mySido
                    depSigungu = mySigungu
                }
                // 도착지 보정
                if ((destSido == "미지정" || !validSidos.contains(destSido)) && mySido.isNotBlank()) {
                    destSido = mySido
                    destSigungu = mySigungu
                }

                if (depSido != "미지정" && mySido.isNotBlank()) {
                    val zone = determineZone(mySido, mySigungu, depSido, depSigungu, activeAreas)
                    when (zone) {
                        DistanceZone.IN_CITY -> inCity++
                        DistanceZone.NEARBY -> nearby++
                        DistanceZone.MID_DIST -> midDist++
                        DistanceZone.LONG_DIST -> longDist++
                        else -> {}
                    }
                }
            }
        }

        MonthlyZoneCount(
            monthLabel = monthLabel,
            inCityCount = inCity,
            nearbyCount = nearby,
            midDistCount = midDist,
            longDistCount = longDist
        )
    }

    return DistanceRegionStats(
        averageDistance = avgDist,
        regionFlows = sortedFlows,
        inCityCount = inCityCount,
        nearbyCount = nearbyCount,
        midDistCount = midDistCount,
        longDistCount = longDistCount,
        totalCount = totalCount,
        destinationRankings = sortedDestinations,
        monthlyZoneCounts = monthlyZoneCountsList
    )
}

fun computeAnnualRevenueStats(estimates: List<Estimate>, events: List<Event>, year: Int): AnnualRevenueStats {
    val yearStr = year.toString()
    
    // 전화번호(phoneNumber) 기준 중복 제거 (최신 견적서 1개만 선택)
    val uniqueEstimates = estimates
        .groupBy { it.phoneNumber.replace(Regex("[^0-9]"), "") }
        .flatMap { (phone, estList) ->
            if (phone.isEmpty()) {
                estList
            } else {
                listOf(estList.maxByOrNull { it.createdAt } ?: estList.first())
            }
        }

    // 계약 완료(isAllDay == true인 연관 일정이 있는 경우)이면서 해당 연도 이사 날짜인 견적서 필터링
    val yearContractedEstimates = uniqueEstimates.filter { est ->
        val estEvents = events.filter { it.linkedEstimateId == est.id }
        estEvents.any { it.isAllDay } && est.moveDate.isNotBlank() && est.moveDate.startsWith(yearStr)
    }

    val annualTotal = yearContractedEstimates.sumOf { est ->
        val costStr = est.totalCost.replace(Regex("[^0-9]"), "")
        val cost = costStr.toLongOrNull() ?: 0L
        val rawCost = if (cost > 0L) cost else est.amount
        if (rawCost > 0L && rawCost < 10000L) rawCost * 10000L else rawCost
    }

    val monthlyRevenues = mutableMapOf<String, Long>()

    yearContractedEstimates.forEach { est ->
        try {
            val dateParts = est.moveDate.split("-")
            if (dateParts.size >= 2) {
                val monthKey = "${dateParts[0]}-${dateParts[1]}"
                val costStr = est.totalCost.replace(Regex("[^0-9]"), "")
                val cost = costStr.toLongOrNull() ?: 0L
                val rawCost = if (cost > 0L) cost else est.amount
                val normalizedCost = if (rawCost > 0L && rawCost < 10000L) rawCost * 10000L else rawCost
                monthlyRevenues[monthKey] = (monthlyRevenues[monthKey] ?: 0L) + normalizedCost
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

// ----------------------------------------
// Growth Rate Tab Data Models & Components
// ----------------------------------------

data class GrowthStats(
    val currentMonthMoves: Int,
    val prevMonthMoves: Int,
    val lastYearMonthMoves: Int,
    val moveCountYoY: Double,
    val moveCountMoM: Double,

    val currentMonthEstimates: Int,
    val prevMonthEstimates: Int,
    val lastYearMonthEstimates: Int,
    val estimateCountYoY: Double,
    val estimateCountMoM: Double,

    val conversionRateCurrent: Double,
    val prevMonthConversionRate: Double,
    val lastYearMonthConversionRate: Double,
    val conversionRateYoYChange: Double,
    val conversionRateMoMChange: Double,

    val currentAvgPrice: Long,
    val prevAvgPrice: Long,
    val lastYearAvgPrice: Long,
    val avgPriceYoY: Double,
    val avgPriceMoM: Double
)

@Composable
fun GrowthRateTabContent(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int) {
    val stats = remember(estimates, events, year, month) {
        computeGrowthStats(estimates, events, year, month)
    }
    
    val prevMonthStr = "지난달"

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📈", fontSize = 22.sp)
                    }
                    Column {
                        Text(
                            text = "${year}년 ${month + 1}월 성장률 리포트",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "작년 동월 및 직전 월과 비교한 종합 성장 추이",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 1. 견적 건수 비교
        item {
            GrowthComparisonCard(
                title = "견적 건수 비교",
                emoji = "📋",
                pastLabel = "작년 ${month + 1}월",
                pastValue = stats.lastYearMonthEstimates.toDouble(),
                pastDisplay = "${stats.lastYearMonthEstimates}건",
                prevLabel = prevMonthStr,
                prevValue = stats.prevMonthEstimates.toDouble(),
                prevDisplay = "${stats.prevMonthEstimates}건",
                currentLabel = "올해 ${month + 1}월",
                currentValue = stats.currentMonthEstimates.toDouble(),
                currentDisplay = "${stats.currentMonthEstimates}건",
                yoyGrowth = stats.estimateCountYoY,
                momGrowth = stats.estimateCountMoM
            )
        }

        // 2. 이사 건수 비교
        item {
            GrowthComparisonCard(
                title = "이사 건수 비교",
                emoji = "🚚",
                pastLabel = "작년 ${month + 1}월",
                pastValue = stats.lastYearMonthMoves.toDouble(),
                pastDisplay = "${stats.lastYearMonthMoves}건",
                prevLabel = prevMonthStr,
                prevValue = stats.prevMonthMoves.toDouble(),
                prevDisplay = "${stats.prevMonthMoves}건",
                currentLabel = "올해 ${month + 1}월",
                currentValue = stats.currentMonthMoves.toDouble(),
                currentDisplay = "${stats.currentMonthMoves}건",
                yoyGrowth = stats.moveCountYoY,
                momGrowth = stats.moveCountMoM
            )
        }

        // 3. 계약 전환율 비교
        item {
            GrowthComparisonCard(
                title = "계약 전환율 비교",
                emoji = "🎯",
                pastLabel = "작년 ${month + 1}월",
                pastValue = stats.lastYearMonthConversionRate,
                pastDisplay = "${String.format("%.1f", stats.lastYearMonthConversionRate)}%",
                prevLabel = prevMonthStr,
                prevValue = stats.prevMonthConversionRate,
                prevDisplay = "${String.format("%.1f", stats.prevMonthConversionRate)}%",
                currentLabel = "올해 ${month + 1}월",
                currentValue = stats.conversionRateCurrent,
                currentDisplay = "${String.format("%.1f", stats.conversionRateCurrent)}%",
                yoyGrowth = stats.conversionRateYoYChange,
                momGrowth = stats.conversionRateMoMChange,
                isPercentagePoint = true
            )
        }

        // 4. 평균 단가 비교
        item {
            GrowthComparisonCard(
                title = "평균 단가 비교",
                emoji = "💰",
                pastLabel = "작년 ${month + 1}월",
                pastValue = stats.lastYearAvgPrice.toDouble(),
                pastDisplay = if (stats.lastYearAvgPrice > 0) formatManwon(stats.lastYearAvgPrice) else "0원",
                prevLabel = prevMonthStr,
                prevValue = stats.prevAvgPrice.toDouble(),
                prevDisplay = if (stats.prevAvgPrice > 0) formatManwon(stats.prevAvgPrice) else "0원",
                currentLabel = "올해 ${month + 1}월",
                currentValue = stats.currentAvgPrice.toDouble(),
                currentDisplay = if (stats.currentAvgPrice > 0) formatManwon(stats.currentAvgPrice) else "0원",
                yoyGrowth = stats.avgPriceYoY,
                momGrowth = stats.avgPriceMoM
            )
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun GrowthComparisonCard(
    title: String,
    emoji: String,
    pastLabel: String,
    pastValue: Double,
    pastDisplay: String,
    prevLabel: String,
    prevValue: Double,
    prevDisplay: String,
    currentLabel: String,
    currentValue: Double,
    currentDisplay: String,
    yoyGrowth: Double,
    momGrowth: Double,
    isPercentagePoint: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Emoji & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 15.sp)
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Chart Column (Takes full card width)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val maxValue = maxOf(pastValue, prevValue, currentValue, 1.0)
                
                // 작년 동월
                BarRow(
                    label = pastLabel,
                    value = pastValue,
                    maxValue = maxValue,
                    displayVal = pastDisplay,
                    barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                // 올해 전월
                BarRow(
                    label = prevLabel,
                    value = prevValue,
                    maxValue = maxValue,
                    displayVal = prevDisplay,
                    barColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 올해 당월
                BarRow(
                    label = currentLabel,
                    value = currentValue,
                    maxValue = maxValue,
                    displayVal = currentDisplay,
                    barColor = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.primary,
                    isBold = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            
            Spacer(modifier = Modifier.height(6.dp))

            // 2. Growth Badges Row (Horizontal layout)
            GrowthRateRow(
                yoyRate = yoyGrowth,
                momRate = momGrowth,
                isPercentagePoint = isPercentagePoint
            )
        }
    }
}

@Composable
fun BarRow(
    label: String,
    value: Double,
    maxValue: Double,
    displayVal: String,
    barColor: Color,
    textColor: Color,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 라벨 영역 (폭 68.dp로 확대해 글자 잘림 방지)
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp),
            maxLines = 1
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // 2. 바 영역 (전체의 70% 너비 차지, 높이를 6.dp로 축소)
        Box(
            modifier = Modifier
                .weight(0.70f)
                .height(6.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.CenterStart
        ) {
            val fraction = (if (maxValue > 0.0) value / maxValue else 0.0).toFloat().coerceIn(0.04f, 1.0f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 3. 값 표시 영역 (나머지 30% 영역에 우측 정렬)
        Text(
            text = displayVal,
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.weight(0.30f),
            maxLines = 1
        )
    }
}

@Composable
fun GrowthRateRow(
    yoyRate: Double,
    momRate: Double,
    isPercentagePoint: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 전년 동월 대비
        GrowthRateBadgeItem(
            label = "전년 동월 대비",
            rate = yoyRate,
            isPercentagePoint = isPercentagePoint,
            modifier = Modifier.weight(1f)
        )
        
        // 2. 전월 대비
        GrowthRateBadgeItem(
            label = "전월 대비",
            rate = momRate,
            isPercentagePoint = isPercentagePoint,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun GrowthRateBadgeItem(
    label: String,
    rate: Double,
    isPercentagePoint: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // 상단: 라벨
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            
            // 하단: 수치 배지 (12.sp로 축소 유지)
            val isPositive = rate >= 0.0
            val displayColor = if (isPositive) Color(0xFF2E7D32) else Color(0xFFD32F2F)
            val bgColor = if (isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            val arrow = if (isPositive) "▲" else "▼"
            val sign = if (isPositive) "+" else ""
            val unit = if (isPercentagePoint) "%p" else "%"
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bgColor)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$arrow $sign${String.format("%.1f", rate)}$unit",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = displayColor,
                    maxLines = 1
                )
            }
        }
    }
}

fun computeGrowthStats(estimates: List<Estimate>, events: List<Event>, year: Int, month: Int): GrowthStats {
    // Unique estimates by phone number (same logic as computeEstimateContractStats)
    val uniqueEstimates = estimates
        .groupBy { it.phoneNumber.replace(Regex("[^0-9]"), "") }
        .flatMap { (phone, estList) ->
            if (phone.isEmpty()) {
                estList
            } else {
                listOf(estList.maxByOrNull { it.createdAt } ?: estList.first())
            }
        }

    // Helper function to check if timeInMillis falls in a specific year and month
    fun isSameMonth(timeMillis: Long, y: Int, m: Int): Boolean {
        val calInstance = Calendar.getInstance().apply { timeInMillis = timeMillis }
        return calInstance.get(Calendar.YEAR) == y && calInstance.get(Calendar.MONTH) == m
    }

    val prevMonth = if (month == 0) 11 else month - 1
    val prevYear = if (month == 0) year - 1 else year

    // 1. Confirmed Moves counts (YoY & MoM)
    val confirmedEvents = events.filter { it.isAllDay && it.teamId != null }
    val currentMonthMoves = confirmedEvents.count { isSameMonth(it.startMillis, year, month) }
    val lastYearMonthMoves = confirmedEvents.count { isSameMonth(it.startMillis, year - 1, month) }
    val prevMonthMoves = confirmedEvents.count { isSameMonth(it.startMillis, prevYear, prevMonth) }
    
    val moveCountYoY = if (lastYearMonthMoves > 0) {
        ((currentMonthMoves.toDouble() - lastYearMonthMoves) / lastYearMonthMoves) * 100
    } else {
        0.0
    }
    val moveCountMoM = if (prevMonthMoves > 0) {
        ((currentMonthMoves.toDouble() - prevMonthMoves) / prevMonthMoves) * 100
    } else {
        0.0
    }

    // 2. Estimate counts (YoY & MoM)
    val currentMonthEstimates = uniqueEstimates.count { isSameMonth(it.createdAt, year, month) }
    val lastYearMonthEstimates = uniqueEstimates.count { isSameMonth(it.createdAt, year - 1, month) }
    val prevMonthEstimates = uniqueEstimates.count { isSameMonth(it.createdAt, prevYear, prevMonth) }
    
    val estimateCountYoY = if (lastYearMonthEstimates > 0) {
        ((currentMonthEstimates.toDouble() - lastYearMonthEstimates) / lastYearMonthEstimates) * 100
    } else {
        0.0
    }
    val estimateCountMoM = if (prevMonthEstimates > 0) {
        ((currentMonthEstimates.toDouble() - prevMonthEstimates) / prevMonthEstimates) * 100
    } else {
        0.0
    }

    // Helper function to calculate conversion rate for a given month
    fun getConversionRate(y: Int, m: Int): Double {
        val monthEstimates = uniqueEstimates.filter { isSameMonth(it.createdAt, y, m) }
        val total = monthEstimates.size
        if (total == 0) return 0.0
        val contractedCount = monthEstimates.count { est ->
            events.any { it.linkedEstimateId == est.id && it.isAllDay }
        }
        return (contractedCount.toDouble() / total) * 100
    }

    // 3. Conversion Rate changes (YoY & MoM)
    val conversionRateCurrent = getConversionRate(year, month)
    val lastYearMonthConversionRate = getConversionRate(year - 1, month)
    val prevMonthConversionRate = getConversionRate(prevYear, prevMonth)
    
    val conversionRateYoYChange = conversionRateCurrent - lastYearMonthConversionRate
    val conversionRateMoMChange = conversionRateCurrent - prevMonthConversionRate

    // Helper function to get average contract price for a given month (by move date)
    fun getAverageContractPrice(y: Int, m: Int): Long {
        val targetMonthStr = String.format("%04d-%02d", y, m + 1)
        val contractedInMonth = uniqueEstimates.filter { est ->
            val estEvents = events.filter { it.linkedEstimateId == est.id }
            val hasAllDayInMonth = estEvents.any { evt ->
                evt.isAllDay && Calendar.getInstance().apply { timeInMillis = evt.startMillis }.let {
                    it.get(Calendar.YEAR) == y && it.get(Calendar.MONTH) == m
                }
            }
            val hasMoveDateInMonth = est.moveDate.startsWith(targetMonthStr) && est.moveDate.isNotBlank() && estEvents.any { it.isAllDay }
            hasAllDayInMonth || hasMoveDateInMonth
        }
        if (contractedInMonth.isEmpty()) return 0L
        val totalPrice = contractedInMonth.sumOf { est ->
            val costStr = est.totalCost.replace(Regex("[^0-9]"), "")
            val cost = costStr.toLongOrNull() ?: 0L
            val rawCost = if (cost > 0L) cost else est.amount
            if (rawCost > 0L && rawCost < 10000L) rawCost * 10000L else rawCost
        }
        return totalPrice / contractedInMonth.size
    }

    // 4. Average Price YoY & MoM Growth
    val currentAvgPrice = getAverageContractPrice(year, month)
    val lastYearAvgPrice = getAverageContractPrice(year - 1, month)
    val prevAvgPrice = getAverageContractPrice(prevYear, prevMonth)
    
    val avgPriceYoY = if (lastYearAvgPrice > 0L) {
        ((currentAvgPrice.toDouble() - lastYearAvgPrice) / lastYearAvgPrice) * 100
    } else {
        0.0
    }
    val avgPriceMoM = if (prevAvgPrice > 0L) {
        ((currentAvgPrice.toDouble() - prevAvgPrice) / prevAvgPrice) * 100
    } else {
        0.0
    }

    return GrowthStats(
        currentMonthMoves = currentMonthMoves,
        prevMonthMoves = prevMonthMoves,
        lastYearMonthMoves = lastYearMonthMoves,
        moveCountYoY = moveCountYoY,
        moveCountMoM = moveCountMoM,
        currentMonthEstimates = currentMonthEstimates,
        prevMonthEstimates = prevMonthEstimates,
        lastYearMonthEstimates = lastYearMonthEstimates,
        estimateCountYoY = estimateCountYoY,
        estimateCountMoM = estimateCountMoM,
        conversionRateCurrent = conversionRateCurrent,
        prevMonthConversionRate = prevMonthConversionRate,
        lastYearMonthConversionRate = lastYearMonthConversionRate,
        conversionRateYoYChange = conversionRateYoYChange,
        conversionRateMoMChange = conversionRateMoMChange,
        currentAvgPrice = currentAvgPrice,
        prevAvgPrice = prevAvgPrice,
        lastYearAvgPrice = lastYearAvgPrice,
        avgPriceYoY = avgPriceYoY,
        avgPriceMoM = avgPriceMoM
    )
}
