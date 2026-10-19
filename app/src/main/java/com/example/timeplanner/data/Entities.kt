package com.example.timeplanner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val KIND_NORMAL = "NORMAL"
const val KIND_MISC = "MISC"    // 雜碎小事：預設沒有時間，只有加入批次才有時間
/** reminderMinutes 的特殊值：此任務明確「不提醒」（即使設定有預設提醒）。 */
const val REMINDER_NONE = -1
const val KIND_BATCH = "BATCH"  // 批次時段：時間軸上的一個特殊區塊，成員是 MISC 任務

/**
 * 任務。規則：
 * - 一般任務沒排程 → isInbox = true（待整理區，不佔時間軸）
 * - scheduledStart != null → 已排程（isInbox = false），結束 = 開始 + 預估分鐘（預設 30）
 * - MISC 任務永遠 isInbox = false，只出現在「雜碎」頁；加入批次後 batchId 指向 BATCH 任務
 * - reminderMinutes：開始前幾分鐘提醒（null = 沿用設定的預設提醒；REMINDER_NONE = 明確不提醒）
 * - bufferMinutes：此任務結束後的緩衝（null = 用全域設定）
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("scheduledStart"), Index("isInbox"), Index("batchId"), Index("kind")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val estimatedMinutes: Int? = null,
    val kind: String = KIND_NORMAL,
    val isInbox: Boolean = true,
    val scheduledStart: Long? = null, // epoch millis
    val isDone: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val reminderMinutes: Int? = null,
    val bufferMinutes: Int? = null,
    val batchId: Long? = null,
    val postponedCount: Int = 0,
    /** 分類（null = 未分類）；對應 categories.id，依設定中的順序代表重要程度。 */
    val categoryId: Long? = null
)

/** 任務分類。sortOrder 越小越重要（0 = 最重要）。 */
@Entity(tableName = "categories", indices = [Index("sortOrder")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    /** 顯示用顏色（ARGB）；可在設定自訂。 */
    val colorArgb: Int = DEFAULT_CATEGORY_COLOR
)

const val DEFAULT_CATEGORY_COLOR: Int = 0xFF1E88E5.toInt()

/** 一筆實際計時紀錄；一個任務可有多筆。endTime == null 代表正在計時。 */
@Entity(
    tableName = "time_entries",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class TimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val startTime: Long,
    val endTime: Long? = null
)

/** 每日回顧（主鍵 = LocalDate.toEpochDay()）。完成回顧時會把當天統計存下來，之後歷史不會被改動。 */
@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
    @PrimaryKey val epochDay: Long,
    val highlight: String = "",     // 今天最大收穫
    val overtime: String = "",      // 哪些任務超時、為什麼
    val tomorrowFocus: String = "", // 明天優先
    val rating: Int = 0,
    val completedAt: Long? = null,  // 有值 = 已完成回顧
    val estimatedMinutes: Int = 0,
    val actualMinutes: Int = 0,
    val doneCount: Int = 0,
    val totalCount: Int = 0
)
