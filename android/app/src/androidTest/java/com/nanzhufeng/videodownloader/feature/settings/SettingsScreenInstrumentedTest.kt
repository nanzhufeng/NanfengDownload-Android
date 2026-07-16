package com.nanzhufeng.videodownloader.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.nanzhufeng.videodownloader.data.settings.AppSettings
import com.nanzhufeng.videodownloader.domain.session.SessionSite
import com.nanzhufeng.videodownloader.domain.session.SiteSessionState
import org.junit.Rule
import org.junit.Test

class SettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accounts_putYoutubeBeforeGroupedShortVideoPlatforms() {
        composeRule.setContent {
            SettingsScreen(settings(), sessions(), {}, {}, {}, {}, {}, expanded = false)
        }

        composeRule.onNodeWithTag("settings-youtube").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-short-video-platforms").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-douyin").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-tiktok").assertIsDisplayed()
    }

    @Test
    fun expandedSettings_usesDedicatedTwoColumnGrid() {
        composeRule.setContent {
            SettingsScreen(settings(), sessions(), {}, {}, {}, {}, {}, expanded = true)
        }

        composeRule.onNodeWithTag("settings-expanded-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-quality-card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-expanded-grid").performScrollToIndex(4)
        composeRule.onNodeWithTag("settings-storage-card").assertIsDisplayed()
    }

    private fun settings() = AppSettings()

    private fun sessions() = listOf(
        SiteSessionState(SessionSite.YOUTUBE, false, "未保存登录会话"),
        SiteSessionState(SessionSite.DOUYIN, false, "未保存登录会话"),
        SiteSessionState(SessionSite.TIKTOK, false, "未保存登录会话"),
    )
}
