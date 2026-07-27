package com.nanzhufeng.videodownloader.domain.download.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

data class Mp3ValidationResult(
    val valid: Boolean,
    val reason: String,
    val fileBytes: Long,
    val headerType: String,
    val mimeType: String? = null,
    val durationUs: Long? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
) {
    fun diagnosticSummary(): String = buildString {
        append(reason)
        append("；bytes=").append(fileBytes)
        append("；header=").append(headerType)
        mimeType?.let { append("；mime=").append(it) }
        durationUs?.let { append("；durationUs=").append(it) }
        sampleRate?.let { append("；sampleRate=").append(it) }
        channelCount?.let { append("；channels=").append(it) }
    }
}

class Mp3ValidationException(
    val validation: Mp3ValidationResult,
) : IOException("MP3 校验失败：${validation.diagnosticSummary()}")

object Mp3FileValidator {
    private const val MIN_MP3_BYTES = 1_024L
    private const val FRAME_SCAN_BYTES = 256 * 1024
    private const val REQUIRED_CONSECUTIVE_FRAMES = 3

    fun isValid(file: File): Boolean = inspect(file).valid

    fun inspect(file: File): Mp3ValidationResult {
        if (!file.isFile) {
            return Mp3ValidationResult(false, "输出文件不存在", 0L, "missing")
        }
        val fileBytes = file.length()
        if (fileBytes <= MIN_MP3_BYTES) {
            return Mp3ValidationResult(false, "输出文件过小", fileBytes, "too-small")
        }

        val frameEvidence = inspectFrames(file)
        val extractorEvidence = inspectWithMediaExtractor(file)
        if (frameEvidence.valid) {
            return frameEvidence.copy(
                mimeType = extractorEvidence.mimeType ?: "audio/mpeg",
                durationUs = extractorEvidence.durationUs,
                sampleRate = extractorEvidence.sampleRate ?: frameEvidence.sampleRate,
                channelCount = extractorEvidence.channelCount ?: frameEvidence.channelCount,
                reason = if (extractorEvidence.valid) {
                    "MP3 帧与系统媒体轨道校验通过"
                } else {
                    "MP3 连续帧校验通过；系统媒体轨道元数据不完整"
                },
            )
        }
        if (extractorEvidence.valid) {
            return extractorEvidence.copy(
                valid = true,
                reason = "系统媒体轨道校验通过",
                headerType = frameEvidence.headerType,
            )
        }
        return frameEvidence.copy(
            valid = false,
            reason = "${frameEvidence.reason}；${extractorEvidence.reason}",
            mimeType = extractorEvidence.mimeType,
            durationUs = extractorEvidence.durationUs,
            sampleRate = extractorEvidence.sampleRate,
            channelCount = extractorEvidence.channelCount,
        )
    }

    private fun inspectFrames(file: File): Mp3ValidationResult {
        val fileBytes = file.length()
        return try {
            RandomAccessFile(file, "r").use { input ->
                val firstTen = ByteArray(10)
                input.readFully(firstTen)
                val hasId3 = firstTen[0] == 'I'.code.toByte() &&
                    firstTen[1] == 'D'.code.toByte() &&
                    firstTen[2] == '3'.code.toByte()
                val audioStart = if (hasId3) {
                    val tagSize = synchsafeInt(firstTen, 6)
                    val footerBytes = if (firstTen[5].toInt() and 0x10 != 0) 10 else 0
                    (10L + tagSize + footerBytes).coerceAtMost(fileBytes)
                } else {
                    0L
                }
                input.seek(audioStart)
                val scanLength = minOf(FRAME_SCAN_BYTES.toLong(), fileBytes - audioStart)
                    .coerceAtLeast(0L)
                    .toInt()
                val bytes = ByteArray(scanLength)
                input.readFully(bytes)
                for (offset in 0 until (bytes.size - 4).coerceAtLeast(0)) {
                    val first = parseFrame(bytes, offset) ?: continue
                    var cursor = offset
                    var frames = 0
                    var current: MpegFrame? = first
                    while (current != null && frames < REQUIRED_CONSECUTIVE_FRAMES) {
                        frames += 1
                        cursor += current.frameBytes
                        current = if (frames < REQUIRED_CONSECUTIVE_FRAMES) {
                            parseFrame(bytes, cursor)
                        } else {
                            current
                        }
                    }
                    if (frames >= REQUIRED_CONSECUTIVE_FRAMES) {
                        return Mp3ValidationResult(
                            valid = true,
                            reason = "检测到 $frames 个连续 MPEG Audio Layer III 帧",
                            fileBytes = fileBytes,
                            headerType = if (hasId3) "ID3+MPEG" else "MPEG",
                            sampleRate = first.sampleRate,
                            channelCount = first.channelCount,
                        )
                    }
                }
                Mp3ValidationResult(
                    valid = false,
                    reason = "没有检测到连续的 MPEG Audio Layer III 帧",
                    fileBytes = fileBytes,
                    headerType = if (hasId3) "ID3-only" else "unknown",
                )
            }
        } catch (error: Throwable) {
            Mp3ValidationResult(
                valid = false,
                reason = "读取 MP3 帧失败：${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                fileBytes = fileBytes,
                headerType = "read-error",
            )
        }
    }

