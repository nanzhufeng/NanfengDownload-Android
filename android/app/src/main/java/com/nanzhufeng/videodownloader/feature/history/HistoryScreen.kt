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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.runtime.LaunchedEffect
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
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<DownloadHistory>,
    throughputReports: List<DownloadThroughputReport> = emptyList(),
    onDeleteRecord: (String) -> Unit,
    onDeleteRecords: (List<String>) -> Unit = { taskIds ->
        taskIds.forEach(onDeleteRecord)
    },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf<DownloadPlatform?>(null) }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.ALL) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingBulkDelete by rememberSaveable { mutableStateOf(false) }
    val filtered = filterCompletedHistory(history, query, platform, period)
    val grouped = filtered.groupBy { formatHistoryDay(it.completedAt) }
    val visibleIds = filtered.map(DownloadHistory::taskId)
    val selectedIdSet = selectedIds.toSet()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("history-screen")) {
        val expanded = maxWidth >= 600.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (expanded) 2 else 1),
            modifier = Modifier
                .fillMaxSize()
                .testTag(if (expanded) "history-expanded-timeline" else "history-compact-timeline"),
            contentPadding = PaddingValues(if (expanded) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "历史",
                        modifier = Modifier.weight(1f),
                        style = if (expanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedIds = emptyList()
                    },
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
                    onPlatformChange = {
                        platform = it
                        selectedIds = emptyList()
                    },
                    onPeriodChange = {
                        period = it
                        selectedIds = emptyList()
                    },
                    showBulkDelete = !selectionMode && filtered.isNotEmpty(),
                    onBulkDelete = {
                        selectedIds = emptyList()
                        selectionMode = true
                    },
                )
            }
            if (selectionMode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history-bulk-toolbar"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "已选 ${selectedIds.size} 项",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                selectedIds = if (
                                    visibleIds.isNotEmpty() && visibleIds.all(selectedIdSet::contains)
                                ) {
                                    emptyList()
                                } else {
                                    visibleIds
                                }
                            },
                            enabled = visibleIds.isNotEmpty(),
                            modifier = Modifier.testTag("history-select-all"),
                        ) {
                            Text(
                                if (
                                    visibleIds.isNotEmpty() && visibleIds.all(selectedIdSet::contains)
                                ) {
                                    "取消全选"
                                } else {
                                    "全选"
                                },
                            )
                        }
                        TextButton(
                            onClick = {
                                selectedIds = emptyList()
                                selectionMode = false
                            },
                            modifier = Modifier.testTag("history-bulk-cancel"),
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = { pendingBulkDelete = true },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.testTag("history-delete-selected"),
                        ) {
                            Text("删除")
                        }
                    }
                }
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
                            selectionMode = selectionMode,
                            selected = item.taskId in selectedIdSet,
                            onSelectionChange = {
                                selectedIds = if (item.taskId in selectedIdSet) {
                                    selectedIds - item.taskId
                                } else {
                                    selectedIds + item.taskId
                                }
                            },
                            onDelete = { pendingDeleteId = item.taskId },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { taskId ->
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
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

    if (pendingBulkDelete) {
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { pendingBulkDelete = false },
            title = { Text("批量删除历史记录？") },
            text = {
                Text("将删除选中的 ${selectedIds.size} 条历史记录，不会删除已经保存的媒体文件。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedIds
                        pendingBulkDelete = false
                        selectedIds = emptyList()
                        selectionMode = false
                        onDeleteRecords(idsToDelete)
                    },
                    modifier = Modifier.testTag("history-confirm-bulk-delete"),
                ) {
                    Text("删除 ${selectedIds.size} 条记录")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDelete = false }) { Text("取消") }
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
    showBulkDelete: Boolean,
    onBulkDelete: () -> Unit,
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
        if (showBulkDelete) {
            TextButton(
                onClick = onBulkDelete,
                modifier = Modifier.testTag("history-bulk-delete"),
            ) {
                Text("批量删除")
            }
        }
    }
}

