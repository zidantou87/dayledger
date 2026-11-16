package com.dayledger.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayledger.app.domain.DimScore
import com.dayledger.app.domain.MonthlyReport
import com.dayledger.app.domain.Reports
import com.dayledger.app.domain.WeeklyReport
import com.dayledger.app.ui.theme.Ink
import com.dayledger.app.util.Fmt
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReportScreen(
    day: DayUi,
    week: WeeklyReport?,
    weekStart: String,
    month: java.time.YearMonth,
    monthReport: MonthlyReport?,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "报告",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
        )
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("日报") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("周报") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("月报") })
        }
        when (tab) {
            0 -> DailyTab(day)
            1 -> WeeklyTab(week, weekStart, onPrevWeek, onNextWeek, onThisWeek)
            else -> MonthTab(month, monthReport, onPrevMonth, onNextMonth, onThisMonth)
        }
    }
}

@Composable
private fun DailyTab(day: DayUi) {
    val report = remember(day) { Reports.daily(day.facts, day.score) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dateLabel(day.date), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    if (!day.hasData) "还没有数据" else "${report.score.grade} · 可信度 ${Fmt.pct(report.score.confidence)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!day.hasData || !day.scoreReady) {
            item {
                SectionCard {
                    EmptyState(
                        if (!day.hasData) "今天还没有可分的事" else "数据还不够成报告",
                        "记账、时间、饮食、复盘，任意两样有记录就能出报告。",
                    )
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(day.score.total, day.score.grade, size = 104.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(report.headline, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "原始分 ${day.score.raw.roundToInt()} · 可信度 ${Fmt.pct(day.score.confidence)}（${day.score.confidenceLabel}）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                day.score.dims.forEach { dim ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = if (dim.key in expanded) expanded - dim.key else expanded + dim.key
                            },
                    ) {
                        DimRow(dim)
                        if (dim.key in expanded) {
                            Column(Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                                dim.evidence.forEach {
                                    Text(
                                        "· $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "点任意一行看扣分依据 · 财务只记录，不计入总分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (report.good.isNotEmpty()) {
            item {
                SectionCard(title = "做得好的") {
                    Bullets(report.good, Ink.good, "＋")
                }
            }
        }
        if (report.improve.isNotEmpty()) {
            item {
                SectionCard(title = "可以改的") {
                    Bullets(report.improve, Ink.bad, "－")
                }
            }
        }
        if (report.suggestions.isNotEmpty()) {
            item {
                SectionCard(title = "明天先做这三件") {
                    Bullets(report.suggestions, MaterialTheme.colorScheme.primary, "→")
                }
            }
        }

        item {
            SectionCard(title = "今天的账") {
                val f = day.facts
                StatRow("支出", Fmt.money(f.expenseCents) + " / " + Fmt.money(day.settings.dailyBudgetCents), Ink.money)
                f.spendByCategory.entries.take(4).forEach { (cat, cents) ->
                    HBar(cat, Fmt.money(cents), cents.toFloat() / f.expenseCents.coerceAtLeast(1L), Ink.money)
                }
                Spacer(Modifier.height(8.dp))
                StatRow("专注", Fmt.duration(f.productiveMinutes), Ink.time)
                f.blocks.groupBy { it.activity }.mapValues { e -> e.value.sumOf { it.minutes } }
                    .entries.sortedByDescending { it.value }.take(4).forEach { (name, mins) ->
                        HBar(name, Fmt.durationShort(mins), mins.toFloat() / f.recordedMinutes.coerceAtLeast(1), Ink.time)
                    }
                Spacer(Modifier.height(8.dp))
                StatRow("三餐", listOfNotNull(
                    if (f.hasBreakfast) "早" else null,
                    if (f.hasLunch) "午" else null,
                    if (f.hasDinner) "晚" else null,
                ).joinToString(" · ").ifEmpty { "未记" }, Ink.diet)
                StatRow("热量", f.kcal?.let { "$it kcal" } ?: "未填", Ink.diet)
                StatRow("喝水", "${f.waterMl} ml", Ink.time)
            }
        }
    }
}

@Composable
private fun MonthTab(
    month: java.time.YearMonth,
    report: MonthlyReport?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThis: () -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) }
                Column(Modifier.weight(1f)) {
                    Text(Fmt.monthLabel(month.year, month.monthValue), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (month == java.time.YearMonth.now()) "本月" else "历史月",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onThis) { Text("本月") }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
            }
        }

        if (report == null || report.avg == null) {
            item {
                SectionCard {
                    EmptyState("这个月还没有数据", "从今天开始记，月底就能看到整月的样子。")
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Metric("${report.avg!!.roundToInt()}", "月分", Ink.score(report.avg))
                    Metric("${report.scoredDays}", "记录天数")
                    Metric("${report.sportDays}", "运动天数", Ink.good)
                    Metric(
                        report.trend?.let { (if (it >= 0) "+" else "") + String.format("%.1f", it) } ?: "—",
                        "较上月",
                        if ((report.trend ?: 0f) >= 0) Ink.good else Ink.bad,
                    )
                }
            }
        }

        if (report.weekAverages.isNotEmpty()) {
            item {
                SectionCard(title = "每周平均") {
                    report.weekAverages.forEachIndexed { index, value ->
                        HBar("第 ${index + 1} 周", value?.roundToInt()?.toString() ?: "—", (value ?: 0f) / 100f, Ink.score(value))
                    }
                }
            }
        }

        item {
            SectionCard(title = "维度平均") {
                report.dimAverages.forEach { (label, value) ->
                    HBar(label, value.roundToInt().toString(), value / 100f, Ink.score(value))
                }
            }
        }

        item {
            SectionCard(title = "这个月的钱") {
                StatRow("总支出", Fmt.money(report.totalSpendCents), Ink.money)
                StatRow("日均", Fmt.money(report.totalSpendCents / 30), Ink.money)
                val max = report.spendByCategory.firstOrNull()?.second ?: 1L
                report.spendByCategory.forEach { (cat, cents) ->
                    HBar(cat, Fmt.money(cents), cents.toFloat() / max, Ink.money)
                }
            }
        }

        item {
            SectionCard(title = "这个月的时间") {
                StatRow("专注", Fmt.duration(report.productiveMinutes), Ink.time)
                StatRow("运动", "${Fmt.duration(report.sportMinutes)} · ${report.sportDays} 天", Ink.good)
                StatRow("完成率", report.completionRate?.let { Fmt.pct(it) } ?: "—", Ink.event)
                val max = report.topActivities.firstOrNull()?.second ?: 1
                report.topActivities.forEach { (name, mins) ->
                    HBar(name, Fmt.durationShort(mins), mins.toFloat() / max, Ink.time)
                }
            }
        }

        item {
            SectionCard(title = "这个月的吃") {
                StatRow("平均热量", report.avgKcal?.let { "$it kcal" } ?: "未填", Ink.diet)
                StatRow("外卖", "${report.takeoutCount} 次", MaterialTheme.colorScheme.onSurface)
                StatRow("夜宵", "${report.lateSnackDays} 天", if (report.lateSnackDays >= 4) Ink.bad else MaterialTheme.colorScheme.onSurface)
            }
        }

        if (report.insights.isNotEmpty()) {
            item { SectionCard(title = "这个月发生了什么") { Bullets(report.insights, Ink.warn, "·") } }
        }
        if (report.suggestions.isNotEmpty()) {
            item { SectionCard(title = "下个月") { Bullets(report.suggestions, MaterialTheme.colorScheme.primary, "→") } }
        }

        item {
            SectionCard {
                Text(
                    "月报只统计有记录的日子；没有记录的天不会把你拉低。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyTab(
    week: WeeklyReport?,
    weekStart: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThis: () -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) }
                Column(Modifier.weight(1f)) {
                    Text("${Fmt.shortDate(weekStart)} – ${Fmt.shortDate(Fmt.parse(weekStart).plusDays(6).toString())}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (Fmt.parse(weekStart) == Fmt.weekStart(Fmt.today())) "本周" else "历史周",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onThis) { Text("本周") }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
            }
        }

        if (week == null || week.avg == null) {
            item {
                SectionCard {
                    EmptyState("这一周还没有数据", "从今天开始记，周日就能看到第一份周报。")
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Metric("${week.avg!!.roundToInt()}", "周分", Ink.score(week.avg))
                    Metric(week.stability?.roundToInt()?.toString() ?: "—", "稳定性", Ink.time)
                    Metric(
                        week.trend?.let { (if (it >= 0) "+" else "") + String.format("%.1f", it) } ?: "—",
                        "较上周",
                        if ((week.trend ?: 0f) >= 0) Ink.good else Ink.bad,
                    )
                    Metric("${week.points.count { (it.score ?: 0f) >= 70f }}/7", "达标", Ink.event)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (week.trend == null) "上周没有数据，下周就能比较了"
                    else if (week.trend > 0) "比上周好 ${String.format("%.1f", week.trend)} 分"
                    else if (week.trend < 0) "比上周低 ${String.format("%.1f", abs(week.trend))} 分"
                    else "和上周持平",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionCard(title = "每天") { WeekBars(week.points) } }

        item {
            SectionCard(title = "维度平均") {
                week.dimAverages.forEach { (label, value) ->
                    HBar(label, value.roundToInt().toString(), value / 100f, Ink.score(value))
                }
            }
        }

        item {
            SectionCard(title = "这一周的钱") {
                StatRow("总支出", Fmt.money(week.totalSpendCents), Ink.money)
                StatRow("日均", Fmt.money(week.totalSpendCents / 7), Ink.money)
                StatRow("外卖", "${week.takeoutCount} 次", if (week.takeoutCount >= 4) Ink.bad else MaterialTheme.colorScheme.onSurface)
                val max = week.spendByCategory.firstOrNull()?.second ?: 1L
                week.spendByCategory.forEach { (cat, cents) ->
                    HBar(cat, Fmt.money(cents), cents.toFloat() / max, Ink.money)
                }
            }
        }

        item {
            SectionCard(title = "这一周的时间") {
                StatRow("专注", Fmt.duration(week.productiveMinutes), Ink.time)
                StatRow("完成率", week.completionRate?.let { Fmt.pct(it) } ?: "—", Ink.event)
                val max = week.minutesByActivity.firstOrNull()?.second ?: 1
                week.minutesByActivity.forEach { (name, mins) ->
                    HBar(name, Fmt.durationShort(mins), mins.toFloat() / max, Ink.time)
                }
            }
        }

        item {
            SectionCard(title = "这一周的吃") {
                StatRow("平均热量", week.avgKcal?.let { "$it kcal" } ?: "未填", Ink.diet)
                StatRow("夜宵", "${week.lateSnackCount} 天", if (week.lateSnackCount >= 2) Ink.bad else MaterialTheme.colorScheme.onSurface)
                StatRow("外卖", "${week.takeoutCount} 次", MaterialTheme.colorScheme.onSurface)
            }
        }

        if (week.insights.isNotEmpty()) {
            item {
                SectionCard(title = "看到了这些") { Bullets(week.insights, Ink.warn, "·") }
            }
        }
        if (week.suggestions.isNotEmpty()) {
            item {
                SectionCard(title = "下周这么办") { Bullets(week.suggestions, MaterialTheme.colorScheme.primary, "→") }
            }
        }
        item {
            SectionCard {
                Text(
                    "周报每周日晚自动重算一次；数据变了，报告跟着变。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Bullets(items: List<String>, color: Color, prefix: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { text ->
            Row {
                Text(prefix, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
