package com.dayledger.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dayledger.app.data.EVENT_DONE
import com.dayledger.app.data.EVENT_SKIPPED
import com.dayledger.app.data.MEAL_BREAKFAST
import com.dayledger.app.data.MEAL_DINNER
import com.dayledger.app.data.MEAL_LUNCH
import com.dayledger.app.data.MEAL_SNACK
import com.dayledger.app.data.PLACE_DINEOUT
import com.dayledger.app.data.PLACE_HOME
import com.dayledger.app.data.PLACE_TAKEOUT
import com.dayledger.app.ui.theme.Ink
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class TimelineRow(
    val key: String,
    val at: Long,
    val time: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val trailing: String?,
    val kind: Kind,
    val refId: Long,
    val eventStatus: Int = -1,
) {
    enum class Kind { TX, TIME, MEAL, EVENT }
}

@Composable
fun TodayScreen(
    day: DayUi,
    onAddExpense: () -> Unit,
    onAddTime: () -> Unit,
    onAddMeal: () -> Unit,
    onAddEvent: () -> Unit,
    onReview: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onStopTimer: () -> Unit,
    onDeleteTx: (Long) -> Unit,
    onDeleteBlock: (Long) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onCompleteEvent: (Long) -> Unit,
    onSkipEvent: (Long) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onEditRow: (TimelineRow) -> Unit,
    onAddWater: (Int) -> Unit,
) {
    val rows = remember(day) { buildRows(day) }
    var selected by remember { mutableStateOf<TimelineRow?>(null) }
    var focusOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DayHeader(day, onPrevDay, onNextDay, onToday)
        }
        if (day.running != null) {
            item { RunningCard(day, onStopTimer, onOpen = { focusOpen = true }) }
        }
        item { SummaryCard(day, onAddWater) }
        item { QuickActions(onAddExpense, onAddTime, onAddMeal, onAddEvent) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("今天的时间轴", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (rows.isEmpty()) "" else "${rows.size} 条记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rows.isEmpty()) {
            item {
                SectionCard {
                    EmptyState(
                        "这一天还是空白的",
                        "记一笔、开个计时，或者把今天要做的事写下来。\n记录从一条开始就够了。",
                        "记第一笔",
                        onAddExpense,
                    )
                }
            }
        } else {
            items(rows, key = { it.key }) { row ->
                TimelineItem(row, onClick = { selected = row })
            }
        }
        item { ReviewCard(day, onReview) }
    }

    selected?.let { row ->
        ItemDialog(
            row = row,
            onDismiss = { selected = null },
            onDelete = {
                when (row.kind) {
                    TimelineRow.Kind.TX -> onDeleteTx(row.refId)
                    TimelineRow.Kind.TIME -> onDeleteBlock(row.refId)
                    TimelineRow.Kind.MEAL -> onDeleteMeal(row.refId)
                    TimelineRow.Kind.EVENT -> onDeleteEvent(row.refId)
                }
                selected = null
            },
            onComplete = { onCompleteEvent(row.refId); selected = null },
            onSkip = { onSkipEvent(row.refId); selected = null },
            onEdit = { onEditRow(row); selected = null },
        )
    }

    if (focusOpen && day.running != null) {
        FocusDialog(
            day = day,
            onStop = { onStopTimer(); focusOpen = false },
            onDismiss = { focusOpen = false },
        )
    }
}

