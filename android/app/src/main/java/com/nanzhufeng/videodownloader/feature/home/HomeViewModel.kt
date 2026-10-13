package com.nanzhufeng.videodownloader.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveredMedia
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveryResult
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import com.nanzhufeng.videodownloader.domain.download.WebSourceRecoveryPolicy
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMedia
import com.nanzhufeng.videodownloader.probe.GenericWebCapture
import com.nanzhufeng.videodownloader.probe.GenericWebEpisode
import com.nanzhufeng.videodownloader.probe.YinghuaEpisodeParser
import com.nanzhufeng.videodownloader.probe.genericCollectionMediaId
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class HomeViewModel(
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val discovery: SourceDiscoveryEngine,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    private val onDouyinReadCompleted: () -> Unit = {},
    private val onReadCompleted: (Long) -> Unit = {},
) : ViewModel() {
    companion object {
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 45_000L

        fun factory(
            downloads: DownloadRepository,
            settings: SettingsRepository,
            discovery: SourceDiscoveryEngine,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(downloads, settings, discovery, onDouyinReadCompleted = {
                android.util.Log.i("DouyinVideoSource", "target UI result committed")
            }, onReadCompleted = { elapsed ->
                android.util.Log.i("ReadPerformance", "stage=ui_commit elapsedMs=$elapsed")
            }) }
        }
    }

    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var readStartedNanos = 0L
    private var readGeneration = 0L
    private var activeCaptureRequest: String? = null
    private var activeRead: Deferred<DiscoveryResult>? = null

    private fun reportReadIfIdle() {
        if (readStartedNanos != 0L && !uiState.value.isReading) {
            val elapsed = (System.nanoTime() - readStartedNanos) / 1_000_000
            readStartedNanos = 0L
            onReadCompleted(elapsed)
        }
    }

    fun onInputChanged(value: String) {
        if (value == uiState.value.input) return
        readGeneration++
        activeCaptureRequest = null
        activeRead?.cancel()
        readStartedNanos = 0L
        mutableUiState.update {
            it.copy(input = value, notice = "", pendingItems = emptyList(), pendingErrors = emptyList(),
                pendingWebEpisodes = emptyList(), refreshingWebMedia = null, webCaptureUrl = null,
                douyinCaptureUrl = null, isReading = false, sourceInput = "", nextPage = null, canLoadMore = false)
        }
    }

    fun requestWebSourceRefresh(queued: QueuedDownload): Boolean {
        if (!WebSourceRecoveryPolicy.offersRecapture(queued)) return false
        if (uiState.value.webCaptureUrl != null || uiState.value.pendingWebEpisodes.isNotEmpty()) {
            mutableUiState.update { it.copy(notice = "请先完成当前网页播放源读取") }
            return false
        }
        mutableUiState.update {
            it.copy(
                isReading = true,
                refreshingWebMedia = queued.media,
                webCaptureUrl = queued.media.originalUrl,
                notice = "请在原作品页播放目标视频，再点击“读取当前播放源”以更新这条任务",
            )
        }
        return true
    }

    fun setPendingSelected(key: String, selected: Boolean) {
        mutableUiState.update { state ->
            state.copy(pendingItems = state.pendingItems.map {
                if (it.key == key) it.copy(selected = selected) else it
            })
        }
    }

    fun dismissPending() {
        mutableUiState.update { it.copy(pendingItems = emptyList(), pendingErrors = emptyList()) }
    }

    suspend fun confirmPending() {
        val pending = uiState.value.pendingItems.filter(PendingMedia::selected)
        if (pending.isEmpty()) {
            mutableUiState.update { it.copy(notice = "请先选择要加入的作品") }
            return
        }
        val captureNeeded = pending.filter(PendingMedia::needsVisibleCapture)
        val ordinary = pending - captureNeeded.toSet()
        val audio = ordinary.filter { it.media.defaultResolution == com.nanzhufeng.videodownloader.core.model.ResolutionPreset.AUDIO_MP3 }
        val video = ordinary - audio.toSet()
        val added = enqueue(video.map { it.media.toMediaItem(it.sourceKind) }) +
            enqueue(audio.map { it.media.toMediaItem(it.sourceKind) },
                com.nanzhufeng.videodownloader.core.model.ResolutionPreset.AUDIO_MP3)
        val episodes = captureNeeded.map { GenericWebEpisode(it.media.sourceUrl, it.media.title) }
        mutableUiState.update {
            it.copy(pendingItems = emptyList(), pendingErrors = emptyList(),
                pendingWebEpisodes = episodes,
                webCaptureUrl = episodes.firstOrNull()?.url,
                isReading = episodes.isNotEmpty(),
                notice = if (episodes.isNotEmpty()) {
                    "已加入 $added 个作品；所选 ${episodes.size} 集需逐集播放并确认来源，确认后才加入队列"
                } else "已加入 $added 个作品，跳过 ${ordinary.size - added} 个重复作品")
        }
    }

    fun restoreInputDraft(value: String) {
        if (value.isBlank()) return
        mutableUiState.update { state ->
            if (state.input.isBlank()) state.copy(input = value) else state
        }
    }

    suspend fun smartRead() {
        if (uiState.value.isReading) return
        val input = uiState.value.input.trim()
        if (input.isBlank()) {
            mutableUiState.update { it.copy(notice = "请先粘贴支持平台的视频链接或分享文本") }
            return
        }
        readStartedNanos = System.nanoTime()
        try { read(input = input, page = 1, append = false) } finally { reportReadIfIdle() }
    }

    suspend fun loadMore() {
        val state = uiState.value
        if (state.isReading) return
        val page = state.nextPage ?: return
        readStartedNanos = System.nanoTime()
        try { read(input = state.sourceInput, page = page, append = true) } finally { reportReadIfIdle() }
    }

    private suspend fun read(input: String, page: Int, append: Boolean) {
        val generation = ++readGeneration
        mutableUiState.update { it.copy(isReading = true, notice = "正在读取作品…") }
        val result = try {
            coroutineScope {
                val operation = async { withTimeout(readTimeoutMillis) { discovery.read(input, page) } }
                activeRead = operation
                try { operation.await() } catch (error: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    if (generation != readGeneration) null else throw error
                } finally { if (activeRead === operation) activeRead = null }
            }
        } catch (_: TimeoutCancellationException) {
            if (generation == readGeneration) mutableUiState.update {
                it.copy(isReading = false, notice = "读取超时，请检查网络后重试。")
            }
            return
        } catch (error: CancellationException) {
            if (generation == readGeneration) mutableUiState.update { it.copy(isReading = false, notice = "") }
            throw error
        } catch (error: Exception) {
            if (generation == readGeneration) mutableUiState.update {
                it.copy(isReading = false, notice =
                    com.nanzhufeng.videodownloader.domain.discovery.DiscoveryFailurePresenter.message(error))
            }
            return
        }
        if (generation != readGeneration || result == null) return
        when (result) {
            is DiscoveryResult.Batch -> mutableUiState.update {
                it.copy(
                    isReading = false,
                    sourceInput = input,
                    nextPage = null,
                    canLoadMore = false,
                    pendingItems = result.items.map { media ->
                        PendingMedia(media, result.sourceKinds[media.platform to media.mediaId]
                            ?: DownloadSourceKind.SINGLE_VIDEO)
                    },
                    pendingErrors = result.errors,
                    notice = "读取到 ${result.items.size} 个作品，${result.errors.size} 条失败；请查看逐项结果",
                )
            }

            is DiscoveryResult.Failure -> mutableUiState.update {
                it.copy(isReading = false, notice = result.message)
            }

            is DiscoveryResult.Single -> {
                val addedCount = enqueue(
                    listOf(result.item.toMediaItem(DownloadSourceKind.SINGLE_VIDEO)),
                    resolutionOverride = result.item.defaultResolution.takeIf {
                        it == com.nanzhufeng.videodownloader.core.model.ResolutionPreset.AUDIO_MP3
                    },
                )
                if (generation != readGeneration) return
                mutableUiState.update {
                    it.copy(
                        isReading = false,
                        sourceInput = input,
                        nextPage = null,
                        canLoadMore = false,
                        notice = if (addedCount == 1) {
                            "已加入 1 个作品，请在下载列表中确认后开始下载"
                        } else {
                            "该作品已存在于下载列表或历史中，未重复添加"
                        },
                    )
                }
            }

            is DiscoveryResult.DouyinCaptureRequired -> mutableUiState.update {
                it.copy(
                    isReading = true,
                    sourceInput = input,
                    notice = "正在通过抖音页面读取媒体，请稍候…",
                    douyinCaptureUrl = result.sourceUrl,
                )
            }

            is DiscoveryResult.WebCaptureRequired -> mutableUiState.update {
                it.copy(
                    isReading = true,
                    sourceInput = input,
                    webCaptureUrl = result.sourceUrl,
                    notice = "正在打开目标网页播放器，请播放目标视频后确认读取",
                )
            }

            is DiscoveryResult.Collection -> {
                if (result.items.any { it.platform == DownloadPlatform.GENERIC || it.platform == DownloadPlatform.PORNHUB }) {
                    mutableUiState.update { state ->
                        val existing = if (append) state.pendingItems else emptyList()
                        state.copy(
                            isReading = false,
                            sourceInput = input,
                            nextPage = result.nextPage,
                            canLoadMore = result.hasMore && result.nextPage != null,
                            pendingItems = (existing + result.items.map { media ->
                                PendingMedia(media, DownloadSourceKind.COLLECTION)
                            }).distinctBy { pending -> pending.media.platform to pending.media.mediaId },
                            pendingErrors = emptyList(),
                            notice = "已读取 ${result.items.size} 个合集作品，勾选后加入队列",
                        )
                    }
                    return
                }
                val addedCount = enqueue(result.items.map { it.toMediaItem(DownloadSourceKind.CREATOR) })
                if (generation != readGeneration) return
                val skippedCount = (result.items.size - addedCount).coerceAtLeast(0)
                mutableUiState.update {
                    it.copy(
                        isReading = false,
                        sourceInput = input,
                        nextPage = result.nextPage,
                        canLoadMore = result.hasMore && result.nextPage != null,
                        notice = when {
                            addedCount == 0 -> "读取到的作品已存在于下载列表或历史中，未重复添加"
                            append && skippedCount == 0 -> "已加载更多 $addedCount 个作品"
                            append -> "已加载更多 $addedCount 个作品，跳过 $skippedCount 个重复作品"
                            skippedCount == 0 -> "已加入 $addedCount 个作品，请在下载列表中确认后开始下载"
                            else -> "已加入 $addedCount 个作品，跳过 $skippedCount 个重复作品"
                        },
                    )
                }
            }
        }
    }

    fun beginCaptureRequest(): String = java.util.UUID.randomUUID().toString().also {
        activeCaptureRequest = it
    }

    suspend fun completeDouyinCapture(
        media: DouyinCapturedMedia?, errorMessage: String = "", requestId: String? = null,
    ) {
        if (requestId != null && requestId != activeCaptureRequest) return
        activeCaptureRequest = null
        try { completeDouyinCaptureInternal(media, errorMessage) } finally { reportReadIfIdle() }
    }

    private suspend fun completeDouyinCaptureInternal(media: DouyinCapturedMedia?, errorMessage: String) {
        val target = uiState.value.douyinCaptureUrl ?: return
        val generation = readGeneration
        if (media != null) {
            val expectedId = com.nanzhufeng.videodownloader.probe.DouyinCaptureStore.extractWorkId(target)
            val actualId = com.nanzhufeng.videodownloader.probe.DouyinCaptureStore.extractWorkId(media.pageUrl)
            if (actualId != media.workId || (expectedId != null && expectedId != media.workId)) {
                mutableUiState.update { it.copy(isReading = false, douyinCaptureUrl = null,
                    notice = "读取结果与当前抖音作品不一致，未创建任务，请重新读取") }
                return
            }
        }
        if (media == null) {
            mutableUiState.update {
                it.copy(
                    isReading = false,
                    douyinCaptureUrl = null,
                    notice = errorMessage.ifBlank {
                        "抖音页面没有返回可下载视频或图文图片。解决办法：请确认作品可播放，到设置中重新登录抖音后重试。"
                    },
                )
            }
            return
        }
        val addedCount = enqueue(
            listOf(
                MediaItem(
                    mediaKey = "",
                    platform = DownloadPlatform.DOUYIN,
                    contentId = media.workId,
                    originalUrl = media.pageUrl,
                    sourceKind = DownloadSourceKind.SINGLE_VIDEO,
                    title = media.title,
                    creator = media.creator,
                    creatorId = "",
                    publishDate = "",
                    thumbnailUrl = media.thumbnailUrl,
                    capturedImageUrls = media.imageUrls,
                    capturedImageExpectedCount = media.imageExpectedCount,
                    capturedImageSourceVersion = media.imageSourceVersion,
                ),
            ),
        )
        if (generation != readGeneration) return
        mutableUiState.update {
            it.copy(
                isReading = false,
                douyinCaptureUrl = null,
                nextPage = null,
                canLoadMore = false,
                notice = if (addedCount == 1) {
                    if (media.imageUrls.isNotEmpty()) "已读取抖音图文并加入 1 个图集任务，请开始下载"
                    else "已通过抖音页面读取并加入 1 个作品，请开始下载"
                } else {
                    "该作品已存在于下载列表或历史中，未重复添加"
                },
            )
        }
        onDouyinReadCompleted()
    }

    suspend fun completeWebCapture(
        capture: GenericWebCapture?,
        episodes: List<GenericWebEpisode> = emptyList(),
        pageUrl: String = "",
        errorMessage: String = "",
        requestId: String? = null,
    ) {
        if (requestId != null && requestId != activeCaptureRequest) return
        activeCaptureRequest = null
        try { completeWebCaptureInternal(capture, episodes, pageUrl, errorMessage) } finally { reportReadIfIdle() }
    }

    private suspend fun completeWebCaptureInternal(
        capture: GenericWebCapture?, episodes: List<GenericWebEpisode>, pageUrl: String, errorMessage: String,
    ) {
        val refreshTarget = uiState.value.refreshingWebMedia
        if (refreshTarget != null) {
            val validated = runCatching { capture?.validate() }.getOrNull()
            if (validated == null || validated.platform != refreshTarget.platform ||
                validated.mediaId != refreshTarget.contentId
            ) {
                mutableUiState.update {
                    it.copy(isReading = false, webCaptureUrl = null, refreshingWebMedia = null,
                        notice = if (capture == null) {
                            errorMessage.ifBlank { "未确认新的播放源，原任务保持失败，未创建新任务" }
                        } else "播放器结果不属于原任务作品，未更新播放源或创建任务")
                }
                return
            }
            val added = enqueueWebCapture(validated, refreshTarget.sourceKind)
            mutableUiState.update {
                it.copy(isReading = false, webCaptureUrl = null, refreshingWebMedia = null,
                    notice = if (added > 0) "已更新原任务的播放源，请开始下载"
                        else "已确认同一作品的播放源；请查看当前任务状态")
            }
            return
        }
        if (episodes.isNotEmpty()) {
            val host = URI(pageUrl).host.orEmpty()
            val pending = episodes.map { episode ->
                PendingMedia(
                    DiscoveredMedia(
                        sourceUrl = episode.url,
                        platform = DownloadPlatform.GENERIC,
                        mediaId = genericCollectionMediaId(episode.url),
                        title = episode.title,
                        creator = com.nanzhufeng.videodownloader.domain.discovery.CreatorIdentity(host, pageUrl),
                        publishedAt = "",
                        thumbnailUrl = "",
                        defaultResolution = com.nanzhufeng.videodownloader.core.model.ResolutionPreset.UP_TO_720P,
                    ),
                    DownloadSourceKind.COLLECTION,
                    needsVisibleCapture = true,
                )
            }
            mutableUiState.update {
                it.copy(isReading = false, webCaptureUrl = null,
                    pendingItems = pending, notice = "读取到 ${pending.size} 集，勾选后逐集确认播放器")
            }
            return
        }
        val captureQueue = uiState.value.pendingWebEpisodes
        if (captureQueue.isNotEmpty()) {
            val current = captureQueue.first()
            val validated = runCatching { capture?.validate() }.getOrNull()
            if (validated == null || validated.pageUrl != current.url) {
                mutableUiState.update {
                    it.copy(isReading = false, webCaptureUrl = null, pendingWebEpisodes = emptyList(),
                        notice = if (capture == null) {
                            "已停止逐集读取；未确认播放源的剧集没有加入下载队列"
                        } else "播放器结果不属于当前剧集，已停止逐集读取，未创建任务")
                }
                return
            }
            val added = enqueueWebCapture(validated)
            val remaining = captureQueue.drop(1)
            mutableUiState.update {
                it.copy(isReading = remaining.isNotEmpty(), webCaptureUrl = remaining.firstOrNull()?.url,
                    pendingWebEpisodes = remaining,
                    notice = if (remaining.isNotEmpty()) {
                        "已确认本集并加入 $added 个任务；请继续播放下一集（还剩 ${remaining.size} 集）"
                    } else "所选剧集已逐集确认；最后一集加入 $added 个任务，重复作品未再次加入")
            }
            return
        }
        if (capture == null) {
            mutableUiState.update {
                it.copy(isReading = false, webCaptureUrl = null,
                    notice = errorMessage.ifBlank { "未读取到正在播放的目标视频，未创建任务" })
            }
            return
        }
        if (uiState.value.webCaptureUrl == null) {
            mutableUiState.update {
                it.copy(isReading = false, notice = "网页读取上下文已变化，未创建任务")
            }
            return
        }
        val validated = capture.validate()
        val added = enqueueWebCapture(validated)
        mutableUiState.update {
            it.copy(isReading = false, webCaptureUrl = null,
                notice = if (added == 1) "已加入目标网页视频，请在下载列表确认后开始下载"
                    else "该网页视频已在下载列表或历史中，已更新来源信息")
        }
    }

    private suspend fun enqueueWebCapture(
        validated: GenericWebCapture,
        sourceKindOverride: DownloadSourceKind? = null,
    ): Int = enqueue(listOf(MediaItem(
            mediaKey = "", platform = validated.platform,
            contentId = validated.mediaId, originalUrl = validated.pageUrl,
            sourceKind = sourceKindOverride ?: if (YinghuaEpisodeParser.isEpisodeUrl(validated.pageUrl)) {
                DownloadSourceKind.COLLECTION
            } else DownloadSourceKind.SINGLE_VIDEO,
            title = validated.title, creator = URI(validated.pageUrl).host.orEmpty(),
            creatorId = "", publishDate = "", thumbnailUrl = "",
            capturedVideoUrl = validated.mediaUrl,
            capturedVideoReferer = validated.referer,
            capturedVideoSourceVersion = 1,
        )))

    private suspend fun enqueue(
        items: List<MediaItem>,
        resolutionOverride: com.nanzhufeng.videodownloader.core.model.ResolutionPreset? = null,
    ): Int {
        if (items.isEmpty()) return 0
        val generation = readGeneration
        val defaults = settings.settings.first()
        currentCoroutineContext().ensureActive()
        if (generation != readGeneration) return 0
        return downloads.enqueue(
            items = items,
            resolution = resolutionOverride ?: defaults.defaultResolution,
            saveTreeUri = defaults.customTreeUri,
            fileNameRule = defaults.fileNameRule,
        ).size
    }

}

