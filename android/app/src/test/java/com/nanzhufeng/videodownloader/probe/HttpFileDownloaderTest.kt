package com.nanzhufeng.videodownloader.probe

import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempDirectory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpFileDownloaderTest {
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

    private fun fakeMp4Payload(): ByteArray = ByteArray(70 * 1024).also { payload ->
        byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
            .copyInto(payload)
    }
}
