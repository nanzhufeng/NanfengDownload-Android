package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    @Test
    fun queryMatchesTitleOrCreatorAndCombinesWithStatusAndPlatform() {
        val result = filterHistory(
            history = listOf(
                history("one", "AI 进展", "作者甲", DownloadPlatform.YOUTUBE, DownloadTaskStatus.COMPLETED),
                history("two", "城市漫步", "作者甲", DownloadPlatform.DOUYIN, DownloadTaskStatus.FAILED),
                history("three", "AI 观察", "作者乙", DownloadPlatform.YOUTUBE, DownloadTaskStatus.FAILED),
            ),
            query = "AI",
            status = HistoryStatusFilter.FAILED,
            platform = DownloadPlatform.YOUTUBE,
            period = HistoryPeriod.ALL,
            now = 2_000_000L,
        )

        assertEquals(listOf("three"), result.map(DownloadHistory::taskId))
    }

    @Test
    fun recentPeriodDropsOlderRecords() {
        val now = 40L * DAY_MILLIS
        val result = filterHistory(
            history = listOf(
                history("recent", "近期", "作者", completedAt = now - 2L * DAY_MILLIS),
                history("old", "较早", "作者", completedAt = now - 10L * DAY_MILLIS),
            ),
            query = "",
            status = HistoryStatusFilter.ALL,
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