@Composable
private fun HistoryItem(
    item: DownloadHistory,
    reports: List<DownloadThroughputReport>,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectionChange: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var showThroughputReport by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showPlayerChooser by rememberSaveable { mutableStateOf(false) }
    var showInternalAudioPlayer by rememberSaveable { mutableStateOf(false) }
    var showVideoSegments by rememberSaveable { mutableStateOf(false) }
    val playable = item.fileExists && item.outputUri != null
    var mediaMetadata by remember(item.taskId, item.outputUri, item.fileSize) {
        mutableStateOf<HistoryMediaMetadata?>(null)
    }
    LaunchedEffect(item.taskId, item.outputUri, item.fileSize, playable) {
        mediaMetadata = if (playable) {
            readHistoryMediaMetadata(context, item)
        } else {
            HistoryMediaMetadata(durationMillis = null, fileSize = item.fileSize)
        }
    }
    val displayedSize = mediaMetadata?.fileSize ?: item.fileSize
    val playItem: () -> Unit = {
        if (shouldUseInternalAudioPlayer(item)) {
            showInternalAudioPlayer = true
        } else if (item.outputUris.size > 1) {
            showVideoSegments = true
        } else {
            openWithDefaultPlayer(context, item).onFailure {
                Toast.makeText(context, "没有可用的视频播放器", Toast.LENGTH_SHORT).show()
            }
        }
        Unit
    }
    val listDurationText = when {
        playable && mediaMetadata == null -> "读取中…"
        mediaMetadata?.durationMillis != null -> formatMediaDuration(requireNotNull(mediaMetadata?.durationMillis))
        playable -> "无法读取"
        else -> "文件不可用"
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(if (expanded) 42.dp else 40.dp)
                .height(if (expanded) 60.dp else 54.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(1.dp)
                    .height(if (expanded) 12.dp else 8.dp)
                    .background(Color(0xFFD4E7DA)),
            )
            if (selectionMode) {
                IconButton(
                    onClick = onSelectionChange,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (expanded) 40.dp else 36.dp)
                        .testTag("history-select-${item.taskId}"),
                ) {
                    Icon(
                        imageVector = if (selected) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = if (selected) {
                            "取消选择 ${item.title}"
                        } else {
                            "选择 ${item.title}"
                        },
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(if (expanded) 28.dp else 26.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "已完成",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                    .size(if (expanded) 24.dp else 22.dp),
                )
            }
            Text(
                formatHistoryClock(item.completedAt),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (expanded) 23.dp else 21.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        WorkbenchCard(
            tone = AppCardTone.MINT,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    if (selectionMode) onSelectionChange() else showDetails = true
                }
                .testTag("history-card-${item.taskId}"),
            contentPadding = PaddingValues(if (expanded) 12.dp else 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HistoryThumbnail(
                    item = item,
                    expanded = expanded,
                    onPlay = {
                        if (selectionMode) onSelectionChange() else playItem()
                    },
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (expanded) 4.dp else 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformIcon(
                        item.platform,
                        contentDescription = "${item.platform.label()} 图标",
                        modifier = Modifier.size(if (expanded) 20.dp else 18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(item.platform.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (!selectionMode) {
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
                                    text = {
                                        Text(
                                            if (shouldUseInternalAudioPlayer(item)) {
                                                "选择外部播放器"
                                            } else {
                                                "选择播放器"
                                            },
                                        )
                                    },
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
                }
                    Text(
                    item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (expanded) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                    Text(
                    buildString {
                        append(item.creator)
                        append(" · ")
                        append(item.resolution.label())
                        if (item.audioSegmentCount > 1) append("  ·  共 ${item.audioSegmentCount} 段")
                        append("  ·  时长 $listDurationText  ·  ${formatBytes(displayedSize)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                }
            }
        }
    }

    if (showDetails) {
        HistoryDetailsDialog(
            item = item,
            mediaMetadata = mediaMetadata,
            hasThroughputReport = reports.isNotEmpty(),
            onDismiss = { showDetails = false },
            onPlay = playItem,
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

    if (showInternalAudioPlayer) {
        InternalAudioPlayerDialog(
            item = item,
            onDismiss = { showInternalAudioPlayer = false },
        )
    }

    if (showVideoSegments) {
        VideoSegmentPlayerDialog(
            item = item,
            onDismiss = { showVideoSegments = false },
            onPlay = { index, uri ->
                openWithDefaultPlayer(
                    context,
                    item.copy(
                        outputUri = uri,
                        outputUris = listOf(uri),
                    ),
                ).onFailure {
                    Toast.makeText(context, "没有可用的视频播放器", Toast.LENGTH_SHORT).show()
                }
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
            containerColor = Color.White,
            tonalElevation = 0.dp,
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
                            if (entry.networkBytes > 0L) {
                                Text("连接模式：${entry.connectionMode.label(entry.connectionCount)}")
                                Text("Range：${if (entry.rangeSupported) "已验证支持" else "不支持或未通过探测"}")
                                Text("实际网络字节：${formatBytes(entry.networkBytes)} · 成品：${formatBytes(entry.committedBytes)}")
                                Text("平均：${formatSpeed(entry.averageBytesPerSecond)} · 峰值：${formatSpeed(entry.peakBytesPerSecond)}")
                                Text("网络耗时：${formatElapsed(entry.elapsedMillis)}")
                                Text("连接内重试：${entry.retryCount} 次 · 重新探测：${entry.reprobeCount} 次")
                            } else {
                                Text(
                                    "输入源：${formatBytes(entry.expectedBytes)} · " +
                                        "本机成品：${formatBytes(entry.committedBytes)}",
                                )
                                Text("本机处理耗时：${formatElapsed(entry.elapsedMillis)}")
                            }
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
private fun VideoSegmentPlayerDialog(
    item: DownloadHistory,
    onDismiss: () -> Unit,
    onPlay: (Int, String) -> Unit,
) {
    val uris = item.outputUris.ifEmpty { item.outputUri?.let(::listOf).orEmpty() }
    AlertDialog(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text("选择视频分段") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "共 ${uris.size} 段，每段都是可独立播放的 MP4。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                uris.forEachIndexed { index, uri ->
                    TextButton(
                        onClick = { onPlay(index, uri) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video-segment-play-${index + 1}"),
                    ) {
                        Text("播放第 ${(index + 1).toString().padStart(2, '0')} 段")
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
private fun HistoryThumbnail(
    item: DownloadHistory,
    expanded: Boolean,
    onPlay: () -> Unit,
) {
    val playable = item.fileExists && item.outputUri != null
    val previewSource = remember(
        item.fileExists,
        item.outputUri,
        item.outputUris,
        item.thumbnailUrl,
        item.resolution,
    ) { historyPreviewSource(item) }
    val context = LocalContext.current
    var localFrameFailed by remember(previewSource) { mutableStateOf(false) }
    val localVideoRequest = remember((previewSource as? HistoryPreviewSource.LocalVideo)?.uri) {
        (previewSource as? HistoryPreviewSource.LocalVideo)?.let { source ->
            ImageRequest.Builder(context)
                .data(source.uri)
                // A short offset avoids a black opening frame on fade-in videos.
                .videoFrameMillis(750)
                .crossfade(false)
                .build()
        }
    }
    Box(
        modifier = Modifier
            .size(width = if (expanded) 84.dp else 68.dp, height = if (expanded) 62.dp else 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = playable, onClick = onPlay)
            .testTag("history-thumbnail-${item.taskId}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (shouldUseInternalAudioPlayer(item)) {
                Icons.Filled.MusicNote
            } else {
                Icons.Outlined.VideoLibrary
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        when (previewSource) {
            is HistoryPreviewSource.LocalVideo -> {
                if (!localFrameFailed) {
                    AsyncImage(
                        model = requireNotNull(localVideoRequest),
                        contentDescription = "${item.title} 本地视频预览图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { localFrameFailed = true },
                    )
                } else if (previewSource.fallbackArtworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = previewSource.fallbackArtworkUrl,
                        contentDescription = "${item.title} 备用视频预览图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            is HistoryPreviewSource.RemoteArtwork -> {
            AsyncImage(
                    model = previewSource.url,
                contentDescription = if (shouldUseInternalAudioPlayer(item)) {
                    "${item.title} 音频封面"
                } else {
                    "${item.title} 视频预览图"
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            }

            HistoryPreviewSource.None -> Unit
        }
        if (playable) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = if (shouldUseInternalAudioPlayer(item)) {
                    "用内置音频播放器播放"
                } else {
                    "用默认视频播放器播放"
                },
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun HistoryDetailsDialog(
    item: DownloadHistory,
    mediaMetadata: HistoryMediaMetadata?,
    hasThroughputReport: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onChoosePlayer: () -> Unit,
    onCopyLink: () -> Unit,
    onShowReport: () -> Unit,
) {
    val playable = item.fileExists && item.outputUri != null
    val displayedSize = mediaMetadata?.fileSize ?: item.fileSize
    val durationText = when {
        playable && mediaMetadata == null -> "正在读取…"
        mediaMetadata?.durationMillis != null -> formatMediaDuration(requireNotNull(mediaMetadata?.durationMillis))
        playable -> "无法读取，请确认文件仍可正常播放"
        else -> "文件不可用"
    }
    AlertDialog(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = {
            Text(if (item.resolution == com.nanzhufeng.videodownloader.core.model.ResolutionPreset.AUDIO_MP3) "音频详情" else "视频详情")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(item.creator, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    buildString {
                        append("${item.platform.label()}  ·  ${item.resolution.label()}")
                        if (item.audioSegmentCount > 1) append("  ·  共 ${item.audioSegmentCount} 段")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${if (shouldUseInternalAudioPlayer(item)) "音频" else "视频"}时长：$durationText",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "文件大小：${formatBytes(displayedSize)}（${formatExactBytes(displayedSize)} 字节）",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (playable) {
                    TextButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (shouldUseInternalAudioPlayer(item)) {
                                "内置播放器播放"
                            } else {
                                "默认播放器播放"
                            },
                        )
                    }
                    TextButton(onClick = onChoosePlayer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (shouldUseInternalAudioPlayer(item)) {
                                "选择外部播放器"
                            } else {
                                "选择播放器"
                            },
                        )
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
        containerColor = Color.White,
        tonalElevation = 0.dp,
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

private fun formatElapsed(valueMillis: Long): String {
    val totalSeconds = (valueMillis.coerceAtLeast(0L) + 500L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}小时${minutes}分${seconds}秒"
        minutes > 0L -> "${minutes}分${seconds}秒"
        else -> String.format(Locale.getDefault(), "%.2f 秒", valueMillis / 1_000.0)
    }
}
