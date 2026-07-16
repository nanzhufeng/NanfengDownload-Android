package com.nanzhufeng.videodownloader.core.model

enum class DownloadPlatform {
    YOUTUBE,
    DOUYIN,
    TIKTOK,
}

enum class DownloadSourceKind {
    SINGLE_VIDEO,
    CREATOR,
    CHANNEL,
    PLAYLIST,
}

enum class ResolutionPreset {
    BEST,
    UP_TO_1080P,
    UP_TO_720P,
    AUDIO_MP3,
}

enum class DownloadTaskStatus {
    WAITING,
    PARSING,
    DOWNLOADING,
    VALIDATING,
    PAUSED,
    WAITING_NETWORK,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED,
}

val DownloadTaskStatus.isTerminal: Boolean
    get() = this in setOf(
        DownloadTaskStatus.COMPLETED,
        DownloadTaskStatus.FAILED,
        DownloadTaskStatus.SKIPPED,
        DownloadTaskStatus.CANCELLED,
    )

data class MediaItem(
    val mediaKey: String,
    val platform: DownloadPlatform,
    val contentId: String,
    val originalUrl: String,
    val sourceKind: DownloadSourceKind,
    val title: String,
    val creator: String,
    val creatorId: String,
    val publishDate: String,
    val thumbnailUrl: String,
)

data class DownloadTask(
    val taskId: String,
    val mediaKey: String,
    val selected: Boolean,
    val sortOrder: Long,
    val resolution: ResolutionPreset,
    val saveTreeUri: String?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadTaskStatus,
    val failureType: String?,
    val errorSummary: String?,
    val retryCount: Int,
    val updatedAt: Long,
)

data class QueuedDownload(
    val task: DownloadTask,
    val media: MediaItem,
)

data class DownloadHistory(
    val taskId: String,
    val platform: DownloadPlatform,
    val contentId: String,
    val originalUrl: String,
    val title: String,
    val creator: String,
    val resolution: ResolutionPreset,
    val finalStatus: DownloadTaskStatus,
    val outputUri: String?,
    val fileSize: Long,
    val fileExists: Boolean,
    val completedAt: Long,
)
