package com.nanzhufeng.videodownloader.feature.home

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.AppSettings
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.domain.discovery.CreatorIdentity
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveredMedia
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveryResult
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun smartRead_singleVideo_enqueuesExactlyOneSelectedItem() = runBlocking {
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), FakeDiscovery())

        viewModel.onInputChanged("https://youtu.be/one")
        viewModel.smartRead()

        assertEquals(1, downloads.enqueued.single().size)
        assertEquals("one", downloads.enqueued.single().single().contentId)
        assertEquals(ResolutionPreset.UP_TO_720P, downloads.resolutions.single())
        assertTrue(viewModel.uiState.value.notice.contains("已加入 1 个作品"))
    }

    @Test
    fun smartRead_collection_thenLoadMore_appendsOnlyNewPage() = runBlocking {
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), FakeDiscovery())

        viewModel.onInputChanged("https://www.tiktok.com/@creator")
        viewModel.smartRead()
        viewModel.loadMore()

        assertEquals(listOf(listOf("first"), listOf("second")), downloads.enqueued.map { batch -> batch.map(MediaItem::contentId) })
        assertTrue(viewModel.uiState.value.notice.contains("已加载更多 1 个作品"))
    }

    @Test
    fun smartRead_timeoutRestoresButtonAndShowsClearNotice() = runBlocking {
        val viewModel = HomeViewModel(
            downloads = RecordingDownloads(),
            settings = FakeSettings(),
            discovery = object : SourceDiscoveryEngine {
                override suspend fun read(input: String, page: Int): DiscoveryResult {
                    delay(1_000)
                    error("不会执行到这里")
                }
            },
            readTimeoutMillis = 20,
        )

        viewModel.onInputChanged("https://youtu.be/slow")
        viewModel.smartRead()

        assertTrue(viewModel.uiState.value.notice.contains("读取超时"))
        assertTrue(!viewModel.uiState.value.isReading)
    }

    @Test
    fun smartRead_whenRepositoryRejectsDuplicate_showsSkippedInsteadOfAdded() = runBlocking {
        val downloads = RecordingDownloads(acceptItems = false)
        val viewModel = HomeViewModel(downloads, FakeSettings(), FakeDiscovery())

        viewModel.onInputChanged("https://youtu.be/one")
        viewModel.smartRead()

        assertTrue(viewModel.uiState.value.notice.contains("已存在"))
        assertTrue(!viewModel.uiState.value.notice.contains("已加入 1 个作品"))
    }

    @Test
    fun restoredDraftFillsEmptyInputButNeverOverwritesIncomingShare() {
        val viewModel = HomeViewModel(RecordingDownloads(), FakeSettings(), FakeDiscovery())

        viewModel.restoreInputDraft("https://example.com/saved")
        assertEquals("https://example.com/saved", viewModel.uiState.value.input)

        viewModel.onInputChanged("https://youtu.be/shared")
        viewModel.restoreInputDraft("https://example.com/older")

        assertEquals("https://youtu.be/shared", viewModel.uiState.value.input)
    }
}

private class FakeDiscovery : SourceDiscoveryEngine {
    override suspend fun read(input: String, page: Int): DiscoveryResult = when (page) {
        1 -> if ("youtu.be" in input) DiscoveryResult.Single(media("one")) else DiscoveryResult.Collection(
            owner = CreatorIdentity("creator", "creator-id"),
            items = listOf(media("first")),
            hasMore = true,
            nextPage = 2,
        )

        2 -> DiscoveryResult.Collection(
            owner = CreatorIdentity("creator", "creator-id"),
            items = listOf(media("second")),
            hasMore = false,
            nextPage = null,
        )

        else -> error("unexpected page")
    }

    private fun media(id: String) = DiscoveredMedia(
        sourceUrl = "https://example.com/$id",
        platform = DownloadPlatform.TIKTOK,
        mediaId = id,
        title = id,
        creator = CreatorIdentity("creator", "creator-id"),
        publishedAt = "20260716",
        thumbnailUrl = "",
        defaultResolution = ResolutionPreset.UP_TO_720P,
    )
}

private class RecordingDownloads(
    private val acceptItems: Boolean = true,
) : DownloadRepository {
    override val activeTasks: Flow<List<QueuedDownload>> = MutableStateFlow(emptyList())
    override val history: Flow<List<DownloadHistory>> = MutableStateFlow(emptyList())
    val enqueued = mutableListOf<List<MediaItem>>()
    val resolutions = mutableListOf<ResolutionPreset>()

    override suspend fun enqueue(items: List<MediaItem>, resolution: ResolutionPreset): List<String> {
        enqueued += items
        resolutions += resolution
        return if (acceptItems) items.map(MediaItem::contentId) else emptyList()
    }

    override suspend fun setSelected(taskId: String, selected: Boolean) = Unit
    override suspend fun bulkSelect(taskIds: List<String>, selected: Boolean) = Unit
    override suspend fun setResolution(taskId: String, resolution: ResolutionPreset) = Unit
    override suspend fun nextSelectedWaiting(): QueuedDownload? = null
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

private class FakeSettings : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(
        AppSettings(defaultResolution = ResolutionPreset.UP_TO_720P),
    )

    override suspend fun setDefaultResolution(value: ResolutionPreset) = Unit
    override suspend fun saveInputDraft(value: String) = Unit
}
