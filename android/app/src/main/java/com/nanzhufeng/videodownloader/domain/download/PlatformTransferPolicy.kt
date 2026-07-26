package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.probe.TransferPolicy

object PlatformTransferPolicy {
    fun forPlatform(platform: DownloadPlatform): TransferPolicy = when (platform) {
        DownloadPlatform.YOUTUBE -> TransferPolicy(
            platform = platform.name,
            maxConnections = 6,
            segmentedThresholdBytes = 8L * 1024L * 1024L,
        )
        DownloadPlatform.TIKTOK -> TransferPolicy(
            platform = platform.name,
            maxConnections = 4,
            segmentedThresholdBytes = 8L * 1024L * 1024L,
        )
        DownloadPlatform.DOUYIN -> TransferPolicy(
            platform = platform.name,
            maxConnections = 3,
            segmentedThresholdBytes = 16L * 1024L * 1024L,
        )
        DownloadPlatform.BILIBILI -> TransferPolicy(
            platform = platform.name,
            maxConnections = 4,
            segmentedThresholdBytes = 8L * 1024L * 1024L,
        )
        DownloadPlatform.XIAOHONGSHU -> TransferPolicy(
            platform = platform.name,
            maxConnections = 3,
            segmentedThresholdBytes = 8L * 1024L * 1024L,
        )
    }

    fun forAudio(platform: DownloadPlatform): TransferPolicy = when (platform) {
        DownloadPlatform.YOUTUBE -> TransferPolicy(
            platform = platform.name,
            maxConnections = 6,
            segmentedThresholdBytes = 8L * 1024L * 1024L,
        )
        else -> forPlatform(platform)
    }
}
