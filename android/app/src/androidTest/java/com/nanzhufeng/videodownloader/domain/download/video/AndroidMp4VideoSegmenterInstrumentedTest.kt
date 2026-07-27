package com.nanzhufeng.videodownloader.domain.download.video

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMp4VideoSegmenterInstrumentedTest {
    @Test
    fun splitsOneDownloadedMp4IntoIndependentlyPlayableSegments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "video-segmenter-test").apply { mkdirs() }
        val source = File(directory, "source.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("video/segment-source-6s.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val destinations = (1..3).map { index -> File(directory, "segment-$index.mp4") }
        val progress = mutableListOf<Int>()

        val output = AndroidMp4VideoSegmenter().split(
            source = source,
            destinations = destinations,
            cancelled = AtomicBoolean(false),
            onProgress = progress::add,
        )

        assertEquals(3, output.size)
        val durations = output.map(::inspect)
        assertTrue(durations.all { evidence -> evidence.hasVideo && evidence.hasAudio })
        assertTrue(durations.all { evidence -> evidence.durationUs in 1_500_000L..2_500_000L })
        assertTrue(durations.all { evidence -> evidence.firstVideoSampleIsSync })
        assertTrue(output.all { file -> file.length() > 32 * 1024L })
        assertTrue(progress.zipWithNext().all { (before, after) -> before <= after })
        assertEquals(100, progress.last())

        output.forEach(File::delete)
        source.delete()
        directory.delete()
        Unit
    }

    private fun inspect(file: File): SegmentEvidence {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val videoTrack = formats.indexOfFirst { format ->
                format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            val hasAudio = formats.any { format ->
                format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            val durationUs = formats.mapNotNull { format ->
                format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }
            }.maxOrNull() ?: 0L
            extractor.selectTrack(videoTrack)
            SegmentEvidence(
                hasVideo = videoTrack >= 0,
                hasAudio = hasAudio,
                durationUs = durationUs,
                firstVideoSampleIsSync =
                    extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0,
            )
        } finally {
            extractor.release()
        }
    }

    private data class SegmentEvidence(
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val durationUs: Long,
        val firstVideoSampleIsSync: Boolean,
    )
}
