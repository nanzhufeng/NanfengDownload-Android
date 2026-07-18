package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadFailureType
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import java.io.File
import java.net.UnknownHostException
import com.nanzhufeng.videodownloader.probe.HttpDownloadException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskRunnerTest {
    @Test
    fun verifiedExistingMediaIsSkippedBeforeResolvingNetworkSource() = runBlocking {
        val repository = RunnerRepository(queued())
        val resolver = RecordingResolver()
        val runner = DownloadTaskRunner(
            repository = repository,
            resolver = resolver,
            transfer = FailingTransfer(),
            outputStore = ExistingOutputStore(),
            clock = { 200L },
        )

        val result = runner.runNext()

        assertEquals(TaskRunResult.Skipped, result)
        assertEquals(0, resolver.calls)
        assertEquals(DownloadTaskStatus.SKIPPED, repository.current.task.status)
        assertEquals(DownloadTaskStatus.SKIPPED, repository.archived.single().finalStatus)
    }

    @Test
    fun pythonNetworkErrorsAreRetryableButHttp403IsNot() {
        assertTrue(
            NetworkErrorClassifier.isRetryable(
                RuntimeException("urllib.error.URLError: SSL connection closed"),
            ),
        )
        assertFalse(
            NetworkErrorClassifier.isRetryable(
                RuntimeException("ERROR: HTTP Error 403: Forbidden"),
            ),
        )
        assertTrue(
            "长视频连接被服务器提前截断时，应刷新来源并继续下载",
            NetworkErrorClassifier.isRetryable(
                java.io.IOException("unexpected end of stream"),
            ),
        )
        assertFalse(
            "内部重试和来源刷新全部耗尽后，应保留为可见失败而不是无限等网络",
            NetworkErrorClassifier.shouldWaitForNetwork(
                java.io.IOException("unexpected end of stream"),
            ),
        )
    }

    @Test
    fun nonNetworkFailurePersistsProblemOnTaskAndHistory() = runBlocking {
        val repository = RunnerRepository(queued())
        val runner = DownloadTaskRunner(
            repository = repository,
            resolver = ThrowingResolver(IllegalStateException("没有可下载视频流")),
            transfer = FailingTransfer(),
            outputStore = EmptyOutputStore(),
            clock = { 200L },
        )

        val result = runner.runNext()

        assertEquals(TaskRunResult.Failed, result)
        assertEquals(DownloadTaskStatus.FAILED, repository.current.task.status)
        assertEquals(DownloadFailureType.SOURCE, repository.current.task.failureType)
        assertEquals("没有可下载视频流", repository.current.task.errorSummary)
        assertEquals(DownloadFailureType.SOURCE, repository.archived.single().failureType)
        assertEquals("没有可下载视频流", repository.archived.single().errorSummary)
    }

    @Test
    fun networkFailureKeepsProblemAndWaitsForRetryWithoutArchiving() = runBlocking {
        val repository = RunnerRepository(queued())
        val runner = DownloadTaskRunner(
            repository = repository,
            resolver = ThrowingResolver(UnknownHostException("网络不可用")),
            transfer = FailingTransfer(),
            outputStore = EmptyOutputStore(),
        )

        val result = runner.runNext()

        assertEquals(TaskRunResult.WaitingForNetwork, result)
        assertEquals(DownloadTaskStatus.WAITING_NETWORK, repository.current.task.status)
        assertEquals(DownloadFailureType.NETWORK, repository.current.task.failureType)
        assertEquals("网络不可用", repository.current.task.errorSummary)
        assertTrue(repository.archived.isEmpty())
    }

    @Test
    fun staleMediaUrlTriggersOneFreshResolveBeforeCompleting() = runBlocking {
        val repository = RunnerRepository(queued())
        val resolver = SuccessfulResolver()
        val transfer = RefreshThenSuccessTransfer()
        val runner = DownloadTaskRunner(
            repository = repository,
            resolver = resolver,
            transfer = transfer,
            outputStore = PublishingOutputStore(),
            clock = { 300L },
        )

        val result = runner.runNext()

        assertEquals(TaskRunResult.Completed, result)
        assertEquals(2, resolver.calls)
        assertEquals(listOf(0, 1), transfer.reprobeCounts)
        assertEquals(DownloadTaskStatus.COMPLETED, repository.archived.single().finalStatus)
    }

    private fun queued() = QueuedDownload(
        task = DownloadTask(
            taskId = "task-one",
            mediaKey = "YOUTUBE:one",
            selected = true,
            sortOrder = 1,
            resolution = ResolutionPreset.UP_TO_720P,
            saveTreeUri = null,
            downloadedBytes = 0,
            totalBytes = 0,
            speedBytesPerSecond = 0,
            remainingSeconds = null,
            status = DownloadTaskStatus.WAITING,
            failureType = null,
            errorSummary = null,
            retryCount = 0,
            updatedAt = 100,
        ),
        media = MediaItem(
            mediaKey = "YOUTUBE:one",
            platform = DownloadPlatform.YOUTUBE,
            contentId = "one",
            originalUrl = "https://youtu.be/one",
            sourceKind = DownloadSourceKind.SINGLE_VIDEO,
            title = "标题",
            creator = "作者",
            creatorId = "creator",
            publishDate = "20260716",
            thumbnailUrl = "",
        ),
    )
}

