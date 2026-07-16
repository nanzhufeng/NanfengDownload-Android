package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus

enum class HistoryStatusFilter(val label: String, val status: DownloadTaskStatus?) {
    ALL("全部", null),
    COMPLETED("完成", DownloadTaskStatus.COMPLETED),
    FAILED("失败", DownloadTaskStatus.FAILED),
    SKIPPED("已跳过", DownloadTaskStatus.SKIPPED),
    CANCELLED("已取消", DownloadTaskStatus.CANCELLED),
}

enum class HistoryPeriod(val label: String, val days: Int?) {
    ALL("全部时间", null),
    LAST_7_DAYS("近 7 天", 7),
    LAST_30_DAYS("近 30 天", 30),
}

fun filterHistory(
    history: List<DownloadHistory>,
    query: String,
    status: HistoryStatusFilter,
    platform: DownloadPlatform?,
    period: HistoryPeriod,
    now: Long = System.currentTimeMillis(),
): List<DownloadHistory> {
    val normalizedQuery = query.trim()
    val cutoff = period.days?.let { days -> now - days * DAY_MILLIS }
    return history.asSequence()
        .filter { item ->
            normalizedQuery.isBlank() ||
                item.title.contains(normalizedQuery, ignoreCase = true) ||
                item.creator.contains(normalizedQuery, ignoreCase = true) ||
                item.originalUrl.contains(normalizedQuery, ignoreCase = true)
        }
        .filter { item -> status.status == null || item.finalStatus == status.status }
        .filter { item -> platform == null || item.platform == platform }
        .filter { item -> cutoff == null || item.completedAt >= cutoff }
        .sortedByDescending(DownloadHistory::completedAt)
        .toList()
}

private const val DAY_MILLIS = 86_400_000L
