package com.nanzhufeng.videodownloader.domain.download.video

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CompressedSampleBufferTest {
    @Test fun onlyTheEncodedFrameIsPassedWithoutChangingTheExtractorBuffer() {
        val buffer = ByteBuffer.wrap(byteArrayOf(9, 9, 1, 2, 3, 8))
        buffer.position(1)
        val sample = compressedSampleBuffer(buffer, 2, 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), ByteArray(sample.remaining()).also(sample::get))
        assertEquals(1, buffer.position())
        assertEquals(0, compressedSampleBuffer(buffer, 0, 0).remaining())
    }
    @Test fun outOfSampleBoundsCannotBeMuxed() {
        assertThrows(IllegalArgumentException::class.java) { compressedSampleBuffer(ByteBuffer.allocate(4), 3, 2) }
    }
}
