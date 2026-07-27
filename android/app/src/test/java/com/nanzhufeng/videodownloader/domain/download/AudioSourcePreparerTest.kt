package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.domain.download.audio.AudioTranscoder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioSourcePreparerTest {
    private lateinit var directory: File
    private lateinit var source: File
    private lateinit var destination: File
    private val validMp3Paths = mutableSetOf<String>()

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("audio-source-preparer").toFile()
        source = File(directory, "source.m4a").apply { writeBytes(ByteArray(2_048)) }
        destination = File(directory, "result.mp3")
        validMp3Paths.clear()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun passesThroughValidatorConfirmedMp3() {
        validMp3Paths += source.absolutePath
        val transcoder = FakeAudioTranscoder { _, _, _ -> error("must not transcode") }

        val prepared = preparer(transcoder).prepare(source, destination, AtomicBoolean(false))

        assertSame(source, prepared.file)
        assertEquals("audio/mpeg", prepared.mimeType)
        assertEquals(0, transcoder.calls)
    }

    @Test
    fun transcodesNonMp3SourceAndReturnsOnlyValidatedDestination() {
        val transcoder = FakeAudioTranscoder { _, target, _ ->
            target.writeBytes(ByteArray(2_048))
            validMp3Paths += target.absolutePath
            target
        }

        val prepared = preparer(transcoder).prepare(source, destination, AtomicBoolean(false))

        assertSame(destination, prepared.file)
        assertEquals("audio/mpeg", prepared.mimeType)
        assertEquals(1, transcoder.calls)
    }

    @Test
    fun reusesAlreadyValidatedDestination() {
        destination.writeBytes(ByteArray(2_048))
        validMp3Paths += destination.absolutePath
        val transcoder = FakeAudioTranscoder { _, _, _ -> error("must not transcode") }

        val prepared = preparer(transcoder).prepare(source, destination, AtomicBoolean(false))

        assertSame(destination, prepared.file)
        assertEquals(0, transcoder.calls)
    }

    @Test
    fun rejectsAndDeletesInvalidTranscoderOutput() {
        val transcoder = FakeAudioTranscoder { _, target, _ ->
            target.writeText("not an mp3")
            target
        }

        try {
            preparer(transcoder).prepare(source, destination, AtomicBoolean(false))
            throw AssertionError("Expected invalid MP3 failure")
        } catch (_: IOException) {
            // Expected.
        }

        assertFalse(destination.exists())
    }

    @Test(expected = CancellationException::class)
    fun propagatesCancellationWithoutPublishingMp3() {
        val transcoder = FakeAudioTranscoder { _, _, _ ->
            throw CancellationException("cancelled")
        }

        try {
            preparer(transcoder).prepare(source, destination, AtomicBoolean(true))
        } finally {
            assertTrue(source.exists())
            assertFalse(destination.exists())
        }
    }

    private fun preparer(transcoder: AudioTranscoder) = AudioSourcePreparer(
        transcoder = transcoder,
        isValidMp3 = { it.absolutePath in validMp3Paths },
    )

    private class FakeAudioTranscoder(
        private val action: (File, File, AtomicBoolean) -> File,
    ) : AudioTranscoder {
        var calls = 0

        override fun transcode(
            source: File,
            destination: File,
            cancelled: AtomicBoolean,
            onProgress: (progressPercent: Int) -> Unit,
        ): File {
            calls += 1
            return action(source, destination, cancelled)
        }
    }
}
