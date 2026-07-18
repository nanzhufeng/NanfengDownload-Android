package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.data.repository.DownloadRepository

interface DownloadEngine {
    suspend fun start()

    suspend fun pauseAll()

    suspend fun stop(taskId: String)

    suspend fun remove(taskId: String)

    suspend fun retry(taskId: String)

    suspend fun resumeWhenNetworkAvailable()
}

object NoOpDownloadEngine : DownloadEngine {
    override suspend fun start() = Unit
    override suspend fun pauseAll() = Unit
    override suspend fun stop(taskId: String) = Unit
    override suspend fun remove(taskId: String) = Unit
    override suspend fun retry(taskId: String) = Unit
    override suspend fun resumeWhenNetworkAvailable() = Unit
}

interface DownloadWorkScheduler {
    fun enqueue()

    fun cancel()

    fun restart() {
        cancel()
        enqueue()
    }
}

class DefaultDownloadEngine(
    private val repository: DownloadRepository,
    private val scheduler: DownloadWorkScheduler,
) : DownloadEngine {
    override suspend fun start() {
        repository.resumePausedTasks()
        scheduler.enqueue()
    }

    override suspend fun pauseAll() {
        scheduler.cancel()
        repository.pauseRunnableTasks()
    }

    override suspend fun stop(taskId: String) {
        repository.cancelTask(taskId)
        scheduler.restart()
    }

    override suspend fun remove(taskId: String) {
        if (repository.removeQueueTask(taskId)) return
        if (!repository.cancelTask(taskId)) return
        scheduler.restart()
        repository.removeQueueTask(taskId)
    }

    override suspend fun retry(taskId: String) {
        if (repository.retryHistory(taskId)) {
            scheduler.enqueue()
        }
    }

    override suspend fun resumeWhenNetworkAvailable() {
        scheduler.restart()
    }
}
