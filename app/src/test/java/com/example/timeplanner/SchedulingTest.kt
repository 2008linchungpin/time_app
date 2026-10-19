package com.example.timeplanner

import com.example.timeplanner.data.TaskEntity
import com.example.timeplanner.data.TimeEntryEntity
import com.example.timeplanner.domain.MINUTE_MS
import com.example.timeplanner.domain.Verdict
import com.example.timeplanner.domain.assignLanes
import com.example.timeplanner.domain.atMinute
import com.example.timeplanner.domain.computeBufferShifts
import com.example.timeplanner.domain.computeVerdict
import com.example.timeplanner.domain.dayEndMillis
import com.example.timeplanner.domain.dayStartMillis
import com.example.timeplanner.domain.computeOverallStats
import com.example.timeplanner.domain.movingAverage
import com.example.timeplanner.domain.effectiveReminder
import com.example.timeplanner.data.REMINDER_NONE
import com.example.timeplanner.domain.estimationAccuracy
import com.example.timeplanner.domain.findOverlaps
import com.example.timeplanner.domain.summarizeDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 純 JVM 單元測試（不需要模擬器）：./gradlew testDebugUnitTest */
class SchedulingTest {
    private val day = LocalDate.of(2030, 1, 1)
    private fun task(id: Long, minute: Int, est: Int = 30, done: Boolean = false, completedAt: Long? = null) =
        TaskEntity(id = id, title = "t$id", estimatedMinutes = est, isInbox = false,
            scheduledStart = day.atMinute(minute), isDone = done, completedAt = completedAt)

    @Test fun overlaps_detected_andTouchingIsNotOverlap() {
        val ids = findOverlaps(listOf(task(1, 600, 60), task(2, 630), task(3, 660)))
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun lanes_sideBySideForOverlaps() {
        val lanes = assignLanes(listOf(task(1, 600, 60), task(2, 630), task(3, 720)))
        assertEquals(2, lanes[1]!!.count); assertEquals(2, lanes[2]!!.count)
        assertEquals(1, lanes[3]!!.count)
        assertTrue(lanes[1]!!.index != lanes[2]!!.index)
    }

    @Test fun verdict_thresholds() {
        assertEquals(Verdict.NO_DATA, computeVerdict(null, 10 * MINUTE_MS))
        assertEquals(Verdict.ON_TRACK, computeVerdict(30, 25 * MINUTE_MS))
        assertEquals(Verdict.SLIGHTLY_OVER, computeVerdict(30, 35 * MINUTE_MS))
        assertEquals(Verdict.OVER, computeVerdict(30, 40 * MINUTE_MS))
    }

    @Test fun bufferShifts_chain_andStopWhenGapIsEnough() {
        val list = listOf(task(1, 540), task(2, 575), task(3, 610), task(4, 700))
        val r = computeBufferShifts(list, 1, 10, day.dayEndMillis())
        assertEquals(day.atMinute(580), r[2])       // 9:35 → 9:40
        assertEquals(day.atMinute(620), r[3])       // 10:10 → 10:20（連鎖）
        assertNull(r[4])                             // 11:40 已經夠遠
    }

    @Test fun bufferShifts_zeroBuffer_orOverlapUntouched() {
        assertTrue(computeBufferShifts(listOf(task(1, 540), task(2, 560)), 1, 0, day.dayEndMillis()).isEmpty())
        // 真正重疊的任務不動（留給重疊警告）
        assertTrue(computeBufferShifts(listOf(task(1, 540), task(2, 560)), 1, 10, day.dayEndMillis()).isEmpty())
    }

    @Test fun summary_totalsAndLists() {
        val s = day.dayStartMillis(); val e = day.dayEndMillis()
        val done = task(1, 540, 30, done = true, completedAt = day.atMinute(600))
        val open = task(2, 660, 60)
        val entries = listOf(TimeEntryEntity(1, 1, day.atMinute(540), day.atMinute(585))) // 45 分
        val sum = summarizeDay(listOf(done, open), entries, s, e, day.atMinute(700))
        assertEquals(90, sum.totalEstimatedMin)
        assertEquals(45 * MINUTE_MS, sum.totalActualMs)
        assertEquals(listOf(1L), sum.completed.map { it.id })
        assertEquals(listOf(2L), sum.unfinished.map { it.id })
        assertEquals(2, sum.totalCount)
    }

    @Test fun accuracy_ratio() {
        val t = task(1, 540, 30, done = true)
        val acc = estimationAccuracy(listOf(t), listOf(TimeEntryEntity(1, 1, 0, 45 * MINUTE_MS)), 0)
        assertNotNull(acc); assertEquals(1.5, acc!!.ratio, 1e-9)
    }

    @Test fun reminder_followsSettingsUnlessOverridden() {
        val t = task(1, 9 * 60)
        assertEquals(10, t.effectiveReminder(10))                                  // 未設定 → 用預設
        assertNull(t.effectiveReminder(null))                                      // 預設為不提醒
        assertEquals(5, t.copy(reminderMinutes = 5).effectiveReminder(10))         // 個別覆寫
        assertNull(t.copy(reminderMinutes = REMINDER_NONE).effectiveReminder(10))  // 明確不提醒
    }

    @Test fun buffer_followsGlobalUnlessOverridden() {
        val a = task(1, 9 * 60); val b = task(2, 9 * 60 + 30)
        assertEquals(day.atMinute(9 * 60 + 40), computeBufferShifts(listOf(a, b), 1, 10, day.dayEndMillis())[2L]) // 全域 10 分
        assertTrue(computeBufferShifts(listOf(a.copy(bufferMinutes = 0), b), 1, 10, day.dayEndMillis()).isEmpty()) // 覆寫 0
    }

    @Test fun overallStats_countsDaysStreakAndFocus() {
        val today = day.plusDays(4)
        fun done(id: Long, d: LocalDate, est: Int) = task(id, 9 * 60, est, done = true,
            completedAt = d.atMinute(10 * 60)).copy(createdAt = d.atMinute(8 * 60))
        val tasks = listOf(done(1, day, 30), done(2, day.plusDays(1), 30), done(3, day.plusDays(1), 30), done(4, day.plusDays(3), 30))
        val entries = listOf(TimeEntryEntity(1, 1, day.atMinute(9 * 60), day.atMinute(9 * 60 + 45)))
        val s = computeOverallStats(tasks, entries, today, today.atMinute(12 * 60))
        assertEquals(5, s.days.size)                    // 第一天 → 今天，每天一筆
        assertEquals(4, s.totalCompleted)
        assertEquals(45, s.totalFocusMin)
        assertEquals(3, s.activeDays)
        assertEquals(2, s.bestStreak)                   // day0, day1
        assertEquals(1, s.currentStreak)                // 今天還沒完成不中斷；從昨天（day3）往回算 = 1
    }

    @Test fun movingAverage_usesAvailableHistory() {
        assertEquals(listOf(2f, 3f, 5f), movingAverage(listOf(2f, 4f, 6f), 2))
    }
}
