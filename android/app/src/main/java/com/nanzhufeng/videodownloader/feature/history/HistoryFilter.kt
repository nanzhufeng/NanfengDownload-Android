package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus

enum class HistoryPeriod(val label: String, val days: Int?) {
    ALL("全部时间", null),
    LAST_7_DAYS("近 7 天", 7),
    LAST_30_DAYS("近 30 天", 30),
}

fun filterCompletedHistory(
    history: List<DownloadHistory>,
    query: String,
    platform: DownloadPlatform?,
    period: HistoryPeriod,
    now: Long = System.currentTimeMillis(),
): List<DownloadHistory> {
    val normalizedQuery = query.trim()
    val cutoff = period.days?.let { now - it * DAY_MILLIS }
    return history.asSequence()
        .filter { it.finalStatus == DownloadTaskStatus.COMPLETED }
        .filter { normalizedQuery.isBlank() || it.title.contains(normalizedQuery, true) || it.creator.contains(normalizedQuery, true) || it.originalUrl.contains(normalizedQuery, true) }
        .filter { platform == null || it.platform == platform }
        .filter { cutoff == null || it.completedAt >= cutoff }
        .sortedByDescending(DownloadHistory::completedAt)
        .toList()
}

private const val DAY_MILLIS = 86_400_000L
