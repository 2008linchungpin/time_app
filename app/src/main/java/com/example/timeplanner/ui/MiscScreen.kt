package com.example.timeplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timeplanner.data.KIND_BATCH
import com.example.timeplanner.domain.atMinute
import com.example.timeplanner.domain.formatClock
import com.example.timeplanner.domain.minuteOfDay
import java.time.LocalTime

/** 雜碎專區：小事不上時間軸；多選 → 建立批次時段 → 在時間軸上以一個區塊出現。 */
@Composable
fun MiscScreen(vm: PlannerViewModel) {
    val misc by vm.misc.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var quick by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var creating by remember { mutableStateOf(false) }
    val batches = remember(allTasks) { allTasks.filter { it.kind == KIND_BATCH }.associateBy { it.id } }
    val selTasks = misc.filter { it.id in selected && it.batchId == null }
    fun add() { if (quick.isNotBlank()) { vm.quickAdd(quick, true); quick = "" } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("雜碎事務（${misc.size}）", style = MaterialTheme.typography.titleLarge)
        Text(
            "零碎小事先放這裡，不會佔用時間軸；勾選多件後建立「批次時段」，一次處理。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = quick, onValueChange = { quick = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("新增一件小事，Enter 存入") },
            trailingIcon = { IconButton(onClick = { add() }) { Icon(Icons.Filled.Add, "新增") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() })
        )
        if (selTasks.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已選 ${selTasks.size} 項", Modifier.weight(1f))
                    TextButton(onClick = { selected = emptySet() }) { Text("清除") }
                    Button(onClick = { creating = true }, modifier = Modifier.padding(end = 8.dp)) { Text("建立批次時段") }
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(misc, key = { it.id }) { t ->
                val batch = t.batchId?.let { batches[it] }
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = t.id in selected,
                            enabled = batch == null,
                            onCheckedChange = { c -> selected = if (c) selected + t.id else selected - t.id }
                        )
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(t.title, style = MaterialTheme.typography.bodyLarge)
                            if (batch != null) {
                                val m = batch.scheduledStart?.minuteOfDay()
                                Text(
                                    "📦 ${batch.title}" + (m?.let { "  ${formatClock(it)}" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (batch != null) TextButton(onClick = { vm.removeFromBatch(t) }) { Text("移出") }
                        IconButton(onClick = { vm.toggleDone(t) }) { Icon(Icons.Filled.Check, "完成") }
                        IconButton(onClick = { vm.delete(t) }) { Icon(Icons.Filled.Delete, "刪除") }
                    }
                }
            }
        }
    }

    if (creating) {
        val sum = selTasks.sumOf { it.estimatedMinutes ?: 5 }
        val est = maxOf(15, (sum + 4) / 5 * 5)
        val nextHour = (LocalTime.now().hour + 1).coerceAtMost(22)
        TaskEditDialog(
            initial = null, defaultStart = date.atMinute(nextHour * 60), defaultDate = date,
            defaultReminder = settings.defaultReminderMinutes, globalBuffer = settings.globalBufferMinutes,
            checkConflict = vm::hasConflict,
            prefillTitle = "雜務批次", prefillEst = est, lockSchedule = true,
            categories = categories,
            defaultStartMinuteOfDay = settings.defaultStartMinuteOfDay,
            onSave = { vm.createBatch(it, selTasks.map { x -> x.id }); selected = emptySet(); creating = false },
            onDismiss = { creating = false }
        )
    }
}
