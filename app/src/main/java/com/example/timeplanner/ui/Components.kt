@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeplanner.ui

import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.widget.EditText
import android.widget.NumberPicker
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.timeplanner.data.CategoryEntity
import com.example.timeplanner.data.KIND_BATCH
import com.example.timeplanner.data.REMINDER_NONE
import com.example.timeplanner.domain.effectiveReminder
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
    val isToday = date == LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "前一天") }
        TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
            Text(
                date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.getDefault())),
                style = MaterialTheme.typography.titleMedium
            )
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, "後一天") }
        // 非今天時顯示「今天」快捷鍵；日曆圖示一律開啟日期選擇器
        if (!isToday) {
            TextButton(onClick = onToday) { Text("今天") }
        }
        IconButton(onClick = { picking = true }) { Icon(Icons.Filled.Today, "選擇日期") }
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

/** 設定 NumberPicker 的數字顏色（含滾輪上下的非選取數字）與選取線顏色；舊版 Android 用反射補上。 */
private fun NumberPicker.styleWheel(digitColor: Int, lineColor: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        textColor = digitColor
        textSize = 26f * resources.displayMetrics.scaledDensity
    } else {
        try {
            NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint").apply {
                isAccessible = true
                (get(this@styleWheel) as Paint).apply { color = digitColor; textSize = 26f * resources.displayMetrics.scaledDensity }
            }
        } catch (_: Exception) {}
        for (i in 0 until childCount) (getChildAt(i) as? EditText)?.apply { setTextColor(digitColor); textSize = 26f }
    }
    try {
        NumberPicker::class.java.getDeclaredField("mSelectionDivider").apply {
            isAccessible = true
            set(this@styleWheel, ColorDrawable(lineColor))
        }
    } catch (_: Exception) {}
    invalidate()
}

