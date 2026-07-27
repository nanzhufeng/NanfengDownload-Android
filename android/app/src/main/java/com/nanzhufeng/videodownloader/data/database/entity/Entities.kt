package com.nanzhufeng.videodownloader.data.database.entity

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "media_items",
    indices = [Index(value = ["platform", "contentId"], unique = true)],
)
data class MediaItemEntity(
    @PrimaryKey val mediaKey: String,
    val platform: String,
    val contentId: String,
    val originalUrl: String,
    val sourceKind: String,
    val title: String,
    val creator: String,
    val creatorId: String,
    val publishDate: String,
    val thumbnailUrl: String,
    val discoveredAt: Long,
)

@Entity(
    tableName = "download_tasks",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["mediaKey"],
            childColumns = ["mediaKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaKey"), Index("status"), Index("sortOrder")],
)
data class DownloadTaskEntity(
    @PrimaryKey val taskId: String,
    val mediaKey: String,
    val selected: Boolean,
    val sortOrder: Long,
    val resolution: String,
    val fileNameRule: String = "DATE_AND_TITLE",
    val saveTreeUri: String?,
    val tempPath: String?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val remainingSeconds: Long?,
    val status: String,
    val failureType: String?,
    val errorSummary: String?,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val connectionMode: String = "UNKNOWN",
    val connectionCount: Int = 0,
    val processingStage: String = "NONE",
    val processingProgressPercent: Int = 0,
    val audioSegmentCount: Int = 1,
)

data class DownloadTaskWithMedia(
    @Embedded val task: DownloadTaskEntity,
    @Relation(
        parentColumn = "mediaKey",
        entityColumn = "mediaKey",
    )
    val media: MediaItemEntity,
)

@Entity(
    tableName = "download_history",
    indices = [
        Index(value = ["platform", "contentId", "resolution"]),
        Index("completedAt"),
        Index("creator"),
    ],
)
data class DownloadHistoryEntity(
    @PrimaryKey val taskId: String,
    val platform: String,
    val contentId: String,
    val originalUrl: String,
    val title: String,
    val creator: String,
    val resolution: String,
    val finalStatus: String,
    val outputUri: String?,
    val fileSize: Long,
    val fileExists: Boolean,
    val completedAt: Long,
    val failureType: String? = null,
    val errorSummary: String? = null,
    val outputUrisJson: String = "[]",
    val audioSegmentCount: Int = 1,
)

data class DownloadHistoryWithThumbnail(
    @Embedded val history: DownloadHistoryEntity,
    @ColumnInfo(name = "mediaThumbnailUrl") val thumbnailUrl: String?,
)

@Entity(
    tableName = "download_throughput_reports",
    indices = [Index("taskId"), Index("startedAt")],
)
data class DownloadThroughputReportEntity(
    @PrimaryKey val reportId: String,
    val taskId: String,
    val platform: String,
    val streamLabel: String,
    val outcome: String,
    val connectionMode: String,
    val connectionCount: Int,
    val rangeSupported: Boolean,
    val expectedBytes: Long,
    val committedBytes: Long,
    val networkBytes: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val elapsedMillis: Long,
    val averageBytesPerSecond: Long,
    val peakBytesPerSecond: Long,
    val retryCount: Int,
    val reprobeCount: Int,
    val fallbackReason: String?,
    val errorSummary: String?,
)
