package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMedia
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMediaSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpTaskMediaResolverTest {
    @Test
    fun douyinUsesFreshWebViewCaptureWhenYtDlpReturnsNoFormats() = runBlocking {
        val captured = DouyinCapturedMedia(
            workId = WORK_ID,
            pageUrl = "https://www.douyin.com/video/$WORK_ID",
            mediaUrl = "https://v3-web.douyinvod.com/video/tos/cn/target.mp4",
            title = "标题",
            creator = "作者",
            thumbnailUrl = "",
            capturedAtMillis = 1L,
        )
        val resolver = YtDlpTaskMediaResolver(
            douyinCaptures = DouyinCapturedMediaSource { captured },
            singleExtractor = { _, _, _ -> error("没有找到可下载且具备音频的 MP4 视频流") },
        )

        val result = resolver.resolve(media(), ResolutionPreset.UP_TO_1080P)

        assertEquals(captured.mediaUrl, result.videoUrl)
        assertNull(result.audioUrl)
        assertEquals(captured.pageUrl, result.headers["Referer"])
    }

    private fun media() = MediaItem(
        mediaKey = "douyin:$WORK_ID",
        platform = DownloadPlatform.DOUYIN,
        contentId = WORK_ID,
        originalUrl = "https://www.douyin.com/video/$WORK_ID",
        sourceKind = DownloadSourceKind.SINGLE_VIDEO,
        title = "标题",
        creator = "作者",
        creatorId = "",
        publishDate = "",
        thumbnailUrl = "",
    )

    private companion object {
        const val WORK_ID = "7669248142533973995"
    }
}
