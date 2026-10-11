package com.nanzhufeng.videodownloader.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.feature.settings.rememberIslandAwareDownloadStart
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset

@Composable
fun HomeScreen(
    queue: List<QueuedDownload>,
    input: String,
    onInputChange: (String) -> Unit,
    onSmartRead: () -> Unit,
    pendingItems: List<PendingMedia> = emptyList(),
    pendingErrors: List<Pair<String, String>> = emptyList(),
    onPendingSelected: (String, Boolean) -> Unit = { _, _ -> },
    onConfirmPending: () -> Unit = {},
    onDismissPending: () -> Unit = {},
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
    val startDownloads = rememberIslandAwareDownloadStart(onStartDownloads)
    if (pendingItems.isNotEmpty() || pendingErrors.isNotEmpty()) {
        val needsVisibleCapture = pendingItems.any(PendingMedia::needsVisibleCapture)
        AlertDialog(
            onDismissRequest = onDismissPending,
            title = { Text(if (needsVisibleCapture) "选择要读取的剧集" else "选择要加入的作品") },
            text = {
                Column {
                    Text(if (needsVisibleCapture) {
                        "共 ${pendingItems.size} 集；所选项需逐集打开播放器。确认播放源后才加入下载队列"
                    } else "共 ${pendingItems.size} 个；只会将勾选作品加入下载队列")
                    if (isReading) Text("正在加载更多…")
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(pendingItems, key = PendingMedia::key) { pending ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = pending.selected,
                                    onCheckedChange = { onPendingSelected(pending.key, it) },
                                )
                                Text(pending.media.title, modifier = Modifier.weight(1f))
                            }
                        }
                        if (pendingErrors.isNotEmpty()) {
                            item { Text("未读取的链接（${pendingErrors.size} 条）") }
                            itemsIndexed(pendingErrors, key = { index, _ -> "failure-$index" }) { index, failure ->
                                Text("${index + 1}. ${failure.first.substringBefore('?').take(120)}\n${failure.second}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (canLoadMore) TextButton(onClick = onLoadMore, enabled = !isReading) {
                        Text("加载更多")
                    }
                    TextButton(onClick = onConfirmPending,
                        enabled = !isReading && pendingItems.any(PendingMedia::selected)) {
                        Text(if (needsVisibleCapture) {
                            "读取所选 ${pendingItems.count(PendingMedia::selected)} 集"
                        } else "加入所选 ${pendingItems.count(PendingMedia::selected)} 项")
                    }
                }
            },
            dismissButton = { TextButton(onClick = onDismissPending) { Text("取消") } },
        )
    }
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
            onStartDownloads = startDownloads,
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
            onStartDownloads = startDownloads,
            onPauseActive = onPauseActive,
            onStopActive = onStopActive,
            networkAvailable = networkAvailable,
            completedCount = completedCount,
        )
    }
}
