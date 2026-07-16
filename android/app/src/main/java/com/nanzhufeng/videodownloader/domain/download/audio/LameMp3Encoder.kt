package com.nanzhufeng.videodownloader.domain.download.audio

import java.io.File
import java.io.IOException

class LameMp3Encoder : Mp3Encoder {
    override fun open(destination: File, format: PcmFormat): Mp3Encoder.Session {
        destination.parentFile?.mkdirs()
        val bitRateKbps = if (format.channelCount == 1) 128 else 192
        val handle = NativeLameBridge.open(
            path = destination.absolutePath,
            sampleRate = format.sampleRate,
            channels = format.channelCount,
            bitRateKbps = bitRateKbps,
        )
        if (handle == 0L) {
            throw IOException("无法初始化 LAME MP3 编码器")
        }
        return NativeSession(handle, format.channelCount)
    }

    private class NativeSession(
        private var handle: Long,
        private val channelCount: Int,
    ) : Mp3Encoder.Session {
        private var finished = false

        override fun encode(interleavedPcm: ShortArray, frames: Int) {
            check(handle != 0L) { "MP3 encoder is closed" }
            check(!finished) { "MP3 encoder is already finished" }
            require(frames >= 0) { "PCM frame count must not be negative" }
            require(frames.toLong() * channelCount <= interleavedPcm.size.toLong()) {
                "PCM buffer does not contain $frames complete frames"
            }
            if (frames == 0) return

            val encodedBytes = NativeLameBridge.encode(handle, interleavedPcm, frames)
            if (encodedBytes < 0) {
                throw IOException("LAME MP3 编码失败：$encodedBytes")
            }
        }

        override fun finish() {
            check(handle != 0L) { "MP3 encoder is closed" }
            if (finished) return
            val encodedBytes = NativeLameBridge.finish(handle)
            if (encodedBytes < 0) {
                throw IOException("LAME MP3 收尾失败：$encodedBytes")
            }
            finished = true
        }

        override fun close() {
            val currentHandle = handle
            if (currentHandle == 0L) return
            handle = 0L
            NativeLameBridge.close(currentHandle)
        }
    }
}
