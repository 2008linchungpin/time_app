package com.example.timeplanner

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.timeplanner.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前景服務：計時狀態以資料庫為準（endTime == null 的紀錄就是正在跑的），
 * 所以 App 被切走、鎖屏甚至被系統回收，時間都不會遺失；通知列用系統 Chronometer 顯示經過時間。
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as TimePlannerApplication
        if (intent?.action == ACTION_STOP) {
            scope.launch(Dispatchers.IO) { app.repository.stopTimer() }
        }
        // 立刻先掛上通知，避免 startForegroundService 逾時
        startForeground(Notifications.TIMER_ID, build("計時中…", System.currentTimeMillis()))
        if (job == null) {
            job = scope.launch {
                app.repository.observeRunning().collectLatest { entry ->
                    if (entry == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val title = app.repository.getTask(entry.taskId)?.title ?: "任務"
                        startForeground(Notifications.TIMER_ID, build("正在計時：$title", entry.startTime))
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun build(text: String, startedAt: Long) =
        NotificationCompat.Builder(this, Notifications.CH_TIMER)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(text)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                0, "停止",
                PendingIntent.getService(
                    this, 1, Intent(this, TimerService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.timeplanner.STOP_TIMER"

        /** running = true 時啟動服務（開始計時）；停止時服務會自己看到資料庫變化後結束。 */
        fun sync(context: Context, running: Boolean) {
            if (running) {
                try {
                    ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
                } catch (_: Exception) {
                    // 背景啟動被系統限制時，資料庫的計時仍然正確，只是少了通知列
                }
            }
        }
    }
}
