package com.nanzhufeng.videodownloader.domain.download

import android.content.Context
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.domain.download.audio.AudioTranscoder
import com.nanzhufeng.videodownloader.domain.download.audio.Mp3AudioTranscoder
import com.nanzhufeng.videodownloader.probe.DirectDownloadRequest
import com.nanzhufeng.videodownloader.probe.FileDownloader
import com.nanzhufeng.videodownloader.probe.HttpFileDownloader
import com.nanzhufeng.videodownloader.probe.Media3MuxProbe
import com.nanzhufeng.videodownloader.probe.MediaFileValidator
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

fun interface DownloadTransferModeSink {
    suspend fun update(taskId: String, mode: DownloadConnectionMode, connectionCount: Int)

    companion object {
        val NONE = DownloadTransferModeSink { _, _, _ -> }
    }
}

fun interface DownloadThroughputReportSink {
    suspend fun record(report: DownloadThroughputReport)

    companion object {
        val NONE = DownloadThroughputReportSink { }
    }
}

class DirectMediaTransfer(
    context: Context,
    downloader: FileDownloader = HttpFileDownloader(),
    audioTranscoder: AudioTranscoder = Mp3AudioTranscoder(),
    private val performanceReporter: DownloadPerformanceReporter = DownloadPerformanceReporter.NONE,
    private val transferModeSink: DownloadTransferModeSink = DownloadTransferModeSink.NONE,
    private val throughputReportSink: DownloadThroughputReportSink = DownloadThroughputReportSink.NONE,
    private val monotonicNanos: () -> Long = System::nanoTime,
) : MediaTransfer {
    private val applicationContext = context.applicationContext
    private val cacheRoot = File(applicationContext.cacheDir, "downloads")
    private val audioSourcePreparer = AudioSourcePreparer(audioTranscoder)
    private val streamDownloadCoordinator = StreamDownloadCoordinator(downloader)

    override suspend fun download(
        task: QueuedDownload,
        source: ResolvedMedia,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ): PreparedMedia = withContext(Dispatchers.IO) {
        val transferScope = CoroutineScope(currentCoroutineContext())
        val streamModes = ConcurrentHashMap<String, Pair<DownloadConnectionMode, Int>>()
        fun modeObserver(streamLabel: String): (DownloadConnectionMode, Int) -> Unit = { mode, count ->
            streamModes[streamLabel] = mode to count
            val snapshots = streamModes.values.toList()
            val totalConnections = snapshots.sumOf { it.second }.coerceAtLeast(1)
            val aggregateMode = if (
                snapshots.any { it.first == DownloadConnectionMode.MULTI } || totalConnections > 1
            ) {
                DownloadConnectionMode.MULTI
            } else {
                DownloadConnectionMode.SINGLE
            }
            transferScope.launch {
                transferModeSink.update(task.task.taskId, aggregateMode, totalConnections)
            }
        }
        val directory = File(cacheRoot, task.task.taskId).apply { mkdirs() }
        val cancelled = AtomicBoolean(false)
        val completionHandle = currentCoroutineContext()[Job]
            ?.invokeOnCompletion { cancelled.set(true) }
        try {
            val isAudioOnly = task.task.resolution == ResolutionPreset.AUDIO_MP3
            val primaryRequest = DirectDownloadRequest(
                url = source.videoUrl,
                headers = source.headers,
                target = File(
                    directory,
                    if (isAudioOnly) {
                        "audio-source.${source.videoExtension.safeExtension("m4a")}"
                    } else {
                        "video.${source.videoExtension.safeExtension("mp4")}"
                    },
                ),
                taskId = task.task.taskId,
                platform = task.media.platform,
                streamLabel = if (isAudioOnly) "音频源" else "视频流",
                transferPolicy = if (isAudioOnly) {
                    PlatformTransferPolicy.forAudio(task.media.platform)
                } else {
                    PlatformTransferPolicy.forPlatform(task.media.platform)
                },
                reprobeCount = source.reprobeCount,
                onModeResolved = modeObserver(if (isAudioOnly) "音频源" else "视频流"),
            )
            if (isAudioOnly) {
                val primary = downloadStreams(task, listOf(primaryRequest), cancelled, onProgress).single()
                val prepared = measureDownloadStage(
                    taskId = task.task.taskId,
                    stage = "audio_transcode",
                    reporter = performanceReporter,
                    nowNanos = monotonicNanos,
                ) {
                    audioSourcePreparer.prepare(
                        source = primary,
                        destination = File(directory, "audio.mp3"),
                        cancelled = cancelled,
                    )
                }
                if (prepared.file != primary) {
                    primary.delete()
                }
                return@withContext prepared
            }

            val audioUrl = source.audioUrl
            if (audioUrl.isNullOrBlank()) {
                val primary = downloadStreams(task, listOf(primaryRequest), cancelled, onProgress).single()
                require(MediaFileValidator.isLikelyMedia(primary)) { "下载结果不是有效媒体文件" }
                return@withContext PreparedMedia(primary, "video/mp4")
            }

            val audioRequest = DirectDownloadRequest(
                url = audioUrl,
                headers = source.headers,
                target = File(directory, "audio.${source.audioExtension.safeExtension("m4a")}"),
                taskId = task.task.taskId,
                platform = task.media.platform,
                streamLabel = "音频流",
                transferPolicy = PlatformTransferPolicy.forAudio(task.media.platform),
                reprobeCount = source.reprobeCount,
                onModeResolved = modeObserver("音频流"),
            )
            val (primary, audio) = downloadStreams(
                task,
                listOf(primaryRequest, audioRequest),
                cancelled,
                onProgress,
            )
            val merged = File(directory, "merged.mp4")
            measureDownloadStage(
                taskId = task.task.taskId,
                stage = "mux",
                reporter = performanceReporter,
                nowNanos = monotonicNanos,
            ) {
                Media3MuxProbe.merge(applicationContext, primary, audio, merged)
            }
            require(MediaFileValidator.isLikelyMedia(merged)) { "合并结果不是有效媒体文件" }
            primary.delete()
            audio.delete()
            PreparedMedia(merged, "video/mp4")
        } finally {
            completionHandle?.dispose()
        }
    }

    private suspend fun downloadStreams(
        task: QueuedDownload,
        requests: List<DirectDownloadRequest>,
        cancelled: AtomicBoolean,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ): List<File> = measureDownloadStage(
        taskId = task.task.taskId,
        stage = "network_transfer",
        reporter = performanceReporter,
        nowNanos = monotonicNanos,
    ) {
        val reports = ConcurrentLinkedQueue<DownloadThroughputReport>()
        try {
            streamDownloadCoordinator.download(
                requests = requests.map { request ->
                    request.copy(onReport = reports::add)
                },
                cancelled = cancelled,
                onProgress = { progress -> progress.forwardTo(onProgress) },
            )
        } finally {
            reports.forEach { throughputReportSink.record(it) }
        }
    }

    private suspend fun TransferProgress.forwardTo(
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ) = onProgress(downloadedBytes, totalBytes, speedBytesPerSecond, remainingSeconds)

    private fun String?.safeExtension(fallback: String): String = this
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
        ?: fallback

}
