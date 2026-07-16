package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    @Test
    fun completedProjection_excludesEveryNonCompletedStatusBeforeQueryAndPlatformFilters() {
        val result = filterCompletedHistory(
            history = listOf(
                history("done", "AI 进展", "作者甲", DownloadPlatform.YOUTUBE, DownloadTaskStatus.COMPLETED),
                history("failed", "AI 失败", "作者甲", DownloadPlatform.YOUTUBE, DownloadTaskStatus.FAILED),
                history("skipped", "AI 跳过", "作者乙", DownloadPlatform.DOUYIN, DownloadTaskStatus.SKIPPED),
                history("cancelled", "AI 取消", "作者乙", DownloadPlatform.TIKTOK, DownloadTaskStatus.CANCELLED),
            ),
            query = "AI",
            platform = null,
            period = HistoryPeriod.ALL,
            now = 2_000_000L,
        )

        assertEquals(listOf("done"), result.map(DownloadHistory::taskId))
    }

    @Test
    fun recentPeriodDropsOlderRecords() {
        val now = 40L * DAY_MILLIS
        val result = filterCompletedHistory(
            history = listOf(
                history("recent", "近期", "作者", completedAt = now - 2L * DAY_MILLIS),
                history("old", "较早", "作者", completedAt = now - 10L * DAY_MILLIS),
            ),
            query = "",
            platform = null,
            period = HistoryPeriod.LAST_7_DAYS,
            now = now,
        )

        assertEquals(listOf("recent"), result.map(DownloadHistory::taskId))
    }

    private fun history(
        id: String,
        title: String,
        creator: String,
        platform: DownloadPlatform = DownloadPlatform.YOUTUBE,
        status: DownloadTaskStatus = DownloadTaskStatus.COMPLETED,
        completedAt: Long = 1_000_000L,
    ) = DownloadHistory(
        taskId = id,
        platform = platform,
        contentId = id,
        originalUrl = "https://example.com/$id",
        title = title,
        creator = creator,
        resolution = ResolutionPreset.UP_TO_720P,
        finalStatus = status,
        outputUri = null,
        fileSize = 0L,
        fileExists = false,
        completedAt = completedAt,
    )

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
