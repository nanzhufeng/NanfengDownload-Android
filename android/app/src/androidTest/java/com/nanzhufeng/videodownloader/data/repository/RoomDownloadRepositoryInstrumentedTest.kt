package com.nanzhufeng.videodownloader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadFailureType
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDownloadRepositoryInstrumentedTest {
    private lateinit var database: NanzhufengDatabase
    private lateinit var repository: RoomDownloadRepository

    @Before
    fun createRepository() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NanzhufengDatabase::class.java,
        ).allowMainThreadQueries().build()
        var nextId = 0
        repository = RoomDownloadRepository(
            database = database,
            clock = { 100L },
            idFactory = { "task-${++nextId}" },
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun enqueueCreatesSelectedWaitingTaskAndSelectionPersists() = runBlocking {
        val taskIds = repository.enqueue(listOf(media()), ResolutionPreset.UP_TO_720P)

        val queued = repository.activeTasks.first().single()
        assertEquals(listOf("task-1"), taskIds)
        assertEquals(DownloadTaskStatus.WAITING, queued.task.status)
        assertEquals("示例视频", queued.media.title)

        repository.setSelected("task-1", false)

        assertFalse(repository.activeTasks.first().single().task.selected)
    }

    @Test
    fun resolutionChangePersistsForOneTaskOnly() = runBlocking {
        repository.enqueue(listOf(media(), media().copy(contentId = "content-2")), ResolutionPreset.UP_TO_720P)

        repository.setResolution("task-2", ResolutionPreset.UP_TO_1080P)

        val queued = repository.activeTasks.first()
        assertEquals(ResolutionPreset.UP_TO_720P, queued.first().task.resolution)
        assertEquals(ResolutionPreset.UP_TO_1080P, queued.last().task.resolution)
    }

    @Test
    fun invalidTransitionIsRejectedAndLegalSequenceSucceeds() = runBlocking {
        repository.enqueue(listOf(media()), ResolutionPreset.UP_TO_720P)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.transition("task-1", DownloadTaskStatus.DOWNLOADING)
            }
        }

        repository.transition("task-1", DownloadTaskStatus.PARSING)
        repository.transition("task-1", DownloadTaskStatus.DOWNLOADING)

        assertEquals(
            DownloadTaskStatus.DOWNLOADING,
            repository.activeTasks.first().single().task.status,
        )
    }

    @Test
    fun terminalResultCanBeArchived() = runBlocking {
        repository.archiveTerminal(history())

        assertEquals("content-1", repository.history.first().single().contentId)
    }

    @Test
    fun cancelTaskArchivesCancelledResult() = runBlocking {
        repository.enqueue(listOf(media()), ResolutionPreset.UP_TO_720P)

        assertTrue(repository.cancelTask("task-1"))

        val archived = repository.history.first().single()
        assertEquals(DownloadTaskStatus.CANCELLED, archived.finalStatus)
        assertEquals("content-1", archived.contentId)
        assertFalse(archived.fileExists)
    }

    @Test
    fun removeQueueTaskDeletesWaitingTaskButRefusesActiveTransfer() = runBlocking {
        repository.enqueue(listOf(media(), media().copy(contentId = "content-2")), ResolutionPreset.UP_TO_720P)
        repository.transition("task-2", DownloadTaskStatus.PARSING)
        repository.transition("task-2", DownloadTaskStatus.DOWNLOADING)

        assertTrue(repository.removeQueueTask("task-1"))
        assertFalse(repository.removeQueueTask("task-2"))

        val remaining = repository.activeTasks.first().single()
        assertEquals("task-2", remaining.task.taskId)
        assertEquals(DownloadTaskStatus.DOWNLOADING, remaining.task.status)
    }

    @Test
    fun retryHistoryRequeuesFailedTaskAndRemovesStaleTerminalRecord() = runBlocking {
        repository.enqueue(listOf(media()), ResolutionPreset.UP_TO_720P)
        repository.transition("task-1", DownloadTaskStatus.PARSING)
        repository.transition("task-1", DownloadTaskStatus.FAILED)
        repository.archiveTerminal(
            history().copy(
                finalStatus = DownloadTaskStatus.FAILED,
                outputUri = null,
                fileSize = 0L,
                fileExists = false,
            ),
        )

        assertTrue(repository.retryHistory("task-1"))

        val queued = repository.activeTasks.first().single()
        assertEquals(DownloadTaskStatus.WAITING, queued.task.status)
        assertTrue(queued.task.selected)
        assertEquals(0L, queued.task.downloadedBytes)
        assertEquals(0L, queued.task.totalBytes)
        assertNull(queued.task.failureType)
        assertNull(queued.task.errorSummary)
        assertTrue(repository.history.first().isEmpty())
    }

    @Test
    fun failedTaskRemainsVisibleInQueueWithItsProblemUntilUserActs() = runBlocking {
        repository.enqueue(listOf(media()), ResolutionPreset.UP_TO_720P)
        repository.transition("task-1", DownloadTaskStatus.PARSING)
        repository.transitionWithProblem(
            taskId = "task-1",
            to = DownloadTaskStatus.FAILED,
            failureType = com.nanzhufeng.videodownloader.core.model.DownloadFailureType.TRANSFER,
            errorSummary = "unexpected end of stream",
        )

        val failed = repository.activeTasks.first().single()
        assertEquals(DownloadTaskStatus.FAILED, failed.task.status)
        assertEquals("unexpected end of stream", failed.task.errorSummary)
    }

    @Test
    fun enqueueSkipsItemsAlreadyPresentInDownloadListAndDuplicatesWithinTheSameRead() = runBlocking {
        assertEquals(
            listOf("task-1"),
            repository.enqueue(listOf(media(), media()), ResolutionPreset.UP_TO_720P),
        )

        assertTrue(repository.enqueue(listOf(media()), ResolutionPreset.UP_TO_1080P).isEmpty())
        assertEquals(1, repository.activeTasks.first().size)
    }

    @Test
    fun enqueueSkipsCompletedHistoryRegardlessOfRequestedResolution() = runBlocking {
        repository.archiveTerminal(history().copy(taskId = "completed-history"))

        assertTrue(repository.enqueue(listOf(media()), ResolutionPreset.UP_TO_1080P).isEmpty())
        assertTrue(repository.activeTasks.first().isEmpty())

        val added = repository.enqueue(
            listOf(media().copy(contentId = "new-content")),
            ResolutionPreset.UP_TO_1080P,
        )
        assertEquals(listOf("task-1"), added)
    }

    @Test
    fun completedHistoryCannotBeRetriedButCanBeDeleted() = runBlocking {
        repository.archiveTerminal(history())

        assertFalse(repository.retryHistory("task-1"))
        assertTrue(repository.deleteHistoryRecord("task-1"))

        assertTrue(repository.history.first().isEmpty())
    }

    private fun media() = MediaItem(
        mediaKey = "ignored",
        platform = DownloadPlatform.TIKTOK,
        contentId = "content-1",
        originalUrl = "https://www.tiktok.com/@creator/video/content-1",
        sourceKind = DownloadSourceKind.SINGLE_VIDEO,
        title = "示例视频",
        creator = "creator",
        creatorId = "creator-id",
        publishDate = "2026-07-16",
        thumbnailUrl = "",
    )

    private fun history() = DownloadHistory(
        taskId = "task-1",
        platform = DownloadPlatform.TIKTOK,
        contentId = "content-1",
        originalUrl = "https://www.tiktok.com/@creator/video/content-1",
        title = "示例视频",
        creator = "creator",
        resolution = ResolutionPreset.UP_TO_720P,
        finalStatus = DownloadTaskStatus.COMPLETED,
        outputUri = "content://media/external/video/media/1",
        fileSize = 1024L,
        fileExists = true,
        completedAt = 200L,
    )
}
