package com.nanzhufeng.videodownloader

import android.content.Context
import androidx.room.Room
import com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.repository.RoomDownloadRepository
import com.nanzhufeng.videodownloader.data.settings.PreferencesSettingsRepository
import com.nanzhufeng.videodownloader.data.settings.SettingsRepository

class AppContainer private constructor(
    val database: NanzhufengDatabase,
    val downloads: DownloadRepository,
    val settings: SettingsRepository,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val applicationContext = context.applicationContext
            val database = Room.databaseBuilder(
                applicationContext,
                NanzhufengDatabase::class.java,
                "nanzhufeng-video-downloader.db",
            ).build()
            return AppContainer(
                database = database,
                downloads = RoomDownloadRepository(database),
                settings = PreferencesSettingsRepository(applicationContext),
            )
        }
    }
}
