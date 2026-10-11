package com.nanzhufeng.videodownloader.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import android.app.Activity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nanzhufeng.videodownloader.AppContainer
import com.nanzhufeng.videodownloader.core.diagnostics.UserFacingErrorPresenter
import com.nanzhufeng.videodownloader.core.ui.NanzhufengTheme
import com.nanzhufeng.videodownloader.core.ui.SelectedSage
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.data.settings.FileNameRule
import com.nanzhufeng.videodownloader.feature.history.HistoryScreen
import com.nanzhufeng.videodownloader.feature.history.InternalVideoPlayerOverlay
import com.nanzhufeng.videodownloader.feature.home.HomeScreen
import com.nanzhufeng.videodownloader.feature.home.HomeViewModel
import com.nanzhufeng.videodownloader.feature.settings.SettingsScreen
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import com.nanzhufeng.videodownloader.domain.download.DownloadEngine
import com.nanzhufeng.videodownloader.domain.download.NoOpDownloadEngine
import com.nanzhufeng.videodownloader.domain.download.WebSourceRecoveryPolicy
import com.nanzhufeng.videodownloader.domain.session.NoOpSessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionSite
import com.nanzhufeng.videodownloader.domain.session.SiteSessionState
import com.nanzhufeng.videodownloader.probe.DouyinProbeActivity
import com.nanzhufeng.videodownloader.probe.GenericWebProbeActivity
import com.nanzhufeng.videodownloader.core.ui.IslandFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

@Composable
fun NanzhufengApp(
    container: AppContainer,
    incomingSharedText: Flow<String> = emptyFlow(),
    openDownloads: Flow<Unit> = emptyFlow(),
) {
    NanzhufengApp(
        downloads = container.downloads,
        settings = container.settings,
        discovery = container.discovery,
        downloadEngine = container.downloadEngine,
        networkAvailable = container.networkAvailable,
        sessions = container.sessions,
        incomingSharedText = incomingSharedText,
        openDownloads = openDownloads,
    )
}

