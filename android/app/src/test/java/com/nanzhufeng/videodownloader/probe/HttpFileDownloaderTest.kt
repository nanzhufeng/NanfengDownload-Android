package com.nanzhufeng.videodownloader.probe

import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.TransferReportOutcome
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpFileDownloaderTest {
    @Test
    fun sequentialSmallRangesAvoidOneLongThrottledAudioResponse() {
        val payload = fakeMp4Payload(size = 100 * 1024)
        val requestedRanges = CopyOnWriteArrayList<String>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range") ?: error("Range header required")
                    requestedRanges += range
                    val bounds = range.removePrefix("bytes=").split('-', limit = 2)
                    val start = bounds[0].toInt()
                    val end = bounds[1].toInt().coerceAtMost(payload.lastIndex)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                        .setBody(Buffer().write(payload.copyOfRange(start, end + 1)))
                }
            }
            start()
        }
        val directory = createTempDirectory("download-chunked-audio-").toFile()
        try {
            var report: DownloadThroughputReport? = null
            val result = HttpFileDownloader(retryDelayMillis = 0).download(
                DirectDownloadRequest(
                    url = server.url("/audio.m4a").toString(),
                    headers = emptyMap(),
                    target = File(directory, "audio.m4a"),
                    transferPolicy = TransferPolicy(
                        platform = "YOUTUBE",
                        maxConnections = 1,
                        segmentedThresholdBytes = Long.MAX_VALUE,
                        chunkSizeBytes = 32L * 1024L,
                    ),
                    onReport = { report = it },
                ),
                AtomicBoolean(false),
            ) { _, _ -> }

            assertArrayEquals(payload, result.readBytes())
            assertEquals(
                listOf(
                    "bytes=0-0",
                    "bytes=0-32767",
                    "bytes=32768-65535",
                    "bytes=65536-98303",
                    "bytes=98304-102399",
                ),
                requestedRanges.toList(),
            )
            assertEquals(DownloadConnectionMode.SINGLE, report!!.connectionMode)
            assertTrue(report!!.rangeSupported)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun interruptedSegmentResumesWithoutRestartingCompletedSiblingSegments() {
        val payload = fakeMp4Payload(size = 2 * 1024 * 1024)
        val split = payload.size / 2
        val interruptions = AtomicInteger(0)
        val firstSegmentRequests = AtomicInteger(0)
        val secondSegmentStarts = CopyOnWriteArrayList<Int>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val rangeHeader = request.getHeader("Range") ?: error("Range header required")
                    val bounds = rangeHeader.removePrefix("bytes=").split('-', limit = 2)
                    val start = bounds[0].toInt()
                    val end = bounds[1].toInt().coerceAtMost(payload.lastIndex)
                    val response = MockResponse()
                        .setResponseCode(206)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                        .setBody(Buffer().write(payload.copyOfRange(start, end + 1)))
                    if (rangeHeader == "bytes=0-0") return response
                    if (start < split) {
                        firstSegmentRequests.incrementAndGet()
                    } else {
                        secondSegmentStarts += start
                        if (interruptions.getAndIncrement() < 3) {
                            response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                        }
                    }
                    return response
                }
            }
            start()
        }
        val directory = createTempDirectory("download-segment-resume-").toFile()
        try {
            var report: DownloadThroughputReport? = null
            val result = HttpFileDownloader(
                retryDelayMillis = 0,
                maxSegments = 2,
                segmentedThresholdBytes = 1,
            ).download(
                DirectDownloadRequest(
                    url = server.url("/long-replay.mp4").toString(),
                    headers = emptyMap(),
                    target = File(directory, "long-replay.mp4"),
                    taskId = "long-replay",
                    platform = DownloadPlatform.YOUTUBE,
                    onReport = { report = it },
                ),
                AtomicBoolean(false),
            ) { _, _ -> }

            assertArrayEquals(payload, result.readBytes())
            assertEquals("已完成的相邻分片不应因另一分片中断而重下", 1, firstSegmentRequests.get())
            assertTrue("中断分片必须从已写入位置继续", secondSegmentStarts.any { it > split })
            assertTrue(report!!.retryCount >= 1)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun downloadsLargeRangeCapableFileWithMultipleConnections() {
        val payload = fakeMp4Payload(size = 2 * 1024 * 1024)
        val rangeRequests = AtomicInteger(0)
        val segmentStarts = CountDownLatch(6)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val rangeHeader = request.getHeader("Range")
                    if (rangeHeader == null) {
                        return MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
                    }
                    rangeRequests.incrementAndGet()
                    val bounds = rangeHeader.removePrefix("bytes=").split('-', limit = 2)
                    val start = bounds[0].toInt()
                    val end = bounds[1].toIntOrNull()?.coerceAtMost(payload.lastIndex) ?: payload.lastIndex
                    if (rangeHeader != "bytes=0-0") {
                        segmentStarts.countDown()
                        assertTrue(
                            "all six file segments should be requested concurrently",
                            segmentStarts.await(2, TimeUnit.SECONDS),
                        )
                    }
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                        .setBody(Buffer().write(payload.copyOfRange(start, end + 1)))
                }
            }
            start()
        }
        val directory = createTempDirectory("download-segmented-").toFile()
        try {
            val target = File(directory, "video.mp4")
            var report: DownloadThroughputReport? = null

            val result = HttpFileDownloader(
                retryDelayMillis = 0,
                maxSegments = 6,
                segmentedThresholdBytes = 1,
            ).download(
                request = DirectDownloadRequest(
                    url = server.url("/video.mp4").toString(),
                    headers = emptyMap(),
                    target = target,
                    taskId = "task-range",
                    platform = DownloadPlatform.YOUTUBE,
                    onReport = { report = it },
                ),
                cancelled = AtomicBoolean(false),
                onProgress = { _, _ -> },
            )

            assertArrayEquals(payload, result.readBytes())
            assertTrue("probe plus six range segments expected", rangeRequests.get() >= 7)
            assertEquals(0L, segmentStarts.count)
            assertFalse(directory.listFiles().orEmpty().any { ".segment-" in it.name })
            assertEquals(TransferReportOutcome.COMPLETED, report!!.outcome)
            assertEquals(DownloadConnectionMode.MULTI, report!!.connectionMode)
            assertEquals(6, report!!.connectionCount)
            assertTrue(report!!.rangeSupported)
            assertEquals(payload.size.toLong(), report!!.committedBytes)
            assertEquals(payload.size.toLong(), report!!.networkBytes)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun ignoredRangeProbeFallsBackToExplicitSingleConnectionReport() {
        val payload = fakeMp4Payload()
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))
            enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))
            start()
        }
        val directory = createTempDirectory("download-single-report-").toFile()
        try {
            var report: DownloadThroughputReport? = null
            val result = HttpFileDownloader(
                retryDelayMillis = 0,
                segmentedThresholdBytes = 1,
            ).download(
                DirectDownloadRequest(
                    url = server.url("/video.mp4").toString(),
                    headers = emptyMap(),
                    target = File(directory, "video.mp4"),
                    taskId = "task-single",
                    platform = DownloadPlatform.DOUYIN,
                    onReport = { report = it },
                ),
                AtomicBoolean(false),
            ) { _, _ -> }

            assertArrayEquals(payload, result.readBytes())
            assertEquals(DownloadConnectionMode.SINGLE, report!!.connectionMode)
            assertEquals(1, report!!.connectionCount)
            assertFalse(report!!.rangeSupported)
            assertTrue(report!!.fallbackReason!!.contains("206"))
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun mismatchedSegmentContentRangeFallsBackWithoutPublishingCorruptParts() {
        val payload = fakeMp4Payload(size = 512 * 1024)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")
                    if (range == null) {
                        return MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
                    }
                    if (range == "bytes=0-0") {
                        return MockResponse()
                            .setResponseCode(206)
                            .setHeader("Content-Range", "bytes 0-0/${payload.size}")
                            .setBody(Buffer().write(payload, 0, 1))
                    }
                    val bounds = range.removePrefix("bytes=").split('-', limit = 2)
                    val start = bounds[0].toInt()
                    val end = bounds[1].toInt()
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-${end - start}/${payload.size}")
                        .setBody(Buffer().write(payload.copyOfRange(start, end + 1)))
                }
            }
            start()
        }
        val directory = createTempDirectory("download-range-mismatch-").toFile()
        try {
            var report: DownloadThroughputReport? = null
            val result = HttpFileDownloader(
                retryDelayMillis = 0,
                maxSegments = 2,
                segmentedThresholdBytes = 1,
            ).download(
                DirectDownloadRequest(
                    url = server.url("/video.mp4").toString(),
                    headers = emptyMap(),
                    target = File(directory, "video.mp4"),
                    taskId = "task-mismatch",
                    platform = DownloadPlatform.TIKTOK,
                    onReport = { report = it },
                ),
                AtomicBoolean(false),
            ) { _, _ -> }

            assertArrayEquals(payload, result.readBytes())
            assertEquals(DownloadConnectionMode.SINGLE, report!!.connectionMode)
            assertTrue(report!!.fallbackReason!!.contains("不稳定"))
            assertFalse(directory.listFiles().orEmpty().any { ".segment-" in it.name })
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun retriesInterruptedResponseFromPartialLength() {
        val payload = fakeMp4Payload()
        var requestCount = 0
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requestCount += 1
                    if (requestCount == 1) {
                        return MockResponse()
                            .setResponseCode(200)
                            .setBody(Buffer().write(payload))
                            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    }
                    val start = request.getHeader("Range")
                        ?.removePrefix("bytes=")
                        ?.substringBefore('-')
                        ?.toIntOrNull()
                        ?: 0
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-${payload.lastIndex}/${payload.size}")
                        .setBody(Buffer().write(payload.copyOfRange(start, payload.size)))
                }
            }
            start()
        }
        val directory = createTempDirectory("download-retry-").toFile()
        try {
            val target = File(directory, "video.mp4")

            val result = HttpFileDownloader(retryDelayMillis = 0).download(
                request = DirectDownloadRequest(
                    url = server.url("/video.mp4").toString(),
                    headers = emptyMap(),
                    target = target,
                ),
                cancelled = AtomicBoolean(false),
                onProgress = { _, _ -> },
            )

            assertArrayEquals(payload, result.readBytes())
            assertTrue(requestCount >= 2)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun resumesPartialDownloadAndProducesExactFile() {
        val payload = fakeMp4Payload()
        val server = startRangeServer(payload)
        val directory = createTempDirectory("download-probe-").toFile()
        try {
            val target = File(directory, "video.mp4")
            File(directory, "video.mp4.part").writeBytes(payload.copyOfRange(0, 4096))
            var latestProgress = 0L

            val result = HttpFileDownloader().download(
                request = DirectDownloadRequest(
                    url = server.url("/video.mp4").toString(),
                    headers = emptyMap(),
                    target = target,
                ),
                cancelled = AtomicBoolean(false),
                onProgress = { downloaded, _ -> latestProgress = downloaded },
            )

            assertArrayEquals(payload, result.readBytes())
            assertFalse(File(directory, "video.mp4.part").exists())
            assertTrue(latestProgress == payload.size.toLong())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellationDoesNotPublishTargetFile() {
        val server = startRangeServer(fakeMp4Payload())
        val directory = createTempDirectory("download-cancel-").toFile()
        try {
            val target = File(directory, "video.mp4")

            assertThrows(CancellationException::class.java) {
                HttpFileDownloader().download(
                    request = DirectDownloadRequest(
                        url = server.url("/video.mp4").toString(),
                        headers = emptyMap(),
                        target = target,
                    ),
                    cancelled = AtomicBoolean(true),
                    onProgress = { _, _ -> },
                )
            }
            assertFalse(target.exists())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun httpErrorDoesNotPublishTargetFile() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(403).setBody("<html>blocked</html>"))
            start()
        }
        val directory = createTempDirectory("download-error-").toFile()
        try {
            val target = File(directory, "video.mp4")

            assertThrows(IOException::class.java) {
                HttpFileDownloader().download(
                    request = DirectDownloadRequest(
                        url = server.url("/video.mp4").toString(),
                        headers = emptyMap(),
                        target = target,
                    ),
                    cancelled = AtomicBoolean(false),
                    onProgress = { _, _ -> },
                )
            }
            assertFalse(target.exists())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun startRangeServer(payload: ByteArray): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val start = request.getHeader("Range")
                    ?.removePrefix("bytes=")
                    ?.substringBefore('-')
                    ?.toIntOrNull()
                    ?: 0
                val body = payload.copyOfRange(start, payload.size)
                return MockResponse()
                    .setResponseCode(if (start > 0) 206 else 200)
                    .setHeader("Accept-Ranges", "bytes")
                    .apply {
                        if (start > 0) {
                            setHeader(
                                "Content-Range",
                                "bytes $start-${payload.lastIndex}/${payload.size}",
                            )
                        }
                    }
                    .setBody(Buffer().write(body))
            }
        }
        start()
    }

    private fun fakeMp4Payload(size: Int = 70 * 1024): ByteArray = ByteArray(size).also { payload ->
        byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
            .copyInto(payload)
    }
}
