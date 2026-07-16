package com.nanzhufeng.videodownloader.domain.download

import android.content.Context
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.DirectDownloadRequest
import com.nanzhufeng.videodownloader.probe.HttpFileDownloader
import com.nanzhufeng.videodownloader.probe.Media3MuxProbe
import com.nanzhufeng.videodownloader.probe.MediaFileValidator
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class DirectMediaTransfer(
    context: Context,
    private val downloader: HttpFileDownloader = HttpFileDownloader(),
) : MediaTransfer {
    private val applicationContext = context.applicationContext
    private val cacheRoot = File(applicationContext.cacheDir, "downloads")

    override suspend fun download(
        task: QueuedDownload,
        source: ResolvedMedia,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ): PreparedMedia = withContext(Dispatchers.IO) {
        val directory = File(cacheRoot, task.task.taskId).apply { mkdirs() }
        val cancelled = AtomicBoolean(false)
        val completionHandle = currentCoroutineContext()[Job]
            ?.invokeOnCompletion { cancelled.set(true) }
        try {
            val video = downloadStream(
                url = source.videoUrl,
                headers = source.headers,
                target = File(directory, "video.${source.videoExtension.safeExtension("mp4")}"),
                cancelled = cancelled,
                baseBytes = 0L,
                onProgress = onProgress,
            )
            if (task.task.resolution == ResolutionPreset.AUDIO_MP3) {
                require(source.videoExtension.equals("mp3", ignoreCase = true)) {
                    "当前来源没有提供可直接保存的 MP3 音频流"
                }
                return@withContext PreparedMedia(video, "audio/mpeg")
            }

            val audioUrl = source.audioUrl
            if (audioUrl.isNullOrBlank()) {
                require(MediaFileValidator.isLikelyMedia(video)) { "下载结果不是有效媒体文件" }
                return@withContext PreparedMedia(video, "video/mp4")
            }

            val videoBytes = video.length()
            val audio = downloadStream(
                url = audioUrl,
                headers = source.headers,
                target = File(directory, "audio.${source.audioExtension.safeExtension("m4a")}"),
                cancelled = cancelled,
                baseBytes = videoBytes,
                onProgress = onProgress,
            )
            val merged = File(directory, "merged.mp4")
            Media3MuxProbe.merge(applicationContext, video, audio, merged)
            require(MediaFileValidator.isLikelyMedia(merged)) { "合并结果不是有效媒体文件" }
            video.delete()
            audio.delete()
            PreparedMedia(merged, "video/mp4")
        } finally {
            completionHandle?.dispose()
        }
    }

    private fun downloadStream(
        url: String,
        headers: Map<String, String>,
        target: File,
        cancelled: AtomicBoolean,
        baseBytes: Long,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit,
    ): File {
        if (MediaFileValidator.isLikelyMedia(target)) return target
        var lastBytes = 0L
        var lastNanos = System.nanoTime()
        var lastReportedNanos = 0L
        return downloader.download(
            request = DirectDownloadRequest(url, headers, target),
            cancelled = cancelled,
        ) { downloaded, total ->
            val now = System.nanoTime()
            if (now - lastReportedNanos < PROGRESS_INTERVAL_NANOS && downloaded != total) {
                return@download
            }
            val elapsedSeconds = ((now - lastNanos).coerceAtLeast(1L)) / 1_000_000_000.0
            val speed = ((downloaded - lastBytes).coerceAtLeast(0L) / elapsedSeconds).toLong()
            val combinedDownloaded = baseBytes + downloaded
            val combinedTotal = if (total > 0L) baseBytes + total else 0L
            val remaining = if (speed > 0L && combinedTotal > combinedDownloaded) {
                (combinedTotal - combinedDownloaded) / speed
            } else {
                null
            }
            runBlocking {
                onProgress(combinedDownloaded, combinedTotal, speed, remaining)
            }
            lastBytes = downloaded
            lastNanos = now
            lastReportedNanos = now
        }
    }

    private fun String?.safeExtension(fallback: String): String = this
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
        ?: fallback

    private companion object {
        const val PROGRESS_INTERVAL_NANOS = 250_000_000L
    }
}
