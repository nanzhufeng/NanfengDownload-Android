package com.nanzhufeng.videodownloader.domain.download.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AacSampleTimelineTest {
    @Test fun clampedFirstPesRetainsAudioLeadAndAllFrames() {
        val lead = initialAacLeadUs(0, 215_422, 11, 44_100)
        assertEquals(39_997L, lead)
        val clock = AacSampleTimeline(44_100, lead)
        val first = clock.times(0, 11)
        val next = clock.times(215_422, 14)
        assertEquals(0L, first.first())
        assertEquals(232_199L, first.last())
        assertEquals(255_419L, next.first())
        assertTrue(next.first() > first.last())
        assertEquals(25, (first + next).size)
    }
    @Test fun separatelyClampedInitialFramesPreserveLeadWithoutAddingSilence() {
        // Target TS: 48 kHz AAC begins 68.722 ms before the first video PTS.
        // Android reports four distinct leading AAC frames at zero, then 16.611 ms.
        val lead = initialAacLeadUs(0, 16_611, 4, 48_000)
        assertEquals(68_722L, lead)
        val clock = AacSampleTimeline(48_000, lead)
        assertEquals(listOf(0L, 21_333L, 42_666L, 63_999L), List(4) { clock.times(0, 1).single() })
        assertEquals(85_333L, clock.times(16_611, 1).single())
    }

    @Test fun unclampedAacRetainsRealGap() {
        assertEquals(0L, initialAacLeadUs(10_000, 270_000, 11, 44_100))
        val clock = AacSampleTimeline(44_100, 0)
        clock.times(0, 1)
        assertEquals(500_000L, clock.times(500_000, 1).single())
    }
    @Test fun oneFrameOverlapAtDiscontinuityPreservesTheSample() {
        val clock = AacSampleTimeline(44_100, 0)
        clock.times(0, 2)
        assertEquals(46_439L, clock.times(40_000, 1).single())
    }
    @Test fun undeclaredLargeRewindFailsInsteadOfCollapsingTheMediaTimeline() {
        val clock = AacSampleTimeline(44_100, 0)
        clock.times(1_000_000, 2)
        assertThrows(IllegalArgumentException::class.java) { clock.times(0, 1) }
    }
    @Test fun twoFramesOfEncoderPaddingAtHlsJoinKeepBothAudioSamples() {
        val clock = AacSampleTimeline(44_100, 39_997)
        clock.times(1_212_262_269, 1)
        assertEquals(1_212_285_488L, clock.times(1_212_200_000, 1).single())
    }
}
