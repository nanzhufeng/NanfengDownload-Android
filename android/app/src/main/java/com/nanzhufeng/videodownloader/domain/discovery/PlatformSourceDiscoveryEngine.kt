package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.diagnostics.UserFacingErrorPresenter
import com.nanzhufeng.videodownloader.probe.ClassifiedSource
import com.nanzhufeng.videodownloader.probe.CreatorVideoEntry
import com.nanzhufeng.videodownloader.probe.Platform
import com.nanzhufeng.videodownloader.probe.SourceKind
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class PlatformSourceDiscoveryEngine(
    private val gateway: ProbeDiscoveryGateway,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val blockingExecutor: ExecutorService = createBlockingExecutor(),
) : SourceDiscoveryEngine {
    override suspend fun read(input: String, page: Int): DiscoveryResult =
        suspendCancellableCoroutine { continuation ->
            val future = try {
                blockingExecutor.submit {
                    val result = readBlocking(input, page)
                    if (continuation.isActive) continuation.resume(result)
                }
            } catch (_: RejectedExecutionException) {
                continuation.resume(DiscoveryResult.Failure("读取任务仍在清理，请稍后重试"))
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { future.cancel(true) }
        }

    private fun readBlocking(input: String, page: Int): DiscoveryResult {
        var source: ClassifiedSource? = null
        return try {
            require(page >= 1) { "页码必须从 1 开始" }
            val classified = gateway.classify(input)
            source = classified
            val resolved = classified.resolveIfNeeded()
            source = classified.copy(kind = resolved.kind, url = resolved.url)
            when (resolved.kind) {
                SourceKind.SINGLE_VIDEO -> {
                    // A Douyin note is an image work even when yt-dlp reports
                    // it as a successful gallery.  That gallery contains the
                    // platform's watermarked rendition, so do not let a
                    // nominally-successful generic parse bypass the target
                    // page capture.  The capture supplies every urlList item
                    // before the task is created.
                    if (classified.platform == Platform.DOUYIN && resolved.url.isDouyinNoteUrl()) {
                        resolved.readDouyinGalleryOrCapture()
                    } else {
                        val media = gateway.extractSingle(resolved.url)
                        if (classified.platform == Platform.DOUYIN && media.hasImageGallery()) {
                            resolved.readDouyinGalleryOrCapture()
                        } else {
                            DiscoveryResult.Single(media.toDiscoveredMedia())
                        }
                    }
                }

                SourceKind.CHANNEL_OR_PLAYLIST -> resolved.readCollection(page)
                SourceKind.UNKNOWN_DOUYIN_SHARE,
                SourceKind.UNKNOWN_TIKTOK_SHARE,
                SourceKind.UNKNOWN_BILIBILI_SHARE,
                SourceKind.UNKNOWN_XIAOHONGSHU_SHARE,
                -> error("链接未能解析为单视频或作品列表")
            }
        } catch (error: Exception) {
            if (source?.platform == Platform.DOUYIN && error.isMissingVideoFormats()) {
                DiscoveryResult.DouyinCaptureRequired(source.url)
            } else {
                DiscoveryResult.Failure(DiscoveryFailurePresenter.message(error))
            }
        }
    }

    private fun Throwable.isMissingVideoFormats(): Boolean {
        val evidence = generateSequence(this) { it.cause }
            .mapNotNull(Throwable::message)
            .joinToString("\n")
            .lowercase()
        return evidence.contains("没有找到可下载") ||
            evidence.contains("no video formats") ||
            evidence.contains("no formats found") ||
            evidence.contains("requested format is not available") ||
            // 新版抖音 `/note/` 动态图片页会被 yt-dlp 直接标为
            // Unsupported URL；它不是用户输入格式错误，应进入已受限域名
            // 和同作品 ID 校验保护的 WebView 捕获链路。
            evidence.contains("unsupported url")
    }

    private fun String.isDouyinNoteUrl(): Boolean =
        contains("douyin.com/note/", ignoreCase = true)

    private fun YtDlpMediaInfo.hasImageGallery(): Boolean =
        imageItems.isNotEmpty() || imageUrls.isNotEmpty()

    private fun ClassifiedSource.readDouyinGalleryOrCapture(): DiscoveryResult =
        // The detail endpoint can return a representation selected for an API
        // client rather than the original-image list rendered for this target
        // work.  A `/note/` must therefore enter the WebView target-page
        // capture path every time; it is the only source from which the app
        // accepts React Flight `aweme.detail.images[].urlList`.
        DiscoveryResult.DouyinCaptureRequired(url)

    private fun ClassifiedSource.resolveIfNeeded(): ClassifiedSource {
        if (
            kind !in setOf(
                SourceKind.UNKNOWN_DOUYIN_SHARE,
                SourceKind.UNKNOWN_TIKTOK_SHARE,
                SourceKind.UNKNOWN_BILIBILI_SHARE,
                SourceKind.UNKNOWN_XIAOHONGSHU_SHARE,
            )
        ) {
            return this
        }
        val resolved = gateway.resolve(url)
        return copy(kind = resolved.kind, url = resolved.url.ifBlank { url })
    }

    private fun ClassifiedSource.readCollection(page: Int): DiscoveryResult {
        val start = (page - 1) * pageSize + 1
        val catalog = gateway.extractCreator(url, start, pageSize)
        require(catalog.creatorId.isNotBlank()) { "作品列表缺少博主标识，已拒绝读取以避免混入其他作者" }

        val ownedEntries = catalog.entries
            .asSequence()
            .filter { it.creatorId == catalog.creatorId }
            .distinctBy(CreatorVideoEntry::id)
            .toList()

        return DiscoveryResult.Collection(
            owner = CreatorIdentity(catalog.creator, catalog.creatorId),
            items = ownedEntries.map { it.toDiscoveredMedia(platform) },
            hasMore = catalog.hasMore,
            nextPage = if (catalog.hasMore) page + 1 else null,
        )
    }

    private fun YtDlpMediaInfo.toDiscoveredMedia(): DiscoveredMedia = DiscoveredMedia(
        sourceUrl = webpageUrl,
        platform = platform.toDownloadPlatform(),
        mediaId = id,
        title = title,
        creator = CreatorIdentity(creator, creatorId),
        publishedAt = uploadDate,
        thumbnailUrl = thumbnail,
        defaultResolution = ResolutionPreset.UP_TO_720P,
    )

    private fun CreatorVideoEntry.toDiscoveredMedia(platform: Platform): DiscoveredMedia = DiscoveredMedia(
        sourceUrl = webpageUrl,
        platform = platform.toDownloadPlatform(),
        mediaId = id,
        title = title,
        creator = CreatorIdentity(creator, creatorId),
        publishedAt = uploadDate,
        thumbnailUrl = thumbnail,
        defaultResolution = ResolutionPreset.UP_TO_720P,
    )

    private fun String.toDownloadPlatform(): DownloadPlatform = when (lowercase()) {
        "youtube" -> DownloadPlatform.YOUTUBE
        "bilibili" -> DownloadPlatform.BILIBILI
        "douyin" -> DownloadPlatform.DOUYIN
        "tiktok" -> DownloadPlatform.TIKTOK
        "xiaohongshu", "rednote" -> DownloadPlatform.XIAOHONGSHU
        else -> error("不支持的平台：$this")
    }

    private fun Platform.toDownloadPlatform(): DownloadPlatform = when (this) {
        Platform.YOUTUBE -> DownloadPlatform.YOUTUBE
        Platform.BILIBILI -> DownloadPlatform.BILIBILI
        Platform.DOUYIN -> DownloadPlatform.DOUYIN
        Platform.TIKTOK -> DownloadPlatform.TIKTOK
        Platform.XIAOHONGSHU -> DownloadPlatform.XIAOHONGSHU
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_BLOCKING_READS = 2
        private val threadCounter = AtomicInteger()

        fun createBlockingExecutor(): ExecutorService = Executors.newFixedThreadPool(
            MAX_BLOCKING_READS,
            ThreadFactory { task ->
                Thread(task, "source-discovery-${threadCounter.incrementAndGet()}").apply {
                    isDaemon = true
                }
            },
        )
    }
}

internal object DiscoveryFailurePresenter {
    fun message(error: Throwable): String {
        val evidence = generateSequence(error) { it.cause }
            .mapNotNull(Throwable::message)
            .joinToString("\n")
        val platform = when {
            evidence.contains("Douyin", ignoreCase = true) -> DownloadPlatform.DOUYIN
            evidence.contains("TikTok", ignoreCase = true) -> DownloadPlatform.TIKTOK
            evidence.contains("YouTube", ignoreCase = true) -> DownloadPlatform.YOUTUBE
            evidence.contains("Bilibili", ignoreCase = true) -> DownloadPlatform.BILIBILI
            evidence.contains("Xiaohongshu", ignoreCase = true) ||
                evidence.contains("Rednote", ignoreCase = true) -> DownloadPlatform.XIAOHONGSHU
            else -> null
        }
        return UserFacingErrorPresenter.message(
            rawError = evidence.ifBlank { error.message },
            platform = platform,
            fallbackProblem = "读取作品失败，平台未返回可识别的原因",
            fallbackAction = "请检查链接是否可在平台 App 中打开，再重新复制分享链接并重试",
        )
    }
}
