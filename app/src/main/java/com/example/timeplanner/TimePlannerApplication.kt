package com.example.timeplanner

import android.app.Application
import com.example.timeplanner.data.AppDatabase
import com.example.timeplanner.data.TimePlannerRepository
import com.example.timeplanner.notify.Notifications
import com.example.timeplanner.notify.ReminderScheduler
import com.example.timeplanner.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimePlannerApplication : Application() {
    /** 不會隨畫面關閉而取消的 scope（快速新增、通知動作用）。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings by lazy { SettingsStore(this) }
    val reminderScheduler by lazy { ReminderScheduler(this) { settings.state.value.defaultReminderMinutes } }
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy {
        TimePlannerRepository(database, reminderScheduler) { settings.state.value.globalBufferMinutes }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        appScope.launch {
            repository.ensureDefaultCategories()
            repository.rescheduleAll()
            reminderScheduler.scheduleDailyReview(settings.state.value)
        }
    }
}
