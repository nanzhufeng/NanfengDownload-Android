package com.nanzhufeng.videodownloader.probe

import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProtocolFileDownloaderTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun fragmentConcurrencyUsesTheTaskBudgetAndHasAHardCeiling() {
        for ((budget, expected) in listOf(1 to 1, 2 to 2, 24 to 6)) {
            val target = File(temp.root, "budget-$budget.mp4")
            val raw = temp.newFile("budget-$budget.ts").apply { writeText("raw") }
            var reportedCount = 0
            val downloader = ProtocolFileDownloader(
                segments = { _, _, observer -> assertEquals(expected, observer.connections()); raw },
                remux = { _, output, _ -> output.apply { writeText("muxed") } },
            )
            downloader.download(request(target).copy(
                downloadSpec = "segment-plan",
                transferPolicy = TransferPolicy("TEST", budget, 0),
                onModeResolved = { _, count -> reportedCount = count },
            ), AtomicBoolean(false)) { _, _ -> }
            assertEquals(expected, reportedCount)
        }
    }

    @Test fun ordinaryHttpStillUsesTheEstablishedDownloader() {
        val target = temp.newFile()
        val downloader = ProtocolFileDownloader(
            direct = FileDownloader { request, _, _ -> request.target.also { it.writeText("direct") } },
            segments = { _, _, _ -> error("must not download fragments") },
        )
        assertEquals(target, downloader.download(request(target), AtomicBoolean(false)) { _, _ -> })
        assertEquals("direct", target.readText())
    }

    @Test fun remuxStartIsReportedBeforeOutputWritingAndCompletionAfterward() {
        val target = File(temp.root, "stage.mp4")
        val raw = temp.newFile("stage.ts").apply { writeText("raw") }
        val phases = mutableListOf<String>()
        val downloader = ProtocolFileDownloader(
            segments = { _, _, _ -> raw },
            remux = { _, output, _ ->
                assertEquals(listOf("container_remux_started"), phases)
                assertFalse(output.exists())
                output.apply { writeText("muxed") }
            },
        )
        downloader.download(request(target).copy(downloadSpec = "plan",
            onPhaseMeasured = { phase, _ -> phases.add(phase) }), AtomicBoolean(false)) { _, _ -> }
        assertEquals(listOf("container_remux_started", "container_remux"), phases)
    }

    @Test fun rawSegmentsAreRemuxedBeforeBecomingTheTaskOutput() {
        val target = File(temp.root, "video.mp4")
        val raw = temp.newFile("fragments.ts").apply { writeText("raw") }
        val plan = File(target.absolutePath + ".hls-plan.json").apply { writeText("private-plan") }
        val progress = mutableListOf<Long>()
        val downloader = ProtocolFileDownloader(
            segments = { spec, _, observer ->
                assertEquals("segment-plan", spec)
                observer.progress(123, 456)
                raw
            },
            remux = { source, output, _ ->
                assertEquals(raw, source)
                assertFalse(output.exists())
                output.apply { writeText("remuxed") }
            },
        )
        assertEquals(target, downloader.download(request(target).copy(downloadSpec = "segment-plan"),
            AtomicBoolean(false)) { done, _ -> progress.add(done) })
        assertEquals("remuxed", target.readText())
        assertEquals(listOf(123L), progress)
        assertFalse(raw.exists())
        assertFalse(plan.exists())
    }

    @Test fun directWebmMustBeRemuxedBeforeTheMp4OutputAppears() {
        val target = File(temp.root, "video.mp4")
        val downloader = ProtocolFileDownloader(
            direct = FileDownloader { request, _, _ ->
                assertTrue(request.target.name.endsWith(".source"))
                request.target.also { it.writeText("synthetic-webm") }
            },
            remux = { source, output, _ ->
                assertEquals("synthetic-webm", source.readText())
                output.apply { writeText("synthetic-mp4") }
            },
        )
        downloader.download(request(target).copy(remuxToMp4 = true), AtomicBoolean(false)) { _, _ -> }
        assertEquals("synthetic-mp4", target.readText())
        assertFalse(File(temp.root, "video.mp4.source").exists())
    }

    @Test fun failedRemuxCannotPublishRawFragments() {
        val target = File(temp.root, "video.mp4")
        val raw = temp.newFile("fragments.ts")
        val downloader = ProtocolFileDownloader(segments = { _, _, _ -> raw },
            remux = { _, _, _ -> error("unsupported codec") })
        assertThrows(IllegalStateException::class.java) {
            downloader.download(request(target).copy(downloadSpec = "segment-plan"), AtomicBoolean(false)) { _, _ -> }
        }
        assertFalse(target.exists())
        assertTrue(raw.exists())
    }

    @Test fun cancelledDownloadNeverStartsRemux() {
        val target = File(temp.root, "video.mp4")
        assertThrows(CancellationException::class.java) {
            ProtocolFileDownloader(segments = { _, _, _ -> error("must not start") })
                .download(request(target).copy(downloadSpec = "segment-plan"), AtomicBoolean(true)) { _, _ -> }
        }
        assertFalse(target.exists())
    }

    @Test fun timelineAndOriginalSurviveFailureAndAreRemovedOnlyAfterSuccessfulMux() {
        val target = File(temp.root, "video.mp4")
        val original = temp.newFile("video.mp4.0123456789abcdef01234567.segments").apply { writeText("original") }
        val normalized = File(original.path + ".timeline.ts").apply { writeText("normalized") }
        val downloader = ProtocolFileDownloader(
            segments = { _, _, _ -> normalized },
            remux = { _, _, _ -> error("mux failed") },
        )
        assertThrows(IllegalStateException::class.java) {
            downloader.download(request(target).copy(downloadSpec = "plan"), AtomicBoolean(false)) { _, _ -> }
        }
        assertEquals("original", original.readText())
        assertEquals("normalized", normalized.readText())
        assertFalse(target.exists())
        ProtocolFileDownloader(
            segments = { _, _, _ -> normalized },
            remux = { _, output, _ -> output.apply { writeText("complete") } },
        ).download(request(target).copy(downloadSpec = "plan"), AtomicBoolean(false)) { _, _ -> }
        assertEquals("complete", target.readText())
        assertFalse(original.exists())
        assertFalse(normalized.exists())
    }

    @Test fun replacementWaitsUntilCancelledRemuxReleasesTheSameTaskCache() {
        val target = File(temp.root, "video.mp4")
        val raw = temp.newFile("owned.segments").apply { writeText("original") }
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val muxAttempts = AtomicInteger()
        val firstStopped = AtomicBoolean(false)
        val failures = ConcurrentHashMap<String, Throwable>()
        val downloader = ProtocolFileDownloader(
            segments = { _, _, _ -> raw },
            remux = { _, output, stopped ->
                if (muxAttempts.incrementAndGet() == 1) {
                    firstEntered.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS)
                    if (stopped.get()) throw CancellationException("old mux cancelled")
                }
                output.apply { writeText("complete") }
            },
        )
        val input = request(target).copy(downloadSpec = "plan")
        val first = Thread {
            try { downloader.download(input, firstStopped) { _, _ -> } }
            catch (error: Throwable) { failures["first"] = error }
        }
        val second = Thread {
            try { downloader.download(input, AtomicBoolean(false)) { _, _ -> } }
            catch (error: Throwable) { failures["second"] = error }
            finally { secondFinished.countDown() }
        }
        first.start()
        var raced = false
        try {
            assertTrue(firstEntered.await(3, TimeUnit.SECONDS))
            second.start()
            raced = secondFinished.await(300, TimeUnit.MILLISECONDS)
        } finally {
            firstStopped.set(true)
            releaseFirst.countDown()
            first.join(5_000)
            if (second.state != Thread.State.NEW) second.join(5_000)
        }
        assertFalse("successor must not reuse a still-owned mux destination", raced)
        assertTrue(failures["first"] is CancellationException)
        assertNull(failures["second"])
        assertEquals("complete", target.readText())
    }

    @Test fun expiredSegmentUrlReachesTheExistingReprobePolicy() {
        val target = File(temp.root, "expired.mp4")
        val downloader = ProtocolFileDownloader(segments = { _, _, observer ->
            observer.httpFailure(403)
            error("fragment failed")
        })
        val failure = assertThrows(HttpDownloadException::class.java) {
            downloader.download(request(target).copy(downloadSpec = "segment-plan"), AtomicBoolean(false)) { _, _ -> }
        }
        assertEquals(403, failure.statusCode)
        assertTrue(com.nanzhufeng.videodownloader.domain.download.TransferReprobePolicy.shouldReprobe(failure))
        assertFalse(target.exists())
    }

    private fun request(file: File) = DirectDownloadRequest("https://example.test/stream", emptyMap(), file)
}
