package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMedia
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMediaSource
import com.nanzhufeng.videodownloader.probe.DouyinCaptureStore
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import com.nanzhufeng.videodownloader.probe.GenericWebCapture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class YtDlpTaskMediaResolverTest {
    @Test fun cancelledExtractionCannotReturnALateCaptureAsSuccess() {
        val target = media()
        val late = java.util.concurrent.atomic.AtomicReference<DouyinCapturedMedia?>(null)
        val resolver = YtDlpTaskMediaResolver(douyinCaptures = DouyinCapturedMediaSource { late.get() },
            singleExtractor = { _, _, _ ->
                late.set(DouyinCapturedMedia(target.contentId, target.originalUrl,
                    "https://v3-web.douyinvod.com/video/tos/clip.mp4", "title", "creator", "", 1000))
                throw kotlinx.coroutines.CancellationException("cancelled")
            })
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            runBlocking { resolver.resolve(target, ResolutionPreset.UP_TO_720P) }
        }
    }
    @Test fun lateCaptureAfterExtractorFailureStillRequiresCurrentAccount() {
        val target = media()
        val late = java.util.concurrent.atomic.AtomicReference<DouyinCapturedMedia?>(null)
        val resolver = YtDlpTaskMediaResolver(douyinCaptures = DouyinCapturedMediaSource { late.get() },
            currentDouyinSessionFingerprint = { "new-session" },
            singleExtractor = { _, _, _ ->
                late.set(DouyinCapturedMedia(target.contentId, target.originalUrl,
                    "https://v3-web.douyinvod.com/video/tos/clip.mp4", "title", "creator", "", 1000,
                    captureUserAgent = "Browser", sessionFingerprint = "old-session"))
                error("extractor failed")
            })
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { resolver.resolve(target, ResolutionPreset.UP_TO_720P) }
        }
    }
    @Test fun capturedVideoUsesTheSameBrowserProfileForTransfer() = runBlocking {
        val target = media()
        val capture = DouyinCapturedMedia(target.contentId, target.originalUrl,
            "https://v3-web.douyinvod.com/video/tos/clip.mp4", "title", "creator", "", 1_000,
            captureUserAgent = "Mobile Browser")
        val resolver = YtDlpTaskMediaResolver(douyinCaptures = DouyinCapturedMediaSource { capture })
        assertEquals("Mobile Browser", resolver.resolve(target, ResolutionPreset.UP_TO_720P).headers["User-Agent"])
    }
    @Test
    fun legacyGenericTaskStillResolvesOnlyItsOriginalUrlAfterIdentityUpgrade() = runBlocking {
        val page = "https://example.test/one/index.m3u8"
        val legacy = "a".repeat(64)
        val info = mediaInfo(emptyList()).copy(platform = "generic", id = "b".repeat(64),
            legacyId = legacy, webpageUrl = page,
            videoDownloadSpec = """{"id":"${"b".repeat(64)}","url":"https://example.test/target.m3u8"}""",
            legacyVideoDownloadSpec = """{"id":"$legacy","url":"https://example.test/target.m3u8"}""")
        val resolver = YtDlpTaskMediaResolver(singleExtractor = { url, _, _ ->
            assertEquals(page, url)
            info
        })
        val result = resolver.resolve(media().copy(platform = DownloadPlatform.GENERIC,
            contentId = legacy, originalUrl = page), ResolutionPreset.UP_TO_720P)
        assertEquals(info.videoUrl, result.videoUrl)
        assertTrue(result.videoDownloadSpec.contains(legacy))
    }

    @Test
    fun legacyAliasCannotApproveAResponseForAnotherPage() {
        val page = "https://example.test/one/index.m3u8"
        val legacy = "a".repeat(64)
        val info = mediaInfo(emptyList()).copy(platform = "generic", id = "b".repeat(64),
            legacyId = legacy, webpageUrl = "https://example.test/two/index.m3u8")
        val resolver = YtDlpTaskMediaResolver(singleExtractor = { _, _, _ -> info })
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { resolver.resolve(media().copy(platform = DownloadPlatform.GENERIC,
                contentId = legacy, originalUrl = page), ResolutionPreset.UP_TO_720P) }
        }
    }

    @Test
    fun persistedTargetPlayerCaptureKeepsRefererAndHlsPlanAfterRestart() = runBlocking {
        val capture = GenericWebCapture(
            "https://example.test/work/42", "https://player.example.test/embed/42",
            "https://cdn.example.test/42.m3u8", "目标作品",
        )
        val media = media().copy(
            platform = DownloadPlatform.GENERIC, contentId = capture.mediaId,
            originalUrl = capture.pageUrl,
            capturedVideoUrl = capture.mediaUrl,
            capturedVideoReferer = capture.referer,
            capturedVideoSourceVersion = 1,
        )
        val resolver = YtDlpTaskMediaResolver(singleExtractor = { _, _, _ ->
            error("持久化的目标播放器来源不应重新误抓其他视频")
        })
        val resolved = resolver.resolve(media, ResolutionPreset.UP_TO_720P)
        assertEquals(capture.mediaUrl, resolved.videoUrl)
        assertEquals(capture.referer, resolved.headers["Referer"])
        assertTrue(resolved.videoDownloadSpec.contains("m3u8_native"))
    }

    @Test
    fun persistedPornhubCaptureUsesOfficialWorkIdentityAfterRestart() = runBlocking {
        val page = "https://www.pornhub.com/view_video.php?viewkey=sample123"
        val capture = GenericWebCapture(page, page, "https://cdn.example.test/target.mp4", "目标作品")
        val item = media().copy(
            platform = DownloadPlatform.PORNHUB, contentId = "sample123", originalUrl = page,
            capturedVideoUrl = capture.mediaUrl, capturedVideoReferer = page,
            capturedVideoSourceVersion = 1,
        )
        val resolver = YtDlpTaskMediaResolver(singleExtractor = { _, _, _ ->
            error("410 的网页捕获不应再次调用失效的提取器")
        })
        assertEquals(capture.mediaUrl, resolver.resolve(item, ResolutionPreset.UP_TO_720P).videoUrl)
    }

    @Test
    fun pornhubSegmentPlanAndTrackHeadersReachTheTransferBoundary() = runBlocking {
        val info = mediaInfo(emptyList()).copy(
            platform = "pornhub", id = "sample123", videoDownloadSpec = "video-plan",
            audioDownloadSpec = "audio-plan", audioHeaders = mapOf("Referer" to "https://audio.test/"),
        )
        val resolver = YtDlpTaskMediaResolver(singleExtractor = { _, _, access ->
            assertTrue(access.cookieHeader.isBlank())
            info
        })
        val result = resolver.resolve(media().copy(platform = DownloadPlatform.PORNHUB,
            contentId = "sample123", originalUrl = "https://cn.pornhub.com/view_video.php?viewkey=sample123"),
            ResolutionPreset.UP_TO_720P)
        assertEquals("video-plan", result.videoDownloadSpec)
        assertEquals("audio-plan", result.audioDownloadSpec)
        assertEquals("https://audio.test/", result.audioHeaders["Referer"])
    }

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
