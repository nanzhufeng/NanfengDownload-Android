package com.nanzhufeng.videodownloader.domain.download.video

import java.nio.ByteBuffer

/** A muxer gets exactly one encoded frame; ADTS headers and adjacent frames stay outside it. */
internal fun compressedSampleBuffer(buffer: ByteBuffer, offset: Int, size: Int): ByteBuffer {
    require(offset >= 0 && size >= 0 && offset.toLong() + size <= buffer.limit())
    return buffer.duplicate().apply {
        position(offset)
        limit(offset + size)
    }.slice()
}
