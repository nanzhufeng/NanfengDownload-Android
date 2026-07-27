package com.nanzhufeng.videodownloader.domain.download.audio

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

interface AudioTranscoder {
    fun transcode(
        source: File,
        destination: File,
        cancelled: AtomicBoolean,
        onProgress: (progressPercent: Int) -> Unit = {},
    ): File

    fun transcodeSegments(
        source: File,
        destinations: List<File>,
        cancelled: AtomicBoolean,
        onProgress: (progressPercent: Int) -> Unit = {},
    ): List<File> {
        require(destinations.size == 1) {
            "当前音频转码器不支持多段输出"
        }
        return listOf(transcode(source, destinations.single(), cancelled, onProgress))
    }
}
