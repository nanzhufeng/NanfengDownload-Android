package com.nanzhufeng.videodownloader.feature.home

import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
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

    private fun task(speed: Long, updatedAt: Long) = DownloadTask(
        taskId = "active",
        mediaKey = "youtube:active",
        selected = true,
        sortOrder = 0L,
        resolution = ResolutionPreset.UP_TO_720P,
        saveTreeUri = null,
        downloadedBytes = 10L,
        totalBytes = 100L,
        speedBytesPerSecond = speed,
        remainingSeconds = 20L,
        status = DownloadTaskStatus.DOWNLOADING,
        failureType = null,
        errorSummary = null,
        retryCount = 0,
        updatedAt = updatedAt,
    )
}
