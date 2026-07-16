package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
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

    private fun readBlocking(input: String, page: Int): DiscoveryResult = try {
            require(page >= 1) { "页码必须从 1 开始" }
            val source = gateway.classify(input)
            val resolved = source.resolveIfNeeded()
            when (resolved.kind) {
                SourceKind.SINGLE_VIDEO -> DiscoveryResult.Single(
                    gateway.extractSingle(resolved.url).toDiscoveredMedia(),
                )

                SourceKind.CHANNEL_OR_PLAYLIST -> resolved.readCollection(page)
                SourceKind.UNKNOWN_DOUYIN_SHARE,
                SourceKind.UNKNOWN_TIKTOK_SHARE,
                -> error("链接未能解析为单视频或作品列表")
            }
    } catch (error: Exception) {
        DiscoveryResult.Failure(error.message ?: "读取作品失败")
    }

    private fun ClassifiedSource.resolveIfNeeded(): ClassifiedSource {
        if (kind !in setOf(SourceKind.UNKNOWN_DOUYIN_SHARE, SourceKind.UNKNOWN_TIKTOK_SHARE)) {
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
        "douyin" -> DownloadPlatform.DOUYIN
        "tiktok" -> DownloadPlatform.TIKTOK
        else -> error("不支持的平台：$this")
    }

    private fun Platform.toDownloadPlatform(): DownloadPlatform = when (this) {
        Platform.YOUTUBE -> DownloadPlatform.YOUTUBE
        Platform.DOUYIN -> DownloadPlatform.DOUYIN
        Platform.TIKTOK -> DownloadPlatform.TIKTOK
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
