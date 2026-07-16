package com.nanzhufeng.videodownloader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
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
