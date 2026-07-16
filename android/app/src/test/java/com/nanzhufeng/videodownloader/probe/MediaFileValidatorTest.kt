package com.nanzhufeng.videodownloader.probe

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileValidatorTest {
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
