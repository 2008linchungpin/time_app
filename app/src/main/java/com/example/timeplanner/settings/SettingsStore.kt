package com.example.timeplanner.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val globalBufferMinutes: Int = 0,          // 每個任務結束後預設緩衝
    val defaultReminderMinutes: Int? = 10,     // 新任務預設提前提醒（null = 不提醒）
    val defaultStartMinuteOfDay: Int = 9 * 60, // 新任務預設開始時間（一天中的第幾分鐘，預設 09:00）
    val reviewEnabled: Boolean = true,
    val reviewHour: Int = 21,
    val reviewMinute: Int = 0
)

/** 輕量設定存放（SharedPreferences，離線、無額外依賴）。 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state

    private fun read() = AppSettings(
        globalBufferMinutes = prefs.getInt("buffer", 0),
        defaultReminderMinutes = prefs.getInt("reminder", 10).takeIf { it >= 0 },
        defaultStartMinuteOfDay = prefs.getInt("defaultStart", 9 * 60).coerceIn(0, 23 * 60 + 55),
        reviewEnabled = prefs.getBoolean("reviewEnabled", true),
        reviewHour = prefs.getInt("reviewHour", 21),
        reviewMinute = prefs.getInt("reviewMinute", 0)
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val n = transform(_state.value)
        _state.value = n
        prefs.edit()
            .putInt("buffer", n.globalBufferMinutes)
            .putInt("reminder", n.defaultReminderMinutes ?: -1)
            .putInt("defaultStart", n.defaultStartMinuteOfDay)
            .putBoolean("reviewEnabled", n.reviewEnabled)
            .putInt("reviewHour", n.reviewHour)
            .putInt("reviewMinute", n.reviewMinute)
            .apply()
    }
}
