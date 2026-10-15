package com.example.timeplanner.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CH_TIMER = "timer"
    const val CH_REMINDER = "reminder"
    const val CH_REVIEW = "review"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_TIMER, "計時中", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_REMINDER, "任務提醒", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_REVIEW, "每日回顧", NotificationManager.IMPORTANCE_DEFAULT)
            )
        )
    }

    /** 每個任務固定一個通知 id。 */
    fun reminderId(taskId: Long): Int = 2000 + (taskId % 100_000).toInt()
    const val REVIEW_ID = 1900
    const val TIMER_ID = 1
}
