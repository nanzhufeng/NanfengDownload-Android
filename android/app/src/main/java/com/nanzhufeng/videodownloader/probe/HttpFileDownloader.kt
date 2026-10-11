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
import java.util.concurrent.Semaphore
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
    val retryAfterMillis: Long? = null,
) : IOException("下载请求失败：HTTP $statusCode")

data class TransferPolicy(
    val platform: String,
    val maxConnections: Int,
    val segmentedThresholdBytes: Long,
    val chunkSizeBytes: Long? = null,
    val preferSingleConnection: Boolean = false,
    val rangeProbeBytes: Int = 1,
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
    val onPhaseMeasured: (String, Long) -> Unit = { _, _ -> },
    /** Ephemeral extractor-owned fragment plan; never persisted as account data. */
    val downloadSpec: String = "",
    val remuxToMp4: Boolean = false,
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
        var effectiveRequest = request
        var emptyMultiFailures = 0
        var slowConnectionRecoveryAttempted = false
        var slowConnectionExpectedBytes = 0L
        var recoveringSinglePrefix = false
        val slowRecoveryClaimed = AtomicBoolean(false)
        val policy = request.transferPolicy ?: TransferPolicy(
            platform = request.platform?.name ?: "GENERIC",
            maxConnections = maxSegments,
            segmentedThresholdBytes = segmentedThresholdBytes,
        )

        repeat(maxAttempts) { attempt ->
            if (cancelled.get()) throw CancellationException("下载已取消")
            try {
                if (!rangePlanResolved) {
                    val probe = probeRangePlan(request, cancelled,
                        policy.copy(preferSingleConnection = policy.preferSingleConnection && !slowConnectionRecoveryAttempted))
                    if (slowConnectionRecoveryAttempted && probe.totalBytes > 0L &&
                        probe.totalBytes != slowConnectionExpectedBytes) throw MediaSourceChangedException()
                    rangePlan = probe.plan?.let { resumeExistingSegmentPlan(partial, it) }
                    reportState.rangeSupported = probe.rangeSupported
                    reportState.expectedBytes = probe.totalBytes
                    reportState.fallbackReason = if (slowConnectionRecoveryAttempted)
                        "持续低速，保留断点后探测可用连接：${probe.reason ?: "使用多连接"}" else probe.reason
                    if (probe.resolvedUrl != null) effectiveRequest = request.copy(url = probe.resolvedUrl)
                    rangePlanResolved = true
                }
                val plan = rangePlan
                if (plan != null) {
                    reportState.mode = DownloadConnectionMode.MULTI
                    reportState.connectionCount = plan.activeConnections
                    request.onModeResolved(reportState.mode, reportState.connectionCount)
                    try {
                        val before = meter.receivedBytes()
                        try {
                            downloadSegmentedAttempt(effectiveRequest, partial, plan, cancelled, onProgress, meter,
                                seedFromSinglePrefix = recoveringSinglePrefix || partial.length() > 0L, slowRecovery = slowRecoveryClaimed)
                            emptyMultiFailures = 0
                        } catch (error: IOException) {
                            if (error is HttpDownloadException || error is RangeNotSupportedException) throw error
                            emptyMultiFailures = if (meter.receivedBytes() == before) emptyMultiFailures + 1 else 0
                            if (emptyMultiFailures < 2) throw error
                            rangePlan = null
                            reportState.mode = DownloadConnectionMode.SINGLE
                            reportState.connectionCount = 1
                            reportState.fallbackReason = "多连接连续无有效数据，保留断点后降级单连接"
                            request.onModeResolved(reportState.mode, reportState.connectionCount)
                            promoteSegmentPrefix(partial, plan)
                            downloadAttempt(effectiveRequest, partial, cancelled, onProgress, meter, slowRecoveryClaimed, slowConnectionExpectedBytes)
                        }
                    } catch (_: RangeNotSupportedException) {
                        // Keep rejected segments until a replacement transfer succeeds.
                        rangePlan = null
                        reportState.mode = DownloadConnectionMode.SINGLE
                        reportState.connectionCount = 1
                        reportState.rangeSupported = false
                        reportState.fallbackReason = "分片响应不稳定，已回退单连接"
                        request.onModeResolved(reportState.mode, reportState.connectionCount)
                        downloadAttempt(effectiveRequest, partial, cancelled, onProgress, meter, slowRecoveryClaimed, slowConnectionExpectedBytes)
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
                            request = effectiveRequest,
                            partial = partial,
                            totalBytes = reportState.expectedBytes,
                            chunkSizeBytes = policy.chunkSizeBytes,
                            cancelled = cancelled,
                            onProgress = onProgress,
                            meter = meter,
                            slowRecovery = slowRecoveryClaimed,
                        )
                    } catch (_: RangeNotSupportedException) {
                        partial.delete()
                        reportState.rangeSupported = false
                        reportState.fallbackReason = "小分块 Range 不稳定，已回退完整单连接"
                        downloadAttempt(effectiveRequest, partial, cancelled, onProgress, meter, slowRecoveryClaimed, slowConnectionExpectedBytes)
                    }
                } else {
                    reportState.mode = DownloadConnectionMode.SINGLE
                    reportState.connectionCount = 1
                    request.onModeResolved(reportState.mode, reportState.connectionCount)
                    downloadAttempt(effectiveRequest, partial, cancelled, onProgress, meter, slowRecoveryClaimed, slowConnectionExpectedBytes)
                }
                if (cancelled.get()) throw CancellationException("下载已取消")
                publish(partial, request.target)
                deleteSegments(partial)
                return request.target
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                if (cancelled.get()) throw CancellationException("下载已取消")
                if (error is HttpDownloadException &&
                    (error.statusCode !in RETRYABLE_HTTP_STATUSES ||
                        (error.retryAfterMillis ?: 0L) > MAX_RETRY_WAIT_MILLIS)) throw error
                if (error is MediaSourceChangedException) throw error
                if (error is SlowConnectionException) {
                    slowConnectionExpectedBytes = error.totalBytes
                    recoveringSinglePrefix = reportState.mode == DownloadConnectionMode.SINGLE
                    slowConnectionRecoveryAttempted = true
                    effectiveRequest = request
                    rangePlanResolved = false
                }
                lastError = error
                if (attempt == maxAttempts - 1) throw error
                reportState.retryCount += 1
                val delay = maxOf(
                    (retryDelayMillis.coerceAtLeast(0L) * (attempt + 1)).coerceAtMost(MAX_RETRY_WAIT_MILLIS),
                    (error as? HttpDownloadException)?.retryAfterMillis ?: 0L,
                )
                awaitRetry(delay, cancelled)
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
        if (policy.preferSingleConnection || (policy.maxConnections < 2 && policy.chunkSizeBytes == null)) {
            return RangeProbe(reason = "平台策略使用完整单连接")
        }
        return try {
            val probeEnd = policy.rangeProbeBytes.coerceIn(1, 64 * 1024) - 1L
            execute(request, cancelled, range = "bytes=0-$probeEnd").use { response ->
                val responseCode = response.code
                if (responseCode >= 400 && responseCode != 416) {
                    throw response.downloadError()
                }
                if (responseCode != HTTP_PARTIAL) {
                    return RangeProbe(reason = "服务器未返回 206 Range 响应")
                }
                val contentRange = response.header("Content-Range")
                val total = contentRange
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                    ?: return RangeProbe(reason = "Content-Range 无有效总长度")
                val parsedRange = parseContentRange(contentRange)
                if (parsedRange == null || parsedRange.start != 0L ||
                    parsedRange.end != minOf(probeEnd, total - 1L)) {
                    return RangeProbe(reason = "Range 探测响应区间不匹配", totalBytes = total)
                }
                if (policy.chunkSizeBytes != null &&
                    (policy.maxConnections < 2 || total < policy.segmentedThresholdBytes)) {
                    return RangeProbe(
                        rangeSupported = true,
                        totalBytes = total,
                        resolvedUrl = response.request.url.toString(),
                        reason = "使用顺序小分块 Range 单连接",
                    )
                }
                if (total < policy.segmentedThresholdBytes) {
                    return RangeProbe(
                        rangeSupported = true,
                        totalBytes = total,
                        reason = "文件小于平台分片阈值",
                        resolvedUrl = response.request.url.toString(),
                    )
                }
                RangeProbe(
                    plan = RangePlan(total, policy.maxConnections.coerceAtLeast(2), policy.chunkSizeBytes),
                    rangeSupported = true,
                    totalBytes = total,
                    resolvedUrl = response.request.url.toString(),
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
        seedFromSinglePrefix: Boolean = false,
        slowRecovery: AtomicBoolean = AtomicBoolean(true),
    ) {
        ensureSegmentPlan(partial, plan)
        if (seedFromSinglePrefix) seedSegmentPrefix(partial, plan, cancelled)
        val ranges = plan.ranges()
        val segmentFiles = ranges.indices.map { index ->
            File(partial.parentFile, "${partial.name}.segment-$index")
        }
        val progressLock = Any()
        val segmentProgress = LongArray(ranges.size) { index ->
            segmentFiles[index].takeIf(File::exists)?.length() ?: 0L
        }
        val abortAllSegments = AtomicBoolean(false)
        onProgress(segmentProgress.sum(), plan.totalBytes)
        val activeConnections = Semaphore(plan.activeConnections)
        val futures = ranges.mapIndexed { index, range ->
            java.util.concurrent.CompletableFuture.runAsync({
                var acquired = false
                try {
                    while (!activeConnections.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                        if (cancelled.get() || abortAllSegments.get()) throw CancellationException("下载已取消")
                    }
                    acquired = true
                    if (cancelled.get() || abortAllSegments.get()) throw CancellationException("下载已取消")
                    downloadRange(
                        request = request,
                        target = segmentFiles[index],
                        range = range,
                        totalBytes = plan.totalBytes,
                        cancelled = cancelled,
                        abortAllSegments = abortAllSegments,
                        meter = meter,
                        chunkSizeBytes = plan.chunkSizeBytes,
                        slowRecovery = slowRecovery,
                    ) { bytesInSegment ->
                        synchronized(progressLock) {
                            segmentProgress[index] = bytesInSegment
                            onProgress(segmentProgress.sum(), plan.totalBytes)
                        }
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException || error is HttpDownloadException ||
                        error is RangeNotSupportedException || error is SlowConnectionException || error is java.net.SocketTimeoutException) {
                        abortAllSegments.set(true)
                    }
                    throw CompletionException(error)
                } finally {
                    if (acquired) activeConnections.release()
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
        totalBytes: Long,
        cancelled: AtomicBoolean,
        abortAllSegments: AtomicBoolean,
        meter: TransferMeter,
        chunkSizeBytes: Long?,
        slowRecovery: AtomicBoolean,
        onProgress: (bytesInSegment: Long) -> Unit,
    ) {
        val expectedLength = range.endInclusive - range.start + 1L
        if (target.length() > expectedLength) target.delete()
        val existing = target.takeIf(File::exists)?.length() ?: 0L
        if (existing == expectedLength) {
            onProgress(existing)
            return
        }
        var downloaded = existing
        while (downloaded < expectedLength) {
            val requestStart = range.start + downloaded
            val requestEnd = if (chunkSizeBytes != null) {
                require(chunkSizeBytes > 0L)
                minOf(range.endInclusive, requestStart + chunkSizeBytes - 1L)
            } else range.endInclusive
            execute(request, cancelled, range = "bytes=$requestStart-$requestEnd", aborted = abortAllSegments).use { response ->
                if (response.code >= 400) throw response.downloadError()
                if (response.code != HTTP_PARTIAL) {
                    throw RangeNotSupportedException()
                }
                val contentRange = response.header("Content-Range").orEmpty()
                if (parseContentRange(contentRange) != ResponseRange(requestStart, requestEnd, totalBytes)) {
                    throw RangeNotSupportedException()
                }
                val body = response.body ?: throw IOException("下载响应缺少内容")
                val responseLength = requestEnd - requestStart + 1L
                if (body.contentLength() > responseLength) {
                    throw IOException("分段响应声明长度超过区间，保留已有断点")
                }
                val responseExpected = downloaded + responseLength
                val window = SlowTransferWindow(totalBytes, slowRecovery)
                body.byteStream().use { input ->
                    FileOutputStream(target, true).buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE_BYTES)
                        while (downloaded < responseExpected) {
                            if (cancelled.get() || abortAllSegments.get()) {
                                throw CancellationException("下载已取消")
                            }
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), responseExpected - downloaded).toInt())
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            meter.record(count)
                            downloaded += count
                            onProgress(downloaded)
                            window.record(count, expectedLength - downloaded)
                        }
                    }
                }
                if (downloaded != responseExpected) {
                    throw IOException("分段连接提前结束：$downloaded/$responseExpected 字节")
                }
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
        slowRecovery: AtomicBoolean,
    ) {
        require(chunkSizeBytes > 0L) { "小分块大小必须大于 0" }
        if (partial.length() > totalBytes) partial.delete()
        var downloaded = partial.takeIf(File::exists)?.length() ?: 0L
        onProgress(downloaded, totalBytes)
        while (downloaded < totalBytes) {
            if (cancelled.get()) throw CancellationException("下载已取消")
            val endInclusive = minOf(downloaded + chunkSizeBytes - 1L, totalBytes - 1L)
            execute(request, cancelled, range = "bytes=$downloaded-$endInclusive").use { response ->
                if (response.code >= 400) throw response.downloadError()
                if (response.code != HTTP_PARTIAL) throw RangeNotSupportedException()
                val contentRange = response.header("Content-Range").orEmpty()
                if (parseContentRange(contentRange) != ResponseRange(downloaded, endInclusive, totalBytes)) {
                    throw RangeNotSupportedException()
                }
                val body = response.body ?: throw IOException("下载响应缺少内容")
                val window = SlowTransferWindow(totalBytes, slowRecovery)
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
                            window.record(count, totalBytes - downloaded)
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
        slowRecovery: AtomicBoolean = AtomicBoolean(true),
        expectedTotalBytes: Long = 0L,
    ) {
        var existing = partial.takeIf(File::exists)?.length() ?: 0L
        repeat(MAX_SINGLE_CONNECTION_RESPONSES) {
            val requestedStart = existing
            var resume = false
            execute(
                request, cancelled,
                range = if (requestedStart > 0L) "bytes=$requestedStart-" else null,
            ).use { response ->
            val responseCode = response.code
            if (responseCode !in 200..299) {
                throw response.downloadError()
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
            val body = response.body ?: throw IOException("下载响应缺少内容")
            val bodyLength = body.contentLength().coerceAtLeast(0L)
                val total = partialRange?.totalBytes ?: if (bodyLength > 0L) start + bodyLength else 0L
            if (expectedTotalBytes > 0L && total != expectedTotalBytes) throw MediaSourceChangedException()
            if (!append && partial.exists()) partial.delete()
            var downloaded = start
            val window = SlowTransferWindow(total, slowRecovery)
            onProgress(downloaded, total)

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
                        window.record(count, total - downloaded)
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

    private fun Response.downloadError(): HttpDownloadException {
        val value = header("Retry-After")?.trim()
        val seconds = value?.toLongOrNull()?.takeIf { it >= 0L }
        val wait = if (seconds != null) {
            if (seconds > MAX_RETRY_WAIT_MILLIS / 1000L) MAX_RETRY_WAIT_MILLIS + 1L else seconds * 1000L
        } else {
            value?.let {
                runCatching {
                    val date = java.time.ZonedDateTime.parse(it, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    (date.toInstant().toEpochMilli() - epochMillis()).coerceAtLeast(0L)
                }.getOrNull()
            }
        }
        return HttpDownloadException(code, wait)
    }

    private fun awaitRetry(delayMillis: Long, cancelled: AtomicBoolean) {
        val deadline = System.nanoTime() + delayMillis * 1_000_000L
        do {
            if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException("下载已取消")
            val remaining = (deadline - System.nanoTime()) / 1_000_000L
            if (remaining <= 0L) return
            try {
                Thread.sleep(minOf(remaining, 25L))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw CancellationException("下载已取消")
            }
        } while (true)
    }

    private fun execute(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        range: String?,
        aborted: AtomicBoolean? = null,
    ): Response {
        var url = Request.Builder().url(request.url).build().url
        repeat(MAX_REDIRECTS) {
            val builder = Request.Builder().url(url)
            request.headersFor(url).forEach(builder::header)
            builder.header("Accept-Encoding", "identity")
            range?.let { builder.header("Range", it) }
            val response = TransferCallGuard.execute(httpClient.newCall(builder.build()), cancelled, aborted)
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

    /** Resume the old byte layout; the current budget bounds active sockets,
     * not the number of checkpoint files. Never throw away valid saved ranges
     * merely because a newer policy has a different connection count. */
    private fun resumeExistingSegmentPlan(partial: File, probed: RangePlan): RangePlan {
        val metadata = segmentPlanFile(partial).takeIf(File::isFile) ?: return probed
        val saved = metadata.bufferedReader().use { it.readLine()?.take(64) }.orEmpty().split(':')
        val total = saved.getOrNull(0)?.toLongOrNull() ?: return probed
        val count = saved.getOrNull(1)?.toIntOrNull()?.takeIf { it in 2..16 } ?: return probed
        if (total != probed.totalBytes) throw MediaSourceChangedException()
        if (!(0 until count).any { File(partial.parentFile, "${partial.name}.segment-$it").length() > 0L }) return probed
        return probed.copy(segmentCount = count, activeConnections = minOf(count, probed.activeConnections))
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

    /** Seed only bytes actually committed by the former contiguous single response. */
    private fun seedSegmentPrefix(partial: File, plan: RangePlan, cancelled: AtomicBoolean) {
        val prefixBytes = partial.takeIf(File::isFile)?.length() ?: return
        if (prefixBytes <= 0 || prefixBytes > plan.totalBytes) return
        java.io.RandomAccessFile(partial, "r").use { input ->
            val buffer = ByteArray(BUFFER_SIZE_BYTES)
            plan.ranges().forEachIndexed { index, range ->
                val available = (prefixBytes - range.start).coerceIn(0, range.endInclusive - range.start + 1)
                val segment = File(partial.parentFile, "${partial.name}.segment-$index")
                var copied = segment.length()
                if (copied >= available) return@forEachIndexed
                input.seek(range.start + copied)
                FileOutputStream(segment, true).buffered().use { output ->
                    while (copied < available) {
                        if (cancelled.get()) throw CancellationException("下载已取消")
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), available - copied).toInt())
                        if (count < 0) throw IOException("无法读取已保存的连续断点")
                        output.write(buffer, 0, count)
                        copied += count
                    }
                }
            }
        }
    }

    private fun segmentPlanFile(partial: File): File =
        File(partial.parentFile, "${partial.name}.segments.meta")

    private fun promoteSegmentPrefix(partial: File, plan: RangePlan) {
        val prefix = File(partial.parentFile, partial.name + ".fallback-prefix")
        try {
            FileOutputStream(prefix).buffered().use { output ->
                for ((index, range) in plan.ranges().withIndex()) {
                    val segment = File(partial.parentFile, "${partial.name}.segment-$index")
                    val size = segment.takeIf(File::isFile)?.length() ?: 0L
                    val expected = range.endInclusive - range.start + 1L
                    if (size <= 0L || size > expected) break
                    segment.inputStream().use { it.copyTo(output) }
                    if (size < expected) break
                }
            }
            if (prefix.length() > partial.length()) {
                if (!prefix.renameTo(partial)) throw IOException("无法保留多连接连续断点")
            }
        } finally { prefix.delete() }
    }

    private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }
        .last()

    private fun publish(partial: File, target: File) {
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }

    /** One severe-low-speed intervention per media stream, never a repeating rate limit. */
    private inner class SlowTransferWindow(private val totalBytes: Long, private val claimed: AtomicBoolean) {
        private var started = monotonicNanos()
        private var bytes = 0L
        fun record(count: Int, remainingBytes: Long) {
            if (claimed.get() || remainingBytes < MIN_ADAPTIVE_REMAINING_BYTES) return
            bytes += count
            val now = monotonicNanos()
            val elapsed = now - started
            if (elapsed < SLOW_CONNECTION_WINDOW_NANOS) return
            if (bytes * 1_000_000_000.0 / elapsed < MIN_ADAPTIVE_BYTES_PER_SECOND &&
                claimed.compareAndSet(false, true)) throw SlowConnectionException(totalBytes)
            started = now
            bytes = 0L
        }
    }

    private class MediaSourceChangedException : IOException("媒体来源长度已变化，已保留原断点，请重新读取作品")

    private class SlowConnectionException(val totalBytes: Long) : IOException("媒体连接持续低速，正在保留断点切换连接")

    private class RangeNotSupportedException : IOException("服务器不支持稳定的分段下载")

    private data class RangeProbe(
        val plan: RangePlan? = null,
        val rangeSupported: Boolean = false,
        val totalBytes: Long = 0L,
        val reason: String? = null,
        val resolvedUrl: String? = null,
    )

    private data class ByteRange(val start: Long, val endInclusive: Long)

    private data class ResponseRange(
        val start: Long,
        val end: Long,
        val totalBytes: Long,
    )

    private data class RangePlan(val totalBytes: Long, val segmentCount: Int, val chunkSizeBytes: Long? = null,
                                 val activeConnections: Int = segmentCount) {
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
        fun receivedBytes(): Long = totalBytes.get()
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
        const val SLOW_CONNECTION_WINDOW_NANOS = 5_000_000_000L
        const val MIN_ADAPTIVE_BYTES_PER_SECOND = 128L * 1024L
        const val MIN_ADAPTIVE_REMAINING_BYTES = 2L * 1024L * 1024L
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
            .readTimeout(12, TimeUnit.SECONDS)
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
        const val MAX_RETRY_WAIT_MILLIS = 60_000L
        val RETRYABLE_HTTP_STATUSES = setOf(408, 429, 500, 502, 503, 504)
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
        val count = readPrefix(input, header)
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
        if ((isIsoBmff || isWebM) && length in MIN_MP3_BYTES until MIN_CONTAINER_BYTES) {
            val complete = ByteArray(length.toInt())
            bytes.copyInto(complete)
            val remaining = ByteArray(complete.size - count)
            if (readPrefix(input, remaining) != remaining.size) return false
            remaining.copyInto(complete, count)
            return if (isIsoBmff) SmallIsoBmffValidator.accepts(complete) else SmallWebmValidator.accepts(complete)
        }
        return (isIsoBmff || isWebM) && length >= MIN_CONTAINER_BYTES
    }

    private fun readPrefix(input: InputStream, buffer: ByteArray): Int {
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) break
            if (read == 0) {
                val byte = input.read()
                if (byte < 0) break
                buffer[count++] = byte.toByte()
            } else count += read
        }
        return count
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
