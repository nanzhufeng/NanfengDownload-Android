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
}

private class FakeGateway : ProbeDiscoveryGateway {
    override fun classify(input: String): ClassifiedSource = when {
        "tiktok.com" in input -> ClassifiedSource(Platform.TIKTOK, SourceKind.CHANNEL_OR_PLAYLIST, input)
        "douyin.com" in input -> ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, input)
        else -> ClassifiedSource(Platform.YOUTUBE, SourceKind.SINGLE_VIDEO, input)
    }

    override fun resolve(url: String): ResolvedSource =
        ResolvedSource(SourceKind.SINGLE_VIDEO, "https://www.douyin.com/video/one")

    override fun extractSingle(url: String): YtDlpMediaInfo = YtDlpMediaInfo(
        platform = if ("douyin" in url) "douyin" else "youtube",
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