/** 時間滾輪選擇：預設 5 分鐘間距，可切換「自訂」為每 1 分鐘。 */
@Composable
fun TimePickDialog(initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val initH = (initialMinute / 60).coerceIn(0, 23)
    val initM = (initialMinute % 60).coerceIn(0, 59)
    // 初始若不是 5 的倍數，自動進自訂模式以免被吸附
    var custom by remember { mutableStateOf(initM % 5 != 0) }
    var hour by remember { mutableIntStateOf(initH) }
    var minute by remember { mutableIntStateOf(initM) }
    val hourLabels = remember { Array(24) { "%02d".format(it) } }
    val stepLabels = remember { Array(12) { "%02d".format(it * 5) } }
    val fullLabels = remember { Array(60) { "%02d".format(it) } }
    // 滾輪數字用最高對比色、選取線用主色，深 / 淺色主題都清楚
    val digitColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇時間") },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour * 60 + minute); onDismiss() }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(160.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 小時滾輪 00–23
                    AndroidView(
                        factory = { ctx ->
                            NumberPicker(ctx).apply {
                                styleWheel(digitColor, lineColor)
                                minValue = 0
                                maxValue = 23
                                displayedValues = hourLabels
                                value = hour
                                wrapSelectorWheel = true
                                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                                setOnValueChangedListener { _, _, newVal -> hour = newVal }
                            }
                        },
                        update = { picker -> if (picker.value != hour) picker.value = hour },
                        modifier = Modifier.width(96.dp).height(160.dp)
                    )
                    Text(
                        "：",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    // 分鐘滾輪：5 分鐘間距 或 自訂每 1 分
                    key(custom) {
                        if (custom) {
                            AndroidView(
                                factory = { ctx ->
                                    NumberPicker(ctx).apply {
                                styleWheel(digitColor, lineColor)
                                        minValue = 0
                                        maxValue = 59
                                        displayedValues = fullLabels
                                        value = minute.coerceIn(0, 59)
                                        wrapSelectorWheel = true
                                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                                        setOnValueChangedListener { _, _, newVal -> minute = newVal }
                                    }
                                },
                                update = { picker ->
                                    val v = minute.coerceIn(0, 59)
                                    if (picker.value != v) picker.value = v
                                },
                                modifier = Modifier.width(96.dp).height(160.dp)
                            )
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    NumberPicker(ctx).apply {
                                styleWheel(digitColor, lineColor)
                                        minValue = 0
                                        maxValue = 11
                                        displayedValues = stepLabels
                                        value = (minute / 5).coerceIn(0, 11)
                                        wrapSelectorWheel = true
                                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                                        setOnValueChangedListener { _, _, newVal -> minute = newVal * 5 }
                                    }
                                },
                                update = { picker ->
                                    val idx = (minute / 5).coerceIn(0, 11)
                                    if (picker.value != idx) picker.value = idx
                                },
                                modifier = Modifier.width(96.dp).height(160.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !custom,
                        onClick = {
                            custom = false
                            minute = (minute / 5) * 5
                        },
                        label = { Text("5 分鐘") }
                    )
                    FilterChip(
                        selected = custom,
                        onClick = { custom = true },
                        label = { Text("自訂") }
                    )
                }
                Text(
                    "目前：%02d:%02d".format(hour, minute),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

/** 設定頁用：預設提醒（null = 不提醒）。 */
val REMINDER_OPTIONS: List<Pair<String, Int?>> = listOf("不提醒" to null, "5 分" to 5, "10 分" to 10, "15 分" to 15)
val BUFFER_OPTIONS: List<Pair<String, Int>> = listOf("0" to 0, "5 分" to 5, "10 分" to 10, "15 分" to 15)

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
    lockSchedule: Boolean = false,
    categories: List<CategoryEntity> = emptyList(),
    /** 設定中的預設開始時間（一天中的第幾分鐘），無既有時間時使用。 */
    defaultStartMinuteOfDay: Int = 9 * 60
) {
    var title by remember { mutableStateOf(initial?.title ?: prefillTitle) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var estText by remember { mutableStateOf((initial?.estimatedMinutes ?: prefillEst)?.toString() ?: "") }
    var scheduled by remember {
        mutableStateOf(lockSchedule || initial?.scheduledStart != null || defaultStart != null)
    }
    val base = initial?.scheduledStart ?: defaultStart
    var date by remember { mutableStateOf(base?.toLocalDate() ?: defaultDate) }
    var minute by remember {
        mutableIntStateOf(base?.minuteOfDay() ?: defaultStartMinuteOfDay.coerceIn(0, 23 * 60 + 55))
    }
    // 畫面上顯示實際生效值（沒特別設定 → 設定中的預設）；null = 不提醒
    var reminder by remember { mutableStateOf(initial?.effectiveReminder(defaultReminder) ?: if (initial == null) defaultReminder else null) }
    var buffer by remember { mutableStateOf(initial?.bufferMinutes ?: globalBuffer) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val est = estText.toIntOrNull()?.takeIf { it > 0 }
    val start = if (scheduled) date.atMinute(minute) else null
    var conflict by remember { mutableStateOf(false) }
    LaunchedEffect(start, est) {
        conflict = start != null && checkConflict(start, est ?: DEFAULT_MINUTES, initial?.id ?: 0L)
    }

    val categoryOptions: List<Pair<String, Long?>> = remember(categories) {
        listOf("未分類" to null) + categories.map { it.name to it.id }
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
                if (!lockSchedule && categories.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("分類（由重要到不重要）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = categoryId == null,
                                onClick = { categoryId = null },
                                label = { Text("未分類") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                            categories.forEach { c ->
                                val catColor = Color(c.colorArgb)
                                val selected = categoryId == c.id
                                FilterChip(
                                    selected = selected,
                                    onClick = { categoryId = c.id },
                                    label = { Text(c.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = catColor.copy(alpha = 0.18f),
                                        labelColor = catColor,
                                        selectedContainerColor = catColor,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
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
                    ChipRow("結束後緩衝", BUFFER_OPTIONS, buffer) { buffer = it }
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
                onClick = { // 與設定相同 → 存 null（之後跟著設定走）；不同 → 存個別值
                    onSave(
                        TaskDraft(
                            title.trim(), notes, est, start,
                            reminderMinutes = if (reminder == defaultReminder) null else (reminder ?: REMINDER_NONE),
                            bufferMinutes = if (buffer == globalBuffer) null else buffer,
                            categoryId = categoryId
                        )
                    ) }
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
