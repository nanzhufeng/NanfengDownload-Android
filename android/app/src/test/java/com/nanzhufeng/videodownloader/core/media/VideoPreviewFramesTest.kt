package com.nanzhufeng.videodownloader.core.media

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class VideoPreviewFramesTest {
    @Test fun blackOpeningDoesNotBecomeThePreview() {
        val requested = mutableListOf<Long>()
        val discarded = mutableListOf<String>()
        val result = VideoPreviewFrames.select(
            60_000,
            frameAt = { requested += it; if (it < 5_000) "black" else "scene" },
            isUsable = { it == "scene" },
            discard = { discarded += it },
        )
        assertEquals("scene", result)
        assertEquals(listOf(750L, 2_000L, 5_000L), requested)
        assertEquals(listOf("black", "black"), discarded)
    }

    @Test fun longBlackIntroCanRecoverFromTheMiddle() {
        val calls = mutableListOf<Long>()
        val result = VideoPreviewFrames.select(120_000,
            frameAt = { calls += it; it }, isUsable = { it >= 60_000 }, discard = {})
        assertEquals(60_000L, result)
        assertEquals(listOf(750L, 2_000L, 5_000L, 12_000L, 30_000L, 60_000L), calls)
    }

    @Test fun allBlackFramesReturnNoPreviewForArtworkFallback() {
        val discarded = mutableListOf<Long>()
        val result = VideoPreviewFrames.select(10_000, frameAt = { it },
            isUsable = { false }, discard = { discarded += it })
        assertNull(result)
        assertTrue(discarded.size <= 8)
        assertEquals(discarded.size, discarded.distinct().size)
    }

    @Test fun subsecondVideoStaysWithinDurationAndCanRecoverItsFirstFrame() {
        val calls = mutableListOf<Long>()
        val result = VideoPreviewFrames.select(400, frameAt = { calls += it; it },
            isUsable = { it == 0L }, discard = {})
        assertEquals(0L, result)
        assertTrue(calls.all { it in 0L..399L })
        assertEquals(calls.size, calls.distinct().size)
    }

    @Test fun missingDurationAndUndecodableFramesRemainBounded() {
        val calls = mutableListOf<Long>()
        val result = VideoPreviewFrames.select<String>(null, frameAt = { calls += it; null },
            isUsable = { true }, discard = {})
        assertNull(result)
        assertTrue(calls.size <= 8)
        assertTrue(calls.all { it in 0L..30_000L })
    }

    @Test fun cancellationDiscardsTheInFlightBitmapAndStopsSeeking() {
        val discarded = mutableListOf<String>()
        var checks = 0
        try {
            VideoPreviewFrames.select(10_000, frameAt = { "frame" },
                isUsable = { true }, discard = { discarded += it },
                checkActive = { if (++checks == 2) throw CancellationException() })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf("frame"), discarded)
        }
    }

    @Test fun blackCompressionNoiseIsRejectedButDarkScenesAndLetterboxingAreKept() {
        assertFalse(VideoPreviewFrames.hasVisibleContent(IntArray(100) { 0xff000000.toInt() }))
        assertFalse(VideoPreviewFrames.hasVisibleContent(IntArray(100) { 0xff080808.toInt() }))
        assertTrue(VideoPreviewFrames.hasVisibleContent(IntArray(100) {
            if (it < 10) 0xff202020.toInt() else 0xff020202.toInt()
        }))
        assertTrue(VideoPreviewFrames.hasVisibleContent(IntArray(100) {
            if (it in 30..69) 0xff7799aa.toInt() else 0xff000000.toInt()
        }))
        assertTrue(VideoPreviewFrames.hasVisibleContent(IntArray(100) { 0xffffffff.toInt() }))
    }
}
