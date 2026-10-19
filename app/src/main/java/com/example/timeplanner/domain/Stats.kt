package com.example.timeplanner.domain

import com.example.timeplanner.data.KIND_BATCH
import com.example.timeplanner.data.TaskEntity
import com.example.timeplanner.data.TimeEntryEntity
import java.time.LocalDate

/** 單日統計（整體回顧的折線圖用）。 */
data class DayStat(
    val date: LocalDate,
    val completed: Int,        // 當天完成的任務數（不含批次時段本身）
    val focusMin: Int,         // 當天開始計時的總分鐘數
    val accuracyPct: Int?      // 當天完成任務的「實際 / 預估」%，沒有可比較的資料 = null
)

data class OverallStats(
    val days: List<DayStat>,   // 第一天 → 今天，每天一筆（沒活動的日子也有，數值為 0）
    val totalCompleted: Int,
    val totalFocusMin: Int,
    val activeDays: Int,       // 至少完成一件事的天數
    val currentStreak: Int,    // 連續完成天數（今天還沒完成不會中斷，從昨天往回算）
    val bestStreak: Int,
    val bestDay: DayStat?      // 完成最多件的一天
)

/** 第一次使用的日期：最早的任務建立 / 計時 / 完成時間；沒有資料 = 今天。 */
fun firstUseDate(tasks: List<TaskEntity>, entries: List<TimeEntryEntity>, today: LocalDate): LocalDate {
    val millis = tasks.map { it.createdAt } +
        tasks.mapNotNull { it.completedAt } +
        entries.map { it.startTime }
    val first = millis.minOrNull()?.toLocalDate() ?: today
    return if (first.isAfter(today)) today else first
}

fun computeOverallStats(
    tasks: List<TaskEntity>,
    entries: List<TimeEntryEntity>,
    today: LocalDate,
    now: Long
): OverallStats {
    val first = firstUseDate(tasks, entries, today)
    val byTask = entries.groupBy { it.taskId }

    val doneTasks = tasks.filter { it.isDone && it.kind != KIND_BATCH && it.completedAt != null }
    val doneByDay = doneTasks.groupBy { it.completedAt!!.toLocalDate() }
    val focusByDay = entries.groupBy { it.startTime.toLocalDate() }
        .mapValues { (_, list) -> (actualMillis(list, now) / MINUTE_MS).toInt() }

    val days = mutableListOf<DayStat>()
    var d = first
    while (!d.isAfter(today)) {
        val done = doneByDay[d].orEmpty()
        var est = 0L
        var act = 0L
        for (t in done) {
            val e = t.estimatedMinutes ?: continue
            if (e <= 0) continue
            val a = actualMillis(byTask[t.id].orEmpty(), now)
            if (a <= 0) continue
            est += e * MINUTE_MS
            act += a
        }
        days += DayStat(
            date = d,
            completed = done.size,
            focusMin = focusByDay[d] ?: 0,
            accuracyPct = if (est > 0) (act * 100.0 / est).toInt() else null
        )
        d = d.plusDays(1)
    }

    var best = 0
    var run = 0
    for (s in days) {
        run = if (s.completed > 0) run + 1 else 0
        if (run > best) best = run
    }
    // 目前連續：從今天往回算；今天還沒完成就從昨天算起
    var cur = 0
    var i = days.lastIndex
    if (i >= 0 && days[i].completed == 0) i--
    while (i >= 0 && days[i].completed > 0) { cur++; i-- }

    return OverallStats(
        days = days,
        totalCompleted = days.sumOf { it.completed },
        totalFocusMin = days.sumOf { it.focusMin },
        activeDays = days.count { it.completed > 0 },
        currentStreak = cur,
        bestStreak = best,
        bestDay = days.filter { it.completed > 0 }.maxByOrNull { it.completed }
    )
}

/** 簡單移動平均（前面不足 window 筆時用已有的資料平均）。 */
fun movingAverage(values: List<Float>, window: Int): List<Float> =
    values.indices.map { i ->
        val from = maxOf(0, i - window + 1)
        values.subList(from, i + 1).average().toFloat()
    }
