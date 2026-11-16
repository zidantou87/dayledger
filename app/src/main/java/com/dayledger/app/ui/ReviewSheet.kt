package com.dayledger.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayledger.app.data.DayReviewEntity
import com.dayledger.app.ui.theme.Ink
import com.dayledger.app.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    date: String,
    review: DayReviewEntity?,
    onDismiss: () -> Unit,
    onWater: (Int) -> Unit,
    onSave: (Int?, Int?, Int?, Int?, Boolean, String) -> Unit,
) {
    var mood by remember(date) { mutableIntStateOf(review?.mood ?: 3) }
    var energy by remember(date) { mutableIntStateOf(review?.energy ?: 3) }
    var bed by remember(date) { mutableIntStateOf(review?.bedMinute ?: 1410) }
    var wake by remember(date) { mutableIntStateOf(review?.wakeMinute ?: 450) }
    var noSpend by remember(date) { mutableStateOf(review?.noSpend ?: false) }
    var note by remember(date) { mutableStateOf(review?.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text("今天的复盘", style = MaterialTheme.typography.headlineSmall)
            Text(
                Fmt.dateLabel(date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            Rating("心情", mood) { mood = it }
            Spacer(Modifier.height(8.dp))
            Rating("精力", energy) { energy = it }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(6.dp))

            MinuteStepper("昨晚几点睡", bed) { bed = wrap(it) }
            MinuteStepper("今早几点起", wake) { wake = wrap(it) }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("睡眠 ${Fmt.duration(com.dayledger.app.domain.sleepDurationMinutes(bed, wake))}", Modifier.weight(1f))
                Text(Fmt.hm(bed) + " — " + Fmt.hm(wake), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("喝水", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${review?.waterMl ?: 0} ml",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink.time,
                    )
                }
                TextButton(onClick = { onWater(-250) }) { Text("−250") }
                TextButton(onClick = { onWater(250) }) { Text("+250") }
                TextButton(onClick = { onWater(500) }) { Text("+500") }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今天没花钱", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "给没消费的一天一个交代",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = noSpend, onCheckedChange = { noSpend = it })
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("今天想记住的一句话") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onSave(mood, energy, bed, wake, noSpend, note) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("存下今天的复盘") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Rating(label: String, value: Int?, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(48.dp), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { i ->
                Box(
                    Modifier
                        .size(42.dp)
                        .background(
                            if (value == i) Ink.warn.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                        .clickable { onChange(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$i",
                        color = if (value == i) Ink.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

private fun wrap(minute: Int): Int = ((minute % 1440) + 1440) % 1440
