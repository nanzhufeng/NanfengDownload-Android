package com.nanzhufeng.videodownloader.domain.download.video

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AacAdtsSampleLayoutTest {
    @Test
    fun stripsSevenByteAdtsHeaderFromAacBeforeMp4Muxing() {
        val sample = adtsSample(byteArrayOf(1, 2, 3, 4), protectionAbsent = true)

        val layout = AacAdtsSampleLayout.forSample("audio/mp4a-latm", ByteBuffer.wrap(sample), sample.size).single()

        assertEquals(7, layout.offset)
        assertEquals(4, layout.size)
        assertEquals(1, sample[layout.offset].toInt())
    }

    @Test
    fun stripsNineByteCrcAdtsHeader() {
        val sample = adtsSample(byteArrayOf(5, 6), protectionAbsent = false)

        val layout = AacAdtsSampleLayout.forSample("audio/mp4a-latm", ByteBuffer.wrap(sample), sample.size).single()

        assertEquals(9, layout.offset)
        assertEquals(2, layout.size)
    }

    @Test
    fun acceptsCompleteConcatenatedAdtsFramesInOneExtractorSample() {
        val sample = adtsSample(byteArrayOf(1, 2), protectionAbsent = true) +
            adtsSample(byteArrayOf(3, 4, 5), protectionAbsent = true)

        val layouts = AacAdtsSampleLayout.forSample("audio/mp4a-latm", ByteBuffer.wrap(sample), sample.size)

        assertEquals(listOf(SampleLayout(7, 2), SampleLayout(16, 3)), layouts)
    }

    @Test
    fun leavesRawAacAndOtherTracksUntouched() {
        val raw = byteArrayOf(1, 2, 3)
        val adts = adtsSample(raw, protectionAbsent = true)

        assertEquals(listOf(SampleLayout(0, raw.size)), AacAdtsSampleLayout.forSample("audio/mp4a-latm", ByteBuffer.wrap(raw), raw.size))
        assertEquals(listOf(SampleLayout(0, adts.size)), AacAdtsSampleLayout.forSample("video/avc", ByteBuffer.wrap(adts), adts.size))
    }

    @Test
    fun rejectsTruncatedAdtsFrameInsteadOfPublishingMalformedMp4() {
        val sample = adtsSample(byteArrayOf(1, 2, 3, 4), protectionAbsent = true)

        assertThrows(IllegalArgumentException::class.java) {
            AacAdtsSampleLayout.forSample("audio/mp4a-latm", ByteBuffer.wrap(sample), sample.size - 1)
        }
    }

    private fun adtsSample(payload: ByteArray, protectionAbsent: Boolean): ByteArray {
        val headerSize = if (protectionAbsent) 7 else 9
        val frameLength = headerSize + payload.size
        val header = byteArrayOf(
            0xff.toByte(),
            (if (protectionAbsent) 0xf1 else 0xf0).toByte(),
            0x4c,
            (0x80 or ((frameLength shr 11) and 0x03)).toByte(),
            ((frameLength shr 3) and 0xff).toByte(),
            (((frameLength and 0x07) shl 5) or 0x1f).toByte(),
            0xfc.toByte(),
        )
        return if (protectionAbsent) header + payload else header + byteArrayOf(0, 0) + payload
    }
}
