package com.nanzhufeng.videodownloader.domain.download.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

data class AudioSourceValidationResult(
    val valid: Boolean,
    val reason: String,
    val mimeType: String? = null,
    val durationUs: Long? = null,
)

object AudioSourceFileValidator {
    // A real track-level MediaExtractor check is stronger than an arbitrary large-size gate.
    // Keep only a tiny corruption guard so valid short clips remain reusable and segmentable.
    private const val MIN_SOURCE_BYTES = 1_024L

    fun inspect(file: File): AudioSourceValidationResult {
        if (!file.isFile) return AudioSourceValidationResult(false, "缓存源文件不存在")
        if (file.length() < MIN_SOURCE_BYTES) {
            return AudioSourceValidationResult(false, "缓存源文件过小：${file.length()} 字节")
        }

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val audioFormat = (0 until extractor.trackCount)
                .map(extractor::getTrackFormat)
                .firstOrNull { format ->
                    format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                ?: return AudioSourceValidationResult(false, "缓存源文件没有可解码音轨")
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)
            val durationUs = audioFormat.longOrNull(MediaFormat.KEY_DURATION)
            val sampleRate = audioFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
            val channels = audioFormat.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
            when {
                sampleRate != null && sampleRate <= 0 ->
                    AudioSourceValidationResult(false, "缓存音轨采样率无效：$sampleRate", mime, durationUs)
                channels != null && channels !in 1..8 ->
                    AudioSourceValidationResult(false, "缓存音轨声道数无效：$channels", mime, durationUs)
                else -> AudioSourceValidationResult(
                    valid = true,
                    reason = "已确认缓存包含可解码音轨",
                    mimeType = mime,
                    durationUs = durationUs,
                )
            }
        } catch (error: Throwable) {
            AudioSourceValidationResult(
                valid = false,
                reason = "缓存源文件无法读取：${error.javaClass.simpleName}: ${error.message.orEmpty()}",
            )
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
}
