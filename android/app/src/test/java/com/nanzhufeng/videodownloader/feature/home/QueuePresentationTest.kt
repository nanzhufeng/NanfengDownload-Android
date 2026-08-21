package com.nanzhufeng.videodownloader.feature.home

import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class QueuePresentationTest {
    @Test
    fun stalledDownload_reportsProblemAndActionWithoutHidingTheStatusSlot() {
        val now = 100_000L
        val controller = TransferHealthNoticeController()

        assertEquals(
            "下载已连续 20 秒没有收到新数据。解决办法：请先等待 App 自动续传；若持续不动，请检查网络或代理后点击重试。",
            controller.update(task(speed = 0L, updatedAt = now - 20_000L), now)?.message,
        )
    }

    @Test
    fun slowDownload_appearsOnlyAfterFiveStableSecondsBelow600Kb() {
        val now = 100_000L
        val controller = TransferHealthNoticeController()

        assertEquals(
            "连接正常，正在持续下载。",
            controller.update(task(speed = 599L * 1024L, updatedAt = now), now)?.message,
        )

        assertEquals(
            "下载速度已持续低于 600 KB/s。解决办法：请检查 Wi-Fi、代理或平台网络；App 会继续下载，无需反复点击重试。",
            controller.update(task(speed = 599L * 1024L, updatedAt = now + 5_000L), now + 5_000L)?.message,
        )
    }

    @Test
    fun slowWarning_staysVisibleUntilSpeedHasRecoveredForEightSeconds() {
        val now = 100_000L
        val controller = TransferHealthNoticeController()
        controller.update(task(speed = 100L * 1024L, updatedAt = now), now)
        val slow = controller.update(task(speed = 100L * 1024L, updatedAt = now + 5_000L), now + 5_000L)

        assertEquals(
            slow,
            controller.update(task(speed = 2L * 1024L * 1024L, updatedAt = now + 6_000L), now + 6_000L),
        )
        assertEquals(
            "连接正常，正在持续下载。",
            controller.update(task(speed = 2L * 1024L * 1024L, updatedAt = now + 14_000L), now + 14_000L)?.message,
        )
    }

    @Test
    fun speedAt600Kb_isHealthy() {
        val now = 100_000L

        assertEquals(
            "连接正常，正在持续下载。",
            TransferHealthNoticeController()
                .update(task(speed = 600L * 1024L, updatedAt = now), now)
                ?.message,
        )
    }

    @Test
    fun audioTask_reportsVideoSourceDownloadBeforeConversion() {
        assertEquals(
            "正在下载视频转换源 40%",
            audioTaskPhaseText(
                task(
                    speed = 2L * 1024L * 1024L,
                    updatedAt = 100_000L,
                    resolution = ResolutionPreset.AUDIO_MP3,
                    downloadedBytes = 40L,
                    totalBytes = 100L,
                    processingStage = DownloadProcessingStage.NETWORK_VIDEO_TO_AUDIO,
                ),
            ),
        )
    }

    @Test
    fun audioTask_reportsMp3ExtractionAfterSourceDownloadCompletes() {
        assertEquals(
            "正在提取音频并生成 MP3 63%",
            audioTaskPhaseText(
                task(
                    speed = 0L,
                    updatedAt = 100_000L,
                    resolution = ResolutionPreset.AUDIO_MP3,
                    downloadedBytes = 100L,
                    totalBytes = 100L,
                    processingStage = DownloadProcessingStage.TRANSCODING,
                    processingProgressPercent = 63,
                ),
            ),
        )
    }

    @Test
    fun videoTask_reportsStableLocalSegmentingProgress() {
        assertEquals(
            "正在无损生成 4 段视频 63%",
            mediaTaskPhaseText(
                task(
                    speed = 0L,
                    updatedAt = 100_000L,
                    processingStage = DownloadProcessingStage.VIDEO_SEGMENTING,
                    processingProgressPercent = 63,
                    segmentCount = 4,
                ),
            ),
        )
    }

    @Test
    fun videoTask_reportsRealMuxProgress() {
        assertEquals(
            "正在快速合并音视频 63%",
            mediaTaskPhaseText(
                task(
                    speed = 0L,
                    updatedAt = 100_000L,
                    processingStage = DownloadProcessingStage.MERGING,
                    processingProgressPercent = 63,
                    downloadedBytes = 100L,
                    totalBytes = 100L,
                ),
            ),
        )
    }

    @Test
    fun resolution360pTask_reportsRealDownscaleProgress() {
        assertEquals(
            "正在转码为 360p 63%",
            mediaTaskPhaseText(
                task(
                    speed = 0L,
                    updatedAt = 100_000L,
                    resolution = ResolutionPreset.UP_TO_360P,
                    processingStage = DownloadProcessingStage.TRANSCODING,
                    processingProgressPercent = 63,
                ),
            ),
        )
    }

    private fun task(
        speed: Long,
        updatedAt: Long,
        resolution: ResolutionPreset = ResolutionPreset.UP_TO_720P,
        downloadedBytes: Long = 10L,
        totalBytes: Long = 100L,
        processingStage: DownloadProcessingStage = DownloadProcessingStage.NETWORK_MEDIA,
        processingProgressPercent: Int = 0,
        segmentCount: Int = 1,
    ) = DownloadTask(
        taskId = "active",
        mediaKey = "youtube:active",
        selected = true,
        sortOrder = 0L,
        resolution = resolution,
        saveTreeUri = null,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        speedBytesPerSecond = speed,
        remainingSeconds = 20L,
        status = DownloadTaskStatus.DOWNLOADING,
        failureType = null,
        errorSummary = null,
        retryCount = 0,
        updatedAt = updatedAt,
        processingStage = processingStage,
        processingProgressPercent = processingProgressPercent,
        audioSegmentCount = segmentCount,
    )
}
