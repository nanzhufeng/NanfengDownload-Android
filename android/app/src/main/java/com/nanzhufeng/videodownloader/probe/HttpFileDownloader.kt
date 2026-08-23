package com.nanzhufeng.videodownloader.probe

import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.TransferReportOutcome
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl

class HttpDownloadException(
    val statusCode: Int,
) : IOException("下载请求失败：HTTP $statusCode")

data class TransferPolicy(
    val platform: String,
    val maxConnections: Int,
    val segmentedThresholdBytes: Long,
    val chunkSizeBytes: Long? = null,
)

data class DirectDownloadRequest(
    val url: String,
    val headers: Map<String, String>,
    val target: File,
    /**
     * Cookies are credentials, not generic media headers. They are only sent
     * to the exact host for which the extractor obtained them and are removed
     * before every cross-host redirect.
     */
    val cookieHeader: String = "",
    val cookieHost: String = "",
    val taskId: String = "",
    val platform: DownloadPlatform? = null,
    val streamLabel: String = "媒体",
    val transferPolicy: TransferPolicy? = null,
    val reprobeCount: Int = 0,
    /** Called with the actual post-redirect URL before any response bytes are saved. */
    val finalUrlValidator: (String) -> Unit = {},
    val onModeResolved: (DownloadConnectionMode, Int) -> Unit = { _, _ -> },
    val onReport: (DownloadThroughputReport) -> Unit = {},
)

internal fun DirectDownloadRequest.headersFor(targetUrl: HttpUrl): Map<String, String> = buildMap {
    headers.forEach { (name, value) ->
        if (!name.isCredentialHeader()) put(name, value)
    }
    if (cookieHeader.isNotBlank() && cookieHost.equals(targetUrl.host, ignoreCase = true)) {
        put("Cookie", cookieHeader)
    }
}

private fun String.isCredentialHeader(): Boolean =
    equals("Cookie", ignoreCase = true) ||
        equals("Authorization", ignoreCase = true) ||
        equals("Proxy-Authorization", ignoreCase = true) ||
        equals("Host", ignoreCase = true)

