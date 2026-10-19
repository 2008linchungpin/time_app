package com.example.timeplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.timeplanner.TimePlannerApplication
import com.example.timeplanner.TimerService
import com.example.timeplanner.data.DailyReviewEntity
import com.example.timeplanner.data.TaskEntity
import com.example.timeplanner.data.TimeEntryEntity
import com.example.timeplanner.domain.TaskDraft
import com.example.timeplanner.domain.dayEndMillis
import com.example.timeplanner.domain.dayStartMillis
import com.example.timeplanner.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModel(private val app: TimePlannerApplication) : ViewModel() {
    private val repo = app.repository
    private val share = SharingStarted.WhileSubscribed(5_000)

    val settings: StateFlow<AppSettings> = app.settings.state

    val selectedDate = MutableStateFlow(LocalDate.now())

    val dayTasks: StateFlow<List<TaskEntity>> = selectedDate
        .flatMapLatest { repo.observeScheduled(it.dayStartMillis(), it.dayEndMillis()) }
        .stateIn(viewModelScope, share, emptyList())

    val inbox = repo.observeInbox().stateIn(viewModelScope, share, emptyList())
    val misc = repo.observeMisc().stateIn(viewModelScope, share, emptyList())
    val allTasks = repo.observeAllTasks().stateIn(viewModelScope, share, emptyList())
    val allEntries = repo.observeAllEntries().stateIn(viewModelScope, share, emptyList())
    val reviews = repo.observeReviews().stateIn(viewModelScope, share, emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, share, emptyList())

    val running: StateFlow<TimeEntryEntity?> = repo.observeRunning().stateIn(viewModelScope, share, null)
    val runningTask: StateFlow<TaskEntity?> = running
        .flatMapLatest { e -> if (e == null) flowOf(null) else repo.observeTask(e.taskId) }
        .stateIn(viewModelScope, share, null)

    /** 通知點擊 → 要在時間軸開啟編輯的任務；開過後由畫面清掉。 */
    val editRequest = MutableStateFlow<TaskEntity?>(null)
    val openReview = MutableStateFlow(false)

    fun reviewFor(date: LocalDate): Flow<DailyReviewEntity?> = repo.observeReview(date.toEpochDay())
    fun entriesFor(taskId: Long): Flow<List<TimeEntryEntity>> = repo.observeEntriesForTask(taskId)

    // ---------- 導覽 ----------
    fun openTask(id: Long) {
        viewModelScope.launch {
            repo.getTask(id)?.let { t ->
                t.scheduledStart?.let { selectedDate.value = java.time.Instant.ofEpochMilli(it)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                editRequest.value = t
            }
        }
    }
    fun consumeEditRequest() { editRequest.value = null }

    // ---------- 任務 ----------
    fun quickAdd(title: String, misc: Boolean) {
        viewModelScope.launch { if (misc) repo.addMiscTask(title) else repo.addInboxTask(title) }
    }
    fun createTask(draft: TaskDraft) { viewModelScope.launch { repo.createTask(draft) } }

    fun saveEdit(task: TaskEntity, d: TaskDraft) {
        viewModelScope.launch {
            repo.updateTask(
                task.copy(
                    title = d.title, notes = d.notes, estimatedMinutes = d.estimatedMinutes,
                    scheduledStart = d.start, reminderMinutes = d.reminderMinutes,
                    bufferMinutes = d.bufferMinutes, categoryId = d.categoryId
                )
            )
        }
    }
    fun moveTask(id: Long, start: Long) { viewModelScope.launch { repo.moveTask(id, start) } }
    fun toggleDone(t: TaskEntity) { viewModelScope.launch { repo.setDone(t.id, !t.isDone) } }
    fun delete(t: TaskEntity) { viewModelScope.launch { repo.deleteTask(t.id) } }
    fun unschedule(t: TaskEntity) { viewModelScope.launch { repo.unschedule(t.id) } }
    suspend fun hasConflict(start: Long, minutes: Int, ignoreId: Long) = repo.hasConflict(start, minutes, ignoreId)

    // ---------- 計時 ----------
    fun toggleTimer(t: TaskEntity) {
        viewModelScope.launch {
            if (running.value?.taskId == t.id) {
                repo.stopTimer()
            } else {
                repo.startTimer(t.id)
                TimerService.sync(app, true)
            }
        }
    }
    fun stopTimer() { viewModelScope.launch { repo.stopTimer() } }
    fun deleteEntry(e: TimeEntryEntity) { viewModelScope.launch { repo.deleteEntry(e) } }

    // ---------- 雜碎 / 批次 ----------
    fun createBatch(d: TaskDraft, ids: List<Long>) { viewModelScope.launch { repo.createBatch(d, ids) } }
    fun removeFromBatch(t: TaskEntity) { viewModelScope.launch { repo.removeFromBatch(t.id) } }
    fun completeBatch(id: Long) { viewModelScope.launch { repo.completeBatch(id) } }

    // ---------- 每日回顧 ----------
    fun moveTomorrow(t: TaskEntity) { viewModelScope.launch { repo.moveToTomorrow(t.id) } }
    fun moveAllTomorrow(list: List<TaskEntity>) { viewModelScope.launch { list.forEach { repo.moveToTomorrow(it.id) } } }
    fun postpone(t: TaskEntity) { viewModelScope.launch { repo.postpone(t.id) } }
    fun saveReview(r: DailyReviewEntity) { viewModelScope.launch { repo.saveReview(r) } }

    // ---------- 分類 ----------
    fun addCategory(name: String, colorArgb: Int) { viewModelScope.launch { repo.addCategory(name, colorArgb) } }
    fun setCategoryColor(id: Long, colorArgb: Int) { viewModelScope.launch { repo.setCategoryColor(id, colorArgb) } }
    fun renameCategory(id: Long, name: String) { viewModelScope.launch { repo.renameCategory(id, name) } }
    fun deleteCategory(id: Long) { viewModelScope.launch { repo.deleteCategory(id) } }
    fun moveCategoryUp(id: Long) { viewModelScope.launch { repo.moveCategory(id, -1) } }
    fun moveCategoryDown(id: Long) { viewModelScope.launch { repo.moveCategory(id, +1) } }

    // ---------- 設定 ----------
    fun updateSettings(t: (AppSettings) -> AppSettings) {
        app.settings.update(t)
        app.reminderScheduler.scheduleDailyReview(app.settings.state.value)
        // 預設提醒 / 緩衝可能改變 → 重新排好既有任務的提醒
        viewModelScope.launch { repo.rescheduleAll() }
    }
    fun canExactAlarm(): Boolean = app.reminderScheduler.canScheduleExact()

    companion object {
        val Factory = viewModelFactory {
            initializer { PlannerViewModel(this[APPLICATION_KEY] as TimePlannerApplication) }
        }
    }
}
