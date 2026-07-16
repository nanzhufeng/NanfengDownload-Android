package com.nanzhufeng.videodownloader.probe

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProbeUiState {
    data object Idle : ProbeUiState
    data class Running(val stage: String) : ProbeUiState
    data class Passed(val message: String) : ProbeUiState
    data class Failed(val stage: String, val message: String) : ProbeUiState
}

data class ProbeReport(
    val title: String = "尚未开始",
    val detail: String = "请选择一项验证操作。",
    val fileSize: Long? = null,
    val outputUri: String? = null,
)

class ProbeViewModel(application: Application) : AndroidViewModel(application) {
    private val ytDlpProbe by lazy { YtDlpProbe() }
    private val downloader = HttpFileDownloader()
    private val cancelled = AtomicBoolean(false)

    private val _uiState = MutableStateFlow<ProbeUiState>(ProbeUiState.Idle)
    val uiState: StateFlow<ProbeUiState> = _uiState.asStateFlow()

    private val _report = MutableStateFlow(ProbeReport())
    val report: StateFlow<ProbeReport> = _report.asStateFlow()

    private var activeJob: Job? = null
    private var parsedSourceUrl: String? = null
    private var mediaInfo: YtDlpMediaInfo? = null
    private var creatorCatalog: CreatorCatalog? = null
    private var latestOutput: File? = null

    fun checkRuntime() = runStage("检查 Python/yt-dlp") {
        val runtime = ytDlpProbe.runtimeInfo()
        _report.value = ProbeReport(
            title = "运行环境可用",
            detail = "Python ${runtime.python}，yt-dlp ${runtime.ytDlp}",
        )
        "Python 与 yt-dlp 已加载"
    }

    fun parseSingle(input: String) = runStage("解析 YouTube / TikTok 单视频") {
        val source = requireSingle(input)
        val started = System.nanoTime()
        val info = ytDlpProbe.extractSingle(source.url)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        parsedSourceUrl = source.url
        mediaInfo = info
        _report.value = ProbeReport(
            title = info.title,
            detail = "平台：${platformLabel(info.platform)}\n作者：${info.creator}\n" +
                "解析耗时：${elapsedMs}ms\n" +
                if (info.audioUrl == null) "单文件音视频" else "独立音视频流",
        )
        "只解析到 1 个${platformLabel(info.platform)}视频"
    }

    fun downloadSingle(input: String) = runStage("下载 YouTube / TikTok 单视频") {
        val source = requireSingle(input)
        val info = if (parsedSourceUrl == source.url) {
            mediaInfo ?: ytDlpProbe.extractSingle(source.url)
        } else {
            ytDlpProbe.extractSingle(source.url)
        }
        parsedSourceUrl = source.url
        mediaInfo = info
        val output = downloadMedia(info)
        latestOutput = output
        _report.value = ProbeReport(
            title = info.title,
            detail = "平台：${platformLabel(info.platform)}\n作者：${info.creator}\n" +
                "文件：${output.name}",
            fileSize = output.length(),
        )
        "${platformLabel(info.platform)}下载${if (info.audioUrl == null) "" else "与合并"}完成"
    }

    fun parseTiktokCreator(input: String) = runStage("读取 TikTok 作者作品") {
        val source = requireTiktokCreator(input)
        val catalog = ytDlpProbe.extractCreator(source.url)
        creatorCatalog = catalog
        val preview = catalog.entries.take(8).joinToString("\n") {
            "${it.id}  ${it.title}"
        }
        _report.value = ProbeReport(
            title = "${catalog.creator}：${catalog.entries.size} 个公开作品",
            detail = "去重 ${catalog.duplicateCount}，剔除其他作者 ${catalog.foreignCount}" +
                if (preview.isBlank()) "" else "\n$preview",
        )
        "TikTok 作者作品读取完成"
    }

    fun downloadCapturedDouyin() = runStage("下载捕获的抖音流") {
        val mediaUrl = requireNotNull(DouyinCaptureStore.latestMediaUrl) {
            "尚未捕获目标抖音作品的视频流"
        }
        val pageUrl = requireNotNull(DouyinCaptureStore.latestPageUrl) {
            "捕获结果缺少目标作品页"
        }
        val cookie = CookieManager.getInstance().getCookie(mediaUrl).orEmpty()
        val headers = buildMap {
            put("Referer", pageUrl)
            put(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/138.0 Mobile Safari/537.36",
            )
            if (cookie.isNotBlank()) put("Cookie", cookie)
        }
        val directory = freshProbeDirectory("douyin")
        val output = downloader.download(
            DirectDownloadRequest(
                url = mediaUrl,
                headers = headers,
                target = File(directory, "douyin.mp4"),
            ),
            cancelled,
        ) { downloaded, total ->
            updateTransferReport("下载抖音流", downloaded, total)
        }
        check(MediaFileValidator.isLikelyMedia(output)) {
            "捕获地址未生成有效媒体文件"
        }
        latestOutput = output
        _report.value = ProbeReport(
            title = "抖音目标作品",
            detail = "来源页：${pageUrl.substringBefore('?')}\n文件：${output.name}",
            fileSize = output.length(),
        )
        "抖音目标流下载完成"
    }

