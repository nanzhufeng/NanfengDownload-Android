package com.nanzhufeng.videodownloader.feature.history

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.ui.AppCardTone
import com.nanzhufeng.videodownloader.core.ui.PlatformIcon
import com.nanzhufeng.videodownloader.core.ui.SelectedFilterChip
import com.nanzhufeng.videodownloader.core.ui.WorkbenchCard
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<DownloadHistory>,
    throughputReports: List<DownloadThroughputReport> = emptyList(),
    onDeleteRecord: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf<DownloadPlatform?>(null) }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.ALL) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = filterCompletedHistory(history, query, platform, period)
    val grouped = filtered.groupBy { formatHistoryDay(it.completedAt) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("history-screen")) {
        val expanded = maxWidth >= 600.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (expanded) 2 else 1),
            modifier = Modifier
                .fillMaxSize()
                .testTag(if (expanded) "history-expanded-timeline" else "history-compact-timeline"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "历史",
                            style = if (expanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text("搜索标题、博主或原链接") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                HistoryFilters(
                    platform = platform,
                    period = period,
                    onPlatformChange = { platform = it },
                    onPeriodChange = { period = it },
                )
            }
            if (grouped.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CompactEmptyHistory(history = history)
                }
            } else {
                grouped.forEach { (day, records) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "day-$day") {
                        Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(records, key = DownloadHistory::taskId) { item ->
                        HistoryItem(
                            item = item,
                            reports = throughputReports.filter { it.taskId == item.taskId },
                            expanded = expanded,
                            onDelete = { pendingDeleteId = item.taskId },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { taskId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除历史记录？") },
            text = { Text("只删除这条记录，不会删除已经保存的视频文件。") },
            confirmButton = {
                Button(onClick = {
                    pendingDeleteId = null
                    onDeleteRecord(taskId)
                }) { Text("删除记录") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CompactEmptyHistory(history: List<DownloadHistory>) {
    WorkbenchCard(tone = AppCardTone.NEUTRAL) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (history.isEmpty()) "还没有下载历史" else "没有匹配结果", fontWeight = FontWeight.SemiBold)
            Text(
                if (history.isEmpty()) "已完成的下载会归档到这里。" else "请调整关键词或筛选条件。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HistoryFilters(
    platform: DownloadPlatform?,
    period: HistoryPeriod,
    onPlatformChange: (DownloadPlatform?) -> Unit,
    onPeriodChange: (HistoryPeriod) -> Unit,
) {
    var platformMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var periodMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("history-platform-time-filters"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.testTag("history-platform-filter")) {
            SelectedFilterChip(
                label = platform?.label() ?: "全部平台",
                selected = true,
                onClick = { platformMenuExpanded = true },
            )
            DropdownMenu(
                expanded = platformMenuExpanded,
                onDismissRequest = { platformMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("全部平台") },
                    onClick = {
                        platformMenuExpanded = false
                        onPlatformChange(null)
                    },
                )
                DownloadPlatform.entries.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value.label()) },
                        onClick = {
                            platformMenuExpanded = false
                            onPlatformChange(value)
                        },
                    )
                }
            }
        }
        Box(modifier = Modifier.testTag("history-period-filter")) {
            SelectedFilterChip(
                label = period.label,
                selected = true,
                onClick = { periodMenuExpanded = true },
            )
            DropdownMenu(
                expanded = periodMenuExpanded,
                onDismissRequest = { periodMenuExpanded = false },
            ) {
                HistoryPeriod.entries.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value.label) },
                        onClick = {
                            periodMenuExpanded = false
                            onPeriodChange(value)
                        },
                    )
                }
            }
        }
        Spacer(
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        )
    }
}

