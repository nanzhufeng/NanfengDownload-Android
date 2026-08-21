package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.core.model.DownloadHistory

/**
 * A history card must prefer the finished local media over an expiring remote
 * platform thumbnail. Coil can cache a local video frame by its MediaStore URI;
 * the remote image remains a fallback for records whose output is unavailable.
 */
internal sealed interface HistoryPreviewSource {
    data class LocalVideo(
        val uri: String,
        val fallbackArtworkUrl: String,
    ) : HistoryPreviewSource
    data class RemoteArtwork(val url: String) : HistoryPreviewSource
    data object None : HistoryPreviewSource
}

internal fun historyPreviewSource(item: DownloadHistory): HistoryPreviewSource {
    if (!shouldUseInternalAudioPlayer(item) && item.fileExists) {
        val localUri = item.outputUris.firstOrNull().orEmpty().ifBlank { item.outputUri.orEmpty() }
        if (localUri.isNotBlank()) {
            return HistoryPreviewSource.LocalVideo(
                uri = localUri,
                fallbackArtworkUrl = item.thumbnailUrl,
            )
        }
    }
    return item.thumbnailUrl
        .takeIf(String::isNotBlank)
        ?.let(HistoryPreviewSource::RemoteArtwork)
        ?: HistoryPreviewSource.None
}
