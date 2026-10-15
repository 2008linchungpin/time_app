package com.example.timeplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timeplanner.data.DailyReviewEntity
import com.example.timeplanner.data.KIND_BATCH
import com.example.timeplanner.domain.Verdict
import com.example.timeplanner.domain.computeVerdict
import com.example.timeplanner.domain.dayEndMillis
import com.example.timeplanner.domain.dayStartMillis
import com.example.timeplanner.domain.estimationAccuracy
import com.example.timeplanner.domain.formatClock
import com.example.timeplanner.domain.formatDuration
import com.example.timeplanner.domain.minuteOfDay
import com.example.timeplanner.domain.summarizeDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(vm: PlannerViewModel, onOpenHistory: () -> Unit) {
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val entries by vm.allEntries.collectAsStateWithLifecycle()
    val now by rememberNow(false)
    var day by remember { mutableStateOf(LocalDate.now()) }
    val summary = remember(allTasks, entries, day, now) {
        summarizeDay(allTasks, entries, day.dayStartMillis(), day.dayEndMillis(), now)
    }
    val accuracy = remember(allTasks, entries, now) { estimationAccuracy(allTasks, entries, now) }
    val actualBy = remember(summary) { summary.rows.associate { it.task.id to it.actualMs } }
    val overtime = remember(summary) {
        summary.rows.filter { computeVerdict(it.task.estimatedMinutes, it.actualMs) == Verdict.OVER }
    }

    val saved by remember(day) { vm.reviewFor(day) }.collectAsStateWithLifecycle(initialValue = null)
    var loaded by remember(day) { mutableStateOf(false) }
    var highlight by remember(day) { mutableStateOf("") }
    var overtimeNote by remember(day) { mutableStateOf("") }
    var tomorrow by remember(day) { mutableStateOf("") }
    var rating by remember(day) { mutableStateOf<Int?>(null) }
    LaunchedEffect(saved) {
        val s = saved
        if (s != null && !loaded) {
            highlight = s.highlight; overtimeNote = s.overtime; tomorrow = s.tomorrowFocus
            rating = s.rating.takeIf { it > 0 }
            loaded = true
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每日回顧", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenHistory) { Text("歷史回顧") }
            }
            DateNav(
                day,
                onPrev = { day = day.minusDays(1) }, onNext = { day = day.plusDays(1) },
                onToday = { day = LocalDate.now() }, onPick = { day = it }
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("完成 ${summary.completed.size} / ${summary.totalCount} 件", style = MaterialTheme.typography.titleMedium)
                    Text("預估 ${summary.totalEstimatedMin} 分　實際 ${formatDuration(summary.totalActualMs)}")
                    PlanActualLabel(summary.totalEstimatedMin, summary.totalActualMs)
                    accuracy?.let {
                        Text(
                            "歷史準度：實際約為預估的 ${(it.ratio * 100).roundToInt()}%（${it.samples} 筆已完成任務）" +
                                if (it.ratio > 1.1) "，下次預估可以抓長一點" else if (it.ratio < 0.9) "，你常常比預估更快" else "，預估很準",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Text("✅ 今天完成（${summary.completed.size}）", style = MaterialTheme.typography.titleMedium) }
        if (summary.completed.isEmpty()) item { Text("還沒有完成的任務", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(summary.completed, key = { "c${it.id}" }) { t ->
            Column {
                Text(t.title)
                PlanActualLabel(t.estimatedMinutes, actualBy[t.id] ?: 0L)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏳ 未完成（${summary.unfinished.size}）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (summary.unfinished.isNotEmpty())
                    Button(onClick = { vm.moveAllTomorrow(summary.unfinished) }) { Text("全部移到隔天") }
            }
        }
        if (summary.unfinished.isEmpty()) item { Text("今天都處理完了 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(summary.unfinished, key = { "u${it.id}" }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text((if (t.kind == KIND_BATCH) "📦 " else "") + t.title)
                        t.scheduledStart?.let { Text(formatClock(it.minuteOfDay()), style = MaterialTheme.typography.labelSmall) }
                    }
                    TextButton(onClick = { vm.moveTomorrow(t) }) { Text("移到隔天") }
                    if (t.kind != KIND_BATCH) TextButton(onClick = { vm.postpone(t) }) { Text("延期") }
                    IconButton(onClick = { vm.toggleDone(t) }) { Icon(Icons.Filled.Check, "完成") }
                }
            }
        }

        if (overtime.isNotEmpty()) {
            item { Text("🔺 明顯超時（${overtime.size}）", style = MaterialTheme.typography.titleMedium) }
            items(overtime, key = { "o${it.task.id}" }) { r ->
                Column {
                    Text(r.task.title)
                    PlanActualLabel(r.task.estimatedMinutes, r.actualMs)
                }
            }
        }

        item { Text("📝 反思（可選填）", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(highlight, { highlight = it }, label = { Text("今天最大收穫？") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(overtimeNote, { overtimeNote = it }, label = { Text("哪些任務超時？為什麼？") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tomorrow, { tomorrow = it }, label = { Text("明天優先做什麼？") }, modifier = Modifier.fillMaxWidth())
                ChipRow<Int?>("今天的感覺", listOf("—" to null, "1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5), rating) { rating = it }
            }
        }
        item {
            val completedAt = saved?.completedAt
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (completedAt != null) {
                    val t = Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("✔ 已於 $t 完成回顧", color = verdictColor(Verdict.ON_TRACK, MaterialTheme.colorScheme.onSurface))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.saveReview(
                            DailyReviewEntity(
                                epochDay = day.toEpochDay(),
                                highlight = highlight.trim(), overtime = overtimeNote.trim(), tomorrowFocus = tomorrow.trim(),
                                rating = rating ?: 0,
                                completedAt = System.currentTimeMillis(),
                                estimatedMinutes = summary.totalEstimatedMin,
                                actualMinutes = (summary.totalActualMs / 60_000L).toInt(),
                                doneCount = summary.completed.size,
                                totalCount = summary.totalCount
                            )
                        )
                    }
                ) { Text(if (completedAt != null) "更新回顧" else "完成回顧，關閉今天") }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp)) }
    }
}

@Composable
fun ReviewHistoryScreen(vm: PlannerViewModel, onBack: () -> Unit) {
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("歷史回顧", style = MaterialTheme.typography.titleLarge)
        }
        if (reviews.isEmpty()) Text("還沒有回顧紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(reviews, key = { it.epochDay }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val d = LocalDate.ofEpochDay(r.epochDay)
                        Text(
                            d.format(DateTimeFormatter.ofPattern("yyyy/M/d EEEE")) +
                                (if (r.rating > 0) "　" + "★".repeat(r.rating) else "") +
                                (if (r.completedAt == null) "　（未完成回顧）" else ""),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("完成 ${r.doneCount}/${r.totalCount}　預估 ${r.estimatedMinutes} 分　實際 ${r.actualMinutes} 分")
                        PlanActualLabel(r.estimatedMinutes, r.actualMinutes * 60_000L)
                        if (r.highlight.isNotBlank()) Text("收穫：${r.highlight}")
                        if (r.overtime.isNotBlank()) Text("超時：${r.overtime}")
                        if (r.tomorrowFocus.isNotBlank()) Text("明天：${r.tomorrowFocus}")
                    }
                }
            }
        }
    }
}
