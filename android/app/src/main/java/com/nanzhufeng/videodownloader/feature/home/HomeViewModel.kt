package com.nanzhufeng.videodownloader.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveredMedia
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveryResult
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class HomeViewModel(
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val discovery: SourceDiscoveryEngine,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
) : ViewModel() {
    companion object {
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 45_000L

        fun factory(
            downloads: DownloadRepository,
            settings: SettingsRepository,
            discovery: SourceDiscoveryEngine,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(downloads, settings, discovery) }
        }
    }

    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    fun onInputChanged(value: String) {
        mutableUiState.update { it.copy(input = value, notice = "") }
    }

    fun restoreInputDraft(value: String) {
        if (value.isBlank()) return
        mutableUiState.update { state ->
            if (state.input.isBlank()) state.copy(input = value) else state
        }
    }

    suspend fun smartRead() {
        val input = uiState.value.input.trim()
        if (input.isBlank()) {
            mutableUiState.update { it.copy(notice = "请先粘贴支持平台的视频链接或分享文本") }
            return
        }
        read(input = input, page = 1, append = false)
    }

    suspend fun loadMore() {
        val state = uiState.value
        val page = state.nextPage ?: return
        read(input = state.sourceInput, page = page, append = true)
    }

    private suspend fun read(input: String, page: Int, append: Boolean) {
        mutableUiState.update { it.copy(isReading = true, notice = "正在读取作品…") }
        val result = try {
            withTimeout(readTimeoutMillis) { discovery.read(input, page) }
        } catch (_: TimeoutCancellationException) {
            mutableUiState.update {
                it.copy(isReading = false, notice = "读取超时，请检查网络后重试。")
            }
            return
        }
        when (result) {
            is DiscoveryResult.Failure -> mutableUiState.update {
                it.copy(isReading = false, notice = result.message)
            }

            is DiscoveryResult.Single -> {
                val addedCount = enqueue(listOf(result.item), DownloadSourceKind.SINGLE_VIDEO)
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
                    notice = "正在通过抖音页面读取视频，请稍候…",
                    douyinCaptureUrl = result.sourceUrl,
                )
            }

            is DiscoveryResult.Collection -> {
                val addedCount = enqueue(result.items, DownloadSourceKind.CREATOR)
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

    suspend fun completeDouyinCapture(media: DouyinCapturedMedia?, errorMessage: String = "") {
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
                DiscoveredMedia(
                    sourceUrl = media.pageUrl,
                    platform = DownloadPlatform.DOUYIN,
                    mediaId = media.workId,
                    title = media.title,
                    creator = com.nanzhufeng.videodownloader.domain.discovery.CreatorIdentity(
                        media.creator,
                        "",
                    ),
                    publishedAt = "",
                    thumbnailUrl = media.thumbnailUrl,
                    defaultResolution = com.nanzhufeng.videodownloader.core.model.ResolutionPreset.UP_TO_720P,
                ),
            ),
            DownloadSourceKind.SINGLE_VIDEO,
        )
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
    }

    private suspend fun enqueue(items: List<DiscoveredMedia>, sourceKind: DownloadSourceKind): Int {
        if (items.isEmpty()) return 0
        val defaults = settings.settings.first()
        return downloads.enqueue(
            items = items.map { it.toMediaItem(sourceKind) },
            resolution = defaults.defaultResolution,
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
)

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
)
