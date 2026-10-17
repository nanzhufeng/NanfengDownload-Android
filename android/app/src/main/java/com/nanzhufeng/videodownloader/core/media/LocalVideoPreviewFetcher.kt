package com.nanzhufeng.videodownloader.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException

/** The revision separates regenerated media from old single-frame Coil entries. */
internal data class LocalVideoPreview(val uri: String, val revision: String) {
    val cacheKey: String get() = "local-video-preview-v2:$uri:$revision"
}

internal class LocalVideoPreviewKeyer : Keyer<LocalVideoPreview> {
    override fun key(data: LocalVideoPreview, options: Options): String = data.cacheKey
}

internal class LocalVideoPreviewFetcher(
    private val data: LocalVideoPreview,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {
    override suspend fun fetch(): DrawableResult = withContext(Dispatchers.IO) {
        decodeSlots.withPermit {
            val coroutine = currentCoroutineContext()
            coroutine.ensureActive()
            val diskCache = imageLoader.diskCache
            val cached = if (options.diskCachePolicy.readEnabled) runCatching {
                diskCache?.openSnapshot(data.cacheKey)?.use { snapshot ->
                    BitmapFactory.decodeFile(snapshot.data.toFile().absolutePath)
                }
            }.getOrNull() else null
            if (cached != null) {
                try {
                    coroutine.ensureActive()
                    return@withPermit result(cached)
                } catch (failure: Throwable) {
                    cached.recycle()
                    throw failure
                }
            }

            val bitmap = MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(options.context, Uri.parse(data.uri))
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.takeIf { it > 0 }
                VideoPreviewFrames.select(
                    durationMillis = duration,
                    frameAt = { millis ->
                        // Exact closest frame, rather than the closest sync frame in a long black intro.
                        try {
                            retriever.getScaledFrameAtTime(
                                millis * 1_000,
                                MediaMetadataRetriever.OPTION_CLOSEST,
                                384,
                                384,
                            )
                        } catch (_: RuntimeException) {
                            null
                        }
                    },
                    isUsable = ::hasVisibleContent,
                    discard = Bitmap::recycle,
                    checkActive = { coroutine.ensureActive() },
                )
            } ?: throw IOException("视频中未取得可用预览帧")
            try {
                coroutine.ensureActive()
                // Use Coil's bounded disk cache and atomic editor; a cache failure cannot hide a good frame.
                if (options.diskCachePolicy.writeEnabled) {
                    val editor = runCatching { diskCache?.openEditor(data.cacheKey) }.getOrNull()
                    if (editor != null) {
                        try {
                            editor.metadata.toFile().writeText("")
                            editor.data.toFile().outputStream().use {
                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it))
                            }
                            editor.commit()
                        } catch (_: Exception) {
                            runCatching { editor.abort() }
                        }
                    }
                }
                coroutine.ensureActive()
                result(bitmap)
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }
    }

    private fun result(bitmap: Bitmap) = DrawableResult(
        drawable = BitmapDrawable(options.context.resources, bitmap),
        isSampled = true,
        dataSource = DataSource.DISK,
    )

    private fun hasVisibleContent(bitmap: Bitmap): Boolean {
        val columns = minOf(bitmap.width, 24)
        val rows = minOf(bitmap.height, 24)
        val pixels = IntArray(columns * rows) { index ->
            bitmap.getPixel(
                ((index % columns + 0.5) * bitmap.width / columns).toInt().coerceAtMost(bitmap.width - 1),
                ((index / columns + 0.5) * bitmap.height / rows).toInt().coerceAtMost(bitmap.height - 1),
            )
        }
        return VideoPreviewFrames.hasVisibleContent(pixels)
    }

    class Factory : Fetcher.Factory<LocalVideoPreview> {
        override fun create(data: LocalVideoPreview, options: Options, imageLoader: ImageLoader): Fetcher =
            LocalVideoPreviewFetcher(data, options, imageLoader)
    }

    companion object {
        private val decodeSlots = Semaphore(2)
    }
}
