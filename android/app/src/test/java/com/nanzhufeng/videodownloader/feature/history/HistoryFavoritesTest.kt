package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFavoritesTest {
    private fun entry(id: String, favorite: Boolean = false) = DownloadHistory(
        id, DownloadPlatform.YOUTUBE, id, "https://example.com/$id", id, "作者",
        ResolutionPreset.UP_TO_720P, DownloadTaskStatus.COMPLETED,
        "content://media/external/video/media/1", 100L, true, 1000L,
        isFavorite = favorite,
    )
    @Test fun favoritesShowOnlySavedCompletedVideosIncludingUnavailableFiles() {
        val rows = listOf(entry("ordinary"), entry("saved", true),
            entry("missing", true).copy(fileExists = false),
            entry("audio", true).copy(resolution = ResolutionPreset.AUDIO_MP3),
            entry("gallery", true).copy(capturedImageExpectedCount = 3),
            entry("cancelled", true).copy(finalStatus = DownloadTaskStatus.CANCELLED))
        assertEquals(listOf("saved", "missing"), filterCompletedHistory(rows, "", null,
            HistoryPeriod.ALL, favoritesOnly = true).map { it.taskId })
    }
    @Test fun cancellingFavoriteKeepsTheRecordAndItsOriginalOutput() {
        val saved = entry("saved", true)
        val cancelled = saved.copy(isFavorite = false)
        assertEquals(emptyList<String>(), filterCompletedHistory(listOf(cancelled), "", null,
            HistoryPeriod.ALL, favoritesOnly = true).map { it.taskId })
        assertEquals(listOf(cancelled), filterCompletedHistory(listOf(cancelled), "", null, HistoryPeriod.ALL))
        assertEquals(saved.outputUri, cancelled.outputUri)
    }
    @Test fun favoriteResultsStillSupportSearchPlatformAndTimeFilters() {
        val rows = listOf(entry("old", true).copy(completedAt = 0),
            entry("recent", true).copy(completedAt = 100 * 86400000L),
            entry("other", true).copy(platform = DownloadPlatform.DOUYIN, completedAt = 100 * 86400000L))
        assertEquals(listOf("recent"), filterCompletedHistory(rows, "recent", DownloadPlatform.YOUTUBE,
            HistoryPeriod.LAST_7_DAYS, now = 100 * 86400000L, favoritesOnly = true).map { it.taskId })
    }
}
