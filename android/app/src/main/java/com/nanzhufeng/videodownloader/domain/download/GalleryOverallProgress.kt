package com.nanzhufeng.videodownloader.domain.download

/**
 * Converts sequential image/live-photo transfers into one task-wide progress.
 * A single picture reaching its own byte length must never make the complete
 * gallery appear to be at 100%.
 */
internal class GalleryOverallProgress(expectedBytes: List<Long>) {
    private val expected = expectedBytes.map { it.coerceAtLeast(0L) }
    private var completedBytes = 0L

    val totalBytes: Long = expected.sum()
    val hasReliableTotal: Boolean = expected.isNotEmpty() && expected.all { it > 0L }

    fun update(currentDownloadedBytes: Long, speedBytesPerSecond: Long): OverallGalleryProgress {
        if (!hasReliableTotal) {
            return OverallGalleryProgress(
                downloadedBytes = completedBytes + currentDownloadedBytes.coerceAtLeast(0L),
                totalBytes = 0L,
                remainingSeconds = null,
            )
        }
        val downloaded = (completedBytes + currentDownloadedBytes.coerceAtLeast(0L))
            .coerceAtMost(totalBytes)
        val remaining = if (speedBytesPerSecond > 0L) {
            ((totalBytes - downloaded).coerceAtLeast(0L) / speedBytesPerSecond).coerceAtLeast(0L)
        } else {
            null
        }
        return OverallGalleryProgress(downloaded, totalBytes, remaining)
    }

    fun complete(fileBytes: Long) {
        completedBytes = (completedBytes + fileBytes.coerceAtLeast(0L)).coerceAtMost(totalBytes)
    }

    fun finish(): OverallGalleryProgress = OverallGalleryProgress(
        downloadedBytes = if (hasReliableTotal) totalBytes else completedBytes,
        totalBytes = if (hasReliableTotal) totalBytes else 0L,
        remainingSeconds = null,
    )
}

internal data class OverallGalleryProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val remainingSeconds: Long?,
)