@Composable
private fun DayHeader(day: DayUi, onPrevDay: () -> Unit, onNextDay: () -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (Fmt.isToday(day.date)) "今天" else Fmt.weekday(Fmt.parse(day.date)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (Fmt.isToday(day.date)) {
                    val hour = java.time.LocalTime.now().hour
                    val greeting = when (hour) {
                        in 5..10 -> "早上好"
                        in 11..13 -> "中午好"
                        in 14..17 -> "下午好"
                        in 18..22 -> "晚上好"
                        else -> "夜深了"
                    }
                    Text(
                        " · $greeting",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!Fmt.isToday(day.date)) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onToday, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("回到今天", fontSize = 12.sp)
                    }
                }
            }
            Text(
                Fmt.dateLabel(day.date),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val s = day.score
            Text(
                text = when {
                    !day.settings.showScore -> "分数已隐藏"
                    !day.hasData -> "还没有数据"
                    !day.scoreReady -> "记录还不够，先继续记"
                    else -> "可信度 ${Fmt.pct(s.confidence)} · ${s.confidenceLabel}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreRing(
                score = if (day.settings.showScore && day.scoreReady) day.score.total else null,
                grade = if (day.scoreReady) day.score.grade else "—",
                emptyLabel = if (day.hasData) "记录不够" else "未记录",
            )
            Spacer(Modifier.height(2.dp))
            Row {
                TextButton(onClick = onPrevDay, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                    Text("‹", fontSize = 18.sp)
                }
                TextButton(onClick = onNextDay, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                    Text("›", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(day: DayUi, onAddWater: (Int) -> Unit) {
    val f = day.facts
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(Fmt.money(f.expenseCents), "支出 / ${Fmt.money(day.settings.dailyBudgetCents)}", Ink.money)
            Metric(Fmt.durationShort(f.productiveMinutes), "专注", Ink.time)
            Metric("${f.eventsDone}/${f.events.size}", "事项", Ink.event)
            Metric(f.kcal?.toString() ?: "—", "热量", Ink.diet)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniProgress(
                "预算",
                (f.expenseCents.toFloat() / day.settings.dailyBudgetCents.coerceAtLeast(1L)),
                Ink.money,
                Modifier.weight(1f),
                overIsBad = true,
            )
            MiniProgress(
                "专注",
                f.productiveMinutes.toFloat() / day.settings.focusTargetMin.coerceAtLeast(1),
                Ink.time,
                Modifier.weight(1f),
            )
            MiniProgress(
                "运动",
                f.sportMinutes.toFloat() / day.settings.sportTargetMin.coerceAtLeast(1),
                Ink.good,
                Modifier.weight(1f),
            )
            MiniProgress(
                "喝水",
                f.waterMl / 1500f,
                Ink.diet,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = Ink.time,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("今天喝了 ${f.waterMl} ml", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onAddWater(250) }) { Text("+250 ml") }
        }
    }
}

@Composable
private fun MiniProgress(label: String, value: Float, color: Color, modifier: Modifier = Modifier, overIsBad: Boolean = false) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${(value * 100).toInt().coerceAtMost(999)}%",
                fontSize = 10.sp,
                color = if (overIsBad && value > 1f) Ink.bad else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(color, androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

@Composable
private fun QuickActions(
    onAddExpense: () -> Unit,
    onAddTime: () -> Unit,
    onAddMeal: () -> Unit,
    onAddEvent: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction("记一笔", Icons.Rounded.Payments, Ink.money, Modifier.weight(1f), onAddExpense)
        QuickAction("计时", Icons.Rounded.Timer, Ink.time, Modifier.weight(1f), onAddTime)
        QuickAction("饮食", Icons.Rounded.Restaurant, Ink.diet, Modifier.weight(1f), onAddMeal)
        QuickAction("日程", Icons.Rounded.CalendarMonth, Ink.event, Modifier.weight(1f), onAddEvent)
    }
}

@Composable
private fun QuickAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(34.dp).background(color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RunningCard(day: DayUi, onStop: () -> Unit, onOpen: () -> Unit) {
    val block = day.running ?: return
    val activity = day.snapshot.activities.firstOrNull { it.id == block.activityId }
    val color = Color(activity?.color ?: 0xFF4A7FB5.toInt())
    val elapsed by produceState(0L, block.id) {
        while (true) {
            value = ((System.currentTimeMillis() - block.startAt) / 1000L).coerceAtLeast(0L)
            delay(1000)
        }
    }
    SectionCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("进行中 · ${activity?.name ?: "活动"}", style = MaterialTheme.typography.titleMedium)
                Text(
                    String.format("%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60),
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                )
            }
            FilledTonalButton(onClick = onStop) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("停止")
            }
        }
    }
}

@Composable
private fun TimelineItem(row: TimelineRow, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(row.color.copy(alpha = 0.9f)),
            )
            Row(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.time,
                    modifier = Modifier.width(46.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.size(8.dp).background(row.color, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        row.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (row.trailing != null) {
                    Text(
                        row.trailing,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (row.kind == TimelineRow.Kind.TX) Ink.money else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(day: DayUi, onReview: () -> Unit) {
    val review = day.snapshot.review
    SectionCard(
        title = "今天的复盘",
        trailing = {
            TextButton(onClick = onReview) { Text(if (review == null) "写复盘" else "修改") }
        },
    ) {
        if (review == null) {
            Text(
                "点一下心情和精力，填一下昨晚几点睡，今天的分数就完整了。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(review.mood?.let { "★".repeat(it) } ?: "—", "心情")
                Metric(review.energy?.let { "★".repeat(it) } ?: "—", "精力")
                Metric("${review.waterMl}ml", "喝水", Ink.time)
                val sleep = if (review.bedMinute != null && review.wakeMinute != null)
                    Fmt.durationShort(com.dayledger.app.domain.sleepDurationMinutes(review.bedMinute, review.wakeMinute))
                else null
                Metric(sleep ?: "—", "睡眠")
            }
        }
    }
}

@Composable
private fun ItemDialog(
    row: TimelineRow,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.title) },
        text = {
            Column {
                Text(row.subtitle, style = MaterialTheme.typography.bodyMedium)
                if (row.trailing != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(row.trailing, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onEdit) { Text("修改") }
                if (row.kind == TimelineRow.Kind.EVENT) {
                    Button(onClick = onComplete) { Text("已完成") }
                } else {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        },
        dismissButton = {
            Row {
                if (row.kind == TimelineRow.Kind.EVENT) {
                    TextButton(onClick = onSkip) { Text("放弃") }
                }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
private fun FocusDialog(day: DayUi, onStop: () -> Unit, onDismiss: () -> Unit) {
    val block = day.running ?: return
    val activity = day.snapshot.activities.firstOrNull { it.id == block.activityId }
    val color = Color(activity?.color ?: 0xFF4A7FB5.toInt())
    val elapsed by produceState(0L, block.id) {
        while (true) {
            value = ((System.currentTimeMillis() - block.startAt) / 1000L).coerceAtLeast(0L)
            delay(1000)
        }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "scale",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("专注中", style = MaterialTheme.typography.labelLarge, color = color)
                Spacer(Modifier.height(6.dp))
                Text(activity?.name ?: "专注", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(280.dp)
                        .scale(scale)
                        .background(color.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            String.format("%02d:%02d", elapsed / 60, elapsed % 60),
                            fontSize = 58.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                        Text(
                            "已专注",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "锁屏也会继续计时，结束会自动记进今天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("结束这一轮", style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = onDismiss) { Text("回到今天") }
            }
        }
    }
}

private fun buildRows(day: DayUi): List<TimelineRow> {
    val s = day.snapshot
    val list = ArrayList<TimelineRow>()
    s.transactions.forEach { t ->
        val cat = s.categories.firstOrNull { it.id == t.categoryId }
        list += TimelineRow(
            key = "tx-${t.id}",
            at = t.occurredAt,
            time = Fmt.hm(t.occurredAt),
            title = cat?.name ?: "未分类",
            subtitle = t.note.ifBlank { if (t.direction == 1) "收入" else "支出" },
            color = Color(cat?.color ?: 0xFF8D8579.toInt()),
            trailing = Fmt.money(t.amountCents),
            kind = TimelineRow.Kind.TX,
            refId = t.id,
        )
    }
    s.blocks.forEach { b ->
        val end = b.endAt ?: return@forEach
        val act = s.activities.firstOrNull { it.id == b.activityId }
        val mins = ((end - b.startAt) / 60000L).toInt().coerceAtLeast(0)
        list += TimelineRow(
            key = "blk-${b.id}",
            at = b.startAt,
            time = Fmt.hm(b.startAt),
            title = act?.name ?: "活动",
            subtitle = "持续 ${Fmt.durationShort(mins)} · ${Fmt.hm(b.startAt)}–${Fmt.hm(end)}" + if (b.note.isNotBlank()) " · ${b.note}" else "",
            color = Color(act?.color ?: 0xFF4A7FB5.toInt()),
            trailing = null,
            kind = TimelineRow.Kind.TIME,
            refId = b.id,
        )
    }
    s.meals.forEach { m ->
        val type = when (m.type) {
            MEAL_BREAKFAST -> "早餐"
            MEAL_LUNCH -> "午餐"
            MEAL_DINNER -> "晚餐"
            MEAL_SNACK -> "加餐"
            else -> "饮食"
        }
        val place = when (m.place) {
            PLACE_HOME -> "在家"
            PLACE_TAKEOUT -> "外卖"
            PLACE_DINEOUT -> "外食"
            else -> ""
        }
        val bits = buildList {
            add(place)
            if (m.kcal != null) add("${m.kcal} kcal")
            if (m.proteinG != null) add("蛋白 ${m.proteinG}g")
            if (m.note.isNotBlank()) add(m.note)
        }.filter { it.isNotBlank() }.joinToString(" · ")
        list += TimelineRow(
            key = "meal-${m.id}",
            at = m.occurredAt,
            time = Fmt.hm(m.occurredAt),
            title = type,
            subtitle = bits,
            color = Ink.diet,
            trailing = null,
            kind = TimelineRow.Kind.MEAL,
            refId = m.id,
        )
    }
    s.events.forEach { e ->
        val status = when (e.status) {
            EVENT_DONE -> "已完成"
            EVENT_SKIPPED -> "已放弃"
            else -> "计划中"
        }
        list += TimelineRow(
            key = "ev-${e.id}",
            at = e.startAt,
            time = Fmt.hm(e.startAt),
            title = (if (e.top3) "★ " else "") + e.title,
            subtitle = "$status · ${Fmt.hm(e.startAt)}–${Fmt.hm(e.endAt)}",
            color = Ink.event,
            trailing = null,
            kind = TimelineRow.Kind.EVENT,
            refId = e.id,
            eventStatus = e.status,
        )
    }
    return list.sortedBy { it.at }
}
