package com.nanzhufeng.videodownloader.navigation

import com.nanzhufeng.videodownloader.core.model.*
import com.nanzhufeng.videodownloader.core.ui.DownloadCompletionNotices
import org.junit.Assert.*
import org.junit.Test

class AppFeedbackResourceTest {
    @Test fun existingCompletedHistoryDoesNotRepeatSuccessOnStartup() {
        assertNull(DownloadCompletionNotices().onSnapshot(listOf(history("old"))))
    }
    @Test fun aNewPublishedDownloadGetsOneNonBlockingNoticeWithItsFullTitle() {
        val observer = DownloadCompletionNotices()
        observer.onSnapshot(emptyList())
        val item = history("new").copy(title = "真实长标题 · 中文 English 2026".repeat(8))
        val notice = requireNotNull(observer.onSnapshot(listOf(item)))
        assertEquals("下载完成", notice.title)
        assertTrue(notice.detail.contains(item.title))
        assertNull(observer.onSnapshot(listOf(item)))
    }
    @Test fun anOldOutputBecomingAvailableIsNotANewDownload() {
        val observer = DownloadCompletionNotices()
        observer.onSnapshot(listOf(history("old").copy(fileExists = false)))
        assertNull(observer.onSnapshot(listOf(history("old"))))
    }
    @Test fun anOldHistoryReturningAfterAnEmptySnapshotDoesNotRepeatSuccess() {
        val observer = DownloadCompletionNotices()
        observer.onSnapshot(listOf(history("old")))
        observer.onSnapshot(emptyList())
        assertNull(observer.onSnapshot(listOf(history("old"))))
    }
    @Test fun failedCancelledAndMissingOutputNeverSaySaved() {
        val observer = DownloadCompletionNotices()
        observer.onSnapshot(emptyList())
        val invalid = listOf(
            history("failed").copy(finalStatus = DownloadTaskStatus.FAILED),
            history("cancelled").copy(finalStatus = DownloadTaskStatus.CANCELLED),
            history("missing").copy(outputUri = null, outputUris = emptyList()),
            history("empty").copy(fileSize = 0),
        )
        assertNull(observer.onSnapshot(invalid))
    }
    private fun history(id: String) = DownloadHistory(
        id, DownloadPlatform.GENERIC, id, "https://fixture.test/$id", id, "", ResolutionPreset.UP_TO_720P,
        DownloadTaskStatus.COMPLETED, "content://fixture/$id", 100L, true, 1L,
    )
}
