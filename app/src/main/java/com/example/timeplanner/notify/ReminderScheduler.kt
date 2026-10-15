package com.example.timeplanner.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.timeplanner.data.ReminderGateway
import com.example.timeplanner.data.TaskEntity
import com.example.timeplanner.settings.AppSettings
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 用 AlarmManager 排「任務開始前提醒」與「每日回顧提醒」。
 * 有精準鬧鐘權限 → setExactAndAllowWhileIdle；沒有 → 退而用 setAndAllowWhileIdle（可能延遲幾分鐘）。
 */
class ReminderScheduler(private val context: Context) : ReminderGateway {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    override fun schedule(task: TaskEntity) {
        cancel(task.id)
        val start = task.scheduledStart ?: return
        val minutes = task.reminderMinutes ?: return
        if (task.isDone) return
        val at = start - minutes * 60_000L
        if (at <= System.currentTimeMillis()) return
        setAlarm(at, remindIntent(task.id))
    }

    override fun cancel(taskId: Long) {
        am.cancel(remindIntent(taskId))
    }

    fun scheduleDailyReview(s: AppSettings) {
        val pi = reviewIntent()
        am.cancel(pi)
        if (!s.reviewEnabled) return
        val zone = ZoneId.systemDefault()
        var next = LocalDate.now().atTime(s.reviewHour, s.reviewMinute).atZone(zone)
        if (!next.isAfter(ZonedDateTime.now(zone))) next = next.plusDays(1)
        setAlarm(next.toInstant().toEpochMilli(), pi)
    }

    private fun setAlarm(at: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // PendingIntent 以 action + data 區分；extras 不影響比對，所以 cancel 時可以不帶
    private fun remindIntent(taskId: Long): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_REMIND)
            .setData(Uri.parse("timeplanner://task/$taskId"))
            .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun reviewIntent(): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_REVIEW)
            .setData(Uri.parse("timeplanner://review"))
        return PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