fun interface FileDownloader {
    fun download(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File
}

class HttpFileDownloader(
    private val maxAttempts: Int = 6,
    private val retryDelayMillis: Long = 400L,
    private val maxSegments: Int = 6,
    private val segmentedThresholdBytes: Long = 8L * 1024L * 1024L,
    private val epochMillis: () -> Long = System::currentTimeMillis,
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val httpClient: OkHttpClient = DEFAULT_HTTP_CLIENT,
) : FileDownloader {
    override fun download(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File {
        val startedAt = epochMillis()
        val startedNanos = monotonicNanos()
        val meter = TransferMeter(monotonicNanos)
        val state = TransferReportState()
        return try {
            val file = downloadMeasured(request, cancelled, onProgress, meter, state)
            request.onReport(
                state.toReport(
                    request = request,
                    outcome = TransferReportOutcome.COMPLETED,
                    committedBytes = file.length(),
                    startedAt = startedAt,
                    elapsedMillis = elapsedMillis(startedNanos),
                    meter = meter,
                    error = null,
                ),
            )
            file
        } catch (error: Throwable) {
            request.onReport(
                state.toReport(
                    request = request,
                    outcome = if (error is CancellationException) {
                        TransferReportOutcome.CANCELLED
                    } else {
                        TransferReportOutcome.FAILED
                    },
                    committedBytes = 0L,
                    startedAt = startedAt,
                    elapsedMillis = elapsedMillis(startedNanos),
                    meter = meter,
                    error = error,
                ),
            )
            throw error
        }
    }

    private fun downloadMeasured(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        meter: TransferMeter,
        reportState: TransferReportState,
    ): File {
        val partial = File(request.target.parentFile, request.target.name + ".part")
        partial.parentFile?.mkdirs()
        var lastError: IOException? = null
        // 每次新下载调用都重新探测 Range。已有 .part 只能证明写过数据，
        // 不能证明本次进程仍掌握总长度和分块策略；跳过探测会把顺序 Range
        // 断点误降级为无上限的单个长响应，重新触发 YouTube 音频限速。
        var rangePlanResolved = false
        var rangePlan: RangePlan? = null
        val policy = request.transferPolicy ?: TransferPolicy(
            platform = request.platform?.name ?: "GENERIC",
            maxConnections = maxSegments,
            segmentedThresholdBytes = segmentedThresholdBytes,
        )

        repeat(maxAttempts) { attempt ->
            if (cancelled.get()) throw CancellationException("下载已取消")
            try {
                if (!rangePlanResolved) {
                    val probe = probeRangePlan(request, cancelled, policy)
                    rangePlan = probe.plan
                    reportState.rangeSupported = probe.rangeSupported
                    reportState.expectedBytes = probe.totalBytes
                    reportState.fallbackReason = probe.reason
                    rangePlanResolved = true
                }
                val plan = rangePlan
                if (plan != null) {
                    reportState.mode = DownloadConnectionMode.MULTI
                    reportState.connectionCount = plan.segmentCount
                    request.onModeResolved(reportState.mode, reportState.connectionCount)
                    try {
                        downloadSegmentedAttempt(request, partial, plan, cancelled, onProgress, meter)
                    } catch (_: RangeNotSupportedException) {
                        deleteSegments(partial)
                        rangePlan = null
                        reportState.mode = DownloadConnectionMode.SINGLE
                        reportState.connectionCount = 1
                        reportState.rangeSupported = false
                        reportState.fallbackReason = "分片响应不稳定，已回退单连接"
                        request.onModeResolved(reportState.mode, reportState.connectionCount)
                        downloadAttempt(request, partial, cancelled, onProgress, meter)
                    }
                } else if (
                    policy.chunkSizeBytes != null &&
                    reportState.rangeSupported &&
                    reportState.expectedBytes > 0L
                ) {
                    reportState.mode = DownloadConnectionMode.SINGLE
                    reportState.connectionCount = 1
                    request.onModeResolved(reportState.mode, reportState.connectionCount)
                    try {
                        downloadChunkedAttempt(
                            request = request,
                            partial = partial,
                            totalBytes = reportState.expectedBytes,
                            chunkSizeBytes = policy.chunkSizeBytes,
                            cancelled = cancelled,
                            onProgress = onProgress,
                            meter = meter,
                        )
                    } catch (_: RangeNotSupportedException) {
                        partial.delete()
                        reportState.rangeSupported = false
                        reportState.fallbackReason = "小分块 Range 不稳定，已回退完整单连接"
                        downloadAttempt(request, partial, cancelled, onProgress, meter)
                    }
                } else {
                    reportState.mode = DownloadConnectionMode.SINGLE
                    reportState.connectionCount = 1
                    request.onModeResolved(reportState.mode, reportState.connectionCount)
                    downloadAttempt(request, partial, cancelled, onProgress, meter)
                }
                publish(partial, request.target)
                deleteSegments(partial)
                return request.target
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpDownloadException) {
                throw error
            } catch (error: IOException) {
                lastError = error
                if (attempt == maxAttempts - 1) throw error
                reportState.retryCount += 1
                if (retryDelayMillis > 0L) {
                    Thread.sleep(retryDelayMillis * (attempt + 1))
                }
            }
        }
        throw lastError ?: IOException("下载失败，且没有错误详情")
    }

    private fun probeRangePlan(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        policy: TransferPolicy,
    ): RangeProbe {
        if (cancelled.get()) {
            return RangeProbe(reason = "平台策略使用单连接")
        }
        if (policy.maxConnections < 2 && policy.chunkSizeBytes == null) {
            return RangeProbe(reason = "平台策略使用完整单连接")
        }
        return try {
            execute(request, range = "bytes=0-0").use { response ->
                val responseCode = response.code
                if (responseCode >= 400 && responseCode != 416) {
                    throw HttpDownloadException(responseCode)
                }
                if (responseCode != HTTP_PARTIAL) {
                    return RangeProbe(reason = "服务器未返回 206 Range 响应")
                }
                val contentRange = response.header("Content-Range")
                val total = contentRange
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                    ?: return RangeProbe(reason = "Content-Range 无有效总长度")
                if (!contentRange.startsWith("bytes 0-0/")) {
                    return RangeProbe(reason = "Range 探测响应区间不匹配", totalBytes = total)
                }
                if (policy.chunkSizeBytes != null) {
                    return RangeProbe(
                        rangeSupported = true,
                        totalBytes = total,
                        reason = "使用顺序小分块 Range 单连接",
                    )
                }
                if (total < policy.segmentedThresholdBytes) {
                    return RangeProbe(
                        rangeSupported = true,
                        totalBytes = total,
                        reason = "文件小于平台分片阈值",
                    )
                }
                RangeProbe(
                    plan = RangePlan(total, policy.maxConnections.coerceAtLeast(2)),
                    rangeSupported = true,
                    totalBytes = total,
                )
            }
        } catch (error: HttpDownloadException) {
            throw error
        } catch (_: IOException) {
            RangeProbe(reason = "Range 能力探测失败，使用单连接")
        }
    }

    private fun downloadSegmentedAttempt(
        request: DirectDownloadRequest,
        partial: File,
        plan: RangePlan,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        meter: TransferMeter,
    ) {
        ensureSegmentPlan(partial, plan)
        val ranges = plan.ranges()
        val segmentFiles = ranges.indices.map { index ->
            File(partial.parentFile, "${partial.name}.segment-$index")
        }
        val progressLock = Any()
        val segmentProgress = LongArray(ranges.size) { index ->
            segmentFiles[index].takeIf(File::exists)?.length() ?: 0L
        }
        val abortAllSegments = AtomicBoolean(false)
        val futures = ranges.mapIndexed { index, range ->
            java.util.concurrent.CompletableFuture.runAsync({
                try {
                    downloadRange(
                        request = request,
                        target = segmentFiles[index],
                        range = range,
                        cancelled = cancelled,
                        abortAllSegments = abortAllSegments,
                        meter = meter,
                    ) { bytesInSegment ->
                        synchronized(progressLock) {
                            segmentProgress[index] = bytesInSegment
                            onProgress(segmentProgress.sum(), plan.totalBytes)
                        }
                    }
                } catch (error: Throwable) {
                    if (
                        error is CancellationException ||
                        error is HttpDownloadException ||
                        error is RangeNotSupportedException
                    ) {
                        abortAllSegments.set(true)
                    }
                    throw CompletionException(error)
                }
            }, SEGMENT_EXECUTOR)
        }
        val failures = futures.mapNotNull { future ->
            try {
                future.join()
                null
            } catch (error: CompletionException) {
                error.rootCause()
            }
        }
        if (failures.isNotEmpty()) {
            val cause = failures.firstOrNull { it !is CancellationException } ?: failures.first()
            when (cause) {
                is CancellationException -> throw cause
                is HttpDownloadException -> throw cause
                is RangeNotSupportedException -> throw cause
                is IOException -> throw cause
                else -> throw IOException("分段下载失败", cause)
            }
        }
        if (cancelled.get()) throw CancellationException("下载已取消")

        FileOutputStream(partial, false).buffered().use { output ->
            segmentFiles.forEach { segment -> segment.inputStream().buffered().use { it.copyTo(output) } }
        }
        if (partial.length() != plan.totalBytes) {
            throw IOException("分段合并后长度异常：${partial.length()}/${plan.totalBytes} 字节")
        }
        deleteSegments(partial)
        onProgress(plan.totalBytes, plan.totalBytes)
    }

    private fun downloadRange(
        request: DirectDownloadRequest,
        target: File,
        range: ByteRange,
        cancelled: AtomicBoolean,
        abortAllSegments: AtomicBoolean,
        meter: TransferMeter,
        onProgress: (bytesInSegment: Long) -> Unit,
    ) {
        val expectedLength = range.endInclusive - range.start + 1L
        if (target.length() > expectedLength) target.delete()
        val existing = target.takeIf(File::exists)?.length() ?: 0L
        if (existing == expectedLength) {
            onProgress(existing)
            return
        }
        val requestStart = range.start + existing
        execute(request, range = "bytes=$requestStart-${range.endInclusive}").use { response ->
            if (response.code != HTTP_PARTIAL) {
                throw RangeNotSupportedException()
            }
            val contentRange = response.header("Content-Range").orEmpty()
            if (!contentRange.startsWith("bytes $requestStart-${range.endInclusive}/")) {
                throw RangeNotSupportedException()
            }
            var downloaded = existing
            val body = response.body ?: throw IOException("下载响应缺少内容")
            body.byteStream().use { input ->
                FileOutputStream(target, true).buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    while (true) {
                        if (cancelled.get() || abortAllSegments.get()) {
                            throw CancellationException("下载已取消")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        meter.record(count)
                        downloaded += count
                        onProgress(downloaded)
                    }
                }
            }
            if (downloaded != expectedLength) {
                throw IOException("分段连接提前结束：$downloaded/$expectedLength 字节")
            }
        }
    }

    private fun downloadChunkedAttempt(
        request: DirectDownloadRequest,
        partial: File,
        totalBytes: Long,
        chunkSizeBytes: Long,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        meter: TransferMeter,
    ) {
        require(chunkSizeBytes > 0L) { "小分块大小必须大于 0" }
        if (partial.length() > totalBytes) partial.delete()
        var downloaded = partial.takeIf(File::exists)?.length() ?: 0L
        onProgress(downloaded, totalBytes)
        while (downloaded < totalBytes) {
            if (cancelled.get()) throw CancellationException("下载已取消")
            val endInclusive = minOf(downloaded + chunkSizeBytes - 1L, totalBytes - 1L)
            execute(request, range = "bytes=$downloaded-$endInclusive").use { response ->
                if (response.code != HTTP_PARTIAL) throw RangeNotSupportedException()
                val contentRange = response.header("Content-Range").orEmpty()
                if (!contentRange.startsWith("bytes $downloaded-$endInclusive/$totalBytes")) {
                    throw RangeNotSupportedException()
                }
                val body = response.body ?: throw IOException("下载响应缺少内容")
                body.byteStream().use { input ->
                    FileOutputStream(partial, true).buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE_BYTES)
                        while (downloaded <= endInclusive) {
                            if (cancelled.get()) throw CancellationException("下载已取消")
                            val remaining = endInclusive - downloaded + 1L
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            meter.record(count)
                            downloaded += count
                            onProgress(downloaded, totalBytes)
                        }
                    }
                }
                if (downloaded != endInclusive + 1L) {
                    throw IOException("小分块连接提前结束：$downloaded/${endInclusive + 1L} 字节")
                }
            }
        }
    }

    private fun downloadAttempt(
        request: DirectDownloadRequest,
        partial: File,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        meter: TransferMeter,
    ) {
        var existing = partial.takeIf(File::exists)?.length() ?: 0L
        repeat(MAX_SINGLE_CONNECTION_RESPONSES) {
            val requestedStart = existing
            var resume = false
            execute(
                request,
                range = if (requestedStart > 0L) "bytes=$requestedStart-" else null,
            ).use { response ->
            val responseCode = response.code
            if (responseCode !in 200..299) {
                throw HttpDownloadException(responseCode)
            }
                val partialRange = if (responseCode == HTTP_PARTIAL) {
                    parseContentRange(response.header("Content-Range"))
                        ?: throw IOException("Range 响应缺少有效 Content-Range")
                } else {
                    null
                }
                if (partialRange != null && partialRange.start != requestedStart) {
                    throw IOException("Range 响应起点异常：${partialRange.start}/$requestedStart")
                }
            val append = partialRange != null && requestedStart > 0L
            val start = if (append) requestedStart else 0L
            if (!append && partial.exists()) {
                partial.delete()
            }
            val body = response.body ?: throw IOException("下载响应缺少内容")
            val bodyLength = body.contentLength().coerceAtLeast(0L)
                val total = partialRange?.totalBytes ?: if (bodyLength > 0L) start + bodyLength else 0L
            var downloaded = start

            body.byteStream().use { input ->
                FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    while (true) {
                        if (cancelled.get()) {
                            throw CancellationException("下载已取消")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        meter.record(count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }
                if (downloaded == start) {
                    throw IOException("媒体响应为空，未保存任何字节")
                }
                if (total > 0L && downloaded > total) {
                    throw IOException("下载响应长度超过声明总长度：$downloaded/$total 字节")
                }
            if (total > 0L && downloaded < total) {
                    if (partialRange == null) {
                throw IOException("下载连接提前结束：$downloaded/$total 字节")
            }
                    // Some image CDNs return a valid 206 slice even when the
                    // initial request did not ask for Range. Continue from the
                    // declared boundary instead of publishing the first slice
                    // as a complete WebP/JPEG file.
                    existing = downloaded
                    resume = true
                }
            }
            if (!resume) return
        }
        throw IOException("单连接 Range 响应次数过多，已停止保存不完整媒体")
    }

    private fun parseContentRange(value: String?): ResponseRange? {
        val match = CONTENT_RANGE_PATTERN.matchEntire(value?.trim().orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        return ResponseRange(start, end, total).takeIf {
            it.start >= 0L && it.end >= it.start && it.totalBytes > it.end
        }
    }

    private fun execute(
        request: DirectDownloadRequest,
        range: String?,
    ): Response {
        var url = Request.Builder().url(request.url).build().url
        repeat(MAX_REDIRECTS) {
            val builder = Request.Builder().url(url)
            request.headersFor(url).forEach(builder::header)
            builder.header("Accept-Encoding", "identity")
            range?.let { builder.header("Range", it) }
            val response = httpClient.newCall(builder.build()).execute()
            if (!response.isRedirect) {
                try {
                    request.finalUrlValidator(response.request.url.toString())
                    return response
                } catch (error: Throwable) {
                    response.close()
                    throw error
                }
            }
            val redirectedUrl = response.header("Location")
                ?.let(url::resolve)
                ?: return response
            if (!redirectedUrl.isHttps) {
                response.close()
                throw IOException("媒体跳转到了非 HTTPS 地址，已拒绝传输")
            }
            response.close()
            url = redirectedUrl
        }
        throw IOException("媒体链接重定向次数过多，已停止传输")
    }

    private fun deleteSegments(partial: File) {
        partial.parentFile?.listFiles()
            ?.filter { it.name.startsWith("${partial.name}.segment-") }
            ?.forEach(File::delete)
        segmentPlanFile(partial).delete()
    }

    private fun ensureSegmentPlan(partial: File, plan: RangePlan) {
        val planFile = segmentPlanFile(partial)
        val fingerprint = "${plan.totalBytes}:${plan.segmentCount}"
        val hasSegments = partial.parentFile?.listFiles()
            ?.any { it.name.startsWith("${partial.name}.segment-") }
            ?: false
        if (hasSegments && planFile.takeIf(File::exists)?.readText() != fingerprint) {
            deleteSegments(partial)
        }
        if (!planFile.exists() || planFile.readText() != fingerprint) {
            planFile.writeText(fingerprint)
        }
    }

    private fun segmentPlanFile(partial: File): File =
        File(partial.parentFile, "${partial.name}.segments.meta")

    private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }
        .last()

    private fun publish(partial: File, target: File) {
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }

    private class RangeNotSupportedException : IOException("服务器不支持稳定的分段下载")

    private data class RangeProbe(
        val plan: RangePlan? = null,
        val rangeSupported: Boolean = false,
        val totalBytes: Long = 0L,
        val reason: String? = null,
    )

    private data class ByteRange(val start: Long, val endInclusive: Long)

    private data class ResponseRange(
        val start: Long,
        val end: Long,
        val totalBytes: Long,
    )

    private data class RangePlan(val totalBytes: Long, val segmentCount: Int) {
        fun ranges(): List<ByteRange> {
            val baseSize = totalBytes / segmentCount
            val remainder = totalBytes % segmentCount
            var start = 0L
            return List(segmentCount) { index ->
                val size = baseSize + if (index < remainder) 1L else 0L
                ByteRange(start, start + size - 1L).also { start += size }
            }
        }
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        ((monotonicNanos() - startedNanos).coerceAtLeast(0L) / 1_000_000L)

    private class TransferMeter(
        private val nowNanos: () -> Long,
    ) {
        private val totalBytes = AtomicLong(0L)
        private var windowStartedNanos = nowNanos()
        private var windowBytes = 0L
        private var peakBytesPerSecond = 0L

        @Synchronized
        fun record(count: Int) {
            if (count <= 0) return
            totalBytes.addAndGet(count.toLong())
            windowBytes += count
            val now = nowNanos()
            val elapsed = now - windowStartedNanos
            if (elapsed >= SPEED_WINDOW_NANOS) {
                val speed = (windowBytes * 1_000_000_000.0 / elapsed.coerceAtLeast(1L)).toLong()
                peakBytesPerSecond = maxOf(peakBytesPerSecond, speed)
                windowStartedNanos = now
                windowBytes = 0L
            }
        }

        @Synchronized
        fun snapshot(elapsedMillis: Long): MeterSnapshot {
            val now = nowNanos()
            val windowElapsed = now - windowStartedNanos
            if (windowBytes > 0L && windowElapsed > 0L) {
                val speed = (windowBytes * 1_000_000_000.0 / windowElapsed).toLong()
                peakBytesPerSecond = maxOf(peakBytesPerSecond, speed)
            }
            val bytes = totalBytes.get()
            val average = if (elapsedMillis > 0L) bytes * 1000L / elapsedMillis else 0L
            return MeterSnapshot(bytes, average, peakBytesPerSecond)
        }
    }

    private data class MeterSnapshot(
        val networkBytes: Long,
        val averageBytesPerSecond: Long,
        val peakBytesPerSecond: Long,
    )

    private data class TransferReportState(
        var mode: DownloadConnectionMode = DownloadConnectionMode.UNKNOWN,
        var connectionCount: Int = 0,
        var rangeSupported: Boolean = false,
        var expectedBytes: Long = 0L,
        var retryCount: Int = 0,
        var fallbackReason: String? = null,
    ) {
        fun toReport(
            request: DirectDownloadRequest,
            outcome: TransferReportOutcome,
            committedBytes: Long,
            startedAt: Long,
            elapsedMillis: Long,
            meter: TransferMeter,
            error: Throwable?,
        ): DownloadThroughputReport {
            val measured = meter.snapshot(elapsedMillis)
            return DownloadThroughputReport(
                reportId = UUID.randomUUID().toString(),
                taskId = request.taskId,
                platform = request.platform ?: DownloadPlatform.YOUTUBE,
                streamLabel = request.streamLabel,
                outcome = outcome,
                connectionMode = mode,
                connectionCount = connectionCount,
                rangeSupported = rangeSupported,
                expectedBytes = expectedBytes.takeIf { it > 0L } ?: committedBytes,
                committedBytes = committedBytes,
                networkBytes = measured.networkBytes,
                startedAt = startedAt,
                finishedAt = startedAt + elapsedMillis,
                elapsedMillis = elapsedMillis,
                averageBytesPerSecond = measured.averageBytesPerSecond,
                peakBytesPerSecond = measured.peakBytesPerSecond,
                retryCount = retryCount,
                reprobeCount = request.reprobeCount,
                fallbackReason = fallbackReason,
                errorSummary = error?.message?.take(400),
            )
        }
    }

    private companion object {
        const val HTTP_PARTIAL = 206
        const val BUFFER_SIZE_BYTES = 512 * 1024
        const val SPEED_WINDOW_NANOS = 250_000_000L
        val DEFAULT_HTTP_CLIENT = OkHttpClient.Builder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 32
                    maxRequestsPerHost = 12
                },
            )
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Googlevideo can starve sibling byte-range streams when OkHttp
            // multiplexes them over one HTTP/2 socket. Independent HTTP/1.1
            // connections preserve the intended platform connection count.
            .protocols(listOf(Protocol.HTTP_1_1))
            // Redirects are followed in execute so credentials can be scoped
            // again for every target URL.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val SEGMENT_EXECUTOR = Executors.newFixedThreadPool(8) { runnable ->
            Thread(runnable, "nanzhufeng-range-download").apply { isDaemon = true }
        }
        const val MAX_REDIRECTS = 12
        const val MAX_SINGLE_CONNECTION_RESPONSES = 128
        val CONTENT_RANGE_PATTERN = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
    }
}

object MediaFileValidator {
    private const val MIN_MP3_BYTES = 1024L
    private const val MIN_IMAGE_BYTES = 1024L
    private const val MIN_CONTAINER_BYTES = 64L * 1024L

    fun isLikelyMedia(file: File): Boolean {
        if (!file.isFile) return false
        return file.inputStream().use { input ->
            isLikelyMedia(input, file.length())
        }
    }

    fun isLikelyMedia(input: InputStream, length: Long): Boolean {
        val header = ByteArray(64)
        val count = input.read(header)
        if (count <= 0) return false
        val bytes = header.copyOf(count)
        val text = bytes.toString(Charsets.ISO_8859_1)
        val trimmedText = text.trimStart('\u0000', ' ', '\t', '\r', '\n')
        if (
            trimmedText.startsWith("<", ignoreCase = true) ||
            trimmedText.startsWith("{") ||
            trimmedText.startsWith("[")
        ) {
            return false
        }

        val isMp3 = text.startsWith("ID3") || hasMpegAudioFrameSync(bytes)
        if (isMp3) return length >= MIN_MP3_BYTES

        val isJpeg = bytes.startsWith(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
        val isPng = bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        val isGif = bytes.startsWith("GIF8".toByteArray(Charsets.US_ASCII))
        val isWebp = bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        if (isWebp) return length >= MIN_IMAGE_BYTES && declaredRiffSize(bytes) == length
        if (isJpeg || isPng || isGif) return length >= MIN_IMAGE_BYTES

        val isIsoBmff = "ftyp" in text
        val isWebM = bytes.startsWith(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())) ||
            "webm" in text.lowercase()
        return (isIsoBmff || isWebM) && length >= MIN_CONTAINER_BYTES
    }

    private fun hasMpegAudioFrameSync(header: ByteArray): Boolean {
        if (header.size < 2) return false
        val first = header[0].toInt() and 0xff
        val second = header[1].toInt() and 0xff
        val versionBits = (second ushr 3) and 0x03
        val layerBits = (second ushr 1) and 0x03
        return first == 0xff &&
            second and 0xe0 == 0xe0 &&
            versionBits != 0x01 &&
            layerBits != 0x00
    }

    private fun declaredRiffSize(header: ByteArray): Long {
        if (header.size < 8) return -1L
        return (
            (header[4].toLong() and 0xff) or
                ((header[5].toLong() and 0xff) shl 8) or
                ((header[6].toLong() and 0xff) shl 16) or
                ((header[7].toLong() and 0xff) shl 24)
            ) + 8L
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
