package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTransferPolicyTest {
    @Test fun fragmentsUseTheBudgetWithoutChangingDirectFilePolicies() {
        assertEquals(6, PlatformTransferPolicy.forFragments(DownloadPlatform.GENERIC).maxConnections)
        assertEquals(6, PlatformTransferPolicy.forFragments(DownloadPlatform.PORNHUB).maxConnections)
        assertEquals(6, PlatformTransferPolicy.forPlatform(DownloadPlatform.GENERIC).maxConnections)
        assertTrue(!PlatformTransferPolicy.forPlatform(DownloadPlatform.DOUYIN).preferSingleConnection)
    }
    @Test
    fun youtubeAudioUsesSequentialNativeRangeChunksToAvoidMultiConnectionStarvation() {
        val audio = PlatformTransferPolicy.forAudio(DownloadPlatform.YOUTUBE)

        assertEquals(1, audio.maxConnections)
        assertEquals(8L * 1024L * 1024L, audio.segmentedThresholdBytes)
        assertEquals(1L * 1024L * 1024L, audio.chunkSizeBytes)
    }

    @Test
    fun youtubeVideoFallbackForAudioKeepsFastVideoSegmentPlan() {
        val fallback = PlatformTransferPolicy.forAudioSource(
            DownloadPlatform.YOUTUBE,
            fromVideoSource = true,
        )

        assertEquals(6, fallback.maxConnections)
        assertEquals(4L * 1024L * 1024L, fallback.chunkSizeBytes)
    }

    @Test
    fun shortVideoUsesTheBoundedChunkPlanWithoutWaitingForSlowRecovery() {
        val youtube = PlatformTransferPolicy.forPlatform(DownloadPlatform.YOUTUBE)
        val tiktok = PlatformTransferPolicy.forPlatform(DownloadPlatform.TIKTOK)
        val douyin = PlatformTransferPolicy.forPlatform(DownloadPlatform.DOUYIN)

        assertEquals(6, youtube.maxConnections)
        assertEquals(6, tiktok.maxConnections)
        assertEquals(6, douyin.maxConnections)
        assertTrue(!douyin.preferSingleConnection)
        assertEquals(4L * 1024L * 1024L, douyin.chunkSizeBytes)
        assertEquals(2L * 1024L * 1024L, douyin.segmentedThresholdBytes)
    }

    @Test
    fun bilibiliAndXiaohongshuUsePlatformAwareRangePlans() {
        val bilibili = PlatformTransferPolicy.forPlatform(DownloadPlatform.BILIBILI)
        val xiaohongshu = PlatformTransferPolicy.forPlatform(DownloadPlatform.XIAOHONGSHU)

        assertEquals(6, bilibili.maxConnections)
        assertEquals(6, xiaohongshu.maxConnections)
        assertEquals(2L * 1024L * 1024L, bilibili.segmentedThresholdBytes)
        assertEquals(2L * 1024L * 1024L, xiaohongshu.segmentedThresholdBytes)
    }
}
