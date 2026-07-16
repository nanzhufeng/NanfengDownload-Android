package com.nanzhufeng.videodownloader

import android.content.Context
import androidx.room.Room
import com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase
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
import com.nanzhufeng.videodownloader.domain.download.MediaStoreOutputStore
import com.nanzhufeng.videodownloader.domain.download.WorkManagerDownloadScheduler
import com.nanzhufeng.videodownloader.domain.download.YtDlpTaskMediaResolver
import kotlinx.coroutines.flow.Flow

class AppContainer private constructor(
    val database: NanzhufengDatabase,
    val downloads: DownloadRepository,
    val settings: SettingsRepository,
    val discovery: SourceDiscoveryEngine,
    val taskRunner: DownloadTaskRunner,
    val downloadEngine: DownloadEngine,
    val networkAvailable: Flow<Boolean>,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val applicationContext = context.applicationContext
            val database = Room.databaseBuilder(
                applicationContext,
                NanzhufengDatabase::class.java,
                "nanzhufeng-video-downloader.db",
            ).build()
            val downloads = RoomDownloadRepository(database)
            val taskRunner = DownloadTaskRunner(
                repository = downloads,
                resolver = YtDlpTaskMediaResolver(),
                transfer = DirectMediaTransfer(applicationContext),
                outputStore = MediaStoreOutputStore(applicationContext),
            )
            return AppContainer(
                database = database,
                downloads = downloads,
                settings = PreferencesSettingsRepository(applicationContext),
                discovery = PlatformSourceDiscoveryEngine(ChaquopyProbeDiscoveryGateway()),
                taskRunner = taskRunner,
                downloadEngine = DefaultDownloadEngine(
                    repository = downloads,
                    scheduler = WorkManagerDownloadScheduler(applicationContext),
                ),
                networkAvailable = NetworkStatusMonitor(applicationContext).available,
            )
        }
    }
}
