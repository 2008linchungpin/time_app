@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeplanner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.timeplanner.data.KIND_BATCH
import com.example.timeplanner.data.TaskEntity
import com.example.timeplanner.data.TimeEntryEntity
import com.example.timeplanner.domain.DEFAULT_MINUTES
import com.example.timeplanner.domain.MINUTE_MS
import com.example.timeplanner.domain.TaskDraft
import com.example.timeplanner.domain.Verdict
import com.example.timeplanner.domain.atMinute
import com.example.timeplanner.domain.computeVerdict
import com.example.timeplanner.domain.durationMinutes
import com.example.timeplanner.domain.formatClock
import com.example.timeplanner.domain.formatDuration
import com.example.timeplanner.domain.minuteOfDay
import com.example.timeplanner.domain.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------- 日期 / 時間 ----------------
@Composable
fun DateNav(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "前一天") }
        TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
            Text(
                date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.getDefault())),
                style = MaterialTheme.typography.titleMedium
            )
        }
        // 日曆圖示需直接開啟日期選擇器；日期文字也保留可點擊功能。
        IconButton(onClick = { picking = true }) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "選擇日期")
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, "後一天") }
        IconButton(onClick = onToday) { Icon(Icons.Filled.Today, "回到今天") }
    }
    if (picking) DatePickDialog(date, onConfirm = onPick, onDismiss = { picking = false })
}

@Composable
fun DatePickDialog(initial: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) { DatePicker(state = state) }
}

@Composable
fun TimePickDialog(initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialMinute / 60, initialMinute % 60, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute); onDismiss() }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = { TimePicker(state = state) }
    )
}

// ---------------- 小元件 ----------------
@Composable
fun <T> ChipRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (text, v) ->
                FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(text) })
            }
        }
    }
}

val REMINDER_OPTIONS: List<Pair<String, Int?>> = listOf("不提醒" to null, "5 分" to 5, "10 分" to 10, "15 分" to 15)
val BUFFER_OPTIONS: List<Pair<String, Int?>> = listOf("預設" to null, "0" to 0, "5 分" to 5, "10 分" to 10, "15 分" to 15)

fun verdictColor(v: Verdict, fallback: Color): Color = when (v) {
    Verdict.OVER -> Color(0xFFD32F2F)
    Verdict.SLIGHTLY_OVER -> Color(0xFFF57C00)
    Verdict.ON_TRACK -> Color(0xFF2E7D32)
    Verdict.NO_DATA -> fallback
}

/** 「預估 45 分｜實際 62 分 🔺 +17」：顏色 + 符號一眼看出超時。 */
@Composable
fun PlanActualLabel(estMin: Int?, actualMs: Long, modifier: Modifier = Modifier) {
    val v = computeVerdict(estMin, actualMs)
    val actualMin = (actualMs / MINUTE_MS).toInt()
    val est = if (estMin != null) "預估 $estMin 分" else "預估 —"
    val act = if (actualMs > 0) "實際 $actualMin 分" else "實際 —"
    val diff = if (estMin != null) actualMin - estMin else 0
    val mark = when (v) {
        Verdict.OVER -> "  🔺 超時 +$diff"
        Verdict.SLIGHTLY_OVER -> "  ▲ +$diff"
        Verdict.ON_TRACK -> if (diff < 0) "  ✔ 提早 ${-diff}" else "  ✔ 準時"
        Verdict.NO_DATA -> ""
    }
    Text(
        "$est｜$act$mark",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = verdictColor(v, MaterialTheme.colorScheme.onSurfaceVariant)
    )
}

