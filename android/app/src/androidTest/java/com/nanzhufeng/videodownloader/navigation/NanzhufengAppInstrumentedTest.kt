package com.nanzhufeng.videodownloader.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.AppSettings
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveryResult
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class NanzhufengAppInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactNavigationOpensOnlyHomeHistoryAndSettings() {
        composeRule.setContent { app(expanded = false) }

        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation-rail").assertDoesNotExist()

        composeRule.onNodeWithTag("nav-history").performClick()
        composeRule.onNodeWithTag("history-screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("settings-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("probe-entry").assertDoesNotExist()
        composeRule.onNodeWithTag("probe-screen").assertDoesNotExist()
    }

    @Test
    fun compactNavigation_exposesSelectedDestinationState() {
        composeRule.setContent { app(expanded = false) }

        composeRule.onNodeWithTag("nav-home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-history").performClick()
        composeRule.onNodeWithTag("nav-history").assert(hasStateDescription("已选中"))
    }

    @Test
    fun expandedNavigationUsesRailInsteadOfBottomBar() {
        composeRule.setContent {
            NanzhufengApp(
                downloads = FakeDownloadRepository(),
                settings = FakeSettingsRepository(),
                discovery = NeverReadDiscovery(),
                expandedOverride = true,
            )
        }

        composeRule.onNodeWithTag("navigation-rail").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-navigation").assertDoesNotExist()
    }

    @Test
    fun expandedHomeUsesQueueWorkspaceAndCompactTaskSidebar() {
        composeRule.setContent {
            NanzhufengApp(
                downloads = FakeDownloadRepository(),
                settings = FakeSettingsRepository(),
                discovery = NeverReadDiscovery(),
                expandedOverride = true,
            )
        }

        val queueWorkspace = composeRule.onNodeWithTag("home-main-queue-workspace")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val taskSidebar = composeRule.onNodeWithTag("home-side-add-task")
            .assertIsDisplayed()
            .getBoundsInRoot()
        composeRule.onNodeWithTag("home-side-quality-progress").assertIsDisplayed()

        assertTrue(
            "The main queue workspace must remain left of the compact task sidebar",
            queueWorkspace.right <= taskSidebar.left,
        )
    }

    @Test
    fun smartReadStaysOnHomeAndNeverShowsProbeControls() {
        composeRule.setContent {
            NanzhufengApp(
                downloads = FakeDownloadRepository(),
                settings = FakeSettingsRepository(),
                discovery = NeverReadDiscovery(),
                expandedOverride = false,
            )
        }

        val input = "https://www.tiktok.com/@creator/video/123456789"
        composeRule.onNodeWithTag("home-input").performTextInput(input)
        composeRule.onNodeWithTag("smart-read").performClick()
        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("probe-screen").assertDoesNotExist()
        composeRule.onNodeWithText("检查 Python/yt-dlp").assertDoesNotExist()
    }

    @Test
    fun compactHomeUsesFormalQueueProgressAndReadEntryOrder() {
        composeRule.setContent {
            NanzhufengApp(
                downloads = FakeDownloadRepository(),
                settings = FakeSettingsRepository(),
                discovery = NeverReadDiscovery(),
                expandedOverride = false,
            )
        }

        composeRule.onNodeWithTag("formal-queue-tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("formal-total-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("formal-read-entry").assertIsDisplayed()
    }

    @Test
    fun compactHome_showsHonestActiveEmptyStateAndReadEntry() {
        composeRule.setContent { app(expanded = false) }

        composeRule.onNodeWithText("当前没有下载任务").assertIsDisplayed()
        composeRule.onNodeWithTag("formal-queue-tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("formal-read-entry").assertIsDisplayed()
    }

    @Test
    fun compactHomeKeepsRunStatusAndQueueAheadOfReadEntry() {
        composeRule.setContent { app(expanded = false) }

        val runStatus = composeRule.onNodeWithTag("home-compact-run-status")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val queue = composeRule.onNodeWithTag("formal-queue-tabs")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val readEntry = composeRule.onNodeWithTag("formal-read-entry")
            .assertIsDisplayed()
            .getBoundsInRoot()

        assertTrue(
            "The outer-screen queue must stay ahead of the add-task action",
            runStatus.bottom <= queue.top && queue.bottom <= readEntry.top,
        )
    }

    @Composable
    private fun app(expanded: Boolean) {
        NanzhufengApp(
            downloads = FakeDownloadRepository(),
            settings = FakeSettingsRepository(),
            discovery = NeverReadDiscovery(),
            expandedOverride = expanded,
        )
    }
}

private class NeverReadDiscovery : SourceDiscoveryEngine {
    override suspend fun read(input: String, page: Int): DiscoveryResult =
        DiscoveryResult.Failure("测试读取失败")
}

private class FakeDownloadRepository : DownloadRepository {
    override val activeTasks: Flow<List<QueuedDownload>> = MutableStateFlow(emptyList())
    override val history: Flow<List<DownloadHistory>> = MutableStateFlow(emptyList())

    override suspend fun enqueue(
        items: List<MediaItem>,
        resolution: ResolutionPreset,
    ): List<String> = emptyList()

    override suspend fun setSelected(taskId: String, selected: Boolean) = Unit
    override suspend fun bulkSelect(taskIds: List<String>, selected: Boolean) = Unit
    override suspend fun setResolution(taskId: String, resolution: ResolutionPreset) = Unit
    override suspend fun nextSelectedWaiting(): QueuedDownload? = null
    override suspend fun updateTransfer(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        remainingSeconds: Long?,
    ) = Unit

    override suspend fun transition(taskId: String, to: DownloadTaskStatus) = Unit

    override suspend fun archiveTerminal(history: DownloadHistory) = Unit
    override suspend fun findCompleted(
        platform: com.nanzhufeng.videodownloader.core.model.DownloadPlatform,
        contentId: String,
        resolution: ResolutionPreset,
    ): DownloadHistory? = null
}

private class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state

    override suspend fun setDefaultResolution(value: ResolutionPreset) {
        state.value = state.value.copy(defaultResolution = value)
    }

    override suspend fun saveInputDraft(value: String) {
        state.value = state.value.copy(inputDraft = value)
    }
}