private class RunnerRepository(initial: QueuedDownload) : DownloadRepository {
    var current = initial
    val archived = mutableListOf<DownloadHistory>()
    override val activeTasks: Flow<List<QueuedDownload>> = MutableStateFlow(listOf(initial))
    override val history: Flow<List<DownloadHistory>> = MutableStateFlow(emptyList())

    override suspend fun enqueue(items: List<MediaItem>, resolution: ResolutionPreset) = emptyList<String>()
    override suspend fun setSelected(taskId: String, selected: Boolean) = Unit
    override suspend fun bulkSelect(taskIds: List<String>, selected: Boolean) = Unit
    override suspend fun setResolution(taskId: String, resolution: ResolutionPreset) = Unit
    override suspend fun nextSelectedWaiting(): QueuedDownload? =
        current.takeIf { it.task.selected && it.task.status == DownloadTaskStatus.WAITING }

    override suspend fun updateTransfer(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        remainingSeconds: Long?,
    ) = Unit

    override suspend fun transition(taskId: String, to: DownloadTaskStatus) {
        current = current.copy(task = current.task.copy(status = to))
    }

    override suspend fun transitionWithProblem(
        taskId: String,
        to: DownloadTaskStatus,
        failureType: DownloadFailureType,
        errorSummary: String,
    ) {
        current = current.copy(
            task = current.task.copy(
                status = to,
                failureType = failureType,
                errorSummary = errorSummary,
            ),
        )
    }

    override suspend fun archiveTerminal(history: DownloadHistory) {
        archived += history
    }

    override suspend fun findCompleted(
        platform: DownloadPlatform,
        contentId: String,
        resolution: ResolutionPreset,
    ): DownloadHistory? = null
}

private class RecordingResolver : TaskMediaResolver {
    var calls = 0

    override suspend fun resolve(media: MediaItem, resolution: ResolutionPreset): ResolvedMedia {
        calls += 1
        error("不应解析网络源")
    }
}

private class ThrowingResolver(private val error: Throwable) : TaskMediaResolver {
    override suspend fun resolve(media: MediaItem, resolution: ResolutionPreset): ResolvedMedia {
        throw error
    }
}

private class SuccessfulResolver : TaskMediaResolver {
    var calls = 0

    override suspend fun resolve(media: MediaItem, resolution: ResolutionPreset): ResolvedMedia {
        calls += 1
        return ResolvedMedia(
            videoUrl = "https://example.invalid/video-$calls",
            audioUrl = null,
            videoExtension = "mp4",
            audioExtension = null,
            headers = emptyMap(),
        )
    }
}

private class RefreshThenSuccessTransfer : MediaTransfer {
    val reprobeCounts = mutableListOf<Int>()

    override suspend fun download(
        task: QueuedDownload,
        source: ResolvedMedia,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ): PreparedMedia {
        reprobeCounts += source.reprobeCount
        if (source.reprobeCount == 0) throw HttpDownloadException(403)
        return PreparedMedia(File.createTempFile("refreshed-source", ".mp4"), "video/mp4")
    }
}

private class FailingTransfer : MediaTransfer {
    override suspend fun download(
        task: QueuedDownload,
        source: ResolvedMedia,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ): PreparedMedia = error("不应下载")
}

private class ExistingOutputStore : DownloadOutputStore {
    override suspend fun findExisting(
        media: MediaItem,
        resolution: ResolutionPreset,
    ): StoredMedia = StoredMedia("content://media/existing", 128_000L)

    override suspend fun uriExists(uri: String): Boolean = true

    override suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
    ): StoredMedia = error("不应写入")
}

private class EmptyOutputStore : DownloadOutputStore {
    override suspend fun findExisting(
        media: MediaItem,
        resolution: ResolutionPreset,
    ): StoredMedia? = null

    override suspend fun uriExists(uri: String): Boolean = false

    override suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
    ): StoredMedia = error("不应写入")
}

private class PublishingOutputStore : DownloadOutputStore {
    override suspend fun findExisting(media: MediaItem, resolution: ResolutionPreset): StoredMedia? = null

    override suspend fun uriExists(uri: String): Boolean = false

    override suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
    ): StoredMedia = StoredMedia("content://media/refreshed", prepared.file.length())
}
