package com.nanzhufeng.videodownloader.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveredMedia
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveryResult
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
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

    suspend fun smartRead() {
        val input = uiState.value.input.trim()
        if (input.isBlank()) {
            mutableUiState.update { it.copy(notice = "请先粘贴抖音、YouTube 或 TikTok 链接") }
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
                enqueue(listOf(result.item), DownloadSourceKind.SINGLE_VIDEO)
                mutableUiState.update {
                    it.copy(
                        isReading = false,
                        sourceInput = input,
                        nextPage = null,
                        canLoadMore = false,
                        notice = "已加入 1 个作品，请在队列中确认后开始下载",
                    )
                }
            }

            is DiscoveryResult.Collection -> {
                enqueue(result.items, DownloadSourceKind.CREATOR)
                mutableUiState.update {
                    it.copy(
                        isReading = false,
                        sourceInput = input,
                        nextPage = result.nextPage,
                        canLoadMore = result.hasMore && result.nextPage != null,
                        notice = if (append) {
                            "已加载更多 ${result.items.size} 个作品"
                        } else {
                            "已加入 ${result.items.size} 个作品，请在队列中确认后开始下载"
                        },
                    )
                }
            }
        }
    }

    private suspend fun enqueue(items: List<DiscoveredMedia>, sourceKind: DownloadSourceKind) {
        if (items.isEmpty()) return
        val resolution = settings.settings.first().defaultResolution
        downloads.enqueue(items.map { it.toMediaItem(sourceKind) }, resolution)
    }

}

data class HomeUiState(
    val input: String = "",
    val sourceInput: String = "",
    val isReading: Boolean = false,
    val canLoadMore: Boolean = false,
    val nextPage: Int? = null,
    val notice: String = "",
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
