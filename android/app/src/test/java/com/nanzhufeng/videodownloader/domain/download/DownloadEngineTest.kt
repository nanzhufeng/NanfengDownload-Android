package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadEngineTest {
    @Test
    fun startResumesPausedTasksAndSchedulesSingleWorker() = runBlocking {
        val repository = EngineRepository()
        val scheduler = RecordingScheduler()
        val engine = DefaultDownloadEngine(repository, scheduler)

        engine.start()

        assertEquals(1, repository.resumeCalls)
        assertEquals(1, scheduler.enqueueCalls)
    }

    @Test
    fun pauseAllCancelsWorkerAndPersistsPausedState() = runBlocking {
        val repository = EngineRepository()
        val scheduler = RecordingScheduler()
        val engine = DefaultDownloadEngine(repository, scheduler)

        engine.pauseAll()

        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, repository.pauseCalls)
    }

    @Test
    fun stopCancelsRequestedTaskAndContinuesRemainingQueue() = runBlocking {
        val repository = EngineRepository()
        val scheduler = RecordingScheduler()
        val engine = DefaultDownloadEngine(repository, scheduler)

        engine.stop("task-one")

        assertTrue(repository.cancelledIds.contains("task-one"))
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, scheduler.enqueueCalls)
    }

    @Test
    fun removeDeletesWaitingTaskWithoutRestartingWorker() = runBlocking {
        val repository = EngineRepository().apply { removable = true }
        val scheduler = RecordingScheduler()
        val engine = DefaultDownloadEngine(repository, scheduler)

        engine.remove("task-one")

        assertEquals(listOf("task-one"), repository.removedIds)
        assertTrue(repository.cancelledIds.isEmpty())
        assertEquals(0, scheduler.cancelCalls)
        assertEquals(0, scheduler.enqueueCalls)
    }

    @Test
    fun removeStopsActiveTaskThenDeletesItAndContinuesQueue() = runBlocking {
        val repository = EngineRepository()
        val scheduler = RecordingScheduler()
        val engine = DefaultDownloadEngine(repository, scheduler)

        engine.remove("task-one")

        assertEquals(listOf("task-one"), repository.cancelledIds)
        assertEquals(listOf("task-one", "task-one"), repository.removedIds)
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, scheduler.enqueueCalls)
    }

    @Test
    fun retryKeepsSameFailedTaskAndSchedulesItAgain() = runBlocking {
        val repository = EngineRepository().apply { retryable = true }
        val scheduler = RecordingScheduler()
        val engine = DefaultDownloadEngine(repository, scheduler)

        engine.retry("task-one")

        assertEquals(listOf("task-one"), repository.retriedIds)
        assertEquals(1, scheduler.enqueueCalls)
    }

    @Test
    fun recoveryReplacesBackedOffWorkerInsteadOfWaitingForItsOldTimer() = runBlocking {
        val scheduler = RecordingScheduler()
        val engine = DefaultDownloadEngine(EngineRepository(), scheduler)

        engine.resumeWhenNetworkAvailable()

        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, scheduler.enqueueCalls)
    }
}

private class RecordingScheduler : DownloadWorkScheduler {
    var enqueueCalls = 0
    var cancelCalls = 0

    override fun enqueue() {
        enqueueCalls += 1
    }

    override fun cancel() {
        cancelCalls += 1
    }
}

private class EngineRepository : DownloadRepository {
    override val activeTasks: Flow<List<QueuedDownload>> = MutableStateFlow(emptyList())
    override val history: Flow<List<DownloadHistory>> = MutableStateFlow(emptyList())
    var resumeCalls = 0
    var pauseCalls = 0
    val cancelledIds = mutableListOf<String>()
    val removedIds = mutableListOf<String>()
    var removable = false
    var retryable = false
    val retriedIds = mutableListOf<String>()

    override suspend fun enqueue(items: List<MediaItem>, resolution: ResolutionPreset) = emptyList<String>()
    override suspend fun setSelected(taskId: String, selected: Boolean) = Unit
    override suspend fun bulkSelect(taskIds: List<String>, selected: Boolean) = Unit
    override suspend fun setResolution(taskId: String, resolution: ResolutionPreset) = Unit
    override suspend fun nextSelectedWaiting(): QueuedDownload? = null
    override suspend fun pauseRunnableTasks(): Int {
        pauseCalls += 1
        return 1
    }

    override suspend fun resumePausedTasks(): Int {
        resumeCalls += 1
        return 1
    }

    override suspend fun cancelTask(taskId: String): Boolean {
        cancelledIds += taskId
        removable = true
        return true
    }

    override suspend fun removeQueueTask(taskId: String): Boolean {
        removedIds += taskId
        return removable
    }

    override suspend fun retryHistory(taskId: String): Boolean {
        retriedIds += taskId
        return retryable
    }

    override suspend fun updateTransfer(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        remainingSeconds: Long?,
    ) = Unit

    override suspend fun transition(taskId: String, to: DownloadTaskStatus) = Unit
    override suspend fun archiveTerminal(history: DownloadHistory) = Unit
    override suspend fun findCompleted(
        platform: DownloadPlatform,
        contentId: String,
        resolution: ResolutionPreset,
    ): DownloadHistory? = null
}
