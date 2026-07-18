package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.probe.DirectDownloadRequest
import com.nanzhufeng.videodownloader.probe.FileDownloader
import com.nanzhufeng.videodownloader.probe.MediaFileValidator
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

internal data class TransferProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val remainingSeconds: Long?,
)

/**
 * Downloads independent media streams concurrently and reports one conflated aggregate progress stream.
 * Blocking network callbacks never wait for database/UI progress persistence.
 */
internal class StreamDownloadCoordinator(
    private val downloader: FileDownloader,
    private val isComplete: (File) -> Boolean = MediaFileValidator::isLikelyMedia,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    suspend fun download(
        requests: List<DirectDownloadRequest>,
        cancelled: AtomicBoolean,
        onProgress: suspend (TransferProgress) -> Unit,
    ): List<File> = coroutineScope {
        require(requests.isNotEmpty()) { "下载流不能为空" }
        val pending = requests.filterNot { isComplete(it.target) }
        if (pending.isEmpty()) return@coroutineScope requests.map { it.target }

        val completedBytes = requests
            .asSequence()
            .filterNot(pending::contains)
            .sumOf { it.target.length() }
        val progressChannel = Channel<TransferProgress>(Channel.CONFLATED)
        val aggregator = AggregateProgress(
            streamCount = pending.size,
            completedBytes = completedBytes,
            nowNanos = nowNanos,
        )
        val reporter = launch {
            for (progress in progressChannel) onProgress(progress)
        }
        val parentJob = currentCoroutineContext()[Job]

        try {
            val downloads = pending.mapIndexed { index, request ->
                async(Dispatchers.IO) {
                    try {
                        downloader.download(request, cancelled) { downloaded, total ->
                            if (parentJob?.isActive == false) cancelled.set(true)
                            aggregator.update(index, downloaded, total)?.let(progressChannel::trySend)
                        }
                    } catch (error: Throwable) {
                        cancelled.set(true)
                        throw error
                    }
                }
            }
            downloads.forEach { it.await() }
            pending.forEachIndexed { index, request ->
                aggregator.update(
                    index = index,
                    downloaded = request.target.length(),
                    total = request.target.length(),
                    force = index == pending.lastIndex,
                )?.let(progressChannel::trySend)
            }
            requests.map { it.target }
        } finally {
            progressChannel.close()
            listOf(reporter).joinAll()
        }
    }

    private class AggregateProgress(
        streamCount: Int,
        private val completedBytes: Long,
        private val nowNanos: () -> Long,
    ) {
        private val downloaded = LongArray(streamCount)
        private val totals = LongArray(streamCount)
        private var lastReportedBytes = completedBytes
        private var lastReportedNanos = nowNanos()

        @Synchronized
        fun update(
            index: Int,
            downloaded: Long,
            total: Long,
            force: Boolean = false,
        ): TransferProgress? {
            this.downloaded[index] = downloaded.coerceAtLeast(0L)
            totals[index] = total.coerceAtLeast(0L)
            val now = nowNanos()
            if (!force && now - lastReportedNanos < PROGRESS_INTERVAL_NANOS) return null

            val combinedDownloaded = completedBytes + this.downloaded.sum()
            val combinedTotal = if (totals.all { it > 0L }) {
                completedBytes + totals.sum()
            } else {
                0L
            }
            val elapsedSeconds = ((now - lastReportedNanos).coerceAtLeast(1L)) / 1_000_000_000.0
            val speed = ((combinedDownloaded - lastReportedBytes).coerceAtLeast(0L) / elapsedSeconds).toLong()
            val remaining = if (speed > 0L && combinedTotal > combinedDownloaded) {
                (combinedTotal - combinedDownloaded) / speed
            } else {
                null
            }
            lastReportedBytes = combinedDownloaded
            lastReportedNanos = now
            return TransferProgress(combinedDownloaded, combinedTotal, speed, remaining)
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_NANOS = 250_000_000L
    }
}