@Composable
private fun HistoryItem(
    item: DownloadHistory,
    reports: List<DownloadThroughputReport>,
    expanded: Boolean,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var showThroughputReport by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showPlayerChooser by rememberSaveable { mutableStateOf(false) }
    val report = reports.firstOrNull { it.outcome == com.nanzhufeng.videodownloader.core.model.TransferReportOutcome.COMPLETED }
        ?: reports.firstOrNull()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已完成",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (expanded) 24.dp else 22.dp),
            )
            Text(
                formatHistoryClock(item.completedAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .width(1.dp)
                    .height(if (expanded) 76.dp else 52.dp)
                    .background(Color(0xFFD4E7DA)),
            )
        }
        WorkbenchCard(
            tone = AppCardTone.MINT,
            modifier = Modifier
                .weight(1f)
                .clickable { showDetails = true }
                .testTag("history-card-${item.taskId}"),
            contentPadding = PaddingValues(if (expanded) 16.dp else 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HistoryThumbnail(
                    item = item,
                    expanded = expanded,
                    onPlay = {
                        openWithDefaultPlayer(context, item).onFailure {
                            Toast.makeText(context, "没有可用的视频播放器", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                Spacer(Modifier.width(if (expanded) 12.dp else 8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (expanded) 6.dp else 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformIcon(
                        item.platform,
                        contentDescription = "${item.platform.label()} 图标",
                        modifier = Modifier.size(if (expanded) 20.dp else 18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(item.platform.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(if (expanded) 40.dp else 26.dp)
                                .testTag("history-overflow-${item.taskId}"),
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (item.fileExists && item.outputUri != null) {
                                DropdownMenuItem(
                                    text = { Text("选择播放器") },
                                    onClick = {
                                        menuExpanded = false
                                        showPlayerChooser = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("复制原链接") },
                                onClick = {
                                    menuExpanded = false
                                    clipboard.setText(AnnotatedString(item.originalUrl))
                                    Toast.makeText(context, "已复制原链接", Toast.LENGTH_SHORT).show()
                                },
                            )
                            if (reports.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("查看吞吐报告") },
                                    onClick = {
                                        menuExpanded = false
                                        showThroughputReport = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("分享原链接") },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, item.originalUrl)
                                            },
                                            "分享原链接",
                                        ),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除历史记录") },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
                    Text(
                    item.title,
                    maxLines = if (expanded) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (expanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                    Text(
                    item.creator,
                    style = if (expanded) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                    Text(
                    "${item.resolution.label()}  ·  ${formatBytes(item.fileSize)}",
                    style = if (expanded) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                    if (report != null) {
                        Text(
                        "${report.connectionMode.label(report.connectionCount)}  ·  平均 ${formatSpeed(report.averageBytesPerSecond)}  ·  峰值 ${formatSpeed(report.peakBytesPerSecond)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showDetails) {
        HistoryDetailsDialog(
            item = item,
            hasThroughputReport = reports.isNotEmpty(),
            onDismiss = { showDetails = false },
            onPlay = {
                openWithDefaultPlayer(context, item).onFailure {
                    Toast.makeText(context, "没有可用的视频播放器", Toast.LENGTH_SHORT).show()
                }
            },
            onChoosePlayer = {
                showDetails = false
                showPlayerChooser = true
            },
            onCopyLink = {
                clipboard.setText(AnnotatedString(item.originalUrl))
                Toast.makeText(context, "已复制原链接", Toast.LENGTH_SHORT).show()
            },
            onShowReport = {
                showDetails = false
                showThroughputReport = true
            },
        )
    }

    if (showPlayerChooser) {
        PlayerChooserDialog(
            item = item,
            onDismiss = { showPlayerChooser = false },
            onSelected = { option ->
                showPlayerChooser = false
                openWithPlayer(context, item, option).onFailure {
                    Toast.makeText(context, "无法打开 ${option.label}", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    if (showThroughputReport && reports.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showThroughputReport = false },
            title = { Text("真实吞吐报告") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    reports.take(8).forEachIndexed { index, entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${index + 1}. ${entry.streamLabel} · ${entry.outcome.label()}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text("连接模式：${entry.connectionMode.label(entry.connectionCount)}")
                            Text("Range：${if (entry.rangeSupported) "已验证支持" else "不支持或未通过探测"}")
                            Text("实际网络字节：${formatBytes(entry.networkBytes)} · 成品：${formatBytes(entry.committedBytes)}")
                            Text("平均：${formatSpeed(entry.averageBytesPerSecond)} · 峰值：${formatSpeed(entry.peakBytesPerSecond)}")
                            Text("耗时：${String.format(Locale.getDefault(), "%.2f 秒", entry.elapsedMillis / 1000.0)}")
                            Text("连接内重试：${entry.retryCount} 次 · 重新探测：${entry.reprobeCount} 次")
                            entry.fallbackReason?.let { Text("策略说明：$it") }
                            entry.errorSummary?.let {
                                Text("原始错误（高级信息）：$it", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThroughputReport = false }) { Text("完成") }
            },
        )
    }
}

@Composable
private fun HistoryThumbnail(
    item: DownloadHistory,
    expanded: Boolean,
    onPlay: () -> Unit,
) {
    val playable = item.fileExists && item.outputUri != null
    Box(
        modifier = Modifier
            .size(width = if (expanded) 104.dp else 76.dp, height = if (expanded) 78.dp else 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = playable, onClick = onPlay)
            .testTag("history-thumbnail-${item.taskId}"),
        contentAlignment = Alignment.Center,
    ) {
        if (item.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = "${item.title} 视频预览图",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        if (playable) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "用默认播放器播放",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun HistoryDetailsDialog(
    item: DownloadHistory,
    hasThroughputReport: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onChoosePlayer: () -> Unit,
    onCopyLink: () -> Unit,
    onShowReport: () -> Unit,
) {
    val playable = item.fileExists && item.outputUri != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (item.resolution == com.nanzhufeng.videodownloader.core.model.ResolutionPreset.AUDIO_MP3) "音频详情" else "视频详情")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(item.creator, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${item.platform.label()}  ·  ${item.resolution.label()}  ·  ${formatBytes(item.fileSize)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (playable) {
                    TextButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                        Text("默认播放器播放")
                    }
                    TextButton(onClick = onChoosePlayer, modifier = Modifier.fillMaxWidth()) {
                        Text("选择播放器")
                    }
                }
                TextButton(onClick = onCopyLink, modifier = Modifier.fillMaxWidth()) {
                    Text("复制原链接")
                }
                if (hasThroughputReport) {
                    TextButton(onClick = onShowReport, modifier = Modifier.fillMaxWidth()) {
                        Text("查看吞吐报告")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun PlayerChooserDialog(
    item: DownloadHistory,
    onDismiss: () -> Unit,
    onSelected: (MediaPlayerOption) -> Unit,
) {
    val context = LocalContext.current
    val players = remember(item.taskId, item.outputUri) { queryMediaPlayers(context, item) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择播放器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (players.isEmpty()) {
                    Text("没有检测到可播放该文件的视频播放器。")
                } else {
                    players.forEach { player ->
                        TextButton(
                            onClick = { onSelected(player) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (player.isSystemDefault) {
                                    "系统默认 · ${player.label}"
                                } else {
                                    player.label
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun com.nanzhufeng.videodownloader.core.model.TransferReportOutcome.label(): String = when (this) {
    com.nanzhufeng.videodownloader.core.model.TransferReportOutcome.COMPLETED -> "完成"
    com.nanzhufeng.videodownloader.core.model.TransferReportOutcome.FAILED -> "失败"
    com.nanzhufeng.videodownloader.core.model.TransferReportOutcome.CANCELLED -> "取消"
}

private fun DownloadConnectionMode.label(connectionCount: Int): String = when (this) {
    DownloadConnectionMode.MULTI -> "多连接 ×${connectionCount.coerceAtLeast(2)}"
    DownloadConnectionMode.SINGLE -> "单连接"
    DownloadConnectionMode.UNKNOWN -> "连接探测中"
}

private fun DownloadPlatform.label(): String = when (this) {
    DownloadPlatform.YOUTUBE -> "YouTube"
    DownloadPlatform.DOUYIN -> "抖音"
    DownloadPlatform.TIKTOK -> "TikTok"
    DownloadPlatform.BILIBILI -> "哔哩哔哩"
    DownloadPlatform.XIAOHONGSHU -> "小红书"
}

private fun com.nanzhufeng.videodownloader.core.model.ResolutionPreset.label(): String = when (this) {
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.BEST -> "最佳画质"
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.UP_TO_1080P -> "1080p 及以下"
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.UP_TO_720P -> "720p 及以下"
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.UP_TO_360P -> "360p 及以下"
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.AUDIO_MP3 -> "仅音频 MP3"
}

private fun formatHistoryDay(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value))

private fun formatHistoryClock(value: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))

private fun formatBytes(value: Long): String = when {
    value <= 0L -> "--"
    value >= 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f GB", value / 1073741824.0)
    value >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", value / 1048576.0)
    else -> String.format(Locale.getDefault(), "%.1f KB", value / 1024.0)
}

private fun formatSpeed(value: Long): String = "${formatBytes(value)}/s"
