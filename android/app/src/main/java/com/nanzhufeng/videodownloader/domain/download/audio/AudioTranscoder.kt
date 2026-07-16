package com.nanzhufeng.videodownloader.domain.download.audio

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

interface AudioTranscoder {
    fun transcode(
        source: File,
        destination: File,
        cancelled: AtomicBoolean,
    ): File
}