    fun writeLatestToMovies() = runStage("写入 Movies 公共目录") {
        val output = requireNotNull(latestOutput) { "尚无可写入的下载结果" }
        val displayName = "probe-${System.currentTimeMillis()}.mp4"
        val uri = MediaStoreProbe.writeVideo(getApplication(), output, displayName)
        _report.value = _report.value.copy(outputUri = uri.toString())
        "已写入 Movies/南烛枫视频下载器/Probe"
    }

    private fun runStage(stage: String, action: suspend () -> String) {
        if (activeJob?.isActive == true) return
        cancelled.set(false)
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ProbeUiState.Running(stage)
            try {
                _uiState.value = ProbeUiState.Passed(action())
            } catch (error: Throwable) {
                val type = error::class.simpleName ?: error.javaClass.simpleName
                val message = error.message?.takeIf(String::isNotBlank) ?: "没有错误详情"
                _uiState.value = ProbeUiState.Failed(stage, "$type: $message")
            }
        }
    }

    private fun requireSingle(input: String): ClassifiedSource {
        val source = UrlClassifier.extractAndClassify(input)
        val resolved = if (source.kind == SourceKind.UNKNOWN_TIKTOK_SHARE) {
            ytDlpProbe.resolveSource(source.url)
        } else {
            null
        }
        return ProbeSourcePolicy.requireSingle(source, resolved)
    }

    private fun requireTiktokCreator(input: String): ClassifiedSource {
        val source = UrlClassifier.extractAndClassify(input)
        val resolved = if (source.kind == SourceKind.UNKNOWN_TIKTOK_SHARE) {
            ytDlpProbe.resolveSource(source.url)
        } else {
            null
        }
        return ProbeSourcePolicy.requireTiktokCreator(source, resolved)
    }

    private suspend fun downloadMedia(info: YtDlpMediaInfo): File {
        val directory = freshProbeDirectory("${info.platform}-${info.id}")
        val video = downloader.download(
            DirectDownloadRequest(
                url = info.videoUrl,
                headers = info.headers,
                target = File(directory, "video.${safeExtension(info.videoExt, "mp4")}"),
            ),
            cancelled,
        ) { downloaded, total ->
            updateTransferReport("下载视频流", downloaded, total)
        }

        val output = if (info.audioUrl != null) {
            val audio = downloader.download(
                DirectDownloadRequest(
                    url = info.audioUrl,
                    headers = info.headers,
                    target = File(directory, "audio.${safeExtension(info.audioExt, "m4a")}"),
                ),
                cancelled,
            ) { downloaded, total ->
                updateTransferReport("下载音频流", downloaded, total)
            }
            Media3MuxProbe.merge(
                context = getApplication(),
                video = video,
                audio = audio,
                output = File(directory, "merged.mp4"),
            )
        } else {
            video
        }
        check(MediaFileValidator.isLikelyMedia(output)) {
            "下载结果不是有效的媒体文件"
        }
        return output
    }

    private fun freshProbeDirectory(name: String): File {
        val directory = File(getApplication<Application>().cacheDir, "probe/$name")
        directory.deleteRecursively()
        check(directory.mkdirs()) { "无法创建探测缓存目录" }
        return directory
    }

    private fun safeExtension(value: String?, fallback: String): String {
        val normalized = value.orEmpty().lowercase()
        return normalized.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: fallback
    }

    private fun updateTransferReport(stage: String, downloaded: Long, total: Long) {
        val totalText = if (total > 0L) " / ${formatBytes(total)}" else ""
        _report.value = ProbeReport(
            title = stage,
            detail = "已接收 ${formatBytes(downloaded)}$totalText",
            fileSize = downloaded,
        )
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun platformLabel(platform: String): String = when (platform.lowercase()) {
        "youtube" -> "YouTube"
        "tiktok" -> "TikTok"
        else -> platform.ifBlank { "未知平台" }
    }
}
