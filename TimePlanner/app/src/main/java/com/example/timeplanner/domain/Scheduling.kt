package com.example.timeplanner.domain

import com.example.timeplanner.data.KIND_BATCH
import com.example.timeplanner.data.TaskEntity
import com.example.timeplanner.data.TimeEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val DEFAULT_MINUTES = 30
const val MINUTE_MS = 60_000L

/** 編輯 / 新增對話框送出的內容。 */
data class TaskDraft(
    val title: String,
    val notes: String = "",
    val estimatedMinutes: Int? = null,
    val start: Long? = null,
    val reminderMinutes: Int? = null,
    val bufferMinutes: Int? = null
)

private val zone: ZoneId get() = ZoneId.systemDefault()

// ---------- 日期工具 ----------
fun LocalDate.dayStartMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
fun LocalDate.dayEndMillis(): Long = plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
fun LocalDate.atMinute(minuteOfDay: Int): Long =
    atStartOfDay().plusMinutes(minuteOfDay.toLong()).atZone(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
fun Long.minuteOfDay(): Int = Instant.ofEpochMilli(this).atZone(zone).let { it.hour * 60 + it.minute }
fun Long.shiftDays(n: Long): Long = Instant.ofEpochMilli(this).atZone(zone).plusDays(n).toInstant().toEpochMilli()

// ---------- 排程 ----------
fun TaskEntity.durationMinutes(): Int = estimatedMinutes ?: DEFAULT_MINUTES
fun TaskEntity.endMillis(): Long? = scheduledStart?.plus(durationMinutes() * MINUTE_MS)
fun TaskEntity.bufferOrDefault(global: Int): Int = bufferMinutes ?: global

/** 回傳所有「與別的任務時間重疊」的任務 id。 */
fun findOverlaps(tasks: List<TaskEntity>): Set<Long> {
    val sched = tasks.filter { it.scheduledStart != null }.sortedBy { it.scheduledStart }
    val result = mutableSetOf<Long>()
    for (i in sched.indices) {
        for (j in i + 1 until sched.size) {
            val a = sched[i]
            val b = sched[j]
            if (b.scheduledStart!! >= a.endMillis()!!) break
            result += a.id
            result += b.id
        }
    }
    return result
}

data class Lane(val index: Int, val count: Int)

/** 重疊的任務並排顯示：回傳每個任務所在的欄位與該群組的欄數。 */
fun assignLanes(tasks: List<TaskEntity>): Map<Long, Lane> {
    val sorted = tasks.filter { it.scheduledStart != null }.sortedBy { it.scheduledStart }
    val result = mutableMapOf<Long, Lane>()
    var cluster = mutableListOf<Pair<TaskEntity, Int>>()
    var laneEnds = mutableListOf<Long>()
    var clusterEnd = Long.MIN_VALUE

    fun flush() {
        val n = laneEnds.size
        cluster.forEach { (t, i) -> result[t.id] = Lane(i, n) }
        cluster = mutableListOf()
        laneEnds = mutableListOf()
        clusterEnd = Long.MIN_VALUE
    }

    for (t in sorted) {
        val s = t.scheduledStart!!
        val e = t.endMillis()!!
        if (s >= clusterEnd) flush()
        var lane = laneEnds.indexOfFirst { it <= s }
        if (lane < 0) {
            laneEnds.add(e)
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = e
        }
        cluster.add(t to lane)
        clusterEnd = maxOf(clusterEnd, e)
    }
    flush()
    return result
}

/**
 * 緩衝順延：anchor 任務結束後要預留緩衝；
 * 開始時間落在 [anchor 結束, anchor 結束 + 緩衝) 的後續任務依序往後推（連鎖），
 * 遇到本來就夠遠的任務就停止。真正和 anchor 時間重疊的不動（交給重疊警告）。
 * 回傳 taskId → 新開始時間。
 */
fun computeBufferShifts(
    tasks: List<TaskEntity>,
    anchorId: Long,
    globalBuffer: Int,
    dayEnd: Long
): Map<Long, Long> {
    val sched = tasks.filter { it.scheduledStart != null }.sortedBy { it.scheduledStart }
    val anchor = sched.firstOrNull { it.id == anchorId } ?: return emptyMap()
    val anchorEnd = anchor.endMillis()!!
    var cursor = anchorEnd + anchor.bufferOrDefault(globalBuffer) * MINUTE_MS
    if (cursor == anchorEnd) return emptyMap()
    val result = mutableMapOf<Long, Long>()
    for (t in sched) {
        if (t.id == anchorId || t.isDone) continue
        val s = t.scheduledStart!!
        if (s < anchorEnd) continue
        if (s >= cursor) break
        val dur = t.durationMinutes() * MINUTE_MS
        if (cursor + dur > dayEnd) break
        result[t.id] = cursor
        cursor += dur + t.bufferOrDefault(globalBuffer) * MINUTE_MS
    }
    return result
}

// ---------- 計劃 vs 實際 ----------
enum class Verdict { NO_DATA, ON_TRACK, SLIGHTLY_OVER, OVER }

/** 實際 ≤ 預估：準時／提前；≤ 120%：稍微超時；> 120%：明顯超時。 */
fun computeVerdict(estimatedMin: Int?, actualMs: Long): Verdict {
    if (estimatedMin == null || estimatedMin <= 0 || actualMs <= 0) return Verdict.NO_DATA
    val estMs = estimatedMin * MINUTE_MS
    return when {
        actualMs <= estMs -> Verdict.ON_TRACK
        actualMs <= estMs * 1.2 -> Verdict.SLIGHTLY_OVER
        else -> Verdict.OVER
    }
}

/** 實際花費（毫秒）；進行中的紀錄用 now 當結束。 */
fun actualMillis(entries: List<TimeEntryEntity>, now: Long): Long =
    entries.sumOf { ((it.endTime ?: now) - it.startTime).coerceAtLeast(0L) }

data class TaskRow(val task: TaskEntity, val actualMs: Long)

data class DaySummary(
    val rows: List<TaskRow>,
    val totalEstimatedMin: Int,
    val totalActualMs: Long,
    val completed: List<TaskEntity>,   // 今天完成的（不含批次時段本身）
    val unfinished: List<TaskEntity>   // 今天排程但還沒完成的
) {
    val totalCount: Int get() = completed.size + unfinished.size
}

/** 某天的彙總：當天排程的任務 + 當天有計時的任務；實際時間只算當天開始的紀錄。 */
fun summarizeDay(
    tasks: List<TaskEntity>,
    entries: List<TimeEntryEntity>,
    dayStart: Long,
    dayEnd: Long,
    now: Long
): DaySummary {
    val actualBy = entries
        .filter { it.startTime in dayStart until dayEnd }
        .groupBy { it.taskId }
        .mapValues { actualMillis(it.value, now) }
    val ids = tasks.filter { t -> t.scheduledStart?.let { it in dayStart until dayEnd } == true }
        .map { it.id }.toSet() + actualBy.keys
    val rows = tasks.filter { it.id in ids }
        .sortedBy { it.scheduledStart ?: Long.MAX_VALUE }
        .map { TaskRow(it, actualBy[it.id] ?: 0L) }
    val completed = tasks.filter {
        it.isDone && it.kind != KIND_BATCH && it.completedAt?.let { c -> c in dayStart until dayEnd } == true
    }
    val unfinished = tasks.filter {
        !it.isDone && it.scheduledStart?.let { s -> s in dayStart until dayEnd } == true
    }.sortedBy { it.scheduledStart }
    return DaySummary(
        rows = rows,
        totalEstimatedMin = rows.sumOf { it.task.estimatedMinutes ?: 0 },
        totalActualMs = rows.sumOf { it.actualMs },
        completed = completed,
        unfinished = unfinished
    )
}

data class Accuracy(val samples: Int, val ratio: Double)

/** 歷史「實際 / 預估」比例（只看已完成且兩者都有的任務），用來修正之後的預估。 */
fun estimationAccuracy(tasks: List<TaskEntity>, entries: List<TimeEntryEntity>, now: Long): Accuracy? {
    val byTask = entries.groupBy { it.taskId }
    var est = 0L
    var act = 0L
    var n = 0
    for (t in tasks) {
        val e = t.estimatedMinutes ?: continue
        if (!t.isDone || e <= 0) continue
        val a = actualMillis(byTask[t.id].orEmpty(), now)
        if (a <= 0) continue
        est += e * MINUTE_MS
        act += a
        n++
    }
    return if (n == 0) null else Accuracy(n, act.toDouble() / est)
}

fun formatDuration(ms: Long): String {
    val totalMin = (ms / MINUTE_MS).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}小時${m}分" else "${m}分"
}

fun formatClock(minuteOfDay: Int): String = "%02d:%02d".format((minuteOfDay / 60) % 24, minuteOfDay % 60)
