package com.dayledger.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayledger.app.data.ActivityCategoryEntity
import com.dayledger.app.data.CategoryEntity
import com.dayledger.app.data.MEAL_BREAKFAST
import com.dayledger.app.data.MEAL_DINNER
import com.dayledger.app.data.MEAL_LUNCH
import com.dayledger.app.data.MEAL_SNACK
import com.dayledger.app.data.PLACE_DINEOUT
import com.dayledger.app.data.PLACE_HOME
import com.dayledger.app.data.PLACE_TAKEOUT
import com.dayledger.app.ui.theme.Ink
import com.dayledger.app.util.Fmt
import java.util.Calendar
import kotlin.math.roundToLong

enum class AddMode { Menu, Expense, Time, Meal, Event }

/** 修改已有记录时带进来的数据；kind 决定直接打开哪个编辑器 */
data class EditData(
    val kind: AddMode,
    val id: Long,
    val amountCents: Long = 0,
    val categoryId: Long = 0,
    val note: String = "",
    val activityId: Long = 0,
    val startAt: Long = 0,
    val endAt: Long = 0,
    val type: Int = 0,
    val place: Int = 0,
    val kcal: Int? = null,
    val protein: Int? = null,
    val veg: Int? = null,
    val title: String = "",
    val top3: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    date: String,
    categories: List<CategoryEntity>,
    activities: List<ActivityCategoryEntity>,
    initialMode: AddMode = AddMode.Menu,
    edit: EditData? = null,
    onDismiss: () -> Unit,
    onAddExpense: (Long, Long, String, Long) -> Unit,
    onStartTimer: (Long, String, Int) -> Unit,
    onAddBlock: (Long, Long, Long) -> Unit,
    onAddMeal: (Int, Int, Int?, Int?, Int?, String, Long) -> Unit,
    onAddEvent: (String, Long, Long, Boolean) -> Unit,
    onUpdateExpense: (Long, Long, Long, String) -> Unit = { _, _, _, _ -> },
    onUpdateBlock: (Long, Long, Long, Long) -> Unit = { _, _, _, _ -> },
    onUpdateMeal: (Long, Int, Int, Int?, Int?, Int?, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onUpdateEvent: (Long, String, Long, Long, Boolean) -> Unit = { _, _, _, _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(edit?.kind ?: initialMode) }
    BackHandler(enabled = mode != AddMode.Menu) { mode = AddMode.Menu }

    val expenses = remember(categories) { categories.filter { it.kind == 0 } }
    val base = remember(date) {
        if (date == Fmt.todayKey()) System.currentTimeMillis() else Fmt.startOfDay(date) + 12 * 3600_000L
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            when (mode) {
                AddMode.Menu -> MenuContent(
                    onPick = { mode = it },
                    onDismiss = onDismiss,
                )

                AddMode.Expense -> ExpenseEditor(
                    categories = expenses,
                    title = if (edit != null) "修改支出" else "记一笔",
                    initialCents = edit?.amountCents ?: 0L,
                    initialCategoryId = edit?.categoryId ?: 0L,
                    initialNote = edit?.note ?: "",
                    saveLabel = if (edit != null) "保存修改" else "记下这笔",
                    onBack = { mode = AddMode.Menu },
                    onSave = { cents, catId, note ->
                        if (edit != null) onUpdateExpense(edit.id, cents, catId, note)
                        else onAddExpense(cents, catId, note, base)
                        onDismiss()
                    },
                )

                AddMode.Time -> if (edit != null) {
                    TimeEditContent(
                        activities = activities,
                        initialActivityId = edit.activityId,
                        initialStart = edit.startAt,
                        initialEnd = edit.endAt,
                        onBack = { mode = AddMode.Menu },
                        onSave = { activityId, start, end ->
                            onUpdateBlock(edit.id, activityId, start, end)
                            onDismiss()
                        },
                    )
                } else {
                    TimeEditor(
                        activities = activities,
                        onBack = { mode = AddMode.Menu },
                        onStart = { id, name, target ->
                            onStartTimer(id, name, target)
                            onDismiss()
                        },
                        onManual = { id, minutes, note ->
                            onAddBlock(id, base - minutes * 60_000L, base)
                            onDismiss()
                        },
                    )
                }

                AddMode.Meal -> MealEditor(
                    baseHour = hourOf(base),
                    initialType = edit?.type,
                    initialPlace = edit?.place,
                    initialKcal = edit?.kcal,
                    initialProtein = edit?.protein,
                    initialVeg = edit?.veg,
                    initialNote = edit?.note ?: "",
                    saveLabel = if (edit != null) "保存修改" else "记下这餐",
                    onBack = { mode = AddMode.Menu },
                    onSave = { type, place, kcal, protein, veg, note ->
                        if (edit != null) onUpdateMeal(edit.id, type, place, kcal, protein, veg, note)
                        else onAddMeal(type, place, kcal, protein, veg, note, base)
                        onDismiss()
                    },
                )

                AddMode.Event -> EventEditor(
                    base = base,
                    initialTitle = edit?.title ?: "",
                    initialStart = edit?.startAt ?: roundUpToHalfHour(base),
                    initialMinutes = if (edit != null) ((edit.endAt - edit.startAt) / 60_000L).toInt().coerceAtLeast(15) else 60,
                    initialTop3 = edit?.top3 ?: false,
                    saveLabel = if (edit != null) "保存修改" else "加进日程",
                    onBack = { mode = AddMode.Menu },
                    onSave = { title, start, end, top3 ->
                        if (edit != null) onUpdateEvent(edit.id, title, start, end, top3)
                        else onAddEvent(title, start, end, top3)
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuContent(onPick: (AddMode) -> Unit, onDismiss: () -> Unit) {
    Column {
        Text("记点什么", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "记一笔不超过 3 秒，剩下的交给晚上的复盘。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuCard("记支出", "吃了什么、买了什么", Icons.Rounded.Payments, Ink.money, Modifier.weight(1f)) {
                onPick(AddMode.Expense)
            }
            MenuCard("记时间", "开始计时或补记", Icons.Rounded.Timer, Ink.time, Modifier.weight(1f)) {
                onPick(AddMode.Time)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuCard("记饮食", "早午晚餐和加餐", Icons.Rounded.Restaurant, Ink.diet, Modifier.weight(1f)) {
                onPick(AddMode.Meal)
            }
            MenuCard("加日程", "计划要做什么", Icons.Rounded.CalendarMonth, Ink.event, Modifier.weight(1f)) {
                onPick(AddMode.Event)
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
    }
}

@Composable
private fun MenuCard(title: String, hint: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(104.dp).clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EditorHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseEditor(
    categories: List<CategoryEntity>,
    title: String,
    initialCents: Long = 0L,
    initialCategoryId: Long = 0L,
    initialNote: String = "",
    saveLabel: String = "记下这笔",
    onBack: () -> Unit,
    onSave: (Long, Long, String) -> Unit,
) {
    var amount by remember { mutableStateOf(if (initialCents > 0) Fmt.moneyPlain(initialCents) else "") }
    var categoryId by remember(categories) {
        mutableStateOf(if (initialCategoryId != 0L) initialCategoryId else categories.firstOrNull()?.id ?: 0L)
    }
    var note by remember { mutableStateOf(initialNote) }
    val cents = parseCents(amount)

    Column {
        EditorHeader(title, onBack)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "¥",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp, top = 4.dp),
            )
            Text(
                text = if (amount.isEmpty()) "0" else amount,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (amount.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(
            Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(12.dp))
        Keypad(
            onKey = { k ->
                val canDot = k != "." || !amount.contains('.')
                if (canDot && amount.length < 9) {
                    amount = if (k == "." && amount.isEmpty()) "0." else amount + k
                }
            },
            onDelete = { amount = amount.dropLast(1) },
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c ->
                ChoiceChip(c.name, c.id == categoryId, Color(c.color)) { categoryId = c.id }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("备注（可不填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(cents, categoryId, note) },
            enabled = cents > 0 && categoryId != 0L,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                saveLabel + if (cents > 0) "  ${Fmt.money(cents)}" else "",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeEditor(
    activities: List<ActivityCategoryEntity>,
    onBack: () -> Unit,
    onStart: (Long, String, Int) -> Unit,
    onManual: (Long, Int, String) -> Unit,
) {
    var activityId by remember { mutableStateOf(activities.firstOrNull()?.id ?: 0L) }
    var minutes by remember { mutableIntStateOf(30) }
    var target by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    val selected = activities.firstOrNull { it.id == activityId }

    Column {
        EditorHeader("记时间", onBack)
        Spacer(Modifier.height(10.dp))
        Text("在做什么", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            activities.forEach { a ->
                ChoiceChip(a.name, a.id == activityId, Color(a.color)) { activityId = a.id }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("计时方式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "自由计时", 25 to "番茄 25", 45 to "番茄 45", 60 to "番茄 60").forEach { (t, label) ->
                ChoiceChip(label, t == target, Ink.time) { target = t }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (target == 0) "开始后常驻通知，锁屏也继续计时，随时可以停。"
            else "$target 分钟到点会提醒你休息，提前停也不会丢记录。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onStart(activityId, selected?.name ?: "专注", target) },
            enabled = activityId != 0L,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("现在开始" + if (target > 0) " · $target 分钟" else "计时") }
        Spacer(Modifier.height(18.dp))
        Text(
            "或者补记刚才那一段",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                ChoiceChip("${m} 分钟", m == minutes, Ink.time) { minutes = m }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("备注（可不填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { onManual(activityId, minutes, note) },
            enabled = activityId != 0L,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("补记 ${minutes} 分钟") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealEditor(
    baseHour: Int,
    initialType: Int? = null,
    initialPlace: Int? = null,
    initialKcal: Int? = null,
    initialProtein: Int? = null,
    initialVeg: Int? = null,
    initialNote: String = "",
    saveLabel: String = "记下这餐",
    onBack: () -> Unit,
    onSave: (Int, Int, Int?, Int?, Int?, String) -> Unit,
) {
    var type by remember {
        mutableIntStateOf(
            initialType ?: when {
                baseHour < 10 -> MEAL_BREAKFAST
                baseHour < 15 -> MEAL_LUNCH
                baseHour < 21 -> MEAL_DINNER
                else -> MEAL_SNACK
            },
        )
    }
    var place by remember { mutableIntStateOf(initialPlace ?: PLACE_HOME) }
    var kcal by remember { mutableStateOf(initialKcal?.toString() ?: "") }
    var protein by remember { mutableStateOf(initialProtein?.toString() ?: "") }
    var veg by remember { mutableStateOf(initialVeg?.toString() ?: "") }
    var note by remember { mutableStateOf(initialNote) }

    Column {
        EditorHeader("记饮食", onBack)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MEAL_BREAKFAST to "早餐", MEAL_LUNCH to "午餐", MEAL_DINNER to "晚餐", MEAL_SNACK to "加餐").forEach { (t, label) ->
                ChoiceChip(label, t == type, Ink.diet) { type = t }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(PLACE_HOME to "在家", PLACE_TAKEOUT to "外卖", PLACE_DINEOUT to "外食").forEach { (p, label) ->
                ChoiceChip(label, p == place, Ink.money) { place = p }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("热量 kcal", kcal, Modifier.weight(1f)) { kcal = it }
            NumberField("蛋白 g", protein, Modifier.weight(1f)) { protein = it }
            NumberField("蔬菜 g", veg, Modifier.weight(1f)) { veg = it }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("吃了什么（可不填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onSave(type, place, kcal.toIntOrNull(), protein.toIntOrNull(), veg.toIntOrNull(), note) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(saveLabel) }
        Spacer(Modifier.height(6.dp))
        Text(
            "热量和营养可以留空，先把吃了什么记下来。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventEditor(
    base: Long,
    initialTitle: String = "",
    initialStart: Long = 0L,
    initialMinutes: Int = 60,
    initialTop3: Boolean = false,
    saveLabel: String = "加进日程",
    onBack: () -> Unit,
    onSave: (String, Long, Long, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var startMillis by remember { mutableLongStateOf(if (initialStart > 0) initialStart else roundUpToHalfHour(base)) }
    var minutes by remember { mutableIntStateOf(initialMinutes) }
    var top3 by remember { mutableStateOf(initialTop3) }

    Column {
        EditorHeader("加日程", onBack)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("要做什么") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("开始", Modifier.weight(1f))
            OutlinedButton(onClick = { startMillis -= 30 * 60_000L }) { Text("−30") }
            Text(
                Fmt.hm(startMillis),
                Modifier.width(72.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = { startMillis += 30 * 60_000L }) { Text("+30") }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90, 120).forEach { m ->
                ChoiceChip("${m} 分钟", m == minutes, Ink.event) { minutes = m }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("重要三件事之一", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "每天最多标三件，执行分会看它",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = top3, onCheckedChange = { top3 = it })
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onSave(title.ifBlank { "未命名事项" }, startMillis, startMillis + minutes * 60_000L, top3) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(saveLabel) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeEditContent(
    activities: List<ActivityCategoryEntity>,
    initialActivityId: Long,
    initialStart: Long,
    initialEnd: Long,
    onBack: () -> Unit,
    onSave: (Long, Long, Long) -> Unit,
) {
    var activityId by remember { mutableStateOf(initialActivityId.takeIf { it != 0L } ?: activities.firstOrNull()?.id ?: 0L) }
    var start by remember { mutableLongStateOf(initialStart) }
    var end by remember { mutableLongStateOf(initialEnd.coerceAtLeast(initialStart + 15 * 60_000L)) }
    val minutes = ((end - start) / 60_000L).toInt().coerceAtLeast(0)

    Column {
        EditorHeader("修改时间", onBack)
        Spacer(Modifier.height(10.dp))
        Text("在做什么", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            activities.forEach { a ->
                ChoiceChip(a.name, a.id == activityId, Color(a.color)) { activityId = a.id }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("开始", Modifier.weight(1f))
            OutlinedButton(onClick = { start -= 15 * 60_000L; if (end <= start) end = start + 15 * 60_000L }) { Text("−15") }
            Text(Fmt.hm(start), Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { start += 15 * 60_000L; if (end <= start) end = start + 15 * 60_000L }) { Text("+15") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("结束", Modifier.weight(1f))
            OutlinedButton(onClick = { end -= 15 * 60_000L }) { Text("−15") }
            Text(Fmt.hm(end), Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { end += 15 * 60_000L }) { Text("+15") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "时长 ${Fmt.duration(minutes)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onSave(activityId, start, end) },
            enabled = activityId != 0L && end > start,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("保存修改") }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { if (key == "⌫") onDelete() else onKey(key) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                key,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) onChange(v) },
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

private fun parseCents(text: String): Long {
    val t = text.trim()
    if (t.isEmpty() || t == ".") return 0
    val d = t.toDoubleOrNull() ?: return 0
    return (d * 100).roundToLong()
}

private fun hourOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

private fun roundUpToHalfHour(millis: Long): Long {
    val step = 30 * 60_000L
    return ((millis / step) + 1) * step
}
