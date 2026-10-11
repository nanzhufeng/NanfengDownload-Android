package com.nanzhufeng.videodownloader.probe

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpAdaptiveTransferTest {
    @Test
    fun existingFourRangeCheckpointSurvivesTheNewSixConnectionPolicy() {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        val saved = 64 * 1024
        for (budget in listOf(6, 2)) {
            val requested = java.util.concurrent.CopyOnWriteArrayList<String>()
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val server = MockWebServer().apply {
                dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val range = request.getHeader("Range")!!
                        requested += range
                        if (range != "bytes=0-8191") {
                            val value = active.incrementAndGet()
                            peak.accumulateAndGet(value) { old, current -> maxOf(old, current) }
                            try { Thread.sleep(200) } finally { active.decrementAndGet() }
                        }
                        val bounds = range.removePrefix("bytes=").split('-')
                        return slice(payload, bounds[0].toInt(), bounds[1].toInt())
                    }
                }
                start()
            }
            val directory = createTempDirectory("douyin-existing-four-").toFile()
            try {
                File(directory, "video.part.segments.meta").writeText("${payload.size}:4")
                repeat(4) { index ->
                    val start = index * 1024 * 1024
                    File(directory, "video.part.segment-$index").writeBytes(payload.copyOfRange(start, start + saved))
                }
                val base = defaultDouyinRequest(server, directory)
                val file = HttpFileDownloader(maxAttempts = 1).download(
                    base.copy(transferPolicy = base.transferPolicy!!.copy(maxConnections = budget)), AtomicBoolean(false),
                ) { _, _ -> }
                assertArrayEquals(payload, file.readBytes())
                val dataRanges = requested.filter { it != "bytes=0-8191" }
                assertEquals("A valid old plan must keep all four saved fragments", 4, dataRanges.size)
                assertEquals(listOf(65536, 1114112, 2162688, 3211264), dataRanges.map { it.removePrefix("bytes=").substringBefore('-').toInt() }.sorted())
                assertTrue("Resuming four saved files must respect the current socket budget", peak.get() <= budget)
            } finally { server.shutdown(); directory.deleteRecursively() }
        }
    }

    @Test
    fun supportedVideoPlatformsActuallyStartSixConcurrentRanges() {
        val payload = ByteArray(6 * 1024 * 1024) { (it % 251).toByte() }
        val platforms = com.nanzhufeng.videodownloader.core.model.DownloadPlatform.entries.filter {
            it != com.nanzhufeng.videodownloader.core.model.DownloadPlatform.YOUTUBE
        }
        for (platform in platforms) {
            val started = java.util.concurrent.CountDownLatch(6)
            val server = MockWebServer().apply {
                dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val range = request.getHeader("Range")!!
                        val bounds = range.removePrefix("bytes=").split('-')
                        if (range != "bytes=0-8191") {
                            started.countDown()
                            check(started.await(3, TimeUnit.SECONDS)) { "${platform.name} did not start real concurrent ranges" }
                        }
                        return slice(payload, bounds[0].toInt(), bounds[1].toInt())
                    }
                }
                start()
            }
            val directory = createTempDirectory("shared-video-ranges-").toFile()
            try {
                val request = DirectDownloadRequest(server.url("/video").toString(), emptyMap(), File(directory, "video"),
                    platform = platform, transferPolicy = com.nanzhufeng.videodownloader.domain.download.PlatformTransferPolicy.forPlatform(platform))
                val file = HttpFileDownloader(maxAttempts = 1).download(request, AtomicBoolean(false)) { _, _ -> }
                assertArrayEquals(platform.name, payload, file.readBytes())
                assertEquals(platform.name, 0L, started.count)
                assertEquals(platform.name, 7, server.requestCount)
            } finally { server.shutdown(); directory.deleteRecursively() }
        }
    }

    @Test
    fun defaultDouyinStartsRealParallelRangesUsingABoundedPrefixProbe() {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        val ranges = java.util.concurrent.CopyOnWriteArrayList<String>()
        val started = java.util.concurrent.CountDownLatch(6)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")
                        ?: return MockResponse().setBody(Buffer().write(payload))
                    ranges += range
                    val bounds = range.removePrefix("bytes=").split('-')
                    if (range != "bytes=0-8191") {
                        started.countDown()
                        check(started.await(3, TimeUnit.SECONDS)) { "Six real ranges never became active" }
                    }
                    return slice(payload, bounds[0].toInt(), bounds[1].toIntOrNull() ?: payload.lastIndex)
                }
            }
            start()
        }
        val directory = createTempDirectory("douyin-default-parallel-").toFile()
        try {
            val file = HttpFileDownloader(maxAttempts = 1).download(
                defaultDouyinRequest(server, directory), AtomicBoolean(false),
            ) { _, _ -> }
            assertArrayEquals(payload, file.readBytes())
            assertEquals("Do not depend on the unreliable one-byte probe", "bytes=0-8191", ranges.firstOrNull())
            assertEquals(7, ranges.size)
            assertEquals(0L, started.count)
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun defaultDouyinSeedsTheExistingSinglePrefixBeforeParallelResume() {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        val prefixSize = 320 * 1024
        val ranges = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")!!
                    ranges += range
                    val bounds = range.removePrefix("bytes=").split('-')
                    return slice(payload, bounds[0].toInt(), bounds[1].toIntOrNull() ?: payload.lastIndex)
                }
            }
            start()
        }
        val directory = createTempDirectory("douyin-prefix-parallel-").toFile()
        try {
            File(directory, "video.part").writeBytes(payload.copyOf(prefixSize))
            val file = HttpFileDownloader(maxAttempts = 1).download(
                defaultDouyinRequest(server, directory), AtomicBoolean(false),
            ) { _, _ -> }
            assertArrayEquals(payload, file.readBytes())
            val dataRanges = ranges.filter { it != "bytes=0-8191" }
            assertEquals(6, dataRanges.size)
            assertTrue("The saved prefix must not be fetched again", dataRanges.any { it.startsWith("bytes=$prefixSize-") && !it.endsWith("-${payload.lastIndex}") })
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun severelySlowDouyinConnectionUpgradesWithoutRedownloadingItsSavedPrefix() {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        val ranges = java.util.concurrent.CopyOnWriteArrayList<String>()
        val clock = java.util.concurrent.atomic.AtomicLong()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")
                    if (range == null) return MockResponse().setBody(Buffer().write(payload))
                        .throttleBody(8192, 5, TimeUnit.MILLISECONDS)
                    ranges += range
                    val bounds = range.removePrefix("bytes=").split('-')
                    return slice(payload, bounds[0].toInt(), bounds[1].toInt())
                }
            }
            start()
        }
        val directory = createTempDirectory("adaptive-slow-douyin-").toFile()
        try {
            val file = HttpFileDownloader(retryDelayMillis = 0, monotonicNanos = clock::get).download(
                DirectDownloadRequest(server.url("/video").toString(), emptyMap(), File(directory, "video"),
                    platform = com.nanzhufeng.videodownloader.core.model.DownloadPlatform.DOUYIN,
                    transferPolicy = com.nanzhufeng.videodownloader.domain.download.PlatformTransferPolicy
                        .forPlatform(com.nanzhufeng.videodownloader.core.model.DownloadPlatform.DOUYIN)
                            .copy(preferSingleConnection = true, rangeProbeBytes = 1)),
                AtomicBoolean(false),
            ) { bytes, _ -> if (bytes > 0) clock.addAndGet(1_000_000_000L) }
            assertArrayEquals(payload, file.readBytes())
            assertTrue("A continuing trickle must not stay single forever", ranges.contains("bytes=0-0"))
            assertTrue("Upgrade must retain the single-stream prefix", ranges.filter { it != "bytes=0-0" }
                .any { it.substringAfter("bytes=").substringBefore('-').toLong() in 1 until 1024 * 1024L })
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun healthyDouyinConnectionHasNoProbeOrExtraRequest() {
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val server = MockWebServer().apply { enqueue(MockResponse().setBody(Buffer().write(payload))); start() }
        val directory = createTempDirectory("adaptive-fast-douyin-").toFile()
        try {
            val file = HttpFileDownloader().download(douyinRequest(server, directory), AtomicBoolean(false)) { _, _ -> }
            assertArrayEquals(payload, file.readBytes())
            assertEquals(1, server.requestCount)
            assertEquals(null, server.takeRequest().getHeader("Range"))
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun slowConnectionWithUnsupportedRangeFallsBackOnceAndKeepsAnExactResult() {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        val ranges = java.util.concurrent.CopyOnWriteArrayList<String>()
        val clock = java.util.concurrent.atomic.AtomicLong()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    request.getHeader("Range")?.let { ranges += it }
                    val response = MockResponse().setBody(Buffer().write(payload))
                    return if (request.getHeader("Range") == null) response.throttleBody(8192, 5, TimeUnit.MILLISECONDS) else response
                }
            }
            start()
        }
        val directory = createTempDirectory("adaptive-no-range-").toFile()
        try {
            val file = HttpFileDownloader(retryDelayMillis = 0, monotonicNanos = clock::get)
                .download(douyinRequest(server, directory), AtomicBoolean(false)) { bytes, _ ->
                    if (bytes > 0) clock.addAndGet(1_000_000_000L)
                }
            assertArrayEquals(payload, file.readBytes())
            assertEquals(1, ranges.count { it == "bytes=0-0" })
            assertEquals(2, ranges.size)
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun changedLengthDuringSlowRecoveryFailsWithoutPublishingOrDeletingThePrefix() {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        val clock = java.util.concurrent.atomic.AtomicLong()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.getHeader("Range") == null) MockResponse().setBody(Buffer().write(payload))
                        .throttleBody(8192, 5, TimeUnit.MILLISECONDS)
                    else MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/9000000").setBody("x")
            }
            start()
        }
        val directory = createTempDirectory("adaptive-changed-").toFile()
        try {
            try {
                HttpFileDownloader(retryDelayMillis = 0, monotonicNanos = clock::get)
                    .download(douyinRequest(server, directory), AtomicBoolean(false)) { bytes, _ ->
                        if (bytes > 0) clock.addAndGet(1_000_000_000L)
                    }
                org.junit.Assert.fail("Changed source must not be published")
            } catch (expected: java.io.IOException) {
                assertTrue(expected.message.orEmpty().contains("长度已变化"))
            }
            assertTrue(File(directory, "video.part").length() > 0)
            assertTrue(!File(directory, "video").exists())
            assertEquals(2, server.requestCount)
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    private fun defaultDouyinRequest(server: MockWebServer, directory: File) = DirectDownloadRequest(
        server.url("/video").toString(), emptyMap(), File(directory, "video"),
        platform = com.nanzhufeng.videodownloader.core.model.DownloadPlatform.DOUYIN,
        transferPolicy = com.nanzhufeng.videodownloader.domain.download.PlatformTransferPolicy
            .forPlatform(com.nanzhufeng.videodownloader.core.model.DownloadPlatform.DOUYIN),
    )

    private fun douyinRequest(server: MockWebServer, directory: File) = DirectDownloadRequest(
        server.url("/video").toString(), emptyMap(), File(directory, "video"),
        platform = com.nanzhufeng.videodownloader.core.model.DownloadPlatform.DOUYIN,
        transferPolicy = com.nanzhufeng.videodownloader.domain.download.PlatformTransferPolicy
            .forPlatform(com.nanzhufeng.videodownloader.core.model.DownloadPlatform.DOUYIN)
            .copy(preferSingleConnection = true, rangeProbeBytes = 1),
    )

    @Test
    fun slowParallelRangesResumeTheirBytesAfterOneFreshProbe() {
        val payload = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        val probes = AtomicInteger()
        val clock = java.util.concurrent.atomic.AtomicLong()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")!!
                    val bounds = range.removePrefix("bytes=").split('-')
                    val response = slice(payload, bounds[0].toInt(), bounds[1].toInt())
                    if (range == "bytes=0-0") { probes.incrementAndGet(); return response }
                    return if (probes.get() == 1) response.throttleBody(8192, 5, TimeUnit.MILLISECONDS) else response
                }
            }
            start()
        }
        val directory = createTempDirectory("adaptive-slow-parallel-").toFile()
        try {
            val file = HttpFileDownloader(retryDelayMillis = 0, monotonicNanos = clock::get)
                .download(request(server, directory, 2), AtomicBoolean(false)) { bytes, _ ->
                    if (bytes > 0) clock.addAndGet(1_000_000_000L)
                }
            assertArrayEquals(payload, file.readBytes())
            assertEquals("Slow ranges need one bounded recovery, not an endless trickle", 2, probes.get())
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun cancellationDuringSlowRecoveryProbeKeepsThePartialAndNeverPublishes() {
        val payload = ByteArray(4 * 1024 * 1024) { 1 }
        val clock = java.util.concurrent.atomic.AtomicLong()
        val probing = java.util.concurrent.CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.getHeader("Range") == null) MockResponse().setBody(Buffer().write(payload))
                        .throttleBody(8192, 5, TimeUnit.MILLISECONDS)
                    else { probing.countDown(); MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
            }
            start()
        }
        val directory = createTempDirectory("adaptive-cancel-").toFile()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<File> {
                HttpFileDownloader(retryDelayMillis = 0, monotonicNanos = clock::get)
                    .download(douyinRequest(server, directory), cancelled) { bytes, _ ->
                        if (bytes > 0) clock.addAndGet(1_000_000_000L)
                    }
            }
            assertTrue(probing.await(3, TimeUnit.SECONDS))
            cancelled.set(true)
            try { future.get(3, TimeUnit.SECONDS); org.junit.Assert.fail("Cancelled recovery cannot publish") }
            catch (expected: java.util.concurrent.ExecutionException) {
                assertTrue(expected.cause is java.util.concurrent.CancellationException)
            }
            assertTrue(File(directory, "video.part").length() > 0)
            assertTrue(!File(directory, "video").exists())
        } finally { cancelled.set(true); executor.shutdownNow(); server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun singleOnlySlowRecoveryRejectsAChangedResumeLength() {
        val payload = ByteArray(4 * 1024 * 1024) { 1 }
        val replacement = ByteArray(9_000_000) { 2 }
        val clock = java.util.concurrent.atomic.AtomicLong()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")
                    if (range == null) return MockResponse().setBody(Buffer().write(payload))
                        .throttleBody(8192, 5, TimeUnit.MILLISECONDS)
                    val start = range.removePrefix("bytes=").substringBefore('-').toInt()
                    return slice(replacement, start, replacement.lastIndex)
                }
            }
            start()
        }
        val directory = createTempDirectory("adaptive-single-changed-").toFile()
        try {
            try {
                HttpFileDownloader(retryDelayMillis = 0, monotonicNanos = clock::get)
                    .download(request(server, directory, 1), AtomicBoolean(false)) { bytes, _ ->
                        if (bytes > 0) clock.addAndGet(1_000_000_000L)
                    }
                org.junit.Assert.fail("Changed single-only source cannot reuse the old prefix")
            } catch (expected: java.io.IOException) {
                assertTrue(expected.message.orEmpty().contains("长度已变化"))
            }
            assertTrue(File(directory, "video.part").length() > 0)
            assertTrue(!File(directory, "video").exists())
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun fallsBackAfterRepeatedMultiConnectionFailuresWithoutProgress() {
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val ranges = AtomicInteger()
        val singles = AtomicInteger()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")
                    if (range == "bytes=0-0") return slice(payload, 0, 0)
                    if (range != null) {
                        ranges.incrementAndGet()
                        return emptySlice(range, payload.size)
                    }
                    singles.incrementAndGet()
                    return MockResponse().setBody(Buffer().write(payload))
                }
            }
            start()
        }
        val directory = createTempDirectory("adaptive-empty-").toFile()
        try {
            val file = downloader().download(request(server, directory, 3), AtomicBoolean(false)) { _, _ -> }
            assertArrayEquals(payload, file.readBytes())
            assertEquals(1, singles.get())
            assertEquals(6, ranges.get())
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun fallbackResumesFromCompletedPrefixWithoutDownloadingItAgain() {
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val split = payload.size / 2
        val prefixRequests = AtomicInteger()
        val resumes = AtomicInteger()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")!!
                    return when (range) {
                        "bytes=0-0" -> slice(payload, 0, 0)
                        "bytes=0-${split - 1}" -> { prefixRequests.incrementAndGet(); slice(payload, 0, split - 1) }
                        "bytes=$split-" -> { resumes.incrementAndGet(); slice(payload, split, payload.lastIndex) }
                        else -> emptySlice(range, payload.size)
                    }
                }
            }
            start()
        }
        val directory = createTempDirectory("adaptive-prefix-").toFile()
        try {
            val file = downloader().download(request(server, directory, 2), AtomicBoolean(false)) { _, _ -> }
            assertArrayEquals(payload, file.readBytes())
            assertEquals(1, prefixRequests.get())
            assertEquals(1, resumes.get())
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun publishesKnownSizeBeforeWaitingForTheFirstSegmentBytes() {
        val payload = ByteArray(128 * 1024) { 1 }
        val progress = mutableListOf<Pair<Long, Long>>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val bounds = request.getHeader("Range")!!.removePrefix("bytes=").split('-')
                    return slice(payload, bounds[0].toInt(), bounds[1].toInt())
                }
            }
            start()
        }
        val directory = createTempDirectory("adaptive-size-").toFile()
        try {
            downloader().download(request(server, directory, 2), AtomicBoolean(false)) { bytes, total ->
                synchronized(progress) { progress += bytes to total }
            }
            assertEquals(0L to payload.size.toLong(), progress.first())
            assertTrue(progress.last().first == payload.size.toLong())
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    private fun downloader() = HttpFileDownloader(
        maxAttempts = 4,
        retryDelayMillis = 0,
        httpClient = OkHttpClient.Builder().readTimeout(150, TimeUnit.MILLISECONDS).build(),
    )

    private fun request(server: MockWebServer, directory: File, connections: Int) = DirectDownloadRequest(
        server.url("/video").toString(), emptyMap(), File(directory, "video"),
        transferPolicy = TransferPolicy("TEST", connections, 1),
    )

    private fun slice(payload: ByteArray, start: Int, end: Int) = MockResponse()
        .setResponseCode(206)
        .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
        .setBody(Buffer().write(payload.copyOfRange(start, end + 1)))

    private fun emptySlice(range: String, total: Int) = MockResponse()
        .setResponseCode(206)
        .setHeader("Content-Range", "bytes ${range.removePrefix("bytes=")}/$total")
        .setBody(Buffer())
        .setHeader("Content-Length", total)
        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
}
