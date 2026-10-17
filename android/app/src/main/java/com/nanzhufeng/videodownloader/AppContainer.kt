package com.nanzhufeng.videodownloader

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase
import com.nanzhufeng.videodownloader.data.database.NanzhufengMigrations
import com.nanzhufeng.videodownloader.data.network.NetworkStatusMonitor
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.repository.RoomDownloadRepository
import com.nanzhufeng.videodownloader.data.settings.PreferencesSettingsRepository
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository
import com.nanzhufeng.videodownloader.domain.discovery.ChaquopyProbeDiscoveryGateway
import com.nanzhufeng.videodownloader.domain.discovery.PlatformSourceDiscoveryEngine
import com.nanzhufeng.videodownloader.domain.discovery.SourceDiscoveryEngine
import com.nanzhufeng.videodownloader.domain.download.DefaultDownloadEngine
import com.nanzhufeng.videodownloader.domain.download.DirectMediaTransfer
import com.nanzhufeng.videodownloader.domain.download.DownloadEngine
import com.nanzhufeng.videodownloader.domain.download.DownloadTaskRunner
import com.nanzhufeng.videodownloader.domain.download.DownloadPerformanceReporter
import com.nanzhufeng.videodownloader.domain.download.MediaStoreOutputStore
import com.nanzhufeng.videodownloader.domain.download.WorkManagerDownloadScheduler
import com.nanzhufeng.videodownloader.domain.download.YtDlpTaskMediaResolver
import com.nanzhufeng.videodownloader.domain.session.SessionProvider
import com.nanzhufeng.videodownloader.domain.session.WebViewSessionProvider
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus

class AppContainer private constructor(
    val database: NanzhufengDatabase,
    val downloads: DownloadRepository,
    val settings: SettingsRepository,
    val discovery: SourceDiscoveryEngine,
    val taskRunner: DownloadTaskRunner,
    val downloadEngine: DownloadEngine,
    val networkAvailable: Flow<Boolean>,
    val sessions: SessionProvider,
    val douyinCaptures: DouyinCapturedMediaRepository,
) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // This observer survives a WorkManager constraint cancellation, so the visible
        // task state changes even while a blocked network read is still unwinding.
        applicationScope.launch {
            combine(networkAvailable, settings.settings) { connected, preferences ->
                connected to preferences.autoResumeNetwork
            }.distinctUntilChanged().collect { (connected, automaticResume) ->
                runCatching {
                    if (!connected) {
                        downloads.markActiveTasksWaitingForNetwork()
                    } else if (automaticResume && downloads.activeTasks.first().any {
                            it.task.selected && it.task.status == DownloadTaskStatus.WAITING_NETWORK
                        }) {
                        downloadEngine.resumeWhenNetworkAvailable()
                    }
                }.onFailure { Log.e("NanfengNetwork", "无法更新网络等待任务", it) }
            }
        }
    }

    companion object {
        @OptIn(markerClass = [UnstableApi::class])
        fun create(context: Context): AppContainer {
            val applicationContext = context.applicationContext
            val database = Room.databaseBuilder(
                applicationContext,
                NanzhufengDatabase::class.java,
                "nanzhufeng-video-downloader.db",
            ).addMigrations(
                NanzhufengMigrations.MIGRATION_1_2,
                NanzhufengMigrations.MIGRATION_2_3,
                NanzhufengMigrations.MIGRATION_3_4,
                NanzhufengMigrations.MIGRATION_4_5,
                NanzhufengMigrations.MIGRATION_5_6,
                NanzhufengMigrations.MIGRATION_6_7,
                NanzhufengMigrations.MIGRATION_7_8,
                NanzhufengMigrations.MIGRATION_8_9,
                NanzhufengMigrations.MIGRATION_9_10,
                NanzhufengMigrations.MIGRATION_10_11,
            )
                .build()
            val downloads = RoomDownloadRepository(database, mediaInspector = com.nanzhufeng.videodownloader.data.repository.AndroidLocalMediaInspector(applicationContext))
            val sessions = WebViewSessionProvider(applicationContext)
            val douyinCaptures = DouyinCapturedMediaRepository(applicationContext)
            val performanceReporter = DownloadPerformanceReporter { taskId, stage, elapsedMillis ->
                Log.i(
                    "DownloadPerformance",
                    "task=$taskId stage=$stage elapsedMs=$elapsedMillis",
                )
            }
            val taskRunner = DownloadTaskRunner(
                repository = downloads,
                resolver = YtDlpTaskMediaResolver(
                    sessions = sessions,
                    douyinCaptures = douyinCaptures,
                ),
                transfer = DirectMediaTransfer(
                    context = applicationContext,
                    performanceReporter = performanceReporter,
                    transferModeSink = { taskId, mode, connectionCount ->
                        downloads.updateConnectionMode(taskId, mode, connectionCount)
                    },
                    throughputReportSink = { report ->
                        downloads.recordThroughputReport(report)
                    },
                    processingSink = { taskId, stage, progressPercent ->
                        downloads.updateProcessing(taskId, stage, progressPercent)
                    },
                ),
                outputStore = MediaStoreOutputStore(applicationContext),
                performanceReporter = performanceReporter,
            )
            return AppContainer(
                database = database,
                downloads = downloads,
                settings = PreferencesSettingsRepository(applicationContext),
                discovery = PlatformSourceDiscoveryEngine(
                    ChaquopyProbeDiscoveryGateway(sessions = sessions),
                ),
                taskRunner = taskRunner,
                downloadEngine = DefaultDownloadEngine(
                    repository = downloads,
                    scheduler = WorkManagerDownloadScheduler(applicationContext),
                ),
                networkAvailable = NetworkStatusMonitor(applicationContext).available,
                sessions = sessions,
                douyinCaptures = douyinCaptures,
            )
        }
    }
}
