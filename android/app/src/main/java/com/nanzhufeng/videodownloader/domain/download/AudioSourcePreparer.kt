package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.domain.download.audio.AudioTranscoder
import com.nanzhufeng.videodownloader.domain.download.audio.Mp3FileValidator
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class AudioSourcePreparer(
    private val transcoder: AudioTranscoder,
    private val isValidMp3: (File) -> Boolean = Mp3FileValidator::isValid,
) {
    fun prepare(
        source: File,
        destination: File,
        cancelled: AtomicBoolean,
    ): PreparedMedia {
        if (cancelled.get()) throw CancellationException("音频转码已取消")
        if (isValidMp3(source)) return PreparedMedia(source, "audio/mpeg")
        if (isValidMp3(destination)) return PreparedMedia(destination, "audio/mpeg")
        if (destination.exists() && !destination.delete()) {
            throw IOException("无法清理无效 MP3 缓存：${destination.absolutePath}")
        }

        try {
            val transcoded = transcoder.transcode(source, destination, cancelled)
            if (transcoded.canonicalFile != destination.canonicalFile) {
                throw IOException("音频转码器返回了意外的输出路径")
            }
            if (!isValidMp3(destination)) {
                destination.delete()
                throw IOException("音频转码结果不是有效 MP3")
            }
            return PreparedMedia(destination, "audio/mpeg")
        } catch (error: Throwable) {
            if (destination.exists() && !isValidMp3(destination)) {
                destination.delete()
            }
            throw error
        }
    }
}
