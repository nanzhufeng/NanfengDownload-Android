package com.nanzhufeng.videodownloader.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText("默认规则用于之后新加入的任务，登录会话保存在本机。").assertDoesNotExist()

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
        val screenHeight = screenBounds.run { bottom - top }
        val accountCardHeight = accountCardBounds.run { bottom - top }
        assertTrue(
            "The account card must not occupy more than 29% of the outer screen " +
                "(account=$accountCardHeight, screen=$screenHeight, ratio=${accountCardHeight / screenHeight})",
            accountCardHeight <= screenHeight * 0.29f,
        )
        assertTrue(
            "The compact quality card must begin in the first 45% of the outer screen",
            qualityCardBounds.top <= (screenBounds.bottom - screenBounds.top) * 0.45f,
        )
        val qualityCardHeight = qualityCardBounds.run { bottom - top }
        assertTrue(
            "The quality card must not occupy more than 29% of the outer screen " +
                "(quality=$qualityCardHeight, screen=$screenHeight, ratio=${qualityCardHeight / screenHeight})",
            qualityCardHeight <= screenHeight * 0.29f,
        )
    }

    @Test
    fun formerlyStaticSettingsOpenRealChoicesAndReportTheSelection() {
        var concurrentDownloads = 1
        var fileNameRule = com.nanzhufeng.videodownloader.data.settings.FileNameRule.DATE_AND_TITLE
        composeRule.setContent {
            SettingsScreen(
                settings = settings(),
                sessions = sessions(),
                onResolutionSelected = {},
                onAutoResumeChanged = {},
                onOpenLogin = {},
                onImportYoutubeCookies = {},
                onClearSession = {},
                onMaxConcurrentDownloadsSelected = { concurrentDownloads = it },
                onFileNameRuleSelected = { fileNameRule = it },
                expanded = false,
            )
        }

        composeRule.onNodeWithTag("settings-screen")
            .performScrollToNode(hasTestTag("settings-concurrency"))
        composeRule.onNodeWithTag("settings-concurrency").performClick()
        composeRule.onNodeWithText("3 个任务").performClick()
        composeRule.runOnIdle { assertTrue(concurrentDownloads == 3) }

        composeRule.onNodeWithTag("settings-file-name").performClick()
        composeRule.onNodeWithText("作者 + 视频标题").performClick()
        composeRule.runOnIdle {
            assertTrue(
                fileNameRule == com.nanzhufeng.videodownloader.data.settings.FileNameRule.CREATOR_AND_TITLE,
            )
        }
    }

    @Test
    fun anonymousBrowserCookiesDoNotClaimSuccessfulLogin() {
        composeRule.setContent {
            SettingsScreen(
                settings = settings(),
                sessions = listOf(
                    SiteSessionState(SessionSite.YOUTUBE, false, "未保存登录会话"),
                    SiteSessionState(
                        SessionSite.DOUYIN,
                        true,
                        "已保存网页会话，尚未确认登录",
                        isAuthenticated = false,
                    ),
                    SiteSessionState(
                        SessionSite.TIKTOK,
                        true,
                        "已检测到登录 Cookie，使用时仍会验证",
                        isAuthenticated = true,
                    ),
                ),
                onResolutionSelected = {},
                onAutoResumeChanged = {},
                onOpenLogin = {},
                onImportYoutubeCookies = {},
                onClearSession = {},
                expanded = false,
            )
        }

        composeRule.onNodeWithText("继续登录").assertIsDisplayed()
        composeRule.onNodeWithText("重新登录").assertIsDisplayed()
    }

    private fun settings() = AppSettings()

    private fun sessions() = listOf(
        SiteSessionState(SessionSite.YOUTUBE, false, "未保存登录会话"),
        SiteSessionState(SessionSite.DOUYIN, false, "未保存登录会话"),
        SiteSessionState(SessionSite.TIKTOK, false, "未保存登录会话"),
    )
}
