package com.nanzhufeng.videodownloader.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nanzhufeng.videodownloader.R
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.ui.AppCardTone
import com.nanzhufeng.videodownloader.core.ui.FailureRed
import com.nanzhufeng.videodownloader.core.ui.HermesOrange
import com.nanzhufeng.videodownloader.core.ui.SuccessGreen
import com.nanzhufeng.videodownloader.core.ui.WaitingYellow
import com.nanzhufeng.videodownloader.core.ui.WarmOrange
import com.nanzhufeng.videodownloader.core.ui.WorkbenchCard

@Composable
internal fun CompactHome(
    queue: List<QueuedDownload>,
    input: String,
    onInputChange: (String) -> Unit,
    onSmartRead: () -> Unit,
    isReading: Boolean,
    notice: String,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onSelectionChanged: (String, Boolean) -> Unit,
    onBulkSelectionChanged: (List<String>, Boolean) -> Unit,
    onResolutionChanged: (String, ResolutionPreset) -> Unit,
    onStartDownloads: () -> Unit,
    onPauseActive: () -> Unit,
    onStopActive: (String) -> Unit,
    networkAvailable: Boolean,
) {
    val active = queue.firstOrNull { it.task.status == DownloadTaskStatus.DOWNLOADING }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home-screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HomeHeader(networkAvailable, Modifier.testTag("home-compact-run-status")) }
        item {
            if (active != null) {
                ActiveDownloadCard(active, onPauseActive, onStopActive)
            } else {
                EmptyActiveCard(compact = true)
            }
        }
        item {
            QueuePanel(
                queue = queue,
                onSelectionChanged = onSelectionChanged,
                onBulkSelectionChanged = onBulkSelectionChanged,
                onResolutionChanged = onResolutionChanged,
                onStartDownloads = onStartDownloads,
                expandedLayout = false,
            )
        }
        item { TotalProgressCard(queue) }
        item {
            ReadEntryCard(
                input = input,
                onInputChange = onInputChange,
                onSmartRead = onSmartRead,
                isReading = isReading,
                notice = notice,
            )
        }
        if (canLoadMore) {
            item {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !isReading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("加载更多作品")
                }
            }
        }
    }
}

@Composable
internal fun ExpandedHome(
    queue: List<QueuedDownload>,
    input: String,
    onInputChange: (String) -> Unit,
    onSmartRead: () -> Unit,
    isReading: Boolean,
    notice: String,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onSelectionChanged: (String, Boolean) -> Unit,
    onBulkSelectionChanged: (List<String>, Boolean) -> Unit,
    onResolutionChanged: (String, ResolutionPreset) -> Unit,
    onStartDownloads: () -> Unit,
    onPauseActive: () -> Unit,
    onStopActive: (String) -> Unit,
    networkAvailable: Boolean,
    defaultResolution: ResolutionPreset,
) {
    val active = queue.firstOrNull { it.task.status == DownloadTaskStatus.DOWNLOADING }

    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("formal-expanded-workbench"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeHeader(networkAvailable)
            QueuePanel(
                queue = queue,
                onSelectionChanged = onSelectionChanged,
                onBulkSelectionChanged = onBulkSelectionChanged,
                onResolutionChanged = onResolutionChanged,
                onStartDownloads = onStartDownloads,
                expandedLayout = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("home-main-queue-workspace"),
            )
        }
        Column(
            modifier = Modifier
                .weight(0.82f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.testTag("home-side-add-task")) {
                ReadEntryCard(input, onInputChange, onSmartRead, isReading, notice)
            }
            QualityAndProgressCard(defaultResolution, queue)
            if (active != null) {
                ActiveDownloadCard(active, onPauseActive, onStopActive)
            } else {
                EmptyActiveCard(compact = true)
            }
            if (canLoadMore) {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !isReading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("加载更多作品") }
            }
        }
    }
}

