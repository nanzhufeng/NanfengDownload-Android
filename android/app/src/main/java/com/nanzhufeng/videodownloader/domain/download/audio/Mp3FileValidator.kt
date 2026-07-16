package com.nanzhufeng.videodownloader.domain.download.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

object Mp3FileValidator {
    fun isValid(file: File): Boolean {
        if (!file.isFile || file.length() <= 1_024L) return false
        val header = file.inputStream().use { input -> ByteArray(3).also(input::read) }
        val hasId3 = header.contentEquals(
            byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()),
        )
        val hasFrameSync =
            header[0].toInt() and 0xff == 0xff &&
                header[1].toInt() and 0xe0 == 0xe0
        if (!hasId3 && !hasFrameSync) return false

        return runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                (0 until extractor.trackCount).any { index ->
                    val format = extractor.getTrackFormat(index)
                    format.getString(MediaFormat.KEY_MIME) == "audio/mpeg" &&
                        format.containsKey(MediaFormat.KEY_DURATION) &&
                        format.getLong(MediaFormat.KEY_DURATION) > 0L &&
                        format.containsKey(MediaFormat.KEY_SAMPLE_RATE) &&
                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE) > 0 &&
                        format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) &&
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) in 1..2
                }
            } finally {
                extractor.release()
            }
        }.getOrDefault(false)
    }
}
