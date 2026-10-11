package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.probe.TransferPolicy

object PlatformTransferPolicy {
    fun forFragments(platform: DownloadPlatform): TransferPolicy = forPlatform(platform).copy(
        maxConnections = 6,
        chunkSizeBytes = null,
        preferSingleConnection = false,
    )

    fun forPlatform(platform: DownloadPlatform): TransferPolicy = when (platform) {
        DownloadPlatform.YOUTUBE -> TransferPolicy(
            platform = platform.name,
            maxConnections = 6,
            segmentedThresholdBytes = 8L * 1024L * 1024L,
            chunkSizeBytes = 4L * 1024L * 1024L,
        )
        // Share the proven video range/chunk path, bounded by the existing
        // per-task/per-stream global connection budget. Images stay separate.
        else -> TransferPolicy(
            platform = platform.name,
            maxConnections = 6,
            segmentedThresholdBytes = 2L * 1024L * 1024L,
            chunkSizeBytes = 4L * 1024L * 1024L,
            rangeProbeBytes = 8 * 1024,
        )
    }

    fun forAudio(platform: DownloadPlatform): TransferPolicy = when (platform) {
        DownloadPlatform.YOUTUBE -> TransferPolicy(
            platform = platform.name,
            maxConnections = 1,
            segmentedThresholdBytes = 8L * 1024L * 1024L,
            chunkSizeBytes = 1L * 1024L * 1024L,
        )
        else -> forPlatform(platform)
    }

    fun forAudioSource(
        platform: DownloadPlatform,
        fromVideoSource: Boolean,
    ): TransferPolicy = if (fromVideoSource) {
        forPlatform(platform)
    } else {
        forAudio(platform)
    }

    fun forImage(platform: DownloadPlatform): TransferPolicy = TransferPolicy(
        platform = platform.name,
        maxConnections = 1,
        segmentedThresholdBytes = Long.MAX_VALUE,
    )
}
