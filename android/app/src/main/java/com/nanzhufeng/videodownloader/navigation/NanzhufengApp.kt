package com.nanzhufeng.videodownloader.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.nanzhufeng.videodownloader.R
import com.nanzhufeng.videodownloader.core.ui.NanzhufengTheme
import com.nanzhufeng.videodownloader.core.ui.SelectedSage
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.data.settings.FileNameRule
import com.nanzhufeng.videodownloader.feature.history.HistoryScreen
import com.nanzhufeng.videodownloader.feature.home.HomeScreen
import com.nanzhufeng.videodownloader.feature.home.HomeViewModel
import com.nanzhufeng.videodownloader.feature.settings.SettingsScreen
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import com.nanzhufeng.videodownloader.domain.download.DownloadEngine
import com.nanzhufeng.videodownloader.domain.download.NoOpDownloadEngine
import com.nanzhufeng.videodownloader.domain.session.NoOpSessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionSite
import com.nanzhufeng.videodownloader.domain.session.SiteSessionState
import com.nanzhufeng.videodownloader.probe.DouyinProbeActivity
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

@Composable
fun NanzhufengApp(
    container: AppContainer,
    incomingSharedText: Flow<String> = emptyFlow(),
) {
    NanzhufengApp(
        downloads = container.downloads,
        settings = container.settings,
        discovery = container.discovery,
        downloadEngine = container.downloadEngine,
        networkAvailable = container.networkAvailable,
        sessions = container.sessions,
        incomingSharedText = incomingSharedText,
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
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val homeViewModel: HomeViewModel = viewModel(
            factory = HomeViewModel.factory(downloads, settings, discovery),
        )
        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
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
        val isNetworkAvailable by networkAvailable.collectAsStateWithLifecycle(initialValue = false)
        val navController = rememberNavController()
        var recoveryRequested by remember { mutableStateOf(false) }
        var completionDialog by remember { mutableStateOf<DownloadHistory?>(null) }

        LaunchedEffect(downloads) {
            var initialized = false
            var knownCompletedIds = emptySet<String>()
            downloads.history.collect { snapshot ->
                val completed = snapshot.filter { it.finalStatus == DownloadTaskStatus.COMPLETED }
                val completedIds = completed.mapTo(mutableSetOf()) { it.taskId }
                if (initialized) {
                    val newest = completed.firstOrNull { it.taskId !in knownCompletedIds }
                    if (newest != null) completionDialog = newest
                }
                knownCompletedIds = completedIds
                initialized = true
            }
        }

        LaunchedEffect(queue, recoveryRequested) {
            val hasInterruptedTransfer = queue.any { queued ->
                queued.task.status in setOf(
                    DownloadTaskStatus.PARSING,
                    DownloadTaskStatus.DOWNLOADING,
                    DownloadTaskStatus.VALIDATING,
                )
            }
            if (!recoveryRequested && hasInterruptedTransfer) {
                recoveryRequested = true
                downloadEngine.resumeWhenNetworkAvailable()
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
                douyinCaptureLauncher.launch(DouyinProbeActivity.createIntent(context, sourceUrl))
            }
        }

        completionDialog?.let { item ->
            AlertDialog(
                modifier = Modifier.testTag("completion-dialog"),
                onDismissRequest = { completionDialog = null },
                title = { Text("下载成功") },
                text = {
                    Text("${item.title}\n已保存到系统媒体库。")
                },
                confirmButton = {
                    TextButton(onClick = { completionDialog = null }) { Text("知道了") }
                },
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val expanded = expandedOverride ?: (maxWidth >= 600.dp)
            val currentRoute by navController.currentBackStackEntryAsState()
            val route = currentRoute?.destination?.route ?: AppDestination.HOME.route

            if (expanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    PrimaryNavigationRail(navController, route)
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
                        onExportCookies = { destinationUri ->
                            scope.launch {
                                sessions.exportCookies(destinationUri)
                                    .onSuccess { count ->
                                        Toast.makeText(context, "已导出 $count 组会话", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "导出登录信息失败",
                                                fallbackAction = "请确认目标文件可写并重新选择保存位置后重试",
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            }
                        },
                        onOpenLogin = sessions::openLogin,
                        onImportYoutubeCookies = { sourceUri ->
                            scope.launch {
                                sessions.importYoutubeCookies(sourceUri)
                                    .onSuccess {
                                        Toast.makeText(context, "YouTube 登录信息已保存", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "导入 YouTube 登录信息失败",
                                                fallbackAction = "请重新导出 Netscape 格式 cookies.txt 后选择该文件",
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            }
                        },
                        onClearSession = { site ->
                            scope.launch {
                                sessions.clear(site)
                                    .onSuccess {
                                        Toast.makeText(
                                            context,
                                            "已清除 ${site.label} 会话",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "无法清除 ${site.label} 登录信息",
                                                fallbackAction = "请先关闭对应登录页面再重试",
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
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
                            scope.launch { downloadEngine.retry(taskId) }
                        },
                        onDeleteHistory = { taskId ->
                            scope.launch { downloads.deleteHistoryRecord(taskId) }
                        },
                        onDeleteHistories = { taskIds ->
                            scope.launch { downloads.deleteHistoryRecords(taskIds) }
                        },
                        expanded = true,
                        networkAvailable = isNetworkAvailable,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        PrimaryBottomNavigation(navController, route)
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
                        onExportCookies = { destinationUri ->
                            scope.launch {
                                sessions.exportCookies(destinationUri)
                                    .onSuccess { count ->
                                        Toast.makeText(context, "已导出 $count 组会话", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "导出登录信息失败",
                                                fallbackAction = "请确认目标文件可写并重新选择保存位置后重试",
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            }
                        },
                        onOpenLogin = sessions::openLogin,
                        onImportYoutubeCookies = { sourceUri ->
                            scope.launch {
                                sessions.importYoutubeCookies(sourceUri)
                                    .onSuccess {
                                        Toast.makeText(context, "YouTube 登录信息已保存", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "导入 YouTube 登录信息失败",
                                                fallbackAction = "请重新导出 Netscape 格式 cookies.txt 后选择该文件",
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            }
                        },
                        onClearSession = { site ->
                            scope.launch {
                                sessions.clear(site)
                                    .onSuccess {
                                        Toast.makeText(
                                            context,
                                            "已清除 ${site.label} 会话",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            UserFacingErrorPresenter.message(
                                                rawError = error.message,
                                                fallbackProblem = "无法清除 ${site.label} 登录信息",
                                                fallbackAction = "请先关闭对应登录页面再重试",
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
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
                            scope.launch { downloadEngine.retry(taskId) }
                        },
                        onDeleteHistory = { taskId ->
                            scope.launch { downloads.deleteHistoryRecord(taskId) }
                        },
                        onDeleteHistories = { taskIds ->
                            scope.launch { downloads.deleteHistoryRecords(taskIds) }
                        },
                        expanded = false,
                        networkAvailable = isNetworkAvailable,
                        modifier = Modifier.padding(padding),
                    )
                }
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
    onExportCookies: (String) -> Unit,
    onOpenLogin: (SessionSite) -> Unit,
    onImportYoutubeCookies: (String) -> Unit,
    onClearSession: (SessionSite) -> Unit,
    onStartDownloads: () -> Unit,
    onPauseDownloads: () -> Unit,
    onStopDownload: (String) -> Unit,
    onDeleteQueued: (String) -> Unit,
    onRetryQueued: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onDeleteHistories: (List<String>) -> Unit,
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
                onDeleteRecords = onDeleteHistories,
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
private fun PrimaryBottomNavigation(navController: NavHostController, currentRoute: String) {
    NavigationBar(
        modifier = Modifier.testTag("bottom-navigation"),
        containerColor = Color.White,
        tonalElevation = 0.dp,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            val navigationSurface by animateColorAsState(
                targetValue = if (selected) SelectedSage else Color.Transparent,
                animationSpec = tween(
                    durationMillis = 120,
                    easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f),
                ),
                label = "bottom-navigation-selection",
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .weight(1f)
                    .height(64.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(navigationSurface)
                    .clickable { navController.openPrimary(destination) }
                    .padding(vertical = 8.dp)
                    .testTag(destination.testTag)
                    .semantics {
                        stateDescription = if (selected) "已选中" else "未选中"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    destination.icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    destination.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PrimaryNavigationRail(navController: NavHostController, currentRoute: String) {
    val appName = stringResource(R.string.app_name)
    NavigationRail(
        modifier = Modifier.testTag("navigation-rail"),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Image(
            painter = painterResource(R.drawable.nanzhufeng_app_icon),
            contentDescription = appName,
            modifier = Modifier
                .padding(12.dp)
                .size(56.dp),
        )
        Spacer(Modifier.height(8.dp))
        AppDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            val navigationSurface by animateColorAsState(
                targetValue = if (selected) SelectedSage else Color.Transparent,
                animationSpec = tween(
                    durationMillis = 120,
                    easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f),
                ),
                label = "navigation-selection",
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .width(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(navigationSurface)
                    .clickable { navController.openPrimary(destination) }
                    .padding(vertical = 12.dp)
                    .testTag(destination.testTag)
                    .semantics {
                        stateDescription = if (selected) "已选中" else "未选中"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    destination.icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    destination.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
