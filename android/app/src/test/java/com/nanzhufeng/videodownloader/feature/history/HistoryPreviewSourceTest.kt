package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPreviewSourceTest {
    @Test
    fun completedVideoPrefersItsLocalMediaStoreFrameOverRemoteArtwork() {
        val source = historyPreviewSource(
            history(
                outputUri = "content://media/external/video/media/42",
                thumbnailUrl = "https://cdn.example/expired.jpg",
            ),
        )

        assertEquals(
            HistoryPreviewSource.LocalVideo(
                uri = "content://media/external/video/media/42",
                fallbackArtworkUrl = "https://cdn.example/expired.jpg",
            ),
            source,
        )
    }

    @Test
    fun unavailableVideoUsesRemoteArtworkAsAFallback() {
        val source = historyPreviewSource(
            history(fileExists = false, thumbnailUrl = "https://cdn.example/cover.jpg"),
        )

        assertEquals(HistoryPreviewSource.RemoteArtwork("https://cdn.example/cover.jpg"), source)
    }

    @Test
    fun audioNeverRequestsAVideoFrame() {
        val source = historyPreviewSource(
            history(resolution = ResolutionPreset.AUDIO_MP3, thumbnailUrl = "https://cdn.example/cover.jpg"),
        )

        assertEquals(HistoryPreviewSource.RemoteArtwork("https://cdn.example/cover.jpg"), source)
    }

    @Test
    fun missingOutputAndArtworkHasAStableIconFallback() {
        assertTrue(historyPreviewSource(history(fileExists = false)) is HistoryPreviewSource.None)
    }

    private fun history(
        fileExists: Boolean = true,
        outputUri: String? = "content://media/external/video/media/1",
        thumbnailUrl: String = "",
        resolution: ResolutionPreset = ResolutionPreset.UP_TO_720P,
    ) = DownloadHistory(
        taskId = "task",
        platform = DownloadPlatform.YOUTUBE,
        contentId = "content",
        originalUrl = "https://www.youtube.com/watch?v=content",
        title = "示例",
        creator = "作者",
        resolution = resolution,
        finalStatus = DownloadTaskStatus.COMPLETED,
        outputUri = outputUri,
        fileSize = 1_024L,
        fileExists = fileExists,
        completedAt = 1L,
        thumbnailUrl = thumbnailUrl,
    )
}
