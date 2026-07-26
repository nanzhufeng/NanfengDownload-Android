package com.nanzhufeng.videodownloader.feature.history

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class HistoryMediaMetadata(
    val durationMillis: Long?,
    val fileSize: Long,
)

internal suspend fun readHistoryMediaMetadata(
    context: Context,
    item: DownloadHistory,
): HistoryMediaMetadata = withContext(Dispatchers.IO) {
    val uri = item.outputUri?.let(Uri::parse)
        ?: return@withContext HistoryMediaMetadata(durationMillis = null, fileSize = item.fileSize)

    val actualSize = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                null
            }
        }
    }.getOrNull()?.takeIf { it > 0L } ?: item.fileSize

    val durationMillis = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
        }
    }.getOrNull()

    HistoryMediaMetadata(
        durationMillis = durationMillis,
        fileSize = actualSize,
    )
}

internal fun formatMediaDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

internal fun formatExactBytes(bytes: Long): String =
    String.format(Locale.US, "%,d", bytes.coerceAtLeast(0L))
