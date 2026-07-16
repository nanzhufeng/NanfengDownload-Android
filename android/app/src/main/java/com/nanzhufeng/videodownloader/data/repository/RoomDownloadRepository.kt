package com.nanzhufeng.videodownloader.data.repository

import androidx.room.withTransaction
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.model.TaskTransitionPolicy
import com.nanzhufeng.videodownloader.core.model.isTerminal
import com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskWithMedia
import com.nanzhufeng.videodownloader.data.database.entity.MediaItemEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDownloadRepository(
    private val database: NanzhufengDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : DownloadRepository {
    private val taskDao = database.downloadTaskDao()
    private val historyDao = database.downloadHistoryDao()

    override val activeTasks: Flow<List<QueuedDownload>> =
        taskDao.observeActive().map { rows -> rows.map(DownloadTaskWithMedia::toDomain) }

    override val history: Flow<List<DownloadHistory>> =
        historyDao.observeAll().map { rows -> rows.map(DownloadHistoryEntity::toDomain) }

    override suspend fun enqueue(
        items: List<MediaItem>,
        resolution: ResolutionPreset,
    ): List<String> = database.withTransaction {
        if (items.isEmpty()) return@withTransaction emptyList()

        val now = clock()
        val firstSortOrder = taskDao.nextSortOrder()
        val normalized = items.map(MediaItem::normalized)
        val tasks = normalized.mapIndexed { index, media ->
            DownloadTaskEntity(
                taskId = idFactory(),
                mediaKey = media.mediaKey,
                selected = true,
                sortOrder = firstSortOrder + index,
                resolution = resolution.name,
                saveTreeUri = null,
                tempPath = null,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBytesPerSecond = 0L,
                remainingSeconds = null,
                status = DownloadTaskStatus.WAITING.name,
                failureType = null,
                errorSummary = null,
                retryCount = 0,
                createdAt = now,
                updatedAt = now,
            )
        }

        database.mediaItemDao().upsertAll(normalized.map { it.toEntity(now) })
        taskDao.upsertAll(tasks)
        tasks.map(DownloadTaskEntity::taskId)
    }

    override suspend fun setSelected(taskId: String, selected: Boolean) {
        check(taskDao.updateSelection(taskId, selected, clock()) == 1) {
            "找不到下载任务：$taskId"
        }
    }

    override suspend fun transition(taskId: String, to: DownloadTaskStatus) {
        database.withTransaction {
            val task = requireNotNull(taskDao.getById(taskId)) { "找不到下载任务：$taskId" }
            val from = DownloadTaskStatus.valueOf(task.status)
            TaskTransitionPolicy.requireTransition(from, to)
            check(taskDao.updateStatus(taskId, to.name, clock()) == 1) {
                "更新下载任务失败：$taskId"
            }
        }
    }

    override suspend fun archiveTerminal(history: DownloadHistory) {
        require(history.finalStatus.isTerminal) { "只有终态任务可以归档" }
        historyDao.upsert(history.toEntity())
    }
}

private fun MediaItem.normalized(): MediaItem = copy(
    mediaKey = "${platform.name}:$contentId",
)

private fun MediaItem.toEntity(discoveredAt: Long) = MediaItemEntity(
    mediaKey = mediaKey,
    platform = platform.name,
    contentId = contentId,
    originalUrl = originalUrl,
    sourceKind = sourceKind.name,
    title = title,
    creator = creator,
    creatorId = creatorId,
    publishDate = publishDate,
    thumbnailUrl = thumbnailUrl,
    discoveredAt = discoveredAt,
)

private fun DownloadTaskWithMedia.toDomain() = QueuedDownload(
    task = DownloadTask(
        taskId = task.taskId,
        mediaKey = task.mediaKey,
        selected = task.selected,
        sortOrder = task.sortOrder,
        resolution = ResolutionPreset.valueOf(task.resolution),
        saveTreeUri = task.saveTreeUri,
        downloadedBytes = task.downloadedBytes,
        totalBytes = task.totalBytes,
        status = DownloadTaskStatus.valueOf(task.status),
        failureType = task.failureType,
        errorSummary = task.errorSummary,
        retryCount = task.retryCount,
        updatedAt = task.updatedAt,
    ),
    media = MediaItem(
        mediaKey = media.mediaKey,
        platform = DownloadPlatform.valueOf(media.platform),
        contentId = media.contentId,
        originalUrl = media.originalUrl,
        sourceKind = DownloadSourceKind.valueOf(media.sourceKind),
        title = media.title,
        creator = media.creator,
        creatorId = media.creatorId,
        publishDate = media.publishDate,
        thumbnailUrl = media.thumbnailUrl,
    ),
)

private fun DownloadHistory.toEntity() = DownloadHistoryEntity(
    taskId = taskId,
    platform = platform.name,
    contentId = contentId,
    originalUrl = originalUrl,
    title = title,
    creator = creator,
    resolution = resolution.name,
    finalStatus = finalStatus.name,
    outputUri = outputUri,
    fileSize = fileSize,
    fileExists = fileExists,
    completedAt = completedAt,
)

private fun DownloadHistoryEntity.toDomain() = DownloadHistory(
    taskId = taskId,
    platform = DownloadPlatform.valueOf(platform),
    contentId = contentId,
    originalUrl = originalUrl,
    title = title,
    creator = creator,
    resolution = ResolutionPreset.valueOf(resolution),
    finalStatus = DownloadTaskStatus.valueOf(finalStatus),
    outputUri = outputUri,
    fileSize = fileSize,
    fileExists = fileExists,
    completedAt = completedAt,
)
