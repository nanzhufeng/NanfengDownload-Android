package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.YtDlpProbe
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMediaSource
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMedia
import com.nanzhufeng.videodownloader.probe.DouyinCaptureStore
import com.nanzhufeng.videodownloader.probe.DouyinCaptureContext
import com.nanzhufeng.videodownloader.probe.SignedMediaUrlExpiry
import com.nanzhufeng.videodownloader.probe.NoOpDouyinCapturedMediaSource
import com.nanzhufeng.videodownloader.domain.session.NoOpSessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionAccess
import com.nanzhufeng.videodownloader.domain.session.SessionProvider
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import com.nanzhufeng.videodownloader.probe.GenericWebCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.security.MessageDigest

class YtDlpTaskMediaResolver(
    private val probe: YtDlpProbe = YtDlpProbe(),
    private val sessions: SessionProvider = NoOpSessionProvider,
    private val douyinCaptures: DouyinCapturedMediaSource = NoOpDouyinCapturedMediaSource,
    private val singleExtractor: (String, ResolutionPreset, SessionAccess) -> YtDlpMediaInfo =
        probe::extractSingle,
    private val currentDouyinSessionFingerprint: () -> String = DouyinCaptureContext::currentSessionFingerprint,
) : TaskMediaResolver {
    private fun validateDouyinCapture(freshCapture: DouyinCapturedMedia, media: MediaItem) {
        require(DouyinCaptureStore.extractWorkId(freshCapture.pageUrl) == media.contentId) {
            "抖音捕获页面与目标作品不一致，请重新智能读取"
        }
        require(freshCapture.mediaUrl.isBlank() || DouyinCaptureStore.isMediaUrl(freshCapture.mediaUrl)) {
            "抖音捕获来源未通过安全校验，请重新智能读取"
        }
        require(freshCapture.workId == media.contentId) { "抖音捕获来源与目标作品不一致，请重新智能读取" }
        if (freshCapture.sessionFingerprint.isNotBlank()) {
            require(freshCapture.sessionFingerprint == currentDouyinSessionFingerprint()) {
                "抖音会话已变化，请重新智能读取目标作品"
            }
        }
        require((freshCapture.mediaUrl.isBlank() || SignedMediaUrlExpiry.isFresh(freshCapture.mediaUrl, System.currentTimeMillis())) &&
            freshCapture.imageUrls.all { SignedMediaUrlExpiry.isFresh(it, System.currentTimeMillis()) }) {
            "抖音媒体来源已过期，请重新智能读取目标作品"
        }
        if (media.originalUrl.isDouyinNoteUrl() || freshCapture.pageUrl.isDouyinNoteUrl()) {
            require(freshCapture.mediaUrl.isBlank() && DouyinCaptureStore.isVerifiedImageGallery(
                freshCapture.imageUrls, freshCapture.imageExpectedCount, freshCapture.imageSourceVersion,
            )) { "抖音图文缺少已验证的完整原图列表，请重新智能读取后再下载" }
        }
    }

    override suspend fun resolve(
        media: MediaItem,
        resolution: ResolutionPreset,
    ): ResolvedMedia = withContext(Dispatchers.IO) {
        if (media.platform in setOf(DownloadPlatform.GENERIC, DownloadPlatform.PORNHUB) &&
            media.capturedVideoSourceVersion == 1
        ) {
            return@withContext media.capturedWebVideoAsResolvedMedia(resolution)
        }
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
            validateDouyinCapture(freshCapture, media)
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
            if (error is kotlinx.coroutines.CancellationException || media.platform != DownloadPlatform.DOUYIN) throw error
            val captured = douyinCaptures.find(media.contentId) ?: throw error
            validateDouyinCapture(captured, media)
            return@withContext captured.asResolvedMedia()
        }
        val collectionUrlIdentity = if (
            media.platform == DownloadPlatform.GENERIC && media.sourceKind == DownloadSourceKind.COLLECTION
        ) genericCollectionUrlIdentity(media.originalUrl) else ""
        val matchingLegacyGeneric = media.platform == DownloadPlatform.GENERIC &&
            info.platform == "generic" && info.legacyId.isNotBlank() &&
            info.legacyId == media.contentId && info.webpageUrl == media.originalUrl
        require(info.id == media.contentId || media.contentId == collectionUrlIdentity || matchingLegacyGeneric) {
            "解析结果与目标作品不一致：${info.id} != ${media.contentId}"
        }
        require(!matchingLegacyGeneric || (
            (info.videoDownloadSpec.isBlank() || info.legacyVideoDownloadSpec.isNotBlank()) &&
                (info.audioDownloadSpec.isBlank() || info.legacyAudioDownloadSpec.isNotBlank())
            )) { "无法确认该旧任务的分片身份，请重新智能读取原链接" }
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
            videoDownloadSpec = if (matchingLegacyGeneric) info.legacyVideoDownloadSpec else info.videoDownloadSpec,
            audioDownloadSpec = if (matchingLegacyGeneric) info.legacyAudioDownloadSpec else info.audioDownloadSpec,
            audioHeaders = info.audioHeaders,
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

    private fun genericCollectionUrlIdentity(url: String): String {
        val host = URI(url).host.orEmpty().lowercase()
        require(host.isNotBlank()) { "合集成员链接缺少来源域名" }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$host\n$url".toByteArray(Charsets.UTF_8))
        return "collection:" + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun MediaItem.capturedWebVideoAsResolvedMedia(resolution: ResolutionPreset): ResolvedMedia {
        val capture = GenericWebCapture(
            pageUrl = originalUrl,
            referer = capturedVideoReferer,
            mediaUrl = capturedVideoUrl,
            title = title,
        ).validate()
        require(contentId == capture.mediaId) { "网页播放器来源身份不匹配，请重新智能读取" }
        val extension = URI(capturedVideoUrl).path.orEmpty()
            .substringAfterLast('.', "mp4").lowercase().takeIf {
                it in setOf("mp4", "m4v", "webm", "m3u8")
            } ?: "mp4"
        val headers = mapOf("Referer" to capturedVideoReferer)
        val maxHeight = when (resolution) {
            ResolutionPreset.BEST -> 100000
            ResolutionPreset.UP_TO_1080P -> 1080
            ResolutionPreset.UP_TO_720P -> 720
            ResolutionPreset.UP_TO_360P -> 360
            ResolutionPreset.AUDIO_MP3 -> 100000
        }
        val spec = if (extension == "m3u8") {
            """{"url":${jsonQuote(capturedVideoUrl)},"protocol":"m3u8_native","ext":"mp4","id":${jsonQuote(contentId)},"max_height":$maxHeight,"http_headers":{"Referer":${jsonQuote(capturedVideoReferer)}}}"""
        } else ""
        return ResolvedMedia(
            videoUrl = capturedVideoUrl,
            audioUrl = null,
            videoExtension = if (extension == "m3u8") "mp4" else extension,
            audioExtension = null,
            headers = headers,
            videoDownloadSpec = spec,
        )
    }

    private fun jsonQuote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code))
                    else append(character)
            }
        }
        append('"')
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
                require(captureUserAgent.isBlank() || DouyinCaptureContext.isSafeUserAgent(captureUserAgent)) {
                    "抖音读取环境无效，请重新智能读取"
                }
                put("User-Agent", captureUserAgent.ifBlank { DOUYIN_PAGE_USER_AGENT })
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
