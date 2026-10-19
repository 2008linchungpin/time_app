package com.example.timeplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE scheduledStart >= :from AND scheduledStart < :to ORDER BY scheduledStart")
    fun observeScheduled(from: Long, to: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduledStart >= :from AND scheduledStart < :to ORDER BY scheduledStart")
    suspend fun getScheduledBetween(from: Long, to: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isInbox = 1 AND isDone = 0 ORDER BY createdAt DESC")
    fun observeInbox(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE kind = 'MISC' AND isDone = 0 ORDER BY createdAt DESC")
    fun observeMisc(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE batchId = :batchId ORDER BY createdAt")
    fun observeMembers(batchId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE batchId = :batchId ORDER BY createdAt")
    suspend fun getMembers(batchId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE scheduledStart IS NOT NULL AND isDone = 0 AND scheduledStart > :now")
    suspend fun getFutureScheduled(now: Long): List<TaskEntity>

    @Query(
        "SELECT COUNT(*) FROM tasks WHERE scheduledStart IS NOT NULL AND id != :ignoreId " +
            "AND scheduledStart < :end " +
            "AND (scheduledStart + COALESCE(estimatedMinutes, 30) * 60000) > :start"
    )
    suspend fun countConflicts(start: Long, end: Long, ignoreId: Long): Int

    @Query("UPDATE tasks SET batchId = :batchId WHERE id IN (:ids) AND kind = 'MISC'")
    suspend fun setBatch(ids: List<Long>, batchId: Long?)

    @Query("UPDATE tasks SET isDone = 1, completedAt = :now WHERE batchId = :batchId AND isDone = 0")
    suspend fun markMembersDone(batchId: Long, now: Long)

    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)
    @Delete suspend fun delete(task: TaskEntity)
}

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries WHERE taskId = :taskId ORDER BY startTime DESC")
    fun observeForTask(taskId: Long): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries ORDER BY startTime")
    fun observeAll(): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE endTime IS NULL LIMIT 1")
    fun observeRunning(): Flow<TimeEntryEntity?>

    @Query("SELECT * FROM time_entries WHERE endTime IS NULL LIMIT 1")
    suspend fun getRunning(): TimeEntryEntity?

    @Query("SELECT * FROM time_entries WHERE taskId = :taskId ORDER BY startTime")
    suspend fun getForTask(taskId: Long): List<TimeEntryEntity>

    @Query("UPDATE time_entries SET endTime = :now WHERE endTime IS NULL")
    suspend fun stopRunning(now: Long)

    @Insert suspend fun insert(entry: TimeEntryEntity): Long
    @Update suspend fun update(entry: TimeEntryEntity)
    @Delete suspend fun delete(entry: TimeEntryEntity)
}

@Dao
interface DailyReviewDao {
    @Query("SELECT * FROM daily_reviews WHERE epochDay = :epochDay")
    fun observe(epochDay: Long): Flow<DailyReviewEntity?>

    @Query("SELECT * FROM daily_reviews WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DailyReviewEntity?

    @Query("SELECT * FROM daily_reviews ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<DailyReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: DailyReviewEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("UPDATE tasks SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearTasksCategory(categoryId: Long)
}
