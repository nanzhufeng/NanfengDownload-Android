package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationStateTest {
    @Test
    fun activeQueue_reportsDeterminateProgressForCurrentTransfers() {
        val state = DownloadNotificationState.from(
            listOf(
                queued("one", DownloadTaskStatus.DOWNLOADING, downloaded = 50L, total = 100L),
                queued("two", DownloadTaskStatus.PARSING, downloaded = 0L, total = 0L),
                queued("three", DownloadTaskStatus.WAITING, downloaded = 0L, total = 0L),
            ),
        )

        assertEquals("正在下载 2 项 · 50%", state.content)
        assertEquals(100, state.max)
        assertEquals(50, state.value)
        assertFalse(state.indeterminate)
    }

    @Test
    fun activeQueue_withoutKnownSize_keepsIndeterminateProgress() {
        val state = DownloadNotificationState.from(
            listOf(queued("one", DownloadTaskStatus.DOWNLOADING, downloaded = 32L, total = 0L)),
        )

        assertEquals("正在下载 1 项", state.content)
        assertTrue(state.indeterminate)
    }

    @Test
    fun idleQueue_doesNotClaimThatDownloadsAreRunning() {
        val state = DownloadNotificationState.from(
            listOf(queued("one", DownloadTaskStatus.WAITING, downloaded = 0L, total = 0L)),
        )

        assertEquals("正在准备下载", state.content)
        assertTrue(state.indeterminate)
    }

    private fun queued(
        id: String,
        status: DownloadTaskStatus,
        downloaded: Long,
        total: Long,
    ) = QueuedDownload(
        task = DownloadTask(
            taskId = id,
            mediaKey = "YOUTUBE:$id",
            selected = true,
            sortOrder = 0L,
            resolution = ResolutionPreset.UP_TO_720P,
            saveTreeUri = null,
            downloadedBytes = downloaded,
            totalBytes = total,
            speedBytesPerSecond = 0L,
            remainingSeconds = null,
            status = status,
            failureType = null,
            errorSummary = null,
            retryCount = 0,
            updatedAt = 0L,
        ),
        media = MediaItem(
            mediaKey = "YOUTUBE:$id",
            platform = DownloadPlatform.YOUTUBE,
            contentId = id,
            originalUrl = "https://example.test/$id",
            sourceKind = DownloadSourceKind.SINGLE_VIDEO,
            title = id,
            creator = "creator",
            creatorId = "creator",
            publishDate = "",
            thumbnailUrl = "",
        ),
    )
}
