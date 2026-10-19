package com.example.timeplanner.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.example.timeplanner.data.TaskEntity
import com.example.timeplanner.domain.atMinute
import java.time.LocalTime

@Composable
fun InboxScreen(vm: PlannerViewModel) {
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var quick by remember { mutableStateOf("") }
    var scheduling by remember { mutableStateOf<TaskEntity?>(null) }
    fun add() { if (quick.isNotBlank()) { vm.quickAdd(quick, false); quick = "" } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Inbox（待整理 ${inbox.size}）", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = quick, onValueChange = { quick = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("想到什麼先記下，Enter 存入") },
            trailingIcon = { IconButton(onClick = { add() }) { Icon(Icons.Filled.Add, "新增") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() })
        )
        if (inbox.isEmpty()) Text("Inbox 是空的，輕鬆。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(inbox, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable { scheduling = t }) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(t.title, style = MaterialTheme.typography.bodyLarge)
                            val sub = buildList {
                                t.estimatedMinutes?.let { add("預估 $it 分") }
                                if (t.postponedCount > 0) add("延期 ${t.postponedCount} 次")
                            }.joinToString("　")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { vm.toggleDone(t) }) { Icon(Icons.Filled.Check, "完成") }
                        IconButton(onClick = { vm.delete(t) }) { Icon(Icons.Filled.Delete, "刪除") }
                    }
                }
            }
        }
    }

    // 點任務 → 補細節並排進時間軸（預設排在選取日期的下一個整點）
    scheduling?.let { t ->
        val nextHour = (LocalTime.now().hour + 1).coerceAtMost(22)
        TaskEditDialog(
            initial = t, defaultStart = date.atMinute(nextHour * 60), defaultDate = date,
            defaultReminder = settings.defaultReminderMinutes, globalBuffer = settings.globalBufferMinutes,
            checkConflict = vm::hasConflict,
            categories = categories,
            defaultStartMinuteOfDay = settings.defaultStartMinuteOfDay,
            onSave = { vm.saveEdit(t, it); scheduling = null },
            onDismiss = { scheduling = null },
            onDelete = { vm.delete(t); scheduling = null }
        )
    }
}
