package com.nanzhufeng.videodownloader.feature.home

import androidx.compose.runtime.Composable
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset

@Composable
fun HomeScreen(
    queue: List<QueuedDownload>,
    input: String,
    onInputChange: (String) -> Unit,
    onSmartRead: () -> Unit,
    isReading: Boolean = false,
    notice: String = "",
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onSelectionChanged: (String, Boolean) -> Unit = { _, _ -> },
    onBulkSelectionChanged: (List<String>, Boolean) -> Unit = { _, _ -> },
    onResolutionChanged: (String, ResolutionPreset) -> Unit = { _, _ -> },
    onAudioSegmentCountChanged: (String, Int) -> Unit = { _, _ -> },
    onDeleteQueued: (String) -> Unit = {},
    onRetryQueued: (String) -> Unit = {},
    onStartDownloads: () -> Unit = {},
    onPauseActive: () -> Unit = {},
    onStopActive: (String) -> Unit = {},
    networkAvailable: Boolean = false,
    defaultResolution: ResolutionPreset = ResolutionPreset.UP_TO_720P,
    completedCount: Int = 0,
    expanded: Boolean = false,
) {
    if (expanded) {
        ExpandedHome(
            queue = queue,
            input = input,
            onInputChange = onInputChange,
            onSmartRead = onSmartRead,
            isReading = isReading,
            notice = notice,
            canLoadMore = canLoadMore,
            onLoadMore = onLoadMore,
            onSelectionChanged = onSelectionChanged,
            onBulkSelectionChanged = onBulkSelectionChanged,
            onResolutionChanged = onResolutionChanged,
            onAudioSegmentCountChanged = onAudioSegmentCountChanged,
            onDeleteQueued = onDeleteQueued,
            onRetryQueued = onRetryQueued,
            onStartDownloads = onStartDownloads,
            onPauseActive = onPauseActive,
            onStopActive = onStopActive,
            networkAvailable = networkAvailable,
            defaultResolution = defaultResolution,
            completedCount = completedCount,
        )
    } else {
        CompactHome(
            queue = queue,
            input = input,
            onInputChange = onInputChange,
            onSmartRead = onSmartRead,
            isReading = isReading,
            notice = notice,
            canLoadMore = canLoadMore,
            onLoadMore = onLoadMore,
            onSelectionChanged = onSelectionChanged,
            onBulkSelectionChanged = onBulkSelectionChanged,
            onResolutionChanged = onResolutionChanged,
            onAudioSegmentCountChanged = onAudioSegmentCountChanged,
            onDeleteQueued = onDeleteQueued,
            onRetryQueued = onRetryQueued,
            onStartDownloads = onStartDownloads,
            onPauseActive = onPauseActive,
            onStopActive = onStopActive,
            networkAvailable = networkAvailable,
            completedCount = completedCount,
        )
    }
}
