package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.probe.ClassifiedSource
import com.nanzhufeng.videodownloader.probe.CreatorCatalog
import com.nanzhufeng.videodownloader.probe.CreatorVideoEntry
import com.nanzhufeng.videodownloader.probe.Platform
import com.nanzhufeng.videodownloader.probe.ResolvedSource
import com.nanzhufeng.videodownloader.probe.SourceKind
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformSourceDiscoveryEngineTest {
    @Test fun shortLinkNetworkFailureShowsSafeStageInsteadOfUnknownPlatformReason() {
        val error = com.nanzhufeng.videodownloader.probe.DouyinShortLinkReadException(
            java.net.UnknownHostException("private-dns-details"),
        )
        assertEquals("抖音短链接地址解析失败。请检查当前网络或 DNS 后重试。",
            DiscoveryFailurePresenter.message(IllegalStateException("wrapper", error)))
    }

    @Test fun completeGenericResolutionDoesNotRequireASecondNetworkExtraction() = runBlocking {
        val page = "https://fixture.test/media/video.mp4"
        val media = YtDlpMediaInfo("generic", "fixture:test:video", "Fixture title", "Fixture", "", page, "", "",
            page, null, "mp4", audioExt=null, headers=emptyMap())
        val gateway = object : ProbeDiscoveryGateway {
            override fun classify(input: String) = ClassifiedSource(Platform.GENERIC, SourceKind.UNKNOWN_WEB_PAGE, page)
            override fun resolve(url: String) = ResolvedSource(SourceKind.SINGLE_VIDEO, page, media)
            override fun extractSingle(url: String): YtDlpMediaInfo = error("Second network extraction is unavailable")
            override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog = error("not a collection")
        }
        val result = PlatformSourceDiscoveryEngine(gateway).read(page)
        assertTrue(result is DiscoveryResult.Single)
        assertEquals("fixture:test:video", (result as DiscoveryResult.Single).item.mediaId)
    }

    @Test
    fun repeatedDouyinShareReusesItsOfficialTargetResolution() = runBlocking {
        val delegate = DouyinFreshCookieGateway()
        var calls = 0
        val gateway = object : ProbeDiscoveryGateway by delegate {
            override fun resolve(url: String): ResolvedSource { calls++; return delegate.resolve(url) }
        }
        val engine = PlatformSourceDiscoveryEngine(gateway)
        val first = engine.read("https://v.douyin.com/qiRt29Dauu4/")
        assertEquals(first, engine.read("https://v.douyin.com/qiRt29Dauu4/"))
        assertEquals(1, calls)
    }

    @Test
    fun expiredDouyinMappingResolvesAgainAndDifferentSharesStaySeparate() = runBlocking {
        var time = 1_000L
        var calls = 0
        var current = "https://www.douyin.com/video/7684884186910329202"
        val gateway = object : ProbeDiscoveryGateway by DouyinFreshCookieGateway() {
            override fun resolve(url: String): ResolvedSource { calls++; return ResolvedSource(SourceKind.SINGLE_VIDEO, current) }
        }
        val engine = PlatformSourceDiscoveryEngine(gateway, nowMillis = { time })
        engine.read("https://v.douyin.com/one/")
        current = "https://www.douyin.com/video/7690158572814077184"
        assertEquals(DiscoveryResult.DouyinCaptureRequired(current), engine.read("https://v.douyin.com/two/"))
        time = 121_001
        assertEquals(DiscoveryResult.DouyinCaptureRequired(current), engine.read("https://v.douyin.com/one/"))
        assertEquals(3, calls)
    }

    @Test
    fun failedAndUntrustedDouyinResolutionsAreNeverCached() = runBlocking {
        var calls = 0
        val gateway = object : ProbeDiscoveryGateway by DouyinFreshCookieGateway() {
            override fun resolve(url: String): ResolvedSource {
                calls++
                if (calls == 1) error("temporary failure")
                return ResolvedSource(SourceKind.SINGLE_VIDEO, "https://douyin.com.evil.example/video/7684884186910329202")
            }
        }
        val engine = PlatformSourceDiscoveryEngine(gateway)
        repeat(3) { engine.read("https://v.douyin.com/one/") }
        assertEquals(3, calls)
    }

    @Test
    fun multipleUrlsProducePerItemResultsWithoutAutoConfusingPlatforms() = runBlocking {
        val base = FakeGateway()
        val gateway = object : ProbeDiscoveryGateway by base {
            override fun classify(input: String) = com.nanzhufeng.videodownloader.probe.UrlClassifier
                .extractAndClassify(input)
            override fun extractSingle(url: String): YtDlpMediaInfo = when {
                "broken" in url -> error("目标页面不可用")
                else -> base.extractSingle("https://youtu.be/one")
                    .copy(platform = "generic", id = url, webpageUrl = url)
            }
        }
        val result = PlatformSourceDiscoveryEngine(gateway).read(
            "https://media.example.test/one.mp4 https://media.example.test/broken.mp4",
        ) as DiscoveryResult.Batch
        assertEquals(1, result.items.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().first.contains("broken.mp4"))
    }

    @Test
    fun moreThanTwentyUrlsFailClearlyWithoutReadingAnyUrl() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(FakeGateway()).read(
            (1..21).joinToString(" ") { "https://example.test/$it.mp4" },
        ) as DiscoveryResult.Failure
        assertTrue(result.message.contains("最多读取 20 条"))
    }

    @Test
    fun collectionInsideMultipleUrlsRetainsMemberKind() = runBlocking {
        val base = FakeGateway()
        val gateway = object : ProbeDiscoveryGateway by base {
            override fun classify(input: String) = com.nanzhufeng.videodownloader.probe.UrlClassifier
                .extractAndClassify(input)
            override fun resolve(url: String) = ResolvedSource(
                if ("series" in url) SourceKind.CHANNEL_OR_PLAYLIST else SourceKind.SINGLE_VIDEO, url,
            )
            override fun extractSingle(url: String) = base.extractSingle("https://youtu.be/one")
                .copy(platform = "generic", id = "one", webpageUrl = url)
            override fun extractWebCollection(url: String, start: Int, pageSize: Int) = CreatorCatalog(
                creator = "剧集", creatorId = "series", entries = listOf(
                    CreatorVideoEntry("ep1", "第一集", "演员", "series", "https://example.test/ep1", "", ""),
                ), duplicateCount = 0, foreignCount = 0, hasMore = true, nextStart = 2,
            )
        }
        val result = PlatformSourceDiscoveryEngine(gateway).read(
            "https://example.test/series https://example.test/one.mp4",
        ) as DiscoveryResult.Batch
        val collection = result.items.first { it.mediaId == "ep1" }
        assertEquals(DownloadSourceKind.COLLECTION,
            result.sourceKinds[collection.platform to collection.mediaId])
        assertTrue(result.errors.any { it.second.contains("单独粘贴合集链接") })
    }

    @Test
    fun genericCollectionUsesCollectionIdentityNotCreatorOwnership() = runBlocking {
        val base = FakeGateway()
        val gateway = object : ProbeDiscoveryGateway by base {
            override fun classify(input: String) = com.nanzhufeng.videodownloader.probe.UrlClassifier
                .extractAndClassify(input)
            override fun resolve(url: String) = ResolvedSource(SourceKind.CHANNEL_OR_PLAYLIST, url)
            override fun extractWebCollection(url: String, start: Int, pageSize: Int) = CreatorCatalog(
                creator = "剧集", creatorId = "series-42", entries = listOf(
                    CreatorVideoEntry("ep1", "第1集", "演员", "series-42", "https://example.test/ep1", "", ""),
                ), duplicateCount = 0, foreignCount = 0, hasMore = false, nextStart = 0,
            )
        }
        val collection = PlatformSourceDiscoveryEngine(gateway)
            .read("https://example.test/series/42") as DiscoveryResult.Collection
        assertEquals("series-42", collection.owner.id)
        assertEquals(DownloadPlatform.GENERIC, collection.items.single().platform)
    }
    @Test
    fun genericWorkPageResolvesToSingleWithoutBorrowingOtherPlatform() = runBlocking {
        val input = "https://vimeo.com/227128119"
        val gateway = object : ProbeDiscoveryGateway by FakeGateway() {
            override fun classify(input: String) = com.nanzhufeng.videodownloader.probe.UrlClassifier
                .extractAndClassify(input)
            override fun resolve(url: String) = ResolvedSource(SourceKind.SINGLE_VIDEO, url)
            override fun extractSingle(url: String) = FakeGateway().extractSingle("https://youtu.be/one")
                .copy(platform = "generic", id = "vimeo-id", webpageUrl = url)
        }
        val item = (PlatformSourceDiscoveryEngine(gateway).read(input) as DiscoveryResult.Single).item
        assertEquals("GENERIC", item.platform.name)
        assertEquals(input, item.sourceUrl)
    }

    @Test
    fun pornhubUsesExistingSingleItemFlowWithOriginalTitle() = runBlocking {
        val input = "https://cn.pornhub.com/view_video.php?viewkey=sample123"
        val base = FakeGateway()
        val gateway = object : ProbeDiscoveryGateway by base {
            override fun classify(input: String) = com.nanzhufeng.videodownloader.probe.UrlClassifier
                .extractAndClassify(input)
            override fun extractSingle(url: String) = base.extractSingle("https://youtu.be/one").copy(
                platform = "pornhub", id = "sample123", title = "Synthetic fixture title", webpageUrl = url,
            )
        }
        val result = PlatformSourceDiscoveryEngine(gateway).read(input) as DiscoveryResult.Single
        assertEquals(DownloadPlatform.PORNHUB, result.item.platform)
        assertEquals("Synthetic fixture title", result.item.title)
        assertEquals("sample123", result.item.mediaId)
        assertEquals(input, result.item.sourceUrl)
    }

    @Test
    fun pornhubExtractorGoneOffersTargetPageCaptureInsteadOfCallingVideoDeleted() = runBlocking {
        val input = "https://www.pornhub.com/view_video.php?viewkey=sample123"
        val gateway = object : ProbeDiscoveryGateway by FakeGateway() {
            override fun classify(input: String) = com.nanzhufeng.videodownloader.probe.UrlClassifier
                .extractAndClassify(input)
            override fun extractSingle(url: String): YtDlpMediaInfo =
                error("[PornHub] sample123: Unable to download webpage: HTTP Error 410: Gone")
        }
        val result = PlatformSourceDiscoveryEngine(gateway).read(input)
        assertEquals(input, (result as DiscoveryResult.WebCaptureRequired).sourceUrl)
    }

    @Test
    fun pornhubForbiddenDoesNotPretendPageCaptureCanBypassAccess() = runBlocking {
        val input = "https://www.pornhub.com/view_video.php?viewkey=sample123"
        val gateway = object : ProbeDiscoveryGateway by FakeGateway() {
            override fun classify(input: String) = com.nanzhufeng.videodownloader.probe.UrlClassifier
                .extractAndClassify(input)
            override fun extractSingle(url: String): YtDlpMediaInfo =
                error("HTTP Error 403: Forbidden")
        }
        assertTrue(PlatformSourceDiscoveryEngine(gateway).read(input) is DiscoveryResult.Failure)
    }

    @Test
    fun singleVideoLink_returnsOnlyOneItem() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(FakeGateway()).read("https://youtu.be/one")

        assertTrue(result is DiscoveryResult.Single)
        val item = (result as DiscoveryResult.Single).item
        assertEquals(DownloadPlatform.YOUTUBE, item.platform)
        assertEquals("one", item.mediaId)
    }

    @Test
    fun creatorCatalog_dropsForeignCreatorsAndKeepsPagination() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(FakeGateway()).read("https://www.tiktok.com/@creator", page = 2)

        assertTrue(result is DiscoveryResult.Collection)
        val catalog = result as DiscoveryResult.Collection
        assertEquals("creator-id", catalog.owner.id)
        assertEquals(listOf("owned-1", "owned-2"), catalog.items.map { it.mediaId })
        assertTrue(catalog.hasMore)
        assertEquals(3, catalog.nextPage)
    }

    @Test
    fun douyinShortVideoUsesPageCaptureAfterOfficialResolution() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(DouyinFreshCookieGateway())
            .read("https://v.douyin.com/qiRt29Dauu4/")

        assertTrue(result is DiscoveryResult.DouyinCaptureRequired)
        assertEquals(
            "https://www.douyin.com/video/7686854662317722889",
            (result as DiscoveryResult.DouyinCaptureRequired).sourceUrl,
        )
    }

    @Test
    fun bilibiliAndXiaohongshuSingleVideosMapToDistinctPlatforms() = runBlocking {
        val bilibili = PlatformSourceDiscoveryEngine(FakeGateway())
            .read("https://www.bilibili.com/video/BV1bK411W797") as DiscoveryResult.Single
        val xiaohongshu = PlatformSourceDiscoveryEngine(FakeGateway())
            .read("https://www.rednote.com/explore/69ce30d3000000002100791c") as DiscoveryResult.Single

        assertEquals(DownloadPlatform.BILIBILI, bilibili.item.platform)
        assertEquals(DownloadPlatform.XIAOHONGSHU, xiaohongshu.item.platform)
    }

    @Test
    fun cancellationInterruptsBlockingGateway() = runBlocking {
        val startedAt = System.nanoTime()

        try {
            withTimeout(50) {
                PlatformSourceDiscoveryEngine(BlockingGateway()).read("https://youtu.be/slow")
            }
            throw AssertionError("阻塞读取必须被超时取消")
        } catch (_: TimeoutCancellationException) {
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue("取消耗时 ${elapsedMillis}ms", elapsedMillis < 250)
        }
    }

    @Test
    fun cancellationReturnsEvenWhenGatewayIgnoresThreadInterrupt() = runBlocking {
        val startedAt = System.nanoTime()

        try {
            withTimeout(50) {
                PlatformSourceDiscoveryEngine(UninterruptibleGateway()).read("https://youtu.be/slow")
            }
            throw AssertionError("不可中断读取也必须按时返回")
        } catch (_: TimeoutCancellationException) {
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue("取消耗时 ${elapsedMillis}ms", elapsedMillis < 250)
        }
    }

    @Test
    fun freshDouyinCookiesFailureBecomesActionableChineseMessage() {
        val message = DiscoveryFailurePresenter.message(
            IllegalStateException(
                "DownloadError: ERROR: [Douyin] 123: Fresh cookies (not necessarily logged in) are needed",
            ),
        )

        assertEquals(
            "抖音需要新的网页会话。请到“设置 → 账号与权限 → 抖音”重新登录，返回后重试。",
            message,
        )
    }

    @Test
    fun douyinZeroFormatsRequestsWebViewCaptureInsteadOfBlamingResolution() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(MissingDouyinFormatsGateway())
            .read("https://v.douyin.com/current/")

        assertTrue(result is DiscoveryResult.DouyinCaptureRequired)
        assertEquals(
            "https://www.douyin.com/video/7669248142533973995",
            (result as DiscoveryResult.DouyinCaptureRequired).sourceUrl,
        )
    }

    @Test
    fun douyinAnimatedNoteUnsupportedByYtDlpRequestsWebViewCapture() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(UnsupportedDouyinNoteGateway())
            .read("https://v.douyin.com/QnQSvGUXFNg/")

        assertTrue(result is DiscoveryResult.DouyinCaptureRequired)
        assertEquals(
            "https://www.douyin.com/note/7674830543565405861",
            (result as DiscoveryResult.DouyinCaptureRequired).sourceUrl,
        )
    }

    @Test
    fun douyinNoteAlwaysRequestsTargetPageCaptureInsteadOfApiGallery() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(DouyinNoteGalleryGateway())
            .read("https://v.douyin.com/stHj3vcpv64/")

        assertTrue(result is DiscoveryResult.DouyinCaptureRequired)
        assertEquals(
            "https://www.douyin.com/note/7676041925425736777",
            (result as DiscoveryResult.DouyinCaptureRequired).sourceUrl,
        )
    }

    @Test
    fun douyinNoteFallsBackToStrictPageCaptureWhenApiReturnsAnotherWork() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(MismatchedDouyinGalleryGateway())
            .read("https://www.douyin.com/note/7670887343922973155")

        assertTrue(result is DiscoveryResult.DouyinCaptureRequired)
        assertEquals(
            "https://www.douyin.com/note/7670887343922973155",
            (result as DiscoveryResult.DouyinCaptureRequired).sourceUrl,
        )
    }

}

