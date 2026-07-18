package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTransferPolicyTest {
    @Test
    fun youtubeAudioKeepsTheReusableSixSegmentPlan() {
        val audio = PlatformTransferPolicy.forAudio(DownloadPlatform.YOUTUBE)

        assertEquals(6, audio.maxConnections)
        assertEquals(8L * 1024L * 1024L, audio.segmentedThresholdBytes)
        assertEquals(null, audio.chunkSizeBytes)
    }

    @Test
    fun youtubeUsesMostConnectionsWhileDouyinKeepsConservativeThreshold() {
        val youtube = PlatformTransferPolicy.forPlatform(DownloadPlatform.YOUTUBE)
        val tiktok = PlatformTransferPolicy.forPlatform(DownloadPlatform.TIKTOK)
        val douyin = PlatformTransferPolicy.forPlatform(DownloadPlatform.DOUYIN)

        assertEquals(6, youtube.maxConnections)
        assertEquals(4, tiktok.maxConnections)
        assertEquals(3, douyin.maxConnections)
        assertTrue(douyin.segmentedThresholdBytes > youtube.segmentedThresholdBytes)
    }
}
