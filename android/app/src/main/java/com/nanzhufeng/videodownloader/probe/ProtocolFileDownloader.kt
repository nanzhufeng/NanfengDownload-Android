package com.nanzhufeng.videodownloader.probe

import androidx.annotation.Keep
import com.chaquo.python.Python
import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.TransferReportOutcome
import com.nanzhufeng.videodownloader.domain.download.video.AndroidStreamRemuxer
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** Routes only extractor-labelled fragments; the established HTTP downloader stays intact. */
class ProtocolFileDownloader(
    private val direct: FileDownloader = HttpFileDownloader(),
    private val segments: (String, File, SegmentObserver) -> File = { spec, target, observer ->
        File(Python.getInstance().getModule("nanzhufeng_probe.segment_transfer")
            .callAttr("download_segments", spec, target.absolutePath, observer).toString())
    },
    private val remux: (File, File, AtomicBoolean) -> File = AndroidStreamRemuxer::remux,
) : FileDownloader {
    override fun download(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        onProgress: (Long, Long) -> Unit,
    ): File {
        if (request.downloadSpec.isBlank() && !request.remuxToMp4) {
            return direct.download(request, cancelled, onProgress)
        }
        return FragmentTargetOwnership.withTarget(request.target, cancelled) {
            downloadOwned(request, cancelled, onProgress)
        }
    }

    private fun downloadOwned(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        onProgress: (Long, Long) -> Unit,
    ): File {
        if (request.downloadSpec.isBlank() && !request.remuxToMp4) {
            return direct.download(request, cancelled, onProgress)
        }
        if (cancelled.get()) throw CancellationException("下载已取消")
        request.target.parentFile?.mkdirs()
        val fragmentConnections = if (request.downloadSpec.isBlank()) 1 else
            (request.transferPolicy?.maxConnections ?: 1).coerceIn(1, 6)
        val fragmentMode = if (fragmentConnections > 1) DownloadConnectionMode.MULTI else DownloadConnectionMode.SINGLE
        request.onModeResolved(fragmentMode, fragmentConnections)
        val startedAt = System.currentTimeMillis()
        val started = System.nanoTime()
        val observer = SegmentObserver(cancelled, onProgress, fragmentConnections, request.onPhaseMeasured)
        val directReport = AtomicReference<DownloadThroughputReport?>(null)
        var outcome = TransferReportOutcome.FAILED
        var outputBytes = 0L
        var failure: String? = null
        try {
            val raw = if (request.downloadSpec.isBlank()) {
                val rawTarget = File(request.target.parentFile, request.target.name + ".source")
                direct.download(request.copy(
                    target = rawTarget,
                    remuxToMp4 = false,
                    onReport = directReport::set,
                ), cancelled, onProgress)
            } else {
                segments(request.downloadSpec, request.target, observer)
            }
            if (cancelled.get()) throw CancellationException("下载已取消")
            val remuxStarted = System.nanoTime()
            observer.phase("container_remux_started", 0)
            val remuxed = try { remux(raw, request.target, cancelled) } finally {
                observer.phase("container_remux", (System.nanoTime() - remuxStarted) / 1_000_000L)
            }
            return remuxed.also {
                outputBytes = it.length()
                outcome = TransferReportOutcome.COMPLETED
                raw.delete()
                if (raw.name.endsWith(".segments.timeline.ts")) {
                    File(raw.absolutePath.removeSuffix(".timeline.ts")).delete()
                }
                File(request.target.absolutePath + ".hls-plan.json").delete()
            }
        } catch (error: Exception) {
            failure = "分段下载或媒体封装失败"
            if (cancelled.get() || error is CancellationException) outcome = TransferReportOutcome.CANCELLED
            if (cancelled.get()) throw CancellationException("下载已暂停或取消").apply { initCause(error) }
            if (observer.httpStatus.get() > 0) {
                throw HttpDownloadException(observer.httpStatus.get()).apply { initCause(error) }
            }
            throw error
        } finally {
            val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            request.platform?.let { platform ->
                request.onReport(DownloadThroughputReport(
                    reportId = UUID.randomUUID().toString(), taskId = request.taskId,
                    platform = platform, streamLabel = "${request.streamLabel}（分段下载与封装）",
                    outcome = outcome,
                    connectionMode = directReport.get()?.connectionMode ?: fragmentMode,
                    connectionCount = directReport.get()?.connectionCount ?: fragmentConnections,
                    rangeSupported = false, expectedBytes = 0, committedBytes = outputBytes,
                    networkBytes = directReport.get()?.networkBytes ?: observer.networkBytes.get(),
                    startedAt = startedAt,
                    finishedAt = startedAt + elapsed, elapsedMillis = elapsed,
                    averageBytesPerSecond = (directReport.get()?.networkBytes ?: observer.networkBytes.get()) *
                        1000L / elapsed,
                    peakBytesPerSecond = directReport.get()?.peakBytesPerSecond ?: 0,
                    retryCount = directReport.get()?.retryCount ?: 0,
                    reprobeCount = request.reprobeCount,
                    fallbackReason = if (request.downloadSpec.isBlank()) {
                        "直链容器重新封装为 MP4"
                    } else {
                        "有限并行分片、顺序合并；网络字节包含清单；峰值与内部重试次数未统计"
                    },
                    errorSummary = failure,
                ))
            }
        }
    }
}

/** Own the entire fragment/normalization/mux lifetime, including a cancelled writer's unwind. */
private object FragmentTargetOwnership {
    private class Owner(val lock: ReentrantLock = ReentrantLock(), var users: Int = 0)
    private val guard = Any()
    private val owners = mutableMapOf<String, Owner>()

    fun <T> withTarget(target: File, stopped: AtomicBoolean, body: () -> T): T {
        val key = target.canonicalPath
        val owner = synchronized(guard) {
            owners.getOrPut(key, ::Owner).also { it.users++ }
        }
        var acquired = false
        try {
            while (!acquired) {
                if (stopped.get() || Thread.currentThread().isInterrupted) {
                    throw CancellationException("等待原下载处理结束时已取消")
                }
                acquired = owner.lock.tryLock(100, TimeUnit.MILLISECONDS)
            }
            if (stopped.get()) throw CancellationException("下载已取消")
            return body()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("等待原下载处理结束时已取消").apply { initCause(error) }
        } finally {
            if (acquired) owner.lock.unlock()
            synchronized(guard) {
                owner.users--
                if (owner.users == 0) owners.remove(key)
            }
        }
    }
}

/** Chaquopy calls these methods reflectively; keep them in formal R8 builds too. */
@Keep
class SegmentObserver(
    private val stopped: AtomicBoolean,
    private val callback: (Long, Long) -> Unit,
    private val connectionBudget: Int = 1,
    private val phaseCallback: (String, Long) -> Unit = { _, _ -> },
) {
    val networkBytes = AtomicLong(0)
    val httpStatus = AtomicInteger(0)
    fun cancelled(): Boolean = stopped.get()
    fun connections(): Int = connectionBudget
    fun phase(name: String, elapsedMillis: Long) = phaseCallback(name, elapsedMillis)
    fun progress(downloaded: Long, total: Long) = callback(downloaded, total)
    fun network(bytes: Long) { networkBytes.set(bytes) }
    fun httpFailure(status: Int) { httpStatus.set(status) }
}
