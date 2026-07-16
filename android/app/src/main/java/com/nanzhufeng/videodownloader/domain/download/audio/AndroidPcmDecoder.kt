package com.nanzhufeng.videodownloader.domain.download.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class AndroidPcmDecoder {
    fun decode(
        source: File,
        cancelled: AtomicBoolean,
        onFormat: (PcmFormat) -> Unit,
        onPcm: (samples: ShortArray, frames: Int) -> Unit,
    ) {
        if (cancelled.get()) throw CancellationException("音频转码已取消")

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var decoderStarted = false
        try {
            extractor.setDataSource(source.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw IOException("源文件中没有可解码的音频轨道")
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("音频轨道缺少 MIME 类型")

            extractor.selectTrack(trackIndex)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decoderStarted = true

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var pcmFormat: PcmFormat? = null

            fun updateOutputFormat(format: MediaFormat) {
                val pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }
                if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                    throw IOException("设备解码器输出的不是 PCM 16-bit：$pcmEncoding")
                }
                val next = PcmFormat(
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                )
                val previous = pcmFormat
                if (previous == null) {
                    pcmFormat = next
                    onFormat(next)
                } else if (previous != next) {
                    throw IOException("音频解码格式在流中发生变化：$previous -> $next")
                }
            }

            while (!outputEnded) {
                if (cancelled.get()) throw CancellationException("音频转码已取消")

                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: throw IOException("设备解码器没有提供输入缓冲区")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> updateOutputFormat(decoder.outputFormat)
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        try {
                            val isCodecConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (bufferInfo.size > 0 && !isCodecConfig) {
                                if (pcmFormat == null) updateOutputFormat(decoder.outputFormat)
                                val activeFormat = pcmFormat
                                    ?: throw IOException("设备解码器没有输出 PCM 格式")
                                if (bufferInfo.size % Short.SIZE_BYTES != 0) {
                                    throw IOException("PCM 缓冲区不是完整的 16-bit 采样")
                                }
                                val outputBuffer = decoder.getOutputBuffer(outputIndex)
                                    ?: throw IOException("设备解码器没有提供输出缓冲区")
                                val end = bufferInfo.offset + bufferInfo.size
                                if (bufferInfo.offset < 0 || end > outputBuffer.capacity()) {
                                    throw IOException("设备解码器返回了越界的 PCM 缓冲区")
                                }
                                val pcmBytes = outputBuffer.duplicate().apply {
                                    position(bufferInfo.offset)
                                    limit(end)
                                    order(ByteOrder.nativeOrder())
                                }
                                val samples = ShortArray(bufferInfo.size / Short.SIZE_BYTES)
                                pcmBytes.asShortBuffer().get(samples)
                                if (samples.size % activeFormat.channelCount != 0) {
                                    throw IOException("PCM 缓冲区末尾含有不完整的声道帧")
                                }
                                onPcm(samples, samples.size / activeFormat.channelCount)
                            }
                        } finally {
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
        } finally {
            if (decoderStarted) runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
    }
}
