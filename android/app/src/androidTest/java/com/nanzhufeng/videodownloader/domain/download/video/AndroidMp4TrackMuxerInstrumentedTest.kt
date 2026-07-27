package com.nanzhufeng.videodownloader.domain.download.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMp4TrackMuxerInstrumentedTest {
    @Test
    fun mergeProducesPlayableVideoAndAudioWithoutTranscoding() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val directory = File(context.cacheDir, "mux-instrumented").apply {
            deleteRecursively()
            mkdirs()
        }
        val video = File(directory, "video.mp4")
        val audio = File(directory, "audio.m4a")
        testAssets.open("video/segment-source-6s.mp4").use { input ->
            video.outputStream().use(input::copyTo)
        }
        testAssets.open("audio/tone-2s-aac.m4a").use { input ->
            audio.outputStream().use(input::copyTo)
        }
        val output = File(directory, "merged.mp4")

        val startedAt = SystemClock.elapsedRealtime()
        val progress = mutableListOf<Int>()
        AndroidMp4TrackMuxer().merge(
            video = video,
            audio = audio,
            output = output,
            cancelled = AtomicBoolean(false),
            onProgress = progress::add,
        )
        val elapsedMillis = SystemClock.elapsedRealtime() - startedAt

        assertTrue(output.isFile && output.length() > 0L)
        assertEquals(setOf("video/avc", "audio/mp4a-latm"), trackMimes(output))
        assertEquals(100, progress.last())
        assertEquals(progress.sorted(), progress)
        println(
            "MUX_BENCHMARK elapsedMs=$elapsedMillis inputBytes=${video.length() + audio.length()} " +
                "outputBytes=${output.length()}",
        )
    }

    @Test
    fun cancelledMergeDeletesPartialOutput() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val directory = File(context.cacheDir, "mux-cancelled").apply {
            deleteRecursively()
            mkdirs()
        }
        val video = File(directory, "video.mp4")
        val audio = File(directory, "audio.m4a")
        testAssets.open("video/segment-source-6s.mp4").use { input ->
            video.outputStream().use(input::copyTo)
        }
        testAssets.open("audio/tone-2s-aac.m4a").use { input ->
            audio.outputStream().use(input::copyTo)
        }
        val output = File(directory, "merged.mp4")

        val error = runCatching {
            AndroidMp4TrackMuxer().merge(
                video = video,
                audio = audio,
                output = output,
                cancelled = AtomicBoolean(true),
                onProgress = {},
            )
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertTrue(!output.exists())
    }

    @Test
    fun largeLocalMediaBenchmarkWhenFixtureIsProvided() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val video = File(context.cacheDir, "mux-benchmark-video.mp4")
        val audio = File(context.cacheDir, "mux-benchmark-audio.m4a")
        assumeTrue("未提供大文件合并性能夹具", video.isFile && audio.isFile)
        val output = File(context.cacheDir, "mux-benchmark-output.mp4")
        output.delete()

        val startedAt = SystemClock.elapsedRealtime()
        AndroidMp4TrackMuxer().merge(
            video = video,
            audio = audio,
            output = output,
            cancelled = AtomicBoolean(false),
            onProgress = {},
        )
        val elapsedMillis = SystemClock.elapsedRealtime() - startedAt

        assertEquals(setOf("video/avc", "audio/mp4a-latm"), trackMimes(output))
        println(
            "MUX_LARGE_BENCHMARK elapsedMs=$elapsedMillis " +
                "inputBytes=${video.length() + audio.length()} outputBytes=${output.length()}",
        )
    }

    private fun trackMimes(file: File): Set<String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).mapNotNullTo(linkedSetOf()) { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            }
        } finally {
            extractor.release()
        }
    }
}