@Composable
fun NanzhufengApp(
    downloads: DownloadRepository,
    settings: SettingsRepository,
    discovery: SourceDiscoveryEngine,
    downloadEngine: DownloadEngine = NoOpDownloadEngine,
    networkAvailable: Flow<Boolean> = flowOf(true),
    incomingSharedText: Flow<String> = emptyFlow(),
    openDownloads: Flow<Unit> = emptyFlow(),
    expandedOverride: Boolean? = null,
    sessions: SessionProvider = NoOpSessionProvider,
) {
    NanzhufengTheme {
        val queue by downloads.activeTasks.collectAsStateWithLifecycle(initialValue = emptyList())
        val history by downloads.history.collectAsStateWithLifecycle(initialValue = emptyList())
        val throughputReports by downloads.throughputReports.collectAsStateWithLifecycle(initialValue = emptyList())
        val appSettings by settings.settings.collectAsStateWithLifecycle(
            initialValue = com.nanzhufeng.videodownloader.data.settings.AppSettings(),
        )
        val sessionStates by sessions.states.collectAsStateWithLifecycle()
        var historyFavoritesOnly by rememberSaveable { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val homeViewModel: HomeViewModel = viewModel(
            factory = HomeViewModel.factory(downloads, settings, discovery),
        )
        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(homeState.input) {
            com.nanzhufeng.videodownloader.probe.DouyinReadPreparation.prepare(context, homeState.input)
            com.nanzhufeng.videodownloader.probe.UrlClassifier.extractUrls(homeState.input).forEach {
                com.nanzhufeng.videodownloader.probe.PornhubApiMetadata.prepareConnection(it)
                com.nanzhufeng.videodownloader.probe.YouTubePageMetadata.prepareConnection(it)
                com.nanzhufeng.videodownloader.probe.TikTokPageVideo.prepareConnection(it)
            }
        }
        val douyinCaptureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val media = if (result.resultCode == Activity.RESULT_OK) {
                DouyinProbeActivity.capturedMedia(result.data)
            } else {
                null
            }
            scope.launch {
                homeViewModel.completeDouyinCapture(
                    media = media,
                    errorMessage = DouyinProbeActivity.errorMessage(result.data),
                )
            }
        }
        val webCaptureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            scope.launch {
                homeViewModel.completeWebCapture(
                    capture = if (result.resultCode == Activity.RESULT_OK) {
                        GenericWebProbeActivity.capture(result.data)
                    } else null,
                    episodes = if (result.resultCode == Activity.RESULT_OK) {
                        GenericWebProbeActivity.episodes(result.data)
                    } else emptyList(),
                    pageUrl = GenericWebProbeActivity.page(result.data),
                    errorMessage = GenericWebProbeActivity.error(result.data),
                )
            }
        }
        val isNetworkAvailable by networkAvailable.collectAsStateWithLifecycle(initialValue = false)
        val navController = rememberNavController()
        val playbackMemory = remember(context.applicationContext) {
            com.nanzhufeng.videodownloader.domain.playback.VideoPlaybackMemory(
                com.nanzhufeng.videodownloader.data.playback.PreferencesVideoPositionStorage(
                    context.applicationContext.getSharedPreferences("video_playback_positions", android.content.Context.MODE_PRIVATE),
                ),
            )
        }
        var activeVideoTaskId by rememberSaveable { mutableStateOf<String?>(null) }
        var activeVideoUri by rememberSaveable { mutableStateOf<String?>(null) }
        var activeVideoTitle by rememberSaveable { mutableStateOf("") }
        var activeVideoPositionMillis by rememberSaveable { mutableStateOf(0L) }
        var activeVideoPlayWhenReady by rememberSaveable { mutableStateOf(true) }

        LaunchedEffect(downloads) {
            val notices = com.nanzhufeng.videodownloader.core.ui.DownloadCompletionNotices()
            downloads.history.collect { snapshot ->
                notices.onSnapshot(snapshot)?.let { notice ->
                    IslandFeedback.success(context, notice.detail, title = notice.title)
                }
            }
        }

        LaunchedEffect(downloads, downloadEngine) {
            // Read the initial persisted snapshot once. Later manual starts are
            // active downloads, rather than interrupted work requiring restart.
            val hasInterruptedTransfer = downloads.activeTasks.first().any { queued ->
                queued.task.status in setOf(
                    DownloadTaskStatus.PARSING,
                    DownloadTaskStatus.DOWNLOADING,
                    DownloadTaskStatus.VALIDATING,
                )
            }
            if (hasInterruptedTransfer) {
                downloadEngine.recoverInterruptedAtStartup()
            }
        }

        LaunchedEffect(incomingSharedText) {
            incomingSharedText.collect { value ->
                homeViewModel.onInputChanged(value)
                settings.saveInputDraft(value)
            }
        }

        LaunchedEffect(settings) {
            homeViewModel.restoreInputDraft(settings.settings.first().inputDraft)
        }

        LaunchedEffect(homeState.douyinCaptureUrl) {
            homeState.douyinCaptureUrl?.let { sourceUrl ->
                douyinCaptureLauncher.launch(
                    DouyinProbeActivity.createIntent(context, sourceUrl),
                    ActivityOptionsCompat.makeCustomAnimation(context, 0, 0),
                )
            }
        }
        LaunchedEffect(homeState.webCaptureUrl) {
            homeState.webCaptureUrl?.let { sourceUrl ->
                webCaptureLauncher.launch(
                    GenericWebProbeActivity.createIntent(context, sourceUrl),
                    ActivityOptionsCompat.makeCustomAnimation(context, 0, 0),
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val expanded = expandedOverride ?: (maxWidth >= 600.dp)
            LaunchedEffect(openDownloads) {
                openDownloads.collect { navController.openPrimary(AppDestination.HOME) }
            }
            val currentRoute by navController.currentBackStackEntryAsState()
            val route = currentRoute?.destination?.route ?: AppDestination.HOME.route

            if (expanded) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        navController = navController,
                        queue = queue,
                        history = history,
                        throughputReports = throughputReports,
                        settings = appSettings,
                        input = homeState.input,
                        isReading = homeState.isReading,
                        notice = homeState.notice,
                        canLoadMore = homeState.canLoadMore,
                        pendingItems = homeState.pendingItems,
                        onPendingSelected = homeViewModel::setPendingSelected,
                        pendingErrors = homeState.pendingErrors,
                        onConfirmPending = { scope.launch { homeViewModel.confirmPending() } },
                        onDismissPending = homeViewModel::dismissPending,
                        onInputChange = { value ->
                            homeViewModel.onInputChanged(value)
                            scope.launch { settings.saveInputDraft(value) }
                        },
                        onSmartRead = {
                            scope.launch { homeViewModel.smartRead() }
                        },
                        onLoadMore = { scope.launch { homeViewModel.loadMore() } },
                        onSelectionChanged = { taskId, selected ->
                            scope.launch { downloads.setSelected(taskId, selected) }
                        },
                        onBulkSelectionChanged = { taskIds, selected ->
                            scope.launch { downloads.bulkSelect(taskIds, selected) }
                        },
                        onItemResolutionChanged = { taskId, resolution ->
                            scope.launch { downloads.setResolution(taskId, resolution) }
                        },
                        onAudioSegmentCountChanged = { taskId, count ->
                            scope.launch { downloads.setAudioSegmentCount(taskId, count) }
                        },
                        onResolutionSelected = { resolution ->
                            scope.launch { settings.setDefaultResolution(resolution) }
                        },
                        sessionStates = sessionStates,
                        onAutoResumeChanged = { value ->
                            scope.launch { settings.setAutoResumeNetwork(value) }
                        },
                        onMaxConcurrentDownloadsSelected = { value ->
                            scope.launch { settings.setMaxConcurrentDownloads(value) }
                        },
                        onFileNameRuleSelected = { value ->
                            scope.launch { settings.setFileNameRule(value) }
                        },
                        onCustomTreeSelected = { uri, name ->
                            scope.launch { settings.setCustomTree(uri, name) }
                        },
                        onUseSystemStorage = {
                            scope.launch { settings.setCustomTree(null, null) }
                        },
                        onExportCookies = { site, destinationUri ->
                            scope.launch {
                                sessions.exportCookies(site, destinationUri)
                                    .onSuccess { count ->
                                        IslandFeedback.success(context, "已导出 ${site.label} 的 $count 条会话")
                                    }
                                    .onFailure { error ->
                                        IslandFeedback.error(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "导出登录信息失败",
                                                fallbackAction = "请确认目标文件可写并重新选择保存位置后重试",
                                            ))
                                    }
                            }
                        },
                        onOpenLogin = sessions::openLogin,
                        onImportYoutubeCookies = { sourceUri ->
                            scope.launch {
                                sessions.importYoutubeCookies(sourceUri)
                                    .onSuccess {
                                        IslandFeedback.success(context, "YouTube 登录信息已保存")
                                    }
                                    .onFailure { error ->
                                        IslandFeedback.error(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "导入 YouTube 登录信息失败",
                                                fallbackAction = "请重新导出 Netscape 格式 cookies.txt 后选择该文件",
                                            ))
                                    }
                            }
                        },
                        onClearSession = { site ->
                            scope.launch {
                                sessions.clear(site)
                                    .onSuccess {
                                        IslandFeedback.success(
                                            context,
                                            "已清除 ${site.label} 会话")
                                    }
                                    .onFailure { error ->
                                        IslandFeedback.error(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "无法清除 ${site.label} 登录信息",
                                                fallbackAction = "请先关闭对应登录页面再重试",
                                            ))
                                    }
                            }
                        },
                        onStartDownloads = { scope.launch { downloadEngine.start() } },
                        onPauseDownloads = { scope.launch { downloadEngine.pauseAll() } },
                        onStopDownload = { taskId -> scope.launch { downloadEngine.stop(taskId) } },
                        onDeleteQueued = { taskId ->
                            scope.launch { downloadEngine.remove(taskId) }
                        },
                        onRetryQueued = { taskId ->
                            val failed = queue.firstOrNull { it.task.taskId == taskId }
                            if (failed != null && WebSourceRecoveryPolicy.offersRecapture(failed)) {
                                homeViewModel.requestWebSourceRefresh(failed)
                            } else {
                                scope.launch { downloadEngine.retry(taskId) }
                            }
                        },
                        onDeleteHistory = { taskId ->
                            scope.launch { downloads.deleteHistoryRecord(taskId) }
                        },
                        historyFavoritesOnly = historyFavoritesOnly,
                        onHistoryFavoritesOnlyChange = { historyFavoritesOnly = it },
                        onHistoryFavoriteChange = { taskId, isFavorite ->
                            scope.launch {
                                try {
                                    if (downloads.setHistoryFavorite(taskId, isFavorite)) {
                                        IslandFeedback.success(context, if (isFavorite) "已收藏" else "已取消收藏")
                                    } else {
                                        IslandFeedback.error(context, "记录已变化，请刷新后重试")
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    IslandFeedback.error(context, "收藏保存失败，请重试")
                                }
                            }
                        },
                        onDeleteHistories = { taskIds ->
                            scope.launch { downloads.deleteHistoryRecords(taskIds) }
                        },
                        onDeleteMissingHistories = {
                            scope.launch { downloads.deleteMissingHistoryRecords() }
                        },
                        onMediaMissing = { taskId ->
                            scope.launch { downloads.markHistoryMediaMissing(taskId) }
                        },
                        onOpenHistoryVideo = { taskId, uri, title ->
                            activeVideoTaskId = taskId
                            activeVideoUri = uri
                            activeVideoTitle = title
                            activeVideoPositionMillis = playbackMemory.positionFor(uri)
                            activeVideoPlayWhenReady = true
                        },
                        expanded = true,
                        networkAvailable = isNetworkAvailable,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryFloatingNavigation(navController, route, compact = false)
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        PrimaryFloatingNavigation(navController, route, compact = true)
                    },
                ) { padding ->
                    AppNavHost(
                        navController = navController,
                        queue = queue,
                        history = history,
                        throughputReports = throughputReports,
                        settings = appSettings,
                        input = homeState.input,
                        isReading = homeState.isReading,
                        notice = homeState.notice,
                        canLoadMore = homeState.canLoadMore,
                        pendingItems = homeState.pendingItems,
                        onPendingSelected = homeViewModel::setPendingSelected,
                        pendingErrors = homeState.pendingErrors,
                        onConfirmPending = { scope.launch { homeViewModel.confirmPending() } },
                        onDismissPending = homeViewModel::dismissPending,
                        onInputChange = { value ->
                            homeViewModel.onInputChanged(value)
                            scope.launch { settings.saveInputDraft(value) }
                        },
                        onSmartRead = {
                            scope.launch { homeViewModel.smartRead() }
                        },
                        onLoadMore = { scope.launch { homeViewModel.loadMore() } },
                        onSelectionChanged = { taskId, selected ->
                            scope.launch { downloads.setSelected(taskId, selected) }
                        },
                        onBulkSelectionChanged = { taskIds, selected ->
                            scope.launch { downloads.bulkSelect(taskIds, selected) }
                        },
                        onItemResolutionChanged = { taskId, resolution ->
                            scope.launch { downloads.setResolution(taskId, resolution) }
                        },
                        onAudioSegmentCountChanged = { taskId, count ->
                            scope.launch { downloads.setAudioSegmentCount(taskId, count) }
                        },
                        onResolutionSelected = { resolution ->
                            scope.launch { settings.setDefaultResolution(resolution) }
                        },
                        sessionStates = sessionStates,
                        onAutoResumeChanged = { value ->
                            scope.launch { settings.setAutoResumeNetwork(value) }
                        },
                        onMaxConcurrentDownloadsSelected = { value ->
                            scope.launch { settings.setMaxConcurrentDownloads(value) }
                        },
                        onFileNameRuleSelected = { value ->
                            scope.launch { settings.setFileNameRule(value) }
                        },
                        onCustomTreeSelected = { uri, name ->
                            scope.launch { settings.setCustomTree(uri, name) }
                        },
                        onUseSystemStorage = {
                            scope.launch { settings.setCustomTree(null, null) }
                        },
                        onExportCookies = { site, destinationUri ->
                            scope.launch {
                                sessions.exportCookies(site, destinationUri)
                                    .onSuccess { count ->
                                        IslandFeedback.success(context, "已导出 ${site.label} 的 $count 条会话")
                                    }
                                    .onFailure { error ->
                                        IslandFeedback.error(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "导出登录信息失败",
                                                fallbackAction = "请确认目标文件可写并重新选择保存位置后重试",
                                            ))
                                    }
                            }
                        },
                        onOpenLogin = sessions::openLogin,
                        onImportYoutubeCookies = { sourceUri ->
                            scope.launch {
                                sessions.importYoutubeCookies(sourceUri)
                                    .onSuccess {
                                        IslandFeedback.success(context, "YouTube 登录信息已保存")
                                    }
                                    .onFailure { error ->
                                        IslandFeedback.error(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "导入 YouTube 登录信息失败",
                                                fallbackAction = "请重新导出 Netscape 格式 cookies.txt 后选择该文件",
                                            ))
                                    }
                            }
                        },
                        onClearSession = { site ->
                            scope.launch {
                                sessions.clear(site)
                                    .onSuccess {
                                        IslandFeedback.success(
                                            context,
                                            "已清除 ${site.label} 会话")
                                    }
                                    .onFailure { error ->
                                        IslandFeedback.error(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "无法清除 ${site.label} 登录信息",
                                                fallbackAction = "请先关闭对应登录页面再重试",
                                            ))
                                    }
                            }
                        },
                        onStartDownloads = { scope.launch { downloadEngine.start() } },
                        onPauseDownloads = { scope.launch { downloadEngine.pauseAll() } },
                        onStopDownload = { taskId -> scope.launch { downloadEngine.stop(taskId) } },
                        onDeleteQueued = { taskId ->
                            scope.launch { downloadEngine.remove(taskId) }
                        },
                        onRetryQueued = { taskId ->
                            val failed = queue.firstOrNull { it.task.taskId == taskId }
                            if (failed != null && WebSourceRecoveryPolicy.offersRecapture(failed)) {
                                homeViewModel.requestWebSourceRefresh(failed)
                            } else {
                                scope.launch { downloadEngine.retry(taskId) }
                            }
                        },
                        onDeleteHistory = { taskId ->
                            scope.launch { downloads.deleteHistoryRecord(taskId) }
                        },
                        historyFavoritesOnly = historyFavoritesOnly,
                        onHistoryFavoritesOnlyChange = { historyFavoritesOnly = it },
                        onHistoryFavoriteChange = { taskId, isFavorite ->
                            scope.launch {
                                try {
                                    if (downloads.setHistoryFavorite(taskId, isFavorite)) {
                                        IslandFeedback.success(context, if (isFavorite) "已收藏" else "已取消收藏")
                                    } else {
                                        IslandFeedback.error(context, "记录已变化，请刷新后重试")
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    IslandFeedback.error(context, "收藏保存失败，请重试")
                                }
                            }
                        },
                        onDeleteHistories = { taskIds ->
                            scope.launch { downloads.deleteHistoryRecords(taskIds) }
                        },
                        onDeleteMissingHistories = {
                            scope.launch { downloads.deleteMissingHistoryRecords() }
                        },
                        onMediaMissing = { taskId ->
                            scope.launch { downloads.markHistoryMediaMissing(taskId) }
                        },
                        onOpenHistoryVideo = { taskId, uri, title ->
                            activeVideoTaskId = taskId
                            activeVideoUri = uri
                            activeVideoTitle = title
                            activeVideoPositionMillis = playbackMemory.positionFor(uri)
                            activeVideoPlayWhenReady = true
                        },
                        expanded = false,
                        networkAvailable = isNetworkAvailable,
                        modifier = Modifier.padding(padding),
                    )
                }
            }

            activeVideoUri?.let { uri ->
                val taskId = activeVideoTaskId ?: return@let
                InternalVideoPlayerOverlay(
                    playbackSessionId = taskId,
                    title = activeVideoTitle,
                    videoUri = uri,
                    playbackMemory = playbackMemory,
                    initialPositionMillis = activeVideoPositionMillis,
                    initialPlayWhenReady = activeVideoPlayWhenReady,
                    onPlaybackSnapshot = { positionMillis, playWhenReady ->
                        if (activeVideoTaskId == taskId && activeVideoUri == uri) {
                            activeVideoPositionMillis = positionMillis
                            activeVideoPlayWhenReady = playWhenReady
                        }
                    },
                    onDismiss = {
                        activeVideoTaskId = null
                        activeVideoUri = null
                        activeVideoTitle = ""
                        activeVideoPositionMillis = 0L
                        activeVideoPlayWhenReady = true
                    },
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    queue: List<com.nanzhufeng.videodownloader.core.model.QueuedDownload>,
    history: List<com.nanzhufeng.videodownloader.core.model.DownloadHistory>,
    throughputReports: List<com.nanzhufeng.videodownloader.core.model.DownloadThroughputReport>,
    settings: com.nanzhufeng.videodownloader.data.settings.AppSettings,
    input: String,
    isReading: Boolean,
    notice: String,
    canLoadMore: Boolean,
    pendingItems: List<com.nanzhufeng.videodownloader.feature.home.PendingMedia>,
    pendingErrors: List<Pair<String, String>>,
    onPendingSelected: (String, Boolean) -> Unit,
    onConfirmPending: () -> Unit,
    onDismissPending: () -> Unit,
    onInputChange: (String) -> Unit,
    onSmartRead: () -> Unit,
    onLoadMore: () -> Unit,
    onSelectionChanged: (String, Boolean) -> Unit,
    onBulkSelectionChanged: (List<String>, Boolean) -> Unit,
    onItemResolutionChanged: (String, com.nanzhufeng.videodownloader.core.model.ResolutionPreset) -> Unit,
    onAudioSegmentCountChanged: (String, Int) -> Unit,
    onResolutionSelected: (com.nanzhufeng.videodownloader.core.model.ResolutionPreset) -> Unit,
    sessionStates: List<SiteSessionState>,
    onAutoResumeChanged: (Boolean) -> Unit,
    onMaxConcurrentDownloadsSelected: (Int) -> Unit,
    onFileNameRuleSelected: (FileNameRule) -> Unit,
    onCustomTreeSelected: (String, String) -> Unit,
    onUseSystemStorage: () -> Unit,
    onExportCookies: (SessionSite, String) -> Unit,
    onOpenLogin: (SessionSite) -> Unit,
    onImportYoutubeCookies: (String) -> Unit,
    onClearSession: (SessionSite) -> Unit,
    onStartDownloads: () -> Unit,
    onPauseDownloads: () -> Unit,
    onStopDownload: (String) -> Unit,
    onDeleteQueued: (String) -> Unit,
    onRetryQueued: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onHistoryFavoriteChange: (String, Boolean) -> Unit,
    historyFavoritesOnly: Boolean,
    onHistoryFavoritesOnlyChange: (Boolean) -> Unit,
    onDeleteHistories: (List<String>) -> Unit,
    onDeleteMissingHistories: () -> Unit,
    onMediaMissing: (String) -> Unit,
    onOpenHistoryVideo: (taskId: String, uri: String, title: String) -> Unit,
    expanded: Boolean,
    networkAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.HOME.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(AppDestination.HOME.route) {
            HomeScreen(
                queue = queue,
                input = input,
                onInputChange = onInputChange,
                onSmartRead = onSmartRead,
                isReading = isReading,
                notice = notice,
                canLoadMore = canLoadMore,
                pendingItems = pendingItems,
                pendingErrors = pendingErrors,
                onPendingSelected = onPendingSelected,
                onConfirmPending = onConfirmPending,
                onDismissPending = onDismissPending,
                onLoadMore = onLoadMore,
                onSelectionChanged = onSelectionChanged,
                onBulkSelectionChanged = onBulkSelectionChanged,
                onResolutionChanged = onItemResolutionChanged,
                onAudioSegmentCountChanged = onAudioSegmentCountChanged,
                onDeleteQueued = onDeleteQueued,
                onRetryQueued = onRetryQueued,
                onStartDownloads = onStartDownloads,
                onPauseActive = onPauseDownloads,
                onStopActive = onStopDownload,
                networkAvailable = networkAvailable,
                defaultResolution = settings.defaultResolution,
                completedCount = history.count {
                    it.finalStatus == com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus.COMPLETED
                },
                expanded = expanded,
            )
        }
        composable(AppDestination.HISTORY.route) {
            HistoryScreen(
                history = history,
                throughputReports = throughputReports,
                onDeleteRecord = onDeleteHistory,
                onHistoryFavoriteChange = onHistoryFavoriteChange,
                favoritesOnly = historyFavoritesOnly,
                onFavoritesOnlyChange = onHistoryFavoritesOnlyChange,
                onDeleteRecords = onDeleteHistories,
                onDeleteMissingRecords = onDeleteMissingHistories,
                onMediaMissing = onMediaMissing,
                onOpenInternalVideo = onOpenHistoryVideo,
            )
        }
        composable(AppDestination.SETTINGS.route) {
            SettingsScreen(
                settings = settings,
                sessions = sessionStates,
                onResolutionSelected = onResolutionSelected,
                onAutoResumeChanged = onAutoResumeChanged,
                onMaxConcurrentDownloadsSelected = onMaxConcurrentDownloadsSelected,
                onFileNameRuleSelected = onFileNameRuleSelected,
                onCustomTreeSelected = onCustomTreeSelected,
                onUseSystemStorage = onUseSystemStorage,
                onExportCookies = onExportCookies,
                onOpenLogin = onOpenLogin,
                onImportYoutubeCookies = onImportYoutubeCookies,
                onClearSession = onClearSession,
                expanded = expanded,
            )
        }
    }
}

@Composable
private fun PrimaryFloatingNavigation(
    navController: NavHostController,
    currentRoute: String,
    compact: Boolean,
) {
    val itemHeight = if (compact) 52.dp else 56.dp
    val bottomGap = if (compact) 4.dp else 6.dp
    val horizontalGap = if (compact) 32.dp else 24.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = horizontalGap, end = horizontalGap, top = 4.dp, bottom = bottomGap)
            .testTag("floating-bottom-navigation"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color(0x12000000),
                    spotColor = Color(0x0A000000),
                )
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
                .padding(3.dp),
        ) {
            AppDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                val navigationSurface by animateColorAsState(
                    targetValue = if (selected) SelectedSage else Color.Transparent,
                    animationSpec = tween(
                        durationMillis = 120,
                        easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f),
                    ),
                    label = "floating-navigation-selection",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(itemHeight)
                        .clip(RoundedCornerShape(28.dp))
                        .background(navigationSurface)
                        .clickable { navController.openPrimary(destination) }
                        .testTag(destination.testTag)
                        .semantics {
                            stateDescription = if (selected) "已选中" else "未选中"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        destination.icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun NavHostController.openPrimary(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(AppDestination.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
