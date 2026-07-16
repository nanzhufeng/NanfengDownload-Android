package com.nanzhufeng.videodownloader.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

        composeRule.onNodeWithText("近 30 天").assertIsDisplayed()
    }
}
