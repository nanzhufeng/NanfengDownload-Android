package com.nanzhufeng.videodownloader.domain.download.audio

data class PcmFormat(
    val sampleRate: Int,
    val channelCount: Int,
) {
    init {
        require(sampleRate > 0) { "PCM sample rate must be positive" }
        require(channelCount in 1..2) { "Only mono and stereo PCM are supported" }
    }
}
