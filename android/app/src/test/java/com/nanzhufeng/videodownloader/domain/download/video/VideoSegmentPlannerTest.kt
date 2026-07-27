package com.nanzhufeng.videodownloader.domain.download.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSegmentPlannerTest {
    @Test
    fun `plans requested independently playable ranges on sync frames`() {
        val durationUs = 12_000_000L
        val ranges = VideoSegmentPlanner.plan(
            durationUs = durationUs,
            segmentCount = 3,
            syncPointsUs = (0L..12L).map { it * 1_000_000L },
        )

        assertEquals(3, ranges.size)
        assertEquals(0L, ranges.first().startUs)
        assertEquals(durationUs, ranges.last().endUs)
        assertTrue(ranges.zipWithNext().all { (left, right) -> left.endUs == right.startUs })
        assertEquals(listOf(4_000_000L, 8_000_000L), ranges.dropLast(1).map { it.endUs })
    }

    @Test
    fun `rejects a count which cannot preserve safe independent segments`() {
        val error = runCatching {
            VideoSegmentPlanner.plan(
                durationUs = 3_000_000L,
                segmentCount = 4,
                syncPointsUs = listOf(0L, 2_000_000L),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("减少分段数量"))
    }
}
