package com.nanzhufeng.videodownloader.domain.download.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LameMp3EncoderInstrumentedTest {
    @Test
    fun encodesStereoPcmAsRecognizableMp3() {
        encodeAndVerify(sampleRate = 44_100, channelCount = 2)
    }

    @Test
    fun encodesMonoPcmAsRecognizableMp3() {
        encodeAndVerify(sampleRate = 48_000, channelCount = 1)
    }

    private fun encodeAndVerify(sampleRate: Int, channelCount: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "lame-$sampleRate-$channelCount.mp3")
        output.delete()

        try {
            LameMp3Encoder().open(
                destination = output,
                format = PcmFormat(sampleRate = sampleRate, channelCount = channelCount),
            ).use { session ->
                val totalFrames = sampleRate * 2
                var firstFrame = 0
                while (firstFrame < totalFrames) {
                    val frameCount = minOf(1_152, totalFrames - firstFrame)
                    val pcm = ShortArray(frameCount * channelCount)
                    repeat(frameCount) { localFrame ->
                        val absoluteFrame = firstFrame + localFrame
                        val sample = (
                            sin(2.0 * PI * 440.0 * absoluteFrame / sampleRate) *
                                Short.MAX_VALUE * 0.25
                            ).toInt().toShort()
                        repeat(channelCount) { channel ->
                            pcm[localFrame * channelCount + channel] = sample
                        }
                    }
                    session.encode(pcm, frameCount)
                    firstFrame += frameCount
                }
                session.finish()
            }

            assertTrue("MP3 output should exceed 1 KiB", output.length() > 1_024)
            val header = output.inputStream().use { input -> ByteArray(3).also(input::read) }
            val startsWithId3 = header.contentEquals(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
            val startsWithFrameSync =
                header[0].toInt() and 0xff == 0xff && header[1].toInt() and 0xe0 == 0xe0
            assertTrue("Output must start with ID3 or MPEG frame sync", startsWithId3 || startsWithFrameSync)

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val audioFormat = (0 until extractor.trackCount)
                    .map(extractor::getTrackFormat)
                    .first { it.getString(MediaFormat.KEY_MIME) == "audio/mpeg" }
                assertEquals(sampleRate, audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                assertEquals(channelCount, audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                assertTrue(audioFormat.getLong(MediaFormat.KEY_DURATION) > 0L)
            } finally {
                extractor.release()
            }
        } finally {
            output.delete()
        }
    }
}
