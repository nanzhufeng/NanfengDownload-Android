package com.nanzhufeng.videodownloader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nanzhufeng.videodownloader.data.database.dao.DownloadHistoryDao
import com.nanzhufeng.videodownloader.data.database.dao.DownloadTaskDao
import com.nanzhufeng.videodownloader.data.database.dao.MediaItemDao
import com.nanzhufeng.videodownloader.data.database.dao.DownloadThroughputReportDao
import com.nanzhufeng.videodownloader.data.database.entity.DownloadHistoryEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadTaskEntity
import com.nanzhufeng.videodownloader.data.database.entity.MediaItemEntity
import com.nanzhufeng.videodownloader.data.database.entity.DownloadThroughputReportEntity

@Database(
    entities = [
        MediaItemEntity::class,
        DownloadTaskEntity::class,
        DownloadHistoryEntity::class,
        DownloadThroughputReportEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class NanzhufengDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao

    abstract fun downloadTaskDao(): DownloadTaskDao

    abstract fun downloadHistoryDao(): DownloadHistoryDao

    abstract fun downloadThroughputReportDao(): DownloadThroughputReportDao
}

object NanzhufengMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE download_history ADD COLUMN failureType TEXT")
            database.execSQL("ALTER TABLE download_history ADD COLUMN errorSummary TEXT")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE download_tasks ADD COLUMN fileNameRule TEXT NOT NULL DEFAULT 'DATE_AND_TITLE'",
            )
        }
    }


    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE download_tasks ADD COLUMN connectionMode TEXT NOT NULL DEFAULT 'UNKNOWN'",
            )
            database.execSQL(
                "ALTER TABLE download_tasks ADD COLUMN connectionCount INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_throughput_reports (
                    reportId TEXT NOT NULL PRIMARY KEY,
                    taskId TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    streamLabel TEXT NOT NULL,
                    outcome TEXT NOT NULL,
                    connectionMode TEXT NOT NULL,
                    connectionCount INTEGER NOT NULL,
                    rangeSupported INTEGER NOT NULL,
                    expectedBytes INTEGER NOT NULL,
                    committedBytes INTEGER NOT NULL,
                    networkBytes INTEGER NOT NULL,
                    startedAt INTEGER NOT NULL,
                    finishedAt INTEGER NOT NULL,
                    elapsedMillis INTEGER NOT NULL,
                    averageBytesPerSecond INTEGER NOT NULL,
                    peakBytesPerSecond INTEGER NOT NULL,
                    retryCount INTEGER NOT NULL,
                    reprobeCount INTEGER NOT NULL,
                    fallbackReason TEXT,
                    errorSummary TEXT
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_download_throughput_reports_taskId ON download_throughput_reports(taskId)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_download_throughput_reports_startedAt ON download_throughput_reports(startedAt)",
            )
        }
    }
}