@Composable
private fun HomeHeader(networkAvailable: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.nanzhufeng_app_icon),
            contentDescription = "南烛枫视频下载器",
            modifier = Modifier.size(48.dp),
        )
        Text(
            "南烛枫视频下载器",
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (networkAvailable) SuccessGreen else HermesOrange, CircleShape),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            if (networkAvailable) "网络良好" else "等待网络",
            color = if (networkAvailable) SuccessGreen else HermesOrange,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActiveDownloadCard(
    queued: QueuedDownload,
    onPause: () -> Unit,
    onStop: (String) -> Unit,
) {
    val progress = queued.progress()
    WorkbenchCard(tone = AppCardTone.MINT) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("正在下载 1", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onPause) { Text("全部暂停") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(queued, Modifier.size(width = 132.dp, height = 82.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(queued.media.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(queued.media.creator, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(resolutionLabel(queued.task.resolution), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatSpeed(queued.task.speedBytesPerSecond),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${(progress * 100).toInt()}% · 剩余 ${formatDuration(queued.task.remainingSeconds)} · ${formatBytes(queued.task.downloadedBytes)} / ${formatBytes(queued.task.totalBytes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Outlined.Pause, contentDescription = "暂停下载", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onStop(queued.task.taskId) }) {
                        Icon(Icons.Filled.Stop, contentDescription = "停止下载", tint = FailureRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyActiveCard(compact: Boolean = false) {
    WorkbenchCard(tone = AppCardTone.MINT) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("当前没有下载任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!compact) {
                Text("智能读取并保留勾选后，任务会在这里显示实时进度。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<QueuedDownload>,
    onSelectionChanged: (String, Boolean) -> Unit,
    onBulkSelectionChanged: (List<String>, Boolean) -> Unit,
    onResolutionChanged: (String, ResolutionPreset) -> Unit,
    onStartDownloads: () -> Unit,
    expandedLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(QueueTab.QUEUE) }
    var batchMode by rememberSaveable { mutableStateOf(false) }
    val filtered = queue.filter { it.task.status.belongsTo(selectedTab) }
    val eligibleIds = filtered.filterNot { it.task.status == DownloadTaskStatus.DOWNLOADING }.map { it.task.taskId }
    val allSelected = eligibleIds.isNotEmpty() && filtered.filterNot { it.task.status == DownloadTaskStatus.DOWNLOADING }.all { it.task.selected }

    WorkbenchCard(
        modifier = modifier.testTag("formal-queue-tabs"),
        tone = AppCardTone.NEUTRAL,
    ) {
        Column(
            modifier = if (expandedLayout) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                QueueTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text("${tab.label}(${queue.count { it.task.status.belongsTo(tab) }})") },
                    )
                }
            }
            if (batchMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { onBulkSelectionChanged(eligibleIds, it) },
                        enabled = eligibleIds.isNotEmpty(),
                    )
                    Text(if (allSelected) "取消当前页选择" else "选择当前页")
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { batchMode = false }) { Text("完成") }
                }
                HorizontalDivider()
            }
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("暂无${selectedTab.label}作品", fontWeight = FontWeight.SemiBold)
                    Text("智能读取后，符合条件的作品会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = if (expandedLayout) Modifier.weight(1f) else Modifier.heightIn(max = 430.dp),
                ) {
                    itemsIndexed(filtered, key = { _, item -> item.task.taskId }) { index, queued ->
                        QueueRow(
                            queued = queued,
                            sequence = index + 1,
                            batchMode = batchMode,
                            onSelectionChanged = onSelectionChanged,
                            onResolutionChanged = onResolutionChanged,
                        )
                        if (index < filtered.lastIndex) HorizontalDivider()
                    }
                }
            }
            if (queue.isNotEmpty()) {
                HorizontalDivider()
                if (batchMode) {
                    Button(
                        onClick = {
                            batchMode = false
                            onStartDownloads()
                        },
                        enabled = queue.any { it.task.selected && it.task.status in setOf(
                            DownloadTaskStatus.WAITING,
                            DownloadTaskStatus.PAUSED,
                            DownloadTaskStatus.WAITING_NETWORK,
                        ) },
                        colors = ButtonDefaults.buttonColors(containerColor = HermesOrange),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("开始下载已选作品")
                    }
                } else {
                    TextButton(
                        onClick = { batchMode = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("批量管理")
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    queued: QueuedDownload,
    sequence: Int,
    batchMode: Boolean,
    onSelectionChanged: (String, Boolean) -> Unit,
    onResolutionChanged: (String, ResolutionPreset) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (batchMode) {
            Checkbox(
                checked = queued.task.selected,
                onCheckedChange = { onSelectionChanged(queued.task.taskId, it) },
                enabled = queued.task.status != DownloadTaskStatus.DOWNLOADING,
            )
        } else {
            Text(sequence.toString(), modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusMarker(queued.task.status)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(queued.media.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(queued.media.creator, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (queued.task.status == DownloadTaskStatus.WAITING_NETWORK || queued.task.status == DownloadTaskStatus.SKIPPED) {
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(queued.task.status)
                Text(resolutionLabel(queued.task.resolution), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ResolutionMenu(
                resolution = queued.task.resolution,
                enabled = queued.task.status == DownloadTaskStatus.WAITING,
                onSelected = { onResolutionChanged(queued.task.taskId, it) },
            )
        }
        if (queued.task.totalBytes > 0L) {
            Text(formatBytes(queued.task.totalBytes), modifier = Modifier.width(62.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResolutionMenu(
    resolution: ResolutionPreset,
    enabled: Boolean,
    onSelected: (ResolutionPreset) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(40.dp),
        ) {
            Text(resolutionLabel(resolution))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ResolutionPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(resolutionLabel(preset)) },
                    onClick = {
                        expanded = false
                        onSelected(preset)
                    },
                )
            }
        }
    }
}

@Composable
private fun TotalProgressCard(queue: List<QueuedDownload>) {
    val downloading = queue.count { it.task.status == DownloadTaskStatus.DOWNLOADING }
    val waiting = queue.count { it.task.status in setOf(DownloadTaskStatus.WAITING, DownloadTaskStatus.PARSING, DownloadTaskStatus.VALIDATING) }
    val downloaded = queue.sumOf { it.task.downloadedBytes.coerceAtLeast(0L) }
    val total = queue.sumOf { it.task.totalBytes.coerceAtLeast(0L) }
    val progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f

    WorkbenchCard(
        modifier = Modifier.testTag("formal-total-progress"),
        tone = AppCardTone.PURPLE,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("总进度", fontWeight = FontWeight.Bold)
                Text("${downloading}个下载中 · ${waiting}个排队中", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.width(138.dp), horizontalAlignment = Alignment.End) {
                Text(
                    "${(progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("${formatBytes(downloaded)} / ${formatBytes(total)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun QualityAndProgressCard(
    defaultResolution: ResolutionPreset,
    queue: List<QueuedDownload>,
) {
    val activeCount = queue.count { it.task.status == DownloadTaskStatus.DOWNLOADING }
    val waitingCount = queue.count {
        it.task.status in setOf(
            DownloadTaskStatus.WAITING,
            DownloadTaskStatus.PARSING,
            DownloadTaskStatus.VALIDATING,
        )
    }
    WorkbenchCard(
        modifier = Modifier.testTag("home-side-quality-progress"),
        tone = AppCardTone.PURPLE,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("默认质量", fontWeight = FontWeight.Bold)
            Text(
                resolutionLabel(defaultResolution),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider()
            Text("任务进度", fontWeight = FontWeight.Bold)
            Text(
                if (queue.isEmpty()) "暂无待处理任务" else "${activeCount} 个下载中 · ${waitingCount} 个待处理",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadEntryCard(
    input: String,
    onInputChange: (String) -> Unit,
    onSmartRead: () -> Unit,
    isReading: Boolean,
    notice: String,
) {
    WorkbenchCard(
        modifier = Modifier.testTag("formal-read-entry"),
        tone = AppCardTone.ORANGE,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth().testTag("home-input"),
                minLines = 3,
                maxLines = 5,
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                label = { Text("抖音、YouTube 或 TikTok 分享文本") },
                supportingText = { Text("${input.length}/1000") },
            )
            Button(
                onClick = onSmartRead,
                enabled = input.isNotBlank() && input.length <= 1000 && !isReading,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("smart-read"),
                colors = ButtonDefaults.buttonColors(containerColor = HermesOrange),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isReading) "正在读取" else "智能读取")
            }
            if (notice.isNotBlank()) {
                Text(notice, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Thumbnail(queued: QueuedDownload, modifier: Modifier) {
    if (queued.media.thumbnailUrl.isBlank()) {
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    } else {
        AsyncImage(
            model = queued.media.thumbnailUrl,
            contentDescription = queued.media.title,
            modifier = modifier.clip(MaterialTheme.shapes.medium),
        )
    }
}

@Composable
private fun StatusMarker(status: DownloadTaskStatus) {
    Box(modifier = Modifier.size(10.dp).background(statusColor(status), CircleShape))
}

@Composable
private fun StatusPill(status: DownloadTaskStatus) {
    val color = statusColor(status)
    Text(
        statusLabel(status),
        modifier = Modifier.clip(MaterialTheme.shapes.small).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

private enum class QueueTab(val label: String) {
    QUEUE("队列"),
    WAITING_NETWORK("等待网络"),
    SKIPPED("已跳过"),
}

private fun DownloadTaskStatus.belongsTo(tab: QueueTab): Boolean = when (tab) {
    QueueTab.QUEUE -> this in setOf(
        DownloadTaskStatus.WAITING,
        DownloadTaskStatus.PARSING,
        DownloadTaskStatus.DOWNLOADING,
        DownloadTaskStatus.PAUSED,
        DownloadTaskStatus.VALIDATING,
    )
    QueueTab.WAITING_NETWORK -> this == DownloadTaskStatus.WAITING_NETWORK
    QueueTab.SKIPPED -> this == DownloadTaskStatus.SKIPPED
}

private fun QueuedDownload.progress(): Float = if (task.totalBytes > 0L) {
    (task.downloadedBytes.toFloat() / task.totalBytes).coerceIn(0f, 1f)
} else {
    0f
}

private fun statusLabel(status: DownloadTaskStatus): String = when (status) {
    DownloadTaskStatus.WAITING -> "等待"
    DownloadTaskStatus.PARSING -> "解析中"
    DownloadTaskStatus.DOWNLOADING -> "下载中"
    DownloadTaskStatus.VALIDATING -> "校验中"
    DownloadTaskStatus.PAUSED -> "已暂停"
    DownloadTaskStatus.WAITING_NETWORK -> "等待网络"
    DownloadTaskStatus.COMPLETED -> "完成"
    DownloadTaskStatus.FAILED -> "失败"
    DownloadTaskStatus.SKIPPED -> "已跳过"
    DownloadTaskStatus.CANCELLED -> "已取消"
}

@Composable
private fun statusColor(status: DownloadTaskStatus): Color = when (status) {
    DownloadTaskStatus.WAITING -> WaitingYellow
    DownloadTaskStatus.PARSING, DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.VALIDATING -> MaterialTheme.colorScheme.primary
    DownloadTaskStatus.WAITING_NETWORK -> WarmOrange
    DownloadTaskStatus.SKIPPED -> FailureRed
    DownloadTaskStatus.COMPLETED -> SuccessGreen
    DownloadTaskStatus.FAILED -> FailureRed
    DownloadTaskStatus.PAUSED, DownloadTaskStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun resolutionLabel(value: ResolutionPreset): String = when (value) {
    ResolutionPreset.BEST -> "最佳画质"
    ResolutionPreset.UP_TO_1080P -> "1080p"
    ResolutionPreset.UP_TO_720P -> "720p"
    ResolutionPreset.AUDIO_MP3 -> "仅音频"
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "--"
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes.toDouble() / (1024L * 1024L * 1024L))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / (1024L * 1024L))
    bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024L)
    else -> "$bytes B"
}

private fun formatSpeed(bytesPerSecond: Long): String = if (bytesPerSecond <= 0L) {
    "--"
} else {
    "${formatBytes(bytesPerSecond)}/s"
}

private fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds < 0L) return "--:--"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, remainder) else "%02d:%02d".format(minutes, remainder)
}
