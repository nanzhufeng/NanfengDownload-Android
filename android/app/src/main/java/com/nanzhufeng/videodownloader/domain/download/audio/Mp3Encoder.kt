package com.nanzhufeng.videodownloader.domain.download.audio

import java.io.Closeable
import java.io.File

interface Mp3Encoder {
    fun open(destination: File, format: PcmFormat): Session

    interface Session : Closeable {
        fun encode(interleavedPcm: ShortArray, frames: Int)

        fun finish()
    }
}
