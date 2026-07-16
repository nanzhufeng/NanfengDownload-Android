package com.nanzhufeng.videodownloader.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class HistoryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun outerScreen_keepsAllFilterGroupsVisibleWithoutHorizontalScrolling() {
        composeRule.setContent {
            Box(Modifier.width(380.dp).height(800.dp)) {
                HistoryScreen(
                    history = emptyList(),
                    onRetry = {},
                    onDeleteRecord = {},
                )
            }
        }

        composeRule.onNodeWithTag("history-filters").assertIsDisplayed()
        composeRule.onNodeWithText("全部").assertIsDisplayed()
        composeRule.onNodeWithText("全部平台").assertIsDisplayed()
        composeRule.onNodeWithText("近 30 天").assertIsDisplayed()
    }
}
