package com.nanzhufeng.videodownloader.domain.download.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoResolutionTranscoderTest {
    @Test
    fun onlyHigherSourceRenditionsRequireThe360pTranscode() {
        assertFalse(needs360pTranscode(360, "UP_TO_360P"))
        assertTrue(needs360pTranscode(540, "UP_TO_360P"))
        assertTrue(needs360pTranscode(720, "UP_TO_360P"))
        assertFalse(needs360pTranscode(720, "UP_TO_720P"))
    }
}