// ---------------- 新增 / 編輯任務 ----------------
@Composable
fun TaskEditDialog(
    initial: TaskEntity?,
    defaultStart: Long?,
    defaultDate: LocalDate,
    defaultReminder: Int?,
    globalBuffer: Int,
    checkConflict: suspend (Long, Int, Long) -> Boolean,
    onSave: (TaskDraft) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    prefillTitle: String = "",
    prefillEst: Int? = null,
    lockSchedule: Boolean = false
) {
    var title by remember { mutableStateOf(initial?.title ?: prefillTitle) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var estText by remember { mutableStateOf((initial?.estimatedMinutes ?: prefillEst)?.toString() ?: "") }
    var scheduled by remember {
        mutableStateOf(lockSchedule || initial?.scheduledStart != null || defaultStart != null)
    }
    val base = initial?.scheduledStart ?: defaultStart
    var date by remember { mutableStateOf(base?.toLocalDate() ?: defaultDate) }
    var minute by remember { mutableIntStateOf(base?.minuteOfDay() ?: (9 * 60)) }
    var reminder by remember { mutableStateOf(if (initial != null) initial.reminderMinutes else defaultReminder) }
    var buffer by remember { mutableStateOf(initial?.bufferMinutes) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val est = estText.toIntOrNull()?.takeIf { it > 0 }
    val start = if (scheduled) date.atMinute(minute) else null
    var conflict by remember { mutableStateOf(false) }
    LaunchedEffect(start, est) {
        conflict = start != null && checkConflict(start, est ?: DEFAULT_MINUTES, initial?.id ?: 0L)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    lockSchedule && initial == null -> "建立批次時段"
                    lockSchedule -> "編輯批次時段"
                    initial == null || initial.id == 0L -> "新增任務"
                    else -> "編輯任務"
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("標題") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = estText, onValueChange = { estText = it.filter(Char::isDigit).take(4) },
                    label = { Text("預估時間（分鐘）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!lockSchedule) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("排進時間軸", Modifier.weight(1f))
                        Switch(checked = scheduled, onCheckedChange = { scheduled = it })
                    }
                }
                if (scheduled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickDate = true }) { Text(date.toString()) }
                        TextButton(onClick = { pickTime = true }) { Text(formatClock(minute)) }
                    }
                    if (conflict) {
                        Text("⚠ 與其他任務時間重疊", color = MaterialTheme.colorScheme.error)
                    }
                    ChipRow("開始前提醒", REMINDER_OPTIONS, reminder) { reminder = it }
                    ChipRow("結束後緩衝（全域 $globalBuffer 分）", BUFFER_OPTIONS, buffer) { buffer = it }
                }
                if (!lockSchedule) {
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it }, label = { Text("備註") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row {
                    onHistory?.let { TextButton(onClick = it) { Text("計時紀錄") } }
                    Spacer(Modifier.weight(1f))
                    onDelete?.let {
                        TextButton(onClick = it) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(TaskDraft(title.trim(), notes, est, start, reminder, buffer)) }
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (pickDate) DatePickDialog(date, onConfirm = { date = it }, onDismiss = { pickDate = false })
    if (pickTime) TimePickDialog(minute, onConfirm = { minute = it }, onDismiss = { pickTime = false })
}

// ---------------- 計時紀錄 ----------------
@Composable
fun TimeEntriesDialog(
    task: TaskEntity,
    entries: List<TimeEntryEntity>,
    now: Long,
    onDelete: (TimeEntryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("M/d HH:mm")
    val zone = ZoneId.systemDefault()
    val total = entries.sumOf { ((it.endTime ?: now) - it.startTime).coerceAtLeast(0L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("計時紀錄：${task.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PlanActualLabel(task.estimatedMinutes, total)
                Spacer(Modifier.padding(4.dp))
                if (entries.isEmpty()) Text("還沒有計時紀錄")
                entries.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val s = Instant.ofEpochMilli(e.startTime).atZone(zone).format(fmt)
                        val end = e.endTime?.let { Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "進行中"
                        Text("$s – $end（${formatDuration(((e.endTime ?: now) - e.startTime).coerceAtLeast(0))}）", Modifier.weight(1f))
                        IconButton(onClick = { onDelete(e) }) { Icon(Icons.Filled.Close, "刪除這筆") }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

// ---------------- 快速新增（Inbox / 雜碎） ----------------
@Composable
fun QuickAddContent(onAdd: (String, Boolean) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var misc by remember { mutableStateOf(false) }
    var added by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text.trim(), misc)
            text = ""
            added++
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("快速記下", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = { Text("想到什麼就寫，Enter 存入") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = misc, onClick = { misc = !misc }, label = { Text("雜碎小事") })
            Spacer(Modifier.width(8.dp))
            if (added > 0) Text("已記下 $added 筆", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { submit() }, enabled = text.isNotBlank()) { Text("新增") }
            TextButton(onClick = onClose) { Text("完成") }
        }
    }
}

// ---------------- 批次時段 ----------------
@Composable
fun BatchDialog(
    batch: TaskEntity,
    members: List<TaskEntity>,
    timerRunning: Boolean,
    onToggleDone: (TaskEntity) -> Unit,
    onRemove: (TaskEntity) -> Unit,
    onCompleteAll: () -> Unit,
    onToggleTimer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val startMin = batch.scheduledStart?.minuteOfDay() ?: 0
    val endMin = startMin + batch.durationMinutes()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📦 ${batch.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${formatClock(startMin)}–${formatClock(endMin)} · 共 ${members.size} 件", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.padding(4.dp))
                if (members.isEmpty()) Text("批次裡沒有小事（到「雜碎」頁選取後建立批次）")
                members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = m.isDone, onCheckedChange = { onToggleDone(m) })
                        Text(m.title, Modifier.weight(1f))
                        IconButton(onClick = { onRemove(m) }) { Icon(Icons.Filled.Close, "移出批次") }
                    }
                }
                Row {
                    TextButton(onClick = onToggleTimer) { Text(if (timerRunning) "停止計時" else "開始計時") }
                    TextButton(onClick = onEdit) { Text("改時間") }
                    TextButton(onClick = onDelete) { Text("刪除批次", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCompleteAll) { Text("全部完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}
