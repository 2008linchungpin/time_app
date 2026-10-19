package com.example.timeplanner.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timeplanner.MainActivity
import com.example.timeplanner.TimePlannerApplication
import com.example.timeplanner.TimerService
import com.example.timeplanner.domain.durationMinutes
import com.example.timeplanner.domain.formatClock
import com.example.timeplanner.domain.minuteOfDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 處理：任務提醒、通知上的「開始計時」、每日回顧提醒、開機後重排鬧鐘。 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TimePlannerApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: TimePlannerApplication, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> remind(app, intent.getLongExtra(EXTRA_TASK_ID, 0L))
            ACTION_START_TIMER -> {
                val id = intent.getLongExtra(EXTRA_TASK_ID, 0L)
                if (id > 0) {
                    app.repository.startTimer(id)
                    NotificationManagerCompat.from(app).cancel(Notifications.reminderId(id))
                    TimerService.sync(app, true)
                }
            }
            ACTION_REVIEW -> {
                postReview(app)
                app.reminderScheduler.scheduleDailyReview(app.settings.state.value) // 排明天
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                app.repository.rescheduleAll()
                app.reminderScheduler.scheduleDailyReview(app.settings.state.value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun remind(app: TimePlannerApplication, taskId: Long) {
        val task = app.repository.getTask(taskId) ?: return
        val start = task.scheduledStart ?: return
        if (task.isDone) return
        val left = ((start - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        val nid = Notifications.reminderId(taskId)

        val open = PendingIntent.getActivity(
            app, nid,
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_EDIT_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val startTimer = PendingIntent.getBroadcast(
            app, 0,
            Intent(app, AlarmReceiver::class.java)
                .setAction(ACTION_START_TIMER)
                .setData(Uri.parse("timeplanner://start/$taskId"))
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, Notifications.CH_REMINDER)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("即將開始：${task.title}")
            .setContentText("$left 分鐘後（${formatClock(start.minuteOfDay())}）· 預估 ${task.durationMinutes()} 分")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "開始計時", startTimer)
            .addAction(0, "調整時間", open)
            .build()
        if (NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            NotificationManagerCompat.from(app).notify(nid, n)
        }
    }

    @SuppressLint("MissingPermission")
    private fun postReview(app: TimePlannerApplication) {
        val open = PendingIntent.getActivity(
            app, Notifications.REVIEW_ID,
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_REVIEW, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, Notifications.CH_REVIEW)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("今天要結束了")
            .setContentText("花兩分鐘回顧今天，為明天做準備")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        if (NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            NotificationManagerCompat.from(app).notify(Notifications.REVIEW_ID, n)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.timeplanner.REMIND"
        const val ACTION_START_TIMER = "com.example.timeplanner.START_TIMER"
        const val ACTION_REVIEW = "com.example.timeplanner.REVIEW"
        const val EXTRA_TASK_ID = "task_id"
    }
}
