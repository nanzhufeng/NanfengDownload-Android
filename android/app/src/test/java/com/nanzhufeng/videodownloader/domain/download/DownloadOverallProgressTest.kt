package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadOverallProgressTest {
    @Test
    fun completedNetworkTransferLeavesRoomForProcessingAndPublishing() {
        val task = task(
            status = DownloadTaskStatus.DOWNLOADING,
            stage = DownloadProcessingStage.NETWORK_MEDIA,
            downloaded = 100L,
            total = 100L,
        )

        assertEquals(80, DownloadOverallProgress.percent(task))
    }

    @Test
    fun finishedVideoSegmentationStillDoesNotClaimTaskCompletion() {
        val task = task(
            status = DownloadTaskStatus.DOWNLOADING,
            stage = DownloadProcessingStage.VIDEO_SEGMENTING,
            processingPercent = 100,
            downloaded = 100L,
            total = 100L,
        )

        assertEquals(95, DownloadOverallProgress.percent(task))
    }

    @Test
    fun mergeProgressUsesLocalProcessingRangeAndStopsBelowCompletion() {
        val task = task(
            status = DownloadTaskStatus.DOWNLOADING,
            stage = DownloadProcessingStage.MERGING,
            processingPercent = 50,
            downloaded = 100L,
            total = 100L,
        )

        assertEquals(89, DownloadOverallProgress.percent(task))
    }

    @Test
    fun publishingStopsBelowOneHundredUntilRepositoryMarksCompleted() {
        val publishing = task(
            status = DownloadTaskStatus.VALIDATING,
            stage = DownloadProcessingStage.PUBLISHING,
            downloaded = 100L,
            total = 100L,
        )
        val completed = publishing.copy(status = DownloadTaskStatus.COMPLETED)

        assertEquals(98, DownloadOverallProgress.percent(publishing))
        assertEquals(100, DownloadOverallProgress.percent(completed))
    }

    @Test
    fun everyUnfinishedPipelineStageRemainsBelowOneHundred() {
        DownloadProcessingStage.entries.forEach { stage ->
            val task = task(
                status = DownloadTaskStatus.DOWNLOADING,
                stage = stage,
                processingPercent = 100,
                downloaded = 100L,
                total = 100L,
            )
            assertTrue("$stage 不得在任务完成前显示 100%", DownloadOverallProgress.percent(task) < 100)
        }
    }

    private fun task(
        status: DownloadTaskStatus,
        stage: DownloadProcessingStage,
        processingPercent: Int = 0,
        downloaded: Long,
        total: Long,
    ) = DownloadTask(
        taskId = "task",
        mediaKey = "YOUTUBE:video",
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
        processingStage = stage,
        processingProgressPercent = processingPercent,
    )
}