private class DouyinFreshCookieGateway : ProbeDiscoveryGateway {
    override fun classify(input: String) =
        ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, input)

    override fun resolve(url: String) =
        ResolvedSource(SourceKind.SINGLE_VIDEO, "https://www.douyin.com/video/7686854662317722889")

    override fun extractSingle(url: String): YtDlpMediaInfo =
        error("ERROR: [Douyin] 7686854662317722889: Fresh cookies are needed")

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        error("不应读取列表")
}

private class MismatchedDouyinGalleryGateway : ProbeDiscoveryGateway {
    override fun classify(input: String) =
        ClassifiedSource(Platform.DOUYIN, SourceKind.SINGLE_VIDEO, input)

    override fun resolve(url: String): ResolvedSource = error("不应解析直接图文地址")

    override fun extractSingle(url: String): YtDlpMediaInfo = error("不应调用通用解析")

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        error("不应读取列表")
}

private class DouyinNoteGalleryGateway : ProbeDiscoveryGateway {
    override fun classify(input: String) =
        ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, input)

    override fun resolve(url: String) =
        ResolvedSource(SourceKind.SINGLE_VIDEO, "https://www.douyin.com/note/7676041925425736777")

    override fun extractSingle(url: String): YtDlpMediaInfo =
        error("抖音图文不应先调用 yt-dlp")

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        error("不应读取列表")
}

