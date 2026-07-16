package com.nanzhufeng.videodownloader.data.repository

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    val activeTasks: Flow<List<QueuedDownload>>
    val history: Flow<List<DownloadHistory>>

    suspend fun enqueue(
        items: List<MediaItem>,
        resolution: ResolutionPreset,
    ): List<String>

    suspend fun setSelected(taskId: String, selected: Boolean)

    suspend fun bulkSelect(taskIds: List<String>, selected: Boolean)

    suspend fun setResolution(taskId: String, resolution: ResolutionPreset)

    suspend fun nextSelectedWaiting(): QueuedDownload?

    suspend fun nextSelectedRunnable(): QueuedDownload? = nextSelectedWaiting()

    suspend fun pauseRunnableTasks(): Int = 0

    suspend fun resumePausedTasks(): Int = 0

    suspend fun cancelTask(taskId: String): Boolean = false

    suspend fun recoverInterruptedTasks(): Int = 0

    suspend fun updateTransfer(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        remainingSeconds: Long?,
    )

    suspend fun transition(taskId: String, to: DownloadTaskStatus)

    suspend fun archiveTerminal(history: DownloadHistory)

    suspend fun findCompleted(
        platform: DownloadPlatform,
        contentId: String,
        resolution: ResolutionPreset,
    ): DownloadHistory?
}
