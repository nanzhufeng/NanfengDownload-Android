package com.nanzhufeng.videodownloader.probe

import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempDirectory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCallGuardTest {
    @Test
    fun cancellationInterruptsStalledHeadersWithoutPublishing() {
        checkCancellation(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
    }

    @Test
    fun cancellationInterruptsStalledBodyWithoutPublishing() {
        checkCancellation(MockResponse().setBody("a".repeat(1024)).setBodyDelay(2, TimeUnit.SECONDS))
    }

    private fun checkCancellation(response: MockResponse) {
        val server = MockWebServer().apply { enqueue(response); start() }
        val directory = createTempDirectory("cancel-stalled-transfer-").toFile()
        val executor = Executors.newSingleThreadExecutor()
        val cancelled = AtomicBoolean(false)
        val target = File(directory, "video.mp4")
        try {
            val future = executor.submit<Boolean> {
                try {
                    HttpFileDownloader().download(
                        DirectDownloadRequest(server.url("/video").toString(), emptyMap(), target,
                            transferPolicy = TransferPolicy("TEST", 1, Long.MAX_VALUE)),
                        cancelled,
                    ) { _, _ -> }
                    false
                } catch (_: CancellationException) { true }
            }
            checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            cancelled.set(true)
            assertTrue("stalled call must cancel promptly", future.get(1, TimeUnit.SECONDS))
            assertFalse("cancelled response must not publish a target", target.exists())
        } finally {
            cancelled.set(true)
            executor.shutdownNow()
            server.shutdown()
            directory.deleteRecursively()
        }
    }
}
