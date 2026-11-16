package com.dayledger.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dayledger.app.BuildConfig
import com.dayledger.app.data.AppSettings
import com.dayledger.app.ui.theme.Ink
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    settings: AppSettings,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<EditKey?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = vm.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    }
                }
            }.onSuccess { onToast("已导出，存到你选的位置了") }
                .onFailure { onToast("导出失败：${it.message}") }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text == null) {
                onToast("读取失败")
                return@launch
            }
            vm.importJson(text) { ok -> onToast(if (ok) "导入完成" else "格式不对，没有导入") }
        }
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        item {
            SectionCard(title = "目标与预算") {
                SettingRow("月预算", Fmt.money(settings.monthlyBudgetCents)) { editing = EditKey.Budget }
                SettingRow("日预算（自动）", Fmt.money(settings.dailyBudgetCents))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                SettingRow("每日热量", "${settings.calorieTarget} kcal") { editing = EditKey.Calorie }
                SettingRow("蛋白质目标", "${settings.proteinTargetG} g") { editing = EditKey.Protein }
                SettingRow("体重", "${settings.weightKg} kg") { editing = EditKey.Weight }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                SettingRow("专注目标", Fmt.duration(settings.focusTargetMin)) { editing = EditKey.Focus }
                SettingRow("运动目标", Fmt.duration(settings.sportTargetMin)) { editing = EditKey.Sport }
                SettingRow("睡眠目标", Fmt.duration(settings.sleepTargetMin)) { editing = EditKey.Sleep }
                MinuteStepper("就寝目标", settings.bedTargetMin) { v ->
                    vm.updateSettings { it.copy(bedTargetMin = ((v % 1440) + 1440) % 1440) }
                }
            }
        }

        item {
            SectionCard(title = "评分") {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("显示分数", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "关掉后只看建议，不看分",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.showScore,
                        onCheckedChange = { v -> vm.updateSettings { it.copy(showScore = v) } },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("严格度", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.7f to "宽松", 1.0f to "标准", 1.3f to "严格").forEach { (v, label) ->
                        ChoiceChip(label, kotlin.math.abs(settings.strictness - v) < 0.05f, Ink.warn) {
                            vm.updateSettings { it.copy(strictness = v) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "严格度只放大或缩小扣分，不影响权重。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "提醒") {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("日程提醒", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "开始前 10 分钟提醒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.remindersEnabled,
                        onCheckedChange = { vm.setRemindersEnabled(it) },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每晚复盘", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "22:00 提醒你记一下今天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.reviewReminderEnabled,
                        onCheckedChange = { vm.setReviewReminder(it) },
                    )
                }
                Text(
                    "用的是系统不精确闹钟，不需要「精确闹钟」特殊权限；只申请通知权限。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "数据") {
                Text(
                    "所有数据只在这台手机上。换手机、重装前，先导出一份 JSON。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = {
                        exportLauncher.launch("dayledger-" + Fmt.todayKey() + ".json")
                    }) { Text("导出备份") }
                    TextButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }) { Text("导入备份") }
                }
                TextButton(onClick = { confirmClear = true }) {
                    Text("清空所有记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            SectionCard(title = "关于") {
                SettingRow("版本", "v${BuildConfig.VERSION_NAME} · 第一版")
                Spacer(Modifier.height(4.dp))
                Text(
                    "日计不需要联网，也没有任何网络权限。分数是本地规则算出来的：可解释、可复现，改数据就重算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { key ->        NumberDialog(
            title = key.title,
            initial = key.initial(settings),
            suffix = key.suffix,
            onDismiss = { editing = null },
            onConfirm = { value ->
                val v = value.toIntOrNull()
                if (v != null && v > 0) {
                    when (key) {
                        EditKey.Budget -> vm.updateSettings { it.copy(monthlyBudgetCents = v * 100L) }
                        EditKey.Calorie -> vm.updateSettings { it.copy(calorieTarget = v) }
                        EditKey.Protein -> vm.updateSettings { it.copy(proteinTargetG = v) }
                        EditKey.Weight -> vm.updateSettings { it.copy(weightKg = v.coerceIn(30, 200)) }
                        EditKey.Focus -> vm.updateSettings { it.copy(focusTargetMin = v) }
                        EditKey.Sport -> vm.updateSettings { it.copy(sportTargetMin = v) }
                        EditKey.Sleep -> vm.updateSettings { it.copy(sleepTargetMin = v) }
                    }
                }
                editing = null
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有记录？") },
            text = {
                Text("记账、时间、饮食、日程和复盘都会删除，分类与设置保留。这个操作不能撤销，建议先导出一份备份。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllRecords()
                    confirmClear = false
                    onToast("已清空，从今天重新开始")
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

private enum class EditKey(val title: String, val suffix: String) {
    Budget("月预算", "元"),
    Calorie("每日热量目标", "kcal"),
    Protein("蛋白质目标", "g"),
    Weight("体重", "kg"),
    Focus("专注目标", "分钟"),
    Sport("运动目标", "分钟"),
    Sleep("睡眠目标", "分钟");

    fun initial(s: AppSettings): String = when (this) {
        Budget -> (s.monthlyBudgetCents / 100).toString()
        Calorie -> s.calorieTarget.toString()
        Protein -> s.proteinTargetG.toString()
        Weight -> s.weightKg.toString()
        Focus -> s.focusTargetMin.toString()
        Sport -> s.sportTargetMin.toString()
        Sleep -> s.sleepTargetMin.toString()
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NumberDialog(
    title: String,
    initial: String,
    suffix: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> if (v.length <= 9 && v.all { it.isDigit() }) text = v },
                suffix = { Text(suffix) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
