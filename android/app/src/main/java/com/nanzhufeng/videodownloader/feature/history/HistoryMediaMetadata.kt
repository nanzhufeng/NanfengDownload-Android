package com.nanzhufeng.videodownloader.feature.history

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class HistoryMediaMetadata(
    val durationMillis: Long?,
    val fileSize: Long,
    val kind: HistoryMediaKind = HistoryMediaKind.VIDEO,
    val readableUris: List<String> = emptyList(),
    val totalFileCount: Int = 0,
)

internal enum class HistoryMediaKind {
    VIDEO,
    AUDIO,
    IMAGE,
}

internal suspend fun readHistoryMediaMetadata(
    context: Context,
    item: DownloadHistory,
): HistoryMediaMetadata = withContext(Dispatchers.IO) {
    val uris = item.outputUris.ifEmpty { item.outputUri?.let(::listOf).orEmpty() }
        .map(Uri::parse)
    if (uris.isEmpty()) {
        return@withContext HistoryMediaMetadata(durationMillis = null, fileSize = item.fileSize)
    }

    val metadata = uris.map { uri ->
        readSingleMetadata(context, uri)
    }
    val readable = metadata.filter(HistorySingleMediaMetadata::readable)
    val readableDurations = readable.mapNotNull(HistorySingleMediaMetadata::durationMillis)
    val readableSizes = metadata.map(HistorySingleMediaMetadata::fileSize)
    val kind = historyMediaKind(item.resolution, metadata.map(HistorySingleMediaMetadata::mimeType))
    HistoryMediaMetadata(
        durationMillis = readableDurations.sum().takeIf { readableDurations.size == uris.size },
        fileSize = readableSizes.sum()
            .takeIf { readableSizes.all { size -> size > 0L } }
            ?: item.fileSize,
        kind = kind,
        readableUris = readable.map { it.uri.toString() },
        totalFileCount = uris.size,
    )
}

private data class HistorySingleMediaMetadata(
    val uri: Uri,
    val durationMillis: Long?,
    val fileSize: Long,
    val mimeType: String,
    val readable: Boolean,
)

private fun readSingleMetadata(
    context: Context,
    uri: Uri,
): HistorySingleMediaMetadata {
    val readable = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.statSize != 0L
        } == true
    }.getOrDefault(false)
    val mimeType = runCatching { context.contentResolver.getType(uri).orEmpty() }.getOrDefault("")
    val actualSize = if (readable) runCatching {
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
    }.getOrNull()?.takeIf { it > 0L } ?: 0L else 0L

    val durationMillis = if (readable && !mimeType.startsWith("image/")) runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
        }
    }.getOrNull() else null

    return HistorySingleMediaMetadata(
        uri = uri,
        durationMillis = durationMillis,
        fileSize = actualSize,
        mimeType = mimeType,
        readable = readable,
    )
}

internal fun historyKindLabel(kind: HistoryMediaKind): String = when (kind) {
    HistoryMediaKind.VIDEO -> "视频"
    HistoryMediaKind.AUDIO -> "音频"
    HistoryMediaKind.IMAGE -> "图片"
}

internal fun historyMediaKind(
    resolution: ResolutionPreset,
    mimeTypes: List<String>,
): HistoryMediaKind = when {
    resolution == ResolutionPreset.AUDIO_MP3 -> HistoryMediaKind.AUDIO
    mimeTypes.any { it.startsWith("image/") } -> HistoryMediaKind.IMAGE
    else -> HistoryMediaKind.VIDEO
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
