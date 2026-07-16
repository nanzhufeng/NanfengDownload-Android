package com.nanzhufeng.videodownloader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = true,
)
abstract class NanzhufengDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao

    abstract fun downloadTaskDao(): DownloadTaskDao

    abstract fun downloadHistoryDao(): DownloadHistoryDao
}

object NanzhufengMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE download_history ADD COLUMN failureType TEXT")
            database.execSQL("ALTER TABLE download_history ADD COLUMN errorSummary TEXT")
        }
    }
}
