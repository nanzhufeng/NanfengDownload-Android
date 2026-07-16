package com.nanzhufeng.videodownloader.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.ui.FailureRed
import com.nanzhufeng.videodownloader.core.ui.MarsGreen
import com.nanzhufeng.videodownloader.core.ui.SuccessGreen
import com.nanzhufeng.videodownloader.core.ui.WaitingYellow

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
    expanded: Boolean = false,
) {
    if (expanded) {
        ExpandedHome(queue, input, onInputChange, onSmartRead, isReading, notice, canLoadMore, onLoadMore)
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("下载工作台", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "粘贴分享文本或链接，智能识别后进入读取与选择流程。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            QueueSummary(queue)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("添加内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home-input"),
                        minLines = 3,
                        maxLines = 5,
                        label = { Text("抖音、YouTube 或 TikTok 分享文本") },
                    )
                    Button(
                        onClick = onSmartRead,
                        enabled = input.isNotBlank() && !isReading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart-read"),
                    ) {
                        androidx.compose.material3.Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(if (isReading) "正在读取" else "智能读取")
                    }
                    if (notice.isNotBlank()) {
                        Text(notice, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text("当前队列", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (queue.isEmpty()) {
            item {
                EmptyQueue()
            }
        } else {
            items(queue, key = { it.task.taskId }) { queued ->
                QueueItem(queued)
            }
        }
        if (canLoadMore) {
            item {
                Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth(), enabled = !isReading) {
                    Text("加载更多作品")
                }
            }
        }
    }
}

@Composable
private fun ExpandedHome(
    queue: List<QueuedDownload>,
    input: String,
    onInputChange: (String) -> Unit,
    onSmartRead: () -> Unit,
    isReading: Boolean,
    notice: String,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("home-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("下载工作台", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "粘贴分享文本或链接，智能识别后进入读取与选择流程。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QueueSummary(queue)
                InputCard(input, onInputChange, onSmartRead, isReading, notice)
            }
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("当前队列", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (queue.isEmpty()) {
                    EmptyQueue()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(queue, key = { it.task.taskId }) { queued -> QueueItem(queued) }
                    }
                }
                if (canLoadMore) {
                    Button(onClick = onLoadMore, enabled = !isReading, modifier = Modifier.fillMaxWidth()) {
                        Text("加载更多作品")
                    }
                }
            }
        }
    }
}

@Composable
private fun InputCard(
    input: String,
    onInputChange: (String) -> Unit,
    onSmartRead: () -> Unit,
    isReading: Boolean,
    notice: String,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("添加内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home-input"),
                minLines = 3,
                maxLines = 5,
                label = { Text("抖音、YouTube 或 TikTok 分享文本") },
            )
            Button(
                onClick = onSmartRead,
                enabled = input.isNotBlank() && !isReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smart-read"),
            ) {
                androidx.compose.material3.Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(if (isReading) "正在读取" else "智能读取")
            }
            if (notice.isNotBlank()) {
                Text(notice, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QueueSummary(queue: List<QueuedDownload>) {
    val completed = queue.count { it.task.status == DownloadTaskStatus.COMPLETED }
    val progress = if (queue.isEmpty()) 0f else completed.toFloat() / queue.size
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("总进度", color = Color.White.copy(alpha = 0.78f))
                    Text(
                        if (queue.isEmpty()) "尚无任务" else "$completed / ${queue.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "等待 ${queue.count { it.task.status == DownloadTaskStatus.WAITING }}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = Color.White.copy(alpha = 0.22f),
            )
        }
    }
}

@Composable
private fun EmptyQueue() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("队列为空", fontWeight = FontWeight.SemiBold)
            Text("智能读取后，作品会在这里等待选择与下载。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QueueItem(queued: QueuedDownload) {
    val progress = if (queued.task.totalBytes > 0L) {
        queued.task.downloadedBytes.toFloat() / queued.task.totalBytes
    } else {
        0f
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                statusLabel(queued.task.status),
                modifier = Modifier
                    .background(statusColor(queued.task.status), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = if (queued.task.status == DownloadTaskStatus.WAITING) Color(0xFF6B4F00) else Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(queued.media.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(queued.media.creator, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun statusLabel(status: DownloadTaskStatus): String = when (status) {
    DownloadTaskStatus.WAITING -> "等待"
    DownloadTaskStatus.PARSING -> "解析"
    DownloadTaskStatus.DOWNLOADING -> "下载中"
    DownloadTaskStatus.VALIDATING -> "校验"
    DownloadTaskStatus.PAUSED -> "暂停"
    DownloadTaskStatus.WAITING_NETWORK -> "等网络"
    DownloadTaskStatus.COMPLETED -> "完成"
    DownloadTaskStatus.FAILED -> "失败"
    DownloadTaskStatus.SKIPPED -> "跳过"
    DownloadTaskStatus.CANCELLED -> "取消"
}

private fun statusColor(status: DownloadTaskStatus): Color = when (status) {
    DownloadTaskStatus.WAITING -> WaitingYellow
    DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.PARSING, DownloadTaskStatus.VALIDATING -> MarsGreen
    DownloadTaskStatus.COMPLETED -> SuccessGreen
    DownloadTaskStatus.FAILED -> FailureRed
    else -> Color(0xFF667085)
}
