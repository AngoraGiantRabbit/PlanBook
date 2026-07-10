package com.example.planbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.viewmodel.ReviewViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 复盘 Tab：月历（PRD 4.4.3）。
 * 今天高亮；有复盘内容的日期下方加小圆点；左右滑动/点击切月；点某天进编辑页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onOpenReview: (String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val state by viewModel.calendarState.collectAsState()
    val today = LocalDate.now()
    val monthFmt = DateTimeFormatter.ofPattern("yyyy年 M月")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.month.format(monthFmt)) },
                actions = {
                    IconButton(onClick = { viewModel.changeMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上月")
                    }
                    IconButton(onClick = { viewModel.changeMonth(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下月")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
        ) {
            // 星期表头（周一~周日）
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekLabels = listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                )
                weekLabels.forEach { dow ->
                    Text(
                        text = dow.getDisplayName(TextStyle.NARROW, Locale.CHINA),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = if (dow == DayOfWeek.SUNDAY || dow == DayOfWeek.SATURDAY)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // 日历网格
            MonthGrid(
                month = state.month,
                today = today,
                reviewDates = state.reviewDates,
                onDayClick = { date -> onOpenReview(date) }
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    today: LocalDate,
    reviewDates: Set<String>,
    onDayClick: (String) -> Unit
) {
    val ym = YearMonth.from(month)
    val firstDay = ym.atDay(1)
    // 周一为一周第一天：offset = (weekday - 1)，Monday=1 → 0
    val offset = (firstDay.dayOfWeek.value - 1)
    val daysInMonth = ym.lengthOfMonth()

    val cells = mutableListOf<LocalDate?>()
    repeat(offset) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(ym.atDay(d))
    // 补齐到 7 的倍数
    while (cells.size % 7 != 0) cells.add(null)

    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (date != null && date == today)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable(enabled = date != null) {
                                date?.let { onDayClick(it.toString()) }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (date != null) {
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (date == today) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal
                                )
                                // 有复盘内容的日期下方加小圆点（PRD 4.4.3）
                                if (date.toString() in reviewDates) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary)
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
