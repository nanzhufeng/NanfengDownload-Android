package com.nanzhufeng.videodownloader.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoContainerPolicyTest {
    @Test
    fun completeWebmIsKeptAsWebmInsteadOfBeingRelabelledMp4() {
        assertFalse(VideoContainerPolicy.requiresDirectMp4Remux("webm"))
        assertEquals("video/webm", VideoContainerPolicy.outputMime("webm", false, false))
        assertEquals("video/mp4", VideoContainerPolicy.outputMime("webm", true, false))
    }

    @Test
    fun fragmentsStillNeedMp4RemuxAndWebmCannotBeSplitAsMp4() {
        assertTrue(VideoContainerPolicy.requiresDirectMp4Remux("ts"))
        assertFalse(VideoContainerPolicy.requiresDirectMp4Remux("mp4"))
        assertThrows(IllegalArgumentException::class.java) {
            VideoContainerPolicy.outputMime("webm", false, true)
        }
    }
}