private class MissingDouyinFormatsGateway : ProbeDiscoveryGateway {
    override fun classify(input: String) =
        ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, input)

    override fun resolve(url: String) =
        ResolvedSource(SourceKind.SINGLE_VIDEO, "https://www.douyin.com/video/7669248142533973995")

    override fun extractSingle(url: String): YtDlpMediaInfo =
        error("没有找到可下载且具备音频的 MP4 视频流")

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        error("不应读取列表")
}

private class UnsupportedDouyinNoteGateway : ProbeDiscoveryGateway {
    override fun classify(input: String) =
        ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, input)

    override fun resolve(url: String) =
        ResolvedSource(SourceKind.SINGLE_VIDEO, "https://www.douyin.com/note/7674830543565405861")

    override fun extractSingle(url: String): YtDlpMediaInfo =
        error("ERROR: Unsupported URL: $url")

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        error("不应读取列表")
}

private class BlockingGateway : ProbeDiscoveryGateway {
    override fun classify(input: String) =
        ClassifiedSource(Platform.YOUTUBE, SourceKind.SINGLE_VIDEO, input)

    override fun resolve(url: String): ResolvedSource = error("不应解析短链接")

    override fun extractSingle(url: String): YtDlpMediaInfo {
        Thread.sleep(1_000)
        error("阻塞调用不应自然返回")
    }

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        error("不应读取列表")
}

