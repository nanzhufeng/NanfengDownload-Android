package com.nanzhufeng.videodownloader

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.Flow

class AppContainer private constructor(
    val database: NanzhufengDatabase,
    val downloads: DownloadRepository,
    val settings: SettingsRepository,
    val discovery: SourceDiscoveryEngine,
    val taskRunner: DownloadTaskRunner,
    val downloadEngine: DownloadEngine,
    val networkAvailable: Flow<Boolean>,
    val sessions: SessionProvider,
) {
    companion object {
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
            )
                .build()
            val downloads = RoomDownloadRepository(database)
            val sessions = WebViewSessionProvider(applicationContext)
            val performanceReporter = DownloadPerformanceReporter { taskId, stage, elapsedMillis ->
                Log.i(
                    "DownloadPerformance",
                    "task=$taskId stage=$stage elapsedMs=$elapsedMillis",
                )
            }
            val taskRunner = DownloadTaskRunner(
                repository = downloads,
                resolver = YtDlpTaskMediaResolver(sessions = sessions),
                transfer = DirectMediaTransfer(
                    context = applicationContext,
                    performanceReporter = performanceReporter,
                    transferModeSink = { taskId, mode, connectionCount ->
                        downloads.updateConnectionMode(taskId, mode, connectionCount)
                    },
                    throughputReportSink = { report ->
                        downloads.recordThroughputReport(report)
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
            )
        }
    }
}
