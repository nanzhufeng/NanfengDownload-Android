package com.nanzhufeng.videodownloader.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.nanzhufeng.videodownloader.data.settings.AppSettings
import com.nanzhufeng.videodownloader.domain.session.SessionSite
import com.nanzhufeng.videodownloader.domain.session.SiteSessionState
import org.junit.Assert.assertTrue
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

        val youtube = composeRule.onNodeWithTag("settings-youtube").assertIsDisplayed()
        val shortVideoPlatforms = composeRule.onNodeWithTag("settings-short-video-platforms").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-douyin").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-tiktok").assertIsDisplayed()

        assertTrue(
            "YouTube must appear before the grouped short-video platforms",
            youtube.getBoundsInRoot().top < shortVideoPlatforms.getBoundsInRoot().top,
        )
    }

    @Test
    fun expandedSettings_usesDedicatedTwoColumnGrid() {
        composeRule.setContent {
            SettingsScreen(settings(), sessions(), {}, {}, {}, {}, {}, expanded = true)
        }

        val accountCardContent = composeRule.onNodeWithTag("settings-youtube").assertIsDisplayed()
        val qualityCard = composeRule.onNodeWithTag("settings-quality-card").assertIsDisplayed()

        assertTrue(
            "The account card must span both grid columns",
            accountCardContent.getBoundsInRoot().run { right - left } >
                qualityCard.getBoundsInRoot().run { right - left } * 1.5f,
        )

        composeRule.onNodeWithTag("settings-expanded-grid").performScrollToNode(hasTestTag("settings-storage-card"))
        composeRule.onNodeWithTag("settings-storage-card").assertIsDisplayed()
    }

    private fun settings() = AppSettings()

    private fun sessions() = listOf(
        SiteSessionState(SessionSite.YOUTUBE, false, "未保存登录会话"),
        SiteSessionState(SessionSite.DOUYIN, false, "未保存登录会话"),
        SiteSessionState(SessionSite.TIKTOK, false, "未保存登录会话"),
    )
}