private class UninterruptibleGateway : ProbeDiscoveryGateway {
    override fun classify(input: String) =
        ClassifiedSource(Platform.YOUTUBE, SourceKind.SINGLE_VIDEO, input)

    override fun resolve(url: String): ResolvedSource = error("不应解析短链接")

    override fun extractSingle(url: String): YtDlpMediaInfo {
        val end = System.nanoTime() + 1_000_000_000L
        while (System.nanoTime() < end) {
            try {
                Thread.sleep(25)
            } catch (_: InterruptedException) {
                // 模拟 Chaquopy：底层 Python 调用不会因协程取消立即退出。
            }
        }
        error("不可中断调用不应自然返回")
    }

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        error("不应读取列表")
}

private class FakeGateway : ProbeDiscoveryGateway {
    override fun classify(input: String): ClassifiedSource = when {
        "tiktok.com" in input -> ClassifiedSource(Platform.TIKTOK, SourceKind.CHANNEL_OR_PLAYLIST, input)
        "douyin.com" in input -> ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, input)
        "bilibili.com" in input -> ClassifiedSource(Platform.BILIBILI, SourceKind.SINGLE_VIDEO, input)
        "rednote.com" in input || "xiaohongshu.com" in input ->
            ClassifiedSource(Platform.XIAOHONGSHU, SourceKind.SINGLE_VIDEO, input)
        else -> ClassifiedSource(Platform.YOUTUBE, SourceKind.SINGLE_VIDEO, input)
    }

    override fun resolve(url: String): ResolvedSource =
        ResolvedSource(SourceKind.SINGLE_VIDEO, "https://www.douyin.com/video/one")

    override fun extractSingle(url: String): YtDlpMediaInfo = YtDlpMediaInfo(
        platform = when {
            "douyin" in url -> "douyin"
            "bilibili" in url -> "bilibili"
            "rednote" in url || "xiaohongshu" in url -> "xiaohongshu"
            else -> "youtube"
        },
        id = "one",
        title = "Single title",
        creator = "creator",
        creatorId = "creator-id",
        webpageUrl = url,
        uploadDate = "20260716",
        thumbnail = "https://example.com/cover.jpg",
        videoUrl = "https://example.com/video.mp4",
        audioUrl = null,
        videoExt = "mp4",
        audioExt = null,
        headers = emptyMap(),
    )

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog = CreatorCatalog(
        creator = "creator",
        creatorId = "creator-id",
        entries = listOf(
            entry("owned-1", "creator-id"),
            entry("foreign", "another-creator"),
            entry("owned-2", "creator-id"),
        ),
        duplicateCount = 0,
        foreignCount = 0,
        hasMore = true,
        nextStart = 3,
    )

    private fun entry(id: String, creatorId: String) = CreatorVideoEntry(
        id = id,
        title = id,
        creator = if (creatorId == "creator-id") "creator" else "other",
        creatorId = creatorId,
        webpageUrl = "https://example.com/$id",
        uploadDate = "20260716",
        thumbnail = "",
    )
}
