package com.dayledger.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayledger.app.ui.theme.Ink
import com.dayledger.app.util.Fmt
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun CalendarScreen(
    month: YearMonth,
    scores: Map<String, Float>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onOpenDate: (String) -> Unit,
) {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val rows = cells.chunked(7)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                Fmt.monthLabel(month.year, month.monthValue),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onThisMonth) { Text("本月") }
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月")
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        rows.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(0.92f))
                    } else {
                        DayCell(Modifier.weight(1f), date, scores[date.toString()], onOpenDate)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(0.92f)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        MonthSummary(scores)

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(null to "未记录", 55f to "偏弱", 68f to "一般", 78f to "不错", 90f to "很好").forEach { (s, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .background(
                                if (s == null) MaterialTheme.colorScheme.surfaceVariant else Ink.score(s).copy(alpha = 0.55f),
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun DayCell(modifier: Modifier, date: LocalDate, score: Float?, onOpen: (String) -> Unit) {
    val key = date.toString()
    val today = Fmt.isToday(key)
    val bg = if (score == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    else Ink.score(score).copy(alpha = 0.16f)
    Box(
        modifier
            .aspectRatio(0.92f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { onOpen(key) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (score != null) {
                Text(
                    score.roundToInt().toString(),
                    fontSize = 9.sp,
                    color = Ink.score(score),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MonthSummary(scores: Map<String, Float>) {
    val values = scores.values.toList()
    SectionCard(title = "这个月") {
        if (values.isEmpty()) {
            Text(
                "还没有记录。点任意一天可以补记，补记也算数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric("${values.size}", "记录天数")
                Metric("${values.average().roundToInt()}", "平均分", Ink.score(values.average().toFloat()))
                Metric(values.maxOrNull()?.roundToInt()?.toString() ?: "—", "最好", Ink.good)
                Metric(values.minOrNull()?.roundToInt()?.toString() ?: "—", "最差", Ink.bad)
            }
        }
    }
}
