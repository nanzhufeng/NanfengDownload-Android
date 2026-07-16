package com.nanzhufeng.videodownloader.probe

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TikTokLiveProbeInstrumentedTest {
    private val tag = "TikTokLiveProbe"

    @Test
    fun publicSingleParsesDownloadsAndWritesMediaStore() {
        val arguments = InstrumentationRegistry.getArguments()
        val url = arguments.getString("tiktokUrl").orEmpty()
        assumeTrue("未提供 tiktokUrl，跳过外网真机探测", url.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = YtDlpProbe().extractSingle(url)

        assertEquals("tiktok", info.platform)
        val directory = File(context.cacheDir, "probe/tiktok-live-${info.id}").apply {
            deleteRecursively()
            assertTrue(mkdirs())
        }
        val output = HttpFileDownloader(retryDelayMillis = 0).download(
            DirectDownloadRequest(
                url = info.videoUrl,
                headers = info.headers,
                target = File(directory, "video.${info.videoExt}"),
            ),
            cancelled = AtomicBoolean(false),
            onProgress = { _, _ -> },
        )

        assertTrue(output.length() > 0L)
        assertTrue(MediaFileValidator.isLikelyMedia(output))
        val uri = MediaStoreProbe.writeVideo(
            context = context,
            source = output,
            displayName = "tiktok-${info.id}.mp4",
        )
        assertEquals("content", uri.scheme)
        println(
            "TIKTOK_SINGLE_OK id=${info.id} creator=${info.creator} " +
                "bytes=${output.length()} uri=$uri",
        )
    }

    @Test
    fun creatorCatalogLoadsTwoPagesWithoutForeignEntries() {
        val arguments = InstrumentationRegistry.getArguments()
        val url = arguments.getString("tiktokCreatorUrl").orEmpty()
        val pageSize = arguments.getString("tiktokPageSize")?.toIntOrNull() ?: 50
        assumeTrue("未提供 tiktokCreatorUrl，跳过外网真机探测", url.isNotBlank())
        val probe = YtDlpProbe()
        val firstStartedAt = SystemClock.elapsedRealtime()
        Log.i(tag, "开始读取 TikTok 作者第一页，pageSize=$pageSize")
        val first = probe.extractCreator(url, start = 1, pageSize = pageSize)
        Log.i(
            tag,
            "TikTok 作者第一页完成，entries=${first.entries.size} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - firstStartedAt}",
        )

        assertEquals(pageSize, first.entries.size)
        assertEquals(0, first.foreignCount)
        assertTrue(first.hasMore)
        val secondStartedAt = SystemClock.elapsedRealtime()
        Log.i(tag, "开始读取 TikTok 作者第二页，start=${first.nextStart}")
        val second = probe.extractCreator(url, start = first.nextStart, pageSize = pageSize)
        Log.i(
            tag,
            "TikTok 作者第二页完成，entries=${second.entries.size} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - secondStartedAt}",
        )
        val merged = first.append(second)

        assertEquals(pageSize * 2, merged.entries.size)
        assertEquals(0, merged.foreignCount)
        assertEquals(1, merged.entries.map(CreatorVideoEntry::creatorId).distinct().size)
        println(
            "TIKTOK_CREATOR_OK creator=${merged.creator} entries=${merged.entries.size} " +
                "foreign=${merged.foreignCount} nextStart=${merged.nextStart}",
        )
    }

    @Test
    fun creatorCatalogLoadsFirstFiftyWithoutForeignEntries() {
        val arguments = InstrumentationRegistry.getArguments()
        val url = arguments.getString("tiktokCreatorUrl").orEmpty()
        assumeTrue("未提供 tiktokCreatorUrl，跳过外网真机探测", url.isNotBlank())
        val startedAt = SystemClock.elapsedRealtime()
        val catalog = YtDlpProbe().extractCreator(url, start = 1, pageSize = 50)

        assertEquals(50, catalog.entries.size)
        assertEquals(0, catalog.foreignCount)
        assertTrue(catalog.hasMore)
        assertEquals(51, catalog.nextStart)
        Log.i(
            tag,
            "TikTok 作者首批 50 条完成，entries=${catalog.entries.size} " +
                "foreign=${catalog.foreignCount} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
    }
}
