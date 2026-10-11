package com.nanzhufeng.videodownloader.domain.download.video

import androidx.media3.common.util.UnstableApi
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@UnstableApi
class Media3TrackMuxerTest {
    @get:Rule val temp = TemporaryFolder()
    private fun sources() = temp.newFile("video.mp4").apply { writeText("video-input") } to
        temp.newFile("audio.m4a").apply { writeText("audio-input") }

    @Test fun long44100AudioAndFractionalVideoDoNotAccumulateMuxClockRounding() {
        for ((samples, sampleStep, rate, timebase) in listOf(
            listOf(161961, 1024, 44100, 48000),
            listOf(225600, 1001, 60000, 90000),
        )) {
            var previous = muxTimestampUs(0, timebase)
            var ticks = 0L
            for (index in 1 until samples) {
                val sourceUs = index.toLong() * sampleStep * 1_000_000L / rate
                val next = muxTimestampUs(sourceUs, timebase)
                ticks += Math.round((next - previous).toDouble() * timebase / 1_000_000.0)
                assertTrue(kotlin.math.abs(next - sourceUs) <= 12)
                previous = next
            }
            val expected = Math.round((samples - 1L) * sampleStep.toDouble() * timebase / rate)
            assertTrue("cumulative clock drift: ${ticks - expected}", kotlin.math.abs(ticks - expected) <= 1)
        }
    }

    @Test fun completedCompressedExportReplacesOnlyItsCacheOutput() = runBlocking {
        val (video, audio) = sources()
        val output = temp.newFile("merged.mp4").apply { writeText("old-cache") }
        val muxer = Media3TrackMuxer(CompressedTrackExporter { v, a, pending, _, _ ->
            assertEquals(video, v); assertEquals(audio, a)
            assertEquals("old-cache", output.readText())
            pending.writeText("complete-compressed-export")
            TrackExportSummary(videoFrames = 30, hasAudio = true)
        })
        assertEquals(output, muxer.merge(video, audio, output, AtomicBoolean(false)) { })
        assertEquals("complete-compressed-export", output.readText())
        assertEquals("video-input", video.readText()); assertEquals("audio-input", audio.readText())
        assertFalse(temp.root.listFiles()!!.any { it.name.endsWith(".mux.part") })
    }

    @Test fun cancellationAndInvalidExportsKeepThePriorCacheIntact() = runBlocking {
        val (video, audio) = sources()
        for (scenario in listOf("cancel", "encoder", "missing-track", "missing-audio", "empty", "failure")) {
            val output = temp.newFile("$scenario.mp4").apply { writeText("old-cache") }
            val cancelled = AtomicBoolean(false)
            val muxer = Media3TrackMuxer(CompressedTrackExporter { _, _, pending, _, _ ->
                if (scenario != "empty") pending.writeText("partial")
                if (scenario == "cancel") cancelled.set(true)
                if (scenario == "failure") error("export failed")
                TrackExportSummary(videoEncoder = if (scenario == "encoder") "unexpected-encoder" else null,
                    videoFrames = if (scenario == "missing-track") 0 else 30, hasAudio = scenario != "missing-audio")
            })
            try {
                muxer.merge(video, audio, output, cancelled) { }
                fail("$scenario must fail without replacing the old cache")
            } catch (error: Exception) {
                if (scenario == "cancel") assertTrue(error is CancellationException)
            }
            assertEquals("old-cache", output.readText())
            assertFalse(temp.root.listFiles()!!.any { it.name.endsWith(".mux.part") })
        }
    }
}