    private fun inspectWithMediaExtractor(file: File): Mp3ValidationResult {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val format = (0 until extractor.trackCount)
                .map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME) == "audio/mpeg" }
                ?: return Mp3ValidationResult(
                    false,
                    "系统媒体解析器没有发现 audio/mpeg 轨道",
                    file.length(),
                    "extractor-no-track",
                )
            val durationUs = format.longOrNull(MediaFormat.KEY_DURATION)
            val sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
            val valid = sampleRate?.let { it > 0 } != false &&
                channels?.let { it in 1..2 } != false
            Mp3ValidationResult(
                valid = valid,
                reason = if (valid) "系统媒体解析器识别到 MP3 音轨" else "系统媒体轨道参数无效",
                fileBytes = file.length(),
                headerType = "extractor",
                mimeType = "audio/mpeg",
                durationUs = durationUs,
                sampleRate = sampleRate,
                channelCount = channels,
            )
        } catch (error: Throwable) {
            Mp3ValidationResult(
                valid = false,
                reason = "系统媒体解析器读取失败：${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                fileBytes = file.length(),
                headerType = "extractor-error",
            )
        } finally {
            extractor.release()
        }
    }

    private fun parseFrame(bytes: ByteArray, offset: Int): MpegFrame? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        val header =
            ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
        if (header ushr 21 and 0x7ff != 0x7ff) return null
        val versionBits = header ushr 19 and 0x3
        val layerBits = header ushr 17 and 0x3
        val bitrateIndex = header ushr 12 and 0xf
        val sampleRateIndex = header ushr 10 and 0x3
        if (versionBits == 1 || layerBits != 1 || bitrateIndex !in 1..14 || sampleRateIndex == 3) {
            return null
        }
        val bitrateKbps = if (versionBits == 3) {
            MPEG1_LAYER3_BITRATES[bitrateIndex]
        } else {
            MPEG2_LAYER3_BITRATES[bitrateIndex]
        }
        val sampleRate = when (versionBits) {
            3 -> BASE_SAMPLE_RATES[sampleRateIndex]
            2 -> BASE_SAMPLE_RATES[sampleRateIndex] / 2
            0 -> BASE_SAMPLE_RATES[sampleRateIndex] / 4
            else -> return null
        }
        val padding = header ushr 9 and 0x1
        val coefficient = if (versionBits == 3) 144 else 72
        val frameBytes = coefficient * bitrateKbps * 1_000 / sampleRate + padding
        if (frameBytes <= 4) return null
        val channelMode = header ushr 6 and 0x3
        return MpegFrame(
            frameBytes = frameBytes,
            sampleRate = sampleRate,
            channelCount = if (channelMode == 3) 1 else 2,
        )
    }

    private fun synchsafeInt(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0x7f) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
            (bytes[offset + 3].toInt() and 0x7f)
    }

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private data class MpegFrame(
        val frameBytes: Int,
        val sampleRate: Int,
        val channelCount: Int,
    )

    private val BASE_SAMPLE_RATES = intArrayOf(44_100, 48_000, 32_000)
    private val MPEG1_LAYER3_BITRATES =
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val MPEG2_LAYER3_BITRATES =
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
}