data class HomeUiState(
    val input: String = "",
    val sourceInput: String = "",
    val isReading: Boolean = false,
    val canLoadMore: Boolean = false,
    val nextPage: Int? = null,
    val notice: String = "",
    val douyinCaptureUrl: String? = null,
    val webCaptureUrl: String? = null,
    val pendingItems: List<PendingMedia> = emptyList(),
    val pendingErrors: List<Pair<String, String>> = emptyList(),
    val pendingWebEpisodes: List<GenericWebEpisode> = emptyList(),
    val refreshingWebMedia: MediaItem? = null,
)

data class PendingMedia(
    val media: DiscoveredMedia,
    val sourceKind: DownloadSourceKind,
    val selected: Boolean = true,
    val needsVisibleCapture: Boolean = false,
) {
    val key: String get() = "${media.platform}:${media.mediaId}"
}

private fun DiscoveredMedia.toMediaItem(sourceKind: DownloadSourceKind) = MediaItem(
    mediaKey = "",
    platform = platform,
    contentId = mediaId,
    originalUrl = sourceUrl,
    sourceKind = sourceKind,
    title = title,
    creator = creator.name,
    creatorId = creator.id,
    publishDate = publishedAt,
    thumbnailUrl = thumbnailUrl,
    capturedImageUrls = capturedImageUrls,
    capturedImageExpectedCount = capturedImageExpectedCount,
    capturedImageSourceVersion = capturedImageSourceVersion,
    legacyContentId = legacyMediaId,
)
