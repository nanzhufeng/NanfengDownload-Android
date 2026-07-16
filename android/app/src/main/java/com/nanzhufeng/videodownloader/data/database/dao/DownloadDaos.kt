package com.nanzhufeng.videodownloader.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskWithMedia
import com.nanzhufeng.videodownloader.data.database.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("SELECT * FROM media_items WHERE mediaKey = :mediaKey")
    suspend fun getByKey(mediaKey: String): MediaItemEntity?
}

@Dao
interface DownloadTaskDao {
    @Upsert
    suspend fun upsertAll(tasks: List<DownloadTaskEntity>)

    @Transaction
    @Query(
        """
        SELECT * FROM download_tasks
        WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
        ORDER BY sortOrder
        """,
    )
    fun observeActive(): Flow<List<DownloadTaskWithMedia>>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    suspend fun getById(taskId: String): DownloadTaskEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM download_tasks
        WHERE selected = 1 AND status = 'WAITING'
        ORDER BY sortOrder
        LIMIT 1
        """,
    )
    suspend fun nextSelectedWaiting(): DownloadTaskWithMedia?

    @Transaction
    @Query(
        """
        SELECT * FROM download_tasks
        WHERE selected = 1 AND status IN ('WAITING', 'WAITING_NETWORK')
        ORDER BY sortOrder
        LIMIT 1
        """,
    )
    suspend fun nextSelectedRunnable(): DownloadTaskWithMedia?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM download_tasks")
    suspend fun nextSortOrder(): Long

    @Query(
        """
        UPDATE download_tasks
        SET selected = :selected, updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateSelection(taskId: String, selected: Boolean, updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET selected = :selected, updatedAt = :updatedAt
        WHERE taskId IN (:taskIds)
        """,
    )
    suspend fun updateSelections(taskIds: List<String>, selected: Boolean, updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET resolution = :resolution, updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateResolution(taskId: String, resolution: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            failureType = NULL,
            errorSummary = NULL,
            updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateStatus(taskId: String, status: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            failureType = :failureType,
            errorSummary = :errorSummary,
            retryCount = retryCount + :retryIncrement,
            updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateStatusWithProblem(
        taskId: String,
        status: String,
        failureType: String,
        errorSummary: String,
        retryIncrement: Int,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speedBytesPerSecond = :speedBytesPerSecond,
            remainingSeconds = :remainingSeconds,
            updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateTransfer(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        remainingSeconds: Long?,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PAUSED', updatedAt = :updatedAt
        WHERE status IN ('WAITING', 'PARSING', 'DOWNLOADING', 'WAITING_NETWORK')
        """,
    )
    suspend fun pauseRunnableTasks(updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'WAITING', updatedAt = :updatedAt
        WHERE status = 'PAUSED'
        """,
    )
    suspend fun resumePausedTasks(updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'CANCELLED', updatedAt = :updatedAt
        WHERE taskId = :taskId
          AND status NOT IN ('COMPLETED', 'FAILED', 'SKIPPED', 'CANCELLED')
        """,
    )
    suspend fun cancelTask(taskId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'WAITING', updatedAt = :updatedAt
        WHERE status IN ('PARSING', 'DOWNLOADING', 'VALIDATING')
        """,
    )
    suspend fun recoverInterruptedTasks(updatedAt: Long): Int
}

@Dao
interface DownloadHistoryDao {
    @Upsert
    suspend fun upsert(item: DownloadHistoryEntity)

    @Query("SELECT * FROM download_history ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<DownloadHistoryEntity>>

    @Query(
        """
        SELECT * FROM download_history
        WHERE platform = :platform
          AND contentId = :contentId
          AND resolution = :resolution
          AND finalStatus = 'COMPLETED'
        ORDER BY completedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findCompleted(
        platform: String,
        contentId: String,
        resolution: String,
    ): DownloadHistoryEntity?
}
