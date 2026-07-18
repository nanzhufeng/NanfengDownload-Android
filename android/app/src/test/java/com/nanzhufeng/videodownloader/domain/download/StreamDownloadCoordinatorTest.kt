package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.probe.DirectDownloadRequest
import com.nanzhufeng.videodownloader.probe.FileDownloader
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDownloadCoordinatorTest {
    @Test
    fun downloadsIndependentVideoAndAudioStreamsConcurrently() = runBlocking {
        val directory = createTempDirectory("parallel-streams-").toFile()
        val bothStarted = CountDownLatch(2)
        val release = CountDownLatch(1)
        val active = AtomicInteger(0)
        val peakActive = AtomicInteger(0)
        val downloader = FileDownloader { request, _, onProgress ->
            val currentActive = active.incrementAndGet()
            peakActive.updateAndGet { previous -> maxOf(previous, currentActive) }
            bothStarted.countDown()
            assertTrue("video and audio should start together", bothStarted.await(2, TimeUnit.SECONDS))
            release.countDown()
            release.await(2, TimeUnit.SECONDS)
            request.target.writeBytes(ByteArray(if (request.target.name.startsWith("video")) 800 else 200))
            onProgress(request.target.length(), request.target.length())
            active.decrementAndGet()
            request.target
        }
        val progress = Collections.synchronizedList(mutableListOf<TransferProgress>())

        try {
            val requests = listOf(
                DirectDownloadRequest("https://example.invalid/video", emptyMap(), File(directory, "video.mp4")),
                DirectDownloadRequest("https://example.invalid/audio", emptyMap(), File(directory, "audio.m4a")),
            )
            val files = withTimeout(3_000) {
                StreamDownloadCoordinator(downloader, isComplete = { false }).download(
                    requests = requests,
                    cancelled = AtomicBoolean(false),
                    onProgress = progress::add,
                )
            }

            assertEquals(2, peakActive.get())
            assertEquals(requests.map { it.target }, files)
            assertTrue(files.all(File::isFile))
            assertEquals(1_000L, progress.last().downloadedBytes)
            assertEquals(1_000L, progress.last().totalBytes)
        } finally {
            directory.deleteRecursively()
        }
    }
}
