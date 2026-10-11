package com.nanzhufeng.videodownloader.domain.download.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackEndTimeTest {
    @Test fun reorderedFramesEndAfterMaximumPresentationTime() {
        val clock = TrackEndTime(33_333)
        listOf(0L, 100_000, 300_000, 200_000, 500_000, 400_000).forEach(clock::record)
        assertEquals(600_000L, clock.endUs())
    }
    @Test fun singleSampleUsesItsTrackPeriodAndDuplicateTimesDoNotShrinkIt() {
        val clock = TrackEndTime(23_219)
        clock.record(100_000); clock.record(100_000)
        assertEquals(123_219L, clock.endUs())
    }
}
