package com.nanzhufeng.videodownloader.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport
import com.nanzhufeng.videodownloader.core.model.TransferReportOutcome
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun outerScreen_showsPlatformAndTimeFiltersWithoutStatusFilters() {
        composeRule.setContent { HistoryScreen(history = emptyList(), onDeleteRecord = {}) }

        composeRule.onNodeWithTag("history-platform-time-filters").assertIsDisplayed()
        composeRule.onNodeWithText("全部平台").assertIsDisplayed()
        composeRule.onNodeWithText("全部时间").assertIsDisplayed()
        val platformBounds = composeRule.onNodeWithTag("history-platform-filter").getBoundsInRoot()
        val periodBounds = composeRule.onNodeWithTag("history-period-filter").getBoundsInRoot()
        val platformCenter = (platformBounds.top.value + platformBounds.bottom.value) / 2f
        val periodCenter = (periodBounds.top.value + periodBounds.bottom.value) / 2f
        assertTrue("平台和时间筛选应保持在同一行", kotlin.math.abs(platformCenter - periodCenter) < 1f)
        composeRule.onNodeWithTag("history-period-filter").performClick()
        composeRule.onNodeWithText("近 30 天").assertIsDisplayed()
        composeRule.onNodeWithText("只展示已完成的下载记录").assertDoesNotExist()
        composeRule.onNodeWithText("平台").assertDoesNotExist()
        composeRule.onNodeWithText("时间").assertDoesNotExist()
        composeRule.onNodeWithText("已跳过").assertDoesNotExist()
        composeRule.onNodeWithText("已取消").assertDoesNotExist()
    }

    @Test
    fun completedRecord_showsOverflowActionsOnlyAfterOpeningMenu() {
        composeRule.setContent {
            HistoryScreen(
                history = listOf(
                    DownloadHistory(
                        taskId = "completed",
                        platform = DownloadPlatform.YOUTUBE,
                        contentId = "content",
                        originalUrl = "https://example.com/video",
                        title = "已完成的视频",
                        creator = "南烛枫",
                        resolution = ResolutionPreset.UP_TO_1080P,
                        finalStatus = DownloadTaskStatus.COMPLETED,
                        outputUri = null,
                        fileSize = 1_024L,
                        fileExists = false,
                        completedAt = 1_700_000_000_000L,
                    ),
                ),
                onDeleteRecord = {},
            )
        }

        composeRule.onNodeWithText("复制原链接").assertDoesNotExist()
        composeRule.onNodeWithText("分享原链接").assertDoesNotExist()
        composeRule.onNodeWithText("删除历史记录").assertDoesNotExist()

        composeRule.onNodeWithTag("history-overflow-completed").performClick()

        composeRule.onNodeWithText("复制原链接").assertIsDisplayed()
        composeRule.onNodeWithText("分享原链接").assertIsDisplayed()
        composeRule.onNodeWithText("删除历史记录").assertIsDisplayed()
    }

    @Test
    fun batchDelete_selectAllConfirmsVisibleHistoryRecords() {
        var deletedIds = emptyList<String>()
        composeRule.setContent {
            HistoryScreen(
                history = listOf(
                    completedHistory("history-1", "第一条历史"),
                    completedHistory("history-2", "第二条历史"),
                ),
                onDeleteRecord = {},
                onDeleteRecords = { deletedIds = it },
            )
        }

        composeRule.onNodeWithTag("history-bulk-delete").performClick()
        composeRule.onNodeWithTag("history-bulk-toolbar").assertIsDisplayed()
        composeRule.onNodeWithTag("history-select-history-1").performClick()
        composeRule.onNodeWithText("已选 1 项").assertIsDisplayed()
        val selectorBounds = composeRule.onNodeWithTag("history-select-history-1").getBoundsInRoot()
        val cardBounds = composeRule.onNodeWithTag("history-card-history-1").getBoundsInRoot()
        val selectorCenter = (selectorBounds.top.value + selectorBounds.bottom.value) / 2f
        val cardCenter = (cardBounds.top.value + cardBounds.bottom.value) / 2f
        assertTrue("左侧选择勾号应与历史卡片垂直居中", kotlin.math.abs(selectorCenter - cardCenter) < 4f)
        assertTrue("选择勾号应单独位于历史卡片左侧", selectorBounds.right.value <= cardBounds.left.value)
        composeRule.onNodeWithTag("history-select-all").performClick()
        composeRule.onNodeWithText("已选 2 项").assertIsDisplayed()
        composeRule.onNodeWithTag("history-delete-selected").performClick()
        composeRule.onNodeWithText("将删除选中的 2 条历史记录，不会删除已经保存的媒体文件。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("history-confirm-bulk-delete").performClick()

        composeRule.runOnIdle {
            assertTrue(deletedIds.toSet() == setOf("history-1", "history-2"))
        }
        composeRule.onNodeWithTag("history-bulk-toolbar").assertDoesNotExist()
    }

    @Test
    fun completedRecordShowsExplicitConnectionModeAndPermanentReportDetails() {
        composeRule.setContent {
            HistoryScreen(
                history = listOf(completedHistory("report-task", "真实下载")),
                throughputReports = listOf(
                    DownloadThroughputReport(
                        reportId = "report-1",
                        taskId = "report-task",
                        platform = DownloadPlatform.YOUTUBE,
                        streamLabel = "视频流",
                        outcome = TransferReportOutcome.COMPLETED,
                        connectionMode = DownloadConnectionMode.MULTI,
                        connectionCount = 6,
                        rangeSupported = true,
                        expectedBytes = 20_000_000L,
                        committedBytes = 20_000_000L,
                        networkBytes = 20_000_000L,
                        startedAt = 100L,
                        finishedAt = 2_100L,
                        elapsedMillis = 2_000L,
                        averageBytesPerSecond = 10_000_000L,
                        peakBytesPerSecond = 13_000_000L,
                        retryCount = 0,
                        reprobeCount = 1,
                        fallbackReason = null,
                        errorSummary = null,
                    ),
                ),
                onDeleteRecord = {},
            )
        }

        composeRule.onNodeWithText("多连接 ×6", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("history-overflow-report-task").performClick()
        composeRule.onNodeWithText("查看吞吐报告").performClick()
        composeRule.onNodeWithText("真实吞吐报告").assertIsDisplayed()
        composeRule.onNodeWithText("Range：已验证支持").assertIsDisplayed()
        composeRule.onNodeWithText("重新探测：1 次", substring = true).assertIsDisplayed()
    }

    @Test
    fun completedVideo_showsThumbnailAndCardOpensActionDetails() {
        composeRule.setContent {
            HistoryScreen(
                history = listOf(
                    completedHistory("interactive", "可播放的视频").copy(
                        outputUri = "content://media/external/video/media/1",
                        fileExists = true,
                        thumbnailUrl = "https://example.com/thumbnail.jpg",
                    ),
                ),
                onDeleteRecord = {},
            )
        }

        composeRule.onNodeWithTag("history-thumbnail-interactive").assertIsDisplayed()
        composeRule.onNodeWithText("时长", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("history-card-interactive").performClick()
        composeRule.onNodeWithText("视频详情").assertIsDisplayed()
        composeRule.onNodeWithText("视频时长：", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("文件大小：1.0 KB（1,024 字节）").assertIsDisplayed()
        composeRule.onNodeWithText("默认播放器播放").assertIsDisplayed()
        composeRule.onNodeWithText("选择播放器").assertIsDisplayed()
    }

    @Test
    fun completedAudio_thumbnailOpensBuiltInPlayerWithoutExternalChooser() {
        composeRule.setContent {
            HistoryScreen(
                history = listOf(
                    completedHistory("audio", "可播放的音频").copy(
                        resolution = ResolutionPreset.AUDIO_MP3,
                        outputUri = "content://media/external/audio/media/1",
                        fileExists = true,
                    ),
                ),
                onDeleteRecord = {},
            )
        }

        composeRule.onNodeWithTag("history-thumbnail-audio").performClick()
        composeRule.onNodeWithTag("internal-audio-player").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放音频").assertIsDisplayed()
        composeRule.onNodeWithText("选择播放器").assertDoesNotExist()
    }

    @Test
    fun segmentedAudio_staysOneHistoryCardAndPlayerCanNavigateSegments() {
        composeRule.setContent {
            HistoryScreen(
                history = listOf(
                    completedHistory("segmented-audio", "分段长音频").copy(
                        resolution = ResolutionPreset.AUDIO_MP3,
                        outputUri = "content://media/external/audio/media/1",
                        outputUris = listOf(
                            "content://media/external/audio/media/1",
                            "content://media/external/audio/media/2",
                            "content://media/external/audio/media/3",
                        ),
                        audioSegmentCount = 3,
                        fileExists = true,
                    ),
                ),
                onDeleteRecord = {},
            )
        }

        composeRule.onNodeWithText("共 3 段", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("history-thumbnail-segmented-audio").performClick()
        composeRule.onNodeWithText("正在播放音频 · 第 1/3 段").assertIsDisplayed()
        composeRule.onNodeWithText("下一段").performClick()
        composeRule.onNodeWithText("正在播放音频 · 第 2/3 段").assertIsDisplayed()
    }

    @Test
    fun segmentedVideo_staysOneHistoryCardAndLetsUserChooseEachPlayablePart() {
        composeRule.setContent {
            HistoryScreen(
                history = listOf(
                    completedHistory("segmented-video", "分段长视频").copy(
                        outputUri = "content://media/external/video/media/1",
                        outputUris = listOf(
                            "content://media/external/video/media/1",
                            "content://media/external/video/media/2",
                            "content://media/external/video/media/3",
                        ),
                        audioSegmentCount = 3,
                        fileExists = true,
                    ),
                ),
                onDeleteRecord = {},
            )
        }

        composeRule.onNodeWithText("共 3 段", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("history-thumbnail-segmented-video").performClick()
        composeRule.onNodeWithText("选择视频分段").assertIsDisplayed()
        composeRule.onNodeWithTag("video-segment-play-1").assertIsDisplayed()
        composeRule.onNodeWithTag("video-segment-play-3").assertIsDisplayed()
    }

    @Test
    fun innerScreen_arrangesCompletedTimelineCardsInTwoColumns() {
        composeRule.setContent {
            Box(Modifier.requiredWidth(700.dp).height(800.dp)) {
                HistoryScreen(
                    history = listOf(
                        completedHistory("first", "内屏第一条"),
                        completedHistory("second", "内屏第二条"),
                    ),
                    onDeleteRecord = {},
                )
            }
        }

        composeRule.onNodeWithTag("history-expanded-timeline").assertIsDisplayed()
        val firstBounds = composeRule.onNodeWithTag("history-card-first").getBoundsInRoot()
        val secondBounds = composeRule.onNodeWithTag("history-card-second").getBoundsInRoot()
        assertTrue("内屏完成卡必须并列为双栏", firstBounds.left < secondBounds.left)
        assertTrue("同一日期轨道下的完成卡应保持同一行", firstBounds.top == secondBounds.top)
    }

    private fun completedHistory(taskId: String, title: String) = DownloadHistory(
        taskId = taskId,
        platform = DownloadPlatform.YOUTUBE,
        contentId = taskId,
        originalUrl = "https://example.com/$taskId",
        title = title,
        creator = "南烛枫",
        resolution = ResolutionPreset.UP_TO_1080P,
        finalStatus = DownloadTaskStatus.COMPLETED,
        outputUri = null,
        fileSize = 1_024L,
        fileExists = false,
        completedAt = 1_700_000_000_000L,
    )
}
