package com.nanzhufeng.videodownloader.core.model

import com.nanzhufeng.videodownloader.data.settings.FileNameRule

enum class DownloadPlatform {
    YOUTUBE,
    BILIBILI,
    DOUYIN,
    TIKTOK,
    XIAOHONGSHU,
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
    UP_TO_360P,
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

enum class DownloadFailureType {
    NETWORK,
    SOURCE,
    TRANSFER,
    OUTPUT,
    UNKNOWN,
}

enum class DownloadConnectionMode {
    UNKNOWN,
    SINGLE,
    MULTI,
}

enum class DownloadProcessingStage {
    NONE,
    NETWORK_MEDIA,
    NETWORK_AUDIO,
    NETWORK_VIDEO_TO_AUDIO,
    MERGING,
    TRANSCODING,
    VIDEO_SEGMENTING,
    VALIDATING,
    PUBLISHING,
}

enum class TransferReportOutcome {
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class DownloadThroughputReport(
    val reportId: String,
    val taskId: String,
    val platform: DownloadPlatform,
    val streamLabel: String,
    val outcome: TransferReportOutcome,
    val connectionMode: DownloadConnectionMode,
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
    val fileNameRule: FileNameRule = FileNameRule.DATE_AND_TITLE,
    val saveTreeUri: String?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val remainingSeconds: Long?,
    val status: DownloadTaskStatus,
    val failureType: DownloadFailureType?,
    val errorSummary: String?,
    val retryCount: Int,
    val updatedAt: Long,
    val connectionMode: DownloadConnectionMode = DownloadConnectionMode.UNKNOWN,
    val connectionCount: Int = 0,
    val processingStage: DownloadProcessingStage = DownloadProcessingStage.NONE,
    val processingProgressPercent: Int = 0,
    /**
     * Historical field name kept for Room/API compatibility.
     * It is the requested output segment count for both MP3 and video tasks.
     */
    val audioSegmentCount: Int = 1,
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
    val failureType: DownloadFailureType? = null,
    val errorSummary: String? = null,
    val thumbnailUrl: String = "",
    val outputUris: List<String> = outputUri?.let(::listOf).orEmpty(),
    /** Requested output segment count; the stored column keeps its legacy name. */
    val audioSegmentCount: Int = 1,
)
