package com.nanzhufeng.videodownloader.domain.download.video

import android.content.Context
import android.net.Uri
import android.media.MediaCodec
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.math.roundToLong
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Pin absolute times to the muxer's clock before it rounds each sample delta. */
internal fun muxTimestampUs(presentationTimeUs: Long, timescale: Int): Long {
    val tick = (presentationTimeUs.toDouble() * timescale / 1_000_000.0).roundToLong()
    return (tick.toDouble() * 1_000_000.0 / timescale).roundToLong()
}

internal data class TrackExportSummary(
    val videoEncoder: String? = null,
    val audioEncoder: String? = null,
    val videoFrames: Int,
    val hasAudio: Boolean,
)

internal fun interface CompressedTrackExporter {
    suspend fun export(video: File, audio: File, output: File, cancelled: AtomicBoolean,
                       onProgress: (Int) -> Unit): TrackExportSummary
}

/** Keeps the old cache intact until a complete, unencoded export can replace it atomically. */
@UnstableApi
internal class Media3TrackMuxer(private val exporter: CompressedTrackExporter) : MediaTrackMuxer {
    constructor(context: Context) : this(Media3CompressedTrackExporter(context.applicationContext))

    override suspend fun merge(video: File, audio: File, output: File, cancelled: AtomicBoolean,
                               onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        require(video.isFile && video.length() > 0L && audio.isFile && audio.length() > 0L) {
            "待合并的音视频流不存在或为空"
        }
        require(output.canonicalFile !in setOf(video.canonicalFile, audio.canonicalFile)) {
            "合并结果不能覆盖音视频来源"
        }
        if (cancelled.get()) throw CancellationException("音视频合并已取消")
        output.parentFile?.mkdirs()
        val partial = File(output.parentFile, output.name + "." + UUID.randomUUID() + ".mux.part")
        try {
            val result = exporter.export(video, audio, partial, cancelled, onProgress)
            if (cancelled.get()) throw CancellationException("音视频合并已取消")
            check(result.videoEncoder == null && result.audioEncoder == null) { "音视频合并意外启用了重新编码，未发布" }
            check(result.videoFrames > 0 && result.hasAudio) { "音视频合并缺少完整轨道，未发布" }
            check(partial.isFile && partial.length() > 0L) { "音视频合并没有生成有效文件" }
            Files.move(partial.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            onProgress(100)
            output
        } finally { partial.delete() }
    }
}

/** Media3 reads MP4 samples in-process, avoiding a Binder round trip for every sample field. */
@UnstableApi
private class Media3CompressedTrackExporter(private val context: Context) : CompressedTrackExporter {
    override suspend fun export(video: File, audio: File, output: File, cancelled: AtomicBoolean,
                                onProgress: (Int) -> Unit): TrackExportSummary = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            lateinit var transformer: Transformer
            lateinit var ticker: Runnable
            val audioSamples = AtomicInteger()
            val factory = InAppMp4Muxer.Factory()
            val countingFactory = object : Muxer.Factory by factory {
                override fun create(path: String): Muxer {
                    val delegate = factory.create(path)
                    val audioTracks = mutableSetOf<Int>()
                    val timescales = mutableMapOf<Int, Int>()
                    return object : Muxer by delegate {
                        override fun addTrack(format: Format): Int = delegate.addTrack(format).also {
                            val isAudio = MimeTypes.isAudio(format.sampleMimeType)
                            if (isAudio) audioTracks.add(it)
                            // Media3 1.7.1 Track.videoUnitTimebase: audio 48k, video 90k.
                            timescales[it] = if (isAudio) 48_000 else 90_000
                        }
                        override fun writeSampleData(trackId: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                            val adjusted = MediaCodec.BufferInfo().apply {
                                set(info.offset, info.size,
                                    muxTimestampUs(info.presentationTimeUs, timescales.getValue(trackId)), info.flags)
                            }
                            delegate.writeSampleData(trackId, buffer, adjusted)
                            if (trackId in audioTracks && info.size > 0) audioSamples.incrementAndGet()
                        }
                    }
                }
            }
            fun fail(error: Throwable) {
                handler.removeCallbacks(ticker)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    handler.removeCallbacks(ticker)
                    if (!continuation.isActive) return
                    continuation.resume(TrackExportSummary(result.videoEncoderName, result.audioEncoderName,
                        result.videoFrameCount, audioSamples.get() > 0))
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    fail(exception)
                }
            }
            transformer = Transformer.Builder(context)
                .setMuxerFactory(countingFactory)
                .addListener(listener)
                .build()
            val progress = ProgressHolder()
            var lastProgress = -1
            ticker = Runnable {
                if (!continuation.isActive) return@Runnable
                if (cancelled.get()) {
                    transformer.cancel()
                    fail(CancellationException("音视频合并已取消"))
                    return@Runnable
                }
                try {
                    if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE &&
                        progress.progress != lastProgress) {
                        lastProgress = progress.progress
                        onProgress(progress.progress.coerceIn(0, 99))
                    }
                } catch (error: Throwable) {
                    transformer.cancel()
                    fail(error)
                    return@Runnable
                }
                handler.postDelayed(ticker, 250)
            }
            continuation.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(ticker)
                    transformer.cancel()
                    output.delete()
                }
            }
            val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(video)))
                .setRemoveAudio(true).build()
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audio)))
                .setRemoveVideo(true).build()
            val composition = Composition.Builder(
                EditedMediaItemSequence.Builder(videoItem).build(),
                EditedMediaItemSequence.Builder(audioItem).build(),
            ).setTransmuxAudio(true).setTransmuxVideo(true).build()
            try {
                transformer.start(composition, output.absolutePath)
                handler.post(ticker)
            } catch (error: Throwable) { fail(error) }
        }
    }
}
