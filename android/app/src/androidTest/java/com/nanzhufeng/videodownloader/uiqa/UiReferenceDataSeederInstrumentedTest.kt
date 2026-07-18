package com.nanzhufeng.videodownloader.uiqa

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nanzhufeng.videodownloader.NanzhufengApplication
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiReferenceDataSeederInstrumentedTest {
    @Test
    fun seedReferenceQueueAndCompletedHistoryOnEmulatorOnly() = runBlocking {
        assertTrue(
            "视觉验收样本只允许写入 Android 模拟器",
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.FINGERPRINT.contains("generic", ignoreCase = true),
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as NanzhufengApplication
        val container = application.container
        val thumbnails = (1..4).map { index ->
            val file = File(context.cacheDir, "ui-reference-queue-$index.png")
            instrumentation.context.assets.open("ui-preview/queue-$index.png").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.toURI().toString()
        }

        withContext(Dispatchers.IO) { container.database.clearAllTables() }

        val media = listOf(
            media("lion", "Me at the zoo", "jawed", DownloadPlatform.YOUTUBE, thumbnails[0]),
            media("travel", "风景太美了！治愈系旅行", "@旅行日记", DownloadPlatform.DOUYIN, thumbnails[1]),
            media("pasta", "Cooking pasta like a pro", "@Chef Luca", DownloadPlatform.TIKTOK, thumbnails[2]),
            media("earth", "NASA – Earth from Space (4K UHD)", "NASA", DownloadPlatform.YOUTUBE, thumbnails[3]),
        )
        val taskIds = container.downloads.enqueue(media, ResolutionPreset.UP_TO_1080P)
        container.downloads.setResolution(taskIds[0], ResolutionPreset.AUDIO_MP3)
        container.downloads.setResolution(taskIds[2], ResolutionPreset.UP_TO_720P)
        container.downloads.setResolution(taskIds[3], ResolutionPreset.BEST)
        container.downloads.transition(taskIds[0], DownloadTaskStatus.PARSING)
        container.downloads.transition(taskIds[0], DownloadTaskStatus.DOWNLOADING)
        container.downloads.updateTransfer(
            taskId = taskIds[0],
            downloadedBytes = 322_000L,
            totalBytes = 448_200L,
            speedBytesPerSecond = 91_000L,
            remainingSeconds = 2L,
        )

        listOf(
            history("history-1", DownloadPlatform.YOUTUBE, "Me at the zoo", "jawed", ResolutionPreset.AUDIO_MP3, 448_200L, "2026-07-16 19:15"),
            history("history-2", DownloadPlatform.YOUTUBE, "Me at the zoo", "jawed", ResolutionPreset.AUDIO_MP3, 448_200L, "2026-07-16 19:13"),
            history("history-3", DownloadPlatform.DOUYIN, "风景太美了！治愈系旅行", "@旅行日记", ResolutionPreset.UP_TO_1080P, 32_700_000L, "2026-07-16 18:44"),
            history("history-4", DownloadPlatform.TIKTOK, "Cooking pasta like a pro", "@Chef Luca", ResolutionPreset.UP_TO_720P, 18_300_000L, "2026-07-16 18:20"),
            history("history-5", DownloadPlatform.YOUTUBE, "NASA – Earth from Space (4K UHD)", "NASA", ResolutionPreset.BEST, 512_600_000L, "2026-07-16 17:58"),
            history("history-6", DownloadPlatform.DOUYIN, "街头音乐现场太好听了", "@音乐现场", ResolutionPreset.UP_TO_1080P, 21_400_000L, "2026-07-15 22:31"),
        ).forEach { container.downloads.archiveTerminal(it) }
    }

    private fun media(
        id: String,
        title: String,
        creator: String,
        platform: DownloadPlatform,
        thumbnailUrl: String,
    ) = MediaItem(
        mediaKey = "${platform.name}:$id",
        platform = platform,
        contentId = id,
        originalUrl = "https://example.com/$id",
        sourceKind = DownloadSourceKind.SINGLE_VIDEO,
        title = title,
        creator = creator,
        creatorId = creator,
        publishDate = "2026-07-16",
        thumbnailUrl = thumbnailUrl,
    )

    private fun history(
        taskId: String,
        platform: DownloadPlatform,
        title: String,
        creator: String,
        resolution: ResolutionPreset,
        fileSize: Long,
        completedAt: String,
    ) = DownloadHistory(
        taskId = taskId,
        platform = platform,
        contentId = taskId,
        originalUrl = "https://example.com/$taskId",
        title = title,
        creator = creator,
        resolution = resolution,
        finalStatus = DownloadTaskStatus.COMPLETED,
        outputUri = null,
        fileSize = fileSize,
        fileExists = false,
        completedAt = requireNotNull(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(completedAt),
        ).time,
    )
}
