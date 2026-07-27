package com.nanzhufeng.videodownloader.data.repository

import androidx.room.withTransaction
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.TransferReportOutcome
import com.nanzhufeng.videodownloader.core.model.DownloadFailureType
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.model.TaskTransitionPolicy
import com.nanzhufeng.videodownloader.data.settings.FileNameRule
import com.nanzhufeng.videodownloader.core.model.isTerminal
import com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryWithThumbnail
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskWithMedia
import com.nanzhufeng.videodownloader.data.database.entity.MediaItemEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadThroughputReportEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

class RoomDownloadRepository(
    private val database: NanzhufengDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : DownloadRepository {
    private val taskDao = database.downloadTaskDao()
    private val historyDao = database.downloadHistoryDao()
    private val throughputDao = database.downloadThroughputReportDao()

    override val activeTasks: Flow<List<QueuedDownload>> =
        taskDao.observeActive().map { rows -> rows.map(DownloadTaskWithMedia::toDomain) }

    override val history: Flow<List<DownloadHistory>> =
        historyDao.observeAll().map { rows -> rows.map(DownloadHistoryWithThumbnail::toDomain) }

    override val throughputReports: Flow<List<DownloadThroughputReport>> =
        throughputDao.observeAll().map { rows -> rows.map(DownloadThroughputReportEntity::toDomain) }

    override suspend fun enqueue(
        items: List<MediaItem>,
        resolution: ResolutionPreset,
    ): List<String> = enqueue(items, resolution, null, FileNameRule.DATE_AND_TITLE)

    override suspend fun enqueue(
        items: List<MediaItem>,
        resolution: ResolutionPreset,
        saveTreeUri: String?,
        fileNameRule: FileNameRule,
    ): List<String> = database.withTransaction {
        if (items.isEmpty()) return@withTransaction emptyList()

        val existingMediaKeys = buildSet {
            addAll(taskDao.getDownloadListMediaKeys())
            addAll(historyDao.getCompletedMediaKeys())
        }
        val normalized = items
            .map(MediaItem::normalized)
            .distinctBy(MediaItem::mediaKey)
            .filterNot { it.mediaKey in existingMediaKeys }
        if (normalized.isEmpty()) return@withTransaction emptyList()

        val now = clock()
        val firstSortOrder = taskDao.nextSortOrder()
        val tasks = normalized.mapIndexed { index, media ->
            DownloadTaskEntity(
                taskId = idFactory(),
                mediaKey = media.mediaKey,
                selected = true,
                sortOrder = firstSortOrder + index,
                resolution = resolution.name,
                fileNameRule = fileNameRule.name,
                saveTreeUri = saveTreeUri,
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

    override suspend fun bulkSelect(taskIds: List<String>, selected: Boolean) {
        if (taskIds.isEmpty()) return
        val updated = taskDao.updateSelections(taskIds.distinct(), selected, clock())
        check(updated == taskIds.distinct().size) {
            "批量更新下载选择状态失败"
        }
    }

    override suspend fun setResolution(taskId: String, resolution: ResolutionPreset) {
        val now = clock()
        check(taskDao.updateResolution(taskId, resolution.name, now) == 1) {
            "找不到下载任务：$taskId"
        }
    }

    override suspend fun setAudioSegmentCount(taskId: String, segmentCount: Int) {
        require(segmentCount in 1..MAX_AUDIO_SEGMENTS) {
            "媒体分段数量必须在 1 到 $MAX_AUDIO_SEGMENTS 之间"
        }
        val task = requireNotNull(taskDao.getById(taskId)) { "找不到下载任务：$taskId" }
        require(DownloadTaskStatus.valueOf(task.status) == DownloadTaskStatus.WAITING) {
            "只有等待中的任务可以修改分段数量"
        }
        check(taskDao.updateAudioSegmentCount(taskId, segmentCount, clock()) == 1) {
            "更新媒体分段数量失败：$taskId"
        }
    }

    override suspend fun nextSelectedWaiting(): QueuedDownload? =
        taskDao.nextSelectedWaiting()?.toDomain()

    override suspend fun nextSelectedRunnable(): QueuedDownload? =
        taskDao.nextSelectedRunnable()?.toDomain()

    override suspend fun pauseRunnableTasks(): Int = taskDao.pauseRunnableTasks(clock())

    override suspend fun resumePausedTasks(): Int = taskDao.resumePausedTasks(clock())

    override suspend fun cancelTask(taskId: String): Boolean = database.withTransaction {
        val queued = taskDao.getWithMediaById(taskId)?.toDomain() ?: return@withTransaction false
        if (taskDao.cancelTask(taskId, clock()) != 1) return@withTransaction false
        historyDao.upsert(
            queued.toHistory(
                finalStatus = DownloadTaskStatus.CANCELLED,
                completedAt = clock(),
            ).toEntity(),
        )
        true
    }

    override suspend fun removeQueueTask(taskId: String): Boolean = database.withTransaction {
        if (taskDao.deleteRemovableById(taskId) != 1) return@withTransaction false
        throughputDao.deleteByTaskId(taskId)
        true
    }

    override suspend fun retryHistory(taskId: String): Boolean = database.withTransaction {
        val history = historyDao.getById(taskId) ?: return@withTransaction false
        val finalStatus = DownloadTaskStatus.valueOf(history.finalStatus)
        if (finalStatus !in setOf(DownloadTaskStatus.FAILED, DownloadTaskStatus.CANCELLED)) {
            return@withTransaction false
        }
        if (taskDao.resetTerminalForRetry(taskId, clock()) != 1) return@withTransaction false
        historyDao.deleteById(taskId)
        true
    }

    override suspend fun deleteHistoryRecord(taskId: String): Boolean =
        historyDao.deleteById(taskId) == 1

    override suspend fun deleteHistoryRecords(taskIds: List<String>): Int {
        val uniqueIds = taskIds.distinct()
        if (uniqueIds.isEmpty()) return 0
        return historyDao.deleteByIds(uniqueIds)
    }

    override suspend fun recoverInterruptedTasks(): Int = taskDao.recoverQueueAfterProcessDeath(clock())

    override suspend fun updateTransfer(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        remainingSeconds: Long?,
    ) {
        check(
            taskDao.updateTransfer(
                taskId = taskId,
                downloadedBytes = downloadedBytes.coerceAtLeast(0L),
                totalBytes = totalBytes.coerceAtLeast(0L),
                speedBytesPerSecond = speedBytesPerSecond.coerceAtLeast(0L),
                remainingSeconds = remainingSeconds?.coerceAtLeast(0L),
                updatedAt = clock(),
            ) == 1,
        ) { "找不到下载任务：$taskId" }
    }

    override suspend fun updateConnectionMode(
        taskId: String,
        mode: DownloadConnectionMode,
        connectionCount: Int,
    ) {
        check(
            taskDao.updateConnectionMode(
                taskId = taskId,
                connectionMode = mode.name,
                connectionCount = connectionCount.coerceAtLeast(0),
                updatedAt = clock(),
            ) == 1,
        ) { "找不到下载任务：$taskId" }
    }

    override suspend fun updateProcessing(
        taskId: String,
        stage: DownloadProcessingStage,
        progressPercent: Int,
    ) {
        check(
            taskDao.updateProcessing(
                taskId = taskId,
                processingStage = stage.name,
                processingProgressPercent = progressPercent.coerceIn(0, 100),
                updatedAt = clock(),
            ) == 1,
        ) { "找不到下载任务：$taskId" }
    }

    override suspend fun recordThroughputReport(report: DownloadThroughputReport) {
        throughputDao.upsert(report.toEntity())
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

    override suspend fun transitionWithProblem(
        taskId: String,
        to: DownloadTaskStatus,
        failureType: DownloadFailureType,
        errorSummary: String,
    ) {
        database.withTransaction {
            val task = requireNotNull(taskDao.getById(taskId)) { "找不到下载任务：$taskId" }
            val from = DownloadTaskStatus.valueOf(task.status)
            TaskTransitionPolicy.requireTransition(from, to)
            check(
                taskDao.updateStatusWithProblem(
                    taskId = taskId,
                    status = to.name,
                    failureType = failureType.name,
                    errorSummary = errorSummary,
                    retryIncrement = if (to == DownloadTaskStatus.WAITING_NETWORK) 1 else 0,
                    updatedAt = clock(),
                ) == 1,
            ) { "更新下载任务失败：$taskId" }
        }
    }

    override suspend fun archiveTerminal(history: DownloadHistory) {
        require(history.finalStatus.isTerminal) { "只有终态任务可以归档" }
        historyDao.upsert(history.toEntity())
    }

    override suspend fun findCompleted(
        platform: DownloadPlatform,
        contentId: String,
        resolution: ResolutionPreset,
        audioSegmentCount: Int,
    ): DownloadHistory? = historyDao.findCompleted(
        platform = platform.name,
        contentId = contentId,
        resolution = resolution.name,
        audioSegmentCount = audioSegmentCount.coerceIn(1, MAX_AUDIO_SEGMENTS),
    )?.toDomain()

    private companion object {
        const val MAX_AUDIO_SEGMENTS = 20
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
        fileNameRule = runCatching { FileNameRule.valueOf(task.fileNameRule) }
            .getOrDefault(FileNameRule.DATE_AND_TITLE),
        saveTreeUri = task.saveTreeUri,
        downloadedBytes = task.downloadedBytes,
        totalBytes = task.totalBytes,
        speedBytesPerSecond = task.speedBytesPerSecond,
        remainingSeconds = task.remainingSeconds,
        status = DownloadTaskStatus.valueOf(task.status),
        failureType = task.failureType?.let(DownloadFailureType::valueOf),
        errorSummary = task.errorSummary,
        retryCount = task.retryCount,
        updatedAt = task.updatedAt,
        connectionMode = runCatching { DownloadConnectionMode.valueOf(task.connectionMode) }
            .getOrDefault(DownloadConnectionMode.UNKNOWN),
        connectionCount = task.connectionCount,
        processingStage = runCatching {
            DownloadProcessingStage.valueOf(task.processingStage)
        }.getOrDefault(DownloadProcessingStage.NONE),
        processingProgressPercent = task.processingProgressPercent.coerceIn(0, 100),
        audioSegmentCount = task.audioSegmentCount.coerceIn(1, 20),
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
    failureType = failureType?.name,
    errorSummary = errorSummary,
    outputUrisJson = JSONArray(outputUris).toString(),
    audioSegmentCount = audioSegmentCount.coerceIn(1, 20),
)

private fun QueuedDownload.toHistory(
    finalStatus: DownloadTaskStatus,
    completedAt: Long,
) = DownloadHistory(
    taskId = task.taskId,
    platform = media.platform,
    contentId = media.contentId,
    originalUrl = media.originalUrl,
    title = media.title,
    creator = media.creator,
    resolution = task.resolution,
    finalStatus = finalStatus,
    outputUri = null,
    fileSize = 0L,
    fileExists = false,
    completedAt = completedAt,
    audioSegmentCount = task.audioSegmentCount.coerceIn(1, 20),
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
    failureType = failureType?.let(DownloadFailureType::valueOf),
    errorSummary = errorSummary,
    outputUris = decodeOutputUris(outputUrisJson, outputUri),
    audioSegmentCount = audioSegmentCount.coerceIn(1, 20),
)

private fun DownloadHistoryWithThumbnail.toDomain() = history.toDomain().copy(
    thumbnailUrl = thumbnailUrl.orEmpty(),
)

private fun DownloadThroughputReport.toEntity() = DownloadThroughputReportEntity(
    reportId = reportId,
    taskId = taskId,
    platform = platform.name,
    streamLabel = streamLabel,
    outcome = outcome.name,
    connectionMode = connectionMode.name,
    connectionCount = connectionCount,
    rangeSupported = rangeSupported,
    expectedBytes = expectedBytes,
    committedBytes = committedBytes,
    networkBytes = networkBytes,
    startedAt = startedAt,
    finishedAt = finishedAt,
    elapsedMillis = elapsedMillis,
    averageBytesPerSecond = averageBytesPerSecond,
    peakBytesPerSecond = peakBytesPerSecond,
    retryCount = retryCount,
    reprobeCount = reprobeCount,
    fallbackReason = fallbackReason,
    errorSummary = errorSummary,
)

private fun DownloadThroughputReportEntity.toDomain() = DownloadThroughputReport(
    reportId = reportId,
    taskId = taskId,
    platform = DownloadPlatform.valueOf(platform),
    streamLabel = streamLabel,
    outcome = TransferReportOutcome.valueOf(outcome),
    connectionMode = DownloadConnectionMode.valueOf(connectionMode),
    connectionCount = connectionCount,
    rangeSupported = rangeSupported,
    expectedBytes = expectedBytes,
    committedBytes = committedBytes,
    networkBytes = networkBytes,
    startedAt = startedAt,
    finishedAt = finishedAt,
    elapsedMillis = elapsedMillis,
    averageBytesPerSecond = averageBytesPerSecond,
    peakBytesPerSecond = peakBytesPerSecond,
    retryCount = retryCount,
    reprobeCount = reprobeCount,
    fallbackReason = fallbackReason,
    errorSummary = errorSummary,
)

private fun decodeOutputUris(value: String, fallback: String?): List<String> {
    val decoded = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
    return decoded.ifEmpty { fallback?.let(::listOf).orEmpty() }
}
