package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.YtDlpProbe
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMediaSource
import com.nanzhufeng.videodownloader.probe.DouyinCaptureStore
import com.nanzhufeng.videodownloader.probe.NoOpDouyinCapturedMediaSource
import com.nanzhufeng.videodownloader.domain.session.NoOpSessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionAccess
import com.nanzhufeng.videodownloader.domain.session.SessionProvider
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

class YtDlpTaskMediaResolver(
    private val probe: YtDlpProbe = YtDlpProbe(),
    private val sessions: SessionProvider = NoOpSessionProvider,
    private val douyinCaptures: DouyinCapturedMediaSource = NoOpDouyinCapturedMediaSource,
    private val singleExtractor: (String, ResolutionPreset, SessionAccess) -> YtDlpMediaInfo =
        probe::extractSingle,
) : TaskMediaResolver {
    override suspend fun resolve(
        media: MediaItem,
        resolution: ResolutionPreset,
    ): ResolvedMedia = withContext(Dispatchers.IO) {
        // The task-owned capture is the only source that survives process
        // recreation and is therefore the authoritative source for a gallery.
        // Do this before consulting the transient capture cache or yt-dlp.
        if (media.platform == DownloadPlatform.DOUYIN && media.hasVerifiedCapturedGallery()) {
            return@withContext media.capturedImagesAsResolvedMedia()
        }
        // A successful yt-dlp parse is not authoritative for a Douyin image
        // post: its public extractor can return the watermarked rendition.
        // The just-read target-page capture contains the verified complete
        // image list, so it must win before any generic re-parse is attempted.
        val freshCapture = if (media.platform == DownloadPlatform.DOUYIN) {
            douyinCaptures.find(media.contentId)
        } else {
            null
        }
        if (freshCapture != null) {
            if (freshCapture.mediaUrl.isNotBlank()) return@withContext freshCapture.asResolvedMedia()
            if (DouyinCaptureStore.isVerifiedImageGallery(
                    imageUrls = freshCapture.imageUrls,
                    expectedCount = freshCapture.imageExpectedCount,
                    sourceVersion = freshCapture.imageSourceVersion,
                )
            ) {
                return@withContext freshCapture.asResolvedMedia()
            }
        }
        if (media.platform == DownloadPlatform.DOUYIN && media.originalUrl.isDouyinNoteUrl()) {
            error("抖音图文缺少已验证的完整原图列表，请重新智能读取后再下载")
        }

        val info = try {
            singleExtractor(
                media.originalUrl,
                resolution,
                sessions.accessFor(media.originalUrl),
            )
        } catch (error: Exception) {
            if (media.platform != DownloadPlatform.DOUYIN) throw error
            val captured = douyinCaptures.find(media.contentId) ?: throw error
            return@withContext captured.asResolvedMedia()
        }
        require(info.id == media.contentId) {
            "解析结果与目标作品不一致：${info.id} != ${media.contentId}"
        }
        if (
            media.platform == DownloadPlatform.DOUYIN &&
            (info.imageItems.isNotEmpty() || info.imageUrls.isNotEmpty())
        ) {
            error("抖音图文缺少已验证的完整原图列表，请重新智能读取后再下载")
        }
        ResolvedMedia(
            videoUrl = info.videoUrl,
            audioUrl = info.audioUrl,
            videoExtension = info.videoExt,
            videoSizeBytes = info.videoSizeBytes,
            audioExtension = info.audioExt,
            headers = info.headers,
            videoCookieHeader = info.videoCookieHeader,
            audioCookieHeader = info.audioCookieHeader,
            audioFromVideoSource = info.audioFromVideoSource,
            imageUrls = (info.imageItems.ifEmpty { info.imageUrls.map { imageUrl ->
                com.nanzhufeng.videodownloader.probe.YtDlpImageItem(imageUrl)
            } }).map { image ->
                ResolvedImage(
                    url = image.url,
                    extension = image.url.imageExtension(),
                    motionUrl = image.motionUrl,
                    motionExtension = image.motionUrl?.videoExtension() ?: "mp4",
                )
            },
        )
    }

    private fun String.imageExtension(): String {
        val extension = substringBefore('?').substringAfterLast('.', "").lowercase()
        return extension.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "jpg"
    }

    private fun String.videoExtension(): String {
        val extension = substringBefore('?').substringAfterLast('.', "").lowercase()
        return extension.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "mp4"
    }

    private fun String.isDouyinNoteUrl(): Boolean = runCatching {
        URI(this).path.orEmpty().contains("/note/")
    }.getOrDefault(false)

    private fun MediaItem.hasVerifiedCapturedGallery(): Boolean =
        DouyinCaptureStore.isVerifiedImageGallery(
            imageUrls = capturedImageUrls,
            expectedCount = capturedImageExpectedCount,
            sourceVersion = capturedImageSourceVersion,
        )

    private fun com.nanzhufeng.videodownloader.probe.DouyinCapturedMedia.asResolvedMedia() =
        ResolvedMedia(
            videoUrl = mediaUrl,
            audioUrl = null,
            videoExtension = "mp4",
            videoSizeBytes = 0L,
            audioExtension = null,
            headers = buildMap {
                put("Referer", pageUrl)
                put("User-Agent", DOUYIN_PAGE_USER_AGENT)
            },
            imageUrls = imageUrls.map { imageUrl ->
                ResolvedImage(imageUrl, imageUrl.imageExtension())
            },
        )

    private fun MediaItem.capturedImagesAsResolvedMedia() = ResolvedMedia(
        videoUrl = "",
        audioUrl = null,
        videoExtension = "mp4",
        videoSizeBytes = 0L,
        audioExtension = null,
        headers = buildMap {
            put("Referer", originalUrl)
            // The source list was captured from the target page with this
            // desktop UA.  Do not switch to a mobile representation for the
            // later byte request: Douyin/CDN can select a different rendition
            // for a different client profile.
            put("User-Agent", DOUYIN_PAGE_USER_AGENT)
        },
        imageUrls = capturedImageUrls.map { imageUrl ->
            ResolvedImage(imageUrl, imageUrl.imageExtension())
        },
    )

    private companion object {
        const val DOUYIN_PAGE_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Safari/537.36"
    }
}
