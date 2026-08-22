package com.nanzhufeng.videodownloader.domain.download.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val TARGET_SHORT_EDGE = 360

fun interface VideoResolutionTranscoder {
    suspend fun transcodeTo360p(
        source: File,
        destination: File,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ): File
}

internal fun needs360pTranscode(shortEdge: Int, resolution: String): Boolean =
    resolution == "UP_TO_360P" && shortEdge > TARGET_SHORT_EDGE

/**
 * Re-encodes an available higher rendition into a real 360p MP4 when a
 * platform does not provide a 360p stream. The choice is based on the short
 * edge so portrait video becomes 360x640 and landscape video becomes 640x360.
 */
@UnstableApi
class AndroidMedia3VideoResolutionTranscoder(
    private val context: Context,
) : VideoResolutionTranscoder {
    override suspend fun transcodeTo360p(
        source: File,
        destination: File,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ): File {
        require(source.isFile && source.length() > 0L) { "待转码视频不存在或为空" }
        require(!cancelled.get()) { throw CancellationException("视频转码已取消") }
        val (width, height) = dimensions(source)
        if (!needs360pTranscode(minOf(width, height), "UP_TO_360P")) return source
        destination.parentFile?.mkdirs()
        if (destination.exists() && !destination.delete()) {
            throw IllegalStateException("无法覆盖旧的 360p 转码文件")
        }
        onProgress(0)
        // Transformer is thread-affine. A WorkManager coroutine may be cancelled
        // from a different dispatcher, so creation, start, and cancellation must
        // all be serialized onto the main application looper.
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
            lateinit var transformer: Transformer
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, result: ExportResult) {
                    if (destination.isFile && destination.length() > 0L) {
                        onProgress(100)
                        continuation.resume(destination)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("360p 转码没有生成有效文件"),
                        )
                    }
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    result: ExportResult,
                    exception: ExportException,
                ) {
                    continuation.resumeWithException(exception)
                }
            }
            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(listener)
                .build()
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post {
                    transformer.cancel()
                }
            }
            // Presentation exposes a height API. For portrait media calculate
            // the proportional height that leaves the output width at 360px.
            val targetHeight = if (height > width) {
                ((height.toLong() * TARGET_SHORT_EDGE / width).toInt() / 2) * 2
            } else {
                TARGET_SHORT_EDGE
            }
            val presentation = Presentation.createForHeight(targetHeight)
            transformer.start(
                EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
                    .setEffects(Effects(emptyList(), listOf(presentation)))
                    .build(),
                destination.absolutePath,
            )
            }
        }
    }

    private fun dimensions(source: File): Pair<Int, Int> = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(source.absolutePath)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?: throw IllegalStateException("无法读取视频宽度，不能安全转为 360p")
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?: throw IllegalStateException("无法读取视频高度，不能安全转为 360p")
        width to height
    }
}
