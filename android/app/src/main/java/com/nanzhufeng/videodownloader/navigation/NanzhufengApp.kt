package com.nanzhufeng.videodownloader.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nanzhufeng.videodownloader.AppContainer
import com.nanzhufeng.videodownloader.R
import com.nanzhufeng.videodownloader.core.ui.NanzhufengTheme
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.feature.history.HistoryScreen
import com.nanzhufeng.videodownloader.feature.home.HomeScreen
import com.nanzhufeng.videodownloader.feature.home.HomeViewModel
import com.nanzhufeng.videodownloader.feature.settings.SettingsScreen
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import com.nanzhufeng.videodownloader.domain.download.DownloadEngine
import com.nanzhufeng.videodownloader.domain.download.NoOpDownloadEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
) {
    NanzhufengTheme {
        val queue by downloads.activeTasks.collectAsStateWithLifecycle(initialValue = emptyList())
        val history by downloads.history.collectAsStateWithLifecycle(initialValue = emptyList())
        val appSettings by settings.settings.collectAsStateWithLifecycle(
            initialValue = com.nanzhufeng.videodownloader.data.settings.AppSettings(),
        )
        val scope = rememberCoroutineScope()
        val homeViewModel: HomeViewModel = viewModel(
            factory = HomeViewModel.factory(downloads, settings, discovery),
        )
        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
        val isNetworkAvailable by networkAvailable.collectAsStateWithLifecycle(initialValue = false)
        val navController = rememberNavController()

        LaunchedEffect(incomingSharedText) {
            incomingSharedText.collect(homeViewModel::onInputChanged)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                        settings = appSettings,
                        input = homeState.input,
                        isReading = homeState.isReading,
                        notice = homeState.notice,
                        canLoadMore = homeState.canLoadMore,
                        onInputChange = homeViewModel::onInputChanged,
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
                        onResolutionSelected = { resolution ->
                            scope.launch { settings.setDefaultResolution(resolution) }
                        },
                        onStartDownloads = { scope.launch { downloadEngine.start() } },
                        onPauseDownloads = { scope.launch { downloadEngine.pauseAll() } },
                        onStopDownload = { taskId -> scope.launch { downloadEngine.stop(taskId) } },
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
                        settings = appSettings,
                        input = homeState.input,
                        isReading = homeState.isReading,
                        notice = homeState.notice,
                        canLoadMore = homeState.canLoadMore,
                        onInputChange = homeViewModel::onInputChanged,
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
                        onResolutionSelected = { resolution ->
                            scope.launch { settings.setDefaultResolution(resolution) }
                        },
                        onStartDownloads = { scope.launch { downloadEngine.start() } },
                        onPauseDownloads = { scope.launch { downloadEngine.pauseAll() } },
                        onStopDownload = { taskId -> scope.launch { downloadEngine.stop(taskId) } },
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
    onResolutionSelected: (com.nanzhufeng.videodownloader.core.model.ResolutionPreset) -> Unit,
    onStartDownloads: () -> Unit,
    onPauseDownloads: () -> Unit,
    onStopDownload: (String) -> Unit,
    expanded: Boolean,
    networkAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.HOME.route,
        modifier = modifier.fillMaxSize(),
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
                onStartDownloads = onStartDownloads,
                onPauseActive = onPauseDownloads,
                onStopActive = onStopDownload,
                networkAvailable = networkAvailable,
                expanded = expanded,
            )
        }
        composable(AppDestination.HISTORY.route) {
            HistoryScreen(history)
        }
        composable(AppDestination.SETTINGS.route) {
            SettingsScreen(settings, onResolutionSelected)
        }
    }
}

@Composable
private fun PrimaryBottomNavigation(navController: NavHostController, currentRoute: String) {
    NavigationBar(modifier = Modifier.testTag("bottom-navigation")) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { navController.openPrimary(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color(0xFFE2EEFF),
                ),
                modifier = Modifier.testTag(destination.testTag),
            )
        }
    }
}

@Composable
private fun PrimaryNavigationRail(navController: NavHostController, currentRoute: String) {
    NavigationRail(modifier = Modifier.testTag("navigation-rail")) {
        Image(
            painter = painterResource(R.drawable.nanzhufeng_app_icon),
            contentDescription = "南烛枫视频下载器",
            modifier = Modifier
                .padding(12.dp)
                .size(48.dp),
        )
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { navController.openPrimary(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color(0xFFE2EEFF),
                ),
                modifier = Modifier.testTag(destination.testTag),
            )
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
