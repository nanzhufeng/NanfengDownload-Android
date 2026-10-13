package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.diagnostics.UserFacingErrorPresenter
import com.nanzhufeng.videodownloader.probe.ClassifiedSource
import com.nanzhufeng.videodownloader.probe.CreatorVideoEntry
import com.nanzhufeng.videodownloader.probe.Platform
import com.nanzhufeng.videodownloader.probe.SourceKind
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import com.nanzhufeng.videodownloader.probe.UrlClassifier
import java.net.URI
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
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SourceDiscoveryEngine {
    override fun prepare() = gateway.prepare()
    private data class CachedResolution(val kind: SourceKind, val url: String, val atMillis: Long)
    private data class Resolution(val source: ClassifiedSource, val media: YtDlpMediaInfo? = null)
    private val douyinResolutions = LinkedHashMap<String, CachedResolution>(32, 0.75f, true)
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
        val urls = if (page == 1) UrlClassifier.extractUrls(input) else emptyList()
        if (urls.size > 20) return DiscoveryResult.Failure("一次最多读取 20 条不同链接，请分批粘贴")
        if (urls.size > 1) {
            val items = mutableListOf<DiscoveredMedia>()
            val errors = mutableListOf<Pair<String, String>>()
            val sourceKinds = mutableMapOf<Pair<DownloadPlatform, String>, DownloadSourceKind>()
            urls.forEach { url ->
                when (val result = readBlocking(url, 1)) {
                    is DiscoveryResult.Single -> items += result.item
                    is DiscoveryResult.Collection -> {
                        items += result.items
                        result.items.forEach { sourceKinds[it.platform to it.mediaId] = DownloadSourceKind.COLLECTION }
                        if (result.hasMore) errors += url to "该合集还有更多内容，请单独粘贴合集链接继续加载"
                    }
                    is DiscoveryResult.Failure -> errors += url to result.message
                    is DiscoveryResult.DouyinCaptureRequired ->
                        errors += url to "该抖音作品需单独读取网页，请单独粘贴这条链接"
                    is DiscoveryResult.WebCaptureRequired ->
                        errors += url to "该动态网页需单独打开播放器，请单独粘贴这条链接"
                    is DiscoveryResult.Batch -> error("嵌套网址批次无效")
                }
            }
            return DiscoveryResult.Batch(items.distinctBy { it.platform to it.mediaId }, errors, sourceKinds)
        }
        var source: ClassifiedSource? = null
        var webCaptureCandidate = ""
        return try {
            require(page >= 1) { "页码必须从 1 开始" }
            val classified = gateway.classify(input)
            source = classified
            if (classified.platform == Platform.GENERIC && classified.kind == SourceKind.UNKNOWN_WEB_PAGE) {
                webCaptureCandidate = classified.url
            }
            val resolution = classified.resolveIfNeeded()
            val resolved = resolution.source
            source = classified.copy(kind = resolved.kind, url = resolved.url)
            when (resolved.kind) {
                SourceKind.SINGLE_VIDEO -> {
                    // A target Douyin video or note uses the scoped page
                    // capture. Generic extraction can reject valid sessions
                    // before opening the page or select watermarked images.
                    if (classified.platform == Platform.DOUYIN && resolved.url.isDouyinWorkUrl()) {
                        resolved.readDouyinTargetPageCapture()
                    } else {
                        val media = resolution.media ?: gateway.extractSingle(resolved.url)
                        if (classified.platform == Platform.DOUYIN && media.hasImageGallery()) {
                            resolved.readDouyinTargetPageCapture()
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
                SourceKind.UNKNOWN_WEB_PAGE,
                -> error("链接未能解析为单视频或作品列表")
            }
        } catch (error: Exception) {
            if (source?.platform == Platform.DOUYIN &&
                source.kind in setOf(SourceKind.SINGLE_VIDEO, SourceKind.UNKNOWN_DOUYIN_SHARE) && error.isMissingVideoFormats()) {
                DiscoveryResult.DouyinCaptureRequired(source.url)
            } else if (source?.platform == Platform.PORNHUB && source.kind == SourceKind.SINGLE_VIDEO &&
                error.isPornhubPageCaptureCandidate() && !error.isRestrictedWebMedia()
            ) {
                DiscoveryResult.WebCaptureRequired(source.url)
            } else if (webCaptureCandidate.isNotBlank() && !error.isRestrictedWebMedia()) {
                DiscoveryResult.WebCaptureRequired(webCaptureCandidate)
            } else {
                DiscoveryResult.Failure(DiscoveryFailurePresenter.message(error))
            }
        }
    }

    private fun Throwable.isPornhubPageCaptureCandidate(): Boolean {
        val evidence = generateSequence(this) { it.cause }
            .mapNotNull(Throwable::message).joinToString("\n").lowercase()
        return listOf("http error 410", "http error 404", "video unavailable", "no video formats",
            "no formats found", "requested format is not available").any(evidence::contains)
    }

    private fun Throwable.isRestrictedWebMedia(): Boolean {
        val evidence = generateSequence(this) { it.cause }
            .mapNotNull(Throwable::message).joinToString("\n").lowercase()
        return listOf("drm", "付费", "私密", "会员", "access denied", "forbidden", "直播", "is live")
            .any(evidence::contains)
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

    private fun String.isDouyinWorkUrl(): Boolean {
        val uri = runCatching { URI(this) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host.orEmpty().lowercase()
        if (host != "douyin.com" && !host.endsWith(".douyin.com") &&
            host != "iesdouyin.com" && !host.endsWith(".iesdouyin.com")
        ) return false
        return Regex("^/(?:share/)?(?:video|note)/\\d+/?$").matches(uri.path.orEmpty())
    }

    private fun YtDlpMediaInfo.hasImageGallery(): Boolean =
        imageItems.isNotEmpty() || imageUrls.isNotEmpty()

    private fun ClassifiedSource.readDouyinTargetPageCapture(): DiscoveryResult =
        // The target page owns both video stream capture and complete note
        // galleries. API/client responses cannot replace its source identity.
        DiscoveryResult.DouyinCaptureRequired(url)

    private fun ClassifiedSource.resolveIfNeeded(): Resolution {
        if (
            kind !in setOf(
                SourceKind.UNKNOWN_DOUYIN_SHARE,
                SourceKind.UNKNOWN_TIKTOK_SHARE,
                SourceKind.UNKNOWN_BILIBILI_SHARE,
                SourceKind.UNKNOWN_XIAOHONGSHU_SHARE,
                SourceKind.UNKNOWN_WEB_PAGE,
            )
        ) {
            return Resolution(this)
        }
        val canCache = platform == Platform.DOUYIN && kind == SourceKind.UNKNOWN_DOUYIN_SHARE &&
            runCatching { URI(url).host.equals("v.douyin.com", true) }.getOrDefault(false)
        if (canCache) {
            val cached = synchronized(douyinResolutions) { douyinResolutions[url] }
            if (cached != null && nowMillis() - cached.atMillis in 0L..120_000L) {
                return Resolution(copy(kind = cached.kind, url = cached.url))
            }
        }
        val resolved = gateway.resolve(url)
        if (canCache && resolved.kind == SourceKind.SINGLE_VIDEO && resolved.url.isDouyinWorkUrl()) {
            synchronized(douyinResolutions) {
                douyinResolutions[url] = CachedResolution(resolved.kind, resolved.url, nowMillis())
                if (douyinResolutions.size > 32) douyinResolutions.remove(douyinResolutions.keys.first())
            }
        }
        val media = resolved.media?.takeIf {
            platform == Platform.GENERIC && resolved.kind == SourceKind.SINGLE_VIDEO &&
                resolved.url == url && it.webpageUrl == url && it.platform == "generic" &&
                it.id.isNotBlank() && it.videoUrl.isNotBlank()
        }
        return Resolution(copy(kind = resolved.kind, url = resolved.url.ifBlank { url }), media)
    }

    private fun ClassifiedSource.readCollection(page: Int): DiscoveryResult {
        val start = (page - 1) * pageSize + 1
        val catalog = if (platform == Platform.GENERIC || platform == Platform.PORNHUB) {
            gateway.extractWebCollection(url, start, pageSize)
        } else {
            gateway.extractCreator(url, start, pageSize)
        }
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
        defaultResolution = if (audioOnly) ResolutionPreset.AUDIO_MP3 else ResolutionPreset.UP_TO_720P,
        legacyMediaId = legacyId,
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
        "pornhub" -> DownloadPlatform.PORNHUB
        "generic" -> DownloadPlatform.GENERIC
        else -> error("不支持的平台：$this")
    }

    private fun Platform.toDownloadPlatform(): DownloadPlatform = when (this) {
        Platform.YOUTUBE -> DownloadPlatform.YOUTUBE
        Platform.BILIBILI -> DownloadPlatform.BILIBILI
        Platform.DOUYIN -> DownloadPlatform.DOUYIN
        Platform.TIKTOK -> DownloadPlatform.TIKTOK
        Platform.XIAOHONGSHU -> DownloadPlatform.XIAOHONGSHU
        Platform.PORNHUB -> DownloadPlatform.PORNHUB
        Platform.GENERIC -> DownloadPlatform.GENERIC
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
        generateSequence(error) { it.cause }
            .filterIsInstance<com.nanzhufeng.videodownloader.probe.DouyinShortLinkReadException>()
            .firstOrNull()?.let { return requireNotNull(it.message) }
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
