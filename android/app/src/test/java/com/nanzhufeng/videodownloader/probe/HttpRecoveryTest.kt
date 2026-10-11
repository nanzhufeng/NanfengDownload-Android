package com.nanzhufeng.videodownloader.probe

import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempDirectory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class HttpRecoveryTest {
    private val payload = ByteArray(96 * 1024) { (it % 251).toByte() }
    private fun request(server: MockWebServer, root: File) = DirectDownloadRequest(
        server.url("/video").toString(), emptyMap(), File(root, "video.mp4"),
        transferPolicy = TransferPolicy("TEST", 1, Long.MAX_VALUE),
    )

    @Test fun transientServerErrorsRecoverWithoutFailingTheTask() {
        for (status in listOf(408, 429, 500, 502, 503, 504)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(status).setHeader("Retry-After", "0"))
                server.enqueue(MockResponse().setBody(Buffer().write(payload)))
                server.start()
                val root = createTempDirectory("http-recovery-").toFile()
                try {
                    val result = HttpFileDownloader(maxAttempts = 2, retryDelayMillis = 0)
                        .download(request(server, root), AtomicBoolean(false)) { _, _ -> }
                    assertArrayEquals(payload, result.readBytes())
                    assertEquals(2, server.requestCount)
                } finally { root.deleteRecursively() }
            }
        }
    }

    @Test fun rateLimitPreservesAndResumesTheExistingPrefix() {
        MockWebServer().use { server ->
            val prefixSize = 8192
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes $prefixSize-${payload.lastIndex}/${payload.size}")
                .setBody(Buffer().write(payload.copyOfRange(prefixSize, payload.size))))
            server.start()
            val root = createTempDirectory("http-resume-rate-").toFile()
            try {
                File(root, "video.mp4.part").writeBytes(payload.copyOfRange(0, prefixSize))
                val result = HttpFileDownloader(maxAttempts = 2, retryDelayMillis = 0)
                    .download(request(server, root), AtomicBoolean(false)) { _, _ -> }
                assertArrayEquals(payload, result.readBytes())
                repeat(2) { assertEquals("bytes=8192-", server.takeRequest().getHeader("Range")) }
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun permanentErrorsAndExhaustedRetriesNeverPublish() {
        for (status in listOf(403, 503)) {
            MockWebServer().use { server ->
                repeat(2) { server.enqueue(MockResponse().setResponseCode(status)) }
                server.start()
                val root = createTempDirectory("http-exhaust-").toFile()
                try {
                    val error = assertThrows(HttpDownloadException::class.java) {
                        HttpFileDownloader(maxAttempts = 2, retryDelayMillis = 0)
                            .download(request(server, root), AtomicBoolean(false)) { _, _ -> }
                    }
                    assertEquals(status, error.statusCode)
                    assertFalse(File(root, "video.mp4").exists())
                    assertEquals(if (status == 403) 1 else 2, server.requestCount)
                } finally { root.deleteRecursively() }
            }
        }
    }

    @Test fun serverRetryAfterIsRespectedButCancellationIsPrompt() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "5"))
            server.start()
            val root = createTempDirectory("http-cancel-retry-").toFile()
            val cancelled = AtomicBoolean(false)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<Throwable?> {
                    try {
                        HttpFileDownloader(maxAttempts = 2, retryDelayMillis = 0)
                            .download(request(server, root), cancelled) { _, _ -> }
                        null
                    } catch (error: Throwable) { error }
                }
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                Thread.sleep(200)
                assertFalse("must wait for Retry-After", future.isDone)
                cancelled.set(true)
                assertTrue(future.get(1, TimeUnit.SECONDS) is CancellationException)
                assertEquals(1, server.requestCount)
                assertFalse(File(root, "video.mp4").exists())
            } finally { executor.shutdownNow(); root.deleteRecursively() }
        }
    }

    @Test fun longServerCooldownIsNotShortenedAndHammered() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3600"))
            server.start()
            val root = createTempDirectory("http-long-cooldown-").toFile()
            try {
                val error = assertThrows(HttpDownloadException::class.java) {
                    HttpFileDownloader(maxAttempts = 2, retryDelayMillis = 0)
                        .download(request(server, root), AtomicBoolean(false)) { _, _ -> }
                }
                assertTrue(error.retryAfterMillis!! > 60_000L)
                assertEquals(1, server.requestCount)
                assertFalse(File(root, "video.mp4").exists())
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun httpDateCooldownIsParsedAgainstTheCurrentClock() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503)
                .setHeader("Retry-After", "Wed, 21 Oct 2015 07:30:00 GMT"))
            server.start()
            val root = createTempDirectory("http-date-cooldown-").toFile()
            try {
                val error = assertThrows(HttpDownloadException::class.java) {
                    HttpFileDownloader(maxAttempts = 1, epochMillis = { 1445412480000L })
                        .download(request(server, root), AtomicBoolean(false)) { _, _ -> }
                }
                assertEquals(120_000L, error.retryAfterMillis)
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun boundedChunksStillUseParallelConnectionsAndPreserveEveryByte() {
        MockWebServer().use { server ->
            val ranges = java.util.concurrent.CopyOnWriteArrayList<String>()
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(r: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    val value = r.getHeader("Range")!!
                    ranges += value
                    val bounds = value.removePrefix("bytes=").split('-').map(String::toInt)
                    return MockResponse().setResponseCode(206)
                        .setHeader("Content-Range", "bytes ${bounds[0]}-${bounds[1]}/${payload.size}")
                        .setBody(Buffer().write(payload.copyOfRange(bounds[0], bounds[1] + 1)))
                }
            }
            server.start()
            val root = createTempDirectory("http-parallel-chunks-").toFile()
            var connections = 0
            try {
                val result = HttpFileDownloader(retryDelayMillis = 0).download(request(server, root).copy(
                    transferPolicy = TransferPolicy("TEST", 3, 1, chunkSizeBytes = 4096),
                    onModeResolved = { _, count -> connections = count },
                ), AtomicBoolean(false)) { _, _ -> }
                assertArrayEquals(payload, result.readBytes())
                assertEquals(3, connections)
                assertTrue(ranges.all {
                    val b = it.removePrefix("bytes=").split('-').map(String::toInt)
                    b[1] - b[0] + 1 <= 4096
                })
            } finally { root.deleteRecursively() }
        }
    }
}
