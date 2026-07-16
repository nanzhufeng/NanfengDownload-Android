package com.nanzhufeng.videodownloader.probe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ProbeScreen(
    onOpenDouyin: (String) -> Unit,
    viewModel: ProbeViewModel = viewModel(),
) {
    var input by rememberSaveable { mutableStateOf(DEFAULT_YOUTUBE_PROBE_URL) }
    val state by viewModel.uiState.collectAsState()
    val report by viewModel.report.collectAsState()
    val creatorCatalog by viewModel.creatorCatalog.collectAsState()
    val running = state is ProbeUiState.Running

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("南烛枫 Android 可行性验证", style = MaterialTheme.typography.headlineSmall)
            Text(
                "验证公开单视频、TikTok 作者作品列表和抖音目标流。",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("抖音、YouTube 或 TikTok 分享文本") },
                minLines = 3,
                maxLines = 6,
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 700.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProbeActions(
                            input = input,
                            running = running,
                            canLoadMore = creatorCatalog?.hasMore == true,
                            viewModel = viewModel,
                            onOpenDouyin = onOpenDouyin,
                            modifier = Modifier.weight(1f),
                        )
                        ProbeResultCard(
                            state = state,
                            report = report,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProbeActions(
                            input = input,
                            running = running,
                            canLoadMore = creatorCatalog?.hasMore == true,
                            viewModel = viewModel,
                            onOpenDouyin = onOpenDouyin,
                        )
                        ProbeResultCard(state = state, report = report)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeActions(
    input: String,
    running: Boolean,
    canLoadMore: Boolean,
    viewModel: ProbeViewModel,
    onOpenDouyin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProbeButton("检查 Python/yt-dlp", running) { viewModel.checkRuntime() }
        ProbeButton("解析 YouTube / TikTok 单视频", running) { viewModel.parseSingle(input) }
        ProbeButton("下载 YouTube / TikTok 单视频", running) { viewModel.downloadSingle(input) }
        ProbeButton("读取 TikTok 作者作品", running) { viewModel.parseTiktokCreator(input) }
        ProbeButton("加载更多 TikTok 作品", running, enabled = canLoadMore) {
            viewModel.loadMoreTiktokCreator()
        }
        ProbeButton("打开抖音登录/探测页", running) {
            onOpenDouyin(douyinUrlOrHome(input))
        }
        ProbeButton("下载捕获的抖音流", running) { viewModel.downloadCapturedDouyin() }
        ProbeButton("写入 Movies 公共目录", running) { viewModel.writeLatestToMovies() }
    }
}

@Composable
private fun ProbeButton(
    label: String,
    running: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !running && enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

@Composable
private fun ProbeResultCard(
    state: ProbeUiState,
    report: ProbeReport,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stateText(state), style = MaterialTheme.typography.titleMedium)
            Text(report.title, style = MaterialTheme.typography.bodyLarge)
            Text(report.detail, style = MaterialTheme.typography.bodyMedium)
            report.fileSize?.let { Text("文件大小：$it 字节") }
            report.outputUri?.let { Text("输出 URI：$it") }
        }
    }
}

private fun stateText(state: ProbeUiState): String = when (state) {
    ProbeUiState.Idle -> "等待验证"
    is ProbeUiState.Running -> "正在执行：${state.stage}"
    is ProbeUiState.Passed -> "通过：${state.message}"
    is ProbeUiState.Failed -> "失败：${state.stage}\n${state.message}"
}

private fun douyinUrlOrHome(input: String): String = runCatching {
    UrlClassifier.extractAndClassify(input)
}.getOrNull()?.takeIf { it.platform == Platform.DOUYIN }?.url ?: DEFAULT_DOUYIN_PROBE_URL

private const val DEFAULT_YOUTUBE_PROBE_URL = "https://youtu.be/dQw4w9WgXcQ"
private const val DEFAULT_DOUYIN_PROBE_URL = "https://v.douyin.com/uS14j-Frr6g/"
