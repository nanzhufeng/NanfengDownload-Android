package com.nanzhufeng.videodownloader.probe

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileValidatorTest {
    @Test
    fun acceptsSmallId3PrefixedMp3() {
        val payload = ByteArray(2 * 1024).also {
            byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()).copyInto(it)
        }

        assertTrue(
            MediaFileValidator.isLikelyMedia(
                input = ByteArrayInputStream(payload),
                length = payload.size.toLong(),
            ),
        )
    }

    @Test
    fun acceptsSmallMpegAudioFrameSync() {
        val payload = ByteArray(2 * 1024).also {
            it[0] = 0xff.toByte()
            it[1] = 0xfb.toByte()
        }

        assertTrue(
            MediaFileValidator.isLikelyMedia(
                input = ByteArrayInputStream(payload),
                length = payload.size.toLong(),
            ),
        )
    }

    @Test
    fun acceptsJpegAboveTheImageMinimumSize() {
        val payload = ByteArray(2 * 1024).also {
            byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte()).copyInto(it)
        }

        assertTrue(
            MediaFileValidator.isLikelyMedia(
                input = ByteArrayInputStream(payload),
                length = payload.size.toLong(),
            ),
        )
    }

    @Test
    fun rejectsSmallFtypPayload() {
        val payload = ByteArray(2 * 1024).also {
            byteArrayOf(
                0,
                0,
                0,
                24,
                'f'.code.toByte(),
                't'.code.toByte(),
                'y'.code.toByte(),
                'p'.code.toByte(),
            ).copyInto(it)
        }

        assertFalse(
            MediaFileValidator.isLikelyMedia(
                input = ByteArrayInputStream(payload),
                length = payload.size.toLong(),
            ),
        )
    }

    @Test
    fun rejectsLargeTextErrorBodies() {
        listOf("<html>blocked</html>", "{\"error\":\"blocked\"}").forEach { prefix ->
            val payload = ByteArray(70 * 1024).also {
                prefix.toByteArray().copyInto(it)
            }

            assertFalse(
                MediaFileValidator.isLikelyMedia(
                    input = ByteArrayInputStream(payload),
                    length = payload.size.toLong(),
                ),
            )
        }
    }

    @Test
    fun rejectsTinyHtmlResponse() {
        val file = File.createTempFile("probe", ".mp4")
        try {
            file.writeText("<html>blocked</html>")
            assertFalse(MediaFileValidator.isLikelyMedia(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun acceptsMp4FtypHeaderAboveMinimumSize() {
        val file = File.createTempFile("probe", ".mp4")
        try {
            file.outputStream().use { output ->
                output.write(
                    byteArrayOf(
                        0,
                        0,
                        0,
                        24,
                        'f'.code.toByte(),
                        't'.code.toByte(),
                        'y'.code.toByte(),
                        'p'.code.toByte(),
                    ),
                )
                output.write(ByteArray(70 * 1024))
            }
            assertTrue(MediaFileValidator.isLikelyMedia(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun validatesMediaStoreStreamWithoutCopyingWholeFile() {
        val payload = ByteArray(70 * 1024).also {
            byteArrayOf(
                0,
                0,
                0,
                24,
                'f'.code.toByte(),
                't'.code.toByte(),
                'y'.code.toByte(),
                'p'.code.toByte(),
            ).copyInto(it)
        }

        assertTrue(
            MediaFileValidator.isLikelyMedia(
                input = ByteArrayInputStream(payload),
                length = payload.size.toLong(),
            ),
        )
    }
}
