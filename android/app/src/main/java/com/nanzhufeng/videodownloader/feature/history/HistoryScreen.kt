package com.nanzhufeng.videodownloader.feature.history

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.ui.AppCardTone
import com.nanzhufeng.videodownloader.core.ui.WorkbenchCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<DownloadHistory>,
    onDeleteRecord: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf<DownloadPlatform?>(null) }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.ALL) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = filterCompletedHistory(history, query, platform, period)

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history-screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("下载历史", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "已完成的下载会按时间排列，便于查找与管理。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text("搜索标题、博主或原链接") },
            )
        }
        item {
            HistoryFilters(
                platform = platform,
                period = period,
                onPlatformChange = { platform = it },
                onPeriodChange = { period = it },
            )
        }
        if (filtered.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (history.isEmpty()) "还没有下载历史" else "没有匹配结果", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (history.isEmpty()) "完成或结束的任务会自动归档到这里。" else "请调整关键词或筛选条件。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(filtered.size, key = { filtered[it].taskId }) { index ->
                HistoryItem(
                    item = filtered[index],
                    onDelete = { pendingDeleteId = filtered[index].taskId },
                )
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
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryFilters(
    platform: DownloadPlatform?,
    period: HistoryPeriod,
    onPlatformChange: (DownloadPlatform?) -> Unit,
    onPeriodChange: (HistoryPeriod) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("history-platform-time-filters"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = platform == null, onClick = { onPlatformChange(null) }, label = { Text("全部平台") })
            DownloadPlatform.entries.forEach { value ->
                FilterChip(
                    selected = platform == value,
                    onClick = { onPlatformChange(value) },
                    label = { Text(value.label()) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryPeriod.entries.forEach { value ->
                FilterChip(
                    selected = period == value,
                    onClick = { onPeriodChange(value) },
                    label = { Text(value.label) },
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    item: DownloadHistory,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    WorkbenchCard(
        tone = AppCardTone.MINT,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history-card-${item.taskId}"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.width(76.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatHistoryDate(item.completedAt), fontWeight = FontWeight.SemiBold)
                Text(
                    formatHistoryClock(item.completedAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(66.dp)
                    .background(Color(0xFF159447), RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("history-overflow-${item.taskId}"),
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (item.fileExists && item.outputUri != null) {
                                DropdownMenuItem(
                                    text = { Text("打开文件") },
                                    onClick = {
                                        menuExpanded = false
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(item.outputUri)).apply {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                },
                                            )
                                        }.onFailure {
                                            Toast.makeText(context, "无法打开该文件", Toast.LENGTH_SHORT).show()
                                        }
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
                Text(item.platform.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${item.creator}  ·  ${item.resolution.label()}  ·  ${formatBytes(item.fileSize)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DownloadPlatform.label(): String = when (this) {
    DownloadPlatform.YOUTUBE -> "YouTube"
    DownloadPlatform.DOUYIN -> "抖音"
    DownloadPlatform.TIKTOK -> "TikTok"
}

private fun com.nanzhufeng.videodownloader.core.model.ResolutionPreset.label(): String = when (this) {
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.BEST -> "最佳画质"
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.UP_TO_1080P -> "1080p 及以下"
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.UP_TO_720P -> "720p 及以下"
    com.nanzhufeng.videodownloader.core.model.ResolutionPreset.AUDIO_MP3 -> "仅音频 MP3"
}

private fun formatHistoryDate(value: Long): String =
    SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(value))

private fun formatHistoryClock(value: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))

private fun formatBytes(value: Long): String = when {
    value <= 0L -> "--"
    value >= 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f GB", value / 1073741824.0)
    value >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", value / 1048576.0)
    else -> String.format(Locale.getDefault(), "%.1f KB", value / 1024.0)
}
