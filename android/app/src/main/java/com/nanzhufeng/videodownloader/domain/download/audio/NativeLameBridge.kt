package com.nanzhufeng.videodownloader.domain.download.audio

internal object NativeLameBridge {
    init {
        System.loadLibrary("nanzhufeng_mp3")
    }

    external fun open(
        path: String,
        sampleRate: Int,
        channels: Int,
        bitRateKbps: Int,
    ): Long

    external fun encode(handle: Long, pcm: ShortArray, frames: Int): Int

    external fun finish(handle: Long): Int

    external fun close(handle: Long)
}
