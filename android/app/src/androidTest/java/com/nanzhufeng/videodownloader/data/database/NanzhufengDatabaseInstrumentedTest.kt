package com.nanzhufeng.videodownloader.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskEntity
import com.nanzhufeng.videodownloader.data.database.entity.MediaItemEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadThroughputReportEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NanzhufengDatabaseInstrumentedTest {
    private lateinit var database: NanzhufengDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NanzhufengDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun activeTaskIncludesItsMediaAndSelectionPersists() = runBlocking {
        database.mediaItemDao().upsertAll(listOf(media()))
        database.downloadTaskDao().upsertAll(listOf(task()))

        val active = database.downloadTaskDao().observeActive().first()

        assertEquals(listOf("task-1"), active.map { it.task.taskId })
        assertEquals("示例视频", active.single().media.title)

        database.downloadTaskDao().updateSelection("task-1", false, 200L)

        assertFalse(database.downloadTaskDao().getById("task-1")!!.selected)
    }

    @Test
    fun completedHistoryUsesStableContentAndResolutionKey() = runBlocking {
        database.downloadHistoryDao().upsert(history())

        val found = database.downloadHistoryDao().findCompleted(
            platform = "TIKTOK",
            contentId = "content-1",
            resolution = "UP_TO_720P",
            audioSegmentCount = 1,
        )

        assertNotNull(found)
        assertEquals("task-1", found!!.taskId)
    }

    @Test
    fun throughputReportPersistsAllMeasuredTransferFacts() = runBlocking {
        database.downloadThroughputReportDao().upsert(
            DownloadThroughputReportEntity(
                reportId = "report-1",
                taskId = "task-1",
                platform = "YOUTUBE",
                streamLabel = "视频流",
                outcome = "COMPLETED",
                connectionMode = "MULTI",
                connectionCount = 6,
                rangeSupported = true,
                expectedBytes = 20_000_000L,
                committedBytes = 20_000_000L,
                networkBytes = 20_000_000L,
                startedAt = 100L,
                finishedAt = 2100L,
                elapsedMillis = 2000L,
                averageBytesPerSecond = 10_000_000L,
                peakBytesPerSecond = 13_000_000L,
                retryCount = 0,
                reprobeCount = 1,
                fallbackReason = null,
                errorSummary = null,
            ),
        )

        val report = database.downloadThroughputReportDao().getByTaskId("task-1").single()

        assertEquals("MULTI", report.connectionMode)
        assertEquals(6, report.connectionCount)
        assertEquals(10_000_000L, report.averageBytesPerSecond)
        assertEquals(1, report.reprobeCount)
    }

    private fun media() = MediaItemEntity(
        mediaKey = "TIKTOK:content-1",
        platform = "TIKTOK",
        contentId = "content-1",
        originalUrl = "https://www.tiktok.com/@creator/video/content-1",
        sourceKind = "SINGLE_VIDEO",
        title = "示例视频",
        creator = "creator",
        creatorId = "creator-id",
        publishDate = "2026-07-16",
        thumbnailUrl = "",
        discoveredAt = 100L,
    )

    private fun task() = DownloadTaskEntity(
        taskId = "task-1",
        mediaKey = "TIKTOK:content-1",
        selected = true,
        sortOrder = 1L,
        resolution = "UP_TO_720P",
        saveTreeUri = null,
        tempPath = null,
        downloadedBytes = 0L,
        totalBytes = 0L,
        speedBytesPerSecond = 0L,
        remainingSeconds = null,
        status = "WAITING",
        failureType = null,
        errorSummary = null,
        retryCount = 0,
        createdAt = 100L,
        updatedAt = 100L,
    )

    private fun history() = DownloadHistoryEntity(
        taskId = "task-1",
        platform = "TIKTOK",
        contentId = "content-1",
        originalUrl = "https://www.tiktok.com/@creator/video/content-1",
        title = "示例视频",
        creator = "creator",
        resolution = "UP_TO_720P",
        finalStatus = "COMPLETED",
        outputUri = "content://media/external/video/media/1",
        fileSize = 1024L,
        fileExists = true,
        completedAt = 200L,
    )
}
