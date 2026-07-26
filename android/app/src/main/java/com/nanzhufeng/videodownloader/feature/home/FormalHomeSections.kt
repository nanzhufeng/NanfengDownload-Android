package com.nanzhufeng.videodownloader.feature.home

import android.widget.Toast
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nanzhufeng.videodownloader.R
import com.nanzhufeng.videodownloader.core.diagnostics.UserFacingErrorPresenter
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.ui.AppCardTone
import com.nanzhufeng.videodownloader.core.ui.FailureRed
import com.nanzhufeng.videodownloader.core.ui.HermesOrange
import com.nanzhufeng.videodownloader.core.ui.ForestGreen
import com.nanzhufeng.videodownloader.core.ui.QualityPurple
import com.nanzhufeng.videodownloader.core.ui.StorageOchre
import com.nanzhufeng.videodownloader.core.ui.SuccessGreen
import com.nanzhufeng.videodownloader.core.ui.WaitingYellow
import com.nanzhufeng.videodownloader.core.ui.WarmOrange
import com.nanzhufeng.videodownloader.core.ui.WorkbenchCard
import kotlinx.coroutines.delay

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
    onDeleteQueued: (String) -> Unit,
    onRetryQueued: (String) -> Unit,
    onStartDownloads: () -> Unit,
    onPauseActive: () -> Unit,
    onStopActive: (String) -> Unit,
    networkAvailable: Boolean,
    completedCount: Int,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val keyboardClearancePx = with(density) { 8.dp.toPx() }
    val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
    val keyboardTopPx = view.height.toFloat() - imeBottomPx
    var actionsBottomPx by remember { mutableStateOf(0f) }
    var keyboardLiftPx by remember { mutableStateOf(0f) }

    LaunchedEffect(imeBottomPx, keyboardTopPx, actionsBottomPx) {
        if (imeBottomPx <= 0f) {
            keyboardLiftPx = 0f
        } else if (keyboardTopPx > 0f && actionsBottomPx > 0f) {
            val unshiftedActionsBottom = actionsBottomPx + keyboardLiftPx
            keyboardLiftPx = (
                unshiftedActionsBottom - keyboardTopPx + keyboardClearancePx
            ).coerceAtLeast(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -keyboardLiftPx }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeHeader(expanded = false)
            RunStatusCard(
                queue = queue,
                completedCount = completedCount,
                networkAvailable = networkAvailable,
                expanded = false,
                modifier = Modifier.testTag("home-compact-run-status"),
            )
            QueuePanel(
                queue = queue,
                onSelectionChanged = onSelectionChanged,
                onBulkSelectionChanged = onBulkSelectionChanged,
                onResolutionChanged = onResolutionChanged,
                onDeleteQueued = onDeleteQueued,
                onRetryQueued = onRetryQueued,
                onStartDownloads = onStartDownloads,
                expandedLayout = false,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            if (canLoadMore) {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !isReading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("加载更多作品")
                }
            }
            ReadEntryCard(
                input = input,
                onInputChange = onInputChange,
                onSmartRead = onSmartRead,
                isReading = isReading,
                notice = notice,
                onActionsBottomChanged = { actionsBottomPx = it },
            )
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
    onDeleteQueued: (String) -> Unit,
    onRetryQueued: (String) -> Unit,
    onStartDownloads: () -> Unit,
    onPauseActive: () -> Unit,
    onStopActive: (String) -> Unit,
    networkAvailable: Boolean,
    defaultResolution: ResolutionPreset,
    completedCount: Int,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp).testTag("formal-expanded-workbench"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HomeHeader(expanded = true)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1.65f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RunStatusCard(queue, completedCount, networkAvailable, expanded = true)
                QueuePanel(
                    queue = queue,
                    onSelectionChanged = onSelectionChanged,
                    onBulkSelectionChanged = onBulkSelectionChanged,
                    onResolutionChanged = onResolutionChanged,
                    onDeleteQueued = onDeleteQueued,
                    onRetryQueued = onRetryQueued,
                    onStartDownloads = onStartDownloads,
                    expandedLayout = true,
                    modifier = if (queue.isEmpty()) {
                        Modifier.fillMaxWidth().testTag("home-main-queue-workspace")
                    } else {
                        Modifier.fillMaxWidth().weight(1f).testTag("home-main-queue-workspace")
                    },
                )
            }
            Column(
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TotalProgressCard(queue, completedCount)
                DefaultQualityCard(defaultResolution)
                Box(modifier = Modifier.testTag("home-side-add-task")) {
                    ReadEntryCard(input, onInputChange, onSmartRead, isReading, notice, dense = true)
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
}

@Composable
private fun HomeHeader(expanded: Boolean) {
    val appName = stringResource(R.string.app_name)
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("首页", style = MaterialTheme.typography.headlineMedium)
            Text(
                "添加视频或链接开始下载，任务在本机处理，安全高效",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.nanzhufeng_app_icon),
            contentDescription = appName,
            modifier = Modifier.size(52.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RunStatusCard(
    queue: List<QueuedDownload>,
    completedCount: Int,
    networkAvailable: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeCount = queue.count { it.task.status == DownloadTaskStatus.DOWNLOADING }
    val waitingCount = queue.count {
        it.task.status in setOf(
            DownloadTaskStatus.WAITING,
            DownloadTaskStatus.PARSING,
            DownloadTaskStatus.VALIDATING,
            DownloadTaskStatus.WAITING_NETWORK,
        )
    }
    val total = queue.sumOf { it.task.totalBytes.coerceAtLeast(0L) }
    val downloaded = queue.sumOf { it.task.downloadedBytes.coerceAtLeast(0L) }
    val progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f

    WorkbenchCard(
        tone = AppCardTone.NEUTRAL,
        modifier = modifier,
        contentPadding = PaddingValues(if (expanded) 16.dp else 12.dp),
    ) {
        Text(
            "运行状态",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(if (expanded) 14.dp else 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatusMetric(Icons.Filled.CloudDone, "网络状态", if (networkAvailable) "良好" else "等待", SuccessGreen, expanded)
            StatusMetric(Icons.Filled.Downloading, "进行中", activeCount.toString(), SuccessGreen, expanded)
            StatusMetric(Icons.Filled.Schedule, "等待中", waitingCount.toString(), WarmOrange, expanded)
            if (expanded) {
                StatusMetric(Icons.Filled.CheckCircle, "已完成", completedCount.toString(), SuccessGreen, true)
            }
            StatusMetric(Icons.Filled.Downloading, "总进度", "${(progress * 100).toInt()}%", SuccessGreen, expanded)
        }
    }
}

@Composable
private fun StatusMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    expanded: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (expanded) 4.dp else 2.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (expanded) 24.dp else 20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (expanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold,
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
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = quickFeedbackTween(),
        label = "active-download-progress",
    )
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
                        Text(
                            resolutionLabel(queued.task.resolution),
                            color = resolutionAccent(queued.task.resolution),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            formatSpeed(queued.task.speedBytesPerSecond),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        when (queued.task.connectionMode) {
                            com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode.MULTI ->
                                "多连接 ×${queued.task.connectionCount.coerceAtLeast(2)}"
                            com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode.SINGLE -> "单连接"
                            com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode.UNKNOWN -> "正在探测连接能力"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${(animatedProgress * 100).toInt()}% · 剩余 ${formatDuration(queued.task.remainingSeconds)} · ${formatBytes(queued.task.downloadedBytes)} / ${formatBytes(queued.task.totalBytes)}",
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
    onDeleteQueued: (String) -> Unit,
    onRetryQueued: (String) -> Unit,
    onStartDownloads: () -> Unit,
    expandedLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val eligibleRows = queue.filter {
        it.task.status in setOf(
            DownloadTaskStatus.WAITING,
            DownloadTaskStatus.PAUSED,
            DownloadTaskStatus.WAITING_NETWORK,
        )
    }
    val eligibleIds = eligibleRows.map { it.task.taskId }
    val allSelected = eligibleRows.isNotEmpty() && eligibleRows.all { it.task.selected }
    val queueListState = rememberLazyListState()
    val activeIndex = queue.indexOfFirst {
        it.task.status in setOf(
            DownloadTaskStatus.PARSING,
            DownloadTaskStatus.DOWNLOADING,
            DownloadTaskStatus.VALIDATING,
        )
    }

    LaunchedEffect(activeIndex, queue.size) {
        if (activeIndex < 0) return@LaunchedEffect
        queueListState.scrollToItem(activeIndex)
        repeat(2) {
            delay(if (it == 0) 48 else 120)
            val activeInfo = queueListState.layoutInfo.visibleItemsInfo
                .firstOrNull { item -> item.index == activeIndex }
                ?: return@repeat
            val viewportCenter = (
                queueListState.layoutInfo.viewportStartOffset +
                    queueListState.layoutInfo.viewportEndOffset
                ) / 2
            val itemCenter = activeInfo.offset + activeInfo.size / 2
            queueListState.scrollBy((itemCenter - viewportCenter).toFloat())
        }
    }

    WorkbenchCard(
        modifier = modifier.testTag("formal-queue-tabs"),
        tone = AppCardTone.NEUTRAL,
        contentPadding = PaddingValues(if (expandedLayout) 16.dp else 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "下载列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (queue.isNotEmpty()) {
                    TextButton(
                        onClick = { onBulkSelectionChanged(eligibleIds, !allSelected) },
                        enabled = eligibleIds.isNotEmpty(),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("queue-select-page"),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (allSelected) "取消全选" else "全选")
                    }
                }
            }
            Box(modifier = Modifier.testTag("queue-selection-inline")) {
                Spacer(Modifier.size(0.dp))
            }
            Spacer(Modifier.height(if (expandedLayout) 8.dp else 4.dp))
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂无下载任务", fontWeight = FontWeight.SemiBold)
                        Text("智能读取后，作品会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    ) {
                        itemsIndexed(queue, key = { _, item -> item.task.taskId }) { index, queued ->
                            QueueRow(
                                queued = queued,
                                onSelectionChanged = onSelectionChanged,
                                onResolutionChanged = onResolutionChanged,
                                onDeleteQueued = onDeleteQueued,
                                onRetryQueued = onRetryQueued,
                                expandedLayout = expandedLayout,
                            )
                            if (index < queue.lastIndex) HorizontalDivider()
                        }
                    }
                    QueueScrollIndicator(
                        state = queueListState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(8.dp)
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .testTag("queue-scroll-indicator"),
                    )
                }
            }
            if (queue.isNotEmpty()) {
                HorizontalDivider()
                Button(
                    onClick = onStartDownloads,
                    enabled = queue.any { it.task.selected && it.task.status in setOf(
                        DownloadTaskStatus.WAITING,
                        DownloadTaskStatus.PAUSED,
                        DownloadTaskStatus.WAITING_NETWORK,
                    ) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .height(40.dp)
                        .testTag("queue-start-selected"),
                ) {
                    Text("开始下载", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun QueueScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleItems = state.layoutInfo.visibleItemsInfo.size
    if (totalItems <= visibleItems || visibleItems == 0) return

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        val thumbFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.18f, 1f)
        val thumbHeight = maxHeight * thumbFraction
        val lastStartIndex = (totalItems - visibleItems).coerceAtLeast(1)
        val scrollFraction = (state.firstVisibleItemIndex.toFloat() / lastStartIndex).coerceIn(0f, 1f)
        val thumbOffset = (maxHeight - thumbHeight) * scrollFraction
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = thumbOffset)
                .height(thumbHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun QueueRow(
    queued: QueuedDownload,
    onSelectionChanged: (String, Boolean) -> Unit,
    onResolutionChanged: (String, ResolutionPreset) -> Unit,
    onDeleteQueued: (String) -> Unit,
    onRetryQueued: (String) -> Unit,
    expandedLayout: Boolean,
) {
    val task = queued.task
    val active = task.status in setOf(
        DownloadTaskStatus.PARSING,
        DownloadTaskStatus.DOWNLOADING,
        DownloadTaskStatus.VALIDATING,
    )
    val canExpand = active || task.status == DownloadTaskStatus.FAILED
    var detailsExpanded by rememberSaveable(task.taskId) { mutableStateOf(active) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(active, task.updatedAt) {
        if (!active) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(active) {
        if (active) detailsExpanded = true
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = canExpand) { detailsExpanded = !detailsExpanded }
            .testTag("queue-row-${task.taskId}"),
        shape = RoundedCornerShape(14.dp),
        color = if (active) Color(0xFFFFF3D6) else Color.Transparent,
        border = if (active) BorderStroke(1.dp, Color(0xFFFFB020)) else null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(if (expandedLayout) 78.dp else 70.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                QueueLeadingControl(task) { onSelectionChanged(task.taskId, it) }
                if (expandedLayout) Thumbnail(queued, Modifier.size(width = 64.dp, height = 48.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        queued.media.title,
                        maxLines = if (active || task.status == DownloadTaskStatus.FAILED) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        queued.media.creator,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (task.status == DownloadTaskStatus.WAITING) {
                            ResolutionMenu(task.resolution, true) { onResolutionChanged(task.taskId, it) }
                        } else {
                            ResolutionBadge(task.resolution)
                        }
                        Text(
                            queueStatusText(queued),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = queueStatusColor(task.status),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
                if (canExpand) {
                    Text(
                        if (detailsExpanded) "收起" else "详情",
                        color = if (active) Color(0xFFB85C00) else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (task.status == DownloadTaskStatus.FAILED) {
                    IconButton(
                        onClick = { onRetryQueued(task.taskId) },
                        modifier = Modifier.size(34.dp).testTag("queue-retry-${task.taskId}"),
                    ) {
                        Icon(Icons.Filled.Downloading, "重试下载", tint = FailureRed, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(
                    onClick = { onDeleteQueued(task.taskId) },
                    modifier = Modifier.size(34.dp).testTag("queue-delete-${task.taskId}"),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "从队列删除 ${queued.media.title}",
                        tint = FailureRed,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            if (detailsExpanded && active) {
                ActiveQueueDetails(queued, nowMillis)
            } else if (detailsExpanded && task.status == DownloadTaskStatus.FAILED) {
                Text(
                    UserFacingErrorPresenter.message(task.errorSummary, queued.media.platform),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 2.dp),
                    color = FailureRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QueueLeadingControl(task: DownloadTask, onSelectionChanged: (Boolean) -> Unit) {
    val selectable = task.status in setOf(DownloadTaskStatus.WAITING, DownloadTaskStatus.PAUSED, DownloadTaskStatus.WAITING_NETWORK)
    val icon = when {
        selectable && task.selected -> Icons.Outlined.CheckCircleOutline
        selectable -> Icons.Filled.RadioButtonUnchecked
        task.status in setOf(DownloadTaskStatus.PARSING, DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.VALIDATING) -> Icons.Filled.Downloading
        task.status == DownloadTaskStatus.FAILED -> Icons.Filled.ErrorOutline
        task.status == DownloadTaskStatus.SKIPPED -> Icons.Filled.SkipNext
        task.status == DownloadTaskStatus.COMPLETED -> Icons.Outlined.CheckCircleOutline
        else -> Icons.Filled.RadioButtonUnchecked
    }
    val tint = when {
        task.status in setOf(DownloadTaskStatus.PARSING, DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.VALIDATING) -> Color(0xFFE97800)
        task.status == DownloadTaskStatus.FAILED -> FailureRed
        task.status == DownloadTaskStatus.SKIPPED -> QualityPurple
        selectable && task.selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = selectable) { onSelectionChanged(!task.selected) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = if (selectable) if (task.selected) "取消选择" else "选择任务" else queueStatusLabel(task.status),
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ActiveQueueDetails(queued: QueuedDownload, nowMillis: Long) {
    val task = queued.task
    val audioTranscoding = isAudioTranscoding(task)
    val healthController = remember(task.taskId) { TransferHealthNoticeController() }
    val healthNotice = if (audioTranscoding) null else healthController.update(task, nowMillis)
    val animatedProgress by animateFloatAsState(
        targetValue = queued.progress(), animationSpec = quickFeedbackTween(), label = "queue-download-progress",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 41.dp, end = 8.dp, bottom = 4.dp)
            .testTag("queue-active-detail-${task.taskId}"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(5.dp),
            color = Color(0xFFE97800), trackColor = Color(0xFFFFD99A),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (audioTranscoding) "正在提取音频" else "速度 ${formatSpeed(queued.task.speedBytesPerSecond)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("${(animatedProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB85C00))
        }
        Text("已下载 ${formatBytes(queued.task.downloadedBytes)} / ${formatBytes(queued.task.totalBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (audioTranscoding) {
                "转换源已下载完成 · 正在生成真实 MP3"
            } else {
                "剩余 ${formatDuration(queued.task.remainingSeconds)} · ${connectionLabel(task)}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (audioTranscoding) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("queue-audio-transcoding-${task.taskId}"),
                color = Color(0xFFFFE7BD),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "转换源已下载完成，正在提取音轨并生成 MP3。请保持 App 运行，完成后会自动保存到音频目录。",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    color = Color(0xFF9B4B00),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        healthNotice?.let { notice ->
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("queue-transfer-health-${task.taskId}"),
                color = if (notice.warning) Color(0xFFFFE7BD) else Color(0xFFE9F7EF),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    notice.message,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    color = if (notice.warning) Color(0xFF9B4B00) else Color(0xFF146C3A),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

internal data class TransferHealthNotice(val message: String, val warning: Boolean)

internal class TransferHealthNoticeController(
    private val slowConfirmMillis: Long = 5_000L,
    private val recoveryConfirmMillis: Long = 8_000L,
) {
    private var slowSinceMillis: Long? = null
    private var recoveredSinceMillis: Long? = null
    private var displayedWarning: WarningKind? = null

    fun update(task: DownloadTask, nowMillis: Long): TransferHealthNotice? {
        if (task.status != DownloadTaskStatus.DOWNLOADING) {
            reset()
            return null
        }
        val slow = task.speedBytesPerSecond < SLOW_SPEED_THRESHOLD_BYTES_PER_SECOND
        val stalledSeconds = ((nowMillis - task.updatedAt).coerceAtLeast(0L) / 1_000L)
        if (slow) {
            recoveredSinceMillis = null
            val slowSince = slowSinceMillis ?: nowMillis.also { slowSinceMillis = it }
            val candidate = when {
                task.speedBytesPerSecond <= 0L && stalledSeconds >= 15L -> WarningKind.STALLED
                nowMillis - slowSince >= slowConfirmMillis -> WarningKind.SLOW
                else -> null
            }
            if (candidate != null) displayedWarning = candidate
        } else {
            slowSinceMillis = null
            if (displayedWarning != null) {
                val recoveredSince = recoveredSinceMillis ?: nowMillis.also { recoveredSinceMillis = it }
                if (nowMillis - recoveredSince >= recoveryConfirmMillis) {
                    displayedWarning = null
                    recoveredSinceMillis = null
                }
            }
        }
        return when (displayedWarning) {
            WarningKind.STALLED -> TransferHealthNotice(
                "下载已连续 $stalledSeconds 秒没有收到新数据。" +
                    "解决办法：请先等待 App 自动续传；若持续不动，请检查网络或代理后点击重试。",
                warning = true,
            )
            WarningKind.SLOW -> TransferHealthNotice(
                "下载速度已持续低于 600 KB/s。" +
                    "解决办法：请检查 Wi-Fi、代理或平台网络；App 会继续下载，无需反复点击重试。",
                warning = true,
            )
            null -> TransferHealthNotice("连接正常，正在持续下载。", warning = false)
        }
    }

    private fun reset() {
        slowSinceMillis = null
        recoveredSinceMillis = null
        displayedWarning = null
    }

    private enum class WarningKind { SLOW, STALLED }

    private companion object {
        const val SLOW_SPEED_THRESHOLD_BYTES_PER_SECOND = 600L * 1024L
    }
}

private fun connectionLabel(task: DownloadTask): String = when (task.connectionMode) {
    com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode.MULTI -> "多连接 ×${task.connectionCount.coerceAtLeast(2)}"
    com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode.SINGLE -> "单连接"
    com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode.UNKNOWN -> "正在探测连接"
}

@Composable
private fun queueStatusColor(status: DownloadTaskStatus): Color = when (status) {
    DownloadTaskStatus.PARSING, DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.VALIDATING -> Color(0xFFB85C00)
    DownloadTaskStatus.FAILED -> FailureRed
    DownloadTaskStatus.SKIPPED -> QualityPurple
    DownloadTaskStatus.WAITING_NETWORK -> WarmOrange
    DownloadTaskStatus.COMPLETED -> SuccessGreen
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun queueStatusLabel(status: DownloadTaskStatus): String = when (status) {
    DownloadTaskStatus.WAITING -> "等待下载"
    DownloadTaskStatus.PARSING -> "正在解析"
    DownloadTaskStatus.DOWNLOADING -> "正在下载"
    DownloadTaskStatus.PAUSED -> "已暂停"
    DownloadTaskStatus.WAITING_NETWORK -> "等待网络"
    DownloadTaskStatus.VALIDATING -> "正在校验"
    DownloadTaskStatus.COMPLETED -> "已完成"
    DownloadTaskStatus.SKIPPED -> "已跳过"
    DownloadTaskStatus.FAILED -> "下载失败"
    DownloadTaskStatus.CANCELLED -> "已取消"
}

private fun queueStatusText(queued: QueuedDownload): String = when (queued.task.status) {
    DownloadTaskStatus.WAITING -> "等待下载"
    DownloadTaskStatus.PARSING -> "正在解析下载地址"
    DownloadTaskStatus.DOWNLOADING -> audioTaskPhaseText(queued.task)
        ?: "下载中 ${(queued.progress() * 100).toInt()}%"
    DownloadTaskStatus.PAUSED -> "已暂停"
    DownloadTaskStatus.WAITING_NETWORK ->
        "等待网络：${UserFacingErrorPresenter.message(queued.task.errorSummary, queued.media.platform)}"
    DownloadTaskStatus.VALIDATING -> if (queued.task.resolution == ResolutionPreset.AUDIO_MP3) {
        "正在校验 MP3 文件"
    } else {
        "正在校验文件"
    }
    DownloadTaskStatus.COMPLETED -> "已完成"
    DownloadTaskStatus.SKIPPED -> "已跳过重复文件"
    DownloadTaskStatus.FAILED ->
        "失败：${UserFacingErrorPresenter.message(queued.task.errorSummary, queued.media.platform)}"
    DownloadTaskStatus.CANCELLED -> "已取消"
}

internal fun audioTaskPhaseText(task: DownloadTask): String? {
    if (task.resolution != ResolutionPreset.AUDIO_MP3 ||
        task.status != DownloadTaskStatus.DOWNLOADING
    ) {
        return null
    }
    return if (isAudioTranscoding(task)) {
        "正在提取音频并生成 MP3"
    } else {
        val progress = if (task.totalBytes > 0L) {
            ((task.downloadedBytes.toDouble() / task.totalBytes) * 100)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }
        "正在下载音频转换源 $progress%"
    }
}

private fun isAudioTranscoding(task: DownloadTask): Boolean =
    task.resolution == ResolutionPreset.AUDIO_MP3 &&
        task.status == DownloadTaskStatus.DOWNLOADING &&
        task.totalBytes > 0L &&
        task.downloadedBytes >= task.totalBytes

@Composable
private fun ResolutionBadge(resolution: ResolutionPreset) {
    val accent = resolutionAccent(resolution)
    Surface(
        modifier = Modifier
            .height(26.dp)
            .widthIn(min = 50.dp)
            .testTag("resolution-badge-${resolution.name}"),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.48f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                resolutionLabel(resolution),
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
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
    val accent = resolutionAccent(resolution)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier
                .height(26.dp)
                .widthIn(min = 50.dp)
                .testTag("resolution-badge-${resolution.name}"),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.58f else 0.38f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = accent,
                disabledContentColor = accent.copy(alpha = 0.76f),
            ),
        ) {
            Text(
                resolutionLabel(resolution),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ResolutionPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(resolutionLabel(preset), color = resolutionAccent(preset)) },
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
private fun TotalProgressCard(queue: List<QueuedDownload>, completedCount: Int = 0) {
    val downloading = queue.count { it.task.status == DownloadTaskStatus.DOWNLOADING }
    val waiting = queue.count { it.task.status in setOf(DownloadTaskStatus.WAITING, DownloadTaskStatus.PARSING, DownloadTaskStatus.VALIDATING) }
    val downloaded = queue.sumOf { it.task.downloadedBytes.coerceAtLeast(0L) }
    val total = queue.sumOf { it.task.totalBytes.coerceAtLeast(0L) }
    val progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = quickFeedbackTween(),
        label = "total-download-progress",
    )

    WorkbenchCard(
        modifier = Modifier.testTag("home-side-total-progress"),
        tone = AppCardTone.NEUTRAL,
    ) {
        Text("任务总进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp)
                Text("${(animatedProgress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("总任务数  ${queue.size + completedCount}", style = MaterialTheme.typography.bodyMedium)
                Text("已完成  $completedCount", style = MaterialTheme.typography.bodyMedium)
                Text("进行中  $downloading", style = MaterialTheme.typography.bodyMedium)
                Text("等待中  $waiting", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun quickFeedbackTween() = tween<Float>(
    durationMillis = 140,
    easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f),
)

@Composable
private fun DefaultQualityCard(defaultResolution: ResolutionPreset) {
    WorkbenchCard(
        modifier = Modifier.testTag("home-side-download-quality"),
        tone = AppCardTone.PURPLE,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("下载质量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.nanzhufeng.videodownloader.core.ui.QualityPurple)
            Text(
                resolutionLabel(defaultResolution),
                color = resolutionAccent(defaultResolution),
                fontWeight = FontWeight.SemiBold,
            )
            Text("新任务默认使用此质量，可在队列中单独调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    dense: Boolean = false,
    onActionsBottomChanged: (Float) -> Unit = {},
) {
    WorkbenchCard(
        modifier = Modifier.testTag("formal-read-entry"),
        tone = AppCardTone.ORANGE,
        contentPadding = PaddingValues(if (dense) 12.dp else 10.dp),
    ) {
        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(if (dense) 7.dp else 6.dp)) {
            Text(
                "添加任务",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WarmOrange,
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home-input"),
                minLines = 2,
                maxLines = if (dense) 2 else 3,
                leadingIcon = {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(input))
                            Toast.makeText(context, "已复制输入内容", Toast.LENGTH_SHORT).show()
                        },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.testTag("copy-input"),
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = "复制输入框内容")
                    }
                },
                trailingIcon = {
                    Text(
                        "${input.length}/1000",
                        modifier = Modifier.testTag("input-character-count").padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                placeholder = { Text("支持抖音、YouTube、TikTok、哔哩哔哩、小红书链接或分享文本") },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        onActionsBottomChanged(coordinates.boundsInRoot().bottom)
                    }
                    .testTag("read-entry-actions"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSmartRead,
                    enabled = input.isNotBlank() && input.length <= 1000 && !isReading,
                    modifier = Modifier.weight(1f).height(if (dense) 48.dp else 44.dp).testTag("smart-read"),
                    colors = ButtonDefaults.buttonColors(containerColor = HermesOrange),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isReading) "正在读取" else "智能读取")
                }
                OutlinedButton(
                    onClick = { onInputChange("") },
                    enabled = input.isNotEmpty() && !isReading,
                    modifier = Modifier.height(if (dense) 48.dp else 44.dp).testTag("clear-input"),
                ) {
                    Text("清空")
                }
            }
            if (notice.isNotBlank() && !notice.startsWith("已加入")) {
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

private fun QueuedDownload.progress(): Float = if (task.totalBytes > 0L) {
    (task.downloadedBytes.toFloat() / task.totalBytes).coerceIn(0f, 1f)
} else {
    0f
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

internal fun resolutionLabel(value: ResolutionPreset): String = when (value) {
    ResolutionPreset.BEST -> "最佳画质"
    ResolutionPreset.UP_TO_1080P -> "1080p"
    ResolutionPreset.UP_TO_720P -> "720p"
    ResolutionPreset.UP_TO_360P -> "360p"
    ResolutionPreset.AUDIO_MP3 -> "仅音频"
}

internal fun resolutionAccent(value: ResolutionPreset): Color = when (value) {
    ResolutionPreset.BEST -> QualityPurple
    ResolutionPreset.UP_TO_1080P -> ForestGreen
    ResolutionPreset.UP_TO_720P -> QualityPurple
    ResolutionPreset.UP_TO_360P -> StorageOchre
    ResolutionPreset.AUDIO_MP3 -> WarmOrange
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
