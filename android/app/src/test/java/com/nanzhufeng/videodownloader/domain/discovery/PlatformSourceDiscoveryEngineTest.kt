package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
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
    fun singleShortLink_staysSingleAfterResolution() = runBlocking {
        val result = PlatformSourceDiscoveryEngine(FakeGateway()).read("https://v.douyin.com/short")

        assertTrue(result is DiscoveryResult.Single)
        assertFalse(result is DiscoveryResult.Collection)
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
