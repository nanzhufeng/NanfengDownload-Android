package com.nanzhufeng.videodownloader.domain.download
import com.nanzhufeng.videodownloader.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DownloadIslandStateTest {
    @Test fun realTransferProgressAndSpeedRemainBelowCompletion() {
        val q = queued("one", DownloadTaskStatus.DOWNLOADING, 50_000_000, 100_000_000)
        val state = DownloadIslandState.from(listOf(q.copy(task=q.task.copy(speedBytesPerSecond=2_500_000))))!!
        assertEquals("下载中 · 50% · 2.5 MB/s", state.compactText)
        assertTrue(state.ongoing)
        assertEquals(99, DownloadIslandState.from(listOf(q.copy(task=q.task.copy(downloadedBytes=100_000_000))))!!.percentage)
    }
    @Test fun processingNeverDisplaysStaleNetworkSpeedOrCompleted() {
        val q = queued("one", DownloadTaskStatus.DOWNLOADING,100,100)
        val state=DownloadIslandState.from(listOf(q.copy(task=q.task.copy(processingStage=DownloadProcessingStage.MERGING, processingProgressPercent=100, speedBytesPerSecond=9000000))))!!
        assertEquals("合并中 · 99%",state.compactText)
        assertTrue(state.ongoing)
    }
    @Test fun publishStageIsShownInsteadOfCollapsingIntoValidation() {
        val q=queued("one", DownloadTaskStatus.VALIDATING,100,100)
        val state=DownloadIslandState.from(listOf(q.copy(task=q.task.copy(processingStage=DownloadProcessingStage.PUBLISHING,processingProgressPercent=30))))!!
        assertEquals("保存中 · 30%",state.compactText)
        assertTrue(state.ongoing)
    }
    @Test fun unknownTotalsAndOldTerminalRowsDoNotFabricateProgress() {
        assertNull(DownloadIslandState.from(listOf(queued("old",DownloadTaskStatus.FAILED,100,100))))
        assertNull(DownloadIslandState.from(listOf(queued("one",DownloadTaskStatus.DOWNLOADING,100,0)))!!.percentage)
        assertEquals(DownloadIslandTone.ERROR, DownloadIslandState.finished(1,0,1).tone)
    }
    @Test fun aRepresentativeTaskDoesNotBorrowAnotherTasksSpeed() {
        val first=queued("one",DownloadTaskStatus.DOWNLOADING,50,100)
        val second=queued("two",DownloadTaskStatus.DOWNLOADING,60,100)
        val list=listOf(first.copy(task=first.task.copy(speedBytesPerSecond=2500000)),second.copy(task=second.task.copy(speedBytesPerSecond=10000000)))
        assertEquals("2.5 MB/s",DownloadIslandState.from(list)!!.speed)
        assertEquals("",DownloadIslandState.from(listOf(list[0].copy(task=list[0].task.copy(processingStage=DownloadProcessingStage.MERGING)),list[1]))!!.speed)
    }
    @Test fun progressCannotExpandTheIslandAndNewTaskResetsMode() {
        val c=DownloadIslandDisplayController();c.onTask("one");assertFalse(c.expanded)
        c.toggle();c.onTask("one");assertTrue(c.expanded)
        c.onTask("two");assertFalse(c.expanded)
        c.toggle();c.collapse();assertFalse(c.expanded)
    }
    @Test fun notificationGatePublishesStagesAndTerminalImmediately() {
        val gate=DownloadIslandUpdateGate();val s=DownloadIslandState("one","test","下载中",1,"")
        assertTrue(gate.publish(s,0));assertFalse(gate.publish(s.copy(percentage=2),400))
        assertTrue(gate.publish(s.copy(stage="合并中"),500));assertTrue(gate.publish(DownloadIslandState.finished(1,0,0),600))
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
