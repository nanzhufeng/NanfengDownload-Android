package com.nanzhufeng.videodownloader.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryWithThumbnail
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskWithMedia
import com.nanzhufeng.videodownloader.data.database.entity.DownloadThroughputReportEntity
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
        WHERE status NOT IN ('COMPLETED', 'CANCELLED')
        ORDER BY sortOrder
        """,
    )
    fun observeActive(): Flow<List<DownloadTaskWithMedia>>

    @Query(
        """
        SELECT mediaKey FROM download_tasks
        WHERE status NOT IN ('COMPLETED', 'CANCELLED')
        """,
    )
    suspend fun getDownloadListMediaKeys(): List<String>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    suspend fun getById(taskId: String): DownloadTaskEntity?

    @Transaction
    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    suspend fun getWithMediaById(taskId: String): DownloadTaskWithMedia?

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
        SET audioSegmentCount = :segmentCount, updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateAudioSegmentCount(taskId: String, segmentCount: Int, updatedAt: Long): Int

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
        SET connectionMode = :connectionMode,
            connectionCount = :connectionCount,
            updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateConnectionMode(
        taskId: String,
        connectionMode: String,
        connectionCount: Int,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET processingStage = :processingStage,
            processingProgressPercent = :processingProgressPercent,
            updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateProcessing(
        taskId: String,
        processingStage: String,
        processingProgressPercent: Int,
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
        DELETE FROM download_tasks
        WHERE taskId = :taskId
          AND status NOT IN ('PARSING', 'DOWNLOADING', 'VALIDATING')
        """,
    )
    suspend fun deleteRemovableById(taskId: String): Int

    @Query(
        """
        UPDATE download_tasks
        SET selected = 1,
            downloadedBytes = 0,
            totalBytes = 0,
            speedBytesPerSecond = 0,
            remainingSeconds = NULL,
            connectionMode = 'UNKNOWN',
            connectionCount = 0,
            processingStage = 'NONE',
            processingProgressPercent = 0,
            status = 'WAITING',
            failureType = NULL,
            errorSummary = NULL,
            retryCount = retryCount + 1,
            updatedAt = :updatedAt
        WHERE taskId = :taskId
          AND status IN ('FAILED', 'CANCELLED')
        """,
    )
    suspend fun resetTerminalForRetry(taskId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'WAITING', updatedAt = :updatedAt
        WHERE status IN ('PARSING', 'DOWNLOADING', 'VALIDATING')
        """,
    )
    suspend fun recoverInterruptedTasks(updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'FAILED',
            failureType = 'TRANSFER',
            errorSummary = '已由较新的同作品任务接替，旧尝试已保留，可重试或删除',
            updatedAt = :updatedAt
        WHERE status IN ('WAITING', 'PARSING', 'DOWNLOADING', 'VALIDATING', 'WAITING_NETWORK')
          AND EXISTS (
              SELECT 1
              FROM download_tasks AS newer
              WHERE newer.mediaKey = download_tasks.mediaKey
                AND newer.sortOrder > download_tasks.sortOrder
                AND newer.status NOT IN ('COMPLETED', 'CANCELLED')
          )
        """,
    )
    suspend fun failSupersededDuplicateAttempts(updatedAt: Long): Int

    @Transaction
    suspend fun recoverQueueAfterProcessDeath(updatedAt: Long): Int {
        failSupersededDuplicateAttempts(updatedAt)
        return recoverInterruptedTasks(updatedAt)
    }
}

@Dao
interface DownloadHistoryDao {
    @Upsert
    suspend fun upsert(item: DownloadHistoryEntity)

    @Query(
        """
        SELECT history.*, media.thumbnailUrl AS mediaThumbnailUrl
        FROM download_history AS history
        LEFT JOIN media_items AS media
          ON media.platform = history.platform
         AND media.contentId = history.contentId
        ORDER BY history.completedAt DESC
        """,
    )
    fun observeAll(): Flow<List<DownloadHistoryWithThumbnail>>

    @Query(
        """
        SELECT platform || ':' || contentId FROM download_history
        WHERE finalStatus = 'COMPLETED'
        """,
    )
    suspend fun getCompletedMediaKeys(): List<String>

    @Query("SELECT * FROM download_history WHERE taskId = :taskId")
    suspend fun getById(taskId: String): DownloadHistoryEntity?

    @Query("DELETE FROM download_history WHERE taskId = :taskId")
    suspend fun deleteById(taskId: String): Int

    @Query(
        """
        SELECT * FROM download_history
        WHERE platform = :platform
          AND contentId = :contentId
          AND resolution = :resolution
          AND audioSegmentCount = :audioSegmentCount
          AND finalStatus = 'COMPLETED'
        ORDER BY completedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findCompleted(
        platform: String,
        contentId: String,
        resolution: String,
        audioSegmentCount: Int,
    ): DownloadHistoryEntity?
}

@Dao
interface DownloadThroughputReportDao {
    @Upsert
    suspend fun upsert(item: DownloadThroughputReportEntity)

    @Query("SELECT * FROM download_throughput_reports ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<DownloadThroughputReportEntity>>

    @Query("SELECT * FROM download_throughput_reports WHERE taskId = :taskId ORDER BY startedAt DESC")
    suspend fun getByTaskId(taskId: String): List<DownloadThroughputReportEntity>

    @Query("DELETE FROM download_throughput_reports WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String): Int
}
