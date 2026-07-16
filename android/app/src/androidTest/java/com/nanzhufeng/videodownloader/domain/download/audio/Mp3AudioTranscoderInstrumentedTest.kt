package com.nanzhufeng.videodownloader.domain.download.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Mp3AudioTranscoderInstrumentedTest {
    @Test
    fun transcodesAacM4aToValidatedMp3() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "tone-2s-source.m4a")
        val destination = File(context.cacheDir, "tone-2s-result.mp3")
        copyFixture(source)
        destination.delete()

        try {
            Mp3AudioTranscoder().transcode(
                source = source,
                destination = destination,
                cancelled = AtomicBoolean(false),
            )

            assertTrue(source.isFile)
            assertTrue(destination.length() > 1_024L)
            assertFalse(source.readBytes().contentEquals(destination.readBytes()))
            val headerText = destination.inputStream().use { input ->
                ByteArray(64).also(input::read).toString(Charsets.ISO_8859_1)
            }
            assertFalse("MP3 must not contain an MP4 ftyp header", "ftyp" in headerText)
            assertTrue(Mp3FileValidator.isValid(destination))

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(destination.absolutePath)
                val format = (0 until extractor.trackCount)
                    .map(extractor::getTrackFormat)
                    .first { it.getString(MediaFormat.KEY_MIME) == "audio/mpeg" }
                assertEquals(44_100, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                assertEquals(2, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                assertTrue("Duration should remain close to two seconds", durationUs in 1_700_000L..2_300_000L)
            } finally {
                extractor.release()
            }
        } finally {
            source.delete()
            destination.delete()
            File(destination.parentFile, destination.name + ".transcoding.part").delete()
        }
    }

    @Test
    fun cancellationPreservesSourceAndRemovesPartialOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "tone-cancel-source.m4a")
        val destination = File(context.cacheDir, "tone-cancel-result.mp3")
        copyFixture(source)
        destination.delete()

        try {
            try {
                Mp3AudioTranscoder().transcode(
                    source = source,
                    destination = destination,
                    cancelled = AtomicBoolean(true),
                )
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                // Expected.
            }

            assertTrue("Source must survive cancellation", source.isFile)
            assertFalse(destination.exists())
            assertFalse(File(destination.parentFile, destination.name + ".transcoding.part").exists())
        } finally {
            source.delete()
            destination.delete()
            File(destination.parentFile, destination.name + ".transcoding.part").delete()
        }
    }

    private fun copyFixture(destination: File) {
        // tone-2s-aac.m4a SHA-256: 063d707040be2299775a40aee6845d33863155adaa72cff31b70ef0aa10da461
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        instrumentationContext.assets.open("audio/tone-2s-aac.m4a").use { input ->
            destination.outputStream().use(input::copyTo)
        }
    }
}
