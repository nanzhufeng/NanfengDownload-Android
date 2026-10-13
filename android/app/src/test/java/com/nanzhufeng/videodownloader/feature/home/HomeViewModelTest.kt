package com.nanzhufeng.videodownloader.feature.home

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
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
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMedia
import com.nanzhufeng.videodownloader.probe.DouyinCaptureStore
import com.nanzhufeng.videodownloader.probe.GenericWebCapture
import com.nanzhufeng.videodownloader.probe.GenericWebEpisode
import com.nanzhufeng.videodownloader.probe.genericCollectionMediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test fun replacementWhileWaitingForSettingsNeverCommitsOldMedia() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val downloads = RecordingDownloads()
        val settings = object : SettingsRepository by FakeSettings() {
            override val settings: Flow<AppSettings> = kotlinx.coroutines.flow.flow {
                waiting.complete(Unit); release.await(); emit(AppSettings())
            }
        }
        val vm = HomeViewModel(downloads, settings, FakeDiscovery())
        vm.onInputChanged("https://youtu.be/old")
        val old = launch { vm.smartRead() }
        waiting.await(); vm.onInputChanged("https://youtu.be/new")
        release.complete(Unit); old.join()
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals("", vm.uiState.value.notice)
    }

    @Test fun oldCaptureCancellationCannotStopANewerCaptureOfTheSameWork() = runBlocking {
        val page = "https://www.douyin.com/note/7676041925425736777"
        val vm = HomeViewModel(RecordingDownloads(), FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.DouyinCaptureRequired(input)
        })
        vm.onInputChanged(page); vm.smartRead()
        val oldRequest = vm.beginCaptureRequest()
        vm.onInputChanged(""); vm.onInputChanged(page); vm.smartRead()
        val currentRequest = vm.beginCaptureRequest()
        vm.completeDouyinCapture(null, "old cancellation", oldRequest)
        assertTrue(vm.uiState.value.isReading)
        assertEquals(page, vm.uiState.value.douyinCaptureUrl)
        vm.completeDouyinCapture(null, "current cancellation", currentRequest)
        assertTrue(!vm.uiState.value.isReading)
        assertEquals("current cancellation", vm.uiState.value.notice)
    }

    @Test fun oldWebEpisodeResultCannotReplaceNewCapture() = runBlocking {
        val page = "https://example.test/collection"
        val vm = HomeViewModel(RecordingDownloads(), FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.WebCaptureRequired(input)
        })
        vm.onInputChanged(page); vm.smartRead()
        val oldRequest = vm.beginCaptureRequest()
        vm.onInputChanged("https://example.test/new"); vm.smartRead()
        vm.beginCaptureRequest()
        vm.completeWebCapture(null, listOf(GenericWebEpisode("https://example.test/old-episode", "old")),
            page, requestId = oldRequest)
        assertTrue(vm.uiState.value.pendingItems.isEmpty())
        assertTrue(vm.uiState.value.isReading)
        assertEquals("https://example.test/new", vm.uiState.value.webCaptureUrl)
    }

    @Test fun duplicateInputCallbackDoesNotCancelAnActiveCapture() = runBlocking {
        val page = "https://www.douyin.com/note/7676041925425736777"
        val vm = HomeViewModel(RecordingDownloads(), FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.DouyinCaptureRequired(input)
        })
        vm.onInputChanged(page); vm.smartRead(); vm.onInputChanged(page)
        assertTrue(vm.uiState.value.isReading)
        assertEquals(page, vm.uiState.value.douyinCaptureUrl)
    }

    @Test fun captureForAnotherWorkCannotCommitToCurrentRead() = runBlocking {
        val page = "https://www.douyin.com/note/7676041925425736777"
        val downloads = RecordingDownloads()
        val vm = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.DouyinCaptureRequired(input)
        })
        vm.onInputChanged(page); vm.smartRead()
        vm.completeDouyinCapture(DouyinCapturedMedia("7669248142533973995",
            "https://www.douyin.com/video/7669248142533973995",
            "https://v3-web.douyinvod.com/video/tos/cn/target.mp4", "wrong", "author", "", 1))
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals(null, vm.uiState.value.douyinCaptureUrl)
        assertTrue(!vm.uiState.value.isReading)
    }

    @Test fun inputReplacementClearsCreatorPagination() = runBlocking {
        val vm = HomeViewModel(RecordingDownloads(), FakeSettings(), FakeDiscovery())
        vm.onInputChanged("https://example.test/creator"); vm.smartRead()
        assertTrue(vm.uiState.value.canLoadMore)
        vm.onInputChanged("https://youtu.be/new")
        assertTrue(!vm.uiState.value.canLoadMore)
        assertEquals(null, vm.uiState.value.nextPage)
        assertEquals("", vm.uiState.value.sourceInput)
    }

    @Test fun unexpectedDiscoveryFailureRestoresReadableState() = runBlocking {
        val vm = HomeViewModel(RecordingDownloads(), FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int): DiscoveryResult = error("测试来源不可用")
        })
        vm.onInputChanged("https://youtu.be/new"); vm.smartRead()
        assertTrue(!vm.uiState.value.isReading)
        assertTrue(vm.uiState.value.notice.contains("测试来源不可用"))
    }

    @Test fun changingInputCancelsOldReadAndNeverEnqueuesItsResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int): DiscoveryResult {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        })
        viewModel.onInputChanged("https://youtu.be/old")
        val job = launch { viewModel.smartRead() }
        started.await()
        viewModel.onInputChanged("https://youtu.be/new")
        assertTrue(!viewModel.uiState.value.isReading)
        kotlinx.coroutines.withTimeout(1_000) { cancelled.await(); job.join() }
        assertEquals("https://youtu.be/new", viewModel.uiState.value.input)
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals("", viewModel.uiState.value.notice)
    }

    @Test fun lateNonCooperativeOldResultCannotReplaceNewRead() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int): DiscoveryResult {
                if (input.endsWith("old")) {
                    started.complete(Unit)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
                    return FakeDiscovery().read("https://youtu.be/old", 1)
                }
                return DiscoveryResult.Failure("new result")
            }
        })
        viewModel.onInputChanged("https://youtu.be/old")
        val old = launch { viewModel.smartRead() }
        started.await()
        viewModel.onInputChanged("https://youtu.be/new")
        viewModel.smartRead()
        release.complete(Unit); old.join()
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals("new result", viewModel.uiState.value.notice)
    }

    @Test fun replacingInputClearsOldCaptureAndPagination() = runBlocking {
        val downloads = RecordingDownloads()
        val page = "https://www.douyin.com/note/7676041925425736777"
        val viewModel = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.DouyinCaptureRequired(input)
        })
        viewModel.onInputChanged(page); viewModel.smartRead()
        viewModel.onInputChanged("https://youtu.be/new")
        viewModel.completeDouyinCapture(DouyinCapturedMedia("7676041925425736777", page,
            "https://v3-web.douyinvod.com/video/tos/cn/target.mp4", "old", "author", "", 1))
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals(null, viewModel.uiState.value.douyinCaptureUrl)
        assertTrue(!viewModel.uiState.value.isReading)
    }

    @Test
    fun staleWebCaptureResultAfterInputChangesNeverCreatesATask() = runBlocking {
        val page = "https://example.test/watch/old"
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.WebCaptureRequired(input)
        })

        viewModel.onInputChanged(page)
        viewModel.smartRead()
        assertEquals(page, viewModel.uiState.value.webCaptureUrl)
        viewModel.onInputChanged("https://example.test/watch/new")
        viewModel.completeWebCapture(
            GenericWebCapture(page, page, "https://cdn.example.test/old.mp4", "旧作品"),
        )

        assertTrue(downloads.enqueued.isEmpty())
        assertTrue(viewModel.uiState.value.notice.contains("未创建任务"))
    }

    @Test
    fun expiredWebTaskCanRefreshOnlyItsOwnConfirmedPlayerSource() = runBlocking {
        val page = "https://www.yhdm.one/vod-play/2025999379/ep1.html"
        val other = "https://www.yhdm.one/vod-play/2025999379/ep2.html"
        val id = genericCollectionMediaId(page)
        val media = MediaItem(
            mediaKey = "GENERIC:$id", platform = DownloadPlatform.GENERIC, contentId = id,
            originalUrl = page, sourceKind = DownloadSourceKind.COLLECTION, title = "第1集",
            creator = "站点", creatorId = "", publishDate = "", thumbnailUrl = "",
            capturedVideoUrl = "https://cdn.example.test/expired.m3u8",
            capturedVideoReferer = page, capturedVideoSourceVersion = 1,
        )
        val task = com.nanzhufeng.videodownloader.core.model.DownloadTask(
            taskId = "failed-episode", mediaKey = media.mediaKey, selected = true, sortOrder = 1,
            resolution = ResolutionPreset.UP_TO_720P, saveTreeUri = null,
            downloadedBytes = 0, totalBytes = 0, speedBytesPerSecond = 0,
            remainingSeconds = null, status = DownloadTaskStatus.FAILED,
            failureType = com.nanzhufeng.videodownloader.core.model.DownloadFailureType.TRANSFER,
            errorSummary = "下载请求失败：HTTP 403", retryCount = 1, updatedAt = 1,
        )
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), FakeDiscovery())

        assertTrue(viewModel.requestWebSourceRefresh(QueuedDownload(task, media)))
        assertEquals(page, viewModel.uiState.value.webCaptureUrl)
        viewModel.completeWebCapture(GenericWebCapture(other, other, "https://cdn.example.test/other.mp4", "其他集"))
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals(null, viewModel.uiState.value.webCaptureUrl)

        assertTrue(viewModel.requestWebSourceRefresh(QueuedDownload(task, media)))
        viewModel.completeWebCapture(GenericWebCapture(page, page, "https://cdn.example.test/fresh.m3u8", "第1集"))
        val refreshed = downloads.enqueued.single().single()
        assertEquals(id, refreshed.contentId)
        assertEquals(media.sourceKind, refreshed.sourceKind)
        assertEquals("https://cdn.example.test/fresh.m3u8", refreshed.capturedVideoUrl)
        assertEquals(null, viewModel.uiState.value.webCaptureUrl)
    }

    @Test
    fun selectedYinghuaEpisodesRequireVisiblePlayerCaptureBeforeQueueing() = runBlocking {
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), FakeDiscovery())
        val first = "https://www.yhdm.one/vod-play/2025999379/ep1.html"
        val second = "https://www.yhdm.one/vod-play/2025999379/ep2.html"

        viewModel.completeWebCapture(
            capture = null,
            episodes = listOf(GenericWebEpisode(first, "第1集"), GenericWebEpisode(second, "第2集")),
            pageUrl = "https://www.yhdm.one/vod/2025999379.html",
        )
        viewModel.confirmPending()

        assertTrue(downloads.enqueued.isEmpty())
        assertEquals(first, viewModel.uiState.value.webCaptureUrl)
        viewModel.completeWebCapture(
            GenericWebCapture(first, first, "https://cdn.example.test/first.m3u8", "作品 第1集"),
        )

        val saved = downloads.enqueued.single().single()
        assertEquals(genericCollectionMediaId(first), saved.contentId)
        assertEquals(DownloadSourceKind.COLLECTION, saved.sourceKind)
        assertEquals("https://cdn.example.test/first.m3u8", saved.capturedVideoUrl)
        assertEquals(second, viewModel.uiState.value.webCaptureUrl)
        viewModel.completeWebCapture(
            GenericWebCapture(second, second, "https://cdn.example.test/second.mp4", "作品 第2集"),
        )
        assertEquals(2, downloads.enqueued.size)
        assertEquals(null, viewModel.uiState.value.webCaptureUrl)
        assertTrue(!viewModel.uiState.value.isReading)
    }

    @Test
    fun cancellingOrMismatchingAnEpisodeNeverQueuesUnconfirmedWorks() = runBlocking {
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), FakeDiscovery())
        val first = "https://www.yhdm.one/vod-play/2025999379/ep1.html"
        val second = "https://www.yhdm.one/vod-play/2025999379/ep2.html"
        val episodes = listOf(GenericWebEpisode(first, "第1集"), GenericWebEpisode(second, "第2集"))

        viewModel.completeWebCapture(null, episodes = episodes, pageUrl = "https://www.yhdm.one/vod/2025999379.html")
        viewModel.confirmPending()
        viewModel.completeWebCapture(
            GenericWebCapture(second, second, "https://cdn.example.test/second.mp4", "其他集"),
        )
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals(null, viewModel.uiState.value.webCaptureUrl)
        assertTrue(viewModel.uiState.value.notice.contains("不属于当前剧集"))

        viewModel.completeWebCapture(null, episodes = episodes, pageUrl = "https://www.yhdm.one/vod/2025999379.html")
        viewModel.confirmPending()
        viewModel.completeWebCapture(null)
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals(null, viewModel.uiState.value.webCaptureUrl)
        assertTrue(viewModel.uiState.value.notice.contains("未确认播放源"))
    }

    @Test
    fun genericCollectionWaitsForSelectionBeforeQueueing() = runBlocking {
        val downloads = RecordingDownloads()
        val first = DiscoveredMedia(
            "https://example.test/one", DownloadPlatform.GENERIC, "one", "第一集",
            CreatorIdentity("合集", "series"), "", "", ResolutionPreset.UP_TO_720P,
        )
        val second = first.copy(sourceUrl = "https://example.test/two", mediaId = "two", title = "第二集")
        val viewModel = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.Collection(
                CreatorIdentity("合集", "series"), listOf(first, second), false, null,
            )
        })

        viewModel.onInputChanged("https://example.test/series")
        viewModel.smartRead()
        assertTrue(downloads.enqueued.isEmpty())
        assertEquals(2, viewModel.uiState.value.pendingItems.size)
        viewModel.setPendingSelected("GENERIC:two", false)
        viewModel.confirmPending()
        assertEquals(listOf("one"), downloads.enqueued.single().map(MediaItem::contentId))
    }

    @Test
    fun genericCollectionLoadMoreKeepsExistingSelectionAndCollectionKind() = runBlocking {
        val downloads = RecordingDownloads()
        val first = DiscoveredMedia(
            "https://example.test/one", DownloadPlatform.GENERIC, "one", "第一集",
            CreatorIdentity("合集", "series"), "", "", ResolutionPreset.UP_TO_720P,
        )
        val second = first.copy(sourceUrl = "https://example.test/two", mediaId = "two", title = "第二集")
        val viewModel = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.Collection(
                CreatorIdentity("合集", "series"), listOf(if (page == 1) first else second),
                page == 1, if (page == 1) 2 else null,
            )
        })
        viewModel.onInputChanged("https://example.test/series")
        viewModel.smartRead()
        viewModel.setPendingSelected("GENERIC:one", false)
        assertTrue(viewModel.uiState.value.canLoadMore)
        viewModel.loadMore()
        assertEquals(2, viewModel.uiState.value.pendingItems.size)
        assertEquals(false, viewModel.uiState.value.pendingItems.first().selected)
        viewModel.confirmPending()
        assertEquals(listOf("two"), downloads.enqueued.single().map(MediaItem::contentId))
        assertEquals(com.nanzhufeng.videodownloader.core.model.DownloadSourceKind.COLLECTION,
            downloads.enqueued.single().single().sourceKind)
    }

    @Test
    fun multipleUrlFailuresRemainVisibleUntilSelectionIsDismissed() = runBlocking {
        val viewModel = HomeViewModel(RecordingDownloads(), FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.Batch(
                emptyList(), listOf("https://example.test/missing?token=secret" to "页面不可用"),
            )
        })
        viewModel.onInputChanged("https://example.test/missing?token=secret https://example.test/other")
        viewModel.smartRead()
        assertEquals(1, viewModel.uiState.value.pendingErrors.size)
        viewModel.dismissPending()
        assertTrue(viewModel.uiState.value.pendingErrors.isEmpty())
    }

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

    @Test
    fun douyinWebViewCaptureCompletesTheSameSmartReadAndEnqueuesMedia() = runBlocking {
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(
            downloads,
            FakeSettings(),
            object : SourceDiscoveryEngine {
                override suspend fun read(input: String, page: Int) =
                    DiscoveryResult.DouyinCaptureRequired("https://v.douyin.com/current/")
            },
        )

        viewModel.onInputChanged("https://v.douyin.com/current/")
        viewModel.smartRead()
        assertTrue(viewModel.uiState.value.isReading)
        assertEquals("https://v.douyin.com/current/", viewModel.uiState.value.douyinCaptureUrl)

        viewModel.completeDouyinCapture(
            DouyinCapturedMedia(
                workId = "7669248142533973995",
                pageUrl = "https://www.douyin.com/video/7669248142533973995",
                mediaUrl = "https://v3-web.douyinvod.com/video/tos/cn/target.mp4",
                title = "抖音作品",
                creator = "作者",
                thumbnailUrl = "",
                capturedAtMillis = 1L,
            ),
        )

        assertEquals("7669248142533973995", downloads.enqueued.single().single().contentId)
        assertTrue(downloads.enqueued.single().single().capturedImageUrls.isEmpty())
        assertTrue(!viewModel.uiState.value.isReading)
        assertTrue(viewModel.uiState.value.notice.contains("已通过抖音页面读取"))
    }

    @Test
    fun pornhubPageCaptureEnqueuesOriginalPlatformAndViewkey() = runBlocking {
        val page = "https://www.pornhub.com/view_video.php?viewkey=sample123"
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.WebCaptureRequired(input)
        })
        viewModel.onInputChanged(page)
        viewModel.smartRead()
        assertEquals(page, viewModel.uiState.value.webCaptureUrl)

        viewModel.completeWebCapture(
            GenericWebCapture(page, page, "https://cdn.example.test/target.mp4", "目标作品"),
        )

        val saved = downloads.enqueued.single().single()
        assertEquals(DownloadPlatform.PORNHUB, saved.platform)
        assertEquals("sample123", saved.contentId)
        assertEquals(page, saved.originalUrl)
        assertTrue(!viewModel.uiState.value.isReading)
    }

    @Test
    fun douyinGalleryIsEnqueuedWithTheExactCapturedImageList() = runBlocking {
        val downloads = RecordingDownloads()
        val viewModel = HomeViewModel(downloads, FakeSettings(), object : SourceDiscoveryEngine {
            override suspend fun read(input: String, page: Int) = DiscoveryResult.DouyinCaptureRequired(input)
        })
        viewModel.onInputChanged("https://www.douyin.com/note/7676041925425736777")
        viewModel.smartRead()
        val imageUrls = listOf("https://p3-sign.douyinpic.com/original~tplv-dy-aweme-images.webp")

        viewModel.completeDouyinCapture(
            DouyinCapturedMedia(
                workId = "7676041925425736777",
                pageUrl = "https://www.douyin.com/note/7676041925425736777",
                mediaUrl = "",
                title = "图文",
                creator = "作者",
                thumbnailUrl = "",
                capturedAtMillis = 1L,
                imageUrls = imageUrls,
                imageExpectedCount = imageUrls.size,
                imageSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
            ),
        )

        val enqueued = downloads.enqueued.single().single()
        assertEquals(imageUrls, enqueued.capturedImageUrls)
        assertEquals(imageUrls.size, enqueued.capturedImageExpectedCount)
        assertEquals(
            DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
            enqueued.capturedImageSourceVersion,
        )
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
        audioSegmentCount: Int,
    ): DownloadHistory? = null
}

private class FakeSettings : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(
        AppSettings(defaultResolution = ResolutionPreset.UP_TO_720P),
    )

    override suspend fun setDefaultResolution(value: ResolutionPreset) = Unit
    override suspend fun saveInputDraft(value: String) = Unit
}
