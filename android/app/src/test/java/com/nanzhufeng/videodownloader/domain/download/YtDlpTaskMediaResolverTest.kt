package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMedia
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMediaSource
import com.nanzhufeng.videodownloader.probe.DouyinCaptureStore
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun douyinImageCaptureWinsOverYtDlpWatermarkedRendition() = runBlocking {
        val originalUrl = "https://p3-sign.douyinpic.com/tos-cn-i-0813/original~tplv-dy-aweme-images.webp"
        val watermarkedUrl = "https://p3-sign.douyinpic.com/tos-cn-i-0813/original~tplv-dy-water-v2.webp"
        val captured = DouyinCapturedMedia(
            workId = WORK_ID,
            pageUrl = "https://www.douyin.com/note/$WORK_ID",
            mediaUrl = "",
            title = "标题",
            creator = "作者",
            thumbnailUrl = "",
            capturedAtMillis = 1L,
            imageUrls = listOf(originalUrl),
            imageExpectedCount = 1,
            imageSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
        )
        val resolver = YtDlpTaskMediaResolver(
            douyinCaptures = DouyinCapturedMediaSource { captured },
            singleExtractor = { _, _, _ -> mediaInfo(imageUrls = listOf(watermarkedUrl)) },
        )

        val result = resolver.resolve(media(), ResolutionPreset.UP_TO_1080P)

        assertEquals(listOf(originalUrl), result.imageUrls.map(ResolvedImage::url))
    }

    @Test
    fun taskOwnedGalleryWinsAfterTheCaptureCacheIsGone() = runBlocking {
        val originalUrls = cleanGallery(14)
        val watermarkedUrl = "https://p3-sign.douyinpic.com/tos-cn-i-0813/original~tplv-dy-water-v2.webp"
        val resolver = YtDlpTaskMediaResolver(
            singleExtractor = { _, _, _ -> mediaInfo(imageUrls = listOf(watermarkedUrl)) },
        )

        val result = resolver.resolve(
            media(note = true).copy(
                capturedImageUrls = originalUrls,
                capturedImageExpectedCount = 14,
                capturedImageSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
            ),
            ResolutionPreset.UP_TO_1080P,
        )

        assertEquals(originalUrls, result.imageUrls.map(ResolvedImage::url))
        assertEquals(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Safari/537.36",
            result.headers["User-Agent"],
        )
    }

    @Test
    fun legacyNoteCannotFallBackToTwoWatermarkedYtDlpImages() = runBlocking {
        var extractorCalls = 0
        val resolver = YtDlpTaskMediaResolver(
            singleExtractor = { _, _, _ ->
                extractorCalls += 1
                mediaInfo(imageUrls = List(2) { index ->
                    "https://p3-sign.douyinpic.com/tos/image-$index~tplv-dy-water-v2.webp"
                })
            },
        )

        val failure = runCatching {
            resolver.resolve(media(note = true), ResolutionPreset.UP_TO_1080P)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("缺少已验证的完整原图列表"))
        assertEquals(0, extractorCalls)
    }

    @Test
    fun legacyShortLinkCannotAcceptTwoWatermarkedYtDlpImages() = runBlocking {
        var extractorCalls = 0
        val resolver = YtDlpTaskMediaResolver(
            singleExtractor = { _, _, _ ->
                extractorCalls += 1
                mediaInfo(imageUrls = List(2) { index ->
                    "https://p3-sign.douyinpic.com/tos/image-$index~tplv-dy-water-v2.webp"
                })
            },
        )

        val failure = runCatching {
            resolver.resolve(
                media(originalUrl = "https://v.douyin.com/example/"),
                ResolutionPreset.UP_TO_1080P,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("缺少已验证的完整原图列表"))
        assertEquals(1, extractorCalls)
    }

    @Test
    fun partialLegacyGalleryCannotPretendToBeCompleteAfterRestart() = runBlocking {
        val resolver = YtDlpTaskMediaResolver(
            singleExtractor = { _, _, _ -> error("不应调用 yt-dlp") },
        )
        val partialLegacy = media(note = true).copy(
            capturedImageUrls = cleanGallery(2),
            capturedImageExpectedCount = 14,
            capturedImageSourceVersion = 0,
        )

        val failure = runCatching {
            resolver.resolve(partialLegacy, ResolutionPreset.UP_TO_1080P)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun declaredFourteenButOnlyTwoCleanImagesIsRejected() = runBlocking {
        val resolver = YtDlpTaskMediaResolver(
            singleExtractor = { _, _, _ -> error("不应调用 yt-dlp") },
        )
        val incomplete = media(note = true).copy(
            capturedImageUrls = cleanGallery(2),
            capturedImageExpectedCount = 14,
            capturedImageSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
        )

        val failure = runCatching {
            resolver.resolve(incomplete, ResolutionPreset.UP_TO_1080P)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    private fun mediaInfo(imageUrls: List<String>) = YtDlpMediaInfo(
        platform = "douyin",
        id = WORK_ID,
        title = "标题",
        creator = "作者",
        creatorId = "",
        webpageUrl = "https://www.douyin.com/note/$WORK_ID",
        uploadDate = "",
        thumbnail = "",
        videoUrl = "",
        audioUrl = null,
        videoExt = "mp4",
        audioExt = null,
        headers = emptyMap(),
        imageUrls = imageUrls,
    )

    private fun media(
        note: Boolean = false,
        originalUrl: String = "https://www.douyin.com/${if (note) "note" else "video"}/$WORK_ID",
    ) = MediaItem(
        mediaKey = "douyin:$WORK_ID",
        platform = DownloadPlatform.DOUYIN,
        contentId = WORK_ID,
        originalUrl = originalUrl,
        sourceKind = DownloadSourceKind.SINGLE_VIDEO,
        title = "标题",
        creator = "作者",
        creatorId = "",
        publishDate = "",
        thumbnailUrl = "",
    )

    private fun cleanGallery(count: Int): List<String> = List(count) { index ->
        "https://p3-sign.douyinpic.com/tos/image-$index~tplv-dy-aweme-images.webp?signature=$index"
    }

    private companion object {
        const val WORK_ID = "7669248142533973995"
    }
}
