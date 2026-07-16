package com.nanzhufeng.videodownloader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nanzhufeng.videodownloader.data.database.dao.DownloadHistoryDao
import com.nanzhufeng.videodownloader.data.database.dao.DownloadTaskDao
import com.nanzhufeng.videodownloader.data.database.dao.MediaItemDao
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskEntity
import com.nanzhufeng.videodownloader.data.database.entity.MediaItemEntity

@Database(
    entities = [
        MediaItemEntity::class,
        DownloadTaskEntity::class,
        DownloadHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NanzhufengDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao

    abstract fun downloadTaskDao(): DownloadTaskDao

    abstract fun downloadHistoryDao(): DownloadHistoryDao
}
