package com.nanzhufeng.videodownloader.data.repository

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.DownloadFailureType
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.settings.FileNameRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface DownloadRepository {
    val activeTasks: Flow<List<QueuedDownload>>
    val history: Flow<List<DownloadHistory>>
    val throughputReports: Flow<List<DownloadThroughputReport>>
        get() = emptyFlow()

    suspend fun enqueue(
        items: List<MediaItem>,
        resolution: ResolutionPreset,
    ): List<String>

    suspend fun enqueue(
        items: List<MediaItem>,
        resolution: ResolutionPreset,
        saveTreeUri: String?,
        fileNameRule: FileNameRule,
    ): List<String> = enqueue(items, resolution)

    suspend fun setSelected(taskId: String, selected: Boolean)

    suspend fun bulkSelect(taskIds: List<String>, selected: Boolean)

    suspend fun setResolution(taskId: String, resolution: ResolutionPreset)

    suspend fun setAudioSegmentCount(taskId: String, segmentCount: Int) = Unit

    suspend fun nextSelectedWaiting(): QueuedDownload?

    suspend fun nextSelectedRunnable(): QueuedDownload? = nextSelectedWaiting()

    suspend fun pauseRunnableTasks(): Int = 0

    suspend fun resumePausedTasks(): Int = 0

    suspend fun cancelTask(taskId: String): Boolean = false

    suspend fun removeQueueTask(taskId: String): Boolean = false

    suspend fun retryHistory(taskId: String): Boolean = false

    suspend fun deleteHistoryRecord(taskId: String): Boolean = false

    suspend fun recoverInterruptedTasks(): Int = 0

    suspend fun updateTransfer(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        remainingSeconds: Long?,
    )

    suspend fun updateConnectionMode(
        taskId: String,
        mode: DownloadConnectionMode,
        connectionCount: Int,
    ) = Unit

    suspend fun updateProcessing(
        taskId: String,
        stage: DownloadProcessingStage,
        progressPercent: Int,
    ) = Unit

    suspend fun recordThroughputReport(report: DownloadThroughputReport) = Unit

    suspend fun transition(taskId: String, to: DownloadTaskStatus)

    suspend fun transitionWithProblem(
        taskId: String,
        to: DownloadTaskStatus,
        failureType: DownloadFailureType,
        errorSummary: String,
    ) {
        transition(taskId, to)
    }

    suspend fun archiveTerminal(history: DownloadHistory)

    suspend fun findCompleted(
        platform: DownloadPlatform,
        contentId: String,
        resolution: ResolutionPreset,
        audioSegmentCount: Int = 1,
    ): DownloadHistory?
}
