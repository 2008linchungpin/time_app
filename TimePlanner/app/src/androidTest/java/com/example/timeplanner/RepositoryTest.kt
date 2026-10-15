package com.example.timeplanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timeplanner.data.AppDatabase
import com.example.timeplanner.data.DailyReviewEntity
import com.example.timeplanner.data.KIND_BATCH
import com.example.timeplanner.data.TimePlannerRepository
import com.example.timeplanner.domain.TaskDraft
import com.example.timeplanner.domain.atMinute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 在裝置/模擬器上跑：Android Studio 對此檔案按 Run。使用記憶體資料庫，不影響真實資料。 */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TimePlannerRepository
    private val day = LocalDate.of(2030, 1, 1)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = TimePlannerRepository(db, null) { 10 } // 全域緩衝 10 分
    }

    @After fun tearDown() = db.close()

    private fun draft(title: String, minute: Int?, est: Int? = 30, buffer: Int? = null) =
        TaskDraft(title, "", est, minute?.let { day.atMinute(it) }, null, buffer)

    // ---- 新增 / 修改 / 刪除 ----
    @Test fun inboxAdd_blankRejected_andNotOnTimeline() = runBlocking {
        assertNull(repo.addInboxTask("   "))
        val id = repo.addInboxTask("  買牛奶 ")!!
        val t = repo.getTask(id)!!
        assertEquals("買牛奶", t.title)
        assertTrue(t.isInbox); assertNull(t.scheduledStart)
        assertEquals(1, repo.observeInbox().first().size)
    }

    @Test fun scheduling_leavesInbox_andDefaultsEstimate() = runBlocking {
        val id = repo.addInboxTask("寫報告")!!
        repo.updateTask(repo.getTask(id)!!.copy(scheduledStart = day.atMinute(600)))
        val t = repo.getTask(id)!!
        assertFalse(t.isInbox); assertEquals(30, t.estimatedMinutes)
        assertTrue(repo.observeInbox().first().isEmpty())
    }

    @Test fun edit_persists() = runBlocking {
        val id = repo.createTask(draft("舊", 600))!!
        repo.updateTask(repo.getTask(id)!!.copy(title = "新", estimatedMinutes = 45))
        val t = repo.getTask(id)!!
        assertEquals("新", t.title); assertEquals(45, t.estimatedMinutes)
    }

    @Test fun delete_cascadesTimeEntries() = runBlocking {
        val id = repo.createTask(draft("A", 600))!!
        repo.startTimer(id); repo.stopTimer(); repo.startTimer(id); repo.stopTimer()
        assertEquals(2, repo.observeEntriesForTask(id).first().size)
        repo.deleteTask(id)
        assertNull(repo.getTask(id))
        assertTrue(repo.observeAllEntries().first().isEmpty())
    }

    // ---- 計時 ----
    @Test fun timer_onlyOneRunning_andStopRecordsEnd() = runBlocking {
        val a = repo.createTask(draft("A", 600))!!
        val b = repo.createTask(draft("B", 700))!!
        repo.startTimer(a); repo.startTimer(b)
        assertEquals(b, repo.getRunning()!!.taskId)
        val aEntry = repo.observeEntriesForTask(a).first().single()
        assertNotNull(aEntry.endTime)
        repo.stopTimer()
        assertNull(repo.getRunning())
    }

    @Test fun markDone_stopsRunningTimer() = runBlocking {
        val a = repo.createTask(draft("A", 600))!!
        repo.startTimer(a)
        repo.setDone(a, true)
        assertNull(repo.getRunning())
        assertTrue(repo.getTask(a)!!.isDone)
        repo.setDone(a, false)
        assertFalse(repo.getTask(a)!!.isDone)
    }

    // ---- 衝突 ----
    @Test fun conflictDetection() = runBlocking {
        val a = repo.createTask(draft("A", 600, 60))!! // 10:00–11:00
        assertTrue(repo.hasConflict(day.atMinute(630), 30))
        assertFalse(repo.hasConflict(day.atMinute(660), 30))   // 剛好接在後面不算衝突
        assertFalse(repo.hasConflict(day.atMinute(630), 30, ignoreId = a)) // 排除自己
    }

    // ---- 緩衝 ----
    @Test fun buffer_pushesNextTask_andChains() = runBlocking {
        val b = repo.createTask(draft("B", 9 * 60 + 35, 30))!!   // 9:35–10:05
        val c = repo.createTask(draft("C", 10 * 60 + 10, 30))!!  // 10:10–10:40
        repo.createTask(draft("A", 9 * 60, 30))!!                // 9:00–9:30 + 緩衝 10 → B 要 ≥ 9:40
        assertEquals(day.atMinute(9 * 60 + 40), repo.getTask(b)!!.scheduledStart)  // 9:40–10:10
        assertEquals(day.atMinute(10 * 60 + 20), repo.getTask(c)!!.scheduledStart) // 連鎖：10:10 + 10 緩衝
    }

    @Test fun buffer_individualOverridesGlobal() = runBlocking {
        val b = repo.createTask(draft("B", 9 * 60 + 31, 30))!!
        repo.createTask(draft("A", 9 * 60, 30, buffer = 0))!! // 個別 0 分 → 不順延
        assertEquals(day.atMinute(9 * 60 + 31), repo.getTask(b)!!.scheduledStart)
    }

    // ---- 搬移 / 延期 ----
    @Test fun moveToTomorrow_andPostpone() = runBlocking {
        val id = repo.createTask(draft("A", 600))!!
        repo.moveToTomorrow(id)
        assertEquals(day.plusDays(1).atMinute(600), repo.getTask(id)!!.scheduledStart)
        repo.postpone(id)
        val t = repo.getTask(id)!!
        assertNull(t.scheduledStart); assertTrue(t.isInbox); assertEquals(1, t.postponedCount)
    }

    // ---- 雜碎 / 批次 ----
    @Test fun misc_notInInbox_andBatchFlow() = runBlocking {
        val m1 = repo.addMiscTask("寄信")!!
        val m2 = repo.addMiscTask("繳費")!!
        assertTrue(repo.observeInbox().first().isEmpty())
        assertEquals(2, repo.observeMisc().first().size)

        val batch = repo.createBatch(draft("雜務批次", 15 * 60, 30), listOf(m1, m2))
        assertEquals(KIND_BATCH, repo.getTask(batch)!!.kind)
        assertEquals(batch, repo.getTask(m1)!!.batchId)
        assertNull(repo.getTask(m1)!!.scheduledStart) // 成員不會單獨出現在時間軸

        repo.setDone(m1, true)
        assertFalse(repo.getTask(batch)!!.isDone)
        repo.setDone(m2, true)
        assertTrue(repo.getTask(batch)!!.isDone) // 全部完成 → 批次完成
    }

    @Test fun deleteBatch_releasesMembers() = runBlocking {
        val m = repo.addMiscTask("小事")!!
        val batch = repo.createBatch(draft("批次", 900, 15), listOf(m))
        repo.deleteTask(batch)
        assertNotNull(repo.getTask(m))
        assertNull(repo.getTask(m)!!.batchId)
    }

    @Test fun completeBatch_marksAllMembers() = runBlocking {
        val m1 = repo.addMiscTask("a")!!
        val m2 = repo.addMiscTask("b")!!
        val batch = repo.createBatch(draft("批次", 900, 15), listOf(m1, m2))
        repo.completeBatch(batch)
        assertTrue(repo.getTask(m1)!!.isDone && repo.getTask(m2)!!.isDone && repo.getTask(batch)!!.isDone)
    }

    // ---- 每日回顧 ----
    @Test fun review_upsertAndRead() = runBlocking {
        val e = day.toEpochDay()
        repo.saveReview(DailyReviewEntity(e, highlight = "完成報告", rating = 4, doneCount = 3, totalCount = 5))
        repo.saveReview(DailyReviewEntity(e, highlight = "更新", rating = 5, completedAt = 1L))
        val r = repo.getReview(e)!!
        assertEquals("更新", r.highlight); assertEquals(5, r.rating)
        assertEquals(1, repo.observeReviews().first().size)
    }
}
