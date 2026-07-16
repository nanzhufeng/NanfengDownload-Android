package com.nanzhufeng.videodownloader.data.repository

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
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

    suspend fun transition(taskId: String, to: DownloadTaskStatus)

    suspend fun archiveTerminal(history: DownloadHistory)
}
