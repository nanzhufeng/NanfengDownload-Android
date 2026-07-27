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
        onProgress: (progressPercent: Int) -> Unit = {},
    ): PreparedMedia = prepareSegments(
        source = source,
        destinations = listOf(destination),
        cancelled = cancelled,
        onProgress = onProgress,
    )

    fun prepareSegments(
        source: File,
        destinations: List<File>,
        cancelled: AtomicBoolean,
        onProgress: (progressPercent: Int) -> Unit = {},
    ): PreparedMedia {
        if (cancelled.get()) throw CancellationException("音频转码已取消")
        require(destinations.isNotEmpty()) { "至少需要一个 MP3 输出路径" }
        if (destinations.size == 1 && isValidMp3(source)) {
            return PreparedMedia(source, "audio/mpeg")
        }
        if (destinations.all(isValidMp3)) {
            return PreparedMedia(destinations.first(), "audio/mpeg", destinations.drop(1))
        }
        destinations.forEach { destination ->
            if (destination.exists() && !destination.delete()) {
                throw IOException("无法清理无效 MP3 缓存：${destination.absolutePath}")
            }
        }

        try {
            val transcoded = transcoder.transcodeSegments(
                source,
                destinations,
                cancelled,
                onProgress,
            )
            val actualPaths = transcoded.map { it.canonicalFile }
            val expectedPaths = destinations.map { it.canonicalFile }
            if (actualPaths != expectedPaths) {
                throw IOException("音频转码器返回了意外的分段输出路径")
            }
            val invalid = destinations.firstOrNull { !isValidMp3(it) }
            if (invalid != null) {
                destinations.forEach(File::delete)
                throw IOException("音频转码结果包含无效 MP3 分段：${invalid.name}")
            }
            return PreparedMedia(destinations.first(), "audio/mpeg", destinations.drop(1))
        } catch (error: Throwable) {
            destinations.forEach { destination ->
                if (destination.exists() && !isValidMp3(destination)) {
                    destination.delete()
                }
            }
            throw error
        }
    }
}
