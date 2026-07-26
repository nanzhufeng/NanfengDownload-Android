package com.nanzhufeng.videodownloader.domain.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.domain.download.audio.Mp3FileValidator
import java.io.File
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

        try {
            val prepared = DirectMediaTransfer(context).download(
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
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun queuedDownload(taskId: String) = QueuedDownload(
        task = DownloadTask(
            taskId = taskId,
            mediaKey = "youtube:test",
            selected = true,
            sortOrder = 0L,
            resolution = ResolutionPreset.AUDIO_MP3,
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
