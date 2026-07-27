package com.nanzhufeng.videodownloader.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadConnectionMode
import com.nanzhufeng.videodownloader.core.model.DownloadFailureType
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.AppSettings
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.feature.home.HomeScreen
import com.nanzhufeng.videodownloader.core.ui.NanzhufengTheme
import com.nanzhufeng.videodownloader.domain.discovery.DiscoveryResult
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals

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
    fun compactNavigationUsesPureWhiteBarAndDistinctSelectedModule() {
        composeRule.setContent { app(expanded = false) }
        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.waitForIdle()

        val navigationPixels = composeRule.onNodeWithTag("bottom-navigation")
            .captureToImage()
            .toPixelMap()
        val selectedPixels = composeRule.onNodeWithTag("nav-settings")
            .captureToImage()
            .toPixelMap()
        val navigationSurface = navigationPixels[4, 4]
        val selectedSurface = selectedPixels[
            (selectedPixels.width * 0.16f).toInt(),
            (selectedPixels.height * 0.5f).toInt(),
        ]

        assertTrue(
            "The compact navigation carrier must be pure white",
            navigationSurface.red > 0.98f &&
                navigationSurface.green > 0.98f &&
                navigationSurface.blue > 0.98f,
        )
        assertTrue(
            "The selected destination must have a visible sage module surface",
            selectedSurface.green > selectedSurface.red + 0.03f &&
                selectedSurface.green > selectedSurface.blue + 0.02f,
        )
    }

    @Test
    fun compactHeaderAndHistoryRemoveRedundantStatusAndManageCopy() {
        composeRule.setContent { app(expanded = false) }

        composeRule.onNodeWithText("网络良好").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-history").performClick()
        composeRule.onNodeWithText("管理").assertDoesNotExist()
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
        val progress = composeRule.onNodeWithTag("home-side-total-progress").assertIsDisplayed().getBoundsInRoot()
        val quality = composeRule.onNodeWithTag("home-side-download-quality").assertIsDisplayed().getBoundsInRoot()
        assertTrue("任务总进度应排在下载质量上方", progress.top < quality.top)
        assertTrue("下载质量应排在添加任务上方", quality.top < taskSidebar.top)

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
    fun clearButtonImmediatelyClearsTheSavedLinkInput() {
        composeRule.setContent {
            NanzhufengApp(
                downloads = FakeDownloadRepository(),
                settings = FakeSettingsRepository(),
                discovery = NeverReadDiscovery(),
                expandedOverride = false,
            )
        }

        composeRule.onNodeWithTag("home-input").performTextInput("https://youtu.be/test")
        composeRule.onNodeWithTag("clear-input").performClick()

        composeRule.onNodeWithTag("home-input").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    @Test
    fun compactHomeUsesReferenceRunStatusQueueAndReadEntryOrder() {
        composeRule.setContent {
            NanzhufengApp(
                downloads = FakeDownloadRepository(),
                settings = FakeSettingsRepository(),
                discovery = NeverReadDiscovery(),
                expandedOverride = false,
            )
        }

        composeRule.onNodeWithTag("formal-queue-tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("home-compact-run-status").assertIsDisplayed()
        composeRule.onNodeWithTag("formal-read-entry").assertIsDisplayed()
    }

    @Test
    fun compactHome_showsHonestActiveEmptyStateAndReadEntry() {
        composeRule.setContent { app(expanded = false) }

        composeRule.onNodeWithText("暂无下载任务").assertIsDisplayed()
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

    @Test
    fun compactEmptyQueue_fillsTheDedicatedWorkspaceAboveTheFixedAddTaskCard() {
        composeRule.setContent {
            Box(Modifier.requiredWidth(360.dp).height(800.dp)) {
                HomeScreen(
                    queue = emptyList(),
                    input = "",
                    onInputChange = {},
                    onSmartRead = {},
                )
            }
        }

        val queueHeight = composeRule.onNodeWithTag("formal-queue-tabs")
            .assertIsDisplayed()
            .getBoundsInRoot()
            .let { bounds -> bounds.bottom - bounds.top }
        val screenHeight = composeRule.onNodeWithTag("home-screen")
            .getBoundsInRoot()
            .let { bounds -> bounds.bottom - bounds.top }

        assertTrue(
            "外屏空队列必须占用添加任务之上的完整工作区",
            queueHeight >= screenHeight * 0.42f,
        )
    }

    @Test
    fun queueSelectionAndStartActionAreDirectWithoutASecondBatchMode() {
        var deletedTaskId: String? = null
        composeRule.setContent {
            NanzhufengTheme {
                Box(Modifier.requiredWidth(360.dp).height(800.dp)) {
                    HomeScreen(
                        queue = listOf(referenceQueueItem()),
                        input = "",
                        onInputChange = {},
                        onSmartRead = {},
                        onDeleteQueued = { deletedTaskId = it },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("queue-select-page").assertIsDisplayed()
        composeRule.onNodeWithTag("queue-selection-actions").assertDoesNotExist()
        composeRule.onNodeWithTag("queue-start-selected").assertIsDisplayed()
        composeRule.onNodeWithTag("resolution-badge-AUDIO_MP3").assertIsDisplayed()
        composeRule.onNodeWithText("等待下载").assertIsDisplayed()
        composeRule.onNodeWithText("批量管理").assertDoesNotExist()
        composeRule.onNodeWithText("完成").assertDoesNotExist()
        composeRule.onNodeWithTag("queue-delete-queue-reference").performClick()
        composeRule.runOnIdle { assertEquals("queue-reference", deletedTaskId) }
    }

    @Test
    fun waitingAudioTaskLetsUserChooseExactSegmentCountInline() {
        var selectedTaskId: String? = null
        var selectedSegmentCount: Int? = null
        composeRule.setContent {
            NanzhufengTheme {
                Box(Modifier.requiredWidth(360.dp).height(800.dp)) {
                    HomeScreen(
                        queue = listOf(referenceQueueItem()),
                        input = "",
                        onInputChange = {},
                        onSmartRead = {},
                        onAudioSegmentCountChanged = { taskId, segmentCount ->
                            selectedTaskId = taskId
                            selectedSegmentCount = segmentCount
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("audio-segment-count").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("均分为 4 段").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("queue-reference", selectedTaskId)
            assertEquals(4, selectedSegmentCount)
        }
    }

    @Test
    fun compactQueueShowsMoreRowsAndAVisibleScrollPositionWithoutRedundantTabs() {
        composeRule.setContent {
            NanzhufengTheme {
                Box(Modifier.requiredWidth(360.dp).height(800.dp)) {
                    HomeScreen(
                        queue = List(8) { index ->
                            referenceQueueItem().copy(
                                task = referenceQueueItem().task.copy(taskId = "queue-$index", sortOrder = index.toLong()),
                                media = referenceQueueItem().media.copy(mediaKey = "youtube:queue-$index", title = "队列视频 $index"),
                            )
                        },
                        input = "",
                        onInputChange = {},
                        onSmartRead = {},
                        notice = "已加入 8 个作品，请在下载列表中确认后开始下载",
                    )
                }
            }
        }

        composeRule.onNodeWithTag("queue-scroll-indicator").assertIsDisplayed()
        composeRule.onNodeWithText("等待网络(0)").assertDoesNotExist()
        composeRule.onNodeWithText("已跳过(0)").assertDoesNotExist()
        composeRule.onNodeWithText("已加入 8 个作品，请在下载列表中确认后开始下载").assertDoesNotExist()
    }

    @Test
    fun activeQueueItemAutoScrollsIntoViewAndShowsDetailedTransferMetrics() {
        val active = referenceQueueItem().copy(
            task = referenceQueueItem().task.copy(
                taskId = "active-row",
                status = DownloadTaskStatus.DOWNLOADING,
                downloadedBytes = 52L * 1024L * 1024L,
                totalBytes = 100L * 1024L * 1024L,
                speedBytesPerSecond = 2L * 1024L * 1024L,
                remainingSeconds = 24L,
                updatedAt = System.currentTimeMillis(),
                connectionMode = DownloadConnectionMode.MULTI,
                connectionCount = 6,
            ),
            media = referenceQueueItem().media.copy(title = "当前正在下载的视频"),
        )
        val queue = List(8) { index ->
            if (index == 6) {
                active
            } else {
                referenceQueueItem().copy(
                    task = referenceQueueItem().task.copy(taskId = "waiting-$index", sortOrder = index.toLong()),
                    media = referenceQueueItem().media.copy(mediaKey = "youtube:waiting-$index", title = "等待视频 $index"),
                )
            }
        }

        composeRule.setContent {
            NanzhufengTheme {
                Box(Modifier.requiredWidth(360.dp).height(800.dp)) {
                    HomeScreen(queue = queue, input = "", onInputChange = {}, onSmartRead = {})
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue-row-active-row").assertIsDisplayed()
        composeRule.onNodeWithTag("queue-active-detail-active-row", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("速度 2.0 MB/s").assertIsDisplayed()
        composeRule.onNodeWithText("已下载 52.0 MB / 100.0 MB").assertIsDisplayed()
        composeRule.onNodeWithText("剩余 00:24 · 多连接 ×6").assertIsDisplayed()
    }

    @Test
    fun addTaskKeepsCountInsideTheInputAndLinkIconCopiesTheText() {
        composeRule.setContent {
            NanzhufengTheme {
                Box(Modifier.requiredWidth(360.dp).height(800.dp)) {
                    HomeScreen(
                        queue = emptyList(),
                        input = "https://example.com/video",
                        onInputChange = {},
                        onSmartRead = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("input-character-count", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("copy-input").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("开始下载已选作品").assertDoesNotExist()
    }

    @Test
    fun queueOffers360pAsACompactColoredPreset() {
        val item = referenceQueueItem().copy(
            task = referenceQueueItem().task.copy(resolution = ResolutionPreset.UP_TO_360P),
        )
        composeRule.setContent {
            NanzhufengTheme {
                HomeScreen(queue = listOf(item), input = "", onInputChange = {}, onSmartRead = {})
            }
        }

        composeRule.onNodeWithTag("resolution-badge-UP_TO_360P").assertIsDisplayed()
    }

    @Test
    fun failedQueueItemKeepsItsRowShowsTheErrorAndCanRetryInPlace() {
        var retriedTaskId: String? = null
        val waiting = referenceQueueItem().copy(
            task = referenceQueueItem().task.copy(taskId = "waiting-row"),
        )
        val failed = referenceQueueItem().copy(
            task = referenceQueueItem().task.copy(
                taskId = "failed-row",
                status = DownloadTaskStatus.FAILED,
                failureType = DownloadFailureType.TRANSFER,
                errorSummary = "unexpected end of stream",
            ),
        )
        composeRule.setContent {
            NanzhufengTheme {
                HomeScreen(
                    queue = listOf(waiting, failed),
                    input = "",
                    onInputChange = {},
                    onSmartRead = {},
                    onRetryQueued = { retriedTaskId = it },
                )
            }
        }

        composeRule.onNodeWithText("失败：网络传输中断", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("解决办法", substring = true).assertIsDisplayed()
        val waitingHeight = composeRule.onNodeWithTag("queue-row-waiting-row").getBoundsInRoot().run { bottom - top }
        val failedHeight = composeRule.onNodeWithTag("queue-row-failed-row").getBoundsInRoot().run { bottom - top }
        assertEquals("状态变化不能导致队列行高度跳动", waitingHeight, failedHeight)
        composeRule.onNodeWithTag("queue-retry-failed-row").performClick()
        composeRule.runOnIdle { assertEquals("failed-row", retriedTaskId) }
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

private fun referenceQueueItem(): QueuedDownload = QueuedDownload(
    task = DownloadTask(
        taskId = "queue-reference",
        mediaKey = "youtube:queue-reference",
        selected = true,
        sortOrder = 1L,
        resolution = ResolutionPreset.AUDIO_MP3,
        saveTreeUri = null,
        downloadedBytes = 0L,
        totalBytes = 0L,
        speedBytesPerSecond = 0L,
        remainingSeconds = null,
        status = DownloadTaskStatus.WAITING,
        failureType = null,
        errorSummary = null,
        retryCount = 0,
        updatedAt = 0L,
    ),
    media = MediaItem(
        mediaKey = "youtube:queue-reference",
        platform = DownloadPlatform.YOUTUBE,
        contentId = "queue-reference",
        originalUrl = "https://example.com/queue-reference",
        sourceKind = DownloadSourceKind.SINGLE_VIDEO,
        title = "Me at the zoo",
        creator = "jawed",
        creatorId = "jawed",
        publishDate = "2026-07-16",
        thumbnailUrl = "",
    ),
)

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
        audioSegmentCount: Int,
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
