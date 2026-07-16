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
        WHERE status NOT IN ('COMPLETED', 'FAILED', 'SKIPPED', 'CANCELLED')
        ORDER BY sortOrder
        """,
    )
    fun observeActive(): Flow<List<DownloadTaskWithMedia>>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    suspend fun getById(taskId: String): DownloadTaskEntity?

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
        SET resolution = :resolution, updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateResolution(taskId: String, resolution: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = :status, updatedAt = :updatedAt
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateStatus(taskId: String, status: String, updatedAt: Long): Int
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
