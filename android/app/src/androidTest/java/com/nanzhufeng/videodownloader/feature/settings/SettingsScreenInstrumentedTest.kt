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
    fun expandedSettings_keepsAccountCardWithinOneGridColumn() {
        composeRule.setContent {
            SettingsScreen(settings(), sessions(), {}, {}, {}, {}, {}, expanded = true)
        }

        val accountCardContent = composeRule.onNodeWithTag("settings-youtube").assertIsDisplayed()
        val qualityCard = composeRule.onNodeWithTag("settings-quality-card").assertIsDisplayed()
        val accountWidth = accountCardContent.getBoundsInRoot().run { right - left }
        val qualityWidth = qualityCard.getBoundsInRoot().run { right - left }

        assertTrue(
            "The account card must stay within one expanded-grid column",
            accountWidth <= qualityWidth * 1.3f,
        )

        composeRule.onNodeWithTag("settings-expanded-grid").performScrollToNode(hasTestTag("settings-storage-card"))
        composeRule.onNodeWithTag("settings-storage-card").assertIsDisplayed()
    }

    @Test
    fun outerSettings_keepsAccountAndQualityCardsCompactOnFirstScreen() {
        composeRule.setContent {
            SettingsScreen(settings(), sessions(), {}, {}, {}, {}, {}, expanded = false)
        }

        val screenBounds = composeRule.onNodeWithTag("settings-screen").getBoundsInRoot()
        val accountCardBounds = composeRule.onNodeWithTag("settings-account-card")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val qualityCardBounds = composeRule.onNodeWithTag("settings-quality-card")
            .assertIsDisplayed()
            .getBoundsInRoot()
        assertTrue(
            "The account card must not occupy more than 28% of the outer screen",
            accountCardBounds.run { bottom - top } <= screenBounds.run { bottom - top } * 0.28f,
        )
        assertTrue(
            "The compact quality card must begin in the first 45% of the outer screen",
            qualityCardBounds.top <= (screenBounds.bottom - screenBounds.top) * 0.45f,
        )
        assertTrue(
            "The quality card must not occupy more than 28% of the outer screen",
            qualityCardBounds.run { bottom - top } <= screenBounds.run { bottom - top } * 0.28f,
        )
    }

    private fun settings() = AppSettings()

    private fun sessions() = listOf(
        SiteSessionState(SessionSite.YOUTUBE, false, "未保存登录会话"),
        SiteSessionState(SessionSite.DOUYIN, false, "未保存登录会话"),
        SiteSessionState(SessionSite.TIKTOK, false, "未保存登录会话"),
    )
}
