package com.nanzhufeng.videodownloader.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
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
        composeRule.onNodeWithText("近 30 天").assertIsDisplayed()
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
}
