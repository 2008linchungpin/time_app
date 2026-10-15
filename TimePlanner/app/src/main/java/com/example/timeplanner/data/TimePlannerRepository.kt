package com.example.timeplanner.data

import androidx.room.withTransaction
import com.example.timeplanner.domain.DEFAULT_MINUTES
import com.example.timeplanner.domain.MINUTE_MS
import com.example.timeplanner.domain.TaskDraft
import com.example.timeplanner.domain.atMinute
import com.example.timeplanner.domain.computeBufferShifts
import com.example.timeplanner.domain.dayEndMillis
import com.example.timeplanner.domain.dayStartMillis
import com.example.timeplanner.domain.shiftDays
import com.example.timeplanner.domain.toLocalDate
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 提醒鬧鐘的抽象（正式版由 ReminderScheduler 實作，測試可傳 null）。 */
interface ReminderGateway {
    fun schedule(task: TaskEntity)
    fun cancel(taskId: Long)
}

/** 全 App 唯一的資料入口：本機 Room(SQLite)，完全離線可用。 */
class TimePlannerRepository(
    private val db: AppDatabase,
    private val gateway: ReminderGateway? = null,
    private val globalBuffer: () -> Int = { 0 }
) {
    private val tasks = db.taskDao()
    private val entries = db.timeEntryDao()
    private val reviews = db.reviewDao()

    // ---------- 讀取 ----------
    fun observeScheduled(from: Long, to: Long): Flow<List<TaskEntity>> = tasks.observeScheduled(from, to)
    fun observeInbox(): Flow<List<TaskEntity>> = tasks.observeInbox()
    fun observeMisc(): Flow<List<TaskEntity>> = tasks.observeMisc()
    fun observeAllTasks(): Flow<List<TaskEntity>> = tasks.observeAll()
    fun observeTask(id: Long): Flow<TaskEntity?> = tasks.observeById(id)
    fun observeMembers(batchId: Long): Flow<List<TaskEntity>> = tasks.observeMembers(batchId)
    suspend fun getTask(id: Long): TaskEntity? = tasks.getById(id)
    suspend fun getMembers(batchId: Long): List<TaskEntity> = tasks.getMembers(batchId)

    fun observeAllEntries(): Flow<List<TimeEntryEntity>> = entries.observeAll()
    fun observeEntriesForTask(taskId: Long): Flow<List<TimeEntryEntity>> = entries.observeForTask(taskId)
    fun observeRunning(): Flow<TimeEntryEntity?> = entries.observeRunning()
    suspend fun getRunning(): TimeEntryEntity? = entries.getRunning()

    fun observeReview(epochDay: Long): Flow<DailyReviewEntity?> = reviews.observe(epochDay)
    fun observeReviews(): Flow<List<DailyReviewEntity>> = reviews.observeAll()

    // ---------- 任務 CRUD ----------
    /** Inbox 快速新增：只需要標題。空白標題回傳 null。 */
    suspend fun addInboxTask(title: String): Long? {
        val t = title.trim()
        if (t.isEmpty()) return null
        return tasks.insert(TaskEntity(title = t, isInbox = true))
    }

    /** 雜碎快速新增：沒有時間，只出現在「雜碎」頁。 */
    suspend fun addMiscTask(title: String): Long? {
        val t = title.trim()
        if (t.isEmpty()) return null
        return tasks.insert(normalize(TaskEntity(title = t, kind = KIND_MISC)))
    }

    suspend fun createTask(draft: TaskDraft): Long? {
        val t = draft.title.trim()
        if (t.isEmpty()) return null
        val id = tasks.insert(
            normalize(
                TaskEntity(
                    title = t, notes = draft.notes, estimatedMinutes = draft.estimatedMinutes,
                    scheduledStart = draft.start, reminderMinutes = draft.reminderMinutes,
                    bufferMinutes = draft.bufferMinutes
                )
            )
        )
        afterSchedule(id)
        return id
    }

    suspend fun updateTask(task: TaskEntity) {
        tasks.update(normalize(task))
        afterSchedule(task.id)
    }

    /** 移動任務；移動後依緩衝設定，把緊接在後面的任務自動往後順延。 */
    suspend fun moveTask(taskId: Long, newStart: Long) {
        val t = tasks.getById(taskId) ?: return
        tasks.update(normalize(t.copy(scheduledStart = newStart)))
        afterSchedule(taskId)
    }

    /** 一鍵移到隔天：已排程 → 同時間 +1 天；未排程 → 隔天 09:00。 */
    suspend fun moveToTomorrow(taskId: Long) {
        val t = tasks.getById(taskId) ?: return
        val s = t.scheduledStart
        val newStart = s?.shiftDays(1) ?: LocalDate.now().plusDays(1).atMinute(9 * 60)
        moveTask(taskId, newStart)
    }

    /** 延期：取消排程、放回 Inbox，並記錄延期次數（批次時段不適用）。 */
    suspend fun postpone(taskId: Long) {
        val t = tasks.getById(taskId) ?: return
        if (t.kind == KIND_BATCH) return
        tasks.update(normalize(t.copy(scheduledStart = null, postponedCount = t.postponedCount + 1)))
        gateway?.cancel(taskId)
    }

    suspend fun unschedule(taskId: Long) {
        val t = tasks.getById(taskId) ?: return
        if (t.kind == KIND_BATCH) return
        tasks.update(normalize(t.copy(scheduledStart = null)))
        gateway?.cancel(taskId)
    }

    suspend fun setDone(taskId: Long, done: Boolean) {
        db.withTransaction {
            val t = tasks.getById(taskId) ?: return@withTransaction
            val now = System.currentTimeMillis()
            if (done && entries.getRunning()?.taskId == taskId) entries.stopRunning(now)
            tasks.update(t.copy(isDone = done, completedAt = if (done) now else null))
            // 批次的成員全部完成 → 批次自動完成
            val bid = t.batchId
            if (done && bid != null && tasks.getMembers(bid).all { it.isDone }) {
                tasks.getById(bid)?.let { if (!it.isDone) tasks.update(it.copy(isDone = true, completedAt = now)) }
            }
        }
        if (done) gateway?.cancel(taskId) else refresh(taskId)
    }

    /** 刪除任務；其計時紀錄因外鍵 CASCADE 一併刪除；刪批次時，成員的 batchId 會被清成 null。 */
    suspend fun deleteTask(taskId: Long) {
        gateway?.cancel(taskId)
        tasks.getById(taskId)?.let { tasks.delete(it) }
    }

    /** 與其他已排程任務時間重疊？（編輯時用 ignoreId 排除自己） */
    suspend fun hasConflict(start: Long, minutes: Int, ignoreId: Long = 0L): Boolean =
        tasks.countConflicts(start, start + minutes * MINUTE_MS, ignoreId) > 0

    // ---------- 雜碎 / 批次 ----------
    /** 建立批次時段，並把選取的雜碎任務放進去。 */
    suspend fun createBatch(draft: TaskDraft, memberIds: List<Long>): Long {
        requireNotNull(draft.start) { "批次時段必須有開始時間" }
        val id = db.withTransaction {
            val batchId = tasks.insert(
                normalize(
                    TaskEntity(
                        title = draft.title.trim().ifEmpty { "雜務批次" },
                        notes = draft.notes,
                        estimatedMinutes = draft.estimatedMinutes,
                        kind = KIND_BATCH,
                        scheduledStart = draft.start,
                        reminderMinutes = draft.reminderMinutes,
                        bufferMinutes = draft.bufferMinutes
                    )
                )
            )
            tasks.setBatch(memberIds, batchId)
            batchId
        }
        afterSchedule(id)
        return id
    }

    suspend fun removeFromBatch(taskId: Long) {
        tasks.setBatch(listOf(taskId), null)
    }

    /** 批次內全部標為完成（批次本身也完成）。 */
    suspend fun completeBatch(batchId: Long) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            tasks.markMembersDone(batchId, now)
            val b = tasks.getById(batchId) ?: return@withTransaction
            if (entries.getRunning()?.taskId == batchId) entries.stopRunning(now)
            tasks.update(b.copy(isDone = true, completedAt = now))
        }
        gateway?.cancel(batchId)
    }

    // ---------- 計時 ----------
    /** 開始計時；同時間只會有一個計時，先停掉正在跑的。 */
    suspend fun startTimer(taskId: Long) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            entries.stopRunning(now)
            entries.insert(TimeEntryEntity(taskId = taskId, startTime = now))
        }
    }

    suspend fun stopTimer() = entries.stopRunning(System.currentTimeMillis())
    suspend fun deleteEntry(entry: TimeEntryEntity) = entries.delete(entry)

    // ---------- 每日回顧 ----------
    suspend fun saveReview(review: DailyReviewEntity) = reviews.upsert(review)
    suspend fun getReview(epochDay: Long): DailyReviewEntity? = reviews.get(epochDay)

    // ---------- 提醒 ----------
    /** App 啟動 / 開機後呼叫：重新排好所有未來任務的提醒鬧鐘。 */
    suspend fun rescheduleAll() {
        tasks.getFutureScheduled(System.currentTimeMillis()).forEach { gateway?.schedule(it) }
    }

    // ---------- 內部規則 ----------
    private suspend fun refresh(id: Long) {
        tasks.getById(id)?.let { gateway?.schedule(it) }
    }

    private suspend fun afterSchedule(id: Long) {
        applyBuffer(id)
        refresh(id)
    }

    /** 緩衝：以 anchor 任務為起點，把落在「結束 + 緩衝」之內的後續任務依序往後順延。 */
    private suspend fun applyBuffer(anchorId: Long) {
        val a = tasks.getById(anchorId) ?: return
        val start = a.scheduledStart ?: return
        val day = start.toLocalDate()
        val dayTasks = tasks.getScheduledBetween(day.dayStartMillis(), day.dayEndMillis())
        val shifts = computeBufferShifts(dayTasks, anchorId, globalBuffer(), day.dayEndMillis())
        for ((id, newStart) in shifts) {
            val t = tasks.getById(id) ?: continue
            val moved = t.copy(scheduledStart = newStart)
            tasks.update(moved)
            gateway?.schedule(moved)
        }
    }

    private fun normalize(t: TaskEntity): TaskEntity =
        if (t.scheduledStart != null) {
            t.copy(isInbox = false, estimatedMinutes = t.estimatedMinutes ?: DEFAULT_MINUTES)
        } else {
            t.copy(isInbox = t.kind == KIND_NORMAL)
        }
}
