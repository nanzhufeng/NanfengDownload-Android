package com.nanzhufeng.videodownloader.domain.download.video

import java.nio.ByteBuffer

internal data class SampleLayout(val offset: Int, val size: Int)

/** AAC frames from some HLS/TS extractors retain ADTS headers, which are invalid inside MP4 samples. */
internal object AacAdtsSampleLayout {
    fun forSample(mime: String, buffer: ByteBuffer, sampleSize: Int): List<SampleLayout> {
        require(sampleSize in 0..buffer.capacity()) { "媒体帧大小无效" }
        if (mime != "audio/mp4a-latm" || sampleSize < 2) return listOf(SampleLayout(0, sampleSize))
        val first = buffer.get(0).toInt() and 0xff
        val second = buffer.get(1).toInt() and 0xff
        if (first != 0xff || (second and 0xf6) != 0xf0) return listOf(SampleLayout(0, sampleSize))

        val layouts = mutableListOf<SampleLayout>()
        var cursor = 0
        while (cursor < sampleSize) {
            require(cursor + 7 <= sampleSize) { "AAC ADTS 帧不完整" }
            val sync = buffer.get(cursor).toInt() and 0xff
            val flags = buffer.get(cursor + 1).toInt() and 0xff
            require(sync == 0xff && (flags and 0xf6) == 0xf0) { "AAC ADTS 帧边界无效" }
            val headerSize = if ((flags and 0x01) == 0) 9 else 7
            require(cursor + headerSize < sampleSize) { "AAC ADTS 帧不完整" }
            val frameLength = ((buffer.get(cursor + 3).toInt() and 0x03) shl 11) or
                ((buffer.get(cursor + 4).toInt() and 0xff) shl 3) or
                ((buffer.get(cursor + 5).toInt() and 0xe0) shr 5)
            require(frameLength > headerSize && cursor + frameLength <= sampleSize) {
                "AAC ADTS 帧长度不匹配"
            }
            require((buffer.get(cursor + 6).toInt() and 0x03) == 0) {
                "暂不支持一个 ADTS 帧内的多个 AAC 原始数据块"
            }
            layouts += SampleLayout(cursor + headerSize, frameLength - headerSize)
            cursor += frameLength
        }
        // An extractor sample can contain several ADTS frames. Each AAC payload must become
        // its own MP4 sample; otherwise later headers remain embedded in the audio stream.
        return layouts
    }
}
