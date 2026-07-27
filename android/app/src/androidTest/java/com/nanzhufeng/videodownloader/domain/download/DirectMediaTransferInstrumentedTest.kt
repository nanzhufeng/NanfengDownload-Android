package com.nanzhufeng.videodownloader.domain.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.domain.download.audio.Mp3FileValidator
import com.nanzhufeng.videodownloader.probe.FileDownloader
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectMediaTransferInstrumentedTest {
    @Test
    fun audioModeRoutesCachedM4aThroughTrueMp3Transcoder() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val taskId = "direct-mp3-instrumented"
        val directory = File(context.cacheDir, "downloads/$taskId")
        directory.deleteRecursively()
        directory.mkdirs()
        val cachedSource = File(directory, "audio-source.m4a")
        instrumentation.context.assets.open("audio/tone-2s-aac.m4a").use { input ->
            cachedSource.outputStream().use { output ->
                input.copyTo(output)
                output.write(ByteArray(40 * 1_024))
            }
        }

        try {
            val prepared = DirectMediaTransfer(context).download(
                task = queuedDownload(taskId),
                source = ResolvedMedia(
                    videoUrl = "http://unused.invalid/audio.m4a",
                    audioUrl = null,
                    videoExtension = "m4a",
                    audioExtension = null,
                    headers = emptyMap(),
                ),
                onProgress = { _, _, _, _ -> },
            )

            assertEquals("audio/mpeg", prepared.mimeType)
            assertEquals("audio.mp3", prepared.file.name)
            assertTrue(Mp3FileValidator.isValid(prepared.file))
            assertFalse("Compressed source should be removed after conversion", cachedSource.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun audioModeExtractsTrueMp3FromCached720pVideoFallback() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val taskId = "direct-video-to-mp3-instrumented"
        val directory = File(context.cacheDir, "downloads/$taskId")
        directory.deleteRecursively()
        directory.mkdirs()
        val cachedSource = File(directory, "audio-source.mp4")
        instrumentation.context.assets.open("audio/tone-2s-video-with-audio.mp4").use { input ->
            cachedSource.outputStream().use { output ->
                input.copyTo(output)
                output.write(ByteArray(40 * 1_024))
            }
        }
        var finalDownloaded = 0L
        var finalTotal = 0L
        val processing = CopyOnWriteArrayList<Pair<DownloadProcessingStage, Int>>()

        try {
            val prepared = DirectMediaTransfer(
                context,
                processingSink = { _, stage, progress -> processing += stage to progress },
            ).download(
                task = queuedDownload(taskId),
                source = ResolvedMedia(
                    videoUrl = "http://unused.invalid/video.mp4",
                    audioUrl = null,
                    videoExtension = "mp4",
                    audioExtension = null,
                    headers = emptyMap(),
                    audioFromVideoSource = true,
                ),
                onProgress = { downloaded, total, _, _ ->
                    finalDownloaded = downloaded
                    finalTotal = total
                },
            )

            assertEquals("audio/mpeg", prepared.mimeType)
            assertEquals("audio.mp3", prepared.file.name)
            assertTrue(Mp3FileValidator.isValid(prepared.file))
            assertFalse("Video source should be removed after conversion", cachedSource.exists())
            assertTrue(finalTotal > 0L)
            assertEquals(finalTotal, finalDownloaded)
            assertTrue(processing.contains(DownloadProcessingStage.NETWORK_VIDEO_TO_AUDIO to 0))
            assertTrue(processing.any { it.first == DownloadProcessingStage.TRANSCODING && it.second in 1..99 })
            assertEquals(DownloadProcessingStage.TRANSCODING to 100, processing.last())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun audioModeCreatesRequestedSegmentsAndPersistsTranscodeTiming() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val taskId = "direct-segmented-mp3-instrumented"
        val directory = File(context.cacheDir, "downloads/$taskId")
        directory.deleteRecursively()
        directory.mkdirs()
        val cachedSource = File(directory, "audio-source.m4a")
        instrumentation.context.assets.open("audio/tone-2s-aac.m4a").use { input ->
            cachedSource.outputStream().use(input::copyTo)
        }
        val sourceBytes = cachedSource.length()
        val reports = CopyOnWriteArrayList<DownloadThroughputReport>()
        val processing = CopyOnWriteArrayList<Pair<DownloadProcessingStage, Int>>()
        var nowNanos = 1_000_000_000L

        try {
            val prepared = DirectMediaTransfer(
                context,
                throughputReportSink = { reports += it },
                processingSink = { _, stage, progress -> processing += stage to progress },
                monotonicNanos = {
                    nowNanos += 500_000_000L
                    nowNanos
                },
                wallClockMillis = { 1_000L },
            ).download(
                task = queuedDownload(taskId, audioSegmentCount = 2),
                source = ResolvedMedia(
                    videoUrl = "http://unused.invalid/audio.m4a",
                    audioUrl = null,
                    videoExtension = "m4a",
                    audioExtension = null,
                    headers = emptyMap(),
                ),
                onProgress = { _, _, _, _ -> },
            )

            assertEquals(2, prepared.files.size)
            assertTrue(prepared.files.all(Mp3FileValidator::isValid))
            assertTrue(prepared.files.all { it.name.contains("-of-2") })
            val report = reports.single { it.streamLabel == "MP3 转码" }
            assertTrue(report.elapsedMillis > 0L)
            assertEquals(sourceBytes, report.expectedBytes)
            assertEquals(prepared.files.sumOf(File::length), report.committedBytes)
            assertEquals(0L, report.networkBytes)
            assertTrue(report.fallbackReason.orEmpty().contains("2 段"))
            val transcodeProgress = processing
                .filter { it.first == DownloadProcessingStage.TRANSCODING }
                .map { it.second }
            assertTrue(
                "转码进度必须稳定单向递增",
                transcodeProgress.zipWithNext().all { (before, after) -> before <= after },
            )
            assertEquals(100, transcodeProgress.last())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun videoModeDownloadsOnceAndCreatesRequestedIndependentMp4Segments() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val taskId = "direct-segmented-video-instrumented"
        val directory = File(context.cacheDir, "downloads/$taskId")
        directory.deleteRecursively()
        val reports = CopyOnWriteArrayList<DownloadThroughputReport>()
        val processing = CopyOnWriteArrayList<Pair<DownloadProcessingStage, Int>>()
        var downloadCalls = 0
        val downloader = FileDownloader { request, _, onProgress ->
            downloadCalls += 1
            request.target.parentFile?.mkdirs()
            instrumentation.context.assets.open("video/segment-source-6s.mp4").use { input ->
                request.target.outputStream().use(input::copyTo)
            }
            onProgress(request.target.length(), request.target.length())
            request.target
        }

        try {
            val prepared = DirectMediaTransfer(
                context = context,
                downloader = downloader,
                throughputReportSink = { reports += it },
                processingSink = { _, stage, progress -> processing += stage to progress },
            ).download(
                task = queuedDownload(
                    taskId = taskId,
                    audioSegmentCount = 3,
                    resolution = ResolutionPreset.UP_TO_720P,
                ),
                source = ResolvedMedia(
                    videoUrl = "https://example.invalid/video.mp4",
                    audioUrl = null,
                    videoExtension = "mp4",
                    audioExtension = null,
                    headers = emptyMap(),
                ),
                onProgress = { _, _, _, _ -> },
            )

            assertEquals(1, downloadCalls)
            assertEquals(3, prepared.files.size)
            assertTrue(prepared.files.all { file -> file.extension == "mp4" && file.length() > 32 * 1_024L })
            assertTrue(processing.any { it.first == DownloadProcessingStage.VIDEO_SEGMENTING })
            assertEquals(DownloadProcessingStage.VIDEO_SEGMENTING to 100, processing.last())
            val report = reports.single { it.streamLabel == "视频本机分段" }
            assertEquals(0L, report.networkBytes)
            assertTrue(report.fallbackReason.orEmpty().contains("3 段"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun queuedDownload(
        taskId: String,
        audioSegmentCount: Int = 1,
        resolution: ResolutionPreset = ResolutionPreset.AUDIO_MP3,
    ) = QueuedDownload(
        task = DownloadTask(
            taskId = taskId,
            mediaKey = "youtube:test",
            selected = true,
            sortOrder = 0L,
            resolution = resolution,
            saveTreeUri = null,
            downloadedBytes = 0L,
            totalBytes = 0L,
            speedBytesPerSecond = 0L,
            remainingSeconds = null,
            status = DownloadTaskStatus.WAITING,
            failureType = null,
            errorSummary = null,
            retryCount = 0,
            updatedAt = 0L,
            audioSegmentCount = audioSegmentCount,
        ),
        media = MediaItem(
            mediaKey = "youtube:test",
            platform = DownloadPlatform.YOUTUBE,
            contentId = "test",
            originalUrl = "https://example.invalid/test",
            sourceKind = DownloadSourceKind.SINGLE_VIDEO,
            title = "Test tone",
            creator = "Codex",
            creatorId = "codex",
            publishDate = "2026-07-16",
            thumbnailUrl = "",
        ),
    )
}
