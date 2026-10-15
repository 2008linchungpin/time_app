@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.timeplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timeplanner.data.KIND_BATCH
import com.example.timeplanner.data.TaskEntity
import com.example.timeplanner.domain.actualMillis
import com.example.timeplanner.domain.assignLanes
import com.example.timeplanner.domain.atMinute
import com.example.timeplanner.domain.dayEndMillis
import com.example.timeplanner.domain.durationMinutes
import com.example.timeplanner.domain.findOverlaps
import com.example.timeplanner.domain.formatClock
import com.example.timeplanner.domain.formatDuration
import com.example.timeplanner.domain.minuteOfDay
import com.example.timeplanner.domain.summarizeDay
import com.example.timeplanner.domain.dayStartMillis
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

private val HourHeight = 80.dp
private val LabelWidth = 52.dp

@Composable
fun rememberNow(fast: Boolean): State<Long> = produceState(System.currentTimeMillis(), fast) {
    while (true) {
        value = System.currentTimeMillis()
        delay(if (fast) 1_000 else 30_000)
    }
}

@Composable
fun TimelineScreen(vm: PlannerViewModel) {
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val tasks by vm.dayTasks.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val entries by vm.allEntries.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val runningTask by vm.runningTask.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val editRequest by vm.editRequest.collectAsStateWithLifecycle()
    val now by rememberNow(running != null)

    val overlaps = remember(tasks) { findOverlaps(tasks) }
    val lanes = remember(tasks) { assignLanes(tasks) }
    val actualByTask = remember(entries, now) { entries.groupBy { it.taskId }.mapValues { actualMillis(it.value, now) } }
    val memberCounts = remember(allTasks) {
        allTasks.filter { it.batchId != null }.groupingBy { it.batchId!! }.eachCount()
    }
    val summary = remember(tasks, entries, date, now) {
        summarizeDay(tasks, entries, date.dayStartMillis(), date.dayEndMillis(), now)
    }

    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var creatingAt by remember { mutableStateOf<Long?>(null) }
    var historyFor by remember { mutableStateOf<TaskEntity?>(null) }
    var batchOpenId by remember { mutableStateOf<Long?>(null) }

    // 通知點「調整時間」→ 直接開啟該任務
    LaunchedEffect(editRequest) {
        editRequest?.let {
            if (it.kind == KIND_BATCH) batchOpenId = it.id else editing = it
            vm.consumeEditRequest()
        }
    }

    val density = LocalDensity.current
    val hourPx = with(density) { HourHeight.toPx() }
    val labelPx = with(density) { LabelWidth.toPx() }
    val scroll = rememberScrollState()
    LaunchedEffect(date) { scroll.scrollTo((7 * hourPx).toInt()) }

    Column(Modifier.fillMaxSize()) {
        DateNav(
            date,
            onPrev = { vm.selectedDate.value = date.minusDays(1) },
            onNext = { vm.selectedDate.value = date.plusDays(1) },
            onToday = { vm.selectedDate.value = LocalDate.now() },
            onPick = { vm.selectedDate.value = it }
        )

        if (overlaps.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "⚠ 有 ${overlaps.size} 個任務時間重疊（紅框），請調整避免衝突",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        val rt = runningTask
        val re = running
        if (rt != null && re != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⏱ 正在計時：${rt.title}　${formatDuration(now - re.startTime)}",
                        Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { vm.stopTimer() }) { Text("停止") }
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val laneAreaPx = with(density) { (maxWidth - LabelWidth).toPx() }
            Column(Modifier.verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(HourHeight * 24)) {
                    // 小時格線
                    repeat(24) { h ->
                        Row(Modifier.offset(y = HourHeight * h).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(
                                "%02d:00".format(h), Modifier.width(LabelWidth).padding(start = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(Modifier.weight(1f).padding(top = 8.dp))
                        }
                    }
                    // 點空白處 → 在該時間新增（對齊 15 分）
                    Box(
                        Modifier.fillMaxSize().padding(start = LabelWidth).pointerInput(date) {
                            detectTapGestures { off ->
                                val m = ((off.y / hourPx * 60f) / 15f).toInt() * 15
                                creatingAt = date.atMinute(m.coerceIn(0, 23 * 60 + 45))
                            }
                        }
                    )
                    // 現在時間紅線
                    if (date == LocalDate.now()) {
                        val nowMin = now.minuteOfDay()
                        HorizontalDivider(
                            Modifier.offset(y = HourHeight * (nowMin / 60f)).padding(start = LabelWidth),
                            thickness = 2.dp, color = Color(0xFFE53935)
                        )
                    }
                    // 任務區塊
                    tasks.forEach { task ->
                        val lane = lanes[task.id] ?: return@forEach
                        val laneW = laneAreaPx / lane.count
                        val buf = task.bufferMinutes ?: settings.globalBufferMinutes
                        val startMin = task.scheduledStart!!.minuteOfDay()
                        if (buf > 0 && !task.isDone) {
                            // 緩衝：區塊後面的淡色預留空檔
                            Box(
                                Modifier
                                    .offset {
                                        IntOffset(
                                            (labelPx + lane.index * laneW).roundToInt(),
                                            ((startMin + task.durationMinutes()) / 60f * hourPx).roundToInt()
                                        )
                                    }
                                    .width(with(density) { (laneW - 4f).toDp() })
                                    .height(HourHeight * (buf / 60f))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                            )
                        }
                        TaskBlock(
                            task = task, date = date, hourPx = hourPx,
                            xPx = labelPx + lane.index * laneW, widthPx = laneW - 4f,
                            conflict = task.id in overlaps,
                            actualMs = actualByTask[task.id] ?: 0L,
                            isRunning = running?.taskId == task.id,
                            memberCount = memberCounts[task.id] ?: 0,
                            onOpen = { if (task.kind == KIND_BATCH) batchOpenId = task.id else editing = task },
                            onMove = { vm.moveTask(task.id, it) },
                            onToggleDone = { vm.toggleDone(task) },
                            onToggleTimer = { vm.toggleTimer(task) }
                        )
                    }
                }
            }
        }

        // 當日彙總
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    "今日 完成 ${summary.completed.size}/${summary.totalCount}　預估 ${summary.totalEstimatedMin} 分　實際 ${formatDuration(summary.totalActualMs)}",
                    style = MaterialTheme.typography.labelLarge
                )
                if (summary.totalEstimatedMin > 0 || summary.totalActualMs > 0)
                    PlanActualLabel(summary.totalEstimatedMin, summary.totalActualMs)
            }
        }
    }

    // ---------- 對話框 ----------
    val defaultReminder = settings.defaultReminderMinutes
    creatingAt?.let { startAt ->
        TaskEditDialog(
            initial = null, defaultStart = startAt, defaultDate = date,
            defaultReminder = defaultReminder, globalBuffer = settings.globalBufferMinutes,
            checkConflict = vm::hasConflict,
            onSave = { vm.createTask(it); creatingAt = null },
            onDismiss = { creatingAt = null }
        )
    }
    editing?.let { t ->
        TaskEditDialog(
            initial = t, defaultStart = null, defaultDate = date,
            defaultReminder = defaultReminder, globalBuffer = settings.globalBufferMinutes,
            checkConflict = vm::hasConflict,
            lockSchedule = t.kind == KIND_BATCH,
            onSave = { vm.saveEdit(t, it); editing = null },
            onDismiss = { editing = null },
            onDelete = { vm.delete(t); editing = null },
            onHistory = { historyFor = t; editing = null }
        )
    }
    historyFor?.let { t ->
        val list by remember(t.id) { vm.entriesFor(t.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
        TimeEntriesDialog(t, list, now, onDelete = vm::deleteEntry, onDismiss = { historyFor = null })
    }
    val batch = allTasks.firstOrNull { it.id == batchOpenId }
    if (batch != null) {
        BatchDialog(
            batch = batch,
            members = allTasks.filter { it.batchId == batch.id },
            timerRunning = running?.taskId == batch.id,
            onToggleDone = vm::toggleDone,
            onRemove = vm::removeFromBatch,
            onCompleteAll = { vm.completeBatch(batch.id); batchOpenId = null },
            onToggleTimer = { vm.toggleTimer(batch) },
            onEdit = { editing = batch; batchOpenId = null },
            onDelete = { vm.delete(batch); batchOpenId = null },
            onDismiss = { batchOpenId = null }
        )
    }
}

@Composable
private fun TaskBlock(
    task: TaskEntity,
    date: LocalDate,
    hourPx: Float,
    xPx: Float,
    widthPx: Float,
    conflict: Boolean,
    actualMs: Long,
    isRunning: Boolean,
    memberCount: Int,
    onOpen: () -> Unit,
    onMove: (Long) -> Unit,
    onToggleDone: () -> Unit,
    onToggleTimer: () -> Unit
) {
    val density = LocalDensity.current
    val duration = task.durationMinutes()
    val baseMinutes = task.scheduledStart!!.minuteOfDay()
    var dragPx by remember(task.scheduledStart) { mutableFloatStateOf(0f) }
    var held by remember(task.scheduledStart) { mutableStateOf(false) }
    // 拖曳時以 15 分鐘為單位吸附
    val preview = (((baseMinutes + dragPx / hourPx * 60f) / 15f).roundToInt() * 15)
        .coerceIn(0, (1440 - duration).coerceAtLeast(0))
    val shown = if (held) preview else baseMinutes
    val currentPreview by rememberUpdatedState(preview)
    val currentMove by rememberUpdatedState(onMove)

    val heightDp = maxOf((HourHeight * (duration / 60f)), 40.dp)
    val widthDp = with(density) { widthPx.toDp() }
    val isBatch = task.kind == KIND_BATCH
    val container = when {
        task.isDone -> MaterialTheme.colorScheme.surfaceVariant
        isBatch -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        border = if (conflict) BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        else if (isRunning) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (held) 8.dp else 0.dp,
        modifier = Modifier
            .offset { IntOffset(xPx.roundToInt(), (shown / 60f * hourPx).roundToInt()) }
            .width(widthDp)
            .height(heightDp)
            .clickable(onClick = onOpen)
            .pointerInput(task.id, task.scheduledStart) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { held = true },
                    onDragEnd = {
                        if (currentPreview != baseMinutes) currentMove(date.atMinute(currentPreview))
                        else { held = false; dragPx = 0f }
                    },
                    onDragCancel = { held = false; dragPx = 0f },
                    onDrag = { change, amount -> change.consume(); dragPx += amount.y }
                )
            }
    ) {
        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    (if (isBatch) "📦 " else "") + task.title +
                        (if (isBatch && memberCount > 0) "（$memberCount 件）" else ""),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null
                )
                Text(
                    "${formatClock(shown)}–${formatClock(shown + duration)}" + if (conflict) "  ⚠ 重疊" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (heightDp >= 64.dp) PlanActualLabel(task.estimatedMinutes, actualMs)
            }
            IconButton(onClick = onToggleTimer, modifier = Modifier.size(36.dp)) {
                Icon(if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (isRunning) "停止計時" else "開始計時")
            }
            IconButton(onClick = onToggleDone, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (task.isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    if (task.isDone) "取消完成" else "標記完成",
                    tint = if (task.isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
